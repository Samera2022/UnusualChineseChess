# ucc-core 高并发支持评估与 ucc-server 解耦分析

> 评估日期: 2026-06-01  
> 评估范围: ucc-core 模块及全项目架构

---

## 一、当前架构总览

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  ucc-app    │────▶│   ucc-api    │────▶│  ucc-core   │
│  (Swing UI) │     │ (网络/序列化) │     │ (核心引擎)   │
└─────────────┘     └──────────────┘     └─────────────┘
                            │                     │
                            ▼                     ▼
                    ┌──────────────┐     ┌─────────────┐
                    │  ucc-common  │◀────│   ucc-ai    │
                    │ (模型/SPI)   │     │ (AI/训练)   │
                    └──────────────┘     └─────────────┘
```

### 模块职责

| 模块 | 职责 | 关键类 |
|------|------|--------|
| `ucc-common` | 数据模型、SPI 接口定义 | `Piece`, `Move`, `BoardState`, `GameSession`, `SimulationContext`, `ReadonlyBoard` |
| `ucc-core` | 核心引擎实现 | `Board`, `GameEngine`, `SimulationBoard`, `MoveValidator`, `CheckDetector`, `GameRulesConfig` |
| `ucc-api` | 网络传输与序列化 | `NetworkSession`, `NetModeController`, `GameStateSerializer` |
| `ucc-ai` | MCTS + 神经网络桥接 | `MCTSAgent`, `PyBridge`, `PyTorchBridge`, `TrainingDataCollector` |
| `ucc-app` | Swing GUI 桌面应用 | `BoardPanel`, `GameController`, `NetGameCoordinator` |

---

## 二、ucc-core 线程安全逐类审计

### 2.1 `Board` — ❌ 完全非线程安全

[`Board.java`](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/Board.java:14)

| 问题点 | 位置 | 严重度 |
|--------|------|--------|
| `turn` 字段无 `volatile`，包级私有被 `SimulationBoard` 直接访问 | [:25](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/Board.java:25) | 🔴 高 |
| `pushToStack`/`popTop`/`removeFromStack` 直接修改 `redPieces`/`blackPieces`/`stacks`，无同步 | [:201-241](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/Board.java:201) | 🔴 高 |
| `deepCopy()` 在遍历 `stacks` 时无快照保护，并发修改会导致 `ConcurrentModificationException` | [:306-318](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/Board.java:306) | 🔴 高 |
| `toState()` 遍历 `stacks` 的 `entrySet()`，同样无保护 | [:348-363](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/Board.java:348) | 🟡 中 |
| `getRedKing()`/`getBlackKing()` 用 `stream().filter()` 遍历 List，并发修改会异常 | [:285-291](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/Board.java:285) | 🟡 中 |

**结论**: `Board` 设计为单线程使用。多线程共享同一 `Board` 实例将导致数据竞态和 `ConcurrentModificationException`。

### 2.2 `GameEngine` — ❌ 非线程安全

[`GameEngine.java`](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/GameEngine.java:32)

| 问题点 | 位置 | 严重度 |
|--------|------|--------|
| `makeMove()` 修改 `moveHistory`/`isRedTurn`/`board`，无锁保护 | [:116-248](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/GameEngine.java:116) | 🔴 高 |
| `undoLastMove()` 修改 `moveHistory`/`ruleChangeHistory`，无锁保护 | [:392-411](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/GameEngine.java:392) | 🔴 高 |
| `addGameStateListener`/`removeGameStateListener` 修改 `listeners` 无同步，与 `notifyGameStateChanged()` 中的迭代存在竞态 | [:610-617](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/GameEngine.java:610) | 🔴 高 |
| `providerListener` lambda 中直接修改 `this.rulesConfig` 和 `this.validator`，来自 `RulesConfigProvider` 的回调线程不确定 | [:38-45](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/GameEngine.java:38) | 🟡 中 |
| `loadSyncState()` 修改内部状态（`savedInitialBoard`、`isRedTurn` 等），来自网络线程的回调 | [:744-764](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/GameEngine.java:744) | 🟡 中 |

**结论**: `GameEngine` 假定所有调用来自单一事件线程（Swing EDT）。在服务器端多线程环境下直接使用会崩溃。

### 2.3 `SimulationBoard` — ⚠️ 设计为单线程，实际安全

[`SimulationBoard.java`](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/SimulationBoard.java:25)

继承 `Board` 的全部非线程安全特性，但设计意图是每个 MCTS 搜索树拥有独立实例。`fork()` 方法通过深拷贝创建隔离副本。

| 问题点 | 位置 | 严重度 |
|--------|------|--------|
| 继承 `Board` 的非线程安全 | 全类 | 🟢 低（设计如此） |
| 单实例内 `simulateMove`/`simulateUndo` 交替调用，不可并发 | [:71-140](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/SimulationBoard.java:71) | 🟢 低（设计如此） |

**结论**: 当前设计合理。若要支持并行 MCTS（如多线程根并行化），需确保每个线程持有独立 `fork()` 副本。

### 2.4 `GameRulesConfig` — ⚠️ 部分线程安全

[`GameRulesConfig.java`](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/rules/GameRulesConfig.java:24)

| 问题点 | 位置 | 严重度 |
|--------|------|--------|
| 公共读写方法均有 `synchronized`，基本安全 | [:55-109](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/rules/GameRulesConfig.java:55) | 🟢 低 |
| **每个实例创建两个 `ExecutorService`（守护线程池）**，若创建大量实例且未调用 `shutdown()` 会泄漏线程 | [:40-49](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/rules/GameRulesConfig.java:40) | 🔴 高 |
| `notifyRuleChange()` 内 `notifyExecutor.submit()` 再 `listenerExecutor.submit()` 形成双重异步，通知延迟不可控 | [:152-170](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/rules/GameRulesConfig.java:152) | 🟡 中 |
| `enforceRuleConsistency()` O(n²) 最坏复杂度，每条规则检查所有规则依赖 | [:111-134](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/rules/GameRulesConfig.java:111) | 🟡 中 |

**关键问题**: 高并发训练场景中，若每局游戏创建新的 `GameRulesConfig` 实例，会不断创建线程池。`RulesConfigProvider.replace()` 会尝试 shutdown 旧实例，但若调用者直接 `new GameRulesConfig()` 而不通过 Provider，线程会泄漏。

### 2.5 `CheckDetector` — ⚠️ 状态可变但非线程安全

[`CheckDetector.java`](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/rules/CheckDetector.java:9)

`canEscapeCheck()` 方法直接修改 `board` 状态（`removePiece` → `pushToStack` → `clearStack` 恢复），这是**有副作用的临时修改**。若多线程共享同一 `CheckDetector`/`Board`，将互相干扰。

### 2.6 `MoveValidator` — ⚠️ 无状态但依赖可变引用

[`MoveValidator.java`](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/rules/MoveValidator.java:7)

`MoveValidator` 本身无内部可变状态（仅持有 `board` 和 `rulesConfig` 引用），但 `rulesConfig` 可通过 `setRulesConfig()` 运行时替换，替换操作无同步保护。

---

## 三、网络层并发评估

### 3.1 `NetworkSession` — ⚠️ P2P 架构，不支持多客户端

[`NetworkSession.java`](ucc-api/src/main/java/io/github/samera2022/chinese_chess/api/net/NetworkSession.java:18)

| 特性 | 现状 | 评价 |
|------|------|------|
| 连接模型 | 1 对 1 P2P（ServerSocket accept 单连接） | ❌ 无法支持 >2 人 |
| 协议 | 文本行协议 + JSON + HMAC 签名 | ⚠️ 可用但非标准 |
| 线程模型 | 1 个 IO 读线程 + 调用线程写 | ⚠️ 简陋 |
| 断线重连 | HELLO 令牌 + SYNC_GAME_STATE | 🟢 已实现 |
| 多并发行 | 不支持 | ❌ |

### 3.2 SPI 接口 — 有设计但无实现

[`MatchmakingService.java`](ucc-common/src/main/java/io/github/samera2022/chinese_chess/common/spi/MatchmakingService.java:5)  
[`MatchSession.java`](ucc-common/src/main/java/io/github/samera2022/chinese_chess/common/spi/MatchSession.java:6)  
[`MessageProtocol.java`](ucc-common/src/main/java/io/github/samera2022/chinese_chess/common/spi/MessageProtocol.java:3)

三个接口仅定义了方法签名和命令常量，**整个项目中无任何实现类**。说明架构上预留了服务端扩展点，但尚未落地。

---

## 四、训练流程并发评估

### 4.1 Python 训练 — 单进程单线程

[`train.py`](ucc-ai/python/train.py:486) 中的 `train_main()`:

```python
for iteration in range(num_iterations):
    for g in range(games_per_iteration):    # 顺序执行每局自我对弈
        trajectory, final_value = selfplay_game(...)
    batch = buffer.sample(batch_size)       # 单 batch 训练
    train_step(model, batch, optimizer)
