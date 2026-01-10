package io.github.samera2022.chinese_chess.rules;

import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 游戏规则配置 - 统一管理所有游戏规则
 * 该类作为单一数据源，避免规则在多处重复定义
 */
public class GameRulesConfig {
    // 基础玩法规则
    @RuleKey(RuleConstants.ALLOW_FLYING_GENERAL)
    public boolean allowFlyingGeneral = false;      // 允许飞将
    @RuleKey(RuleConstants.DISABLE_FACING_GENERALS)
    public boolean disableFacingGenerals = false;   // 取消对将（允许王见王）
    @RuleKey(RuleConstants.PAWN_CAN_RETREAT)
    public boolean pawnCanRetreat = false;          // 兵卒可以后退
    @RuleKey(RuleConstants.NO_RIVER_LIMIT)
    public boolean noRiverLimit = false;            // 取消过河限制（所有棋子）
    @RuleKey(RuleConstants.ADVISOR_CAN_LEAVE)
    public boolean advisorCanLeave = false;         // 仕可以离开宫
    @RuleKey(RuleConstants.INTERNATIONAL_KING)
    public boolean internationalKing = false;       // 国际象棋风格的王
    @RuleKey(RuleConstants.PAWN_PROMOTION)
    public boolean pawnPromotion = false;           // 兵卒晋升规则
    @RuleKey(RuleConstants.ALLOW_OWN_BASE_LINE)
    public boolean allowOwnBaseLine = false;        // 兵到达己方底线可以晋升
    @RuleKey(RuleConstants.ALLOW_INSIDE_RETREAT)
    public boolean allowInsideRetreat = false;      // 兵可以在宫内后退
    @RuleKey(RuleConstants.INTERNATIONAL_ADVISOR)
    public boolean internationalAdvisor = false;    // 国际象棋风格的仕
    @RuleKey(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER)
    public boolean allowElephantCrossRiver = false; // 象可以过河
    @RuleKey(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER)
    public boolean allowAdvisorCrossRiver = false;  // 仕可以过河
    @RuleKey(RuleConstants.ALLOW_KING_CROSS_RIVER)
    public boolean allowKingCrossRiver = false;     // 王可以过河
    @RuleKey(RuleConstants.LEFT_RIGHT_CONNECTED)
    public boolean leftRightConnected = false;      // 左右相连（所有棋子）
    @RuleKey(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE)
    public boolean leftRightConnectedHorse = false; // 左右相连（仅马）
    @RuleKey(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT)
    public boolean leftRightConnectedElephant = false; // 左右相连（仅象）

    // 取消卡子规则
    @RuleKey(RuleConstants.UNBLOCK_PIECE)
    public boolean unblockPiece = false;            // 通用取消卡子
    @RuleKey(RuleConstants.UNBLOCK_HORSE_LEG)
    public boolean unblockHorseLeg = false;         // 马脚可以被跳过
    @RuleKey(RuleConstants.UNBLOCK_ELEPHANT_EYE)
    public boolean unblockElephantEye = false;      // 象眼可以被跳过

    // 特殊规则
    @RuleKey(RuleConstants.ALLOW_CAPTURE_OWN_PIECE)
    public boolean allowCaptureOwnPiece = false;    // 允许吃自己的棋子
    @RuleKey(RuleConstants.ALLOW_PIECE_STACKING)
    public boolean allowPieceStacking = false;      // 允许棋子堆叠
    @RuleKey(RuleConstants.MAX_STACKING_COUNT)
    public int maxStackingCount = 2;                // 最大堆叠数量
    @RuleKey(RuleConstants.ALLOW_CARRY_PIECES_ABOVE)
    public boolean allowCarryPiecesAbove = false;   // 允许背负上方棋子
    @RuleKey(RuleConstants.ALLOW_CAPTURE_CONVERSION)
    public boolean allowCaptureConversion = false;  // 允许俘虏：吃子改为转换归己方
    @RuleKey(RuleConstants.DEATH_MATCH_UNTIL_VICTORY)
    public boolean deathMatchUntilVictory = false;  // 死战方休：必须吃掉全部棋子

    // UI相关配置
    @RuleKey(RuleConstants.ALLOW_UNDO)
    public boolean allowUndo = true;                // 允许悔棋
    @RuleKey(RuleConstants.SHOW_HINTS)
    public boolean showHints = true;                // 显示提示

    // reflection-backed mapping from rule constant name -> Field
    private static final Map<String, Field> RULE_FIELDS;

