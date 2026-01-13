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
     * 内部方法：设置玩法启用状态，自动校验依赖与冲突（仅在尝试启用时检查）
     * 不会触发监听器，也不会强制禁用依赖项。
     * @return 是否设置成功（依赖/冲突校验未通过则失败）
     */
    private boolean setRuleEnabledInternal(String registryName, boolean enabled) {
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
     * 之后会强制执行规则一致性。
     */
    public void setAllRuleEnabledStates(Map<String, Boolean> states) {
        if (states == null) return;
        ruleEnabledMap.clear();
        ruleEnabledMap.putAll(states);
        enforceRuleConsistency(ChangeSource.API); // 批量更新后强制执行一致性
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
        enforceRuleConsistency(ChangeSource.API); // 初始化后强制执行一致性
    }

    // 自动注册规则：根据 RuleRegistry 枚举自动初始化所有规则的 isEnabled 状态
    public void autoRegisterRules() {
        for (RuleRegistry rule : RuleRegistry.values()) {
            if (!ruleEnabledMap.containsKey(rule.registryName)) {
                ruleEnabledMap.put(rule.registryName, false);
            }
        }
        enforceRuleConsistency(ChangeSource.API); // 自动注册后强制执行一致性
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

    /**
     * 辅助方法：通知所有监听器规则状态已更改。
     */
    private void notifyRuleChange(String key, boolean oldV, boolean newV, ChangeSource src) {
        List<RuleChangeListener> copy;
        synchronized (changeListeners) { copy = new ArrayList<>(changeListeners); }
        if (!copy.isEmpty()) {
            final String finalKey = key;
            final boolean finalOldV = oldV;
            final boolean finalNewV = newV;
            final ChangeSource finalSrc = src;
            notifyExecutor.submit(() -> {
                for (RuleChangeListener l : copy) {
                    try {
                        Future<?> fut = listenerExecutor.submit(() -> {
                            try {
                                l.onRuleChanged(finalKey, finalOldV, finalNewV, finalSrc);
                            } catch (Throwable inner) {
                                System.err.println("[GameRulesConfig] listener threw: " + inner);
                                inner.printStackTrace(System.err);
                            }
                        });
                        try {
                            fut.get(200, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException te) {
                            fut.cancel(true);
                            System.err.println("[GameRulesConfig] listener timed out for key=" + finalKey + " listener=" + l);
                        } catch (ExecutionException ee) {
                            System.err.println("[GameRulesConfig] listener execution failed for key=" + finalKey + " listener=" + l + ": " + ee.getCause());
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

    /**
     * 强制执行依赖和冲突规则的一致性。
     * 如果任何规则被启用但其依赖未满足或存在冲突，它将被禁用。此过程是递归的。
     * @param source 导致此一致性检查的原始更改来源。
     * @return true 如果在执行一致性期间任何规则状态发生变化。
     */
    private boolean enforceRuleConsistency(ChangeSource source) {
        boolean changedAny = false;
        boolean iterationChanged;
        // 持续迭代直到不再发生变化，以处理依赖链
        do {
            iterationChanged = false;
            Map<String, Boolean> currentStatesSnapshot = new HashMap<>(ruleEnabledMap); // 使用当前状态的快照进行检查

            for (RuleRegistry rule : RuleRegistry.values()) {
                String registryName = rule.registryName;
                boolean isCurrentlyEnabled = Boolean.TRUE.equals(ruleEnabledMap.get(registryName));
                boolean canBeEnabled = rule.canBeEnabled(currentStatesSnapshot); // 根据快照检查是否可以启用

                if (isCurrentlyEnabled && !canBeEnabled) {
                    // 规则已启用但根据依赖/冲突不应启用。强制禁用它。
                    ruleEnabledMap.put(registryName, false); // 直接更新 map
                    notifyRuleChange(registryName, isCurrentlyEnabled, false, source); // 通知监听器
                    iterationChanged = true;
                    changedAny = true;
                }
            }
        } while (iterationChanged); // 如果此迭代中任何规则发生变化，则继续

        return changedAny;
    }

    /**
     * 通用设置玩法状态（支持监听器通知和依赖一致性检查）
     * 这是外部修改规则状态的主要入口点。
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

        // 尝试设置规则。这会检查尝试启用时的直接依赖。
        setRuleEnabledInternal(registryName, newValue);

        // 获取规则在直接设置后的状态，用于判断是否需要通知。
        boolean stateAfterDirectSet = isRuleEnabled(registryName);

        // 强制执行所有规则的一致性。这会禁用任何现在违反依赖/冲突的规则。
        // 传递原始 source，以便通知反映用户的操作。
        enforceRuleConsistency(source);

        // 获取规则在一致性检查后的最终状态。
        boolean finalNewValue = isRuleEnabled(registryName);

        // 如果原始规则的状态在整个过程中发生了变化，则通知。
        if (oldValue != finalNewValue) {
            notifyRuleChange(registryName, oldValue, finalNewValue, source);
        }
        // enforceRuleConsistency 已经为它所做的任何更改发出了通知。
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
        
        // 保存旧状态
        Map<String, Boolean> oldStates = new HashMap<>(ruleEnabledMap);
        
        // 更新状态
        ruleEnabledMap.clear();
        for (RuleRegistry rule : RuleRegistry.values()) {
            if (json.has(rule.registryName)) {
                ruleEnabledMap.put(rule.registryName, json.get(rule.registryName).getAsBoolean());
            } else {
                ruleEnabledMap.put(rule.registryName, false); // 默认未设置的规则为禁用
            }
        }
        
        // 强制一致性
        enforceRuleConsistency(ChangeSource.UI);
        
        // 检查差异并通知
        for (Map.Entry<String, Boolean> entry : ruleEnabledMap.entrySet()) {
            String key = entry.getKey();
            boolean newVal = entry.getValue();
            boolean oldVal = Boolean.TRUE.equals(oldStates.get(key));
            
            if (newVal != oldVal) {
                notifyRuleChange(key, oldVal, newVal, ChangeSource.UI);
            }
        }
    }

    // 构造函数或初始化流程中调用 autoRegisterRules()
    @Override
    protected void finalize() throws Throwable {
        try { shutdownNotifier(); } finally { super.finalize(); }
    }
}
