package io.github.samera2022.chinese_chess.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 服务端配置类，集中管理所有 ucc-server 配置参数。
 * 单例模式（Holder 懒加载），在首次访问时从 classpath 加载 server.properties。
 */
public final class ServerConfig {

    // ---- 字段 ----

    /** WebSocket 服务端口，默认 8080 */
    private final int wsPort;
    /** gRPC Python 推理服务地址，默认 "localhost" */
    private final String grpcHost;
    /** gRPC Python 推理服务端口，默认 50051 */
    private final int grpcPort;
    /** gRPC Python 训练服务端口，默认 50052 */
    private final int grpcTrainingPort;
    /** Netty worker 线程数，默认 CPU 核心数×2 */
    private final int workerThreads;
    /** 自博弈 Worker 数量，默认 100 */
    private final int selfPlayWorkers;
    /** 批量推理大小，默认 64 */
    private final int batchSize;
    /** 批量超时毫秒，默认 5 */
    private final long batchTimeoutMs;
    /** Disruptor RingBuffer 大小，默认 16384 */
    private final int ringBufferSize;
    /** 置换表最大条目数，默认 10_000_000 */
    private final int ttMaxEntries;
    /** 房间超时分钟数，默认 30 */
    private final int roomTimeoutMinutes;
    /** MCTS 探索常数，默认 1.414 */
    private final double mctsExplorationConstant;
    /** MCTS 每次模拟次数，默认 800 */
    private final int mctsSimulations;
    /** MCTS 时间限制毫秒，默认 5000 */
    private final long mctsTimeLimitMs;

    // ---- 构造器 ----

    private ServerConfig() {
        Properties props = new Properties();
        try (InputStream is = ServerConfig.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // 使用默认值
        }

        this.wsPort = getInt(props, "server.ws.port", 8080);
        this.grpcHost = props.getProperty("server.grpc.host", "localhost");
        this.grpcPort = getInt(props, "server.grpc.port", 50051);
        this.grpcTrainingPort = getInt(props, "server.grpc.training_port", 50052);
        this.workerThreads = getInt(props, "server.worker.threads",
                Runtime.getRuntime().availableProcessors() * 2);
        this.selfPlayWorkers = getInt(props, "server.selfplay.workers", 100);
        this.batchSize = getInt(props, "server.batch.size", 64);
        this.batchTimeoutMs = getLong(props, "server.batch.timeout_ms", 5);
        this.ringBufferSize = getInt(props, "server.ringbuffer.size", 16384);
        this.ttMaxEntries = getInt(props, "server.tt.max_entries", 10_000_000);
        this.roomTimeoutMinutes = getInt(props, "server.room.timeout_minutes", 30);
        this.mctsExplorationConstant = getDouble(props, "server.mcts.exploration_constant", 1.414);
        this.mctsSimulations = getInt(props, "server.mcts.simulations", 800);
        this.mctsTimeLimitMs = getLong(props, "server.mcts.time_limit_ms", 5000);
    }

    // ---- 单例访问 ----

    private static final class Holder {
        static final ServerConfig INSTANCE = new ServerConfig();
    }

    /**
     * 获取 ServerConfig 的单例实例。
     *
     * @return 全局唯一的 ServerConfig 实例
     */
    public static ServerConfig getInstance() {
        return Holder.INSTANCE;
    }

    // ---- 公共 getter ----

    public int getWsPort() {
        return wsPort;
    }

    public String getGrpcHost() {
        return grpcHost;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    public int getGrpcTrainingPort() {
        return grpcTrainingPort;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public int getSelfPlayWorkers() {
        return selfPlayWorkers;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getBatchTimeoutMs() {
        return batchTimeoutMs;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public int getTtMaxEntries() {
        return ttMaxEntries;
    }

    public int getRoomTimeoutMinutes() {
        return roomTimeoutMinutes;
    }

    public double getMctsExplorationConstant() {
        return mctsExplorationConstant;
    }

    public int getMctsSimulations() {
        return mctsSimulations;
    }

    public long getMctsTimeLimitMs() {
        return mctsTimeLimitMs;
    }

    // ---- 辅助方法 ----

    private int getInt(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long getLong(Properties props, String key, long defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double getDouble(Properties props, String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
