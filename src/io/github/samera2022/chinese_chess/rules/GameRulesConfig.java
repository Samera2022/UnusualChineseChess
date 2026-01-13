package io.github.samera2022.chinese_chess.rules;

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
@SuppressWarnings("unused")
public class GameRulesConfig {
    // ========== 新玩法注册与启用状态维护 ==========
    private final Map<String, Boolean> ruleEnabledMap = new HashMap<>();

    /**
     * 获取玩法当前启用状态
     */
    public boolean isRuleEnabled(String registryName) {
        Boolean v = ruleEnabledMap.get(registryName);
        return v != null ? v : false;
    }

    /**
     * 设置玩法启用状态，自动校验依赖与冲突
     * @return 是否设置成功（依赖/冲突校验未通过则失败）
     */
    public boolean setRuleEnabled(String registryName, boolean enabled) {
        RuleRegistry rule = RuleRegistry.getByRegistryName(registryName);
        if (rule == null) return false;
        if (enabled) {
            // 检查依赖
            for (String dep : rule.dependentRegistryNames) {
                if (!Boolean.TRUE.equals(ruleEnabledMap.get(dep))) return false;
            }
            // 检查冲突
            for (String conf : rule.conflictRegistryNames) {
                if (Boolean.TRUE.equals(ruleEnabledMap.get(conf))) return false;
            }
        }
        ruleEnabledMap.put(registryName, enabled);
        return true;
    }

    /**
     * 获取所有玩法的当前启用状态快照
     */
    public Map<String, Boolean> getAllRuleEnabledStates() {
        return new HashMap<>(ruleEnabledMap);
    }

    /**
     * 批量设置玩法启用状态（不校验依赖/冲突，适合反序列化）
     */
    public void setAllRuleEnabledStates(Map<String, Boolean> states) {
        if (states == null) return;
        ruleEnabledMap.clear();
        ruleEnabledMap.putAll(states);
    }

    /**
     * 初始化所有玩法为默认禁用（或部分默认启用）
     */
    public void initAllRulesDefault() {
        for (RuleRegistry rule : RuleRegistry.values()) {
            ruleEnabledMap.put(rule.registryName, false);
        }
        // 可在此处设置部分玩法默认启用
        ruleEnabledMap.put("allowUndo", true);
    }

    // 自动注册规则：根据 RuleRegistry 枚举自动初始化所有规则的 isEnabled 状态
    public void autoRegisterRules() {
        for (RuleRegistry rule : RuleRegistry.values()) {
            if (!ruleEnabledMap.containsKey(rule.registryName)) {
                ruleEnabledMap.put(rule.registryName, false);
            }
        }
    }

    // ========== 监听器相关（如有需要可保留） ==========
    public enum ChangeSource {
        UI,       // changes originated from local UI actions
        NETWORK,  // changes applied from network snapshots
        API       // programmatic changes (internal code)
    }

    public interface RuleChangeListener {
        void onRuleChanged(String key, boolean oldValue, boolean newValue, ChangeSource source);
    }

    private final List<RuleChangeListener> changeListeners = new ArrayList<>();

