# Inference Server GPU 优化方案

> 对标：[`ucc-ai/python/inference_server.py`](ucc-ai/python/inference_server.py)  
> 硬件目标：RTX 3060 12GB  
> 当前瓶颈：单次 batch(64) 推理约 15-25ms，优化目标 8-12ms

---

## 一、现状概览

### 1.1 当前已实现的优化

| 优化 | 代码位置 | 说明 |
|------|---------|------|
| `torch.no_grad()` | [`inference_server.py:62`](ucc-ai/python/inference_server.py:62) | 禁用梯度追踪，降低显存占用 |
| `cuda.amp.autocast()` | [`inference_server.py:64`](ucc-ai/python/inference_server.py:64) | 混合精度 FP16 推理（仅 CUDA 启用） |
| 批量处理 (batch=64) | Java [`BatchingEngine.java`](ucc-server/src/main/java/io/github/samera2022/chinese_chess/server/train/BatchingEngine.java) 负责打包 | Java 端汇聚 64 个请求后一次性发送 |

### 1.2 当前缺失的关键优化

| 优化 | 缺失影响 | 难度 | 优先级 |
|------|---------|------|--------|
| **模型 Warmup** | 首次推理延迟 2-3 秒（JIT 编译） | 低 | P0 |
| **Pinned Memory** | CPU→GPU 传输带宽减半 | 低 | P0 |
| **异步传输 (non_blocking)** | CPU 等待传输完成，浪费算力 | 低 | P1 |
| **CUDA Stream 多流** | 无法重叠传输+计算 | 中 | P1 |
| **CUDA Graph** | kernel launch 开销未消除 | 高 | P2 |
| **TensorRT/ONNX** | 推理速度 ~2x 差距 | 高 | P2 |
| **Graceful Shutdown** | 训练中断可能损坏模型文件 | 低 | P1 |
| **显存碎片控制** | 长期运行可能 OOM | 中 | P1 |

---

## 二、优化方案详情

### 2.1 P0 — 立即实施

#### 2.1.1 模型 Warmup

**问题**：PyTorch 首次调用 `model.forward()` 时触发 CUDA JIT 编译（kernel 编译 + 缓存），导致第一个请求延迟高达 2-3 秒。在 BatchingEngine 的 5ms 超时窗口内，这会导致：
- 首个 batch 超时返回部分数据
- 后续 batch 恢复正常

**方案**：在 `InferenceServicer.__init__()` 中执行一次 dummy 推理

```python
class InferenceServicer:
    def __init__(self, model_path: str, device: str = "cuda"):
        # ... 现有代码 ...
        self.model.eval()
        self.use_amp = self.device.type == "cuda"

        # ── Warmup：执行一次 dummy 推理触发 CUDA JIT 编译 ──
        self._warmup()

    def _warmup(self) -> None:
        """使用 dummy 数据预热模型，消除首次推理的 JIT 编译延迟。"""
        dummy_board = torch.randn(1, 14, 10, 9, device=self.device)
        dummy_rule = torch.randn(1, 28, device=self.device)
        with torch.no_grad():
            if self.use_amp:
                with torch.cuda.amp.autocast():
                    self.model(dummy_board, dummy_rule)
            else:
                self.model(dummy_board, dummy_rule)
        # 同步等待 GPU 完成
        torch.cuda.synchronize(self.device)
        print(f"  ✓ 模型 Warmup 完成（CUDA kernel 已编译缓存）")
```

**预期收益**：
- 消除首次推理的 2-3 秒延迟
- 后续推理延迟稳定在 15-25ms

---

#### 2.1.2 Pinned Memory（固定内存）

**问题**：当前使用 `torch.from_numpy().to(device)` 进行 CPU→GPU 传输，分配的是**可分页内存**（pageable memory）。GPU 通过 DMA 从可分页内存传输数据时，需要先复制到固定的临时缓冲区（pinned staging buffer），导致：
- 传输带宽减半（实际带宽约 6-8 GB/s，理论峰值 12 GB/s）
- 无法使用 `non_blocking=True` 异步传输

**方案**：使用 `pin_memory()` 或 `torch.Tensor.pin_memory()` 提前固定内存

