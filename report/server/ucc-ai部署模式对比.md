# ucc-ai 独立进程 vs 内嵌入 ucc-server 对比分析

> 版本: 1.0  
> 日期: 2026-06-01  
> 关联文档: [ucc-server解耦与高并发架构方案.md](ucc-server解耦与高并发架构方案.md)

---

## 一、两种架构模式定义

### 模式 A: 独立进程（gRPC 通信）

```
┌───────────────────┐         gRPC/Protobuf        ┌──────────────────┐
│   ucc-server      │ ◀─────────────────────────▶  │   ucc-ai Python  │
│   (Java 21 JVM)   │     localhost:50051          │   (独立 Python   │
│   72 核 CPU        │     Unix Domain Socket        │   进程, GPU)     │
└───────────────────┘                               └──────────────────┘
```

- Java 和 Python 各自独立 JVM/进程
- 通过 gRPC (Protobuf) 或 Redis 通信
- 各自独立部署、启动、监控、重启

### 模式 B: 内嵌入 ucc-server（JNI/DJL 调用）

```
┌─────────────────────────────────────────────┐
│   ucc-server (Java 21 JVM)                   │
│                                               │
│   ┌─────────────┐    ┌──────────────────┐   │
│   │ Java Room/   │    │ PyTorch Java      │   │
│   │ Match/MCTS   │───▶│ (DJL / JPype /   │   │
│   │              │    │  JNI 直接调用)     │   │
│   └─────────────┘    └──────────────────┘   │
│                                               │
│   全部运行在同一个 OS 进程中                    │
└─────────────────────────────────────────────┘
```

- Python/PT 模型通过 Java 生态加载：
  - **DJL (Deep Java Library)**: 纯 Java 推理引擎，加载 TorchScript/ONNX
  - **JPype**: JVM 内嵌入 CPython 解释器
  - **JNI + LibTorch**: C++ FFI 直接调用

---

## 二、逐维度对比

### 2.1 通信开销

| 维度 | 模式 A (独立进程 gRPC) | 模式 B (进程内嵌) |
|------|----------------------|-------------------|
| 序列化 | ✅ Protobuf 二进制序列化，极低 CPU 开销 | ✅ 零序列化（共享内存 / 直接指针） |
| 网络栈 | ⚠️ 经 TCP/IP 协议栈（即使 localhost） | ✅ 无网络栈 |
| 延迟 (p50) | ~50-200μs (localhost gRPC) | ~5-20μs (JNI/DJL) |
| 延迟 (p99) | ~500μs-2ms（GC 抖动 + TCP 重传） | ~20-50μs |
| 内存拷贝 | ⚠️ Protobuf 序列化 → socket buffer → 反序列化，至少 2 次拷贝 | ✅ 零拷贝或 1 次（DJL 的 NDArray 共享） |
| 适用 Batch 大小 | ✅ 大批量 (≥64) 时摊销效果好 | ✅ 任意 Batch 大小均高效 |

**结论**: 模式 B 在**延迟**和**内存带宽**上有绝对优势。但模式 A 在 Batch=64+ 时差距缩小，因为 Protobuf 序列化开销被摊销。

### 2.2 部署与运维

| 维度 | 模式 A (独立进程) | 模式 B (内嵌) |
|------|-----------------|--------------|
| 部署单元 | 2 个（JAR + Python 脚本） | 1 个（单个 fat JAR） |
| 启动顺序 | Java 先启动，Python 后启动；需健康检查 | 单命令启动 |
| 进程监控 | 2 个进程分别监控 | 1 个进程 |
| 崩溃隔离 | ✅ Python 崩溃不影响 Java 对局 | ❌ Python/GPU 崩溃拉垮整个 Java 进程 |
| 内存隔离 | ✅ 各自独立内存空间，OOM 不互相影响 | ❌ JVM 堆 + 本机内存（LibTorch）共享，GPU OOM 可能触发 JVM OOM Killer |
| 独立扩缩容 | ✅ GPU 推理成为独立微服务，可独立扩容（加 GPU 机器） | ❌ 与 Java 绑定，无法独立扩容 |
| 滚动更新 | ✅ Python 模型更新 → 重启 Python，Java 无感知（gRPC 自动重连） | ❌ 模型更新需重启整个 JVM |
| 日志/监控 | 2 套日志系统，需统一采集 | 1 套日志（但 GPU 错误在 JNI 层难以捕获） |

