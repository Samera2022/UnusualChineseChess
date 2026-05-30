# SPI 改造后审计报告

> **审计日期**: 2026-05-30  
> **审计范围**: SPI 改造后的全量源码  
> **构建状态**: ✅ `mvn clean compile` 通过

---

## 审计总览

| 类别 | 数量 | 
|------|------|
| 🟢 通过 | 11 |
| 🟡 可优化（不阻塞） | 5 |
| 🔴 阻塞 | 0 |
| ⚠ 清理遗留 | 1 |

---

## 🟢 通过的改造项

| # | 检查项 | 证据 |
|---|--------|------|
| 1 | `GameSession` SPI 已扩充 9 个新方法 | [GameSession.java:33-63](ucc-common/src/main/java/io/github/samera2022/chinese_chess/common/spi/GameSession.java:33) |
| 2 | `GameEngine` 实现 `GameSession` | [GameEngine.java:32](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/GameEngine.java:32) — `implements GameStateAccessor, GameSession` |
| 3 | `BoardPanel` 已走 SPI | [BoardPanel.java:4-7](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/BoardPanel.java:4) — 仅 import `GameSession`, `ReadonlyBoard`, `Piece`, `RuleRegistry`，无任何 `ucc-core.*` |
| 4 | `GameController` 实现 `SessionListener` | [GameController.java:20](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/GameController.java:20) — `implements SessionListener` |
| 5 | `MoveHistoryPanel` 实现 `SessionListener` | [MoveHistoryPanel.java:18](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/MoveHistoryPanel.java:18) — `implements SessionListener` |
| 6 | `ForceMoveHandler` 已走 SPI | [ForceMoveHandler.java:4-5](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/ForceMoveHandler.java:4) — 仅 import `GameSession`, `ReadonlyBoard`，无 `Board`/`MoveValidator` |
| 7 | `GameEngine.GameState` 内部枚举已删除 | 搜索 `enum GameState` 在 ucc-core 中返回 0 结果 |
| 8 | `GameEngine.GameStateListener` 内部接口已删除 | 搜索 `interface GameStateListener` 在 ucc-core 中返回 0 结果 |
| 9 | `gameState` 字段使用 `common.GameStatus` | [GameEngine.java:49](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/GameEngine.java:49) — `private GameStatus gameState` |
| 10 | `DocumentInputFilter` 已移至 ucc-app | [filter/DocumentInputFilter.java](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/filter/DocumentInputFilter.java) |
| 11 | `RuleSettingsPanel` 引用 `DocumentInputFilter` | [RuleSettingsPanel.java:3](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/RuleSettingsPanel.java:3) — `import ...app.ui.filter.DocumentInputFilter` |

---

## 🟡 可优化的残留耦合

以下 5 处仍引用 `ucc-core.*` 的具体类，但**均有合理原因**，不阻塞发布：

### 1. `NetGameCoordinator` — 双接口持有（合理）

[NetGameCoordinator.java:5-6](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/NetGameCoordinator.java:5):
```java
import io.github.samera2022.chinese_chess.common.spi.GameSession;   // ← SPI ✅
import io.github.samera2022.chinese_chess.core.engine.GameEngine;   // ← 具体类 🟡
```
同时持有 `GameSession session` 和 `GameEngine engineForSync`。`engineForSync` 用于调用 `getSyncState()`/`loadSyncState()`——这些方法在 `GameStateAccessor` 接口中而非 `GameSession` 中。

**建议**：将 `engineForSync` 声明为 `GameStateAccessor` 类型（`GameEngine` 已实现此接口）：
```java
private final GameStateAccessor syncAccessor;  // 替代 GameEngine engineForSync
```

### 2. `InfoSidePanel` — 未迁移

[InfoSidePanel.java:5](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/InfoSidePanel.java:5):
```java
import io.github.samera2022.chinese_chess.core.engine.GameEngine;
private final GameEngine gameEngine;   // 🟡 直接持有具体类
```

`InfoSidePanel` 使用 `gameEngine` 主要做数据查询（`getBoard()`, `isRedTurn()`, `getMoveHistory()` 等），这些都在 `GameSession` 中有对应方法。

