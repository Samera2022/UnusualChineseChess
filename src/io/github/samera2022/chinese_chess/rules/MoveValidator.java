package io.github.samera2022.chinese_chess.rules;

import io.github.samera2022.chinese_chess.engine.Board;
import io.github.samera2022.chinese_chess.model.Piece;

/**
 * 移动规则检查器 - 验证棋子的合法移动
 * 包含中国象棋的所有棋子移动规则
 */
public class MoveValidator {
    private Board board;
    // 特殊规则开关
    private boolean allowFlyingGeneral = false;
    private boolean pawnCanRetreat = false;
    private boolean noRiverLimit = false;
    private boolean advisorCanLeave = false;
    private boolean internationalKing = false;
    private boolean pawnPromotion = false;
    private boolean allowOwnBaseLine = false;
    private boolean allowInsideRetreat = false;
    private boolean internationalAdvisor = false;
    private boolean allowElephantCrossRiver = false;
    private boolean allowAdvisorCrossRiver = false;
    private boolean allowKingCrossRiver = false;
    private boolean leftRightConnected = false;

    // 新增：取消卡子相关规则
    private boolean unblockPiece = false;
    private boolean unblockHorseLeg = false;
    private boolean unblockElephantEye = false;

    // 新增：兵过河特殊规则
    private boolean noRiverLimitPawn = false;

    public MoveValidator(Board board) {
        this.board = board;
    }

    // 供引擎注入特殊玩法开关
    public void setAllowFlyingGeneral(boolean allow) { this.allowFlyingGeneral = allow; }
    public void setPawnCanRetreat(boolean allow) { this.pawnCanRetreat = allow; }
    public void setNoRiverLimit(boolean allow) { this.noRiverLimit = allow; }
    public void setAdvisorCanLeave(boolean allow) { this.advisorCanLeave = allow; }
    public void setInternationalKing(boolean allow) { this.internationalKing = allow; }
    public void setPawnPromotion(boolean allow) { this.pawnPromotion = allow; }
    public void setAllowOwnBaseLine(boolean allow) { this.allowOwnBaseLine = allow; }
    public void setAllowInsideRetreat(boolean allow) { this.allowInsideRetreat = allow; }
    public void setInternationalAdvisor(boolean allow) { this.internationalAdvisor = allow; }
    public void setAllowElephantCrossRiver(boolean allow) { this.allowElephantCrossRiver = allow; }
    public void setAllowAdvisorCrossRiver(boolean allow) { this.allowAdvisorCrossRiver = allow; }
    public void setAllowKingCrossRiver(boolean allow) { this.allowKingCrossRiver = allow; }
    public void setLeftRightConnected(boolean allow) { this.leftRightConnected = allow; }

    public void setUnblockPiece(boolean allow) { this.unblockPiece = allow; }
    public void setUnblockHorseLeg(boolean allow) { this.unblockHorseLeg = allow; }
    public void setUnblockElephantEye(boolean allow) { this.unblockElephantEye = allow; }
    public boolean isUnblockPiece() { return unblockPiece; }
    public boolean isUnblockHorseLeg() { return unblockHorseLeg; }
    public boolean isUnblockElephantEye() { return unblockElephantEye; }

    public boolean isPawnPromotion() { return pawnPromotion; }
    public boolean isAllowOwnBaseLine() { return allowOwnBaseLine; }
    public boolean isAllowInsideRetreat() { return allowInsideRetreat; }
    public boolean isInternationalAdvisor() { return internationalAdvisor; }
    public void setNoRiverLimitPawn(boolean allow) { this.noRiverLimitPawn = allow; }
    public boolean isNoRiverLimitPawn() { return noRiverLimitPawn; }

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

    // --- 辅助方法：计算水平路径上的障碍数 ---