**结论**: 模式 A 在**运维灵活性**和**故障隔离**上远超模式 B。模式 B 的优势仅在于"单进程部署更简单"。

### 2.3 开发体验

| 维度 | 模式 A (独立进程) | 模式 B (内嵌) |
|------|-----------------|--------------|
| 技术栈统一性 | ❌ Java + Python 双语言维护 | ✅ DJL 方案下可纯 Java（TorchScript 模型） |
| Protobuf 代码生成 | ⚠️ 需要 `.proto` → Java + Python 双端生成 | ✅ 不需要 |
| 调试难度 | ⚠️ Java 端 + Python 端分别调试，跨进程问题定位慢 | ⚠️ JNI 调用栈难以调试（JPype/JNI 崩溃无堆栈） |
| 模型开发迭代 | ✅ Python 生态丰富，直接用 PyTorch/HuggingFace | ⚠️ DJL 生态较 PyTorch 弱，高级操作需绕路 |
| 训练流程集成 | ✅ 训练脚本直接用原生 PyTorch | ❌ DJL 训练能力弱，训练仍需 Python |
| 已有代码复用 | ✅ `model.py` / `selfplay.py` 几乎不用改 | ❌ `MiniResNet` 需重写为 DJL 的 `Block`；`selfplay_game` 无法复用 |
| 社区/文档 | ✅ gRPC + PyTorch 文档极其丰富 | ⚠️ DJL 社区较小，JPype 文档陈旧 |

**结论**: 模式 A 对**现有 Python 代码改动最小**，且保留了 PyTorch 完整生态。模式 B 需要大规模重写 AI 侧代码。

### 2.4 GPU 利用与性能

| 维度 | 模式 A (独立进程) | 模式 B (内嵌) |
|------|-----------------|--------------|
| GPU 独占性 | ✅ Python 进程独占 GPU | ⚠️ JVM + Python 共享进程，GPU 上下文由 JNI 管理 |
| 推理优化 | ✅ TensorRT / ONNX Runtime / AMP 全支持 | ✅ DJL 支持 TensorRT/ONNX Runtime 后端 |
| CPU-GPU 数据传输 | ⚠️ 经 JVM → Protobuf → socket → Python → GPU | ✅ DJL 的 NDArray 可直接 pin 在 GPU 内存 |
| 多 GPU 支持 | ✅ gRPC 负载均衡到多个 Python 进程（多 GPU） | ⚠️ 需额外配置 |
| 批处理灵活性 | ✅ Python 端自由控制 batch 组装策略 | ✅ DJL 的 Predictor 也支持 batch |

**结论**: DJL 在 GPU 推理上理论性能更好（少一次序列化），但 PyTorch 原生推理 + TensorRT 在实际吞吐量上不逊色。

### 2.5 与你当前代码库的兼容性

| 维度 | 模式 A (独立进程) | 模式 B (内嵌) |
|------|-----------------|--------------|
| `model.py` (MiniResNet) | ✅ 直接复用 | ❌ 需用 DJL 重写（~300 行 → ~500 行 DJL Block） |
| `selfplay.py` (MCTS) | ✅ 保留，推理改为 gRPC 调用 | ❌ MCTS 逻辑迁移到 Java（重复实现） |
| `train.py` (训练循环) | ✅ 完全保留 | ❌ 训练仍需 Python，两套推理代码 |
| `PyBridge.java` | ⚠️ 可移除（gRPC 替代） | ⚠️ 可移除（DJL 替代） |
| `MCTSAgent.java` | ✅ 保留，增加 gRPC 推理调用 | ⚠️ 大幅修改，嵌入 DJL Predictor |
| `RuleEncoder.java` | ✅ 不变 | ✅ 不变 |

**结论**: 模式 A 改动量小一个数量级。模式 B 需要重写约 1500+ 行 Python 为 Java/DJL。

---

## 三、混合方案：模式 C

在实际生产环境中，最好的方案通常是**混合架构**：

