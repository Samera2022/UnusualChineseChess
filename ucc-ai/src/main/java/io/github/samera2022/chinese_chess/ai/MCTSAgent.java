package io.github.samera2022.chinese_chess.ai;

import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.common.spi.SimulationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * 支持神经网络评估的 MCTS Agent。
 *
 * <p>当提供了神经网络推理回调（{@link #setInferenceFunction}）时，叶节点评估使用神经网络
 * 预测的 value 替代启发式 Rollout；否则回退到纯启发式评估。</p>
 */
public class MCTSAgent {

    private static final int ROLLOUT_DEPTH = 4;
    private static final double EXPLORATION_CONSTANT = 1.414;
    private static final int MAX_ROLLOUT_STEPS = 20;

    private final Random random = new Random();
    private volatile float[] lastPolicy;

    /** 神经网络推理回调：输入 (SimulationContext, float[]规则向量) → 输出 float[policy+value] */
    private BiFunction<SimulationContext, float[], CompletableFuture<float[]>> inferenceFunction;

    public void setInferenceFunction(BiFunction<SimulationContext, float[], CompletableFuture<float[]>> fn) {
        this.inferenceFunction = fn;
    }

    static class MCTSNode {
        Move move;
        MCTSNode parent;
        List<MCTSNode> children;
        int visitCount;
        double totalValue;
        boolean expanded;
        /** 缓存神经网络的 policy 先验概率（仅在使用 NN 评估时有效） */
        double priorPolicy;

        MCTSNode(Move move, MCTSNode parent) {
            this.move = move;
            this.parent = parent;
            this.children = new ArrayList<>();
            this.visitCount = 0;
            this.totalValue = 0.0;
            this.expanded = false;
            this.priorPolicy = 0.0;
        }

        /** 带先验概率的构造函数，用于 PUCT 搜索 */
        MCTSNode(Move move, MCTSNode parent, double prior) {
            this(move, parent);
            this.priorPolicy = prior;
        }

        double averageValue() {
            return visitCount > 0 ? totalValue / visitCount : 0.0;
        }
    }

    public Move findBestMove(SimulationContext ctx, int numSimulations, long timeLimitMs) {
        List<Move> rootLegalMoves = ctx.generateLegalMoves();
        if (rootLegalMoves.isEmpty()) {
            return null;
        }

        MCTSNode root = new MCTSNode(null, null);
        long startTime = System.currentTimeMillis();

        for (int sim = 0; sim < numSimulations; sim++) {
            if (timeLimitMs > 0 && System.currentTimeMillis() - startTime >= timeLimitMs) {
                break;
            }

            // 每次模拟创建新的 fork，避免 simulateUndo 累积错误导致棋盘状态不一致
            SimulationContext forkCtx = ctx.fork();
            int totalMoves = 0;
            MCTSNode node = root;

            // ── 1. 选择 (Selection) ──
            while (true) {
                List<Move> moves = forkCtx.generateLegalMoves();
                if (moves.isEmpty()) {
                    break;
                }
                if (node.expanded) {
                    MCTSNode bestChild = selectBestChild(node);
                    if (bestChild == null) {
                        break;
                    }
                    Move childMove = bestChild.move;
                    // 子节点的走法是之前某次模拟中生成的，在新的 forkCtx 上可能不合法
                    // 跳过不合法走法
                    if (!forkCtx.isValidMove(
                            childMove.getFromRow(), childMove.getFromCol(),
                            childMove.getToRow(), childMove.getToCol())) {
                        bestChild.visitCount = Integer.MAX_VALUE;
                        continue;
                    }
                    forkCtx.simulateMove(
                            childMove.getFromRow(), childMove.getFromCol(),
                            childMove.getToRow(), childMove.getToCol());
                    totalMoves++;
                    node = bestChild;
                } else {
                    break;
                }
            }

            // ── 3. 评估 (Evaluation) ──
            // 先评估叶节点，获取神经网络输出的 value 和 policy
            float[] nnResult = null;
            double value;
            if (inferenceFunction != null) {
                // 使用神经网络评估（异步，同步等待结果）
                // 直接传递 forkCtx，由调用方 lambda 从 SimulationContext 提取 BoardState 并提交推理
                try {
                    nnResult = inferenceFunction.apply(forkCtx, null).get();
                    // nnResult[0] = value, nnResult[1...] = policy
                    value = nnResult.length > 0 ? nnResult[0] : 0.0;
                } catch (Exception e) {
                    value = heuristicRollout(forkCtx, ROLLOUT_DEPTH);
                }
            } else {
                // 无神经网络 → 回退到纯启发式评估
                value = heuristicRollout(forkCtx, ROLLOUT_DEPTH);
            }

            // ── 2. 扩展 (Expansion) ──
            List<Move> parentMoves = forkCtx.generateLegalMoves();
            if (!parentMoves.isEmpty() && !node.expanded) {
                List<Move> sorted = sortMovesByHeuristic(parentMoves, forkCtx);
                Move unexpanded = pickUnexpandedMove(node, sorted);
                if (unexpanded != null) {
                    forkCtx.simulateMove(
                            unexpanded.getFromRow(), unexpanded.getFromCol(),
                            unexpanded.getToRow(), unexpanded.getToCol());
                    totalMoves++;

                    // 从 nnResult 中获取当前着法的 policy 先验概率
                    double prior = 0.0;
                    if (nnResult != null) {
                        int moveIdx = indexOfMove(parentMoves, unexpanded);
                        if (moveIdx >= 0 && 1 + moveIdx < nnResult.length) {
                            prior = nnResult[1 + moveIdx];
                        }
                    }
                    MCTSNode child = new MCTSNode(unexpanded, node, prior);
                    node.children.add(child);
                    node.expanded = (node.children.size() >= parentMoves.size());
                    node = child;
                }
            }

            // ── 4. 反向传播 (Backpropagation) ──
            MCTSNode bpNode = node;
            while (bpNode != null) {
                bpNode.visitCount++;
                bpNode.totalValue += value;
                value = -value;
                bpNode = bpNode.parent;
            }

            for (int i = 0; i < totalMoves; i++) {
                forkCtx.simulateUndo();
            }
        }

        // ── 根选择 ──
        MCTSNode bestChild = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (MCTSNode child : root.children) {
            double winRate = child.averageValue();
            double visitBonus = Math.log10(child.visitCount + 1);
            double maxLogVisits = 0;
            for (MCTSNode c2 : root.children) {
                maxLogVisits = Math.max(maxLogVisits, Math.log10(c2.visitCount + 1));
            }
            double normalizedBonus = maxLogVisits > 0 ? visitBonus / maxLogVisits : 0;
            double score = winRate + 0.1 * normalizedBonus;
            if (score > bestScore) {
                bestScore = score;
                bestChild = child;
            }
        }

        lastPolicy = buildPolicyFromRoot(root);

        // 根选择后验证：确保返回的走法在原始 ctx 上合法
        if (bestChild != null) {
            int fr = bestChild.move.getFromRow();
            int fc = bestChild.move.getFromCol();
            int tr = bestChild.move.getToRow();
            int tc = bestChild.move.getToCol();
            if (!ctx.isValidMove(fr, fc, tr, tc)) {
                // MCTS 树中存储的走法在新棋盘上不合法，从当前合法走法中选最佳
                List<Move> fallback = ctx.generateLegalMoves();
                if (!fallback.isEmpty()) {
                    return fallback.get(0);
                }
                return null;
            }
            return bestChild.move;
        }
        return null;
    }

    public float[] getLastPolicy() {
        return lastPolicy;
    }

    private static float[] buildPolicyFromRoot(MCTSNode root) {
        if (root.children.isEmpty()) {
            return new float[0];
        }
        float[] policy = new float[root.children.size()];
        double totalVisits = 0.0;
        for (int i = 0; i < root.children.size(); i++) {
            policy[i] = (float) root.children.get(i).visitCount;
            totalVisits += policy[i];
        }
        if (totalVisits > 0) {
            for (int i = 0; i < policy.length; i++) {
                policy[i] /= (float) totalVisits;
            }
        }
        return policy;
    }

    private static double normalizeEval(int eval) {
        if (eval == 0) return 0.0;
        double scale = 2000.0;
        return Math.tanh(eval / scale);
    }

    private double heuristicRollout(SimulationContext ctx, int maxSteps) {
        int steps = 0;
        while (steps < maxSteps) {
            List<Move> moves = ctx.generateLegalMoves();
            if (moves.isEmpty()) {
                return ctx.isRedTurn() ? -1.0 : 1.0;
            }
            Move selected = weightedRandomMove(moves, ctx);
            ctx.simulateMove(
                    selected.getFromRow(), selected.getFromCol(),
                    selected.getToRow(), selected.getToCol());
            steps++;
        }
        int eval = ctx.evaluate();
        return normalizeEval(eval);
    }

    private Move weightedRandomMove(List<Move> moves, SimulationContext ctx) {
        double[] weights = new double[moves.size()];
        double totalWeight = 0;
        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            double w = 1.0;
            Piece captured = ctx.getBoard().getPiece(m.getToRow(), m.getToCol());
            if (captured != null) {
                w += getPieceWeight(captured) * 0.1;
            }
            Piece movingPiece = ctx.getBoard().getPiece(m.getFromRow(), m.getFromCol());
            if (movingPiece != null) {
                if (movingPiece.isRed()) {
                    if (m.getToRow() < m.getFromRow()) w *= 2.0;
                } else {
                    if (m.getToRow() > m.getFromRow()) w *= 2.0;
                }
            }
            weights[i] = Math.max(w, 0.01);
            totalWeight += weights[i];
        }
        double r = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < moves.size(); i++) {
            cumulative += weights[i];
            if (r <= cumulative) return moves.get(i);
        }
        return moves.get(moves.size() - 1);
    }

    private List<Move> sortMovesByHeuristic(List<Move> moves, SimulationContext ctx) {
        ReadonlyBoard board = ctx.getBoard();
        List<Move> sorted = new ArrayList<>(moves);
        sorted.sort(Comparator.comparingInt((Move m) -> {
            int score = 0;
            Piece captured = board.getPiece(m.getToRow(), m.getToCol());
            if (captured != null) score += getPieceWeight(captured) * 10;
            Piece movingPiece = board.getPiece(m.getFromRow(), m.getFromCol());
            if (movingPiece != null) {
                if (movingPiece.isRed() && m.getToRow() < m.getFromRow()) score += 5;
                else if (!movingPiece.isRed() && m.getToRow() > m.getFromRow()) score += 5;
            }
            return -score;
        }));
        return sorted;
    }

    private MCTSNode selectBestChild(MCTSNode parent) {
        MCTSNode best = null;
        double bestUCB = Double.NEGATIVE_INFINITY;
        for (MCTSNode child : parent.children) {
            double ucb;
            if (child.visitCount == 0) {
                ucb = Double.MAX_VALUE;
            } else {
                double exploitation = child.totalValue / child.visitCount;
                // PUCT 公式：c_puct * prior * sqrt(parentVisit) / (1 + childVisit)
                double exploration = EXPLORATION_CONSTANT * child.priorPolicy
                        * Math.sqrt(parent.visitCount) / (1.0 + child.visitCount);
                ucb = exploitation + exploration;
            }
            if (ucb > bestUCB) {
                bestUCB = ucb;
                best = child;
            }
        }
        return best;
    }

    private Move pickUnexpandedMove(MCTSNode node, List<Move> sortedMoves) {
        for (Move m : sortedMoves) {
            boolean alreadyExpanded = false;
            for (MCTSNode child : node.children) {
                if (movesEqual(child.move, m)) {
                    alreadyExpanded = true;
                    break;
                }
            }
            if (!alreadyExpanded) return m;
        }
        return null;
    }

    private static int getPieceWeight(Piece piece) {
        if (piece == null) return 0;
        switch (piece.getType()) {
            case RED_KING:    case BLACK_KING:    return 10000;
            case RED_CHARIOT: case BLACK_CHARIOT: return 900;
            case RED_CANNON:  case BLACK_CANNON:  return 450;
            case RED_HORSE:   case BLACK_HORSE:   return 400;
            case RED_ELEPHANT:case BLACK_ELEPHANT:return 200;
            case RED_ADVISOR: case BLACK_ADVISOR: return 200;
            case RED_SOLDIER: case BLACK_SOLDIER: return 100;
            default: return 0;
        }
    }

    private static boolean movesEqual(Move a, Move b) {
        if (a == null || b == null) return false;
        return a.getFromRow() == b.getFromRow()
                && a.getFromCol() == b.getFromCol()
                && a.getToRow() == b.getToRow()
                && a.getToCol() == b.getToCol()
                && a.getSelectedStackIndex() == b.getSelectedStackIndex();
    }

    /**
     * 在 moves 列表中查找与 target 相等的着法索引。
     * @return 匹配的索引，未找到返回 -1
     */
    private static int indexOfMove(List<Move> moves, Move target) {
        for (int i = 0; i < moves.size(); i++) {
            if (movesEqual(moves.get(i), target)) {
                return i;
            }
        }
        return -1;
    }
}