```

- 每局自我对弈**顺序**执行，无法利用多核 CPU
- 同一时刻只有一个 `PyBridge` Java 进程（长驻管道）
- MCTS 搜索树内**不使用**神经网络评估（全部走 mock），但树搜索本身是单线程的

### 4.2 Java MCTS — 单线程

[`MCTSAgent.java`](ucc-ai/src/main/java/io/github/samera2022/chinese_chess/ai/MCTSAgent.java:90) 中的 `findBestMove()` 是标准的单线程 Select→Expand→Simulate→Backprop 循环。

---

## 五、高并发场景需求与当前差距

### 5.1 网络对战场景

| 需求 | 当前状态 | 差距 |
|------|----------|------|
| 大厅/房间列表 | ❌ 无 | 需服务端维护 |
| 自动匹配 | ❌ SPI 接口仅存根 | 需实现 MatchmakingService |
| 多局并发 | ❌ P2P 单连接 | 需服务端管理多 Session |
| 观战 | ❌ 无 | 需广播机制 |
| 断线重连 | 🟢 已实现（P2P） | 服务端需保存对局状态 |
| 安全/反作弊 | ⚠️ HMAC 签名 | 需服务端校验每步着法 |
| NAT 穿透 | ❌ 无 | 需 relay 或 STUN |

### 5.2 高并发训练场景

| 需求 | 当前状态 | 差距 |
|------|----------|------|
| 并行自我对弈 | ❌ 单线程 | 需多进程/多 `PyBridge` 实例 |
| 分布式 replay buffer | ❌ 内存 buffer | 需 Redis/共享存储 |
| GPU 利用率 | ⚠️ 单模型 | 可 batch 推理但数据生成是瓶颈 |
| 训练容错 | ❌ 无 | 需 checkpoint/恢复机制 |
| 多规则并行训练 | ❌ 顺序课程学习 | 可按规则分片并行 |

---

## 六、是否需要解耦出 ucc-server？

### ✅ **结论：强烈建议解耦出 ucc-server 模块**

理由如下：

### 6.1 架构必然性

当前 P2P 架构仅适用于局域网两人对弈。一旦需要：
- 互联网对战（NAT 穿透 / relay）
- 匹配系统
- 多局并发
- 观战/回放服务
- 集中式训练调度

就必须有一个中心化服务端。

### 6.2 SPI 已预留扩展点

`ucc-common` 中已定义但未实现的接口：

```
MatchmakingService   → 匹配服务
MatchSession         → 对局会话（服务端视角）
MessageProtocol      → 命令常量定义
GameSession          → 由 GameEngine 实现，可直接复用
SessionListener      → 会话事件监听
```

这些接口表明项目设计阶段已考虑到服务端解耦。

### 6.3 建议的 ucc-server 模块设计

```
ucc-server/
├── pom.xml
├── src/main/java/.../server/
│   ├── UCCServer.java              # 启动入口（Netty/WebSocket）
│   ├── session/
│   │   ├── ServerGameSession.java  # 服务端对局会话（包装 GameEngine）
│   │   └── SessionManager.java     # 会话生命周期管理
│   ├── match/
│   │   ├── MatchmakingServiceImpl.java  # 实现 MatchmakingService
│   │   ├── LobbyManager.java
│   │   └── EloRatingService.java
│   ├── net/
│   │   ├── MessageDispatcher.java  # 协议路由
│   │   ├── ClientConnection.java   # 客户端连接封装
│   │   └── RelayHandler.java       # NAT 中继
│   └── train/
│       ├── TrainingOrchestrator.java # 分布式训练编排
│       └── ReplayBufferServer.java   # 集中式经验回放
```

### 6.4 解耦后的模块依赖关系

```
ucc-common ◀──── ucc-core ◀──── ucc-server
    ▲              ▲                │
    │              │                │
    └──── ucc-api ◀┘                │
                                    │
                              ucc-app (客户端)
                              ucc-ai  (训练客户端)
