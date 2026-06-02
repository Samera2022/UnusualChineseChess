package io.github.samera2022.chinese_chess.server.train;

import io.github.samera2022.chinese_chess.ai.MCTSAgent;
import io.github.samera2022.chinese_chess.ai.TrainingDataCollector;
import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.core.engine.Board;
import io.github.samera2022.chinese_chess.core.engine.SimulationBoard;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.core.rules.RulesConfigProvider;
import io.github.samera2022.chinese_chess.server.config.ServerConfig;
import io.github.samera2022.chinese_chess.common.spi.SimulationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 训练流水线编排器。
 *
 * <p>负责课程学习（Curriculum Learning）的完整流程编排：
 * <ol>
 *   <li>按进度划分 {@link CurriculumStage}（基础 → 叠子 → 连通 → 全功能 → 大师）</li>
 *   <li>每轮迭代生成对应阶段的规则配置</li>
 *   <li>启动并行自博弈 Worker</li>
 *   <li>等待数据收集后触发训练：先推 Redis，再通过 gRPC 通知 Python 训练服务</li>
 *   <li>定期评估模型</li>
 * </ol>
 *
 * <p>总计 2000 轮迭代，每 50 轮进行一次评估。</p>
 */
public class TrainingOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(TrainingOrchestrator.class);

    /** 课程学习总迭代次数 */
    private static final int TOTAL_ITERATIONS = 2000;
    /** 评估间隔（迭代次数） */
    private static final int EVAL_INTERVAL = 50;
    /** 触发训练的最小样本数 */
    private static final int MIN_SAMPLES_FOR_TRAINING = 32;

    // ══════════════════════════════════════════════
    // 内部枚举：CurriculumStage
    // ══════════════════════════════════════════════

    /**
     * 课程学习阶段。
     *
     * <p>按训练进度从简单到复杂逐步引入规则：
     * <ul>
     *   <li>{@link #BASIC}        — 基础规则（0-20%）</li>
     *   <li>{@link #STACKING}     — 叠子规则（20-40%）</li>
     *   <li>{@link #CONNECTED}    — 连通规则（40-60%）</li>
     *   <li>{@link #FULL_FEATURES}— 全功能（60-80%）</li>
     *   <li>{@link #MASTER}       — 大师级（80-100%）</li>
     * </ul>
     */
    public enum CurriculumStage {
        /** 基础规则（0-20%）：仅标准象棋规则，无特殊规则 */
        BASIC,
        /** 叠子规则（20-40%）：引入棋子堆叠 */
        STACKING,
        /** 连通规则（40-60%）：引入左右/上下连通 */
        CONNECTED,
        /** 全功能（60-80%）：所有规则组合 */
        FULL_FEATURES,
        /** 大师级（80-100%）：全规则 + 高难度参数 */
        MASTER
    }

    // ══════════════════════════════════════════════
    // 字段
    // ══════════════════════════════════════════════

    private final BatchingEngine batchingEngine;
    private final ServerConfig config;
    private final RedisReplayBuffer replayBuffer;
    private final TrainingServiceClient trainingClient;

    // ══════════════════════════════════════════════
    // 构造器
    // ══════════════════════════════════════════════

    /**
     * 构造训练编排器。
     *
     * @param batchingEngine 批处理引擎，用于启动自博弈 Worker
     * @param config         服务端配置
     * @param replayBuffer   Redis 经验回放缓冲区（可为 null，null 时跳过推送）
     */
    public TrainingOrchestrator(BatchingEngine batchingEngine, ServerConfig config, RedisReplayBuffer replayBuffer) {
        this.batchingEngine = batchingEngine;
        this.config = config;
        this.replayBuffer = replayBuffer;
        this.trainingClient = new TrainingServiceClient(config);
    }

    /**
     * 构造训练编排器（使用自定义 gRPC 训练客户端，便于测试）。
     */
    TrainingOrchestrator(BatchingEngine batchingEngine, ServerConfig config,
                         RedisReplayBuffer replayBuffer, TrainingServiceClient trainingClient) {
        this.batchingEngine = batchingEngine;
        this.config = config;
        this.replayBuffer = replayBuffer;
        this.trainingClient = trainingClient;
    }

    // ══════════════════════════════════════════════
    // 核心方法：课程学习主循环
    // ══════════════════════════════════════════════

    /**
     * 启动课程学习训练流水线。
     *
     * <p>主循环：
     * <ol>
     *   <li>根据进度确定当前 {@link CurriculumStage}</li>
     *   <li>生成对应阶段的 {@link GameRulesConfig}</li>
     *   <li>启动并行自博弈 Worker</li>
     *   <li>等待 Worker 产出数据</li>
     *   <li>将样本推入 Redis（持久化）</li>
     *   <li>通过 gRPC 将样本推送给 Python 训练服务并拉取新权重</li>
     *   <li>每 {@value #EVAL_INTERVAL} 轮评估</li>
     * </ol>
     */
    public void startCurriculumTraining() {
        logger.info("Starting curriculum training: {} iterations, eval every {} iterations",
                TOTAL_ITERATIONS, EVAL_INTERVAL);

        // 连接 gRPC 训练服务（非阻塞）
        trainingClient.connect();

        for (int iter = 0; iter < TOTAL_ITERATIONS; iter++) {
            // 1. 确定当前阶段
            float progress = (float) iter / TOTAL_ITERATIONS;
            CurriculumStage stage = getStage(progress);
            logger.info("Iteration {}/{}, Progress: {}%, Stage: {}",
                    iter + 1, TOTAL_ITERATIONS, String.format("%.1f", progress * 100), stage);

            // 2. 生成规则配置并同步到全局 RulesConfigProvider
            //    SelfPlayWorker 内部 new SimulationBoard() 时通过 RulesConfigProvider.get()
            //    获取规则配置，因此必须在此同步。
            GameRulesConfig rules = generateRulesForStage(stage);
            RulesConfigProvider.replace(rules);

            // 3. 启动并行自博弈 Worker，返回在所有 Worker 完成后完成的 Future
            BatchingEngine.GameBatchResult result = batchingEngine.startParallelGames(
                    config.getSelfPlayWorkers(), rules);

            // 4. 等待本轮所有 Worker 完成对局
            result.future().join();

            // 收集各 Worker 的训练样本
            List<TrainingDataCollector.TrainingSample> allSamples = new ArrayList<>();
            int totalSamples = 0;
            for (SelfPlayWorker w : result.workers()) {
                var workerSamples = w.getCollector().getSamples();
                allSamples.addAll(workerSamples);
                totalSamples += workerSamples.size();
            }
            logger.info("Collected {} training samples from {} workers", totalSamples, config.getSelfPlayWorkers());

            // 5. 触发训练 — 将样本推入 Redis（持久化），并通过 gRPC 通知 Python 训练服务
            if (totalSamples > 0) {
                logger.info("Iteration {} produced {} samples", iter, totalSamples);

                // 5a. 推入 Redis（持久化备份）
                if (replayBuffer != null) {
                    int pushed = 0;
                    for (SelfPlayWorker w : result.workers()) {
                        for (var sample : w.getCollector().getSamples()) {
                            float[] boardTensor = BatchingEngine.boardStateToTensor(sample.board);
                            replayBuffer.pushSample("training:iter:" + iter,
                                boardTensor,
                                sample.policy, sample.value);
                            pushed++;
                        }
                    }
                    logger.info("Pushed {} samples to Redis key training:iter:{}", pushed, iter);
                } else {
                    logger.info("RedisReplayBuffer not configured, samples not persisted to Redis");
                }

                // 5b. 通过 gRPC 通知 Python 训练服务
                if (totalSamples >= MIN_SAMPLES_FOR_TRAINING) {
                    logger.info("Pushing {} samples to Python training service via gRPC...", allSamples.size());
                    boolean pushOk = trainingClient.pushSamples(allSamples);
                    if (pushOk) {
                        logger.info("Successfully pushed {} samples to Python training service", allSamples.size());

                        // 拉取训练后的模型权重
                        byte[] weights = trainingClient.pullWeights();
                        if (weights != null && weights.length > 0) {
                            logger.info("Pulled updated model weights ({} bytes) from Python training service",
                                    weights.length);
                            // 将新权重热更新到推理服务，使后续自我对弈使用更强的模型
                            boolean updateOk = batchingEngine.updateInferenceModel(weights, iter);
                            if (updateOk) {
                                logger.info("Model hot-updated to iteration {} — RL闭环完成", iter);
                            } else {
                                logger.warn("Model hot-update failed, inference continues with old weights");
                            }
                        } else {
                            logger.warn("PullWeights returned empty or null, model weights not updated");
                        }
                    } else {
                        logger.warn("Failed to push samples to Python training service, training skipped for iter {}",
                                iter);
                    }
                } else {
                    logger.info("Not enough samples ({} < {}) to trigger gRPC training",
                            totalSamples, MIN_SAMPLES_FOR_TRAINING);
                }
            }

            // 6. 定期评估 — 本地 MCTS vs MCTS 对战
            if (iter > 0 && iter % EVAL_INTERVAL == 0) {
                logger.info("Evaluation at iteration {} (Stage: {})", iter, stage);
                int evalGames = 10;
                int redWins = 0, blackWins = 0, draws = 0;
                for (int g = 0; g < evalGames; g++) {
                    // 根据当前阶段选择棋盘模式：连通规则打开时用 18×9
                    boolean evalTb = stage == CurriculumStage.CONNECTED
                        || stage == CurriculumStage.FULL_FEATURES
                        || stage == CurriculumStage.MASTER;
                    Board evalBoard = evalTb ? new Board(Board.EXPANDED_ROWS) : new Board();
                    SimulationContext evalCtx = new SimulationBoard(evalBoard);
                    MCTSAgent redAgent = new MCTSAgent();
                    MCTSAgent blackAgent = new MCTSAgent();
                    int maxEvalMoves = 200;
                    for (int m = 0; m < maxEvalMoves; m++) {
                        // 终局判断
                        if (evalCtx.generateLegalMoves().isEmpty()
                                || evalBoard.getRedKing() == null
                                || evalBoard.getBlackKing() == null) {
                            break;
                        }
                        Move move = (evalCtx.isRedTurn() ? redAgent : blackAgent)
                                .findBestMove(evalCtx, 400, 3000);
                        if (move == null) break;
                        evalCtx.simulateMove(move.getFromRow(), move.getFromCol(),
                                move.getToRow(), move.getToCol());
                    }
                    // 统计胜负
                    if (evalBoard.getRedKing() == null) {
                        blackWins++;
                    } else if (evalBoard.getBlackKing() == null) {
                        redWins++;
                    } else {
                        draws++;
                    }
                }
                logger.info("Evaluation result ({} games): red={}, black={}, draws={}",
                        evalGames, redWins, blackWins, draws);
            }
        }

        // 关闭 gRPC 连接
        trainingClient.shutdown();

        logger.info("Curriculum training completed after {} iterations", TOTAL_ITERATIONS);
    }

    // ══════════════════════════════════════════════
    // 私有方法
    // ══════════════════════════════════════════════

    /**
     * 根据训练进度确定当前课程阶段。
     *
     * @param progress 训练进度 [0.0, 1.0)
     * @return 对应的课程阶段
     */
    CurriculumStage getStage(float progress) {
        if (progress < 0.2f) {
            return CurriculumStage.BASIC;
        } else if (progress < 0.4f) {
            return CurriculumStage.STACKING;
        } else if (progress < 0.6f) {
            return CurriculumStage.CONNECTED;
        } else if (progress < 0.8f) {
            return CurriculumStage.FULL_FEATURES;
        } else {
            return CurriculumStage.MASTER;
        }
    }

    /**
     * 根据课程阶段生成对应的游戏规则配置。
     *
     * <p>当前简化实现：返回默认规则配置。
     * 后续应根据阶段启用/禁用不同规则组合。</p>
     *
     * @param stage 当前课程阶段
     * @return 对应阶段的规则配置
     */
    GameRulesConfig generateRulesForStage(CurriculumStage stage) {
        GameRulesConfig rules = new GameRulesConfig();

        // TODO: 各阶段应启用/禁用不同规则组合
        // 例如：
        //   BASIC:        所有特殊规则 = false（标准象棋）
        //   STACKING:     allow_piece_stacking = true
        //   CONNECTED:    top_bottom_connected = true, left_right_connected = true
        //   FULL_FEATURES:全部规则 = true
        //   MASTER:       全规则 + 极端 max_stacking_count
        switch (stage) {
            case BASIC:
                // 仅标准象棋规则，所有特殊规则关闭
                rules.set("allow_piece_stacking", false, GameRulesConfig.ChangeSource.API);
                rules.set("top_bottom_connected", false, GameRulesConfig.ChangeSource.API);
                rules.set("left_right_connected", false, GameRulesConfig.ChangeSource.API);
                rules.set("allow_capture_conversion", false, GameRulesConfig.ChangeSource.API);
                rules.set("allow_carry_pieces_above", false, GameRulesConfig.ChangeSource.API);
                break;
            case STACKING:
                // 引入叠子规则
                rules.set("allow_piece_stacking", true, GameRulesConfig.ChangeSource.API);
                rules.set("top_bottom_connected", false, GameRulesConfig.ChangeSource.API);
                rules.set("left_right_connected", false, GameRulesConfig.ChangeSource.API);
                break;
            case CONNECTED:
                // 引入连通规则
                rules.set("allow_piece_stacking", true, GameRulesConfig.ChangeSource.API);
                rules.set("top_bottom_connected", true, GameRulesConfig.ChangeSource.API);
                rules.set("left_right_connected", true, GameRulesConfig.ChangeSource.API);
                break;
            case FULL_FEATURES:
                // 全功能开启
                rules.set("allow_piece_stacking", true, GameRulesConfig.ChangeSource.API);
                rules.set("top_bottom_connected", true, GameRulesConfig.ChangeSource.API);
                rules.set("left_right_connected", true, GameRulesConfig.ChangeSource.API);
                rules.set("allow_capture_conversion", true, GameRulesConfig.ChangeSource.API);
                rules.set("allow_carry_pieces_above", true, GameRulesConfig.ChangeSource.API);
                break;
            case MASTER:
                // 全功能 + 极端参数
                rules.set("allow_piece_stacking", true, GameRulesConfig.ChangeSource.API);
                rules.set("top_bottom_connected", true, GameRulesConfig.ChangeSource.API);
                rules.set("left_right_connected", true, GameRulesConfig.ChangeSource.API);
                rules.set("allow_capture_conversion", true, GameRulesConfig.ChangeSource.API);
                rules.set("allow_carry_pieces_above", true, GameRulesConfig.ChangeSource.API);
                rules.set("max_stacking_count", 16, GameRulesConfig.ChangeSource.API);
                break;
        }

        logger.debug("Generated rules for stage {}: stacking={}, connected={}",
                stage,
                rules.getBoolean("allow_piece_stacking"),
                rules.getBoolean("top_bottom_connected"));
        return rules;
    }
}