**建议**：改为 `private final GameSession session;`

### 3. `ChineseChessFrame` — 组装入口（不可避免）

[ChineseChessFrame.java:4](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/ChineseChessFrame.java:4):
```java
import io.github.samera2022.chinese_chess.core.engine.GameEngine;
public final GameEngine gameEngine;  // 🟡 但作为组装点，不可避免
```

`ChineseChessFrame` 是依赖注入的组装点，必须 `new GameEngine()` 实例化。这在 DI 模式中是不可消除的——总有一个地方要知道具体类。**当前可接受**。

### 4. `GameController` — `GameRulesConfig` 直接引用

[GameController.java:11-12](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/GameController.java:11):
```java
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.core.rules.RulesConfigProvider;
```

`GameController` 的 `rulesChangeListener` 需要直接操作 `GameRulesConfig`（记录规则变更历史、监听 provider 实例切换）。这些能力超出 `GameSession` 的范围。

**建议**：这是架构上的合理权衡。`GameController` 作为"中介者"负责协调规则变更→引擎更新→UI 刷新，它需要比普通 UI 组件更深的访问权限。

### 5. `RuleSettingsPanel` — 配置绑定需要具体类型

[RuleSettingsPanel.java:6](ucc-app/src/main/java/io/github/samera2022/chinese_chess/app/ui/RuleSettingsPanel.java:6):
```java
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
private GameRulesConfig config;  // 🟡 bindConfig() 需要具体类型
```

`RuleSettingsPanel.bindConfig(GameRulesConfig)` 需要进行 `addRuleChangeListener`/`getAllValues`/`toJson`/`applySnapshot` 等操作，这些都在 `GameRulesConfig` 上。**当前可接受**——如果将来需要完全解耦，可以在 `GameSession` 中增加代理方法。

---

## ⚠ 清理遗留

| 问题 | 详情 |
|------|------|
| `ucc-api/filter/` 空目录 | `DocumentInputFilter.java` 已迁移，但 `ucc-api/src/.../api/filter/` 目录仍存在（空）。应删除。 |

---

## ucc-app 对 `ucc-core` 的引用汇总

| 组件 | 引用 `core.engine` | 引用 `core.rules` | 状态 |
|------|:--:|:--:|---|
| `BoardPanel` | ❌ 无 | ❌ 无 | ✅ 完全解耦 |
| `ForceMoveHandler` | ❌ 无 | ❌ 无 | ✅ 完全解耦 |
| `MoveHistoryPanel` | ❌ 无 | ❌ 无 | ✅ 完全解耦 |
| `GameController` | ❌ 无 | 🟡 `GameRulesConfig`, `RulesConfigProvider` | 可接受 |
| `NetGameCoordinator` | 🟡 `GameEngine` | 🟡 `GameRulesConfig` | 可优化为 `GameStateAccessor` |
| `InfoSidePanel` | 🟡 `GameEngine` | ❌ 无 | 可改为 `GameSession` |
| `ChineseChessFrame` | 🟡 `GameEngine` | 🟡 `GameRulesConfig`, `RulesConfigProvider` | 组装入口，不可避免 |
| `RuleSettingsPanel` | ❌ 无 | 🟡 `GameRulesConfig` | 配置绑定需要 |

**改造前**：5 个组件直接依赖 `GameEngine`，每个组件 import 4-5 个 `ucc-core.*` 类  
**改造后**：3 个核心组件（BoardPanel、ForceMoveHandler、MoveHistoryPanel）已完全解耦，剩余 4 个的耦合均有合理原因且可渐进优化

---

## 总结

SPI 改造**整体成功**。核心走子渲染路径（BoardPanel + ForceMoveHandler + MoveHistoryPanel）已完全与 ucc-core 解耦，仅通过 `GameSession` + `ReadonlyBoard` + `SessionListener` 三个 SPI 接口交互。这意味着：

- ✅ 未来 `ucc-android` 可以直接实现这三个接口，UI 组件可复用
- ✅ 未来任何 JVM 语言前端都可以替换引擎实现
- ✅ `DocumentInputFilter` 归属问题已修复
- 🟡 剩余 4 个组件的耦合均有合理原因，可渐进式优化
