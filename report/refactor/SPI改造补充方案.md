# SPI 改造补充方案

> **配套主方案**: [`模块解耦重构方案.md`](./模块解耦重构方案.md)  
> **配套审计报告**: [`重构审计报告.md`](./重构审计报告.md)  
> **版本**: 1.0  
> **日期**: 2026-05-30  
> **目标**: (1) 让 ucc-app 全部走 SPI 接口，与 ucc-core 具体类解耦；(2) 修复 `DocumentInputFilter` 归属问题

---

## 背景

当前状态：

- SPI 接口（`GameSession`、`ReadonlyBoard`、`SessionListener`）已在 ucc-common 中定义
- `GameEngine` 实现了 `GameStateAccessor`，`Board` 实现了 `ReadonlyBoard`，但 `GameEngine` **未实现 `GameSession`**
- ucc-app 的 5 个组件全部直接依赖 `GameEngine` 具体类，未使用任何 SPI 接口
- ucc-app 对 `GameEngine` 的 22 个调用点中，有 7 个是 SPI 接口尚未覆盖的

---

## 一、SPI 接口缺口分析

### GameSession 当前方法 vs ucc-app 实际调用

| 方法 | `GameSession`已定义 | `GameEngine` 已实现 | ucc-app 调用 | 缺口 |
|------|:--:|:--:|:--:|---|
| `makeMove(...)` | ✅ | ✅ | BoardPanel, GameController, ForceMoveHandler | — |
| `isValidMove(...)` | ✅ | ✅ | BoardPanel | — |
| `undoLastMove()` | ✅ | ✅ | GameController, NetGameCoordinator | — |
| `forceApplyMove(...)` | ✅ | ✅ | ForceMoveHandler | — |
| `restart()` | ✅ | ✅ | GameController, NetGameCoordinator | — |
| `isRedTurn()` | ✅ | ✅ | GameController, BoardPanel, ForceMoveHandler | — |
| `getBoardRows()` / `getBoardCols()` | ✅ | ✅(via Board) | BoardPanel(渲染) | — |
| `getPiece()` / `getStack()` / `getStackSize()` | ✅ | ✅(via Board) | BoardPanel(渲染), ForceMoveHandler | — |
| `getMoveHistory()` | ✅ | ✅ | MoveHistoryPanel, ForceMoveHandler, NetGameCoordinator | — |
| `getGameStatus()` | ✅ | ✅ | GameController, GameStateExporter | — |
| `getRuleBoolean()` / `getRuleInt()` / `setRule()` | ✅ | ❌ | BoardPanel, GameController, ForceMoveHandler | 🔴 GameEngine 未代理 |
| `shutdown()` | ✅ | ✅ | — | — |
| **以下是 SPI 未定义但 ucc-app 实际调用的方法** | | | | |
| `getBoard()` → 获取 `ReadonlyBoard` | ❌ | ✅(返回Board) | BoardPanel(渲染), ForceMoveHandler, GameController, NetGameCoordinator | 🔴 需新增 |
| `rebuildBoardForTopBottom()` | ❌ | ✅ | GameController | 🔴 需新增 |
| `rebuildBoardToStep(int)` | ❌ | ✅ | GameController(回放导航) | 🔴 需新增 |
| `setReplayMode(bool,int)` | ❌ | ✅ | MoveHistoryPanel | 🔴 需新增 |
| `isInReplayMode()` | ❌ | ✅ | MoveHistoryPanel | 🔴 需新增 |
| `getCombinedHistory()` | ❌ | ✅ | MoveHistoryPanel | 🔴 需新增 |
| `addRuleChangeToHistory(...)` | ❌ | ✅ | GameController | 🔴 需新增 |
| `needsPromotion(int,int)` | ❌ | ✅ | BoardPanel(兵卒晋升判断) | 🔴 需新增 |
| `getSyncState()` / `loadSyncState(...)` | ❌ | ✅ | NetGameCoordinator | 🟡 已在 `GameStateAccessor` 中 |
| `addGameStateListener(...)` | ❌ | ✅(重载) | GameController, MoveHistoryPanel | 🔴 需新增 |
| `removeGameStateListener(...)` | ❌ | ✅(重载) | — | 🔴 需新增 |
| `saveInitialStateForReplay()` | ❌(在Accessor中) | ✅ | GameStateImporter | 🟡 已在 `GameStateAccessor` 中 |

