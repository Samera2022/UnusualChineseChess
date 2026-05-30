package io.github.samera2022.chinese_chess.common.spi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AIStrategyConfig {
    private final Map<String, Object> params;

    public AIStrategyConfig(Map<String, Object> params) {
        this.params = new HashMap<>(params);
    }

    public int getInt(String key, int defaultVal) {
        Object val = params.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }

    public boolean getBoolean(String key, boolean defaultVal) {
        Object val = params.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return defaultVal;
    }

    public Map<String, Object> getAll() {
        return Collections.unmodifiableMap(params);
    }

    public AIStrategyConfig with(String key, Object val) {
        Map<String, Object> newParams = new HashMap<>(this.params);
        newParams.put(key, val);
        return new AIStrategyConfig(newParams);
    }
}