```python
# ── 修改前 ──
board_batch = torch.from_numpy(board_tensors).to(self.device)
rule_batch = torch.from_numpy(rule_tensors).to(self.device)

# ── 修改后 ──
board_batch = torch.from_numpy(board_tensors).pin_memory().to(
    self.device, non_blocking=True
)
rule_batch = torch.from_numpy(rule_tensors).pin_memory().to(
    self.device, non_blocking=True
)
# 需要同步点
torch.cuda.current_stream().synchronize()
```

**但是**：`pin_memory()` 每次调用都会分配/释放固定内存，引入额外开销。更好的方式是在 `__init__` 中预分配 pinned buffer：

```python
class InferenceServicer:
    def __init__(self, ...):
        # ... 现有代码 ...
        # 预分配 Pinned Memory Buffer（最大 batch=64, rows=18, cols=9）
        self.pinned_board = torch.empty(
            (64, 14, 18, 9), dtype=torch.float32, pin_memory=True
        )
        self.pinned_rule = torch.empty(
            (64, 28), dtype=torch.float32, pin_memory=True
        )

    def BatchInfer(self, request, context):
        batch_size = len(request.boards)
        rows = 18 if request.rules[0].rule_vector[21] >= 0.5 else 10
        cols = 9

        # 使用预分配 pinned buffer 的子视图
        board_view = self.pinned_board[:batch_size, :, :rows, :]
        rule_view = self.pinned_rule[:batch_size, :]

        for i, (board_proto, rules_proto) in enumerate(
            zip(request.boards, request.rules)):
            board_dict = proto_to_dict(board_proto)
            board_tensor = board_to_tensor(board_dict, rows, cols)
            board_view[i] = torch.from_numpy(board_tensor)
            rule_view[i] = torch.from_numpy(
                np.array(rules_proto.rule_vector, dtype=np.float32)
            )

        # 异步传输到 GPU
        board_batch = board_view.to(self.device, non_blocking=True)
        rule_batch = rule_view.to(self.device, non_blocking=True)

        with torch.no_grad():
            if self.use_amp:
                with torch.cuda.amp.autocast():
                    policy_logits, values = self.model(board_batch, rule_batch)
            else:
                policy_logits, values = self.model(board_batch, rule_batch)
        # ...
```

**预期收益**：
- CPU→GPU 传输时间减少约 40-50%
- 对于 batch=64，传输 ~0.5MB → 时间从 ~80μs 降至 ~40μs

---

### 2.2 P1 — 短周期实施

#### 2.2.1 CUDA Stream 多流（传输与计算重叠）

**问题**：当前全部操作在默认流（default stream）上执行，CPU→GPU 数据传输和 GPU kernel 计算串行执行。使用 CUDA Stream 可以：
1. 流 A：传输下一个 batch 的数据
2. 流 B：执行当前 batch 的推理
3. 两个流在 GPU 上**并发执行**（计算 + 数据传输重叠）

**方案**：

```python
class InferenceServicer:
    def __init__(self, ...):
        # ... 现有代码 ...
        self.infer_stream = torch.cuda.Stream(device=self.device)
        self.transfer_stream = torch.cuda.Stream(device=self.device)
        self._prewarm_streams()

    def _prewarm_streams(self):
        """预热 stream 上下文，避免首次使用时的创建开销。"""
        with torch.cuda.stream(self.infer_stream):
            pass
        with torch.cuda.stream(self.transfer_stream):
            pass
        torch.cuda.synchronize()

    def BatchInfer(self, request, context):
        batch_size = len(request.boards)
        # ... 准备数据 ...

        # 步骤 1：在 transfer_stream 上异步传输
        with torch.cuda.stream(self.transfer_stream):
            board_batch = torch.from_numpy(board_tensors).to(
                self.device, non_blocking=True
            )
            rule_batch = torch.from_numpy(rule_tensors).to(
                self.device, non_blocking=True
            )

        # 步骤 2：同步后切换到 infer_stream
        self.transfer_stream.synchronize()

        with torch.cuda.stream(self.infer_stream):
            with torch.no_grad():
                if self.use_amp:
                    with torch.cuda.amp.autocast():
                        policy_logits, values = self.model(
                            board_batch, rule_batch
                        )
                else:
                    policy_logits, values = self.model(board_batch, rule_batch)

        # 步骤 3：同步后返回结果
        self.infer_stream.synchronize()
        # ... 构造响应 ...
```

