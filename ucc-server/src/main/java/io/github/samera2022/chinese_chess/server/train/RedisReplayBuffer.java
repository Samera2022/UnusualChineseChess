package io.github.samera2022.chinese_chess.server.train;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Redis-backed replay buffer for off-policy training.
 * <p>
 * Thread-safe: uses {@link JedisPool} (already thread-safe) + per-key lock for atomic operations.
 * </p>
 *
 * <h3>序列化格式</h3>
 * <pre>
 *   [state长度(int)][state数据(float[])][policy长度(int)][policy数据(float[])][value(float)]
 * </pre>
 */
@SuppressWarnings("unused")
public class RedisReplayBuffer {

    private final JedisPool pool;
    private final ReentrantLock lock = new ReentrantLock();

    // ==================== 构造函数 ====================

    /**
     * 通过 Redis 连接参数构造。
     *
     * @param host     Redis 主机地址
     * @param port     Redis 端口
     * @param password Redis 密码（可为 null 或空字符串）
     */
    public RedisReplayBuffer(String host, int port, String password) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(16);
        config.setMaxIdle(8);
        if (password != null && !password.isEmpty()) {
            this.pool = new JedisPool(config, host, port, 2000, password);
        } else {
            this.pool = new JedisPool(config, host, port, 2000);
        }
    }

    /**
     * 通过外部创建的 {@link JedisPool} 构造。
     *
     * @parampool pool 连接池
     */
    public RedisReplayBuffer(JedisPool pool) {
        this.pool = pool;
    }

    // ==================== 核心方法 ====================

    /**
     * 将样本序列化后推入 Redis list。
     *
     * @param key    Redis key
     * @param state  棋盘状态特征（float 数组）
     * @param policy 策略概率分布（float 数组）
     * @param value  局面价值标量
     */
    public void pushSample(String key, float[] state, float[] policy, float value) {
        byte[] data = serialize(state, policy, value);
        try (Jedis jedis = pool.getResource()) {
            jedis.rpush(key.getBytes(), data);
        }
    }

    /**
     * 从 Redis list 中随机采样 {@code batchSize} 个样本。
     * <p>
     * 使用 {@code lindex} 按字节随机访问，避免 {@code lrange} 全量拉取。
     * </p>
     *
     * @param key       Redis key
     * @param batchSize 目标采样数量
     * @return List of float[][3]，每个元素为 { state, policy, value }
     */
    public List<float[][]> sampleBatch(String key, int batchSize) {
        try (Jedis jedis = pool.getResource()) {
            long total = jedis.llen(key);
            if (total == 0) {
                return new ArrayList<>();
            }

            int actualSize = (int) Math.min(batchSize, total);
            List<float[][]> batch = new ArrayList<>(actualSize);

            Random rand = new Random();
            boolean[] picked = new boolean[(int) total];
            int pickedCount = 0;

            while (pickedCount < actualSize) {
                int idx = rand.nextInt((int) total);
                if (!picked[idx]) {
                    picked[idx] = true;
                    pickedCount++;

                    byte[] data = jedis.lindex(key.getBytes(), idx);
                    if (data != null && data.length > 0) {
                        float[][] sample = deserialize(data);
                        batch.add(sample);
                    }
                }
            }

            return batch;
        }
    }

    /**
     * 获取 replay buffer 大小（Redis list 长度）。
     *
     * @param key Redis key
     * @return list 长度
     */
    public long size(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.llen(key);
        }
    }

    /**
     * 清空 replay buffer（删除 Redis key）。
     *
     * @param key Redis key
     */
    public void clear(String key) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
        }
    }

    /**
     * 关闭 Jedis 连接池。
     */
    public void close() {
        pool.close();
    }

    // ==================== 序列化 / 反序列化 ====================

    /**
     * 将 state + policy + value 序列化为字节数组。
     * <p>
     * 格式：<br>
     * {@code [state长度(int)][state数据(float[])][policy长度(int)][policy数据(float[])][value(float)]}
     * </p>
     */
    private byte[] serialize(float[] state, float[] policy, float value) {
        int stateBytes = state.length * 4;   // float = 4 bytes
        int policyBytes = policy.length * 4;
        ByteBuffer buf = ByteBuffer.allocate(4 + stateBytes + 4 + policyBytes + 4);
        buf.putInt(state.length);
        for (float f : state) {
            buf.putFloat(f);
        }
        buf.putInt(policy.length);
        for (float f : policy) {
            buf.putFloat(f);
        }
        buf.putFloat(value);
        return buf.array();
    }

    /**
     * 从字节数组反序列化为 {@code float[][3]}。
     * <p>
     * 返回数组索引：<br>
     * {@code [0] = state, [1] = policy, [2] = new float[]{value}}
     * </p>
     */
    private float[][] deserialize(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int stateLen = buf.getInt();
        float[] state = new float[stateLen];
        for (int i = 0; i < stateLen; i++) {
            state[i] = buf.getFloat();
        }
        int policyLen = buf.getInt();
        float[] policy = new float[policyLen];
        for (int i = 0; i < policyLen; i++) {
            policy[i] = buf.getFloat();
        }
        float value = buf.getFloat();
        return new float[][]{state, policy, new float[]{value}};
    }
}
