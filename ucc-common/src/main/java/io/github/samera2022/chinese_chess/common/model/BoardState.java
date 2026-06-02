package io.github.samera2022.chinese_chess.common.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    // ══════════════════════════════════════════════════════════
    // 置换表支持：哈希 & 相等性
    // ══════════════════════════════════════════════════════════

    /**
     * 计算用于 {@link io.github.samera2022.chinese_chess.server.train.TranspositionTable}
     * 的 64 位哈希键。
     *
     * <p>基于 rows, cols, redTurn 以及所有 StackEntry 的 (row, col, pieceTypes ordinal)
     * 计算。entries 内部按 (row, col) 排序以保证顺序无关的确定性。</p>
     *
     * @return 64 位哈希值
     */
    public long toHash() {
        long hash = 17;
        hash = hash * 31 + rows;
        hash = hash * 31 + cols;
        hash = hash * 31 + (redTurn ? 1 : 0);

        // 按 (row, col) 排序以保证不同调用顺序下哈希值一致
        List<StackEntry> sorted = new ArrayList<>(entries);
        sorted.sort((a, b) -> {
            int cmp = Integer.compare(a.row, b.row);
            return cmp != 0 ? cmp : Integer.compare(a.col, b.col);
        });

        for (StackEntry entry : sorted) {
            hash = hash * 31 + entry.row;
            hash = hash * 31 + entry.col;
            for (Piece.Type type : entry.pieceTypes) {
                hash = hash * 31 + type.ordinal();
            }
        }
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BoardState that)) return false;
        if (rows != that.rows || cols != that.cols || redTurn != that.redTurn) return false;

        // 按 (row, col) 排序后逐项比较
        List<StackEntry> aSorted = new ArrayList<>(entries);
        aSorted.sort((x, y) -> {
            int cmp = Integer.compare(x.row, y.row);
            return cmp != 0 ? cmp : Integer.compare(x.col, y.col);
        });
        List<StackEntry> bSorted = new ArrayList<>(that.entries);
        bSorted.sort((x, y) -> {
            int cmp = Integer.compare(x.row, y.row);
            return cmp != 0 ? cmp : Integer.compare(x.col, y.col);
        });
        return aSorted.equals(bSorted);
    }

    @Override
    public int hashCode() {
        long h = toHash();
        return (int) (h ^ (h >>> 32));
    }

    // ══════════════════════════════════════════════════════════

    public static class StackEntry {
        public final int row, col;
        public final List<Piece.Type> pieceTypes;

        public StackEntry(int row, int col, List<Piece.Type> pieceTypes) {
            this.row = row;
            this.col = col;
            this.pieceTypes = pieceTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StackEntry that)) return false;
            return row == that.row && col == that.col && pieceTypes.equals(that.pieceTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(row, col, pieceTypes);
        }
    }
}
