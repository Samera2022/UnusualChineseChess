package io.github.samera2022.chinese_chess.common.model;

import java.util.List;

public class BoardState {
    private final int rows;
    private final int cols;
    private final List<StackEntry> entries;

    public BoardState(int rows, int cols, List<StackEntry> entries) {
        this.rows = rows;
        this.cols = cols;
        this.entries = entries;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public List<StackEntry> getEntries() { return entries; }

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
