package io.github.samera2022.chinese_chess.engine;

import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.model.Move;
import io.github.samera2022.chinese_chess.model.Piece;

import io.github.samera2022.chinese_chess.rules.CheckDetector;
import io.github.samera2022.chinese_chess.rules.MoveValidator;
import io.github.samera2022.chinese_chess.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.rules.RuleConstants;

import java.util.*;

/**
 * 游戏引擎 - 管理游戏状态和逻辑
 */
public class GameEngine {
    private Board board;
    private MoveValidator validator;
    private CheckDetector checkDetector;
    private GameRulesConfig rulesConfig;
    private List<Move> moveHistory;
    private boolean isRedTurn;
    private GameState gameState;
    private List<GameStateListener> listeners;

    // 回放功能支持
    private Board savedInitialBoard = null;
    private boolean savedInitialIsRedTurn = true;
    private boolean isInReplayMode = false;
    private int currentReplayStep = -1;


    public enum GameState {
        RUNNING,
        RED_CHECKMATE,
        BLACK_CHECKMATE,
        DRAW
    }

    public interface GameStateListener {
        void onGameStateChanged(GameState newState);
        void onMoveExecuted(Move move);
    }

    public GameEngine() {
        this.rulesConfig = new GameRulesConfig();
        this.board = new Board();
        this.validator = new MoveValidator(board);
        this.validator.setRulesConfig(rulesConfig);
        this.checkDetector = new CheckDetector(board, validator);
        this.moveHistory = new ArrayList<>();
        this.isRedTurn = true;
        this.gameState = GameState.RUNNING;
        this.listeners = new ArrayList<>();
    }

    /**
     * 尝试执行一步棋
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        return makeMove(fromRow, fromCol, toRow, toCol, null);
    }

    /**
     * 尝试执行一步棋（带晋升选项）
     * @param promotionType 如果兵到达底线，晋升为此类型（null表示不晋升或不需要晋升）
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol, Piece.Type promotionType) {
        if (gameState != GameState.RUNNING) {
            return false;
        }

        // 如果处于回放模式，需要截断后续的着法记录
        if (isInReplayMode && currentReplayStep >= 0 && currentReplayStep < moveHistory.size()) {
            // 保留前 currentReplayStep 步，删除后面的
            moveHistory = new ArrayList<>(moveHistory.subList(0, currentReplayStep));
            // 退出回放模式
            isInReplayMode = false;
            currentReplayStep = -1;
        }

        Piece piece = board.getPiece(fromRow, fromCol);
        if (piece == null) {
            return false;
        }

        // 检查是否是当前玩家的棋子
        if (piece.isRed() != isRedTurn) {
            return false;
        }

        // 验证着法合法性
        if (!validator.isValidMove(fromRow, fromCol, toRow, toCol)) {
            return false;
        }

        // 执行移动
        Piece capturedPiece = board.getPiece(toRow, toCol);

        // 判断是否为堆叠移动
        boolean isStackingMove = false;
        if (rulesConfig.getBoolean(RuleConstants.ALLOW_PIECE_STACKING) && rulesConfig.getInt(RuleConstants.MAX_STACKING_COUNT) > 1 && capturedPiece != null &&
            capturedPiece.isRed() == piece.isRed()) {
            int stackSize = board.getStackSize(toRow, toCol);
            if (stackSize < rulesConfig.getInt(RuleConstants.MAX_STACKING_COUNT)) {
                isStackingMove = true;
                capturedPiece = null; // 堆叠时不吃子，只堆叠
            }
        }

        if (capturedPiece != null && !isStackingMove) {
            board.removePiece(toRow, toCol);
        }

        piece.move(toRow, toCol);
        if (isStackingMove) {
            board.pushToStack(toRow, toCol, piece);
        } else {
            board.setPiece(toRow, toCol, piece);
        }
        board.setPiece(fromRow, fromCol, null);

        // 检查兵卒晋升
        if (rulesConfig.getBoolean(RuleConstants.PAWN_PROMOTION) &&
            (piece.getType() == Piece.Type.RED_SOLDIER || piece.getType() == Piece.Type.BLACK_SOLDIER)) {
            boolean isAtBaseLine = (piece.isRed() && toRow == 0) || (!piece.isRed() && toRow == 9);
            if (isAtBaseLine && promotionType != null) {
                // 执行晋升
                board.setPiece(toRow, toCol, new Piece(promotionType, toRow, toCol));
            }
        }

        // 记录着法
        Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedPiece);
        if (isStackingMove) {
            move.setStacking(true);
            move.setStackBefore(new ArrayList<>(board.getStack(toRow, toCol)));
        }
        moveHistory.add(move);

        // 切换回合
        isRedTurn = !isRedTurn;

        // 通知监听器
        for (GameStateListener listener : listeners) {
            listener.onMoveExecuted(move);
        }


        // 检查游戏状态
        checkGameState();

        return true;
    }

    /**
     * 检查兵卒是否需要晋升
     */
    public boolean needsPromotion(int row, int col) {
        if (!rulesConfig.getBoolean(RuleConstants.PAWN_PROMOTION)) {
            return false;
        }
        Piece piece = board.getPiece(row, col);
        if (piece == null) {
            return false;
        }
        if (piece.getType() != Piece.Type.RED_SOLDIER && piece.getType() != Piece.Type.BLACK_SOLDIER) {
            return false;
        }
        // 红兵到达第0行（黑方底线），黑卒到达第9行（红方底线）
        return (piece.isRed() && row == 0) || (!piece.isRed() && row == 9);
    }

