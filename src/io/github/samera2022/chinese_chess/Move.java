package io.github.samera2022.chinese_chess;

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

    @Override
    public String toString() {
        String notation = piece.getDisplayName() + " (" + fromRow + "," + fromCol + ") -> (" + toRow + "," + toCol + ")";
        if (capturedPiece != null) {
            notation += " [captures " + capturedPiece.getDisplayName() + "]";
        }
        return notation;
    }
}