### 特别说明：`GameEngine.getRulesConfig()` 的引用

`BoardPanel`、`GameController`、`ForceMoveHandler`、`NetGameCoordinator` 都直接调用了 `gameEngine.getRulesConfig()`。这个调用返回的是 `GameRulesConfig` 类型（属于 ucc-core），破坏了抽象。应通过 `GameSession` 的 `getRuleBoolean()`/`getRuleInt()` 等代理方法替代，让 `GameSession` 实现者内部转发到 `GameRulesConfig`。

---

## 二、改造方案

### Phase A: 扩充 `GameSession` 接口（ucc-common，15 分钟）

修改 [`GameSession.java`](ucc-common/src/main/java/io/github/samera2022/chinese_chess/common/spi/GameSession.java)，新增以下方法：

```java
// 新增方法列表：

// 1. 获取只读棋盘（替代 gameEngine.getBoard()）
ReadonlyBoard getBoard();

// 2. 回放/导航
void rebuildBoardToStep(int step);
void setReplayMode(boolean inReplay, int step);
boolean isInReplayMode();

// 3. 组合历史（着法+规则变更）
List<HistoryItem> getCombinedHistory();

// 4. 规则变更记录
void addRuleChangeToHistory(RuleChangeRecord record);

// 5. 兵卒晋升判断
boolean needsPromotion(int row, int col);

// 6. 棋盘模式切换（上下连通）
void rebuildBoardForTopBottom();

// 7. 监听器管理（使用 common.GameStateListener，非内部接口）
void addSessionListener(SessionListener listener);
void removeSessionListener(SessionListener listener);

// 8. 设置规则（代理到 GameRulesConfig）
// 已有的 getRuleBoolean/getRuleInt/setRule 保持不变

// 9. 同步状态（已在 GameStateAccessor 中，但为统一入口也加入 GameSession）
// 可选：getSyncState / loadSyncState ——但这两者有特定 Gson 依赖，
// 保持通过 GameStateAccessor 访问即可。
```

### Phase B: 让 `GameEngine` 实现 `GameSession`（ucc-core，30 分钟）

修改 [`GameEngine.java`](ucc-core/src/main/java/io/github/samera2022/chinese_chess/core/engine/GameEngine.java) 类声明：

```java
// 当前：
public class GameEngine implements GameStateAccessor {

// 改为：
public class GameEngine implements GameStateAccessor, GameSession {
```

新增实现方法（大部分是已有方法的代理/重命名）：

| 接口方法 | 实现方式 |
|---------|---------|
| `getBoard()` | `return this.board;` — Board 已实现 ReadonlyBoard |
| `rebuildBoardToStep(int)` | 已有，直接暴露 |
| `setReplayMode(bool,int)` | 已有，直接暴露 |
| `isInReplayMode()` | 已有，直接暴露 |
| `getCombinedHistory()` | 已有，直接暴露 |
| `addRuleChangeToHistory(RuleChangeRecord)` | 已有，直接暴露 |
| `needsPromotion(int,int)` | 已有，直接暴露 |
| `rebuildBoardForTopBottom()` | 已有，直接暴露 |
| `getRuleBoolean(String)` | 新增代理：`return rulesConfig.getBoolean(key)` |
| `getRuleInt(String)` | 新增代理：`return rulesConfig.getInt(key)` |
| `setRule(String,Object)` | 新增代理：`rulesConfig.set(key, value, ChangeSource.UI)` |
| `addSessionListener(SessionListener)` | 将 `SessionListener` 包装为内部 `GameStateListener`，复用现有的 `commonListenerWrappers` 机制 |
| `removeSessionListener(SessionListener)` | 同上 |

