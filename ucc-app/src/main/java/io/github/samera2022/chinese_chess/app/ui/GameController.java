package io.github.samera2022.chinese_chess.app.ui;

import io.github.samera2022.chinese_chess.common.GameStatus;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.model.RuleChangeRecord;
import io.github.samera2022.chinese_chess.common.rules.RuleRegistry;
import io.github.samera2022.chinese_chess.common.spi.GameSession;
import io.github.samera2022.chinese_chess.common.spi.SessionListener;
import io.github.samera2022.chinese_chess.api.net.NetModeController;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.core.rules.RulesConfigProvider;

import javax.swing.*;

/**
 * 游戏控制器 - 走子协调、规则变更、状态同步。
 * 处理用户点击棋盘走子逻辑、规则开关变更时的棋盘/UI 协调、悔棋逻辑、与 GameEngine 交互的核心业务逻辑。
 */
public class GameController implements SessionListener {

    private final GameSession session;
    private final NetModeController netController;
    private final BoardPanel boardPanel;
    private final MoveHistoryPanel moveHistoryPanel;
    private final RuleSettingsPanel ruleSettingsPanel;
    private final InfoSidePanel infoSidePanel;
    private final ForceMoveHandler forceMoveHandler;
    private AIModeEnabler aiModeEnabler;
    private JButton undoButton;
    private JLabel statusLabel;
    private GameRulesConfig rulesConfig;

    // ruleChangeListener: initialized in constructor
    private GameRulesConfig.RuleChangeListener rulesChangeListener;
    // provider-level listener: initialized in constructor
    private RulesConfigProvider.InstanceChangeListener providerInstanceListener;

    public GameController(GameSession session, NetModeController netController,
                          BoardPanel boardPanel, MoveHistoryPanel moveHistoryPanel,
                          RuleSettingsPanel ruleSettingsPanel, InfoSidePanel infoSidePanel,
                          ForceMoveHandler forceMoveHandler) {
        this.session = session;
        this.netController = netController;
        this.boardPanel = boardPanel;
        this.moveHistoryPanel = moveHistoryPanel;
        this.ruleSettingsPanel = ruleSettingsPanel;
        this.infoSidePanel = infoSidePanel;
        this.forceMoveHandler = forceMoveHandler;
        this.rulesConfig = RulesConfigProvider.get();

        // ruleChangeListener: migrates across provider instances
        this.rulesChangeListener = (key, oldVal, newVal, source) -> {
            if (RuleRegistry.TOP_BOTTOM_CONNECTED.registryName.equals(key) && source != GameRulesConfig.ChangeSource.INTERNAL_CONSISTENCY) {
                SwingUtilities.invokeLater(() -> {
                    session.rebuildBoardForTopBottom();
                    boardPanel.setCellSizeForRows(session.getBoardRows());
                    boolean tbOn = session.getRuleBoolean(RuleRegistry.TOP_BOTTOM_CONNECTED.registryName);
                    infoSidePanel.setViewToggleEnabled(tbOn);
                    JFrame frame = getFrame();
                    frame.setResizable(true);
                    frame.pack();
                    frame.setResizable(false);
                    frame.revalidate();
                    frame.repaint();
                    updateStatus();
                    if (moveHistoryPanel != null) moveHistoryPanel.refreshHistory();
                    if (ruleSettingsPanel != null) ruleSettingsPanel.refreshUI();
                });
            }
            if (source == GameRulesConfig.ChangeSource.INTERNAL_CONSISTENCY) {
                return;
            }
            if (key != null && !RuleRegistry.ALLOW_UNDO.registryName.equals(key) && !RuleRegistry.SHOW_HINTS.registryName.equals(key)) {
                int afterMoveIndex = session.getMoveHistory().size() - 1;
                RuleChangeRecord record = new RuleChangeRecord(key, oldVal, newVal, afterMoveIndex);
                session.addRuleChangeToHistory(record);
                if (moveHistoryPanel != null) {
                    moveHistoryPanel.refreshHistory();
                }
            }
        };

        // provider-level listener to migrate ruleChangeListener
        this.providerInstanceListener = (oldInst, newInst) -> {
            try {
                if (oldInst != null) {
                    try { oldInst.removeRuleChangeListener(rulesChangeListener); } catch (Throwable t) {
                        System.err.println("[GameController] Failed to remove listener from old config: " + t);
                    }
                }
                if (newInst != null) {
                    try { newInst.addRuleChangeListener(rulesChangeListener); } catch (Throwable t) {
                        System.err.println("[GameController] Failed to add listener to new config: " + t);
                    }
                }
                this.rulesConfig = newInst;
            } catch (Throwable t) {
                System.err.println("[GameController] Failed to migrate provider instance: " + t);
            }
        };

        // 初始化 AIModeEnabler（在构造函数末尾，所有依赖对象已就绪）
        this.aiModeEnabler = new AIModeEnabler(session, this, boardPanel);
    }

    /** 初始化所有监听器，当 ChineseChessFrame 组件创建完成后调用 */
    public void install() {
        RulesConfigProvider.addInstanceChangeListener(providerInstanceListener);
        if (rulesConfig != null) rulesConfig.addRuleChangeListener(rulesChangeListener);
        session.addSessionListener(this);

        boardPanel.setLocalMoveListener((fr, fc, tr, tc) -> {
            if (netController.isActive()) {
                int selectedStackIndex = boardPanel.getSelectedStackIndex();
                netController.getSession().sendMove(fr, fc, tr, tc, selectedStackIndex);
            }
        });

        moveHistoryPanel.setStepChangeListener(step -> {
            session.rebuildBoardToStep(step);
            boardPanel.repaint();
            updateStatus();
        });
    }

