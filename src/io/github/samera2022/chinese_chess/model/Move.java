package io.github.samera2022.chinese_chess.model;

import java.util.List;

/**
 * 着法记录 - 记录一步棋的信息
 */
public class Move {
    private int fromRow;
    private int fromCol;
    private int toRow;
    private int toCol;
    private Piece piece;
    private Piece capturedPiece; // 被吃的棋子（可能为null）
    private boolean isStacking = false; // 是否是堆叠移动
    private List<Piece> stackBefore; // 堆叠前的棋子列表（用于撤销）

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

    @Override
    public String toString() {
        String notation = piece.getDisplayName() + " (" + fromRow + "," + fromCol + ") -> (" + toRow + "," + toCol + ")";
        if (isStacking) {
            notation += " [堆叠]";
        } else if (capturedPiece != null) {
            notation += " [吃了 " + capturedPiece.getDisplayName() + "]";
        }
        return notation;
    }
}
