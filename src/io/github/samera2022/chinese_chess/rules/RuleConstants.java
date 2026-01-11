package io.github.samera2022.chinese_chess.rules;

/**
 * 游戏规则常量定义
 * 集中管理所有规则键的名称，避免硬编码字符串
 */
public class RuleConstants {
    // 基础玩法规则
    public static final String ALLOW_FLYING_GENERAL = "allow_flying_general";      // 允许飞将
    public static final String DISABLE_FACING_GENERALS = "disable_facing_generals"; // 取消对将
    public static final String PAWN_CAN_RETREAT = "pawn_can_retreat";              // 兵卒可以后退
    public static final String NO_RIVER_LIMIT = "no_river_limit";                  // 取消过河限制（所有棋子）
    public static final String ADVISOR_CAN_LEAVE = "advisor_can_leave";            // 仕可以离开宫
    public static final String INTERNATIONAL_KING = "international_king";         // 国际象棋风格的王
    public static final String PAWN_PROMOTION = "pawn_promotion";                 // 兵卒晋升规则
    public static final String ALLOW_OWN_BASE_LINE = "allow_own_base_line";         // 兵到达己方底线可以晋升
    public static final String ALLOW_INSIDE_RETREAT = "allow_inside_retreat";      // 兵可以在宫内后退
    public static final String INTERNATIONAL_ADVISOR = "international_advisor";   // 国际象棋风格的仕
    public static final String ALLOW_ELEPHANT_CROSS_RIVER = "allow_elephant_cross_river"; // 象可以过河
    public static final String ALLOW_ADVISOR_CROSS_RIVER = "allow_advisor_cross_river";   // 仕可以过河
    public static final String ALLOW_KING_CROSS_RIVER = "allow_king_cross_river";   // 王可以过河
    public static final String LEFT_RIGHT_CONNECTED = "left_right_connected";      // 左右相连（所有棋子）
    public static final String LEFT_RIGHT_CONNECTED_HORSE = "left_right_connected_horse";     // 左右相连（仅马）
    public static final String LEFT_RIGHT_CONNECTED_ELEPHANT = "left_right_connected_elephant"; // 左右相连（仅象）

    // 取消卡子规则
    public static final String UNBLOCK_PIECE = "unblock_piece";                   // 通用取消卡子
    public static final String UNBLOCK_HORSE_LEG = "unblock_horse_leg";            // 马脚可以被跳过
    public static final String UNBLOCK_ELEPHANT_EYE = "unblock_elephant_eye";      // 象眼可以被跳过

    // 特殊规则
    public static final String ALLOW_CAPTURE_OWN_PIECE = "allow_capture_own_piece"; // 允许吃自己的棋子
    public static final String ALLOW_PIECE_STACKING = "allow_piece_stacking";      // 允许棋子堆叠
    public static final String MAX_STACKING_COUNT = "max_stacking_count";          // 最大堆叠数量
    public static final String ALLOW_CARRY_PIECES_ABOVE = "allow_carry_pieces_above"; // 允许背负上方棋子
    public static final String ALLOW_CAPTURE_CONVERSION = "allow_capture_conversion"; // 允许俘虏（吃子改为转换归己方）
    public static final String DEATH_MATCH_UNTIL_VICTORY = "death_match_until_victory"; // 死战方休（必须吃掉全部棋子）

