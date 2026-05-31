package io.github.samera2022.chinese_chess.app.ui;

import io.github.samera2022.chinese_chess.ai.RuleAwareAI;
import io.github.samera2022.chinese_chess.common.GameStatus;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.spi.GameSession;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.core.engine.Board;
import io.github.samera2022.chinese_chess.core.engine.SimulationBoard;

import javax.swing.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 对弈模式管理器。
 *
 * <p>负责管理 AI 对弈模式的开关状态，以及在回合切换时
 * 自动触发 AI 走子（异步执行，不阻塞 EDT）。
 * AI 默认执黑方，可通过 {@link #setAIPlaysRed(boolean)} 配置执方。</p>
 *
 * <h3>线程安全</h3>
 * <ul>
 *   <li>AI 搜索在单线程 {@link ExecutorService} 中执行，避免阻塞 EDT。</li>
 *   <li>通过 {@link #aiRunning} 做幂等保护，同一时间只有一个 AI 搜索运行。</li>
 *   <li>AI 结果通过 {@link SwingUtilities#invokeLater} 应用到棋盘。</li>
 * </ul>
 */
public class AIModeEnabler {

    /** AI 模式开关 */
    private volatile boolean aiModeEnabled = false;

    /** 游戏会话，提供 forceApplyMove + 棋盘状态 */
    private final GameSession session;

    /** 控制器，用于更新 UI 状态 */
    private final GameController controller;

    /** 棋盘面板，用于刷新棋盘 */
    private final BoardPanel boardPanel;

    /** AI 搜索专用单线程池 */
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UCC-AI-Worker");
        t.setDaemon(true);
        return t;
    });

    /** 幂等保护：标记当前是否已有 AI 搜索正在运行 */
    private final AtomicBoolean aiRunning = new AtomicBoolean(false);

    /** AI 执红方开关（false = 执黑方，默认） */
    private volatile boolean aiPlaysRed = false;

    /**
     * 构造 AIModeEnabler。
     *
     * @param session   游戏会话
     * @param controller 游戏控制器
     * @param boardPanel 棋盘面板
     */
    public AIModeEnabler(GameSession session, GameController controller, BoardPanel boardPanel) {
        this.session = session;
        this.controller = controller;
        this.boardPanel = boardPanel;
    }

    /**
     * 设置 AI 模式开关。
     *
     * @param enabled true 开启 AI，false 关闭
     */
    public void setAIEnabled(boolean enabled) {
        boolean wasEnabled = this.aiModeEnabled;
        this.aiModeEnabled = enabled;

        // 如果新开启 AI，且当前轮到 AI 的回合，立即触发一次走子
        if (enabled && !wasEnabled && session.isRedTurn() == aiPlaysRed
                && session.getGameStatus() == GameStatus.RUNNING) {
            triggerAIMove();
        }
    }

    /**
     * 查询 AI 是否开启。
     *
     * @return true 表示 AI 模式已开启
     */
    public boolean isAIEnabled() {
        return aiModeEnabled;
    }

    /**
     * 当回合切换时调用，检查是否需要 AI 走子。
     *
     * <p>由 {@link GameController#onMoveExecuted(Move)} 和
     * {@link GameController#updateStatus()} 调用。</p>
     */
    public void onTurnChanged() {
        if (!aiModeEnabled) {
            return;
        }
        // 当轮到 AI 执方且游戏进行中时触发 AI 走子
        if (session.isRedTurn() == aiPlaysRed && session.getGameStatus() == GameStatus.RUNNING) {
            triggerAIMove();
        }
    }

    /**
     * 触发 AI 走子（异步执行）。
     *
     * <p>在后台线程中执行 AI 搜索，找到最佳着法后通过
     * {@link SwingUtilities#invokeLater} 应用到棋盘。
     * 具有幂等保护：同一时间只有一个 AI 搜索在运行。</p>
     */
    public void triggerAIMove() {
        // 前提条件检查
        if (!aiModeEnabled) {
            return;
        }
        if (session.getGameStatus() != GameStatus.RUNNING) {
            return;
        }
        if (session.isRedTurn() != aiPlaysRed) {
            // 非 AI 执方回合，不触发
            return;
        }

        // 幂等保护
        if (!aiRunning.compareAndSet(false, true)) {
            return; // 已有 AI 搜索在运行
        }

        aiExecutor.submit(() -> {
            try {
                // 再次检查条件（可能在排队期间状态已变）
                if (!aiModeEnabled
                        || session.getGameStatus() != GameStatus.RUNNING
                        || session.isRedTurn() != aiPlaysRed) {
                    return;
                }

                // a. 从 session 直接获取 Board，省去 BoardState 序列化往返
                ReadonlyBoard rb = session.getBoard();
                if (!(rb instanceof Board)) {
                    return; // 安全检查：非 Board 实例无法构造 SimulationBoard
                }
                SimulationBoard simBoard = new SimulationBoard((Board) rb);

                // b. 创建 RuleAwareAI 实例
                RuleAwareAI ai = new RuleAwareAI();

                // c. 调用 AI 搜索：maxDepth=0（忽略），timeLimitMs=5000
                Move bestMove = ai.findBestMove(simBoard, 0, 5000);

                // d. 如果找到着法，通过 SwingUtilities.invokeLater 应用到棋盘
                if (bestMove != null) {
                    final int fr = bestMove.getFromRow();
                    final int fc = bestMove.getFromCol();
                    final int tr = bestMove.getToRow();
                    final int tc = bestMove.getToCol();

                    SwingUtilities.invokeLater(() -> {
                        try {
                            // forceApplyMove 直接应用，不经过合法性校验（信任 AI）
                            session.forceApplyMove(fr, fc, tr, tc, null /* promotionType */, -1 /* stackIndex */);

                            // e. 更新 UI
                            controller.updateStatus();
                            boardPanel.repaint();
                        } catch (Throwable t) {
                            System.err.println("[AIModeEnabler] Failed to apply AI move: " + t);
                            t.printStackTrace(System.err);
                        }
                    });
                }
            } catch (Throwable t) {
                System.err.println("[AIModeEnabler] AI search error: " + t);
                t.printStackTrace(System.err);
            } finally {
                aiRunning.set(false);
            }
        });
    }

    /**
     * 设置 AI 执方。
     *
     * @param playsRed true 表示 AI 执红方，false 表示 AI 执黑方（默认）
     */
    public void setAIPlaysRed(boolean playsRed) {
        this.aiPlaysRed = playsRed;
    }

    /**
     * 查询 AI 当前执方。
     *
     * @return true 表示 AI 执红方，false 表示执黑方
     */
    public boolean isAIPlaysRed() {
        return aiPlaysRed;
    }

    /**
     * 关闭 AI 执行器。
     * 应在应用退出时调用。
     */
    public void shutdown() {
        aiModeEnabled = false;
        aiExecutor.shutdownNow();
    }
}