### Phase C: 改造 ucc-app 使用 `GameSession` + `ReadonlyBoard`（ucc-app，2-3 天）

以下是每个组件需要改动的具体清单：

#### C1. `BoardPanel.java`

**改动前**（直接依赖）：
```java
import io.github.samera2022.chinese_chess.core.engine.Board;
import io.github.samera2022.chinese_chess.core.engine.GameEngine;
import io.github.samera2022.chinese_chess.core.rules.MoveValidator;
import io.github.samera2022.chinese_chess.core.rules.RulesConfigProvider;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.common.rules.RuleRegistry;

public class BoardPanel extends JPanel {
    private GameEngine gameEngine;
    private GameRulesConfig rulesConfig = RulesConfigProvider.get();
```

**改动后**（SPI 接口）：
```java
import io.github.samera2022.chinese_chess.common.spi.GameSession;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.GameConfig;

public class BoardPanel extends JPanel {
    private final GameSession session;
    // 移除 rulesConfig / RulesConfigProvider / RuleRegistry / MoveValidator 直接引用
    
    public BoardPanel(GameSession session, ...) {
        this.session = session;
    }
    
    // 渲染时：
    private ReadonlyBoard board() { return session.getBoard(); }
    
    // 走子验证：通过 session.isValidMove() 替代 new MoveValidator(board).isValidMove()
    
    // 规则查询：通过 session.getRuleBoolean("allow_piece_stacking") 替代
    //           rulesConfig.getBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName)
    
    // 晋升判断：通过 session.needsPromotion(row, col)
}
```

**注意**：`RuleRegistry.registryName` 字符串继续可用（它在 ucc-common 中），只是不再通过 `GameRulesConfig` 实例访问，改为通过 `GameSession.getRuleBoolean(String)` 字符串键访问。

#### C2. `GameController.java`

```java
// 改动前：
implements GameEngine.GameStateListener

// 改动后：
implements io.github.samera2022.chinese_chess.common.spi.SessionListener

// 改动前：
private GameEngine gameEngine;
gameEngine.addGameStateListener(this);

// 改动后：
private GameSession session;
session.addSessionListener(this);

// onGameStateChanged(GameEngine.GameState) → onGameStateChanged(GameStatus)
```

#### C3. `MoveHistoryPanel.java`

```java
// 改动前：
implements GameEngine.GameStateListener

// 改动后：
implements io.github.samera2022.chinese_chess.common.spi.SessionListener
```

#### C4. `ForceMoveHandler.java`

```java
// 改动前：
private GameEngine gameEngine;
import io.github.samera2022.chinese_chess.core.engine.Board;
import io.github.samera2022.chinese_chess.core.rules.MoveValidator;

// 改动后：
private GameSession session;
// 移除 Board 和 MoveValidator 直接 import
// Board board = session.getBoard();
// 走子验证通过 session.isValidMove()
```

#### C5. `NetGameCoordinator.java`

```java
// 改动前：
private GameEngine gameEngine;

// 改动后：
private GameSession session;
// gameEngine.getSyncState() 不可用 → 改为通过 GameStateAccessor 接口
// （GameEngine 同时实现 GameSession 和 GameStateAccessor，调用方根据需要选接口）
```

> **权衡**：`getSyncState()`/`loadSyncState()` 是 Gson 特定的序列化方法，不适合放入平台无关的 `GameSession`。建议 `NetGameCoordinator` 对序列化部分额外持有 `GameStateAccessor` 引用（传入同一个 `GameEngine` 实例），而非把序列化塞进 `GameSession`。

#### C6. `ChineseChessFrame.java`

