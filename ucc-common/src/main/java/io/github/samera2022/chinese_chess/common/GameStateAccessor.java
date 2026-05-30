package io.github.samera2022.chinese_chess.common;

import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.model.RuleChangeRecord;
import java.util.List;

public interface GameStateAccessor {
    BoardState getBoardState();
    boolean isRedTurn();
    GameStatus getGameStatus();
    List<Move> getMoveHistory();
    List<RuleChangeRecord> getRuleChangeHistory();
    JsonObject getRulesSnapshot();
    void applyRulesSnapshot(JsonObject snapshot);
    boolean makeMove(int fromRow, int fromCol, int toRow, int toCol,
                     Piece.Type promotionType, int selectedStackIndex);
    boolean undoLastMove();
    void addMoveToHistory(Move move);
    void clearMoveHistory();
    void addRuleChangeToHistory(RuleChangeRecord record);
    void clearRuleChangeHistory();
    void setRedTurn(boolean isRedTurn);
    void restart();
    void saveInitialStateForReplay();
    void addGameStateListener(GameStateListener listener);
    void removeGameStateListener(GameStateListener listener);

    /** 清空棋盘所有棋子 */
    void clearBoardPieces();

    /** 向指定位置添加一枚棋子（压入堆栈） */
    void addPieceToBoard(int row, int col, Piece.Type type);

    /** 从 BoardState 快照加载棋盘数据 */
    void loadBoardState(BoardState state);

    /** 通知所有监听器刷新状态（用于导入/同步后） */
    void notifyListenersRefresh();
}
