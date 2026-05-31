package io.github.samera2022.chinese_chess.ai;

import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.common.spi.SimulationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 改进版 MCTS（蒙特卡洛树搜索）Agent。
 * <p>
 * 使用 UCB1 公式进行树节点的选择，通过截断启发式评估和加权随机走子
 * 进行模拟（Rollout），最终选择平均胜率最高的着法作为最佳走子。
 * </p>
 *
 * <h3>改进要点</h3>
 * <ul>
 *   <li><b>截断评估</b>：Rollout 直接从当前位置使用 {@link SimulationContext#evaluate()}
 *       做启发式评估，不再纯随机走 200 步。</li>
 *   <li><b>走子排序</b>：扩展时优先尝试吃子（按被吃子价值排序）、前进走法，
 *       让树优先探索有前途的分支。</li>
 *   <li><b>加权随机 Rollout</b>：短模拟中按启发式规则偏好走子，而非纯随机。</li>
 *   <li><b>根选择按胜率</b>：最终从根节点选择平均胜率最高的子节点。</li>
 * </ul>
 */
public class MCTSAgent {

    /** 评估截断的 rollout 深度：4 表示模拟走子 4 步后再做启发式评估 */
    private static final int ROLLOUT_DEPTH = 4;

    /** C_puct / UCB 的探索常数。越大越偏向探索，越小越贪婪。 */
    private static final double EXPLORATION_CONSTANT = 1.414;

    /** 模拟阶段的最大步数（仅在 ROLLOUT_DEPTH > 0 时生效） */
    private static final int MAX_ROLLOUT_STEPS = 20;

    /** 随机数生成器 */
    private final Random random = new Random();

    // ══════════════════════════════════════════════
    // 内部类：MCTSNode
    // ══════════════════════════════════════════════

    /** MCTS 搜索树中的一个节点。 */
    static class MCTSNode {
        /** 到达此节点的着法（根节点为 null） */
        Move move;
        /** 父节点 */
        MCTSNode parent;
        /** 子节点列表 */
        List<MCTSNode> children;
        /** 访问次数 */
        int visitCount;
        /** 累计价值（来自该节点视角的胜负结果累计） */
        double totalValue;
        /** 是否已完全展开 */
        boolean expanded;

        MCTSNode(Move move, MCTSNode parent) {
            this.move = move;
            this.parent = parent;
            this.children = new ArrayList<>();
            this.visitCount = 0;
            this.totalValue = 0.0;
            this.expanded = false;
        }

        /** 平均价值（胜率） */
        double averageValue() {
            return visitCount > 0 ? totalValue / visitCount : 0.0;
        }
    }

    // ══════════════════════════════════════════════
    // 公共方法
    // ══════════════════════════════════════════════

    /**
     * 使用 MCTS 搜索查找最佳着法。
     *
     * @param ctx             当前局面上下文（不会被修改）
     * @param numSimulations  最大模拟次数
     * @param timeLimitMs     时间限制（毫秒），≤ 0 表示无时间限制
     * @return 最佳着法；如果当前无合法走法则返回 null
     */
    public Move findBestMove(SimulationContext ctx, int numSimulations, long timeLimitMs) {
        List<Move> rootLegalMoves = ctx.generateLegalMoves();
        if (rootLegalMoves.isEmpty()) {
            return null;
        }

        SimulationContext forkCtx = ctx.fork();
        MCTSNode root = new MCTSNode(null, null);
        long startTime = System.currentTimeMillis();

        for (int sim = 0; sim < numSimulations; sim++) {
            if (timeLimitMs > 0 && System.currentTimeMillis() - startTime >= timeLimitMs) {
                break;
            }

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
                    forkCtx.simulateMove(
                            childMove.getFromRow(), childMove.getFromCol(),
                            childMove.getToRow(), childMove.getToCol());
                    totalMoves++;
                    node = bestChild;
                } else {
                    break;
                }
            }

            // ── 2. 扩展 (Expansion) ──
            List<Move> parentMoves = forkCtx.generateLegalMoves();
            if (!parentMoves.isEmpty() && !node.expanded) {
                // 按启发式排序走法，优先扩展有前途的走法
                List<Move> sorted = sortMovesByHeuristic(parentMoves, forkCtx);
                Move unexpanded = pickUnexpandedMove(node, sorted);
                if (unexpanded != null) {
                    forkCtx.simulateMove(
                            unexpanded.getFromRow(), unexpanded.getFromCol(),
                            unexpanded.getToRow(), unexpanded.getToCol());
                    totalMoves++;

                    MCTSNode child = new MCTSNode(unexpanded, node);
                    node.children.add(child);
                    node.expanded = (node.children.size() >= parentMoves.size());
                    node = child;
                }
            }

            // ── 3. 模拟 (Evaluation) ──
            double value;
            if (ROLLOUT_DEPTH == 0) {
                // 截断评估：使用启发式 Rollout 模拟 4 步后评估局面
                value = heuristicRollout(forkCtx, ROLLOUT_DEPTH);
            } else {
                // 短模拟：启发式加权随机走子
                value = heuristicRollout(forkCtx, ROLLOUT_DEPTH);
            }
            totalMoves += ROLLOUT_DEPTH;

            // ── 4. 反向传播 (Backpropagation) ──
            MCTSNode bpNode = node;
            while (bpNode != null) {
                bpNode.visitCount++;
                bpNode.totalValue += value;
                value = -value;
                bpNode = bpNode.parent;
            }

            // ── 5. 撤消 ──
            for (int i = 0; i < totalMoves; i++) {
                forkCtx.simulateUndo();
            }
        }

        // ── 根选择：优先平均胜率，其次访问次数 ──
        MCTSNode bestChild = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (MCTSNode child : root.children) {
            // 综合评分 = 0.5 * 平均胜率 + 0.5 * log10(访问次数+1) / maxLogVisits
            // 防止访问次数极少但胜率偶然高的噪声节点
            double winRate = child.averageValue();
            double visitBonus = Math.log10(child.visitCount + 1);

            // 获取最大 log 访问量做归一化
            double maxLogVisits = 0;
            for (MCTSNode c2 : root.children) {
                maxLogVisits = Math.max(maxLogVisits, Math.log10(c2.visitCount + 1));
            }
            double normalizedBonus = maxLogVisits > 0 ? visitBonus / maxLogVisits : 0;

            // score = winRate + 0.1 * normalized exploration bonus
            // 主要还是看 winRate
            double score = winRate + 0.1 * normalizedBonus;

            if (score > bestScore) {
                bestScore = score;
                bestChild = child;
            }
        }
        return bestChild != null ? bestChild.move : null;
    }

    // ══════════════════════════════════════════════
    // 启发式评估与 Rollout
    // ══════════════════════════════════════════════

    /**
     * 将子力评估值归一化到 [-1, 1] 范围。
     * <p>
     * 使用 sigmoid 风格映射，基础子力总分约 15000（含双将），
     * 5000 分差异即明显优势（约 0.5）。
     * </p>
     */
    private static double normalizeEval(int eval) {
        // 使用 tanh 将 eval 映射到 [-1, 1]
        // scale 参数控制"敏感度"：scale 越小，需要更大分差才能产生显著信号
        if (eval == 0) return 0.0;
        double scale = 2000.0;
        return Math.tanh(eval / scale);
    }

    /**
     * 启发式 Rollout：执行指定步数的加权随机走子，然后用 evaluate() 评分。
     */
    private double heuristicRollout(SimulationContext ctx, int maxSteps) {
        int steps = 0;
        while (steps < maxSteps) {
            List<Move> moves = ctx.generateLegalMoves();
            if (moves.isEmpty()) {
                return ctx.isRedTurn() ? -1.0 : 1.0;
            }
            // 启发式加权选择走法
            Move selected = weightedRandomMove(moves, ctx);
            ctx.simulateMove(
                    selected.getFromRow(), selected.getFromCol(),
                    selected.getToRow(), selected.getToCol());
            steps++;
        }
        // 截断评估
        int eval = ctx.evaluate();
        return normalizeEval(eval);
    }

    /**
     * 启发式加权随机选择走法。
     * <p>
     * 权重规则：
     * <ul>
     *   <li>吃子：被吃子价值越高，权重越大（×100~900）</li>
     *   <li>前进（红方向上行、黑方向下行）：权重 ×2</li>
     *   <li>平移/后退：基础权重 1</li>
     * </ul>
     * </p>
     */
    private Move weightedRandomMove(List<Move> moves, SimulationContext ctx) {
        double[] weights = new double[moves.size()];
        double totalWeight = 0;

        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            double w = 1.0;

            // 吃子奖励
            Piece captured = ctx.getBoard().getPiece(m.getToRow(), m.getToCol());
            if (captured != null) {
                w += getPieceWeight(captured) * 0.1; // 被吃子价值的 10%
            }

            // 前进奖励
            Piece movingPiece = ctx.getBoard().getPiece(m.getFromRow(), m.getFromCol());
            if (movingPiece != null) {
                if (movingPiece.isRed()) {
                    // 红方向上行（row 减小）
                    if (m.getToRow() < m.getFromRow()) {
                        w *= 2.0;
                    }
                } else {
                    // 黑方向下行（row 增大）
                    if (m.getToRow() > m.getFromRow()) {
                        w *= 2.0;
                    }
                }
            }

            weights[i] = Math.max(w, 0.01); // 避免零权重
            totalWeight += weights[i];
        }

        // 轮盘赌选择
        double r = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < moves.size(); i++) {
            cumulative += weights[i];
            if (r <= cumulative) {
                return moves.get(i);
            }
        }
        return moves.get(moves.size() - 1);
    }

    // ══════════════════════════════════════════════
    // 走子排序
    // ══════════════════════════════════════════════

    /**
     * 按启发式规则对走法排序，优先级从高到低：
     * <ol>
     *   <li>吃高价值棋子（车 > 炮 > 马 > ...）</li>
     *   <li>吃低价值棋子</li>
     *   <li>前进</li>
     *   <li>其他</li>
     * </ol>
     */
    private List<Move> sortMovesByHeuristic(List<Move> moves, SimulationContext ctx) {
        ReadonlyBoard board = ctx.getBoard();
        List<Move> sorted = new ArrayList<>(moves);
        sorted.sort(Comparator.comparingInt((Move m) -> {
            // 计算启发式评分（越高越优先）
            int score = 0;

            // 吃子：极大加分
            Piece captured = board.getPiece(m.getToRow(), m.getToCol());
            if (captured != null) {
                score += getPieceWeight(captured) * 10;
            }

            // 前进：少量加分
            Piece movingPiece = board.getPiece(m.getFromRow(), m.getFromCol());
            if (movingPiece != null) {
                if (movingPiece.isRed() && m.getToRow() < m.getFromRow()) {
                    score += 5;
                } else if (!movingPiece.isRed() && m.getToRow() > m.getFromRow()) {
                    score += 5;
                }
            }

            return -score; // 降序排列（高分在前）
        }));
        return sorted;
    }

    // ══════════════════════════════════════════════
    // 工具方法
    // ══════════════════════════════════════════════

    /** UCB1 子节点选择。 */
    private MCTSNode selectBestChild(MCTSNode parent) {
        MCTSNode best = null;
        double bestUCB = Double.NEGATIVE_INFINITY;

        for (MCTSNode child : parent.children) {
            double ucb;
            if (child.visitCount == 0) {
                ucb = Double.MAX_VALUE;
            } else {
                double exploitation = child.totalValue / child.visitCount;
                double exploration = EXPLORATION_CONSTANT
                        * Math.sqrt(Math.log(parent.visitCount) / child.visitCount);
                ucb = exploitation + exploration;
            }

            if (ucb > bestUCB) {
                bestUCB = ucb;
                best = child;
            }
        }
        return best;
    }

    /**
     * 从未展开的合法走法中选择一个（按排序后的列表顺序）。
     */
    private Move pickUnexpandedMove(MCTSNode node, List<Move> sortedMoves) {
        for (Move m : sortedMoves) {
            boolean alreadyExpanded = false;
            for (MCTSNode child : node.children) {
                if (movesEqual(child.move, m)) {
                    alreadyExpanded = true;
                    break;
                }
            }
            if (!alreadyExpanded) {
                return m;
            }
        }
        return null;
    }

    /** 棋子权重（供启发式排序和加权选择使用）。 */
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

    /** 着法等价比较。 */
    private boolean movesEqual(Move a, Move b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getFromRow() == b.getFromRow()
                && a.getFromCol() == b.getFromCol()
                && a.getToRow() == b.getToRow()
                && a.getToCol() == b.getToCol()
                && a.getSelectedStackIndex() == b.getSelectedStackIndex();
    }
}
