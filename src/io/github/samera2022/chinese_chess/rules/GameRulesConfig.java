package io.github.samera2022.chinese_chess.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
    private final Map<String, Object> ruleValues = new HashMap<>();

    public enum ChangeSource {
        UI,
        NETWORK,
        API,
        UNDO,
        INTERNAL_CONSISTENCY
    }

    public interface RuleChangeListener {
        void onRuleChanged(String key, Object oldValue, Object newValue, ChangeSource source);
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

    public GameRulesConfig() {
        resetToDefault();
    }

    public void resetToDefault() {
        ruleValues.clear();
        for (RuleRegistry rule : RuleRegistry.values()) {
            ruleValues.put(rule.registryName, rule.defaultValue);
        }
        enforceRuleConsistency(ChangeSource.API);
    }

    public void set(String registryName, Object value, ChangeSource source) {
        Object oldValue = ruleValues.get(registryName);
        if (Objects.equals(oldValue, value)) {
            return;
        }
        ruleValues.put(registryName, value);
        notifyRuleChange(registryName, oldValue, value, source);
        if (source != ChangeSource.INTERNAL_CONSISTENCY) {
            enforceRuleConsistency(ChangeSource.INTERNAL_CONSISTENCY);
        }
    }

    public boolean getBoolean(String registryName) {
        Object value = ruleValues.get(registryName);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    public int getInt(String registryName) {
        Object value = ruleValues.get(registryName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    public String getString(String registryName) {
        Object value = ruleValues.get(registryName);
        return value != null ? String.valueOf(value) : null;
    }

    public Map<String, Object> getAllValues() {
        return new HashMap<>(ruleValues);
    }

    private void enforceRuleConsistency(ChangeSource source) {
        boolean iterationChanged;
        do {
            iterationChanged = false;
            Map<String, Object> currentValuesSnapshot = new HashMap<>(ruleValues);

            for (RuleRegistry rule : RuleRegistry.values()) {
                String registryName = rule.registryName;
                
                if (getBoolean(registryName) && !rule.canBeEnabled(currentValuesSnapshot)) {
                    set(registryName, false, source);
                    iterationChanged = true;
                }
            }
        } while (iterationChanged);
    }

    public void addRuleChangeListener(RuleChangeListener l) {
        if (l == null) return;
        synchronized (changeListeners) {
            if (!changeListeners.contains(l)) {
                changeListeners.add(l);
            }
        }
    }

    public void removeRuleChangeListener(RuleChangeListener l) {
        if (l == null) return;
        synchronized (changeListeners) {
            changeListeners.remove(l);
        }
    }

    private void notifyRuleChange(String key, Object oldV, Object newV, ChangeSource src) {
        List<RuleChangeListener> copy;
        synchronized (changeListeners) {
            copy = new ArrayList<>(changeListeners);
        }
        if (!copy.isEmpty()) {
            notifyExecutor.submit(() -> {
                for (RuleChangeListener l : copy) {
                    listenerExecutor.submit(() -> {
                        try {
                            l.onRuleChanged(key, oldV, newV, src);
                        } catch (Throwable inner) {
                            System.err.println("[GameRulesConfig] listener threw: " + inner);
                        }
                    });
                }
            });
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Object> entry : ruleValues.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                json.addProperty(key, (Boolean) value);
            } else if (value instanceof Number) {
                json.addProperty(key, (Number) value);
            } else {
                json.addProperty(key, String.valueOf(value));
            }
        }
        return json;
    }

    public void applySnapshot(JsonObject snapshot, ChangeSource source) {
        if (snapshot == null) return;
        
        for (Map.Entry<String, JsonElement> entry : snapshot.entrySet()) {
            String key = entry.getKey();
            JsonElement el = entry.getValue();
            if (el.isJsonPrimitive()) {
                Object newValue = null;
                if (el.getAsJsonPrimitive().isBoolean()) {
                    newValue = el.getAsBoolean();
                } else if (el.getAsJsonPrimitive().isNumber()) {
                    newValue = el.getAsNumber();
                } else {
                    newValue = el.getAsString();
                }
                
                Object oldValue = ruleValues.get(key);
                if (!Objects.equals(oldValue, newValue)) {
                    ruleValues.put(key, newValue);
                    notifyRuleChange(key, oldValue, newValue, source);
                }
            }
        }
        enforceRuleConsistency(ChangeSource.INTERNAL_CONSISTENCY);
    }

    public void shutdown() {
        try {
            notifyExecutor.shutdownNow();
            listenerExecutor.shutdownNow();
            notifyExecutor.awaitTermination(200, TimeUnit.MILLISECONDS);
            listenerExecutor.awaitTermination(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
