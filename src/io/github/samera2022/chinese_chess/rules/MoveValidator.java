package io.github.samera2022.chinese_chess.rules;

import io.github.samera2022.chinese_chess.engine.Board;
import io.github.samera2022.chinese_chess.model.Piece;
import io.github.samera2022.chinese_chess.rules.RuleConstants;
import io.github.samera2022.chinese_chess.rules.RulesConfigProvider;

import static io.github.samera2022.chinese_chess.ui.RuleSettingsPanel.isEnabled;

/**
 * 移动规则检查器 - 验证棋子的合法移动
 * 包含中国象棋的所有棋子移动规则
 */
public class MoveValidator {
    private Board board;
    private GameRulesConfig rulesConfig;

    public MoveValidator(Board board) {
        this.board = board;
        // 默认使用全局 provider 的共享配置，避免未注入时产生孤立实例
        this.rulesConfig = RulesConfigProvider.get();
    }

    /**
     * 注入规则配置对象
     */
    public void setRulesConfig(GameRulesConfig rulesConfig) {
        if (rulesConfig != null) {
            this.rulesConfig = rulesConfig;
        }
    }

    // ========== Setter方法（直接修改rulesConfig） ==========

    public void setAllowFlyingGeneral(boolean allow) { rulesConfig.set(RuleConstants.ALLOW_FLYING_GENERAL, allow, GameRulesConfig.ChangeSource.API); }
    public void setDisableFacingGenerals(boolean allow) { rulesConfig.set(RuleConstants.DISABLE_FACING_GENERALS, allow, GameRulesConfig.ChangeSource.API); }
    public void setPawnCanRetreat(boolean allow) { rulesConfig.set(RuleConstants.PAWN_CAN_RETREAT, allow, GameRulesConfig.ChangeSource.API); }
    public void setNoRiverLimit(boolean allow) { rulesConfig.set(RuleConstants.NO_RIVER_LIMIT, allow, GameRulesConfig.ChangeSource.API); }
    public void setAdvisorCanLeave(boolean allow) { rulesConfig.set(RuleConstants.ADVISOR_CAN_LEAVE, allow, GameRulesConfig.ChangeSource.API); }
    public void setInternationalKing(boolean allow) { rulesConfig.set(RuleConstants.INTERNATIONAL_KING, allow, GameRulesConfig.ChangeSource.API); }
    public void setPawnPromotion(boolean allow) { rulesConfig.set(RuleConstants.PAWN_PROMOTION, allow, GameRulesConfig.ChangeSource.API); }
    public void setAllowOwnBaseLine(boolean allow) { rulesConfig.set(RuleConstants.ALLOW_OWN_BASE_LINE, allow, GameRulesConfig.ChangeSource.API); }
    public void setAllowInsideRetreat(boolean allow) { rulesConfig.set(RuleConstants.ALLOW_INSIDE_RETREAT, allow, GameRulesConfig.ChangeSource.API); }
    public void setInternationalAdvisor(boolean allow) { rulesConfig.set(RuleConstants.INTERNATIONAL_ADVISOR, allow, GameRulesConfig.ChangeSource.API); }
    public void setAllowElephantCrossRiver(boolean allow) { rulesConfig.set(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER, allow, GameRulesConfig.ChangeSource.API); }
    public void setAllowAdvisorCrossRiver(boolean allow) { rulesConfig.set(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER, allow, GameRulesConfig.ChangeSource.API); }
    public void setAllowKingCrossRiver(boolean allow) { rulesConfig.set(RuleConstants.ALLOW_KING_CROSS_RIVER, allow, GameRulesConfig.ChangeSource.API); }
    public void setLeftRightConnected(boolean allow) { rulesConfig.set(RuleConstants.LEFT_RIGHT_CONNECTED, allow, GameRulesConfig.ChangeSource.API); }
    public void setLeftRightConnectedHorse(boolean allow) { rulesConfig.set(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE, allow, GameRulesConfig.ChangeSource.API); }
    public void setLeftRightConnectedElephant(boolean allow) { rulesConfig.set(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT, allow, GameRulesConfig.ChangeSource.API); }

