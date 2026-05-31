package io.github.samera2022.chinese_chess.ai;

import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.spi.AIStrategy;
import io.github.samera2022.chinese_chess.common.spi.AIStrategyConfig;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.common.spi.SimulationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 强化学习 AI 的推理阶段入口。
 *
 * <p>该类实现 {@link AIStrategy} 接口，作为强化学习 AI 的统一入口。
 * 当前为 Phase 1 基线实现，使用纯 MCTS（蒙特卡洛树搜索）进行着法选择，
 * 无需加载神经网络模型。后续 Phase 2+ 可在此类中集成神经网络评估。
 * </p>
 *
 * <h3>配置参数</h3>
 * <ul>
 *   <li><b>numSimulations</b>（int，默认 400）：MCTS 每步的模拟次数。</li>
 * </ul>
 *
 * @see MCTSAgent
 * @see AIStrategy
 */
public class RuleAwareAI implements AIStrategy {

    /** MCTS 每步模拟次数，默认 400 */
    private int numSimulations = 400;

    /** 当前配置 */
    private AIStrategyConfig currentConfig;

    /** PyTorch 模型桥接，为 MCTS 提供神经网络评估（Phase 1 使用 fallback 模式） */
    private final PyTorchBridge pytorchBridge;

    /**
     * 构造 RuleAwareAI，使用默认配置初始化。
     *
     * <p>在构造函数中初始化 {@link PyTorchBridge} 为 fallback 模式，
     * 后续 Phase 2+ 可通过 {@link #getPyTorchBridge()} 替换为真实模型。</p>
     */
    public RuleAwareAI() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("numSimulations", 400);
        this.currentConfig = new AIStrategyConfig(defaults);
        this.numSimulations = currentConfig.getInt("numSimulations", 400);
        this.pytorchBridge = PyTorchBridge.createFallback();
    }

    /**
     * 返回 AI 策略名称。
     *
     * @return "RuleAware-RL"
     */
    @Override
    public String getName() {
        return "RuleAware-RL";
    }

    /**
     * 查找当前局面下的最佳着法。
     *
     * <p>当前阶段使用纯 MCTS 搜索，不依赖神经网络评估。
     * 创建 {@link MCTSAgent} 实例并委托其执行搜索。
     * {@link #pytorchBridge} 已初始化（Phase 1 fallback 模式），
     * 供后续 Phase 2+ 在 MCTSAgent 内部集成神经网络评估时使用。</p>
     *
     * @param ctx         当前局面上下文
     * @param maxDepth    最大搜索深度（当前未使用，保留供后续扩展）
     * @param timeLimitMs 时间限制（毫秒），≤ 0 表示无时间限制
     * @return 最佳着法；如果当前无合法走法则返回 null
     */
    @Override
    public Move findBestMove(SimulationContext ctx, int maxDepth, long timeLimitMs) {
        // 获取只读棋盘，用于后续可能的局面评估（当前阶段 MCTSAgent 不需要神经网络）
        ReadonlyBoard board = ctx.getBoard();

        // 创建 MCTSAgent 实例并执行搜索
        MCTSAgent mctsAgent = new MCTSAgent();
        return mctsAgent.findBestMove(ctx, numSimulations, timeLimitMs);
    }

    /**
     * 获取 PyTorchBridge 实例。
     *
     * <p>Phase 1 返回 fallback 模式实例，Phase 2+ 可用于替换为真实模型。
     * 调用方可直接对返回的实例调用 {@link PyTorchBridge#loadModel(String)}
     * 加载 TorchScript 模型。</p>
     *
     * @return PyTorchBridge 实例（不为 {@code null}）
     */
    public PyTorchBridge getPyTorchBridge() {
        return pytorchBridge;
    }

    /**
     * 获取当前 AI 策略配置。
     *
     * @return 当前配置对象
     */
    @Override
    public AIStrategyConfig getConfig() {
        return currentConfig;
    }

    /**
     * 应用新的 AI 策略配置。
     *
     * <p>从配置中读取 {@code numSimulations} 参数并更新内部状态。
     * </p>
     *
     * @param config 新的配置对象
     */
    @Override
    public void applyConfig(AIStrategyConfig config) {
        this.currentConfig = config;
        this.numSimulations = config.getInt("numSimulations", 400);
    }
}
