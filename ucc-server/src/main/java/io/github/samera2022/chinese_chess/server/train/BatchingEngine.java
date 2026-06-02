package io.github.samera2022.chinese_chess.server.train;

import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.server.config.ServerConfig;
import io.github.samera2022.chinese_chess.server.net.GrpcInferenceClient;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 生产者-消费者批处理引擎。
 *
 * <p>100-120 个 {@link SelfPlayWorker} 产出推理请求 →
 * {@link RingBuffer} → BatchAssembler → gRPC（通过 {@link InferenceClient}）→ Python 推理服务。</p>
 *
 * <p>使用 Java 21 虚拟线程 + Disruptor RingBuffer 实现高吞吐低延迟。</p>
 */
public class BatchingEngine {

    private static final Logger logger = LoggerFactory.getLogger(BatchingEngine.class);

    private static final int BATCH_SIZE = 64;
    private static final long BATCH_TIMEOUT_MS = 5;

    private final RingBuffer<InferenceEvent> ringBuffer;
    private final InferenceClient inferenceClient;
    private final ExecutorService workerPool;
    private final TranspositionTable tt;
    private final int ringBufferSize;
    private final Disruptor<InferenceEvent> disruptor;
    private final ScheduledExecutorService scheduler;

    // ══════════════════════════════════════════════
    // 嵌套 record：GameBatchResult
    // ══════════════════════════════════════════════

    /**
     * 并行自博弈批次的返回结果。
     *
     * <p>同时包含等待所有 Worker 完成的 {@link CompletableFuture} 和 Worker 引用列表，
     * 供调用者在所有对局完成后遍历收集训练样本。</p>
     *
     * @param future  在所有 Worker 执行完毕后完成的 Future
     * @param workers 本次启动的所有 SelfPlayWorker 实例
     */
    public record GameBatchResult(CompletableFuture<Void> future, java.util.List<SelfPlayWorker> workers) {}

    // ══════════════════════════════════════════════
    // 内部接口：InferenceClient（占位，子任务 J 实现）
    // ══════════════════════════════════════════════

    /**
     * 推理客户端接口。
     * <p>接收 {@link BoardState} 对象列表而非已展平的 float 张量，
     * 实现类自行决定如何序列化（如 gRPC Protobuf 或本地张量转换）。</p>
     */
    public interface InferenceClient {
        /**
         * 批量推理。
         *
         * @param states      棋盘状态列表
         * @param ruleVectors 规则向量列表，每个元素为 28 位规则编码
         * @return 推理结果列表，每个元素为 {@code [value, policy_0, policy_1, ..., policy_n]}，
         *         即 value + 完整 policy 分布（长度 = rows × cols）
         */
        List<float[]> batchInfer(List<BoardState> states, List<float[]> ruleVectors);
    }

    // ══════════════════════════════════════════════
    // 内部类：InferenceEvent
    // ══════════════════════════════════════════════

    /**
     * Disruptor RingBuffer 事件。
     * <p>每个事件代表一个推理请求，包含局面、规则向量以及用于异步回调的 {@link CompletableFuture}。</p>
     */
    public static class InferenceEvent {
        /** 局面快照 */
        public BoardState board;
        /** 规则向量（28 位：27 布尔 + 1 连续值） */
        public float[] ruleVector;
        /** 异步回调：完成后返回 policy+value 组合数组 */
        public CompletableFuture<float[]> future;

        /** 清除事件数据以便 RingBuffer 复用。 */
        void clear() {
            this.board = null;
            this.ruleVector = null;
            this.future = null;
        }
    }

    // ══════════════════════════════════════════════
    // 构造器
    // ══════════════════════════════════════════════

    /**
     * 构造 BatchingEngine。
     *
     * @param config          服务端配置（单例）
     * @param inferenceClient 推理客户端（占位接口实现）
     */
    public BatchingEngine(ServerConfig config, InferenceClient inferenceClient) {
        this.ringBufferSize = config.getRingBufferSize();
        this.inferenceClient = inferenceClient;
        this.tt = new TranspositionTable(config.getTtMaxEntries());

        // 创建 BatchEventHandler
        BatchEventHandler batchHandler = new BatchEventHandler();

        // 创建 Disruptor RingBuffer
        this.disruptor = new Disruptor<>(
                InferenceEvent::new,
                ringBufferSize,
                Thread.ofVirtual().factory(),
                ProducerType.MULTI,
                new BlockingWaitStrategy()
        );
        // 使用 Disruptor 标准的 EventHandler 注册消费者
        this.disruptor.handleEventsWith(batchHandler);
        this.ringBuffer = disruptor.getRingBuffer();
        this.disruptor.start();

        // 创建定时任务，定期刷新未满批（超时机制）
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "batch-flush-timer");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(
                batchHandler::flushIfNeeded,
                BATCH_TIMEOUT_MS,
                BATCH_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
        );