    public void setUnblockPiece(boolean allow) { rulesConfig.set(RuleConstants.UNBLOCK_PIECE, allow, GameRulesConfig.ChangeSource.API); }
    public void setUnblockHorseLeg(boolean allow) { rulesConfig.set(RuleConstants.UNBLOCK_HORSE_LEG, allow, GameRulesConfig.ChangeSource.API); }
    public void setUnblockElephantEye(boolean allow) { rulesConfig.set(RuleConstants.UNBLOCK_ELEPHANT_EYE, allow, GameRulesConfig.ChangeSource.API); }
    public void setAllowCaptureOwnPiece(boolean allow) { rulesConfig.set(RuleConstants.ALLOW_CAPTURE_OWN_PIECE, allow, GameRulesConfig.ChangeSource.API); }
    public void setAllowPieceStacking(boolean allow) { rulesConfig.set(RuleConstants.ALLOW_PIECE_STACKING, allow, GameRulesConfig.ChangeSource.API); }
    public void setMaxStackingCount(int count) { rulesConfig.set(RuleConstants.MAX_STACKING_COUNT, Math.max(1, count), GameRulesConfig.ChangeSource.API); }

    // 动态访问帮助方法，统一使用通用Getter
    private boolean r(String key) { return rulesConfig != null && rulesConfig.getBoolean(key); }
    private int ri(String key) { return rulesConfig != null ? rulesConfig.getInt(key) : 0; }