    private final ExecutorService notifyExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RuleChangeNotifier");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService listenerExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "RuleChangeListener");
        t.setDaemon(true);
        return t;
    });

    public void shutdownNotifier() {
        try { notifyExecutor.shutdownNow(); } catch (Throwable ignored) {}
        try { listenerExecutor.shutdownNow(); } catch (Throwable ignored) {}
        try { notifyExecutor.awaitTermination(200, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try { listenerExecutor.awaitTermination(200, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    public void addRuleChangeListener(RuleChangeListener l) {
        if (l == null) return;
        synchronized (changeListeners) { changeListeners.add(l); }
    }

    public void removeRuleChangeListener(RuleChangeListener l) {
        if (l == null) return;
        synchronized (changeListeners) { changeListeners.remove(l); }
    }

    public List<RuleChangeListener> getRuleChangeListenersSnapshot() {
        synchronized (changeListeners) {
            return new ArrayList<>(changeListeners);
        }
    }

    public void transferListenersTo(GameRulesConfig target) {
        if (target == null) throw new IllegalArgumentException("target cannot be null");
        List<RuleChangeListener> toMove;
        synchronized (changeListeners) {
            toMove = new ArrayList<>(changeListeners);
        }
        if (toMove.isEmpty()) return;
        for (RuleChangeListener l : toMove) {
            if (l == null) continue;
            try {
                boolean exists = false;
                List<RuleChangeListener> targetSnapshot = target.getRuleChangeListenersSnapshot();
                for (RuleChangeListener tl : targetSnapshot) {
                    if (tl == l) { exists = true; break; }
                }
                if (!exists) {
                    target.addRuleChangeListener(l);
                }
                try { removeRuleChangeListener(l); } catch (Throwable ignored) {}
            } catch (Throwable t) {
                System.err.println("[GameRulesConfig] transferListenersTo: failed to transfer listener: " + t);
                t.printStackTrace(System.err);
            }
        }
    }

    // ========== 玩法变更通知 ==========
    public boolean setRuleEnabledWithNotify(String registryName, boolean enabled, ChangeSource source) {
        boolean oldValue = isRuleEnabled(registryName);
        boolean changed = setRuleEnabled(registryName, enabled);
        boolean newValue = isRuleEnabled(registryName);
        if (changed && oldValue != newValue) {
            List<RuleChangeListener> copy;
            synchronized (changeListeners) { copy = new ArrayList<>(changeListeners); }
            if (!copy.isEmpty()) {
                final String key = registryName;
                final boolean oldV = oldValue;
                final boolean newV = newValue;
                final ChangeSource src = source;
                notifyExecutor.submit(() -> {
                    for (RuleChangeListener l : copy) {
                        try {
                            Future<?> fut = listenerExecutor.submit(() -> {
                                try {
                                    l.onRuleChanged(key, oldV, newV, src);
                                } catch (Throwable inner) {
                                    System.err.println("[GameRulesConfig] listener threw: " + inner);
                                    inner.printStackTrace(System.err);
                                }
                            });
                            try {
                                fut.get(200, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException te) {
                                fut.cancel(true);
                                System.err.println("[GameRulesConfig] listener timed out for key=" + key + " listener=" + l);
                            } catch (ExecutionException ee) {
                                System.err.println("[GameRulesConfig] listener execution failed for key=" + key + " listener=" + l + ": " + ee.getCause());
                                if (ee.getCause() != null) ee.getCause().printStackTrace(System.err);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                System.err.println("[GameRulesConfig] listener wait interrupted");
                            }
                        } catch (Throwable t) {
                            System.err.println("[GameRulesConfig] failed invoking listener: " + t);
                            t.printStackTrace(System.err);
                        }
                    }
                });
            }
        }
        return changed;
    }

    /**
     * 通用设置玩法状态（支持监听器通知）
     */
    public void set(String registryName, Object value, ChangeSource source) {
        boolean oldValue = isRuleEnabled(registryName);
        boolean newValue = false;
        if (value instanceof Boolean) {
            newValue = (Boolean) value;
        } else if (value instanceof Number) {
            newValue = ((Number) value).intValue() != 0;
        } else if (value instanceof String) {
            newValue = Boolean.parseBoolean((String) value);
        }
        boolean changed = setRuleEnabled(registryName, newValue);
        if (changed && oldValue != newValue) {
            List<RuleChangeListener> copy;
            synchronized (changeListeners) { copy = new ArrayList<>(changeListeners); }
            if (!copy.isEmpty()) {
                final String key = registryName;
                final boolean oldV = oldValue;
                final boolean newV = newValue;
                final ChangeSource src = source;
                notifyExecutor.submit(() -> {
                    for (RuleChangeListener l : copy) {
                        try {
                            Future<?> fut = listenerExecutor.submit(() -> {
                                try {
                                    l.onRuleChanged(key, oldV, newV, src);
                                } catch (Throwable inner) {
                                    System.err.println("[GameRulesConfig] listener threw: " + inner);
                                    inner.printStackTrace(System.err);
                                }
                            });
                            try {
                                fut.get(200, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException te) {
                                fut.cancel(true);
                                System.err.println("[GameRulesConfig] listener timed out for key=" + key + " listener=" + l);
                            } catch (ExecutionException ee) {
                                System.err.println("[GameRulesConfig] listener execution failed for key=" + key + " listener=" + l + ": " + ee.getCause());
                                if (ee.getCause() != null) ee.getCause().printStackTrace(System.err);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                System.err.println("[GameRulesConfig] listener wait interrupted");
                            }
                        } catch (Throwable t) {
                            System.err.println("[GameRulesConfig] failed invoking listener: " + t);
                            t.printStackTrace(System.err);
                        }
                    }
                });
            }
        }
    }

    /**
     * 获取玩法的 boolean 状态
     */
    public boolean getBoolean(String registryName) {
        return isRuleEnabled(registryName);
    }

    /**
     * 获取玩法的 int 状态（true=1, false=0）
     */
    public int getInt(String registryName) {
        return isRuleEnabled(registryName) ? 1 : 0;
    }

    /**
     * 导出玩法状态为 JSON
     */
    public com.google.gson.JsonObject toJson() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        for (RuleRegistry rule : RuleRegistry.values()) {
            json.addProperty(rule.registryName, isRuleEnabled(rule.registryName));
        }
        return json;
    }

    /**
     * 从 JSON 加载玩法状态
     */
    public void loadFromJson(com.google.gson.JsonObject json) {
        if (json == null) return;
        for (RuleRegistry rule : RuleRegistry.values()) {
            if (json.has(rule.registryName)) {
                setRuleEnabled(rule.registryName, json.get(rule.registryName).getAsBoolean());
            }
        }
    }

    // 构造函数或初始化流程中调用 autoRegisterRules()
    @Override
    protected void finalize() throws Throwable {
        try { shutdownNotifier(); } finally { super.finalize(); }
    }
}
