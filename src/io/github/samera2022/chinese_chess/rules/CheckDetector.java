package io.github.samera2022.chinese_chess.rules;

import io.github.samera2022.chinese_chess.engine.Board;
import io.github.samera2022.chinese_chess.model.Piece;

/**
 * 将军检测器 - 检测是否有棋子在将军
 */
public class CheckDetector {
    private Board board;
    private MoveValidator validator;

    public CheckDetector(Board board) {
        this.board = board;
        this.validator = new MoveValidator(board);
    }

    /**
     * 检查某一方的王是否被将（受到攻击）
     */
    public boolean isInCheck(boolean isRed) {
        Piece king = isRed ? board.getRedKing() : board.getBlackKing();
        if (king == null) {
            return false;
        }

        // 遍历对方的所有棋子，检查是否有能够攻击到王的
        java.util.List<Piece> enemyPieces = isRed ? board.getBlackPieces() : board.getRedPieces();

        for (Piece enemyPiece : enemyPieces) {
            // 检查敌棋是否能攻击到王
            if (validator.isValidMove(enemyPiece.getRow(), enemyPiece.getCol(), king.getRow(), king.getCol())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查某一方是否被将死（无法走棋且被将）
     */
    public boolean isCheckmate(boolean isRed) {
        // 首先必须在将状态
        if (!isInCheck(isRed)) {
            return false;
        }

        // 检查是否有任何合法的着法可以脱离将
        java.util.List<Piece> pieces = isRed ? board.getRedPieces() : board.getBlackPieces();

        for (Piece piece : pieces) {
            for (int row = 0; row < board.getRows(); row++) {
                for (int col = 0; col < board.getCols(); col++) {
                    if (validator.isValidMove(piece.getRow(), piece.getCol(), row, col)) {
                        // 尝试这一步棋是否能脱离将
                        if (canEscapeCheck(piece, row, col, isRed)) {
                            return false; // 有办法脱离将
                        }
                    }
                }
            }
        }

        // 无法脱离将，被将死
        return true;
    }

    /**
     * 检查移动后是否能脱离将
     */
    private boolean canEscapeCheck(Piece piece, int toRow, int toCol, boolean isRed) {
        // 保存原始状态
        int originalRow = piece.getRow();
        int originalCol = piece.getCol();
        Piece capturedPiece = board.getPiece(toRow, toCol);

        // 执行移动
        if (capturedPiece != null) {
            board.removePiece(toRow, toCol);
        }
        piece.move(toRow, toCol);
        board.setPiece(toRow, toCol, piece);
        board.setPiece(originalRow, originalCol, null);

        // 检查移动后是否仍在将
        boolean stillInCheck = isInCheck(isRed);

        // 恢复原始状态
        piece.move(originalRow, originalCol);
        board.setPiece(originalRow, originalCol, piece);
        board.setPiece(toRow, toCol, capturedPiece);
        if (capturedPiece != null) {
            if (capturedPiece.isRed()) {
                // 需要恢复到棋子列表中
            } else {
                // 需要恢复到棋子列表中
            }
        }

        return !stillInCheck;
    }
}

