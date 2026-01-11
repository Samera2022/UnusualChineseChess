package io.github.samera2022.chinese_chess.model;

/**
 * 玩法变更记录 - 记录游戏过程中的玩法变更
 */
public class RuleChangeRecord implements HistoryItem {
    private final String ruleKey;      // 规则键名（如 RuleConstants.ALLOW_FLYING_GENERAL）
    private final String displayName;  // 显示名称（如 "允许飞将"）
    private final boolean enabled;     // true表示启用（+），false表示取消（-）
    private final int afterMoveIndex;  // 在哪一步着法之后发生的变更（-1表示游戏开始前）

    public RuleChangeRecord(String ruleKey, String displayName, boolean enabled, int afterMoveIndex) {
        this.ruleKey = ruleKey;
        this.displayName = displayName;
        this.enabled = enabled;
        this.afterMoveIndex = afterMoveIndex;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getAfterMoveIndex() {
        return afterMoveIndex;
    }

    @Override
    public String toString() {
        String prefix = enabled ? "[Rule] + " : "[Rule] - ";
        return prefix + "[" + displayName + "]";
    }
}