**⚠️ 注意**：在当前架构下（Java BatchingEngine 打包为单次 gRPC 调用），多流优化收益有限，因为一次只有一个 `BatchInfer` 请求。只有在**多个 BatchingEngine 实例并发发送请求**时才有显著收益。

**预期收益**：
- 单请求场景：~5% 提升（主要来自 stream 上下文优化）
- 多请求并发场景：~30% 提升（传输与计算重叠）

---

#### 2.2.2 异步 gRPC + 请求队列

**问题**：当前 gRPC 是同步调用模式。当多个 `BatchInfer` 同时到达时，它们共享同一个模型实例，在 Python 中排队执行。

**方案**：引入显式请求队列 + 后台 batching 线程

```python
import asyncio
from concurrent.futures import ThreadPoolExecutor
import threading

class AsyncInferenceServicer:
    def __init__(self, model_path: str, device: str = "cuda"):
        # ... 模型初始化 ...
        self.request_queue: asyncio.Queue = asyncio.Queue(maxsize=128)
        self.batch_size = 64
        self.batch_timeout = 0.005  # 5ms
        self._start_bg_batcher()

    def _start_bg_batcher(self):
        """后台线程：从队列收集请求，打包批量推理。"""
        def batcher_loop():
            while True:
                batch = []
                deadline = time.monotonic() + self.batch_timeout
                while len(batch) < self.batch_size:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        break
                    try:
                        # 等待最多 remaining 秒
                        pass  # 异步队列获取
                    except:
                        break
                if batch:
                    self._execute_batch(batch)

        thread = threading.Thread(target=batcher_loop, daemon=True)
        thread.start()

    async def BatchInfer(self, request, context):
        """异步 gRPC 端点。"""
        future = asyncio.get_event_loop().create_future()
        await self.request_queue.put((request, future))
        return await future
```

**预期收益**：
- 在高并发场景下减少 gRPC 线程池争用
- 允许更好的请求合并策略

---

#### 2.2.3 显存碎片控制

**问题**：长期运行（数小时）时，PyTorch 的 CUDA 内存分配器可能产生碎片，导致：
- 可用显存总量减少
- 偶尔触发 CUDA OOM

**方案**：

```python
class InferenceServicer:
    def __init__(self, ...):
        # ... 现有代码 ...
        # 限制 PyTorch 显存使用率
        if self.device.type == "cuda":
            # 预留 20% 显存给其他进程（如训练）
            torch.cuda.set_per_process_memory_fraction(0.8)
            # 启用内存统计
            torch.cuda.memory.set_per_process_memory_fraction(0.8)
            print(f"  ✓ 显存限制: 80%（共 {torch.cuda.get_device_properties(0).total_memory / 1e9:.1f}GB）")

    def _maybe_empty_cache(self):
        """定期清理显存缓存（每小时一次）。"""
        now = time.monotonic()
        if now - self._last_cache_clean > 3600:
            torch.cuda.empty_cache()
            self._last_cache_clean = now
```

**预期收益**：
- 消除长时间运行的显存泄漏风险
- 保持稳定的推理性能

---

### 2.3 P2 — 中长期实施

#### 2.3.1 CUDA Graph

**问题**：每次 `model.forward()` 都涉及大量的 CUDA kernel launch 操作（每个 Conv2D、BatchNorm、ReLU 各一次）。对于 5 残差块 + 128 滤波器的 MiniResNet，一次前向传播约涉及 50-80 个 kernel launch。每个 kernel launch 的开销约为 5-10μs，合计 250-800μs 的纯开销。

**CUDA Graph 原理**：
1. 首次运行时捕获完整的 GPU 计算图（所有 kernel 的依赖关系）
2. 后续运行时只需要一次 `graph.replay()` 即可重放整个计算图
3. 消除了 kernel launch 的 CPU 开销