    /**
     * 检查游戏状态（是否有一方被将死）
     */
    private void checkGameState() {
        // 获取两个王
        Piece redKing = board.getRedKing();
        Piece blackKing = board.getBlackKing();

        if (redKing == null) {
            gameState = GameState.RED_CHECKMATE;  // 红王被吃 → 红方败 → RED_CHECKMATE
            notifyGameStateChanged();
            return;
        }

        if (blackKing == null) {
            gameState = GameState.BLACK_CHECKMATE;  // 黑王被吃 → 黑方败 → BLACK_CHECKMATE
            notifyGameStateChanged();
            return;
        }

        // 检查黑方是否被将死
        if (checkDetector.isCheckmate(false)) {
            gameState = GameState.BLACK_CHECKMATE;  // 黑方被将死 → 黑方败
            notifyGameStateChanged();
            return;
        }

        // 检查红方是否被将死
        if (checkDetector.isCheckmate(true)) {
            gameState = GameState.RED_CHECKMATE;  // 红方被将死 → 红方败
            notifyGameStateChanged();
            return;
        }
    }

    private void notifyGameStateChanged() {
        for (GameStateListener listener : listeners) {
            listener.onGameStateChanged(gameState);
        }
    }

    /**
     * 撤销上一步棋
     */
    public boolean undoLastMove() {
        if (!rulesConfig.getBoolean(RuleConstants.ALLOW_UNDO)) {
            return false;
        }
        if (moveHistory.isEmpty()) {
            return false;
        }

        Move lastMove = moveHistory.remove(moveHistory.size() - 1);

        // 处理堆叠撤销
        if (lastMove.isStacking()) {
            // 从目标位置弹出该棋子
            board.popTop(lastMove.getToRow(), lastMove.getToCol());
            // 将棋子放回原位置
            Piece piece = lastMove.getPiece();
            piece.move(lastMove.getFromRow(), lastMove.getFromCol());
            board.setPiece(lastMove.getFromRow(), lastMove.getFromCol(), piece);
        } else {
            // 普通撤销逻辑
            Piece piece = lastMove.getPiece();
            piece.move(lastMove.getFromRow(), lastMove.getFromCol());
            board.setPiece(lastMove.getFromRow(), lastMove.getFromCol(), piece);
            board.setPiece(lastMove.getToRow(), lastMove.getToCol(), null);

            // 恢复被吃的棋子（如果有）
            if (lastMove.getCapturedPiece() != null) {
                Piece captured = lastMove.getCapturedPiece();
                board.setPiece(lastMove.getToRow(), lastMove.getToCol(), captured);
            }
        }

        // 切换回合
        isRedTurn = !isRedTurn;

        // 恢复游戏状态
        gameState = GameState.RUNNING;
        notifyGameStateChanged();

        return true;
    }

    /**
     * 重新开始游戏
     */
    public void restart() {
        board.reset();
        moveHistory.clear();
        isRedTurn = true;
        gameState = GameState.RUNNING;
        // 清理回放相关缓存，避免回放残留影响新对局
        savedInitialBoard = null;
        savedInitialIsRedTurn = true;
        isInReplayMode = false;
        currentReplayStep = -1;
        notifyGameStateChanged();
    }

    public Board getBoard() {
        return board;
    }

    public List<Move> getMoveHistory() {
        return new ArrayList<>(moveHistory);
    }

    public void clearMoveHistory() {
        moveHistory.clear();
    }

    public void addMoveToHistory(Move move) {
        moveHistory.add(move);
    }

    /**
     * 保存当前棋盘状态作为回放起点（用于导入残局后）
     */
    public void saveInitialStateForReplay() {
        savedInitialBoard = board.deepCopy();
        savedInitialIsRedTurn = isRedTurn;
    }

    /**
     * 重建棋盘到指定步数（用于回放）
     * @param step 步数，0表示初始状态，n表示执行了前n步
     */
    public void rebuildBoardToStep(int step) {
        if (savedInitialBoard == null) {
            return; // 没有保存的初始状态，无法回放
        }

        // 清空当前棋盘
        board.clearBoard();

        // 从保存的初始状态复制棋子
        for (int row = 0; row < board.getRows(); row++) {
            for (int col = 0; col < board.getCols(); col++) {
                Piece piece = savedInitialBoard.getPiece(row, col);
                if (piece != null) {
                    Piece pieceCopy = new Piece(piece.getType(), row, col);
                    board.putPieceFresh(row, col, pieceCopy); // 同步棋子列表
                }
            }
        }

        // 恢复初始轮次
        isRedTurn = savedInitialIsRedTurn;

        // 执行前step步着法
        List<Move> moves = getMoveHistory();
        for (int i = 0; i < step && i < moves.size(); i++) {
            Move move = moves.get(i);

            // 获取棋子
            Piece piece = board.getPiece(move.getFromRow(), move.getFromCol());
            if (piece == null) continue;

            // 移除目标位置的棋子（如果有）
            Piece targetPiece = board.getPiece(move.getToRow(), move.getToCol());
            if (targetPiece != null) {
                board.removePiece(move.getToRow(), move.getToCol());
            }

            // 移动棋子
            piece.move(move.getToRow(), move.getToCol());
            board.setPiece(move.getToRow(), move.getToCol(), piece);
            board.setPiece(move.getFromRow(), move.getFromCol(), null);

            // 切换回合
            isRedTurn = !isRedTurn;
        }

        gameState = GameState.RUNNING;
    }

