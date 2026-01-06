package io.github.samera2022.chinese_chess;

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
        this.checkDetector = new CheckDetector(board);
        this.moveHistory = new ArrayList<>();
        this.isRedTurn = true; // 红方先行
        this.gameState = GameState.RUNNING;
        this.listeners = new ArrayList<>();
    }

    /**
     * 尝试执行一步棋
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
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

        // 记录着法
        Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedPiece);
        moveHistory.add(move);

        // 通知监听器
        for (GameStateListener listener : listeners) {
            listener.onMoveExecuted(move);
        }

        // 切换回合
        isRedTurn = !isRedTurn;

        // 检查游戏状态
        checkGameState();

        return true;
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

    public boolean isRedTurn() {
        return isRedTurn;
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
}

