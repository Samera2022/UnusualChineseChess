package io.github.samera2022.chinese_chess.core.engine;

import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;

import java.util.*;

/**
 * 棋盘类 - 管理中国象棋的棋盘状态
 * 标准模式：10 行 9 列 (0-9, 0-8)
 * 上下连通模式：18 行 9 列 (0-17, 0-8)
 */
public class Board implements ReadonlyBoard {
    public static final int COLS = 9;
    public static final int STANDARD_ROWS = 10;
    public static final int EXPANDED_ROWS = 18;

    private final int rows;
    private Piece[][] board;
    private List<Piece> redPieces;
    private List<Piece> blackPieces;
    private final Map<String, Deque<Piece>> stacks = new HashMap<>();

    public Board() {
        this(STANDARD_ROWS, true, false);
    }

    public Board(int rows) {
        this(rows, true, false);
    }

    private Board(int rows, boolean initialize, boolean symmetric) {
        this.rows = rows;
        this.board = new Piece[rows][COLS];
        this.redPieces = new ArrayList<>();
        this.blackPieces = new ArrayList<>();
        if (initialize) {
            initializeBoard(symmetric);
        }
    }

    // Copy constructor for deepCopy
    private Board(int rows, boolean unused) {
        this.rows = rows;
        this.board = new Piece[rows][COLS];
        this.redPieces = new ArrayList<>();
        this.blackPieces = new ArrayList<>();
    }

    private void initializeBoard() {
        initializeBoard(false);
    }

