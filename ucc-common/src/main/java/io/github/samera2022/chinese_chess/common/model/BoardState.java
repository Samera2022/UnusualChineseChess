package io.github.samera2022.chinese_chess.common.model;

import java.util.List;

public class BoardState {
    private final int rows;
    private final int cols;
    private final List<StackEntry> entries;
    /** true = 红方回合，false = 黑方回合 */
    private final boolean redTurn;

    /**
     * @param rows    棋盘行数
     * @param cols    棋盘列数
     * @param entries 堆栈项列表
     * @param redTurn 当前是否红方回合
     */
    public BoardState(int rows, int cols, List<StackEntry> entries, boolean redTurn) {
        this.rows = rows;
        this.cols = cols;
        this.entries = entries;
        this.redTurn = redTurn;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public List<StackEntry> getEntries() { return entries; }
    /** @return true 表示当前为红方回合 */
    public boolean isRedTurn() { return redTurn; }

    public static class StackEntry {
        public final int row, col;
        public final List<Piece.Type> pieceTypes;

        public StackEntry(int row, int col, List<Piece.Type> pieceTypes) {
            this.row = row;
            this.col = col;
            this.pieceTypes = pieceTypes;
        }
    }
}
