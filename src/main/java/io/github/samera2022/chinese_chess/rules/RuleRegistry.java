package io.github.samera2022.chinese_chess.rules;

import io.github.samera2022.chinese_chess.consts.Consts;

import java.util.Map;

public enum RuleRegistry {
    ALLOW_UNDO(
            "allow_undo",
            "允许悔棋",
            "",
            new String[]{},
            new String[]{},
            true,
            "outside",
            Consts.CHECK_BOX,
            true
    ),
    SHOW_HINTS(
            "show_hints",
            "显示提示",
            "",
            new String[]{},
            new String[]{},
            true,
            "outside",
            Consts.CHECK_BOX,
            false
    ),
    ALLOW_FORCE_MOVE(
            "allow_force_move",
            "允许强制移动",
            "",
            new String[]{},
            new String[]{},
            true,
            "outside",
            Consts.CHECK_BOX,
            false
    ),
    ALLOW_FLYING_GENERAL(
            "allow_flying_general",
            "允许飞将",
            "",
            new String[]{},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    DISABLE_FACING_GENERALS(
            "disable_facing_generals",
            "取消对将",
            "",
            new String[]{},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    ADVISOR_CAN_LEAVE(
            "advisor_can_leave",
            "允许出仕",
            "",
            new String[]{},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    INTERNATIONAL_KING(
            "international_king",
            "国际化王",
            "",
            new String[]{},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    INTERNATIONAL_ADVISOR(
            "international_advisor",
            "国际化仕",
            "",
            new String[]{},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    NO_RIVER_LIMIT(
            "no_river_limit",
            "取消过河限制",
            "",
            new String[]{},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    PAWN_CAN_RETREAT(
            "pawn_can_retreat",
            "兵卒可后退",
            "",
            new String[]{},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    ALLOW_INSIDE_RETREAT(
            "allow_inside_retreat",
            "允许境内后退",
            "",
            new String[]{"pawn_can_retreat"},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    PAWN_PROMOTION(
            "pawn_promotion",
            "允许兵卒底线晋升",
            "",
            new String[]{},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    ALLOW_OWN_BASE_LINE(
            "allow_own_base_line",
            "允许己方底线晋升",
            "",
            new String[]{"pawn_promotion"},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    UNBLOCK_PIECE(
            "unblock_piece",
            "取消卡子",
            "",
            new String[]{},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    UNBLOCK_HORSE_LEG(
            "unblock_horse_leg",
            "取消卡马脚",
            "",
            new String[]{"unblock_piece"},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    UNBLOCK_ELEPHANT_EYE(
            "unblock_elephant_eye",
            "取消卡象眼",
            "",
            new String[]{"unblock_piece"},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    ALLOW_CAPTURE_OWN_PIECE(
            "allow_capture_own_piece",
            "允许自己吃自己",
            "",
            new String[]{},
            new String[]{"allow_piece_stacking"},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    ALLOW_CAPTURE_CONVERSION(
            "allow_capture_conversion",
            "允许俘虏（吃子改为转换归己方）",
            "",
            new String[]{},
            new String[]{},
            true,
            "extended",
            Consts.CHECK_BOX,
            false
    ),
    LEFT_RIGHT_CONNECTED(
            "left_right_connected",
            "左右连通",
            "",
            new String[]{},
            new String[]{},
            true,
            "special",
            Consts.CHECK_BOX,
            false
    ),
    LEFT_RIGHT_CONNECTED_HORSE(
            "left_right_connected_horse",
            "额外允许马",
            "",
            new String[]{"left_right_connected"},
            new String[]{},
            true,
            "special",
            Consts.CHECK_BOX,
            false
    ),
    LEFT_RIGHT_CONNECTED_ELEPHANT(
            "left_right_connected_elephant",
            "额外允许象",
            "",
            new String[]{"left_right_connected"},
            new String[]{},
            true,
            "special",
            Consts.CHECK_BOX,
            false
    ),
    DEATH_MATCH_UNTIL_VICTORY(
            "death_match_until_victory",
            "死战方休（必须吃掉全部棋子）",
            "",
            new String[]{},
            new String[]{},
            true,
            "special",
            Consts.CHECK_BOX,
            false
    ),
    ALLOW_PIECE_STACKING(
            "allow_piece_stacking",
            "允许棋子堆叠",
            "",
            new String[]{},
            new String[]{"allow_capture_own_piece"},
            true,
            "special",
            Consts.CHECK_BOX,
            false
    ),
    MAX_STACKING_COUNT(
            "max_stacking_count",
            "最大堆叠数量",
            "",
            new String[]{"allow_piece_stacking"},
            new String[]{},
            true,
            "special",
            Consts.TEXT_AREA,
            2
    ),
    ALLOW_CARRY_PIECES_ABOVE(
            "allow_carry_pieces_above",
            "允许背负上方棋子",
            "",
            new String[]{"allow_piece_stacking"},
            new String[]{},
            true,
            "special",
            Consts.CHECK_BOX,
            false
    ),;
    public final String registryName;
    public final String displayName;
    public final String tooltip;
    public final String[] dependentRegistryNames;
    public final String[] conflictRegistryNames;
    public final boolean displayOnUI;
    public final String targetComponent;
    public final int type;
    public final Object defaultValue;

    RuleRegistry(String registryName, String displayName, String tooltip, String[] dependentRegistryNames, String[] conflictRegistryNames, boolean displayOnUI, String targetComponent, int type, Object defaultValue) {
        this.registryName = registryName;
        this.displayName = displayName;
        this.tooltip = tooltip;
        this.dependentRegistryNames = dependentRegistryNames;
        this.conflictRegistryNames = conflictRegistryNames;
        this.displayOnUI = displayOnUI;
        this.targetComponent = targetComponent;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public static RuleRegistry getByRegistryName(String name) {
        for (RuleRegistry r : values()) {
            if (r.registryName.equals(name)) return r;
        }
        return null;
    }

    public boolean canBeEnabled(Map<String, Object> enabledMap) {
        // 检查依赖玩法是否全部启用
        for (String dep : dependentRegistryNames) {
            if (!Boolean.TRUE.equals(enabledMap.get(dep))) {
                return false;
            }
        }
        // 检查冲突玩法是否全部未启用
        for (String conf : conflictRegistryNames) {
            if (Boolean.TRUE.equals(enabledMap.get(conf))) {
                return false;
            }
        }
        return true;
    }
}
