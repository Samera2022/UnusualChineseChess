# 架构决策记录 (Architecture Decision Records)

> 记录 ucc-server 解耦与高并发架构开发过程中的关键决策

---

## ADR-1: 训练数据闭环修复 — 方案 B (返回 Worker 列表)

| 项 | 内容 |
|------|------|
| **日期** | 2026-06-01 |
| **状态** | ✅ 已采纳 |
| **决策** | 选择方案 B：`startParallelGames()` 返回 `GameBatchResult(CompletableFuture<Void>, List<SelfPlayWorker>)` |

**备选方案**:
- 方案 A: `SelfPlayWorker implements Callable<TrainingDataCollector>`

**选择理由**:
1. `Runnable` 语义正确："执行一局自博弈"是副作用操作
2. `Callable` 在此场景下是冗余抽象 — `call()` 返回值 = `getCollector()`，无信息增量
3. 方案 B 扩展路径更平滑 — 未来加 `getMoveCount()`/`getGameDuration()` 只需加 getter
4. 方案 B 改动更安全 — `SelfPlayWorker` 源码零改动

**参见**: [`训练数据闭环修复方案对比.md`](训练数据闭环修复方案对比.md)

---

## ADR-2: ucc-server 依赖 ucc-ai

| 项 | 内容 |
|------|------|
| **日期** | 2026-06-01 |
| **状态** | ✅ 已采纳 |
| **决策** | `ucc-server` 保留 `ucc-ai` 依赖 |

**选择理由**:
- `MCTSAgent` 是通用搜索算法，`TrainingDataCollector` 是数据结构容器，应被服务端复用
- 真正的解耦点在**神经网络推理**（通过 gRPC 分离），而非 MCTS 搜索逻辑
- Java 侧负责 MCTS 树搜索，Python 侧仅负责神经网络推理 — 职责清晰

**参见**: [`ucc-server实现审计报告.md`](ucc-server实现审计报告.md) 第四节

---

## ADR-3: 自定义 Empty vs google.protobuf.Empty

| 项 | 内容 |
|------|------|
| **日期** | 2026-06-01 |
| **状态** | ✅ 已采纳 |
| **决策** | 在 `ucc_chess.proto` 中自定义 `message Empty {}`，不依赖 `google/protobuf/empty.proto` |

**选择理由**:
- `protoc` 编译器在 Maven 插件中可能找不到标准 well-known types include 路径
- 自定义 `Empty` 与 `google.protobuf.Empty` 在语义和二进制编码上完全等价
- 避免跨平台（Windows/Linux）protoc include 路径不一致问题

---

## ADR-4: JSON WebSocket vs Protobuf Binary（Phase 5 客户端）

| 项 | 内容 |
|------|------|
| **日期** | 2026-06-02 |
| **状态** | ✅ 已采纳（临时方案） |
| **决策** | Phase 5 客户端先使用 JSON (Gson) over WebSocket Text 帧，后续迁移 Protobuf Binary |

**选择理由**:
- 客户端快速集成 — 现有 `ucc-app` 已使用 Gson，无需引入新 Protobuf 运行时
- JSON 调试友好 — 开发阶段可直观查看消息内容
- 迁移路径清晰 — `WsMessage` proto 已定义，后续仅需改变编解码器

**迁移计划**: Phase 5 后期 → 引入 Protobuf Binary WebSocket 帧，同时保留 JSON 兼容模式

---

## ADR-5: JDK 21 升级 + 虚拟线程

| 项 | 内容 |
|------|------|
| **日期** | 2026-06-01 |
| **状态** | ✅ 已采纳 |
| **决策** | 全项目 JDK 版本从 11 升级到 21，`BatchingEngine` 使用 `Thread.ofVirtual()` |

**选择理由**:
- 虚拟线程在管理 100+ 并发 SelfPlayWorker 时开销极低
- ZGC 在 JDK 21 中生产就绪，适合大内存 (128GB) 场景
- `ReentrantLock` 在虚拟线程下自动 pin 载体线程的问题已有解决方案

**参见**: [`P0线程安全改造方案.md`](P0线程安全改造方案.md) P1-2

---

## ADR-6: P0-2 Board 快照化方案选择

| 项 | 内容 |
|------|------|
| **日期** | 2026-06-01 |
| **状态** | ✅ 已采纳 |
| **决策** | `deepCopy()` 和 `toState()` 通过不可变 `BoardState` 快照实现，而非 `ConcurrentHashMap` 替换 `stacks` |

**选择理由**:
- 不可变快照比替换集合类型侵入性小
- `ConcurrentHashMap` + `ConcurrentLinkedDeque` 需改变大量内部操作逻辑
- `BoardState` 快照在语义上更明确 — "导出一致性快照"

**参见**: [`P0线程安全改造方案.md`](P0线程安全改造方案.md) P0-2 备选方案