    /**
     * 设置回放模式状态
     */
    public void setReplayMode(boolean inReplayMode, int step) {
        this.isInReplayMode = inReplayMode;
        this.currentReplayStep = step;
    }

    /**
     * 获取是否处于回放模式
     */
    public boolean isInReplayMode() {
        return isInReplayMode;
    }

    /**
     * 获取当前回放步数
     */
    public int getCurrentReplayStep() {
        return currentReplayStep;
    }

    public boolean isRedTurn() {
        return isRedTurn;
    }

    public void setRedTurn(boolean isRedTurn) {
        this.isRedTurn = isRedTurn;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void addGameStateListener(GameStateListener listener) {
        listeners.add(listener);
    }

    public void removeGameStateListener(GameStateListener listener) {
        listeners.remove(listener);
    }

    // 配置项访问器 - 委托给rulesConfig
    public boolean isAllowUndo() { return rulesConfig.getBoolean(RuleConstants.ALLOW_UNDO); }
    public void setAllowUndo(boolean allowUndo) { rulesConfig.set(RuleConstants.ALLOW_UNDO, allowUndo); }

    public boolean isShowHints() { return rulesConfig.getBoolean(RuleConstants.SHOW_HINTS); }
    public void setShowHints(boolean showHints) { rulesConfig.set(RuleConstants.SHOW_HINTS, showHints); }

    // 特殊玩法访问器 - 统一委托给rulesConfig
    public JsonObject getSpecialRules() { return rulesConfig.toJson(); }
    public boolean isSpecialRuleEnabled(String key) {
        return rulesConfig.getBoolean(key);
    }

    public void setUnblockPiece(boolean allow) { rulesConfig.set(RuleConstants.UNBLOCK_PIECE, allow); }
    public void setUnblockHorseLeg(boolean allow) { rulesConfig.set(RuleConstants.UNBLOCK_HORSE_LEG, allow); }
    public void setUnblockElephantEye(boolean allow) { rulesConfig.set(RuleConstants.UNBLOCK_ELEPHANT_EYE, allow); }
    public boolean isUnblockPiece() { return rulesConfig.getBoolean(RuleConstants.UNBLOCK_PIECE); }
    public boolean isUnblockHorseLeg() { return rulesConfig.getBoolean(RuleConstants.UNBLOCK_HORSE_LEG); }
    public boolean isUnblockElephantEye() { return rulesConfig.getBoolean(RuleConstants.UNBLOCK_ELEPHANT_EYE); }

    public void setNoRiverLimitPawn(boolean allow) { rulesConfig.set(RuleConstants.NO_RIVER_LIMIT_PAWN, allow); }
    public boolean isNoRiverLimitPawn() { return rulesConfig.getBoolean(RuleConstants.NO_RIVER_LIMIT_PAWN); }

    public void setAllowCaptureOwnPiece(boolean allow) { rulesConfig.set(RuleConstants.ALLOW_CAPTURE_OWN_PIECE, allow); }
    public boolean isAllowCaptureOwnPiece() { return rulesConfig.getBoolean(RuleConstants.ALLOW_CAPTURE_OWN_PIECE); }

    public void setAllowPieceStacking(boolean allow) { rulesConfig.set(RuleConstants.ALLOW_PIECE_STACKING, allow); }
    public boolean isAllowPieceStacking() { return rulesConfig.getBoolean(RuleConstants.ALLOW_PIECE_STACKING); }
    public void setMaxStackingCount(int count) { rulesConfig.set(RuleConstants.MAX_STACKING_COUNT, count); }
    public int getMaxStackingCount() { return rulesConfig.getInt(RuleConstants.MAX_STACKING_COUNT); }

    /**
     * 获取设置快照（供联机同步）
     */
    public JsonObject getSettingsSnapshot() {
        return rulesConfig.toJson();
    }

    /**
     * 应用设置快照（供联机同步）
     */
    public void applySettingsSnapshot(JsonObject snapshot) {
        if (snapshot != null) {
            rulesConfig.loadFromJson(snapshot);
            validator.setRulesConfig(rulesConfig);
        }
    }

    /**
     * 获取规则配置对象
     */
    public GameRulesConfig getRulesConfig() {
        return rulesConfig;
    }
}