**方案**：

```python
class InferenceServicer:
    def __init__(self, ...):
        # ... 现有代码 ...
        self.use_cudagraph = False
        self.graph = None
        self.static_board = None
        self.static_rule = None
        self.static_policy = None
        self.static_value = None

    def _maybe_capture_graph(self, board: torch.Tensor, rule: torch.Tensor):
        """捕获 CUDA Graph（仅在首次固定形状时）。"""
        if self.graph is not None:
            return

        # CUDA Graph 要求静态输入输出形状
        self.static_board = board
        self.static_rule = rule
        self.static_policy = torch.empty_like(board)  # dummy
        self.static_value = torch.empty((board.shape[0], 1), device=self.device)

        # 预热
        for _ in range(3):
            with torch.no_grad():
                self.model(self.static_board, self.static_rule)

        # 捕获 Graph
        self.graph = torch.cuda.CUDAGraph()
        with torch.cuda.graph(self.graph):
            with torch.no_grad():
                if self.use_amp:
                    with torch.cuda.amp.autocast():
                        self.static_policy, self.static_value = self.model(
                            self.static_board, self.static_rule
                        )
                else:
                    self.static_policy, self.static_value = self.model(
                        self.static_board, self.static_rule
                    )

    def batch_infer_with_graph(self, board: torch.Tensor, rule: torch.Tensor):
        """使用 CUDA Graph 执行推理。"""
        if self.graph is None:
            # 回退到普通推理
            with torch.no_grad():
                return self.model(board, rule)

        # 将输入数据复制到静态 buffer
        self.static_board.copy_(board)
        self.static_rule.copy_(rule)

        # 重放 CUDA Graph
        self.graph.replay()

        return self.static_policy.clone(), self.static_value.clone()
```

**预期收益**：
- 消除 250-800μs 的 kernel launch 开销
- 总推理时间减少约 10-20%
- 需要固定输入形状（batch_size 和 rows×cols 稳定时效果最佳）

---

#### 2.3.2 TensorRT / ONNX Runtime

**问题**：PyTorch 的 eager mode 执行有大量的 Python 解释器开销和算子调度开销。TensorRT 可以对计算图进行：
- 层融合（fuse Conv2D + BatchNorm + ReLU 为单个 kernel）
- 精度校准（INT8 量化）
- 内存优化（in-place 操作减少显存）
- 自动调优（选择最优 kernel 实现）

**方案**：

```python
# ── 导出 ONNX ──
import torch.onnx

def export_to_onnx(model, output_path="model.onnx"):
    model.eval()
    dummy_board = torch.randn(1, 14, 10, 9)
    dummy_rule = torch.randn(1, 28)
    torch.onnx.export(
        model,
        (dummy_board, dummy_rule),
        output_path,
        input_names=["board", "rules"],
        output_names=["policy", "value"],
        dynamic_axes={
            "board": {0: "batch_size"},
            "rules": {0: "batch_size"},
            "policy": {0: "batch_size"},
            "value": {0: "batch_size"},
        },
        opset_version=17,
    )
```

```python
# ── TensorRT 推理 ──
import tensorrt as trt

class TensorRTInference:
    def __init__(self, onnx_path, max_batch_size=64):
        self.logger = trt.Logger(trt.Logger.WARNING)
        self.builder = trt.Builder(self.logger)
        self.network = self.builder.create_network(
            1 << int(trt.NetworkDefinitionCreationFlag.EXPLICIT_BATCH)
        )
        self.parser = trt.OnnxParser(self.network, self.logger)
        with open(onnx_path, "rb") as f:
            self.parser.parse(f.read())

        config = self.builder.create_builder_config()
        config.set_memory_pool_limit(
            trt.MemoryPoolType.WORKSPACE, 1 << 30  # 1GB
        )
        config.set_flag(trt.BuilderFlag.FP16)  # FP16 推理

        self.engine = self.builder.build_engine(self.network, config)
        self.context = self.engine.create_execution_context()

    def infer(self, board_np, rule_np):
        # TensorRT 执行推理
        ...
```