    /**
     * 计算两点之间水平路径上的障碍物数量。
     * 返回数组 int[2]：
     * index 0: 直接路径（Direct Path）中间的棋子数
     * index 1: 环绕路径（Wrap Path）中间的棋子数
     */
    private int[] countHorizontalObstacles(int row, int col1, int col2) {
        // 1. 统计该行总共有多少棋子
        int totalPiecesInRow = 0;
        for (int c = 0; c < 9; c++) {
            if (board.getPiece(row, c) != null) {
                totalPiecesInRow++;
            }
        }

        // 2. 统计直接路径（min到max之间）的棋子数
        int minCol = Math.min(col1, col2);
        int maxCol = Math.max(col1, col2);
        int directPathObstacles = 0;

        for (int c = minCol + 1; c < maxCol; c++) {
            if (board.getPiece(row, c) != null) {
                directPathObstacles++;
            }
        }

        // 3. 计算环绕路径的障碍数
        // 环绕路径障碍 = 总数 - 起点占用(1) - 终点占用(如果有) - 直接路径障碍
        // 注意：MoveValidator 调用此方法时，起点一定有子。终点可能有子也可能没子。
        // 但我们这里计算的是"路径中间"的障碍，不包含起点和终点本身。
        // 所以，我们需要从 totalPiecesInRow 中减去起点和终点（如果它们被算进去了）。

        int occupiedEndPoints = 0;
        if (board.getPiece(row, col1) != null) occupiedEndPoints++;
        if (board.getPiece(row, col2) != null) occupiedEndPoints++;

        // 剩余的棋子就是分布在两条路径中间的
        int allIntermediatePieces = totalPiecesInRow - occupiedEndPoints;

        // 环绕路径障碍 = 所有中间棋子 - 直接路径障障碍
        int wrapPathObstacles = allIntermediatePieces - directPathObstacles;

        // 防御性修正（虽然逻辑上不会小于0）
        if (wrapPathObstacles < 0) wrapPathObstacles = 0;

        return new int[]{directPathObstacles, wrapPathObstacles};
    }

    /**
     * 车（车）：可以向前、后、左、右走，不受步数限制
     * 改进：支持左右联通逻辑
     */
    private boolean isValidChariotMove(int fromRow, int fromCol, int toRow, int toCol) {
        // 必须走直线
        if (fromRow != toRow && fromCol != toCol) {
            return false;
        }

        if (fromRow == toRow) {
            // --- 水平移动 ---
            int[] obstacles = countHorizontalObstacles(fromRow, fromCol, toCol);
            int directObs = obstacles[0];
            int wrapObs = obstacles[1];

            // 车的规则：路径上不能有障碍 (障碍数必须为 0)
            boolean directPathValid = (directObs == 0);
            boolean wrapPathValid = (wrapObs == 0);

            if (leftRightConnected) {
                // 如果开启左右联通，任一路径通畅即可
                return directPathValid || wrapPathValid;
            } else {
                // 仅检查直接路径
                return directPathValid;
            }
        } else {
            // --- 竖直移动 (逻辑不变) ---
            int minRow = Math.min(fromRow, toRow);
            int maxRow = Math.max(fromRow, toRow);
            for (int row = minRow + 1; row < maxRow; row++) {
                if (board.getPiece(row, fromCol) != null) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 炮（炮）：走棋规则与车相同，但吃棋时必须跳过一个棋子
     * 改进：完美支持左右联通吃子逻辑
     */
    private boolean isValidCannonMove(int fromRow, int fromCol, int toRow, int toCol) {
        // 必须走直线
        if (fromRow != toRow && fromCol != toCol) {
            return false;
        }

        Piece targetPiece = board.getPiece(toRow, toCol);
        boolean isCapture = (targetPiece != null);

        if (fromRow == toRow) {
            // --- 水平移动 ---
            int[] obstacles = countHorizontalObstacles(fromRow, fromCol, toCol);
            int directObs = obstacles[0];
            int wrapObs = obstacles[1];

            // 炮的规则：
            // 移动（不吃子）：障碍必须为 0
            // 吃子：障碍必须为 1 (炮架)

            boolean directPathValid;
            boolean wrapPathValid;

            if (isCapture) {
                directPathValid = (directObs == 1);
                wrapPathValid = (wrapObs == 1);
            } else {
                directPathValid = (directObs == 0);
                wrapPathValid = (wrapObs == 0);
            }

            if (leftRightConnected) {
                // 左右联通：任一路径满足条件即可
                return directPathValid || wrapPathValid;
            } else {
                return directPathValid;
            }

        } else {
            // --- 竖直移动 (逻辑不变) ---
            int countPieces = 0;
            int minRow = Math.min(fromRow, toRow);
            int maxRow = Math.max(fromRow, toRow);
            for (int row = minRow + 1; row < maxRow; row++) {
                if (board.getPiece(row, fromCol) != null) {
                    countPieces++;
                }
            }

            if (isCapture) {
                return countPieces == 1;
            } else {
                return countPieces == 0;
            }
        }
    }

    // --- 象（相）---
    private boolean isValidElephantMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        // 只判断取消河界
        if (!noRiverLimit) {
            if (piece.isRed() && toRow < 5) {
                return false;
            }
            if (!piece.isRed() && toRow > 4) {
                return false;
            }
        }
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        if (rowDiff != 2 || colDiff != 2) {
            return false;
        }
        int midRow = (fromRow + toRow) / 2;
        int midCol = (fromCol + toCol) / 2;
        if (unblockPiece && unblockElephantEye) {
            return true;
        }
        return board.getPiece(midRow, midCol) == null;
    }

    // --- 马（马）---
    private boolean isValidHorseMove(int fromRow, int fromCol, int toRow, int toCol) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);

        if (!((rowDiff == 1 && colDiff == 2) || (rowDiff == 2 && colDiff == 1))) {
            return false;
        }

        int midRow, midCol;
        if (rowDiff == 1) {
            midRow = fromRow;
            midCol = (fromCol + toCol) / 2;
        } else {
            midRow = (fromRow + toRow) / 2;
            midCol = fromCol;
        }

        // 取消卡马脚
        if (unblockPiece && unblockHorseLeg) {
            return true;
        }

        return board.getPiece(midRow, midCol) == null;
    }