```
┌──────────────────────────────────────────────────────────────┐
│  ucc-server (Java 21 JVM)                                     │
│                                                                │
│  ┌─────────────────┐                                          │
│  │ Java MCTS Agent  │── 叶节点评估 ──┐                         │
│  │ (轻量启发式)      │               │                         │
│  └─────────────────┘               │                         │
│                                     ▼                          │
│  ┌──────────────────────────────────────────────┐             │
│  │           InferenceRouter (策略路由)           │             │
│  │                                                │             │
│  │  if (TranspositionTable.hit) → 直接返回缓存     │             │
│  │  if (batch.size < 32)        → Java DJL 本地推理│             │
│  │  if (batch.size >= 32)       → gRPC → Python  │             │
│  └──────────────────────────────────────────────┘             │
│                                                                │
│  ┌─────────────────┐     gRPC      ┌──────────────────────┐  │
│  │ DJL + ONNX      │               │ ucc-ai Python         │  │
│  │ (小批量低延迟    │               │ (大批量高吞吐 + 训练)   │  │
│  │  推理fallback)   │               │ GPU 批处理             │  │
│  └─────────────────┘               └──────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

**双重推理路径**：
- **热路径（小 batch）**: Java DJL 加载 ONNX 模型，延迟 < 20μs，用于 TranspositionTable 未命中但 batch 未凑够时的快速评估
- **冷路径（大 batch）**: gRPC → Python → GPU，用于 BatchingEngine 组装的大批量推理

**优点**:
- 兼顾低延迟（DJL 本地）和高吞吐（gRPC 批量）
- 训练流程完全独立，不受推理影响
- 模型更新 → 导出 ONNX → Java 侧热加载 + Python 侧热加载

**缺点**:
- 架构复杂度上升，需维护两套推理后端
- ONNX 模型需与 PyTorch 训练模型保持同步

---

## 四、决策矩阵

| 评估维度 | 权重 | 模式 A (独立进程) | 模式 B (内嵌 DJL) | 模式 C (混合) |
|----------|------|:---:|:---:|:---:|
| 通信延迟 | 中 | 7 | **10** | **9** |
| 吞吐量 (Batch=64) | 高 | **9** | 8 | **9** |
| 故障隔离 | 高 | **10** | 3 | 7 |
| 运维简单度 | 中 | 6 | **9** | 4 |
| 独立扩缩容 | 高 | **10** | 2 | **9** |
| 现有代码改动量 | 高 | **9** | 2 | 6 |
| PyTorch 生态兼容 | 高 | **10** | 3 | 8 |
| 训练流程集成 | 高 | **10** | 2 | **10** |
| GPU 独占利用 | 中 | **9** | 7 | 8 |
| 开发周期 | 高 | **8** | 3 | 5 |
| **加权总分** | | **9.1** | 4.1 | 7.4 |

---

## 五、建议

### 🏆 强烈推荐: 模式 A（独立进程 gRPC）

理由：

1. **最小改动量**：现有 `model.py`/`selfplay.py`/`train.py` 几乎完整保留，只需新增 `inference_server.py`
2. **故障隔离**：GPU OOM / PyTorch 崩溃不影响正在进行的网络对局
3. **独立扩缩容**：将来 GPU 升级（RTX 4090 / A100）只需替换 Python 推理节点，Java 服务端零改动
4. **热更新模型**：训练产出的新权重 → 重启 Python 推理进程（< 5 秒），Java 端通过 gRPC 自动重连
5. **PyTorch 生态完整**：TensorRT、AMP、FSDP 等全部可用，不受 DJL 制约

### 演进路径

```
Phase 1-2: 模式 A（gRPC 独立进程）— 快速落地
     ↓
Phase 3:   运行中收集延迟数据，评估瓶颈是否在 gRPC 通信
     ↓  (如果 gRPC 通信成为瓶颈)
Phase 4:   引入模式 C 的 DJL fallback（仅为 TranspositionTable miss 的小批量服务）
     ↓  (如果 DJL 运维成本过高)
Phase 5:   评估 DJL 收益后决定保留或移除
```

### 不推荐模式 B 的原因

1. 现有 ~2000 行 Python AI 代码需要重写为 Java/DJL，风险高、周期长
2. DJL 的训练能力远弱于 PyTorch，训练仍然需要 Python，导致两份推理代码长期并存
3. JNI 崩溃难以排查（segfault 无 Java 堆栈），生产环境问题定位困难
4. 进程内 GPU 异常会拉垮整个 JVM，所有正在进行的网络对局都将丢失