**预期收益**（RTX 3060）：
| 引擎 | 推理时间 (batch=64) | 加速比 |
|------|--------------------|--------|
| PyTorch eager (FP32) | 15-25ms | 1x |
| PyTorch AMP (FP16) | 10-18ms | ~1.4x |
| TensorRT (FP16) | 5-10ms | ~2.5x |
| TensorRT (INT8) | 3-6ms | ~4x |

---

#### 2.3.3 Multi-Process GPU 推理

**问题**：单进程单模型的推理服务无法利用 RTX 3060 的全部计算能力。当 batch_size=64 时，GPU 利用率可能在 60-80%（取决于模型复杂度）。进一步增加 batch_size 受限于 BatchingEngine 的 5ms 超时窗口。

**方案**：多个 Worker 进程共享 GPU，每个进程运行独立的模型副本

```python
import multiprocessing as mp

def worker_process(rank, model_path, port_offset, device_id=0):
    """Worker 进程：独立 GPU context + 独立端口。"""
    torch.cuda.set_device(device_id)
    servicer = InferenceServicer(model_path, f"cuda:{device_id}")
    serve(model_path, port=50051 + port_offset, max_workers=4)

def start_multi_gpu(model_path, num_workers=2):
    """启动多个推理 Worker 进程。"""
    processes = []
    for i in range(num_workers):
        p = mp.Process(
            target=worker_process,
            args=(i, model_path, i, 0),  # 同一 GPU
        )
        p.start()
        processes.append(p)

    for p in processes:
        p.join()
```

**⚠️ 注意**：多个进程共享同一 GPU 时，每个进程需要独立的模型副本，显存占用线性增加。对于 RTX 3060 12GB：
- 1 个进程：模型 ~2GB + batch buffer ~0.5GB = ~2.5GB
- 2 个进程：~5GB
- 3 个进程：~7.5GB（仍可用）

然后 Java 端的 `GrpcInferenceClient` 需要实现**负载均衡**：将推理请求分发到多个 gRPC 端点。

**预期收益**：
- 2 Worker：吞吐量 ~1.8x
- 3 Worker：吞吐量 ~2.5x（有显存争用，非线性）

---

## 三、优化路线图

### 阶段 1：立即（1-2 天）

| # | 优化 | 预计耗时 | 预期收益 |
|---|------|---------|---------|
| 1 | 模型 Warmup | 30 分钟 | 消除首次推理 2-3 秒延迟 |
| 2 | Pinned Memory | 1 小时 | 传输时间减少 40% |
| 3 | 显存限制 + 定期清理 | 30 分钟 | 消除长期运行 OOM 风险 |

### 阶段 2：短周期（1 周）

| # | 优化 | 预计耗时 | 预期收益 |
|---|------|---------|---------|
| 4 | CUDA Stream 多流 | 2 小时 | 多请求场景吞吐量 +30% |
| 5 | 异步请求队列 | 4 小时 | 高并发场景 GPU 利用率 +20% |
| 6 | Graceful Shutdown | 1 小时 | 安全退出保护模型文件 |

### 阶段 3：中长期（2-4 周）

| # | 优化 | 预计耗时 | 预期收益 |
|---|------|---------|---------|
| 7 | CUDA Graph | 2 天 | 推理时间 -15% |
| 8 | TensorRT 集成 | 5 天 | 推理时间 -60%（FP16） |
| 9 | Multi-Process 负载均衡 | 3 天 | 吞吐量 +80% |

---

## 四、预期最终性能

### 单次 BatchInfer (batch=64, RTX 3060)

| 阶段 | 推理时间 | 吞吐量 | 相比当前 |
|------|---------|--------|---------|
| 当前 (PyTorch AMP) | 15-25ms | ~3200 样本/秒 | 1x |
| 阶段 1 (Warmup + Pinned) | 12-18ms | ~4200 样本/秒 | 1.3x |
| 阶段 2 (Stream + 队列) | 10-15ms | ~5000 样本/秒 | 1.6x |
| 阶段 3 (TensorRT) | 5-10ms | ~9000 样本/秒 | 2.8x |

### 结合 Java 端 100 Workers