    private void initializeBoard(boolean symmetric) {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < COLS; j++) {
                board[i][j] = null;
            }
        }
        redPieces.clear();
        blackPieces.clear();
        stacks.clear();

        if (symmetric) {
            initExpandedSymmetric();
        } else if (rows == EXPANDED_ROWS) {
            initExpandedSymmetric();
        } else {
            initStandard();
        }
    }

    private void initStandard() {
        // 黑方（顶部，行 0-4）
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

        // 红方（底部，行 5-9）
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

    /**
     * 18行对称布局（上下连通模式）：
     * 黑方 row 0-8（上半区），红方 row 9-17（下半区，红在下）
     */
    private void initExpandedSymmetric() {
        // 黑方 (row 0-8) —— 上方
        for (int c = 0; c < COLS; c += 2) addPiece(1, c, Piece.Type.BLACK_SOLDIER);
        addPiece(2, 1, Piece.Type.BLACK_CANNON);
        addPiece(2, 7, Piece.Type.BLACK_CANNON);
        addPiece(4, 4, Piece.Type.BLACK_KING);
        addPiece(4, 3, Piece.Type.BLACK_ADVISOR);
        addPiece(4, 5, Piece.Type.BLACK_ADVISOR);
        addPiece(4, 2, Piece.Type.BLACK_ELEPHANT);
        addPiece(4, 6, Piece.Type.BLACK_ELEPHANT);
        addPiece(4, 1, Piece.Type.BLACK_HORSE);
        addPiece(4, 7, Piece.Type.BLACK_HORSE);
        addPiece(4, 0, Piece.Type.BLACK_CHARIOT);
        addPiece(4, 8, Piece.Type.BLACK_CHARIOT);
        addPiece(6, 1, Piece.Type.BLACK_CANNON);
        addPiece(6, 7, Piece.Type.BLACK_CANNON);
        for (int c = 0; c < COLS; c += 2) addPiece(7, c, Piece.Type.BLACK_SOLDIER);

        // 红方 (row 9-17) —— 下方
        for (int c = 0; c < COLS; c += 2) addPiece(10, c, Piece.Type.RED_SOLDIER);
        addPiece(11, 1, Piece.Type.RED_CANNON);
        addPiece(11, 7, Piece.Type.RED_CANNON);
        addPiece(13, 4, Piece.Type.RED_KING);
        addPiece(13, 3, Piece.Type.RED_ADVISOR);
        addPiece(13, 5, Piece.Type.RED_ADVISOR);
        addPiece(13, 2, Piece.Type.RED_ELEPHANT);
        addPiece(13, 6, Piece.Type.RED_ELEPHANT);
        addPiece(13, 1, Piece.Type.RED_HORSE);
        addPiece(13, 7, Piece.Type.RED_HORSE);
        addPiece(13, 0, Piece.Type.RED_CHARIOT);
        addPiece(13, 8, Piece.Type.RED_CHARIOT);
        addPiece(15, 1, Piece.Type.RED_CANNON);
        addPiece(15, 7, Piece.Type.RED_CANNON);
        for (int c = 0; c < COLS; c += 2) addPiece(16, c, Piece.Type.RED_SOLDIER);
    }

    private String key(int r, int c) {
        return r + "," + c;
    }

    private Deque<Piece> stackOf(int r, int c) {
        return stacks.computeIfAbsent(key(r, c), k -> new ArrayDeque<>());
    }

    @Override
    public List<Piece> getStack(int row, int col) {
        if (!isValid(row, col)) return Collections.emptyList();
        Deque<Piece> dq = stacks.get(key(row, col));
        return dq == null ? Collections.emptyList() : new ArrayList<>(dq);
    }

    @Override
    public int getStackSize(int row, int col) {
        if (!isValid(row, col)) return 0;
        Deque<Piece> dq = stacks.get(key(row, col));
        return dq == null ? 0 : dq.size();
    }

    private void addPiece(int row, int col, Piece.Type type) {
        Piece piece = new Piece(type, row, col);
        pushToStack(row, col, piece);
    }

    @Override
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
        piece.move(row, col);
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
        p.move(-1, -1);
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
        List<Piece> list = new ArrayList<>(dq);
        Piece removed = list.remove(index);
        removed.move(-1, -1);
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
        piece.move(row, col);
        Deque<Piece> dq = stackOf(row, col);
        if (index < 0) index = 0;
        if (index > dq.size()) index = dq.size();
        List<Piece> list = new ArrayList<>(dq);
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
                p.move(-1, -1);
                if (p.isRed()) redPieces.remove(p);
                else blackPieces.remove(p);
            }
        }
        board[row][col] = null;
    }

    @Override
    public int getRows() { return rows; }
    @Override
    public int getCols() { return COLS; }

    @Override
    public List<Piece> getRedPieces() { return new ArrayList<>(redPieces); }
    @Override
    public List<Piece> getBlackPieces() { return new ArrayList<>(blackPieces); }

    @Override
    public Piece getRedKing() {
        return redPieces.stream().filter(p -> p.getType() == Piece.Type.RED_KING).findFirst().orElse(null);
    }
    @Override
    public Piece getBlackKing() {
        return blackPieces.stream().filter(p -> p.getType() == Piece.Type.BLACK_KING).findFirst().orElse(null);
    }

    public void reset() { initializeBoard(); }

    public void resetSymmetric() { initializeBoard(true); }

    public void clearBoard() {
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < COLS; j++)
                board[i][j] = null;
        redPieces.clear();
        blackPieces.clear();
        stacks.clear();
    }

    public Board deepCopy() {
        Board copy = new Board(this.rows, false);
        for (int r = 0; r < rows; r++) {
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
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < COLS; j++) {
                Piece piece = board[i][j];
                sb.append(piece != null ? piece.toString() : ".").append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public boolean isValid(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < COLS;
    }

    /**
     * 将当前棋盘状态导出为不可变快照。
     */
    public BoardState toState() {
        List<BoardState.StackEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Deque<Piece>> e : stacks.entrySet()) {
            Deque<Piece> dq = e.getValue();
            if (dq == null || dq.isEmpty()) continue;
            String[] parts = e.getKey().split(",");
            int r = Integer.parseInt(parts[0]);
            int c = Integer.parseInt(parts[1]);
            List<Piece.Type> types = new ArrayList<>();
            for (Piece p : dq) {
                types.add(p.getType());
            }
            entries.add(new BoardState.StackEntry(r, c, types));
        }
        return new BoardState(rows, COLS, entries);
    }

    /**
     * 从 BoardState 快照恢复棋盘。
     */
    public static Board fromState(BoardState state) {
        Board board = new Board(state.getRows(), false, false);
        for (BoardState.StackEntry entry : state.getEntries()) {
            for (Piece.Type type : entry.pieceTypes) {
                Piece p = new Piece(type, entry.row, entry.col);
                board.pushToStack(entry.row, entry.col, p);
            }
        }
        return board;
    }
}
