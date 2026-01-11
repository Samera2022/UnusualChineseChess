package io.github.samera2022.chinese_chess.model;

/**
 * 历史记录项接口 - 着法记录和玩法变更记录的共同接口
 */
public interface HistoryItem {
    /**
     * 获取该历史项的字符串表示
     * @return 字符串表示
     */
    String toString();
}

