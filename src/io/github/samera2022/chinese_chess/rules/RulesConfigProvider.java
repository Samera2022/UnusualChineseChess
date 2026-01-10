package io.github.samera2022.chinese_chess.rules;

/**
 * Provider for a single shared GameRulesConfig instance.
 * Acts as the authoritative source for rules when the application uses a global config.
 */
public final class RulesConfigProvider {
    private RulesConfigProvider() {}

    private static final GameRulesConfig INSTANCE = new GameRulesConfig();

    public static GameRulesConfig get() {
        return INSTANCE;
    }

    public static void addRuleChangeListener(GameRulesConfig.RuleChangeListener l) {
        INSTANCE.addRuleChangeListener(l);
    }

    public static void removeRuleChangeListener(GameRulesConfig.RuleChangeListener l) {
        INSTANCE.removeRuleChangeListener(l);
    }

    /**
     * Shutdown provider-managed resources (delegates to the config's notifier shutdown).
     */
    public static void shutdown() {
        try { INSTANCE.shutdownNotifier(); } catch (Throwable ignored) {}
    }
}