```

- `ucc-core` **不依赖** `ucc-server`
- `ucc-server` **依赖** `ucc-core`（复用 `GameEngine`、`Board` 等）
- 客户端（`ucc-app`）通过 `ucc-api` 与服务端通信
- `ucc-ai` 训练可作为 `ucc-server` 的插件或独立部署

### 6.5 线程安全改造要点

在解耦 `ucc-server` 之前或同时，`ucc-core` 需要以下改造：

| 改造项 | 优先级 | 说明 |
|--------|--------|------|
| `GameEngine` 加可重入锁 | 🔴 P0 | `makeMove()`/`undoLastMove()`/`restart()` 加 `synchronized` |
| `Board.deepCopy()` 加同步 | 🔴 P0 | 或改为从 `BoardState` 快照构造 |
| `GameEngine` 监听器列表改用 `CopyOnWriteArrayList` | 🔴 P0 | 替代普通 `ArrayList` |
| `GameRulesConfig` 线程池改为静态共享 | 🟡 P1 | 避免每实例创建线程池 |
| `CheckDetector.canEscapeCheck()` 改为在副本上操作 | 🟡 P1 | 避免副作用 |
| `SimulationBoard` 标注 `@NotThreadSafe` | 🟢 P2 | 文档化设计意图 |

---

## 七、总结

### 当前 ucc-core 能力矩阵

| 维度 | 评分 | 说明 |
|------|------|------|
| 单机对弈（人 vs 人） | 🟢 成熟 | GUI 完整支持 |
| 单机对弈（人 vs AI） | 🟢 成熟 | MCTS + PyTorch |
| 局域网 P2P 对战 | 🟡 可用 | 需手动输入 IP |
| 互联网对战 | 🔴 不可用 | 无服务端 |
| 并行自我对弈训练 | 🔴 不可用 | 单线程 |
| 分布式训练 | 🔴 不可用 | 无基础设施 |
| 匹配/天梯 | 🔴 不可用 | SPI 存根 |

### 最终建议

1. **立即创建 `ucc-server` 模块**，基于 Netty 或 Vert.x 实现 WebSocket 服务端
2. **对 `ucc-core` 进行线程安全改造**（P0 项优先）
3. **实现 `MatchmakingService` 和 `MatchSession` 接口**
4. **训练并行化**：`ucc-server` 内置训练编排器，管理多个 `PyBridge` 工作进程
5. 保持 `ucc-core` 作为纯引擎库，不含任何网络/通信逻辑
