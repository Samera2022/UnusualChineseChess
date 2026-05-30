package io.github.samera2022.chinese_chess.common.spi;

import io.github.samera2022.chinese_chess.common.GameStatus;
import io.github.samera2022.chinese_chess.common.model.HistoryItem;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.model.RuleChangeRecord;
import java.util.List;

public interface GameSession {
    boolean makeMove(int fromRow, int fromCol, int toRow, int toCol);
    boolean makeMove(int fromRow, int fromCol, int toRow, int toCol,
                     Piece.Type promotionType, int selectedStackIndex);
    boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol);
    boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, int selectedStackIndex);
    boolean undoLastMove();
    boolean forceApplyMove(int fromRow, int fromCol, int toRow, int toCol,
                           Piece.Type promotionType, int selectedStackIndex);
    Piece getPiece(int row, int col);
    List<Piece> getStack(int row, int col);
    int getStackSize(int row, int col);
    int getBoardRows();
    int getBoardCols();
    boolean isRedTurn();
    GameStatus getGameStatus();
    List<Move> getMoveHistory();
    void restart();
    boolean getRuleBoolean(String ruleKey);
    int getRuleInt(String ruleKey);
    void setRule(String ruleKey, Object value);
    void shutdown();

    // === 新增 SPI 方法 ===

    /** 获取只读棋盘 */
    ReadonlyBoard getBoard();

    /** 回放导航：将棋盘重建到指定步数 */
    void rebuildBoardToStep(int step);

    /** 设置回放模式 */
    void setReplayMode(boolean inReplay, int step);

    /** 查询是否处于回放模式 */
    boolean isInReplayMode();

    /** 组合历史（着法 + 规则变更） */
    List<HistoryItem> getCombinedHistory();

    /** 添加规则变更记录 */
    void addRuleChangeToHistory(RuleChangeRecord record);

    /** 判断指定位置的兵卒是否需要晋升 */
    boolean needsPromotion(int row, int col);

    /** 棋盘模式切换（顶底翻转） */
    void rebuildBoardForTopBottom();

    /** 注册会话监听器 */
    void addSessionListener(SessionListener listener);

    /** 移除会话监听器 */
    void removeSessionListener(SessionListener listener);
}