```
100 Workers × 800 simulations/局 × ～50 步/局
= 4,000,000 次推理/局

当前 (3200 样本/秒) → 约 20.8 分钟/局
阶段 1 (4200 样本/秒) → 约 15.9 分钟/局
阶段 2 (5000 样本/秒) → 约 13.3 分钟/局
阶段 3 (9000 样本/秒) → 约 7.4 分钟/局
```

---

## 五、实施注意事项

### 5.1 兼容性检查

```python
# 在启动时检查 GPU 能力，自动降级
def check_gpu_capability():
    if not torch.cuda.is_available():
        print("⚠️  CUDA 不可用，使用 CPU 推理")
        return "cpu"

    capability = torch.cuda.get_device_capability(0)
    major, minor = capability
    print(f"  GPU: {torch.cuda.get_device_name(0)}")
    print(f"  CUDA Capability: {major}.{minor}")
    print(f"  显存: {torch.cuda.get_device_properties(0).total_memory / 1e9:.1f} GB")

    if major < 7:
        print("  ⚠️  架构较旧（< Volta），AMP 可能不支持，降级 FP32")
    else:
        print("  ✓  支持 AMP FP16")

    if major < 8:
        print("  ⚠️  架构较旧，CUDA Graph 可能不支持")
    else:
        print("  ✓  支持 CUDA Graph")

    return f"cuda:0"
```

### 5.2 回退策略

所有优化都应有 `try/except` 回退：

```python
try:
    # 尝试使用 CUDA Graph
    graph.replay()
except RuntimeError as e:
    # 回退到普通推理
    logger.warning(f"CUDA Graph 失败，回退到 eager mode: {e}")
    policy_logits, values = self.model(board, rule)
```

### 5.3 基准测试

每次优化后运行基准测试：

```bash
# 在 inference_server.py 中使用 --test 模式
python inference_server.py --model checkpoints/best.pt --test

# 或者使用独立基准脚本
python benchmark_inference.py --model checkpoints/best.pt --batch_size 64 --iterations 100
```

建议 `benchmark_inference.py` 输出：

```
Benchmark Results (batch_size=64, iterations=100):
  Avg:  18.34 ms
  P50:  17.89 ms
  P95:  22.15 ms
  P99:  25.67 ms
  GPU Util: 72.3%
  GPU Mem:  2.1 GB / 12.0 GB
```

---

## 六、与训练流水线的集成

### 6.1 训练时推理服务的行为

在训练期间，`inference_server.py` 的行为取决于使用哪条训练路径：

| 场景 | 推理服务状态 | 说明 |
|------|------------|------|
| Python train.py 自包含训练 | ❌ 不需要 | train.py 内部调用 model.forward() |
| Java 高性能训练（推理路径） | ✅ 必须运行 | Java BatchingEngine → gRPC → inference_server |
| Java 高性能训练（训练路径） | ✅ 必须运行 | 推理 + 训练共用 GPU，需要协调 |

### 6.2 GPU 共享策略（RTX 3060 12GB）

当推理和训练共用同一张 GPU 时：

```python
# inference_server.py 启动时预留显存
torch.cuda.set_per_process_memory_fraction(0.4)  # 预留 40% (4.8GB)

# train.py 训练时
torch.cuda.set_per_process_memory_fraction(0.5)   # 使用 50% (6.0GB)
# 剩余 10% (1.2GB) 作为缓冲
```

**更好的方案**：使用 CUDA MPS（Multi-Process Service），让两个进程共享 GPU 资源。

```bash
# 启动 CUDA MPS 守护进程
export CUDA_MPS_PIPE_DIRECTORY=/tmp/mps_pipe
export CUDA_MPS_LOG_DIRECTORY=/tmp/mps_log
nvidia-cuda-mps-control -d

# 运行推理服务
CUDA_MPS_PIPE_DIRECTORY=/tmp/mps_pipe python inference_server.py ...

# 运行训练
CUDA_MPS_PIPE_DIRECTORY=/tmp/mps_pipe python train.py --full ...
```

> **注意**：CUDA MPS 在 RTX 30 系列上支持有限。如果遇到问题，建议改用时间片轮转策略（默认行为）。