    /**
     * 验证着法是否合法
     */
    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        return isValidMove(fromRow, fromCol, toRow, toCol, -1);
    }

    /**
     * 验证着法是否合法（支持堆栈索引）
     * @param fromRow 源行
     * @param fromCol 源列
     * @param toRow 目标行
     * @param toCol 目标列
     * @param selectedStackIndex 选择的堆栈索引（-1表示使用顶部棋子）
     * @return 是否合法
     */
    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, int selectedStackIndex) {
        if (rulesConfig == null) {
            // 未注入规则配置，使用全局 provider 的实例继续判定（避免孤立本地实例）
            rulesConfig = RulesConfigProvider.get();
        }

        // 检查坐标有效性
        if (!board.isValid(fromRow, fromCol) || !board.isValid(toRow, toCol)) {
            return false;
        }

        // 检查源位置是否有棋子
        Piece piece;
        if (selectedStackIndex >= 0) {
            // 从堆栈中获取指定索引的棋子
            java.util.List<Piece> stack = board.getStack(fromRow, fromCol);
            if (selectedStackIndex >= stack.size()) {
                return false;
            }
            piece = stack.get(selectedStackIndex);
        } else {
            // 使用顶部棋子
            piece = board.getPiece(fromRow, fromCol);
        }

        if (piece == null) {
            return false;
        }

        // 检查目标位置是否是己方棋子
        Piece targetPiece = board.getPiece(toRow, toCol);
        if (targetPiece != null && targetPiece.isRed() == piece.isRed()) {
            if (r(RuleConstants.ALLOW_PIECE_STACKING) && ri(RuleConstants.MAX_STACKING_COUNT) > 1) {
                int stackSize = board.getStackSize(toRow, toCol);
                if (stackSize >= ri(RuleConstants.MAX_STACKING_COUNT)) {
                    return false;
                }
                // 堆栈未满，允许堆叠
            } else if (!r(RuleConstants.ALLOW_CAPTURE_OWN_PIECE)) {
                return false;
            }
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
     * 在左右联通模式下，使用扩展棋盘来处理边界穿越逻辑。
     * 返回数组 int[2]：
     * index 0: 直接路径（Direct Path）中间的棋子数
     * index 1: 环绕路径（Wrap Path）中间的棋子数
     */
    private int[] countHorizontalObstacles(int row, int col1, int col2) {
        if (r(RuleConstants.LEFT_RIGHT_CONNECTED)) {
            // 在左右联通模式下使用扩展棋盘逻辑
            return countHorizontalObstaclesWithWrap(row, col1, col2);
        } else {
            // 原有逻辑：仅计算直接路径
            return countHorizontalObstaclesNormal(row, col1, col2);
        }
    }

    /**
     * 标准的水平障碍计算（不考虑左右联通）
     */
    private int[] countHorizontalObstaclesNormal(int row, int col1, int col2) {
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
        int occupiedEndPoints = 0;
        if (board.getPiece(row, col1) != null) occupiedEndPoints++;
        if (board.getPiece(row, col2) != null) occupiedEndPoints++;

        int allIntermediatePieces = totalPiecesInRow - occupiedEndPoints;
        int wrapPathObstacles = allIntermediatePieces - directPathObstacles;

        if (wrapPathObstacles < 0) wrapPathObstacles = 0;

        return new int[]{directPathObstacles, wrapPathObstacles};
    }

    /**
     * 在左右联通模式下计算水平障碍
     * 使用扩展棋盘（三倍宽度）来处理边界穿越逻辑
     */
    private int[] countHorizontalObstaclesWithWrap(int row, int col1, int col2) {
        final int BOARD_WIDTH = 9;

        // 在扩展棋盘上的坐标（中间部分）
        int expandedCol1 = col1 + BOARD_WIDTH;
        int expandedCol2 = col2 + BOARD_WIDTH;

        // 统计直接路径（扩展棋盘上的两点间，最短路径）的棋子数
        int minCol = Math.min(expandedCol1, expandedCol2);
        int maxCol = Math.max(expandedCol1, expandedCol2);
        int directPathObstacles = 0;

        for (int expandedC = minCol + 1; expandedC < maxCol; expandedC++) {
            // 将扩展棋盘上的坐标映射回原棋盘
            int originalCol = mapExpandedColToOriginal(expandedC);
            if (board.getPiece(row, originalCol) != null) {
                directPathObstacles++;
            }
        }

        // 统计环绕路径的棋子数
        // 环绕路径是绕过棋盘另一端的路径
        // 总的中间棋子数 - 直接路径的棋子数 = 环绕路径的棋子数
        int totalMiddlePieces = 0;
        for (int c = 0; c < BOARD_WIDTH; c++) {
            if (board.getPiece(row, c) != null && c != col1 && c != col2) {
                totalMiddlePieces++;
            }
        }

        int wrapPathObstacles = totalMiddlePieces - directPathObstacles;
        if (wrapPathObstacles < 0) wrapPathObstacles = 0;

        return new int[]{directPathObstacles, wrapPathObstacles};
    }

    /**
     * 将扩展棋盘上的列坐标映射回原棋盘
     * 扩展棋盘：[0-8]左镜像, [9-17]原始, [18-26]右镜像
     */
    private int mapExpandedColToOriginal(int expandedCol) {
        final int BOARD_WIDTH = 9;

        if (expandedCol < BOARD_WIDTH) {
            // 左镜像部分：映射到原棋盘
            return expandedCol;
        } else if (expandedCol < 2 * BOARD_WIDTH) {
            // 中间原始部分
            return expandedCol - BOARD_WIDTH;
        } else {
            // 右镜像部分：映射到原棋盘
            return expandedCol - 2 * BOARD_WIDTH;
        }
    }

    /**
     * 检查垂直方向两点间是否无棋子阻挡（不含端点）
     */
    private boolean isClearVerticalPath(int fromRow, int toRow, int col) {
        int step = fromRow < toRow ? 1 : -1;
        for (int r = fromRow + step; r != toRow; r += step) {
            if (board.getPiece(r, col) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查水平方向两点间是否无棋子阻挡（不含端点）
     * 支持左右连通模式
     * 返回boolean[2]：
     * index 0: 直接路径是否清晰
     * index 1: 环绕路径是否清晰（仅在左右连通模式有效）
     */
    private boolean[] isClearHorizontalPath(int row, int fromCol, int toCol) {
        boolean directPathClear = true;
        boolean wrapPathClear = true;

        // 检查直接路径
        int step = fromCol < toCol ? 1 : -1;
        for (int c = fromCol + step; c != toCol; c += step) {
            if (board.getPiece(row, c) != null) {
                directPathClear = false;
                break;
            }
        }

        // 如果启用左右连通，检查环绕路径
        if (r(RuleConstants.LEFT_RIGHT_CONNECTED)) {
            wrapPathClear = true;
            // 环绕路径是绕过棋盘另一端的路径
            // 计算环绕路径上的棋子数
            int totalPiecesInRow = 0;
            for (int c = 0; c < 9; c++) {
                if (board.getPiece(row, c) != null) {
                    totalPiecesInRow++;
                }
            }

            // 统计直接路径的棋子数
            int directPathPieces = 0;
            int minCol = Math.min(fromCol, toCol);
            int maxCol = Math.max(fromCol, toCol);
            for (int c = minCol + 1; c < maxCol; c++) {
                if (board.getPiece(row, c) != null) {
                    directPathPieces++;
                }
            }

            // 环绕路径的棋子数 = 总数 - 两个端点 - 直接路径上的棋子数
            int occupiedEndPoints = 0;
            if (board.getPiece(row, fromCol) != null) occupiedEndPoints++;
            if (board.getPiece(row, toCol) != null) occupiedEndPoints++;

            int wrapPathPieces = totalPiecesInRow - occupiedEndPoints - directPathPieces;
            wrapPathClear = (wrapPathPieces == 0);
        }

        return new boolean[]{directPathClear, wrapPathClear};
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

            if (r(RuleConstants.LEFT_RIGHT_CONNECTED)) {
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

            if (r(RuleConstants.LEFT_RIGHT_CONNECTED)) {
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

    // --- 象（相）- 修正版 ---
    private boolean isValidElephantMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        if (!r(RuleConstants.NO_RIVER_LIMIT)) {
            if (piece.isRed() && toRow < 5) return false;
            if (!piece.isRed() && toRow > 4) return false;
        }
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        boolean isStandardMove = rowDiff == 2 && colDiff == 2;
        boolean isWrapMove = false;
        if (r(RuleConstants.LEFT_RIGHT_CONNECTED) && r(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT) && colDiff > 4) {
            int wrappedColDiff = 9 - colDiff;
            isWrapMove = rowDiff == 2 && wrappedColDiff == 2;
        }
        if (!isStandardMove && !isWrapMove) return false;
        int midRow = (fromRow + toRow) / 2;
        int midCol = isWrapMove ? ((fromCol + toCol + 9) / 2) % 9 : (fromCol + toCol) / 2;
        if (r(RuleConstants.UNBLOCK_PIECE) && r(RuleConstants.UNBLOCK_ELEPHANT_EYE)) return true;
        return board.getPiece(midRow, midCol) == null;
    }

    // --- 马（马）---
    private boolean isValidHorseMove(int fromRow, int fromCol, int toRow, int toCol) {
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        boolean isStandardMove = (rowDiff == 1 && colDiff == 2) || (rowDiff == 2 && colDiff == 1);
        boolean isWrapMove = false;
        if (r(RuleConstants.LEFT_RIGHT_CONNECTED) && r(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE) && colDiff > 4) {
            int wrappedColDiff = 9 - colDiff;
            isWrapMove = (rowDiff == 1 && wrappedColDiff == 2) || (rowDiff == 2 && wrappedColDiff == 1);
        }
        if (!isStandardMove && !isWrapMove) return false;
        int midRow, midCol;
        if (rowDiff == 1) { midRow = fromRow; midCol = (fromCol + toCol) / 2; }
        else { midRow = (fromRow + toRow) / 2; midCol = fromCol; }
        if (r(RuleConstants.UNBLOCK_PIECE) && r(RuleConstants.UNBLOCK_HORSE_LEG)) return true;
        return board.getPiece(midRow, midCol) == null;
    }

    /**
     * 王（帥）：在宫内活动
     */
    private boolean isValidKingMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        Piece target = board.getPiece(toRow, toCol);
        if (target != null && target.isRed() != piece.isRed()) {
            boolean targetIsKing = target.getType() == Piece.Type.RED_KING || target.getType() == Piece.Type.BLACK_KING;
            // 取消对将规则：启用时不允许王见王（飞将）
            if (r(RuleConstants.DISABLE_FACING_GENERALS) && targetIsKing) {
                return false; // 禁止王见王
            }
            // 王不见王：同列或同行无阻挡，允许直接吃对方王（飞将吃王 / 将吃帥）
            if (targetIsKing && fromCol == toCol && isClearVerticalPath(fromRow, toRow, fromCol)) {
                return true; // 同列无阻挡
            }
            if (targetIsKing && fromRow == toRow) {
                boolean[] pathResults = isClearHorizontalPath(fromRow, fromCol, toCol);
                boolean directPathClear = pathResults[0];
                boolean wrapPathClear = pathResults[1];
                // 同行：直接路径清晰或（左右连通模式下）环绕路径清晰
                if (r(RuleConstants.LEFT_RIGHT_CONNECTED)) {
                    return directPathClear || wrapPathClear;
                } else {
                    return directPathClear;
                }
            }
        }
        if (!r(RuleConstants.NO_RIVER_LIMIT) && !isEnabled(RuleRegistry.ALLOW_FLYING_GENERAL.registryName) && !r(RuleConstants.ALLOW_KING_CROSS_RIVER)) {
            int minCol = 3, maxCol = 5;
            int minRow = piece.isRed() ? 7 : 0;
            int maxRow = piece.isRed() ? 9 : 2;
            if (toRow < minRow || toRow > maxRow || toCol < minCol || toCol > maxCol) return false;
        }
        int rowDiff = Math.abs(toRow - fromRow);
        int rawColDiff = Math.abs(toCol - fromCol);
        int colDiff = r(RuleConstants.LEFT_RIGHT_CONNECTED) ? Math.min(rawColDiff, 9 - rawColDiff) : rawColDiff;
        if (r(RuleConstants.INTERNATIONAL_KING)) {
            return rowDiff <= 1 && colDiff <= 1 && (rowDiff + colDiff) > 0;
        } else {
            return (rowDiff + colDiff) == 1;
        }
    }

    /**
     * 士（仕）- 修正版
     * 采用了"三倍宽棋盘"的逻辑思路来处理左右联通的斜线移动
     */
    private boolean isValidAdvisorMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        if (!r(RuleConstants.ADVISOR_CAN_LEAVE)) {
            int minCol = 3, maxCol = 5;
            int minRow = piece.isRed() ? 7 : 0;
            int maxRow = piece.isRed() ? 9 : 2;
            if (toRow < minRow || toRow > maxRow || toCol < minCol || toCol > maxCol) return false;
        }
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        if (!r(RuleConstants.INTERNATIONAL_ADVISOR)) {
            return rowDiff == 1 && colDiff == 1;
        }
        if (rowDiff == 0 && colDiff > 0) {
            int[] obstacles = countHorizontalObstacles(fromRow, fromCol, toCol);
            return r(RuleConstants.LEFT_RIGHT_CONNECTED) ? (obstacles[0] == 0 || obstacles[1] == 0) : obstacles[0] == 0;
        }
        if (colDiff == 0 && rowDiff > 0) {
            int step = toRow > fromRow ? 1 : -1;
            for (int r = fromRow + step; r != toRow; r += step) {
                if (board.getPiece(r, fromCol) != null) return false;
            }
            return true;
        }
        int[] potentialTargetCols = r(RuleConstants.LEFT_RIGHT_CONNECTED)
            ? new int[]{toCol, toCol - 9, toCol + 9}
            : new int[]{toCol};
        for (int virtualToCol : potentialTargetCols) {
            int virtualColDiff = Math.abs(virtualToCol - fromCol);
            if (rowDiff == virtualColDiff && rowDiff > 0) {
                if (checkDiagonalPathWithWrap(fromRow, fromCol, toRow, virtualToCol)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 辅助方法：检查斜线路径是否有障碍（支持虚拟坐标）
     * 逻辑：在虚拟的宽棋盘上行走，但在检查棋子时映射回 0-8 范围
     * * @param r1 起点行
     * @param c1 起点列
     * @param r2 终点行
     * @param virtualC2 终点虚拟列（可能是负数，也可能 > 8）
     */
    private boolean checkDiagonalPathWithWrap(int r1, int c1, int r2, int virtualC2) {
        int rowStep = r2 > r1 ? 1 : -1;
        int colStep = virtualC2 > c1 ? 1 : -1;

        // 步数等于行差（因为是斜线，行差等于列差）
        int steps = Math.abs(r2 - r1);

        int currentR = r1;
        int currentVirtualC = c1;

        // 从起点走向终点（不包含起点，不包含终点）
        for (int i = 0; i < steps - 1; i++) {
            currentR += rowStep;
            currentVirtualC += colStep;

            // 核心：将虚拟坐标映射回真实棋盘坐标 [0, 8]
            // Java的 % 运算符对负数会保留负号，所以用 (a % n + n) % n 公式
            int realC = (currentVirtualC % 9 + 9) % 9;

            // 如果路径上有子，则此路不通
            if (board.getPiece(currentR, realC) != null) {
                return false;
            }
        }

        // 路径畅通（终点是否有己方棋子的判断在 isValidMove 开头已经做过了）
        return true;
    }

    /**
     * 兵（兵）
     */
    //左右连通
    private boolean isValidSoldierMove(int fromRow, int fromCol, int toRow, int toCol, Piece piece) {
        int rowDiff = toRow - fromRow;
        int rawColDiff = Math.abs(toCol - fromCol);
        int colDiff = r(RuleConstants.LEFT_RIGHT_CONNECTED) ? Math.min(rawColDiff, 9 - rawColDiff) : rawColDiff;
        boolean isRed = piece.isRed();
        boolean hasCrossedRiver;
        if (isRed) {
            hasCrossedRiver = fromRow < 5;
            if (!hasCrossedRiver && !r(RuleConstants.NO_RIVER_LIMIT)) {
                if (rowDiff == -1 && colDiff == 0) return true;
                if (r(RuleConstants.ALLOW_INSIDE_RETREAT) && r(RuleConstants.PAWN_CAN_RETREAT) && rowDiff == 1 && colDiff == 0) return true;
                return false;
            } else {
                if (rowDiff == -1 && colDiff == 0) return true;
                if (rowDiff == 0 && colDiff == 1) return true;
                if (r(RuleConstants.PAWN_CAN_RETREAT) && rowDiff == 1 && colDiff == 0) return true;
            }
        } else {
            hasCrossedRiver = fromRow > 4;
            if (!hasCrossedRiver && !r(RuleConstants.NO_RIVER_LIMIT)) {
                if (rowDiff == 1 && colDiff == 0) return true;
                if (r(RuleConstants.ALLOW_INSIDE_RETREAT) && r(RuleConstants.PAWN_CAN_RETREAT) && rowDiff == -1 && colDiff == 0) return true;
                return false;
            } else {
                if (rowDiff == 1 && colDiff == 0) return true;
                if (rowDiff == 0 && colDiff == 1) return true;
                if (r(RuleConstants.PAWN_CAN_RETREAT) && rowDiff == -1 && colDiff == 0) return true;
            }
        }
        return false;
    }
}
