package io.github.samera2022.chinese_chess.model;

import java.util.List;

/**
 * 着法记录 - 记录一步棋的信息
 */
public class Move implements HistoryItem {
    private int fromRow;
    private int fromCol;
    private int toRow;
    private int toCol;
    private Piece piece;
    private Piece capturedPiece; // 被吃的棋子（可能为null）
    private boolean isStacking = false; // 是否是堆叠移动
    private List<Piece> stackBefore; // 堆叠前的棋子列表（用于撤销）
    private boolean captureConversion = false; // 是否为俘虏（吃子改为转换）
    private Piece convertedPiece;              // 转换后的己方棋子
    private int selectedStackIndex = -1; // 从堆栈中选择的棋子索引（-1表示无效或不从堆栈选择）
    private List<Piece> movedStack; // 移动时随之移动的堆栈中的其他棋子
    private boolean isForceMove = false; // 是否为强制走子

    public Move(int fromRow, int fromCol, int toRow, int toCol, Piece piece, Piece capturedPiece) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
        this.piece = piece;
        this.capturedPiece = capturedPiece;
    }

    public int getFromRow() {
        return fromRow;
    }

    public int getFromCol() {
        return fromCol;
    }

    public int getToRow() {
        return toRow;
    }

    public int getToCol() {
        return toCol;
    }

    public Piece getPiece() {
        return piece;
    }

    public Piece getCapturedPiece() {
        return capturedPiece;
    }

    public void setStacking(boolean stacking) {
        this.isStacking = stacking;
    }

    public boolean isStacking() {
        return isStacking;
    }

    public void setStackBefore(List<Piece> stack) {
        this.stackBefore = stack;
    }

    public List<Piece> getStackBefore() {
        return stackBefore;
    }

    public void setCaptureConversion(boolean captureConversion) { this.captureConversion = captureConversion; }
    public boolean isCaptureConversion() { return captureConversion; }
    public void setConvertedPiece(Piece convertedPiece) { this.convertedPiece = convertedPiece; }
    public Piece getConvertedPiece() { return convertedPiece; }

    public void setSelectedStackIndex(int index) { this.selectedStackIndex = index; }
    public int getSelectedStackIndex() { return selectedStackIndex; }
    public void setMovedStack(List<Piece> stack) { this.movedStack = stack; }
    public List<Piece> getMovedStack() { return movedStack; }

    public void setForceMove(boolean force) { this.isForceMove = force; }
    public boolean isForceMove() { return isForceMove; }

    @Override
    public String toString() {
        StringBuilder notation = new StringBuilder();
        if (isForceMove) {
            notation.append("Force ");
        }
        notation.append(piece.getDisplayName())
                .append(" (").append(fromRow).append(",").append(fromCol).append(") -> (")
                .append(toRow).append(",").append(toCol).append(")");
        if (selectedStackIndex >= 0) {
            notation.append(" [堆叠选择:").append(selectedStackIndex + 1).append("]");
        } else if (isStacking) {
            notation.append(" [堆叠]");
        } else if (captureConversion && capturedPiece != null) {
            notation.append(" [俘虏 ").append(capturedPiece.getDisplayName()).append("]");
        } else if (capturedPiece != null) {
            notation.append(" [吃了 ").append(capturedPiece.getDisplayName()).append("]");
        }
        return notation.toString();
    }
}
