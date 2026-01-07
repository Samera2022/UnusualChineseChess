package io.github.samera2022.chinese_chess.engine;

import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.model.Move;
import io.github.samera2022.chinese_chess.model.Piece;

import io.github.samera2022.chinese_chess.rules.CheckDetector;
import io.github.samera2022.chinese_chess.rules.MoveValidator;

import java.util.*;

/**
 * 游戏引擎 - 管理游戏状态和逻辑
 */
public class GameEngine {
    private Board board;
    private MoveValidator validator;
    private CheckDetector checkDetector;
    private List<Move> moveHistory;
    private boolean isRedTurn;
    private GameState gameState;
    private List<GameStateListener> listeners;

    // 回放功能支持
    private Board savedInitialBoard = null; // 保存的初始棋盘状态（用于回放）
    private boolean savedInitialIsRedTurn = true;

    // 新增：配置项
    private boolean allowUndo = true;
    private boolean showHints = true;
    // 特殊玩法
    private final JsonObject specialRules = new JsonObject();

    // 新增：取消卡子相关规则
    private boolean unblockPiece = false;
    private boolean unblockHorseLeg = false;
    private boolean unblockElephantEye = false;

    // 兵过河特殊规则
    private boolean noRiverLimitPawn = false;

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
        this.board = new Board();
        this.validator = new MoveValidator(board);
        this.checkDetector = new CheckDetector(board, validator);
        this.moveHistory = new ArrayList<>();
        this.isRedTurn = true; // 红方先行
        this.gameState = GameState.RUNNING;
        this.listeners = new ArrayList<>();
        // 默认特殊玩法开关
        specialRules.addProperty("allowFlyingGeneral", false);
        specialRules.addProperty("pawnCanRetreat", false);
        specialRules.addProperty("noRiverLimit", false);
        specialRules.addProperty("advisorCanLeave", false);
        specialRules.addProperty("internationalKing", false);
        specialRules.addProperty("pawnPromotion", false);
        specialRules.addProperty("allowOwnBaseLine", false);
        specialRules.addProperty("allowInsideRetreat", false);
        specialRules.addProperty("internationalAdvisor", false);
        specialRules.addProperty("allowElephantCrossRiver", false);
        specialRules.addProperty("allowAdvisorCrossRiver", false);
        specialRules.addProperty("allowKingCrossRiver", false);
        specialRules.addProperty("leftRightConnected", false);
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
        if (capturedPiece != null) {
            board.removePiece(toRow, toCol);
        }

        piece.move(toRow, toCol);
        board.setPiece(toRow, toCol, piece);
        board.setPiece(fromRow, fromCol, null);

        // 检查兵卒晋升
        if (isSpecialRuleEnabled("pawnPromotion") &&
            (piece.getType() == Piece.Type.RED_SOLDIER || piece.getType() == Piece.Type.BLACK_SOLDIER)) {
            boolean isAtBaseLine = (piece.isRed() && toRow == 0) || (!piece.isRed() && toRow == 9);
            if (isAtBaseLine && promotionType != null) {
                // 执行晋升
                board.setPiece(toRow, toCol, new Piece(promotionType, toRow, toCol));
            }
        }

