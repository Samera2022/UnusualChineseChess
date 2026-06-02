package io.github.samera2022.chinese_chess.server.train;

import io.github.samera2022.chinese_chess.ai.MCTSAgent;
import io.github.samera2022.chinese_chess.ai.TrainingDataCollector;
import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.common.spi.SimulationContext;
import io.github.samera2022.chinese_chess.core.engine.Board;
import io.github.samera2022.chinese_chess.core.engine.SimulationBoard;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.core.rules.RuleEncoder;
import io.github.samera2022.chinese_chess.server.config.ServerConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelfPlayWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SelfPlayWorker.class);

    private static final int MAX_MOVES = 400;

    private final int workerId;
    private final GameRulesConfig rules;
    private final BatchingEngine batchingEngine;
    private final TrainingDataCollector collector;
    private final MCTSAgent mctsAgent;
    private final int mctsSimulations;
    private final long mctsTimeLimitMs;

    public SelfPlayWorker(int workerId, GameRulesConfig rules, BatchingEngine batchingEngine) {
        this(workerId, rules, batchingEngine, ServerConfig.getInstance());
    }

    public SelfPlayWorker(int workerId, GameRulesConfig rules, BatchingEngine batchingEngine, ServerConfig config) {
        this.workerId = workerId;
        this.rules = rules;
        this.batchingEngine = batchingEngine;
        this.collector = new TrainingDataCollector();
        this.mctsAgent = new MCTSAgent();
        this.mctsSimulations = config.getMctsSimulations();
        this.mctsTimeLimitMs = config.getMctsTimeLimitMs();
    }

    @Override
    public void run() {
        logger.debug("SelfPlayWorker-{} started", workerId);

        try {
            boolean topBottomConnected = rules.getBoolean("top_bottom_connected");
            int rows = topBottomConnected ? Board.EXPANDED_ROWS : Board.STANDARD_ROWS;
            Board board = new Board(rows);
            SimulationContext ctx = new SimulationBoard(board);

            float[] ruleBooleanVec = RuleEncoder.encode(rules);
            float[] ruleContinuousVec = RuleEncoder.encodeContinuous(rules);
            float[] ruleVector = mergeRuleVectors(ruleBooleanVec, ruleContinuousVec);

            // 配置神经网络推理回调：MCTS 叶节点评估时从此 lambda 提交推理请求到 BatchingEngine
            // 集成 TranspositionTable 缓存，避免重复推理同一局面
            TranspositionTable tt = batchingEngine.getTranspositionTable();
            mctsAgent.setInferenceFunction((simCtx, ruleVecIgnored) -> {
                BoardState state = ((Board) simCtx.getBoard()).toState();
                long hash = state.toHash();

                // 1) 查置换表：若命中则直接返回缓存的 [value, policy_0, ..., policy_n]
                Optional<TranspositionTable.TtEntry> cached = tt.get(hash);
                if (cached.isPresent()) {
                    TranspositionTable.TtEntry entry = cached.get();
                    float[] policy = entry.policy();
                    float[] result = new float[1 + policy.length];
                    result[0] = entry.value();
                    System.arraycopy(policy, 0, result, 1, policy.length);
                    return CompletableFuture.completedFuture(result);
                }

                // 2) 未命中 → 提交推理，完成后将结果写入置换表
                return batchingEngine.submitInference(state, ruleVector)
                        .thenApply(inferResult -> {
                            // inferResult[0] = value, inferResult[1..] = policy
                            float value = inferResult[0];
                            float[] policy = Arrays.copyOfRange(inferResult, 1, inferResult.length);
                            tt.put(hash, policy, value);
                            return inferResult;
                        });
            });

            int moveCount = 0;
            List<StepSample> tempSamples = new ArrayList<>();

            while (moveCount < MAX_MOVES) {
                if (ctx.generateLegalMoves().isEmpty()) {
                    break;
                }

                Board currentBoard = (Board) ctx.getBoard();
                if (currentBoard.getRedKing() == null || currentBoard.getBlackKing() == null) {
                    break;
                }

                Move bestMove = mctsAgent.findBestMove(ctx, mctsSimulations, mctsTimeLimitMs);
                if (bestMove == null) {
                    break;
                }

                float[] policy = mctsAgent.getLastPolicy();
                if (policy == null) {
                    policy = new float[0];
                }

                BoardState state = currentBoard.toState();
                tempSamples.add(new StepSample(state, ruleVector, policy));

                int fr = bestMove.getFromRow();
                int fc = bestMove.getFromCol();
                int tr = bestMove.getToRow();
                int tc = bestMove.getToCol();

                // 诊断：检查 MCTS 返回的着法在当前棋盘上是否合法
                ReadonlyBoard rb = ctx.getBoard();
                Piece srcPiece = rb.getPiece(fr, fc);
                if (srcPiece == null) {
                    logger.error("SelfPlayWorker-{}: MCTS move {}: no piece at ({},{}). Board:\n{}",
                            workerId, bestMove, fr, fc, currentBoard);
                    List<Move> legal = ctx.generateLegalMoves();
                    logger.error("Legal moves ({}):", legal.size());
                    for (int i = 0; i < Math.min(legal.size(), 20); i++) {
                        Move m = legal.get(i);
                        logger.error("  [{}] ({},{})->({},{})",
                                i, m.getFromRow(), m.getFromCol(), m.getToRow(), m.getToCol());
                    }
                    break;
                }

                if (!ctx.isValidMove(fr, fc, tr, tc)) {
                    logger.warn("SelfPlayWorker-{}: MCTS move {} invalid via isValidMove "
                            + "(but piece {} at ({},{}))",
                            workerId, bestMove, srcPiece.getType(), fr, fc);
                }

                boolean success = ctx.simulateMove(fr, fc, tr, tc);
                if (!success) {
                    logger.warn("SelfPlayWorker-{}: simulateMove failed for move {}", workerId, bestMove);
                    break;
                }
                moveCount++;
            }

            // 对局结束后，根据终局结果回填所有样本的 value target
            float finalValue;
            Board finalBoard = (Board) ctx.getBoard();
            if (finalBoard.getRedKing() == null) {
                // 红方将/帅被吃 → 黑胜
                finalValue = -1.0f;
            } else if (finalBoard.getBlackKing() == null) {
                // 黑方将/帅被吃 → 红胜
                finalValue = 1.0f;
            } else if (moveCount >= MAX_MOVES) {
                // 达到最大步数上限 → 平局
                finalValue = 0.0f;
            } else if (ctx.generateLegalMoves().isEmpty()) {
                // 当前回合方无子可走 → 该方负
                finalValue = ctx.isRedTurn() ? -1.0f : 1.0f;
            } else {
                // 其他异常终止（MCTS 返回 null、非法着法等）→ 平局
                finalValue = 0.0f;
            }

            for (StepSample sample : tempSamples) {
                collector.addSample(sample.state, sample.ruleVector, sample.policy, finalValue);
            }

            logger.info("SelfPlayWorker-{} finished game: {} moves, {} samples collected, result={}",
                    workerId, moveCount, collector.size(), finalValue);

        } catch (Exception e) {
            logger.error("SelfPlayWorker-{} error in game", workerId, e);
        } finally {
            logger.info("SelfPlayWorker-{} exiting, total samples in collector: {}",
                    workerId, collector.size());
        }
    }

    public TrainingDataCollector getCollector() {
        return collector;
    }

    /**
     * 临时存储对局过程中每步的样本数据（不含 value），
     * 待对局结束后根据终局结果统一回填 value target。
     */
    private static class StepSample {
        final BoardState state;
        final float[] ruleVector;
        final float[] policy;

        StepSample(BoardState state, float[] ruleVector, float[] policy) {
            this.state = state;
            this.ruleVector = ruleVector;
            this.policy = policy;
        }
    }

    private static float[] mergeRuleVectors(float[] booleanVec, float[] continuousVec) {
        int boolLen = booleanVec.length;
        float[] merged = new float[boolLen + 1];
        System.arraycopy(booleanVec, 0, merged, 0, boolLen);
        merged[boolLen] = continuousVec[0];
        return merged;
    }
}