    /**
     * 王（帥）：在宫内活动
     */
    private boolean isValidKingMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        // 只判断取消河界
        if (!noRiverLimit && !allowFlyingGeneral && !allowKingCrossRiver) {
            int minCol = 3;
            int maxCol = 5;
            int minRow = piece.isRed() ? 7 : 0;
            int maxRow = piece.isRed() ? 9 : 2;
            if (toRow < minRow || toRow > maxRow || toCol < minCol || toCol > maxCol) {
                return false;
            }
        }
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        if (internationalKing) {
            return rowDiff <= 1 && colDiff <= 1 && (rowDiff + colDiff) > 0;
        } else {
            return (rowDiff + colDiff) == 1;
        }
    }

    /**
     * 士（仕）
     */
    private boolean isValidAdvisorMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        // 只判断取消河界
        if (!noRiverLimit) {
            int minCol = 3;
            int maxCol = 5;
            int minRow = piece.isRed() ? 7 : 0;
            int maxRow = piece.isRed() ? 9 : 2;
            if (toRow < minRow || toRow > maxRow || toCol < minCol || toCol > maxCol) {
                return false;
            }
        }
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        if (internationalAdvisor) {
            if (rowDiff == 0 && colDiff > 0) {
                int step = toCol > fromCol ? 1 : -1;
                for (int c = fromCol + step; c != toCol; c += step) {
                    if (board.getPiece(fromRow, c) != null) return false;
                }
                return true;
            }
            if (colDiff == 0 && rowDiff > 0) {
                int step = toRow > fromRow ? 1 : -1;
                for (int r = fromRow + step; r != toRow; r += step) {
                    if (board.getPiece(r, fromCol) != null) return false;
                }
                return true;
            }
            if (rowDiff == colDiff && rowDiff > 0) {
                int rowStep = toRow > fromRow ? 1 : -1;
                int colStep = toCol > fromCol ? 1 : -1;
                int r = fromRow + rowStep, c = fromCol + colStep;
                while (r != toRow && c != toCol) {
                    if (board.getPiece(r, c) != null) return false;
                    r += rowStep; c += colStep;
                }
                return true;
            }
            return false;
        }
        return rowDiff == 1 && colDiff == 1;
    }

    /**
     * 兵（兵）
     */
    private boolean isValidSoldierMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        int rowDiff = toRow - fromRow;
        int colDiff = Math.abs(toCol - fromCol);
        boolean isRed = piece.isRed();
        boolean hasCrossedRiver;
        if (isRed) {
            hasCrossedRiver = fromRow < 5;
            if (!hasCrossedRiver && !noRiverLimit) {
                if (rowDiff == -1 && colDiff == 0) return true;
                if (allowInsideRetreat && pawnCanRetreat && rowDiff == 1 && colDiff == 0) return true;
                return false;
            } else {
                if (rowDiff == -1 && colDiff == 0) return true;
                if (rowDiff == 0 && colDiff == 1) return true;
                if (pawnCanRetreat && rowDiff == 1 && colDiff == 0) return true;
            }
        } else {
            hasCrossedRiver = fromRow > 4;
            if (!hasCrossedRiver && !noRiverLimit) {
                if (rowDiff == 1 && colDiff == 0) return true;
                if (allowInsideRetreat && pawnCanRetreat && rowDiff == -1 && colDiff == 0) return true;
                return false;
            } else {
                if (rowDiff == 1 && colDiff == 0) return true;
                if (rowDiff == 0 && colDiff == 1) return true;
                if (pawnCanRetreat && rowDiff == -1 && colDiff == 0) return true;
            }
        }
        return false;
    }
}