        // 创建虚拟线程池
        this.workerPool = Executors.newVirtualThreadPerTaskExecutor();

        logger.info("BatchingEngine initialized: ringBufferSize={}, batchSize={}, batchTimeoutMs={}",
                ringBufferSize, BATCH_SIZE, BATCH_TIMEOUT_MS);
    }

    // ══════════════════════════════════════════════
    // 公共方法
    // ══════════════════════════════════════════════

    /**
     * 启动指定数量的自博弈 Worker（旧版 fire-and-forget 接口，保留兼容）。
     *
     * @param numWorkers Worker 数量
     * @param rules      游戏规则配置
     * @deprecated 请使用 {@link #startParallelGames(int, GameRulesConfig)}，它返回
     *             {@link CompletableFuture} 供调用者等待所有 Worker 完成。
     */
    @Deprecated
    public void startSelfPlay(int numWorkers, GameRulesConfig rules) {
        startParallelGames(numWorkers, rules).future().join();
    }

    /**
     * 启动指定数量的并行自博弈 Worker，每局对弈完成后自动结束。
     *
     * <p>返回的 {@link CompletableFuture} 在所有 Worker 完成对局后完成，
     * 调用者可通过 {@code .join()} 等待，无需 {@code Thread.sleep()}。</p>
     *
     * @param numWorkers Worker 数量
     * @param rules      游戏规则配置
     * @return 在所有 Worker 执行完毕后完成的 Future
     */
    public GameBatchResult startParallelGames(int numWorkers, GameRulesConfig rules) {
        logger.info("Starting {} self-play workers (parallel, one-shot mode)", numWorkers);
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[numWorkers];
        List<SelfPlayWorker> workers = new ArrayList<>(numWorkers);
        for (int i = 0; i < numWorkers; i++) {
            SelfPlayWorker worker = new SelfPlayWorker(i, rules, this);
            workers.add(worker);
            futures[i] = CompletableFuture.runAsync(worker, workerPool);
        }
        return new GameBatchResult(CompletableFuture.allOf(futures), workers);
    }

    /**
     * 提交推理请求到 RingBuffer。
     *
     * @param state      局面快照
     * @param ruleVector 规则向量（28 位）
     * @return 异步结果 Future，完成后返回 policy+value 组合数组
     */
    public CompletableFuture<float[]> submitInference(BoardState state, float[] ruleVector) {
        CompletableFuture<float[]> future = new CompletableFuture<>();
        long seq = ringBuffer.next();
        try {
            InferenceEvent event = ringBuffer.get(seq);
            event.board = state;
            event.ruleVector = ruleVector;
            event.future = future;
        } finally {
            ringBuffer.publish(seq);
        }
        return future;
    }

    /**
     * 获取置换表。
     *
     * @return TranspositionTable 实例
     */
    public TranspositionTable getTranspositionTable() {
        return tt;
    }

    /**
     * 热更新推理模型的权重（强化学习闭环）。
     *
     * <p>将训练后的模型权重推送给推理服务（{@code inference_server.py}），
     * 使后续的 MCTS 推理使用更新后的模型。</p>
     *
     * @param weightsData 模型权重的序列化字节
     * @param iteration   训练迭代编号
     * @return true 表示更新成功
     */
    public boolean updateInferenceModel(byte[] weightsData, int iteration) {
        if (inferenceClient instanceof GrpcInferenceClient) {
            return ((GrpcInferenceClient) inferenceClient).updateModel(weightsData, iteration);
        }
        logger.warn("InferenceClient is not GrpcInferenceClient, cannot hot-update model");
        return false;
    }

    // ══════════════════════════════════════════════
    // 内部类：BatchEventHandler（Disruptor 标准消费者）
    // ══════════════════════════════════════════════

    /**
     * 实现 Disruptor 标准的 {@link EventHandler} 接口，
     * 在 {@link #onEvent(InferenceEvent, long, boolean)} 中积累事件到本地批量列表，
     * 达到 {@link #BATCH_SIZE} 后调用 {@link #flushBatch(List)} 执行批量推理。
     *
     * <p>同时暴露 {@link #flushIfNeeded()} 方法供定时任务调用，实现超时刷新未满批。</p>
     */
    private class BatchEventHandler implements EventHandler<InferenceEvent> {

        private final List<InferenceEvent> batch = new ArrayList<>(BATCH_SIZE);
        private long lastFlush = System.currentTimeMillis();

        @Override
        public void onEvent(InferenceEvent event, long sequence, boolean endOfBatch) {
            synchronized (this) {
                batch.add(event);
                if (batch.size() >= BATCH_SIZE) {
                    flushBatch(batch);
                    batch.clear();
                    lastFlush = System.currentTimeMillis();
                }
            }
        }

        /**
         * 定时任务调用，检查并刷新未满批。
         */
        public void flushIfNeeded() {
            synchronized (this) {
                if (!batch.isEmpty() && System.currentTimeMillis() - lastFlush >= BATCH_TIMEOUT_MS) {
                    flushBatch(batch);
                    batch.clear();
                    lastFlush = System.currentTimeMillis();
                }
            }
        }
    }

    /**
     * 执行批量推理并分派结果到各个 Future。
     *
     * <p>将 batch 中的 {@link BoardState} 通过 {@link #boardStateToTensor} 转换为
     * 棋盘张量，调用 {@link InferenceClient#batchInfer} 执行批量推理，
     * 解析返回的 [value, policy] 结果并分派到各 event 的 {@link CompletableFuture}。</p>
     *
     * @param batch 待推理的事件批次
     */
    private void flushBatch(List<InferenceEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }

        logger.debug("Flushing batch of {} events", batch.size());

        try {
            List<BoardState> states = new ArrayList<>(batch.size());
            List<float[]> ruleVectors = new ArrayList<>(batch.size());

            for (InferenceEvent event : batch) {
                states.add(event.board);
                ruleVectors.add(event.ruleVector);
            }

            // 调用推理客户端（传入 BoardState，由 GrpcInferenceClient 内部构建 gRPC proto）
            List<float[]> results = inferenceClient.batchInfer(states, ruleVectors);

            // 解析批量结果并分派给各 future
            if (results.size() != batch.size()) {
                logger.warn("batchInfer returned {} results, expected {}",
                        results.size(), batch.size());
            }
            for (int i = 0; i < batch.size(); i++) {
                float[] boardResult = (i < results.size()) ? results.get(i) : null;
                if (boardResult == null) {
                    // 安全 fallback
                    boardResult = new float[]{0.5f};
                }
                batch.get(i).future.complete(boardResult);
            }
        } catch (Exception e) {
            logger.error("Batch inference failed for {} events", batch.size(), e);
            for (InferenceEvent event : batch) {
                event.future.completeExceptionally(e);
            }
        }
    }

    /**
     * 将 {@link BoardState} 转换为神经网络输入的棋盘张量。
     *
     * <p>张量形状为 [channels × rows × cols] 展平为一维 float 数组，其中
     * channels = 14，对应 {@link Piece.Type} 的 14 种棋子类型（ordinal 0-13）。
     * 每个格子 (r,c) 的堆栈中，某类型棋子出现 n 次则对应 channel 的值 = n。</p>
     *
     * @deprecated 自 InferenceClient 接口改为传 {@link BoardState} 后，
     *     {@link GrpcInferenceClient} 直接遍历 {@code state.getEntries()} 构建
     *     {@code BoardStateProto}，此张量转换方法不再被调用。保留供本地 mock 推理使用。
     * @param state 局面快照
     * @return 展平的棋盘张量，长度 = 14 × rows × cols
     */
    /**
     * 将 {@link BoardState} 转换为 14×rows×cols 展平棋盘张量。
     * 包级可见，供 {@link TrainingOrchestrator} 在推送训练样本到 Redis 时调用。
     */
    static float[] boardStateToTensor(BoardState state) {
        int rows = state.getRows();
        int cols = state.getCols();
        int channels = Piece.Type.values().length; // 14
        int totalSize = channels * rows * cols;
        float[] tensor = new float[totalSize];

        for (BoardState.StackEntry entry : state.getEntries()) {
            int r = entry.row;
            int c = entry.col;
            // 边界检查：跳过越界的 entry（防御性编程）
            if (r < 0 || r >= rows || c < 0 || c >= cols) {
                continue;
            }
            for (Piece.Type pieceType : entry.pieceTypes) {
                int ch = pieceType.ordinal(); // 0..13
                if (ch >= 0 && ch < channels) {
                    int idx = ch * rows * cols + r * cols + c;
                    tensor[idx] += 1.0f;
                }
            }
        }

        return tensor;
    }
}