        // 记录着法
        Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedPiece);
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
        if (!isSpecialRuleEnabled("pawnPromotion")) {
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
            gameState = GameState.BLACK_CHECKMATE;
            notifyGameStateChanged();
            return;
        }

        if (blackKing == null) {
            gameState = GameState.RED_CHECKMATE;
            notifyGameStateChanged();
            return;
        }

        // 检查黑方是否被将死
        if (checkDetector.isCheckmate(false)) {
            gameState = GameState.RED_CHECKMATE;
            notifyGameStateChanged();
            return;
        }

        // 检查红方是否被将死
        if (checkDetector.isCheckmate(true)) {
            gameState = GameState.BLACK_CHECKMATE;
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
        if (!allowUndo) {
            return false;
        }
        if (moveHistory.isEmpty()) {
            return false;
        }

        Move lastMove = moveHistory.remove(moveHistory.size() - 1);

        // 移动棋子回原位置
        Piece piece = lastMove.getPiece();
        piece.move(lastMove.getFromRow(), lastMove.getFromCol());
        board.setPiece(lastMove.getFromRow(), lastMove.getFromCol(), piece);
        board.setPiece(lastMove.getToRow(), lastMove.getToCol(), null);

        // 恢复被吃的棋子（如果有）
        if (lastMove.getCapturedPiece() != null) {
            Piece captured = lastMove.getCapturedPiece();
            board.setPiece(lastMove.getToRow(), lastMove.getToCol(), captured);
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
                    board.setPiece(row, col, pieceCopy);
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

    // 配置项访问器
    public boolean isAllowUndo() { return allowUndo; }
    public void setAllowUndo(boolean allowUndo) { this.allowUndo = allowUndo; }

    public boolean isShowHints() { return showHints; }
    public void setShowHints(boolean showHints) { this.showHints = showHints; }

    // 特殊玩法访问器（逻辑接入可在 MoveValidator 等处后续实现）
    public JsonObject getSpecialRules() { return specialRules.deepCopy(); }
    public void setSpecialRule(String key, boolean value) { specialRules.addProperty(key, value); applySpecialRuleToValidator(key, value); }
    public boolean isSpecialRuleEnabled(String key) {
        return specialRules.has(key) && specialRules.get(key).getAsBoolean();
    }

    private void applySpecialRuleToValidator(String key, boolean value) {
        if (validator == null) return;
        switch (key) {
            case "allowFlyingGeneral":
                validator.setAllowFlyingGeneral(value);
                break;
            case "pawnCanRetreat":
                validator.setPawnCanRetreat(value);
                break;
            case "noRiverLimit":
                validator.setNoRiverLimit(value);
                break;
            case "advisorCanLeave":
                validator.setAdvisorCanLeave(value);
                break;
            case "internationalKing":
                validator.setInternationalKing(value);
                break;
            case "pawnPromotion":
                validator.setPawnPromotion(value);
                break;
            case "allowOwnBaseLine":
                validator.setAllowOwnBaseLine(value);
                break;
            case "allowInsideRetreat":
                validator.setAllowInsideRetreat(value);
                break;
            case "internationalAdvisor":
                validator.setInternationalAdvisor(value);
                break;
            case "allowElephantCrossRiver":
                validator.setAllowElephantCrossRiver(value);
                break;
            case "allowAdvisorCrossRiver":
                validator.setAllowAdvisorCrossRiver(value);
                break;
            case "allowKingCrossRiver":
                validator.setAllowKingCrossRiver(value);
                break;
            case "leftRightConnected":
                validator.setLeftRightConnected(value);
                break;
            default:
                break;
        }
    }

    // 设置快照（供联机同步）
    public JsonObject getSettingsSnapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("allowUndo", allowUndo);
        root.add("specialRules", getSpecialRules());
        return root;
    }

    public void applySettingsSnapshot(JsonObject snapshot) {
        if (snapshot == null) return;
        if (snapshot.has("allowUndo")) {
            setAllowUndo(snapshot.get("allowUndo").getAsBoolean());
        }
        if (snapshot.has("specialRules") && snapshot.get("specialRules").isJsonObject()) {
            JsonObject sr = snapshot.getAsJsonObject("specialRules");
            for (String key : sr.keySet()) {
                boolean val = sr.get(key).getAsBoolean();
                setSpecialRule(key, val);
            }
        }
    }

    public void setUnblockPiece(boolean allow) { this.unblockPiece = allow; validator.setUnblockPiece(allow); }
    public void setUnblockHorseLeg(boolean allow) { this.unblockHorseLeg = allow; validator.setUnblockHorseLeg(allow); }
    public void setUnblockElephantEye(boolean allow) { this.unblockElephantEye = allow; validator.setUnblockElephantEye(allow); }
    public boolean isUnblockPiece() { return unblockPiece; }
    public boolean isUnblockHorseLeg() { return unblockHorseLeg; }
    public boolean isUnblockElephantEye() { return unblockElephantEye; }
    // 兵过河特殊规则
    public void setNoRiverLimitPawn(boolean allow) { this.noRiverLimitPawn = allow; validator.setNoRiverLimitPawn(allow); }
    public boolean isNoRiverLimitPawn() { return noRiverLimitPawn; }
}