    public void setUndoButton(JButton undoButton) {
        this.undoButton = undoButton;
    }

    public void setStatusLabel(JLabel statusLabel) {
        this.statusLabel = statusLabel;
    }

    public GameRulesConfig.RuleChangeListener getRuleChangeListener() {
        return rulesChangeListener;
    }

    public RulesConfigProvider.InstanceChangeListener getProviderInstanceListener() {
        return providerInstanceListener;
    }

    /** 获取 AI 模式管理器，供 InfoSidePanel 注入 */
    public AIModeEnabler getAIEnabler() {
        return aiModeEnabler;
    }

    /** 撤销 */
    public void undo() {
        if (session.undoLastMove()) {
            boardPanel.clearForceMoveIndicator();
            boardPanel.clearRemotePieceHighlight();
            if (netController.isActive()) {
                netController.getSession().sendUndo();
            }
            moveHistoryPanel.refreshHistory();
            boardPanel.repaint();
            updateStatus();
        }
    }

    /** 重新开始 */
    public void restart() {
        session.restart();
        boardPanel.clearSelection();
        boardPanel.clearForceMoveIndicator();
        boardPanel.clearRemotePieceHighlight();
        forceMoveHandler.clearPendingRequests();
        if (netController.isActive()) {
            netController.getSession().sendRestart();
        }
        moveHistoryPanel.hideNavigation();
        boardPanel.repaint();
        updateStatus();
    }

    /** 对端请求撤销 */
    public void onPeerUndo() {
        if (session.undoLastMove()) {
            boardPanel.clearForceMoveIndicator();
            boardPanel.clearRemotePieceHighlight();
            moveHistoryPanel.refreshHistory();
            boardPanel.repaint();
            updateStatus();
        }
    }

    /** 处理对方着法 */
    public void onPeerMove(int fromRow, int fromCol, int toRow, int toCol, int selectedStackIndex) {
        try {
            Piece piece = session.getPiece(fromRow, fromCol);
            if (piece == null) return;
            if (session.makeMove(fromRow, fromCol, toRow, toCol, null, selectedStackIndex)) {
                boardPanel.clearSelection();
                boardPanel.repaint();
                updateStatus();
                moveHistoryPanel.refreshHistory();
            } else {
                System.err.println("收到对手无效着法，请求重新同步...");
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    /** 对端重开 */
    public void onPeerRestart() {
        session.restart();
        boardPanel.clearForceMoveIndicator();
        boardPanel.clearRemotePieceHighlight();
        forceMoveHandler.clearPendingRequests();
        boardPanel.repaint();
        updateStatus();
    }

    /** 更新状态标签 */
    public void updateStatus() {
        if (statusLabel == null) return;
        GameStatus state = session.getGameStatus();
        String status;
        switch (state) {
            case RUNNING:
                String currentPlayer = session.isRedTurn() ? "红方" : "黑方";
                status = "轮到：" + currentPlayer;
                break;
            case RED_CHECKMATE:
                status = "黑方胜利！";
                break;
            case BLACK_CHECKMATE:
                status = "红方胜利！";
                break;
            case DRAW:
                status = "游戏平局";
                break;
            default:
                status = "游戏进行中";
        }
        statusLabel.setText(status);

        if (aiModeEnabler != null) {
            aiModeEnabler.onTurnChanged();
        }

        if (undoButton != null) {
            boolean allowUndo = rulesConfig.getBoolean(RuleRegistry.ALLOW_UNDO.registryName);
            if (!netController.isActive()) {
                undoButton.setEnabled(allowUndo);
            } else {
                Boolean localControls = boardPanel.getLocalControlsRed();
                boolean isLocalTurn = (localControls != null) && (session.isRedTurn() == localControls);
                undoButton.setEnabled(allowUndo && isLocalTurn);
            }
        }
    }

    @Override
    public void onGameStateChanged(GameStatus newState) {
        boardPanel.clearForceMoveIndicator();
        boardPanel.clearRemotePieceHighlight();
        updateStatus();
        boardPanel.repaint();
    }

    @Override
    public void onMoveExecuted(Move move) {
        boardPanel.repaint();
        updateStatus();
        if (aiModeEnabler != null) {
            aiModeEnabler.onTurnChanged();
        }
    }

    @Override
    public void onBoardChanged() {
        boardPanel.repaint();
    }

    /** 设置规则面板启用/禁用 */
    public void setRuleSettingsEnabled(boolean enabled) {
        if (ruleSettingsPanel != null) {
            ruleSettingsPanel.setEnabled(enabled);
            enableRecursively(ruleSettingsPanel, enabled);
        }
    }

    private void enableRecursively(java.awt.Container container, boolean enabled) {
        for (java.awt.Component c : container.getComponents()) {
            c.setEnabled(enabled);
            if (c instanceof java.awt.Container) {
                enableRecursively((java.awt.Container) c, enabled);
            }
        }
    }

    /** 关闭时清理资源 */
    public void shutdown() {
        try {
            if (aiModeEnabler != null) aiModeEnabler.shutdown();
        } catch (Throwable ignored) {}
        try {
            if (rulesConfig != null) rulesConfig.removeRuleChangeListener(rulesChangeListener);
        } catch (Throwable ignored) {}
        try { if (boardPanel != null) boardPanel.unbind(); } catch (Throwable ignored) {}
        RulesConfigProvider.removeInstanceChangeListener(providerInstanceListener);
    }

    private JFrame getFrame() {
        return (JFrame) SwingUtilities.getWindowAncestor(boardPanel);
    }

    private static void logError(Throwable t) {
        if (t == null) return;
        System.err.println("[GameController] " + t);
        try { t.printStackTrace(System.err); } catch (Throwable ignored) {}
    }
}
