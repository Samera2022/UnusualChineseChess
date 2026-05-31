package io.github.samera2022.chinese_chess.ai;

import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.spi.SimulationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MCTS（蒙特卡洛树搜索）Agent。
 * <p>
 * 使用 UCB1 公式进行树节点的选择，通过随机走子进行模拟（Rollout），
 * 最终选择访问次数最多的着法作为最佳走子。适用于所有玩法规则。
 * </p>
 *
 * <h3>算法流程</h3>
 * <ol>
 *   <li><b>选择 (Select)</b>：从根节点沿树下降，使用 UCB1 选择子节点，
 *       直至遇到未完全展开或终止节点。</li>
 *   <li><b>扩展 (Expand)</b>：从未展开的合法走法中选择一个，执行并创建新子节点。</li>
 *   <li><b>模拟 (Rollout)</b>：从新节点出发随机走子至终局或达到步数上限（200步）。</li>
 *   <li><b>反向传播 (Backpropagate)</b>：将模拟结果沿父链向上更新访问次数和累计价值。</li>
 * </ol>
 */
public class MCTSAgent {

    /** 模拟阶段的最大随机走子步数 */
    private static final int MAX_ROLLOUT_STEPS = 200;

    /** 随机数生成器，用于模拟阶段的随机走子 */
    private final Random random = new Random();

    // ══════════════════════════════════════════════
    // 内部类：MCTSNode
    // ══════════════════════════════════════════════

    /**
     * MCTS 搜索树中的一个节点。
     */
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

        /** 是否已完全展开（所有合法走法都有对应子节点） */
        boolean expanded;

        /**
         * 创建 MCTS 节点。
         *
         * @param move   到达此节点的着法，根节点传 null
         * @param parent 父节点，根节点传 null
         */
        MCTSNode(Move move, MCTSNode parent) {
            this.move = move;
            this.parent = parent;
            this.children = new ArrayList<>();
            this.visitCount = 0;
            this.totalValue = 0.0;
            this.expanded = false;
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
        // 无合法走法时直接返回 null
        List<Move> rootLegalMoves = ctx.generateLegalMoves();
        if (rootLegalMoves.isEmpty()) {
            return null;
        }

        // fork 一份上下文副本，避免污染原始 ctx
        SimulationContext forkCtx = ctx.fork();
        MCTSNode root = new MCTSNode(null, null);
        long startTime = System.currentTimeMillis();

        for (int sim = 0; sim < numSimulations; sim++) {
            // ── 时间限制检查 ──
            if (timeLimitMs > 0 && System.currentTimeMillis() - startTime >= timeLimitMs) {
                break;
            }

            int totalMoves = 0;   // 本次迭代中 simulateMove 的总次数（用于后续撤销）
            MCTSNode node = root;

            // ──────────────────────────────────────
            // 1. 选择 (Selection)
            //    沿树下降，使用 UCB1 选择子节点，
            //    直到遇到未完全展开或终止节点。
            // ──────────────────────────────────────
            while (true) {
                List<Move> moves = forkCtx.generateLegalMoves();
                if (moves.isEmpty()) {
                    // 终止节点：当前方无合法走法
                    break;
                }
                if (node.expanded) {
                    // 已完全展开 → 选择 UCB 最高的子节点继续下降
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
                    // 未完全展开 → 跳出选择阶段，进入扩展
                    break;
                }
            }

            // ──────────────────────────────────────
            // 2. 扩展 (Expansion)
            //    从未展开的合法走法中选择一个执行，
            //    创建新子节点。
            // ──────────────────────────────────────
            List<Move> parentMoves = forkCtx.generateLegalMoves();
            if (!parentMoves.isEmpty() && !node.expanded) {
                Move unexpanded = pickUnexpandedMove(node, parentMoves);
                if (unexpanded != null) {
                    forkCtx.simulateMove(
                            unexpanded.getFromRow(), unexpanded.getFromCol(),
                            unexpanded.getToRow(), unexpanded.getToCol());
                    totalMoves++;

                    MCTSNode child = new MCTSNode(unexpanded, node);
                    node.children.add(child);

                    // 更新父节点 expanded 状态
                    node.expanded = (node.children.size() >= parentMoves.size());

                    node = child;
                }
            }

            // ──────────────────────────────────────
            // 3. 模拟 (Rollout / Evaluation)
            //    从当前节点出发，随机走子直至终局或达到步数上限。
            // ──────────────────────────────────────
            int rolloutSteps = 0;
            double value = 0;
            boolean terminalReached = false;

            while (rolloutSteps < MAX_ROLLOUT_STEPS) {
                List<Move> rMoves = forkCtx.generateLegalMoves();
                if (rMoves.isEmpty()) {
                    // 终局：当前回合方无合法走法 → 该方落败
                    // 红方回合无走子 → 黑胜 → 返回 -1
                    // 黑方回合无走子 → 红胜 → 返回 1
                    value = forkCtx.isRedTurn() ? -1 : 1;
                    terminalReached = true;
                    break;
                }
                // 随机选择一步走子
                Move randomMove = rMoves.get(random.nextInt(rMoves.size()));
                forkCtx.simulateMove(
                        randomMove.getFromRow(), randomMove.getFromCol(),
                        randomMove.getToRow(), randomMove.getToCol());
                rolloutSteps++;
            }

            if (!terminalReached) {
                // 达到步数上限，判定为平局
                value = 0;
            }
            totalMoves += rolloutSteps;

            // ──────────────────────────────────────
            // 4. 反向传播 (Backpropagation)
            //    沿父链向上更新 visitCount 和 totalValue。
            //    value 的正负需要交替（回合交替，对手得分取反）。
            // ──────────────────────────────────────
            MCTSNode bpNode = node;
            while (bpNode != null) {
                bpNode.visitCount++;
                bpNode.totalValue += value;
                value = -value;       // 正负交替
                bpNode = bpNode.parent;
            }

            // ──────────────────────────────────────
            // 5. 撤销本次迭代的所有走子
            //    将 forkCtx 恢复到根节点状态，为下一次迭代做准备。
            // ──────────────────────────────────────
            for (int i = 0; i < totalMoves; i++) {
                forkCtx.simulateUndo();
            }
        }

        // ── 返回根节点的子节点中 visitCount 最高的对应着法 ──
        MCTSNode bestChild = null;
        int maxVisits = -1;
        for (MCTSNode child : root.children) {
            if (child.visitCount > maxVisits) {
                maxVisits = child.visitCount;
                bestChild = child;
            }
        }
        return bestChild != null ? bestChild.move : null;
    }