    static {
        Map<String, Field> map = new HashMap<>();
        Field[] fields = GameRulesConfig.class.getDeclaredFields();
        for (Field f : fields) {
            RuleKey a = f.getAnnotation(RuleKey.class);
            if (a != null) {
                f.setAccessible(true);
                map.put(a.value(), f);
            }
        }
        RULE_FIELDS = Collections.unmodifiableMap(map);
    }

    /**
     * 创建默认配置副本
     */
    public GameRulesConfig copy() {
        GameRulesConfig config = new GameRulesConfig();
        for (Map.Entry<String, Field> e : RULE_FIELDS.entrySet()) {
            Field f = e.getValue();
            try {
                Object val = f.get(this);
                // primitives/boxed types are supported by Field.set
                f.set(config, val);
            } catch (IllegalAccessException ex) {
                // should not happen because we setAccessible(true)
                throw new RuntimeException(ex);
            }
        }
        return config;
    }

    /**
     * 转换为JsonObject（用于保存/序列化）
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Field> e : RULE_FIELDS.entrySet()) {
            String key = e.getKey();
            Field f = e.getValue();
            try {
                Object val = f.get(this);
                if (val instanceof Boolean) {
                    json.addProperty(key, (Boolean) val);
                } else if (val instanceof Number) {
                    json.addProperty(key, (Number) val);
                }
                // other types are ignored for now
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
        return json;
    }

    /**
     * 从JsonObject加载配置
     */
    public void loadFromJson(JsonObject json) {
        for (Map.Entry<String, Field> e : RULE_FIELDS.entrySet()) {
            String key = e.getKey();
            if (!json.has(key)) continue;
            Field f = e.getValue();
            Class<?> type = f.getType();
            try {
                if ((type == boolean.class) || (type == Boolean.class)) {
                    f.setBoolean(this, json.get(key).getAsBoolean());
                } else if ((type == int.class) || (type == Integer.class)) {
                    f.setInt(this, json.get(key).getAsInt());
                } else if (Number.class.isAssignableFrom(type)) {
                    // generic number handling
                    f.set(this, json.get(key).getAsString());
                } else {
                    // unsupported types are ignored
                }
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
    }


    // ========== Setters ==========
    // setters removed per request; use public fields or the dynamic set(String,Object) instead

    // ========== 通用动态访问方法 ==========

    private Field fieldFor(String ruleName) {
        return RULE_FIELDS.get(ruleName);
    }

    /**
     * 根据规则常量获取规则值
     * @param ruleName 规则常量（来自RuleConstants）
     * @return 规则值，可能是Boolean或Integer
     */
    public Object get(String ruleName) {
        Field f = fieldFor(ruleName);
        if (f == null) return null;
        try {
            return f.get(this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据规则常量设置规则值
     * @param ruleName 规则常量（来自RuleConstants）
     * @param value 规则值（Boolean或Integer）
     */
    public void set(String ruleName, Object value) {
        Field f = fieldFor(ruleName);
        if (f == null) return;
        Class<?> type = f.getType();
        try {
            if ((type == boolean.class) || (type == Boolean.class)) {
                boolean v = false;
                if (value instanceof Boolean) v = (Boolean) value;
                else if (value instanceof Number) v = ((Number) value).intValue() != 0;
                else if (value instanceof String) v = Boolean.parseBoolean((String) value);
                f.setBoolean(this, v);
            } else if ((type == int.class) || (type == Integer.class)) {
                int v = 0;
                if (value instanceof Number) v = ((Number) value).intValue();
                else if (value instanceof String) {
                    try { v = Integer.parseInt((String) value); } catch (NumberFormatException ignored) {}
                } else if (value instanceof Boolean) v = ((Boolean) value) ? 1 : 0;
                // ensure minimum for max stacking count (previously handled by setter)
                if (RuleConstants.MAX_STACKING_COUNT.equals(ruleName)) {
                    v = Math.max(1, v);
                    f.setInt(this, v);
                } else {
                    f.setInt(this, v);
                }
            } else {
                // unsupported types: try direct set if compatible
                if (value != null && type.isInstance(value)) {
                    f.set(this, value);
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据规则常量获取布尔值规则
     * @param ruleName 规则常量
     * @return 布尔值，如果规则不存在或不是布尔值则返回false
     */
    public boolean getBoolean(String ruleName) {
        Object value = get(ruleName);
        return value instanceof Boolean && (Boolean) value;
    }

    /**
     * 根据规则常量获取整数值规则
     * @param ruleName 规则常量
     * @return 整数值，如果规则不存在或不是整数值则返回0
     */
    public int getInt(String ruleName) {
        Object value = get(ruleName);
        return value instanceof Integer ? (Integer) value : 0;
    }
}