    // UI相关配置
    public static final String ALLOW_UNDO = "allow_undo";                         // 允许悔棋
    public static final String SHOW_HINTS = "show_hints";                         // 显示提示

//    /**
//     * 获取规则键对应的显示名称
//     * @param ruleKey 规则键
//     * @return 显示名称，如果找不到则返回规则键本身
//     */
//    public static final RuleInfo ALLOW_FLYING_GENERAL_INFO = new RuleInfo(ALLOW_FLYING_GENERAL, "允许飞将");
//    public static final RuleInfo DISABLE_FACING_GENERALS_INFO = new RuleInfo(DISABLE_FACING_GENERALS, "取消对将");
//    public static final RuleInfo PAWN_CAN_RETREAT_INFO = new RuleInfo(PAWN_CAN_RETREAT, "允许兵卒后退");
//    public static final RuleInfo NO_RIVER_LIMIT_INFO = new RuleInfo(NO_RIVER_LIMIT, "取消河界");
//    public static final RuleInfo ADVISOR_CAN_LEAVE_INFO = new RuleInfo(ADVISOR_CAN_LEAVE, "允许出仕");
//    public static final RuleInfo INTERNATIONAL_KING_INFO = new RuleInfo(INTERNATIONAL_KING, "允许国际化将");
//    public static final RuleInfo PAWN_PROMOTION_INFO = new RuleInfo(PAWN_PROMOTION, "允许兵卒底线晋升");
//    public static final RuleInfo ALLOW_OWN_BASE_LINE_INFO = new RuleInfo(ALLOW_OWN_BASE_LINE, "允许己方底线晋升");
//    public static final RuleInfo ALLOW_INSIDE_RETREAT_INFO = new RuleInfo(ALLOW_INSIDE_RETREAT, "允许兵卒境内后退");
//    public static final RuleInfo INTERNATIONAL_ADVISOR_INFO = new RuleInfo(INTERNATIONAL_ADVISOR, "允许国际化仕");
//    public static final RuleInfo ALLOW_ELEPHANT_CROSS_RIVER_INFO = new RuleInfo(ALLOW_ELEPHANT_CROSS_RIVER, "象可以过河");
//    public static final RuleInfo ALLOW_ADVISOR_CROSS_RIVER_INFO = new RuleInfo(ALLOW_ADVISOR_CROSS_RIVER, "仕可以过河");
//    public static final RuleInfo ALLOW_KING_CROSS_RIVER_INFO = new RuleInfo(ALLOW_KING_CROSS_RIVER, "王可以过河");
//    public static final RuleInfo LEFT_RIGHT_CONNECTED_INFO = new RuleInfo(LEFT_RIGHT_CONNECTED, "左右相连");
//    public static final RuleInfo LEFT_RIGHT_CONNECTED_HORSE_INFO = new RuleInfo(LEFT_RIGHT_CONNECTED_HORSE, "额外允许马");
//    public static final RuleInfo LEFT_RIGHT_CONNECTED_ELEPHANT_INFO = new RuleInfo(LEFT_RIGHT_CONNECTED_ELEPHANT, "额外允许象");
//    public static final RuleInfo UNBLOCK_PIECE_INFO = new RuleInfo(UNBLOCK_PIECE, "取消卡子");
//    public static final RuleInfo UNBLOCK_HORSE_LEG_INFO = new RuleInfo(UNBLOCK_HORSE_LEG, "取消卡马脚");
//    public static final RuleInfo UNBLOCK_ELEPHANT_EYE_INFO = new RuleInfo(UNBLOCK_ELEPHANT_EYE, "取消卡象眼");
//    public static final RuleInfo ALLOW_CAPTURE_OWN_PIECE_INFO = new RuleInfo(ALLOW_CAPTURE_OWN_PIECE, "允许自己吃自己");
//    public static final RuleInfo ALLOW_PIECE_STACKING_INFO = new RuleInfo(ALLOW_PIECE_STACKING, "允许堆叠棋子");
//    public static final RuleInfo MAX_STACKING_COUNT_INFO = new RuleInfo(MAX_STACKING_COUNT, "最大堆叠数量");
//    public static final RuleInfo ALLOW_CARRY_PIECES_ABOVE_INFO = new RuleInfo(ALLOW_CARRY_PIECES_ABOVE, "允许背负上方棋子");
//    public static final RuleInfo ALLOW_CAPTURE_CONVERSION_INFO = new RuleInfo(ALLOW_CAPTURE_CONVERSION, "允许俘虏");
//    public static final RuleInfo DEATH_MATCH_UNTIL_VICTORY_INFO = new RuleInfo(DEATH_MATCH_UNTIL_VICTORY, "死战方休");
//    /**
//     * 通过displayName查找RuleInfo，找不到返回null
//     */
//    public static RuleInfo getRuleInfoByDisplayName(String displayName) {
//        for (RuleInfo info : getAllRuleInfos()) {
//            if (info.displayName.equals(displayName)) {
//                return info;
//            }
//        }
//        return null;
//    }
//
//    /**
//     * 通过registryName查找RuleInfo，找不到返回null
//     */
//    public static RuleInfo getRuleInfoByRegistryName(String registryName) {
//        for (RuleInfo info : getAllRuleInfos()) {
//            if (info.registryName.equals(registryName)) {
//                return info;
//            }
//        }
//        return null;
//    }

//    /**
//     * 获取所有基础玩法的RuleInfo（静态方法，便于遍历）
//     */
//    public static RuleInfo[] getAllRuleInfos() {
//        return new RuleInfo[] {
//            ALLOW_FLYING_GENERAL_INFO,
//            DISABLE_FACING_GENERALS_INFO,
//            PAWN_CAN_RETREAT_INFO,
//            NO_RIVER_LIMIT_INFO,
//            ADVISOR_CAN_LEAVE_INFO,
//            INTERNATIONAL_KING_INFO,
//            PAWN_PROMOTION_INFO,
//            ALLOW_OWN_BASE_LINE_INFO,
//            ALLOW_INSIDE_RETREAT_INFO,
//            INTERNATIONAL_ADVISOR_INFO,
//            ALLOW_ELEPHANT_CROSS_RIVER_INFO,
//            ALLOW_ADVISOR_CROSS_RIVER_INFO,
//            ALLOW_KING_CROSS_RIVER_INFO,
//            LEFT_RIGHT_CONNECTED_INFO,
//            LEFT_RIGHT_CONNECTED_HORSE_INFO,
//            LEFT_RIGHT_CONNECTED_ELEPHANT_INFO,
//            UNBLOCK_PIECE_INFO,
//            UNBLOCK_HORSE_LEG_INFO,
//            UNBLOCK_ELEPHANT_EYE_INFO,
//            ALLOW_CAPTURE_OWN_PIECE_INFO,
//            ALLOW_PIECE_STACKING_INFO,
//            MAX_STACKING_COUNT_INFO,
//            ALLOW_CARRY_PIECES_ABOVE_INFO,
//            ALLOW_CAPTURE_CONVERSION_INFO,
//            DEATH_MATCH_UNTIL_VICTORY_INFO
//        };
//    }
//
//    /**
//     * 基础玩法结构体，持有规则键名和显示名（显示名需严格与RuleSettingsPane一致）
//     */
//    public static class RuleInfo {
//        public final String registryName;
//        public final String displayName;
//        public RuleInfo(String registryName, String displayName) {
//            this.registryName = registryName;
//            this.displayName = displayName;
//        }
//    }
}