    // ══════════════════════════════════════════════
    // 工具方法
    // ══════════════════════════════════════════════

    /**
     * 检查当前局面是否为终止局面（无合法走法）。
     *
     * @param ctx 模拟上下文
     * @return 如果 generateLegalMoves() 返回空列表则返回 true
     */
    private boolean isTerminal(SimulationContext ctx) {
        return ctx.generateLegalMoves().isEmpty();
    }

    /**
     * 使用 UCB1 公式选择最佳子节点。
     * <pre>
     *   UCB = (totalValue / visitCount) + sqrt(2 * ln(parent.visitCount) / visitCount)
     * </pre>
     * 如果子节点 visitCount = 0，UCB 设为 {@link Double#MAX_VALUE}，
     * 确保未访问节点被优先探索。
     *
     * @param parent 父节点
     * @return UCB 值最高的子节点，如果没有子节点则返回 null
     */
    private MCTSNode selectBestChild(MCTSNode parent) {
        MCTSNode best = null;
        double bestUCB = Double.NEGATIVE_INFINITY;

        for (MCTSNode child : parent.children) {
            double ucb;
            if (child.visitCount == 0) {
                // 优先探索未访问节点
                ucb = Double.MAX_VALUE;
            } else {
                double exploitation = child.totalValue / child.visitCount;
                double exploration = Math.sqrt(
                        2.0 * Math.log(parent.visitCount) / child.visitCount);
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
     * 从未展开的合法走法中选择一个。
     * <p>
     * 遍历合法走法列表，返回第一个还没有对应子节点的走法。
     * </p>
     *
     * @param node       当前节点
     * @param legalMoves 合法走法列表
     * @return 未展开的走法，如果全部已展开则返回 null
     */
    private Move pickUnexpandedMove(MCTSNode node, List<Move> legalMoves) {
        for (Move m : legalMoves) {
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

    /**
     * 比较两个着法是否相等（按起止坐标及堆叠选择索引比较）。
     * <p>
     * 由于 {@link Move} 未重写 {@code equals}，这里通过行列坐标和
     * {@code selectedStackIndex} 进行等价判断。堆叠玩法下不同
     * {@code selectedStackIndex} 的着法应视为不同走法。
     * </p>
     *
     * @param a 着法 A
     * @param b 着法 B
     * @return 起止坐标及堆叠选择索引均相同时返回 true
     */
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
