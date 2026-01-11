package io.github.samera2022.chinese_chess.rules;

/**
 * 游戏规则常量定义
 * 集中管理所有规则键的名称，避免硬编码字符串
 */
public class RuleConstants {
    // 基础玩法规则
    public static final String ALLOW_FLYING_GENERAL = "allowFlyingGeneral";      // 允许飞将
    public static final String DISABLE_FACING_GENERALS = "disableFacingGenerals"; // 取消对将
    public static final String PAWN_CAN_RETREAT = "pawnCanRetreat";              // 兵卒可以后退
    public static final String NO_RIVER_LIMIT = "noRiverLimit";                  // 取消过河限制（所有棋子）
    public static final String ADVISOR_CAN_LEAVE = "advisorCanLeave";            // 仕可以离开宫
    public static final String INTERNATIONAL_KING = "internationalKing";         // 国际象棋风格的王
    public static final String PAWN_PROMOTION = "pawnPromotion";                 // 兵卒晋升规则
    public static final String ALLOW_OWN_BASE_LINE = "allowOwnBaseLine";         // 兵到达己方底线可以晋升
    public static final String ALLOW_INSIDE_RETREAT = "allowInsideRetreat";      // 兵可以在宫内后退
    public static final String INTERNATIONAL_ADVISOR = "internationalAdvisor";   // 国际象棋风格的仕
    public static final String ALLOW_ELEPHANT_CROSS_RIVER = "allowElephantCrossRiver"; // 象可以过河
    public static final String ALLOW_ADVISOR_CROSS_RIVER = "allowAdvisorCrossRiver";   // 仕可以过河
    public static final String ALLOW_KING_CROSS_RIVER = "allowKingCrossRiver";   // 王可以过河
    public static final String LEFT_RIGHT_CONNECTED = "leftRightConnected";      // 左右相连（所有棋子）
    public static final String LEFT_RIGHT_CONNECTED_HORSE = "leftRightConnectedHorse";     // 左右相连（仅马）
    public static final String LEFT_RIGHT_CONNECTED_ELEPHANT = "leftRightConnectedElephant"; // 左右相连（仅象）

    // 取消卡子规则
    public static final String UNBLOCK_PIECE = "unblockPiece";                   // 通用取消卡子
    public static final String UNBLOCK_HORSE_LEG = "unblockHorseLeg";            // 马脚可以被跳过
    public static final String UNBLOCK_ELEPHANT_EYE = "unblockElephantEye";      // 象眼可以被跳过

    // 特殊规则
    public static final String ALLOW_CAPTURE_OWN_PIECE = "allowCaptureOwnPiece"; // 允许吃自己的棋子
    public static final String ALLOW_PIECE_STACKING = "allowPieceStacking";      // 允许棋子堆叠
    public static final String MAX_STACKING_COUNT = "maxStackingCount";          // 最大堆叠数量
    public static final String ALLOW_CARRY_PIECES_ABOVE = "allowCarryPiecesAbove"; // 允许背负上方棋子
    public static final String ALLOW_CAPTURE_CONVERSION = "allowCaptureConversion"; // 允许俘虏（吃子改为转换归己方）
    public static final String DEATH_MATCH_UNTIL_VICTORY = "deathMatchUntilVictory"; // 死战方休（必须吃掉全部棋子）

    // UI相关配置
    public static final String ALLOW_UNDO = "allowUndo";                         // 允许悔棋
    public static final String SHOW_HINTS = "showHints";                         // 显示提示

    /**
     * 获取规则键对应的显示名称
     * @param ruleKey 规则键
     * @return 显示名称，如果找不到则返回规则键本身
     */
    public static String getDisplayName(String ruleKey) {
        switch (ruleKey) {
            case ALLOW_FLYING_GENERAL: return "允许飞将";
            case DISABLE_FACING_GENERALS: return "取消对将";
            case PAWN_CAN_RETREAT: return "兵卒可以后退";
            case NO_RIVER_LIMIT: return "取消过河限制";
            case ADVISOR_CAN_LEAVE: return "仕可以离开宫";
            case INTERNATIONAL_KING: return "国际象棋风格的王";
            case PAWN_PROMOTION: return "兵卒晋升规则";
            case ALLOW_OWN_BASE_LINE: return "兵到达己方底线可以晋升";
            case ALLOW_INSIDE_RETREAT: return "兵可以在宫内后退";
            case INTERNATIONAL_ADVISOR: return "国际象棋风格的仕";
            case ALLOW_ELEPHANT_CROSS_RIVER: return "象可以过河";
            case ALLOW_ADVISOR_CROSS_RIVER: return "仕可以过河";
            case ALLOW_KING_CROSS_RIVER: return "王可以过河";
            case LEFT_RIGHT_CONNECTED: return "左右相连";
            case LEFT_RIGHT_CONNECTED_HORSE: return "左右相连(仅马)";
            case LEFT_RIGHT_CONNECTED_ELEPHANT: return "左右相连(仅象)";
            case UNBLOCK_PIECE: return "通用取消卡子";
            case UNBLOCK_HORSE_LEG: return "马脚可以被跳过";
            case UNBLOCK_ELEPHANT_EYE: return "象眼可以被跳过";
            case ALLOW_CAPTURE_OWN_PIECE: return "允许吃自己的棋子";
            case ALLOW_PIECE_STACKING: return "允许棋子堆叠";
            case MAX_STACKING_COUNT: return "最大堆叠数量";
            case ALLOW_CARRY_PIECES_ABOVE: return "允许背负上方棋子";
            case ALLOW_CAPTURE_CONVERSION: return "允许俘虏";
            case DEATH_MATCH_UNTIL_VICTORY: return "死战方休";
            case ALLOW_UNDO: return "允许悔棋";
            case SHOW_HINTS: return "显示提示";
            default: return ruleKey;
        }
    }

    /**
     * 基础玩法结构体，持有规则键名和显示名（显示名需严格与RuleSettingsPane一致）
     */
    public static class BasicRuleInfo {
        public final String key;
        public final String displayName;
        public BasicRuleInfo(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }
    }
}
