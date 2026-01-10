package io.github.samera2022.chinese_chess.rules;

import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 游戏规则配置 - 统一管理所有游戏规则
 * 该类作为单一数据源，避免规则在多处重复定义
 */
public class GameRulesConfig {
    // 基础玩法规则
    @RuleKey(RuleConstants.ALLOW_FLYING_GENERAL)
    private boolean allowFlyingGeneral = false;      // 允许飞将
    @RuleKey(RuleConstants.DISABLE_FACING_GENERALS)
    private boolean disableFacingGenerals = false;   // 取消对将（允许王见王）
    @RuleKey(RuleConstants.PAWN_CAN_RETREAT)
    private boolean pawnCanRetreat = false;          // 兵卒可以后退
    @RuleKey(RuleConstants.NO_RIVER_LIMIT)
    private boolean noRiverLimit = false;            // 取消过河限制（所有棋子）
    @RuleKey(RuleConstants.ADVISOR_CAN_LEAVE)
    private boolean advisorCanLeave = false;         // 仕可以离开宫
    @RuleKey(RuleConstants.INTERNATIONAL_KING)
    private boolean internationalKing = false;       // 国际象棋风格的王
    @RuleKey(RuleConstants.PAWN_PROMOTION)
    private boolean pawnPromotion = false;           // 兵卒晋升规则
    @RuleKey(RuleConstants.ALLOW_OWN_BASE_LINE)
    private boolean allowOwnBaseLine = false;        // 兵到达己方底线可以晋升
    @RuleKey(RuleConstants.ALLOW_INSIDE_RETREAT)
    private boolean allowInsideRetreat = false;      // 兵可以在宫内后退
    @RuleKey(RuleConstants.INTERNATIONAL_ADVISOR)
    private boolean internationalAdvisor = false;    // 国际象棋风格的仕
    @RuleKey(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER)
    private boolean allowElephantCrossRiver = false; // 象可以过河
    @RuleKey(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER)
    private boolean allowAdvisorCrossRiver = false;  // 仕可以过河
    @RuleKey(RuleConstants.ALLOW_KING_CROSS_RIVER)
    private boolean allowKingCrossRiver = false;     // 王可以过河
    @RuleKey(RuleConstants.LEFT_RIGHT_CONNECTED)
    private boolean leftRightConnected = false;      // 左右相连（所有棋子）
    @RuleKey(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE)
    private boolean leftRightConnectedHorse = false; // 左右相连（仅马）
    @RuleKey(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT)
    private boolean leftRightConnectedElephant = false; // 左右相连（仅象）

    // 取消卡子规则
    @RuleKey(RuleConstants.UNBLOCK_PIECE)
    private boolean unblockPiece = false;            // 通用取消卡子
    @RuleKey(RuleConstants.UNBLOCK_HORSE_LEG)
    private boolean unblockHorseLeg = false;         // 马脚可以被跳过
    @RuleKey(RuleConstants.UNBLOCK_ELEPHANT_EYE)
    private boolean unblockElephantEye = false;      // 象眼可以被跳过

    // 特殊规则
    @RuleKey(RuleConstants.ALLOW_CAPTURE_OWN_PIECE)
    private boolean allowCaptureOwnPiece = false;    // 允许吃自己的棋子
    @RuleKey(RuleConstants.ALLOW_PIECE_STACKING)
    private boolean allowPieceStacking = false;      // 允许棋子堆叠
    @RuleKey(RuleConstants.MAX_STACKING_COUNT)
    private int maxStackingCount = 2;                // 最大堆叠数量
    @RuleKey(RuleConstants.ALLOW_CARRY_PIECES_ABOVE)
    private boolean allowCarryPiecesAbove = false;   // 允许背负上方棋子
    @RuleKey(RuleConstants.ALLOW_CAPTURE_CONVERSION)
    private boolean allowCaptureConversion = false;  // 允许俘虏：吃子改为转换归己方
    @RuleKey(RuleConstants.DEATH_MATCH_UNTIL_VICTORY)
    private boolean deathMatchUntilVictory = false;  // 死战方休：必须吃掉全部棋子

