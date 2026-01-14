package io.github.samera2022.chinese_chess.engine;

import io.github.samera2022.chinese_chess.model.Piece;

import java.util.*;

/**
 * 棋盘类 - 管理中国象棋的棋盘状态
 * 棋盘为 10 行 9 列，坐标为 (0-9, 0-8)
 */
public class Board {
    private static final int ROWS = 10;
    private static final int COLS = 9;
    private Piece[][] board;
    private List<Piece> redPieces;
    private List<Piece> blackPieces;
    private final Map<String, Deque<Piece>> stacks = new HashMap<>();

    public Board() {
        this.board = new Piece[ROWS][COLS];
        this.redPieces = new ArrayList<>();
        this.blackPieces = new ArrayList<>();
        initializeBoard();
    }

    /**
     * 初始化棋盘 - 按照中国象棋标准布局
     */
    private void initializeBoard() {
        // 清空棋盘
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                board[i][j] = null;
            }
        }
        redPieces.clear();
        blackPieces.clear();
        stacks.clear();

        // 初始化黑方（顶部，行 0-4）
        addPiece(0, 4, Piece.Type.BLACK_KING);
        addPiece(0, 3, Piece.Type.BLACK_ADVISOR);
        addPiece(0, 5, Piece.Type.BLACK_ADVISOR);
        addPiece(0, 2, Piece.Type.BLACK_ELEPHANT);
        addPiece(0, 6, Piece.Type.BLACK_ELEPHANT);
        addPiece(0, 1, Piece.Type.BLACK_HORSE);
        addPiece(0, 7, Piece.Type.BLACK_HORSE);
        addPiece(0, 0, Piece.Type.BLACK_CHARIOT);
        addPiece(0, 8, Piece.Type.BLACK_CHARIOT);
        addPiece(2, 1, Piece.Type.BLACK_CANNON);
        addPiece(2, 7, Piece.Type.BLACK_CANNON);
        addPiece(3, 0, Piece.Type.BLACK_SOLDIER);
        addPiece(3, 2, Piece.Type.BLACK_SOLDIER);
        addPiece(3, 4, Piece.Type.BLACK_SOLDIER);
        addPiece(3, 6, Piece.Type.BLACK_SOLDIER);
        addPiece(3, 8, Piece.Type.BLACK_SOLDIER);

        // 初始化红方（底部，行 5-9）
        addPiece(9, 4, Piece.Type.RED_KING);
        addPiece(9, 3, Piece.Type.RED_ADVISOR);
        addPiece(9, 5, Piece.Type.RED_ADVISOR);
        addPiece(9, 2, Piece.Type.RED_ELEPHANT);
        addPiece(9, 6, Piece.Type.RED_ELEPHANT);
        addPiece(9, 1, Piece.Type.RED_HORSE);
        addPiece(9, 7, Piece.Type.RED_HORSE);
        addPiece(9, 0, Piece.Type.RED_CHARIOT);
        addPiece(9, 8, Piece.Type.RED_CHARIOT);
        addPiece(7, 1, Piece.Type.RED_CANNON);
        addPiece(7, 7, Piece.Type.RED_CANNON);
        addPiece(6, 0, Piece.Type.RED_SOLDIER);
        addPiece(6, 2, Piece.Type.RED_SOLDIER);
        addPiece(6, 4, Piece.Type.RED_SOLDIER);
        addPiece(6, 6, Piece.Type.RED_SOLDIER);
        addPiece(6, 8, Piece.Type.RED_SOLDIER);
    }

    private String key(int r, int c) {
        return r + "," + c;
    }

    private Deque<Piece> stackOf(int r, int c) {
        return stacks.computeIfAbsent(key(r, c), k -> new ArrayDeque<>());
    }

    public List<Piece> getStack(int row, int col) {
        if (!isValid(row, col)) return Collections.emptyList();
        Deque<Piece> dq = stacks.get(key(row, col));
        return dq == null ? Collections.emptyList() : new ArrayList<>(dq);
    }

    public int getStackSize(int row, int col) {
        if (!isValid(row, col)) return 0;
        Deque<Piece> dq = stacks.get(key(row, col));
        return dq == null ? 0 : dq.size();
    }

    private void addPiece(int row, int col, Piece.Type type) {
        Piece piece = new Piece(type, row, col);
        pushToStack(row, col, piece);
    }

    public Piece getPiece(int row, int col) {
        if (!isValid(row, col)) return null;
        Deque<Piece> dq = stacks.get(key(row, col));
        return dq == null || dq.isEmpty() ? null : dq.peekLast();
    }

    public void setPiece(int row, int col, Piece piece) {
        if (!isValid(row, col)) return;
        clearStack(row, col);
        if (piece != null) {
            pushToStack(row, col, piece);
        }
    }

    public void removePiece(int row, int col) {
        if (!isValid(row, col)) return;
        popTop(row, col);
    }

    public void pushToStack(int row, int col, Piece piece) {
        if (!isValid(row, col) || piece == null) return;
        piece.move(row, col); // Ensure piece's internal coordinates are updated
        Deque<Piece> dq = stackOf(row, col);
        dq.addLast(piece);
        board[row][col] = dq.peekLast();
        if (piece.isRed()) {
            if (!redPieces.contains(piece)) redPieces.add(piece);
        } else {
            if (!blackPieces.contains(piece)) blackPieces.add(piece);
        }
    }

    public Piece popTop(int row, int col) {
        if (!isValid(row, col)) return null;
        Deque<Piece> dq = stacks.get(key(row, col));
        if (dq == null || dq.isEmpty()) return null;
        Piece p = dq.removeLast();
        p.move(-1, -1); // Mark as off-board
        if (p.isRed()) redPieces.remove(p);
        else blackPieces.remove(p);
        board[row][col] = dq.peekLast();
        if (dq.isEmpty()) stacks.remove(key(row, col));
        return p;
    }

    public Piece removeFromStack(int row, int col, int index) {
        if (!isValid(row, col)) return null;
        Deque<Piece> dq = stacks.get(key(row, col));
        if (dq == null || dq.isEmpty() || index < 0 || index >= dq.size()) return null;

        java.util.List<Piece> list = new java.util.ArrayList<>(dq);
        Piece removed = list.remove(index);
        removed.move(-1, -1); // Mark as off-board

        if (removed.isRed()) redPieces.remove(removed);
        else blackPieces.remove(removed);

        dq.clear();
        dq.addAll(list);

        board[row][col] = dq.peekLast();
        if (dq.isEmpty()) stacks.remove(key(row, col));

        return removed;
    }

    public void insertToStack(int row, int col, int index, Piece piece) {
        if (!isValid(row, col) || piece == null) return;
        piece.move(row, col); // Ensure piece's internal coordinates are updated
        Deque<Piece> dq = stackOf(row, col);
        
        if (index < 0) index = 0;
        if (index > dq.size()) index = dq.size();

        java.util.List<Piece> list = new java.util.ArrayList<>(dq);
        list.add(index, piece);

        if (piece.isRed()) {
            if (!redPieces.contains(piece)) redPieces.add(piece);
        } else {
            if (!blackPieces.contains(piece)) blackPieces.add(piece);
        }

        dq.clear();
        dq.addAll(list);
        board[row][col] = dq.peekLast();
    }

    public void clearStack(int row, int col) {
        if (!isValid(row, col)) return;
        Deque<Piece> dq = stacks.remove(key(row, col));
        if (dq != null) {
            for (Piece p : dq) {
                p.move(-1, -1); // Mark as off-board
                if (p.isRed()) redPieces.remove(p);
                else blackPieces.remove(p);
            }
        }
        board[row][col] = null;
    }

    public int getRows() {
        return ROWS;
    }

    public int getCols() {
        return COLS;
    }

    public List<Piece> getRedPieces() {
        return new ArrayList<>(redPieces);
    }

    public List<Piece> getBlackPieces() {
        return new ArrayList<>(blackPieces);
    }

    public Piece getRedKing() {
        return redPieces.stream()
            .filter(p -> p.getType() == Piece.Type.RED_KING)
            .findFirst()
            .orElse(null);
    }

    public Piece getBlackKing() {
        return blackPieces.stream()
            .filter(p -> p.getType() == Piece.Type.BLACK_KING)
            .findFirst()
            .orElse(null);
    }

    public void reset() {
        initializeBoard();
    }

    public void clearBoard() {
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                board[i][j] = null;
            }
        }
        redPieces.clear();
        blackPieces.clear();
        stacks.clear();
    }

    public Board deepCopy() {
        Board copy = new Board();
        copy.clearBoard();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                for (Piece p : getStack(r, c)) {
                    Piece cp = new Piece(p.getType(), r, c);
                    copy.pushToStack(r, c, cp);
                }
            }
        }
        return copy;
    }

    public void putPieceFresh(int row, int col, Piece piece) {
        if (!isValid(row, col)) return;
        popTop(row, col);
        pushToStack(row, col, piece);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                Piece piece = board[i][j];
                if (piece != null) {
                    sb.append(piece.toString()).append(" ");
                } else {
                    sb.append(". ");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public boolean isValid(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS;
    }
}
