package io.github.samera2022.chinese_chess.server.train;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 LRU 的全局置换表。
 * 利用 128GB 大内存，缓存已评估的局面。
 *
 * Key: BoardState 的 hash（zzHash 或 SHA-256 前 8 字节）
 * Value: (policy, value)
 */
public class TranspositionTable {
    private static final int DEFAULT_MAX_ENTRIES = 10_000_000;

    private final LinkedHashMap<Long, TtEntry> cache;

    public TranspositionTable() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public TranspositionTable(int maxEntries) {
        this.cache = new LinkedHashMap<Long, TtEntry>(16, 0.75f, true) {
            @Override
            @SuppressWarnings("unchecked")
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > maxEntries;
            }
        };
    }

    public synchronized Optional<TtEntry> get(long hash) {
        return Optional.ofNullable(cache.get(hash));
    }

    public synchronized void put(long hash, float[] policy, float value) {
        cache.put(hash, new TtEntry(policy, value));
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized boolean contains(long hash) {
        return cache.containsKey(hash);
    }

    /**
     * 置换表条目：策略数组 + 价值评分。
     */
    public record TtEntry(float[] policy, float value) {}
}
