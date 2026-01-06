package io.github.samera2022.chinese_chess;

import java.util.*;

/**
 * 移动规则检查器 - 验证棋子的合法移动
 * 包含中国象棋的所有棋子移动规则
 */
public class MoveValidator {
    private Board board;

    public MoveValidator(Board board) {
        this.board = board;
    }

    /**
     * 验证着法是否合法
     */
    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        // 检查坐标有效性
        if (!board.isValid(fromRow, fromCol) || !board.isValid(toRow, toCol)) {
            return false;
        }

        // 检查源位置是否有棋子
        Piece piece = board.getPiece(fromRow, fromCol);
        if (piece == null) {
            return false;
        }

        // 检查目标位置是否是己方棋子
        Piece targetPiece = board.getPiece(toRow, toCol);
        if (targetPiece != null && targetPiece.isRed() == piece.isRed()) {
            return false;
        }

        // 检查源和目的地相同
        if (fromRow == toRow && fromCol == toCol) {
            return false;
        }

        // 根据棋子类型检查移动规则
        switch (piece.getType()) {
            case RED_KING:
            case BLACK_KING:
                return isValidKingMove(fromRow, fromCol, toRow, toCol, piece);
            case RED_ADVISOR:
            case BLACK_ADVISOR:
                return isValidAdvisorMove(fromRow, fromCol, toRow, toCol, piece);
            case RED_ELEPHANT:
            case BLACK_ELEPHANT:
                return isValidElephantMove(fromRow, fromCol, toRow, toCol, piece);
            case RED_HORSE:
            case BLACK_HORSE:
                return isValidHorseMove(fromRow, fromCol, toRow, toCol);
            case RED_CHARIOT:
            case BLACK_CHARIOT:
                return isValidChariotMove(fromRow, fromCol, toRow, toCol);
            case RED_CANNON:
            case BLACK_CANNON:
                return isValidCannonMove(fromRow, fromCol, toRow, toCol);
            case RED_SOLDIER:
            case BLACK_SOLDIER:
                return isValidSoldierMove(fromRow, fromCol, toRow, toCol, piece);
            default:
                return false;
        }
    }

    /**
     * 王（帅）：在宫内活动，每次只能走一步，可以前进、后退、左右走
     */
    private boolean isValidKingMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        // 检查是否在宫内
        int minCol = 3;
        int maxCol = 5;
        int minRow = piece.isRed() ? 7 : 0;
        int maxRow = piece.isRed() ? 9 : 2;

        if (toRow < minRow || toRow > maxRow || toCol < minCol || toCol > maxCol) {
            return false;
        }

        // 只能走一步
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);

        return (rowDiff + colDiff) == 1;
    }

    /**
     * 士（仕）：只能在宫内走，每次走一步，走的方向只能是斜线
     */
    private boolean isValidAdvisorMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        // 检查是否在宫内
        int minCol = 3;
        int maxCol = 5;
        int minRow = piece.isRed() ? 7 : 0;
        int maxRow = piece.isRed() ? 9 : 2;

        if (toRow < minRow || toRow > maxRow || toCol < minCol || toCol > maxCol) {
            return false;
        }

        // 只能走对角线，每次一步
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);

        return rowDiff == 1 && colDiff == 1;
    }

    /**
     * 象（相）：只能走对角线，但不能过河
     * 红象：row >= 5; 黑象：row <= 4
     * 同时象不能被象眼(中间的格子)阻挡
     */
    private boolean isValidElephantMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        // 检查是否过河
        if (piece.isRed() && toRow < 5) {
            return false;
        }
        if (!piece.isRed() && toRow > 4) {
            return false;
        }

        // 只能走对角线，每次两步
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);

        if (rowDiff != 2 || colDiff != 2) {
            return false;
        }

        // 检查象眼是否被阻挡
        int midRow = (fromRow + toRow) / 2;
        int midCol = (fromCol + toCol) / 2;

        return board.getPiece(midRow, midCol) == null;
    }

    /**
     * 马（马）：每次走一格直线，然后再走一格斜线（L形移动）
     * 但如果"马脚"被阻挡，马不能走
     */
    private boolean isValidHorseMove(int fromRow, int fromCol, int toRow, int toCol) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);

        // 检查是否是L形（1-2或2-1）
        if (!((rowDiff == 1 && colDiff == 2) || (rowDiff == 2 && colDiff == 1))) {
            return false;
        }

        // 检查马脚
        int midRow, midCol;
        if (rowDiff == 1) {
            // 水平走2格，竖直走1格
            midRow = fromRow;
            midCol = (fromCol + toCol) / 2;
        } else {
            // 竖直走2格，水平走1格
            midRow = (fromRow + toRow) / 2;
            midCol = fromCol;
        }

        return board.getPiece(midRow, midCol) == null;
    }

    /**
     * 车（车）：可以向前、后、左、右走，不受步数限制
     * 但不能跨过其他棋子
     */
    private boolean isValidChariotMove(int fromRow, int fromCol, int toRow, int toCol) {
        // 必须走直线
        if (fromRow != toRow && fromCol != toCol) {
            return false;
        }

        // 检查路径上是否有棋子阻挡
        if (fromRow == toRow) {
            // 水平移动
            int minCol = Math.min(fromCol, toCol);
            int maxCol = Math.max(fromCol, toCol);
            for (int col = minCol + 1; col < maxCol; col++) {
                if (board.getPiece(fromRow, col) != null) {
                    return false;
                }
            }
        } else {
            // 竖直移动
            int minRow = Math.min(fromRow, toRow);
            int maxRow = Math.max(fromRow, toRow);
            for (int row = minRow + 1; row < maxRow; row++) {
                if (board.getPiece(row, fromCol) != null) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 炮（炮）：走棋规则与车相同，但吃棋时必须跳过一个棋子
     */
    private boolean isValidCannonMove(int fromRow, int fromCol, int toRow, int toCol) {
        // 必须走直线
        if (fromRow != toRow && fromCol != toCol) {
            return false;
        }

        Piece targetPiece = board.getPiece(toRow, toCol);

        if (targetPiece == null) {
            // 不吃子，路径必须清空
            if (fromRow == toRow) {
                int minCol = Math.min(fromCol, toCol);
                int maxCol = Math.max(fromCol, toCol);
                for (int col = minCol + 1; col < maxCol; col++) {
                    if (board.getPiece(fromRow, col) != null) {
                        return false;
                    }
                }
            } else {
                int minRow = Math.min(fromRow, toRow);
                int maxRow = Math.max(fromRow, toRow);
                for (int row = minRow + 1; row < maxRow; row++) {
                    if (board.getPiece(row, fromCol) != null) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            // 吃子，必须恰好跳过一个棋子
            int countPieces = 0;
            if (fromRow == toRow) {
                int minCol = Math.min(fromCol, toCol);
                int maxCol = Math.max(fromCol, toCol);
                for (int col = minCol + 1; col < maxCol; col++) {
                    if (board.getPiece(fromRow, col) != null) {
                        countPieces++;
                    }
                }
            } else {
                int minRow = Math.min(fromRow, toRow);
                int maxRow = Math.max(fromRow, toRow);
                for (int row = minRow + 1; row < maxRow; row++) {
                    if (board.getPiece(row, fromCol) != null) {
                        countPieces++;
                    }
                }
            }
            return countPieces == 1;
        }
    }

    /**
     * 兵（兵）：可以前进，在过河后可以左右走
     * 每次只能走一步
     * 红方向上走（row减小），黑方向下走（row增大）
     */
    private boolean isValidSoldierMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        int rowDiff = toRow - fromRow;
        int colDiff = Math.abs(toCol - fromCol);

        boolean isRed = piece.isRed();
        boolean hasCrossedRiver;

        if (isRed) {
            // 红方：row >= 5表示未过河（在己方），row < 5表示已过河
            hasCrossedRiver = fromRow < 5;

            if (!hasCrossedRiver) {
                // 未过河，只能向前走
                return rowDiff == -1 && colDiff == 0;
            } else {
                // 已过河，可以向前或横向走
                if (rowDiff == -1 && colDiff == 0) {
                    return true; // 向前走
                }
                if (rowDiff == 0 && colDiff == 1) {
                    return true; // 横向走
                }
            }
        } else {
            // 黑方：row <= 4表示未过河（在己方），row > 4表示已过河
            hasCrossedRiver = fromRow > 4;

            if (!hasCrossedRiver) {
                // 未过河，只能向前走
                return rowDiff == 1 && colDiff == 0;
            } else {
                // 已过河，可以向前或横向走
                if (rowDiff == 1 && colDiff == 0) {
                    return true; // 向前走
                }
                if (rowDiff == 0 && colDiff == 1) {
                    return true; // 横向走
                }
            }
        }

        return false;
    }
}

