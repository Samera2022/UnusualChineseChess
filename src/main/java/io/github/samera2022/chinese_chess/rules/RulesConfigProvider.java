package io.github.samera2022.chinese_chess.rules;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provider for a replaceable shared GameRulesConfig instance.
 * Supports hot-replace of the underlying config and exposes provider-level instance change listeners.
 */
public final class RulesConfigProvider {
    private RulesConfigProvider() {}

    // volatile so readers see the latest instance immediately
    private static volatile GameRulesConfig INSTANCE = new GameRulesConfig();

    // listeners that want to know when the provider swaps to a new instance
    public interface InstanceChangeListener {
        /**
         * Called after the instance has been replaced. oldInstance may be null if there was no previous.
         */
        void onInstanceReplaced(GameRulesConfig oldInstance, GameRulesConfig newInstance);
    }

    private static final List<InstanceChangeListener> instanceListeners = new CopyOnWriteArrayList<>();

    public static GameRulesConfig get() {
        return INSTANCE;
    }

    /**
     * Replace the underlying GameRulesConfig instance.
     * This will attempt to gracefully shutdown the old instance's notifier and
     * transfer no listeners automatically. Callers that need listener transfer
     * can register provider-level listeners and perform explicit migration.
     */
    public static void replace(GameRulesConfig newInstance) {
        if (newInstance == null) throw new IllegalArgumentException("newInstance cannot be null");
        GameRulesConfig old = INSTANCE;
        if (old == newInstance) return;
        
        INSTANCE = newInstance;
        // notify instance listeners
        for (InstanceChangeListener l : instanceListeners) {
            try { l.onInstanceReplaced(old, newInstance); } catch (Throwable ignored) {}
        }
        // attempt to shutdown old notifier to avoid leaked threads
        try { if (old != null) old.shutdown(); } catch (Throwable ignored) {}
    }

    public static void addInstanceChangeListener(InstanceChangeListener l) { instanceListeners.add(l); }
    public static void removeInstanceChangeListener(InstanceChangeListener l) { instanceListeners.remove(l); }

    // Convenience proxies to the current instance
    public static void addRuleChangeListener(GameRulesConfig.RuleChangeListener l) {
        if (l == null) return;
        GameRulesConfig inst = INSTANCE;
        if (inst != null) inst.addRuleChangeListener(l);
    }

    public static void removeRuleChangeListener(GameRulesConfig.RuleChangeListener l) {
        if (l == null) return;
        GameRulesConfig inst = INSTANCE;
        if (inst != null) inst.removeRuleChangeListener(l);
    }

    /**
     * Shutdown provider-managed resources (delegates to the config's notifier shutdown).
     */
    public static void shutdown() {
        GameRulesConfig inst = INSTANCE;
        if (inst != null) {
            try { inst.shutdown(); } catch (Throwable ignored) {}
        }
    }
}
