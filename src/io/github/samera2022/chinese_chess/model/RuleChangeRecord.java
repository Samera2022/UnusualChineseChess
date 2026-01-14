package io.github.samera2022.chinese_chess.model;

/**
 * 玩法变更记录 - 记录游戏过程中的玩法变更
 */
public class RuleChangeRecord implements HistoryItem {
    private final String ruleKey;      // 规则键名（如 RuleConstants.ALLOW_FLYING_GENERAL）
    private final Object oldValue;     // 旧值
    private final Object newValue;     // 新值
    private final int afterMoveIndex;  // 在哪一步着法之后发生的变更（-1表示游戏开始前）

    public RuleChangeRecord(String ruleKey, Object oldValue, Object newValue, int afterMoveIndex) {
        this.ruleKey = ruleKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.afterMoveIndex = afterMoveIndex;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    public int getAfterMoveIndex() {
        return afterMoveIndex;
    }

    @Override
    public String toString() {
        return String.format("[Rule] %s: %s -> %s", ruleKey, oldValue, newValue);
    }
}
