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
    public static final String NO_RIVER_LIMIT_PAWN = "noRiverLimitPawn";         // 兵过河限制
    public static final String ALLOW_CAPTURE_OWN_PIECE = "allowCaptureOwnPiece"; // 允许吃自己的棋子
    public static final String ALLOW_PIECE_STACKING = "allowPieceStacking";      // 允许棋子堆叠
    public static final String MAX_STACKING_COUNT = "maxStackingCount";          // 最大堆叠数量
    public static final String ALLOW_CAPTURE_CONVERSION = "allowCaptureConversion"; // 允许俘虏（吃子改为转换归己方）
    public static final String DEATH_MATCH_UNTIL_VICTORY = "deathMatchUntilVictory"; // 死战方休（必须吃掉全部棋子）

    // UI相关配置
    public static final String ALLOW_UNDO = "allowUndo";                         // 允许悔棋
    public static final String SHOW_HINTS = "showHints";                         // 显示提示
}