    // UI相关配置
    @RuleKey(RuleConstants.ALLOW_UNDO)
    private boolean allowUndo = true;                // 允许悔棋
    @RuleKey(RuleConstants.SHOW_HINTS)
    private boolean showHints = true;                // 显示提示

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
     * Helper: get boolean rule value (interprets numbers/strings as needed)
     */
    public boolean getBoolean(String ruleName) {
        Object v = get(ruleName);
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    /**
     * Helper: get int rule value (interprets booleans/strings as needed)
     */
    public int getInt(String ruleName) {
        Object v = get(ruleName);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // listeners for rule changes (delta notification)
    public enum ChangeSource {
        UI,       // changes originated from local UI actions
        NETWORK,  // changes applied from network snapshots
        API       // programmatic changes (internal code)
    }

    public interface RuleChangeListener {
        void onRuleChanged(String key, Object oldValue, Object newValue, ChangeSource source);
    }

    private final List<RuleChangeListener> changeListeners = new ArrayList<>();

    // single-threaded executor to run change notifications off the calling thread
    private final ExecutorService notifyExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RuleChangeNotifier");
        t.setDaemon(true);
        return t;
    });

    // pooled executor to run each listener with a timeout so one slow listener won't block others
    private final ExecutorService listenerExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "RuleChangeListener");
        t.setDaemon(true);
        return t;
    });

    /**
     * Shutdown the internal notifier executor (best-effort).
     */
    public void shutdownNotifier() {
        try {
            notifyExecutor.shutdownNow();
        } catch (Throwable ignored) {}
        try {
            listenerExecutor.shutdownNow();
        } catch (Throwable ignored) {}
        try {
            notifyExecutor.awaitTermination(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try {
            listenerExecutor.awaitTermination(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    public void addRuleChangeListener(RuleChangeListener l) {
        if (l == null) return;
        synchronized (changeListeners) { changeListeners.add(l); }
    }

    public void removeRuleChangeListener(RuleChangeListener l) {
        if (l == null) return;
        synchronized (changeListeners) { changeListeners.remove(l); }
    }

    /**
     * 根据规则常量设置规则值（默认来源为 API）
     * @param ruleName 规则常量（来自RuleConstants）
     * @param value 规则值（Boolean或Integer）
     */
    public void set(String ruleName, Object value) {
        set(ruleName, value, ChangeSource.API);
    }

    /**
     * 根据规则常量设置规则值，并指定变更来源
     * @param ruleName 规则常量
     * @param value 规则值
     * @param source 变更来源（用于避免网络回环等）
     */
    public void set(String ruleName, Object value, ChangeSource source) {
        Field f = fieldFor(ruleName);
        if (f == null) return;
        Class<?> type = f.getType();
        try {
            Object oldVal = f.get(this);
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
                if (RuleConstants.MAX_STACKING_COUNT.equals(ruleName)) {
                    v = Math.max(1, v);
                    f.setInt(this, v);
                } else {
                    f.setInt(this, v);
                }
            } else {
                if (value != null && type.isInstance(value)) {
                    f.set(this, value);
                }
            }
            Object newVal = f.get(this);
            if (!Objects.equals(oldVal, newVal)) {
                List<RuleChangeListener> copy;
                synchronized (changeListeners) { copy = new ArrayList<>(changeListeners); }
                if (!copy.isEmpty()) {
                    // execute notifications asynchronously
                    final String key = ruleName;
                    final Object oldV = oldVal;
                    final Object newV = newVal;
                    final ChangeSource src = source;
                    notifyExecutor.submit(() -> {
                        for (RuleChangeListener l : copy) {
                            try {
                                // run each listener on the listenerExecutor and wait with timeout
                                Future<?> fut = listenerExecutor.submit(() -> {
                                    l.onRuleChanged(key, oldV, newV, src);
                                });
                                try {
                                    // wait at most 500ms for a listener to finish
                                    fut.get(500, TimeUnit.MILLISECONDS);
                                } catch (TimeoutException te) {
                                    System.err.println("[GameRulesConfig] RuleChangeListener timeout for key=" + key + " listener=" + l);
                                    fut.cancel(true);
                                } catch (ExecutionException ee) {
                                    System.err.println("[GameRulesConfig] RuleChangeListener threw exception for key=" + key + " listener=" + l);
                                    if (ee.getCause() != null) ee.getCause().printStackTrace(System.err);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            } catch (Throwable t) {
                                // defensive catch: log error but keep notifying others
                                System.err.println("[GameRulesConfig] Unexpected error while notifying rule change listener: " + t);
                                t.printStackTrace(System.err);
                            }
                        }
                    });
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
