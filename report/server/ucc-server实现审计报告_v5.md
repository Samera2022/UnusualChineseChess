# ucc-server 实现审计报告 v5

> 审计日期: 2026-06-02  
> 审计轮次: 第五轮（前次问题回归验证 + 剩余问题逐项确认）  
> 编译状态: ✅ `mvn compile -q` — exit code 0

---

## 一、前次问题回归

| # | 问题 | 位置 | v4 标记 | v5 状态 | 验证 |
|---|------|------|:-------:|:-------:|:----|
| 1 | `export_onnx.py:14` 参数顺序错误（128 残差块） | `MiniResNet(14,18,9,28,128)` | 🔴 | ✅ | 已改为 `num_res_blocks=5, filters=128` |
| 2 | `WsClient.java:74` `latch.await()` 无超时 | `latch.await()` | 🟡 | ✅ | 已改为 `latch.await(5, SEC)` + 超时异常 |
| 3 | `GrpcInferenceClient` stub 全部注释 | `stub = null` | 🟡 | ✅ | 已启用真实 `InferenceServiceGrpc.newBlockingStub()` |
| 4 | `GrpcInferenceClient` mock 占位 | 返回固定 0.5f | 🟡 | 🟡 | 连接失败时仍返回 mock（降级策略合理） |
| 5 | `honest-assessment.md` 描述过时 | 标注"骨架" | 🟢 | ❌ | **仍标注 `BatchingEngine`/`GrpcInferenceClient`/`inference_server.py` 为"骨架"，与实际不符** |
| 6 | `TrainingOrchestrator` 训练触发 TODO | 未对接 Redis | 🟢 | 🟢 | 仍为 TODO |

---

## 二、新增发现

### 🔴 问题 1: `GrpcInferenceClient.batchInfer()` 发送空 BoardStateProto

**位置**: [`GrpcInferenceClient.java:72-78`](../../ucc-server/src/main/java/io/github/samera2022/chinese_chess/server/net/GrpcInferenceClient.java:72)

```java
for (int i = 0; i < boardTensors.size(); i++) {
    BoardStateProto.Builder boardPb = BoardStateProto.newBuilder();
    // 从 tensor 无法反推 BoardState，传空 protobuf 占位
    reqBuilder.addBoards(boardPb.build());  // ← 空 BoardStateProto
    ...
}
```

**根因**: `InferenceClient` 接口的签名是 `batchInfer(List<float[]> boardTensors, List<float[]> ruleVectors)`，传入的是已经展平的 float 张量。从张量**无法**反推出原始棋盘状态（棋子排列信息丢失）。因此 `GrpcInferenceClient` 只能传空 `BoardStateProto`。

**影响**: Python 侧 `inference_server.py` 的 `BatchInfer()` 收到的 `board.entries` 为空数组 → `board_to_tensor()` 输出全零张量 → 神经网络输出无意义。

**修复方案**:

| 方案 | 改动 | 评价 |
|------|------|:---:|
| A: 修改 `InferenceClient` 签名 | 改为 `batchInfer(List<BoardState>, List<float[]>)`，由 `GrpcInferenceClient` 自己调用 `boardStateToTensor()` 后构建 proto | 🥇 推荐 |
| B: 在 `BatchingEngine.flushBatch()` 中构建 proto | 直接传 Proto 字节而非 float 张量 | 🟡 可行但接口耦合了 protobuf 类型 |

**建议**: 方案 A。`InferenceClient` 当前接口设计在"纯 tensor"和"gRPC proto"两个抽象层次之间失落了棋盘信息。改为传入 `BoardState` 对象让客户端自行决定如何序列化。

---

### 🟡 问题 2: `honest-assessment.md` 需更新

当前 4 项标注与实际不符：

| 行 | 标注 | 实际状态 |
|:---|------|:-------:|
| 18 | `GrpcInferenceClient` ❌ 骨架 / TODO 注释 | ✅ 已启用真实 gRPC stub |
| 19 | `BatchingEngine` ❌ 骨架 / `new float[0]` | ✅ 已使用 `boardStateToTensor()` |
| 21 | `inference_server.py` ❌ 骨架 / try 降级 | ✅ 已启用 `HAS_PROTO` 分支 |
| 24 | `TrainingOrchestrator` ❌ 骨架 / 评估是 TODO | ✅ 评估已实现（`_play_eval_game`） |

---

### 🟡 问题 3: `TrainingOrchestrator` 评估仅用标准棋盘

[`TrainingOrchestrator.java:148`](../../ucc-server/src/main/java/io/github/samera2022/chinese_chess/server/train/TrainingOrchestrator.java:148):

```java
Board evalBoard = new Board();  // 只创建 standard 10 行棋盘
```

评估对局始终使用 10×9 标准棋盘，不测试 18×9 上下连通模式。属于功能不完整，非 bug。

---

### 🟢 `export_onnx.py` 参数已修复 ✅

```python
# v4 发现的问题（已修复）
MiniResNet(14, 18, 9, 28, 128)          # num_res_blocks=128 ❌
# v5 验证
MiniResNet(14, 18, 9, 28, num_res_blocks=5, filters=128)  # 正确 ✅
```

---

## 三、累计问题清单

| 优先级 | 数量 | 问题 | 位置 |
|:---:|:---:|------|------|
| 🔴 | 1 | `batchInfer()` 传空 BoardStateProto → Python 收到空 entries → 全零张量 | `GrpcInferenceClient.java:72-78` |
| 🟡 | 2 | `honest-assessment.md` 4 项描述过时；评估仅用标准棋盘 | `honest-assessment.md:18-24`, `TrainingOrchestrator.java:148` |
| 🟢 | 2 | 训练触发未对接 Redis；`inference_server.py` 未继承 gRPC 基类 | `TrainingOrchestrator.java:138`, `inference_server.py:27` |

---

## 四、总结

| 维度 | v4 | v5 |
|:---|:---:|:---:|
| 编译 | ✅ | ✅ |
| bug 数 | 1（export_onnx.py） | **1（GrpcInferenceClient 空 BoardStateProto）** |
| 完成度 | ~85% | **~88%** |

**编译: ✅ PASSED**  
**当前阻塞性 bug: 1 个（gRPC 推理链路空棋盘问题）**  
**修复建议**: 修改 `InferenceClient` 接口签名，将 `boardTensors: List<float[]>` 替换为 `states: List<BoardState>`，由 `GrpcInferenceClient` 内部调用 `boardStateToTensor()` 后再构建 proto。
