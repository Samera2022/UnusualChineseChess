package io.github.samera2022.chinese_chess.rules;

import io.github.samera2022.chinese_chess.engine.Board;
import io.github.samera2022.chinese_chess.model.Piece;

/**
 * 将军检测器 - 检测是否有棋子在将军
 */
public class CheckDetector {
    private Board board;
    private MoveValidator validator;

    public CheckDetector(Board board, MoveValidator validator) {
        this.board = board;
        this.validator = validator;
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
        java.util.List<Piece> capturedStack = null;
        if (capturedPiece != null) {
            // 保存完整的堆栈信息以便恢复
            capturedStack = new java.util.ArrayList<>(board.getStack(toRow, toCol));
        }

        // 执行移动：从原位置移除，放到目标位置
        board.removePiece(originalRow, originalCol);
        // 清除目标位置（含堆叠）
        board.clearStack(toRow, toCol);
        piece.move(toRow, toCol);
        board.pushToStack(toRow, toCol, piece);

        // 检查移动后是否仍在将
        boolean stillInCheck = isInCheck(isRed);

        // 恢复原始状态：先清除临时位置
        board.clearStack(toRow, toCol);
        // 恢复被吃棋子（含堆叠）
        if (capturedStack != null) {
            for (Piece p : capturedStack) {
                p.move(toRow, toCol);
                board.pushToStack(toRow, toCol, p);
            }
        }
        // 恢复移动的棋子
        piece.move(originalRow, originalCol);
        board.pushToStack(originalRow, originalCol, piece);

        return !stillInCheck;
    }
}

