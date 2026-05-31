package io.github.samera2022.chinese_chess.core.rules;

/**
 * 将 GameRulesConfig 编码为强化学习所需的规则向量。
 *
 * <p>编码分为两类：
 * <ul>
 *   <li>{@link #encode(GameRulesConfig)}：27 位布尔规则向量，每维取值 0.0f 或 1.0f。</li>
 *   <li>{@link #encodeContinuous(GameRulesConfig)}：长度 1 的连续值向量，内容为归一化的 max_stacking_count。</li>
 * </ul>
 */
public final class RuleEncoder {

    private RuleEncoder() {
        // 工具类，禁止实例化
    }

    /**
     * 将 GameRulesConfig 中的 27 个布尔规则编码为 float[27]。
     *
     * <p>27 个布尔位与 RuleRegistry 枚举常量的对应关系：
     * <pre>
     *   0  | ALLOW_UNDO                    ("allow_undo")
     *   1  | SHOW_HINTS                    ("show_hints")
     *   2  | ALLOW_FORCE_MOVE              ("allow_force_move")
     *   3  | ALLOW_FLYING_GENERAL          ("allow_flying_general")
     *   4  | DISABLE_FACING_GENERALS       ("disable_facing_generals")
     *   5  | ADVISOR_CAN_LEAVE             ("advisor_can_leave")
     *   6  | INTERNATIONAL_KING            ("international_king")
     *   7  | INTERNATIONAL_ADVISOR         ("international_advisor")
     *   8  | NO_RIVER_LIMIT                ("no_river_limit")
     *   9  | PAWN_CAN_RETREAT              ("pawn_can_retreat")
     *  10  | ALLOW_INSIDE_RETREAT          ("allow_inside_retreat")
     *  11  | PAWN_PROMOTION                ("pawn_promotion")
     *  12  | ALLOW_OWN_BASE_LINE           ("allow_own_base_line")
     *  13  | UNBLOCK_PIECE                 ("unblock_piece")
     *  14  | UNBLOCK_HORSE_LEG             ("unblock_horse_leg")
     *  15  | UNBLOCK_ELEPHANT_EYE          ("unblock_elephant_eye")
     *  16  | ALLOW_CAPTURE_OWN_PIECE       ("allow_capture_own_piece")
     *  17  | ALLOW_CAPTURE_CONVERSION      ("allow_capture_conversion")
     *  18  | LEFT_RIGHT_CONNECTED          ("left_right_connected")
     *  19  | DEATH_MATCH_UNTIL_VICTORY     ("death_match_until_victory")
     *  20  | ALLOW_PIECE_STACKING          ("allow_piece_stacking")
     *  21  | TOP_BOTTOM_CONNECTED          ("top_bottom_connected")
     *  22  | LEFT_RIGHT_CONNECTED_HORSE    ("left_right_connected_horse")
     *  23  | LEFT_RIGHT_CONNECTED_ELEPHANT ("left_right_connected_elephant")
     *  24  | ALLOW_CARRY_PIECES_ABOVE      ("allow_carry_pieces_above")
     *  25  | TOP_BOTTOM_CONNECTED_HORSE    ("top_bottom_connected_horse")
     *  26  | TOP_BOTTOM_CONNECTED_ELEPHANT ("top_bottom_connected_elephant")
     * </pre>
     *
     * @param config 游戏规则配置，不可为 null
     * @return 长度 27 的 float 数组，true → 1.0f，false → 0.0f
     */
    public static float[] encode(GameRulesConfig config) {
        float[] vec = new float[27];
        vec[0]  = config.getBoolean("allow_undo")                      ? 1.0f : 0.0f;
        vec[1]  = config.getBoolean("show_hints")                      ? 1.0f : 0.0f;
        vec[2]  = config.getBoolean("allow_force_move")                ? 1.0f : 0.0f;
        vec[3]  = config.getBoolean("allow_flying_general")            ? 1.0f : 0.0f;
        vec[4]  = config.getBoolean("disable_facing_generals")         ? 1.0f : 0.0f;
        vec[5]  = config.getBoolean("advisor_can_leave")               ? 1.0f : 0.0f;
        vec[6]  = config.getBoolean("international_king")              ? 1.0f : 0.0f;
        vec[7]  = config.getBoolean("international_advisor")           ? 1.0f : 0.0f;
        vec[8]  = config.getBoolean("no_river_limit")                  ? 1.0f : 0.0f;
        vec[9]  = config.getBoolean("pawn_can_retreat")                ? 1.0f : 0.0f;
        vec[10] = config.getBoolean("allow_inside_retreat")            ? 1.0f : 0.0f;
        vec[11] = config.getBoolean("pawn_promotion")                  ? 1.0f : 0.0f;
        vec[12] = config.getBoolean("allow_own_base_line")             ? 1.0f : 0.0f;
        vec[13] = config.getBoolean("unblock_piece")                   ? 1.0f : 0.0f;
        vec[14] = config.getBoolean("unblock_horse_leg")               ? 1.0f : 0.0f;
        vec[15] = config.getBoolean("unblock_elephant_eye")            ? 1.0f : 0.0f;
        vec[16] = config.getBoolean("allow_capture_own_piece")         ? 1.0f : 0.0f;
        vec[17] = config.getBoolean("allow_capture_conversion")        ? 1.0f : 0.0f;
        vec[18] = config.getBoolean("left_right_connected")            ? 1.0f : 0.0f;
        vec[19] = config.getBoolean("death_match_until_victory")       ? 1.0f : 0.0f;
        vec[20] = config.getBoolean("allow_piece_stacking")            ? 1.0f : 0.0f;
        vec[21] = config.getBoolean("top_bottom_connected")            ? 1.0f : 0.0f;
        vec[22] = config.getBoolean("left_right_connected_horse")      ? 1.0f : 0.0f;
        vec[23] = config.getBoolean("left_right_connected_elephant")   ? 1.0f : 0.0f;
        vec[24] = config.getBoolean("allow_carry_pieces_above")        ? 1.0f : 0.0f;
        vec[25] = config.getBoolean("top_bottom_connected_horse")      ? 1.0f : 0.0f;
        vec[26] = config.getBoolean("top_bottom_connected_elephant")   ? 1.0f : 0.0f;
        return vec;
    }

    /**
     * 将 max_stacking_count 编码为归一化的连续值向量。
     *
     * <p>归一化公式：{@code config.getInt("max_stacking_count") / 16.0f}。
     *
     * @param config 游戏规则配置，不可为 null
     * @return 长度 1 的 float 数组，值为 max_stacking_count / 16.0f
     */
    public static float[] encodeContinuous(GameRulesConfig config) {
        return new float[] { config.getInt("max_stacking_count") / 16.0f };
    }
}
