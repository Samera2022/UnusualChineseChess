package io.github.samera2022.chinese_chess.model;

/**
 * 棋子类 - 表示中国象棋中的一个棋子
 */
public class Piece {
    // 棋子类型枚举
    public enum Type {
        // 红方棋子
        RED_KING("帥", "K", true),
        RED_ADVISOR("士", "A", true),
        RED_ELEPHANT("象", "E", true),
        RED_HORSE("马", "H", true),
        RED_CHARIOT("车", "R", true),
        RED_CANNON("炮", "C", true),
        RED_SOLDIER("兵", "P", true),

        // 黑方棋子
        BLACK_KING("将", "k", false),
        BLACK_ADVISOR("士", "a", false),
        BLACK_ELEPHANT("象", "e", false),
        BLACK_HORSE("马", "h", false),
        BLACK_CHARIOT("车", "r", false),
        BLACK_CANNON("炮", "c", false),
        BLACK_SOLDIER("兵", "p", false);

        private final String chineseName;
        private final String symbol;
        private final boolean isRed;

        Type(String chineseName, String symbol, boolean isRed) {
            this.chineseName = chineseName;
            this.symbol = symbol;
            this.isRed = isRed;
        }

        public String getChineseName() {
            return chineseName;
        }

        public String getSymbol() {
            return symbol;
        }

        public boolean isRed() {
            return isRed;
        }
    }

    private Type type;
    private int row;
    private int col;

    public Piece(Type type, int row, int col) {
        this.type = type;
        this.row = row;
        this.col = col;
    }

    public Type getType() {
        return type;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public void move(int newRow, int newCol) {
        this.row = newRow;
        this.col = newCol;
    }

    public boolean isRed() {
        return type.isRed();
    }

    public String getDisplayName() {
        return type.getChineseName();
    }

    @Override
    public String toString() {
        return type.getSymbol();
    }
}