构造函数中改为传入 `GameSession` 而非 `GameEngine` 给子组件。由于 `GameEngine` 同时实现 `GameSession` 和 `GameStateAccessor`，`ChineseChessFrame` 可以直接把同一个实例以不同接口类型传给不同接收方。

---

## 三、`DocumentInputFilter` 问题

### 3.1 当前状况

文件位置：[`ucc-api/src/main/java/io/github/samera2022/chinese_chess/api/filter/DocumentInputFilter.java`](ucc-api/src/main/java/io/github/samera2022/chinese_chess/api/filter/DocumentInputFilter.java)

```java
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
```

**这是一个纯 Swing UI 组件**。它继承 `javax.swing.text.DocumentFilter`，用于约束 `JTextField` 的输入内容。在原项目中被 `RuleSettingsPanel` 的文本字段使用，限制最大堆叠数量等数值输入。

### 3.2 问题

1. **归属错误**：`DocumentInputFilter` 是 Swing 类，应属于 ucc-app（UI 层），而非 ucc-api
2. **孤立文件**：重构后没有任何模块引用它（`RuleSettingsPanel` 移入 ucc-app 时没有带上它）
3. **Android 兼容性破坏**：如果不移走，Swing 的 import 会导致 ucc-api 无法在 Android 上使用

### 3.3 修复步骤

```
1. 将 DocumentInputFilter.java 从 ucc-api/src/.../api/filter/ 
   移动到 ucc-app/src/.../app/ui/filter/
   包名: io.github.samera2022.chinese_chess.api.filter
        → io.github.samera2022.chinese_chess.app.ui.filter

2. 在 RuleSettingsPanel.java 中使用它：
   JTextField field = new JTextField(5);
   ((AbstractDocument) field.getDocument()).setDocumentFilter(
       new DocumentInputFilter() {
           @Override
           public boolean isValidContent(String input) {
               return input.matches("\\d*"); // 只允许数字
           }
       }
   );

3. 删除 ucc-api/src/.../api/filter/ 目录（如果已空）
```

### 3.4 额外确认

当前搜索 ucc-app 全量源码，`DocumentInputFilter` 引用次数为 **0**。这意味着 `RuleSettingsPanel` 中的 `JTextField` 目前没有输入过滤——用户可能输入非法字符。修复后应重新添加过滤逻辑。

---

## 四、改造影响总结

| 改造前 | 改造后 |
|--------|--------|
| ucc-app 直接依赖 4 个 ucc-core 具体类 | ucc-app 只依赖 ucc-common 中的 `GameSession` + `ReadonlyBoard` + `SessionListener` |
| `GameEngine` 未实现 `GameSession` | `GameEngine implements GameSession, GameStateAccessor` |
| `DocumentInputFilter` 在 ucc-api（孤立+错误） | 移至 ucc-app，被 `RuleSettingsPanel` 正常使用 |
| 无法跨语言复用 | `GameSession` 接口是纯 Java 接口，任何 JVM 语言前端均可通过接口调用引擎；非 JVM 语言可通过 IPC（HTTP/gRPC）封装此接口 |

### 改造后的 ucc-app 依赖链

```
ucc-app ──→ ucc-common ( GameSession, ReadonlyBoard, SessionListener, Piece, Move, ... )
ucc-app ──→ ucc-api    ( NetModeController, NetworkSession, GameStateExporter, ... )
ucc-app ──→ ucc-core   ( ⚠ 仅 ChineseChessFrame 中实例化 new GameEngine() 时引用 )
                       ( 子组件全部通过 GameSession 接口交互，不感知 GameEngine 存在 )
```

### 关键收益

- **ucc-app 的核心走子/渲染逻辑不再耦合任何 `ucc-core.*` 包下的类**
- 未来 `ucc-android` 可以直接实现 `GameSession` + `ReadonlyBoard`（或复用 `GameEngine`），UI 层与引擎层完全隔离
- `GameSession` 成为引擎的 **单一入口接口**，不再有"部分走 SPI、部分走具体类"的分裂状态
