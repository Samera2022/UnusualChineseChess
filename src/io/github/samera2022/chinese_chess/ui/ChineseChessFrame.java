package io.github.samera2022.chinese_chess.ui;

import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.UpdateInfo;
import io.github.samera2022.chinese_chess.engine.Board;
import io.github.samera2022.chinese_chess.engine.GameEngine;
import io.github.samera2022.chinese_chess.io.GameStateExporter;
import io.github.samera2022.chinese_chess.io.GameStateImporter;
import io.github.samera2022.chinese_chess.model.Move;
import io.github.samera2022.chinese_chess.model.Piece;
import io.github.samera2022.chinese_chess.model.RuleChangeRecord;
import io.github.samera2022.chinese_chess.net.NetModeController;
import io.github.samera2022.chinese_chess.net.NetworkSession;
import io.github.samera2022.chinese_chess.rules.RuleConstants;
import io.github.samera2022.chinese_chess.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.rules.RulesConfigProvider;
import io.github.samera2022.chinese_chess.rules.MoveValidator;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.Objects;

/**
 * 主窗口 - 中国象棋游戏的GUI
 */
public class ChineseChessFrame extends JFrame implements GameEngine.GameStateListener {
    // sequence generator for force-move requests
    private final java.util.concurrent.atomic.AtomicLong forceSeqGenerator = new java.util.concurrent.atomic.AtomicLong(1);
    // pending force requests (seq -> RequestInfo)
    private final java.util.Map<Long, RequestInfo> pendingForceRequests = new java.util.HashMap<>();
    // processed force requests from peer (to avoid duplicate dialogs)
    private final java.util.Set<Long> processedPeerRequests = new java.util.HashSet<>();

    private static class RequestInfo {
        final int fr, fc, tr, tc;
        final long seq;
        final int historyLen;
        int retries = 0;
        javax.swing.Timer timer;
        RequestInfo(int fr, int fc, int tr, int tc, long seq, int historyLen) { this.fr=fr;this.fc=fc;this.tr=tr;this.tc=tc;this.seq=seq;this.historyLen=historyLen; }
    }

    private GameEngine gameEngine;
    private GameRulesConfig rulesConfig;
    private BoardPanel boardPanel;
    private MoveHistoryPanel moveHistoryPanel;
    private JPanel rightPanel;
    private JToggleButton togglePanelButton;
    private JButton undoButton;
    private JButton restartButton;

    // 新的右侧网络面板
    private static NetModeController netController = new NetModeController();
    private InfoSidePanel infoSidePanel;
    private RuleSettingsPanel ruleSettingsPanel;
    private boolean ruleSettingsLocked = false;
    private JPanel westDock;

    // 状态标签（左侧）
    private JLabel statusLabel;

    // pending diffs to send to client (key -> new value)
    private final Object pendingDiffsLock = new Object();
    private JsonObject pendingDiffs = new JsonObject();

    // named listeners so they can be migrated when provider replaces the instance
    private final GameRulesConfig.RuleChangeListener rulesChangeListener = (key, oldVal, newVal, source) -> {
        // Record rule changes in history (except UI-related settings)
        // Record for all sources (UI and NETWORK) so both host and client have the records
        if (key != null && !RuleConstants.ALLOW_UNDO.equals(key) && !RuleConstants.SHOW_HINTS.equals(key)) {
            if (newVal instanceof Boolean) {
                boolean enabled = (Boolean) newVal;
                int afterMoveIndex = gameEngine.getMoveHistory().size() - 1;
                RuleChangeRecord record = new RuleChangeRecord(key, key, enabled, afterMoveIndex);
                gameEngine.addRuleChangeToHistory(record);
                // Update the move history panel
                if (moveHistoryPanel != null) {
                    moveHistoryPanel.refreshHistory();
                }
            }
        }

        // don't forward network-originated changes back to peer
        if (key == null || source == GameRulesConfig.ChangeSource.NETWORK) return;

        synchronized (pendingDiffsLock) {
            if (newVal == null) {
                pendingDiffs.add(key, com.google.gson.JsonNull.INSTANCE);
            } else if (newVal instanceof Boolean) {
                pendingDiffs.addProperty(key, (Boolean) newVal);
            } else if (newVal instanceof Number) {
                Number n = (Number) newVal;
                if (n instanceof Integer || n.intValue() == n.doubleValue()) pendingDiffs.addProperty(key, n.intValue());
                else pendingDiffs.addProperty(key, n.doubleValue());
            } else if (newVal instanceof String) {
                pendingDiffs.addProperty(key, (String) newVal);
            } else {
                pendingDiffs.addProperty(key, newVal.toString());
            }
        }
        // schedule debounced send on EDT
        SwingUtilities.invokeLater(this::sendSettingsSnapshotToClient);
    };
    private final RulesConfigProvider.InstanceChangeListener providerInstanceListener = (oldInst, newInst) -> {
        // migrate rule change listener from old instance to new instance and update cached ref
        try {
            if (oldInst != null) {
                try { oldInst.removeRuleChangeListener(rulesChangeListener); } catch (Throwable ignored) {}
            }
            if (newInst != null) {
                try { newInst.addRuleChangeListener(rulesChangeListener); } catch (Throwable ignored) {}
            }
            // update cached ref
            rulesConfig = newInst;
        } catch (Throwable ignored) {}
    };

    public ChineseChessFrame() {
        setTitle("不同寻常的中国象棋 - Unusual Chinese Chess");
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/UnusualChineseChess.png"))).getImage());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // Ensure engine-managed resources are shutdown when the window closes
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    if (gameEngine != null) {
                        gameEngine.shutdown();
                    }
                    try { if (rulesConfig != null) rulesConfig.removeRuleChangeListener(rulesChangeListener); } catch (Throwable ignored) {}
                    try { if (boardPanel != null) boardPanel.unbind(); } catch (Throwable ignored) {}
                    RulesConfigProvider.removeInstanceChangeListener(providerInstanceListener);
                    RulesConfigProvider.shutdown();
                } catch (Exception ignored) {}
            }
        });

        // 初始化游戏引擎
        gameEngine = new GameEngine();
        // cache rulesConfig reference for concise access in this frame (centralized provider)
        rulesConfig = RulesConfigProvider.get();
        // register provider-level migration listener so UI keeps observing the active config
        RulesConfigProvider.addInstanceChangeListener(providerInstanceListener);
        // register the change listener to collect diffs
        if (rulesConfig != null) rulesConfig.addRuleChangeListener(rulesChangeListener);
        gameEngine.addGameStateListener(this);

        // Also ensure engine-managed resources are stopped on JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (gameEngine != null) {
                    gameEngine.shutdown();
                }
                try { if (rulesConfig != null) rulesConfig.removeRuleChangeListener(rulesChangeListener); } catch (Throwable ignored) {}
                try { if (boardPanel != null) boardPanel.unbind(); } catch (Throwable ignored) {}
                RulesConfigProvider.removeInstanceChangeListener(providerInstanceListener);
                RulesConfigProvider.shutdown();
            } catch (Throwable ignored) {}
        }));


        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 棋盘 + 控制栏
        JPanel center = new JPanel(new BorderLayout());
        boardPanel = new BoardPanel(gameEngine);
        // 设置本地走子监听：若在联机中，发送到对端
        boardPanel.setLocalMoveListener((fr, fc, tr, tc) -> {
            if (netController.isActive()) {
                int selectedStackIndex = boardPanel.getSelectedStackIndex(); // 新增：获取当前选中的堆叠索引
                netController.getSession().sendMove(fr, fc, tr, tc, selectedStackIndex);
            }
        });
        // 设置强制走子请求监听：用户中键点击后发送强制走子请求
        boardPanel.setForceMoveRequestListener((fromRow, fromCol, toRow, toCol) -> {
            System.out.println("[DEBUG] 用户申请强制走子: " + fromRow + "," + fromCol + " -> " + toRow + "," + toCol);
            if (rulesConfig.getBoolean(RuleConstants.ALLOW_FORCE_MOVE)) {
                if (netController.isActive()) {
                    long seq = forceSeqGenerator.incrementAndGet();
                    int historyLen = gameEngine.getMoveHistory().size();
                    int selectedStackIndex = boardPanel.getSelectedStackIndex();
                    System.out.println("[DEBUG] 生成序列号: " + seq + ", 历史长度: " + historyLen);
                    try {
                        netController.getSession().sendForceMoveRequest(fromRow, fromCol, toRow, toCol, seq, historyLen, selectedStackIndex);
                        System.out.println("[DEBUG] 已发送FORCE_MOVE_REQUEST");
                        RequestInfo info = new RequestInfo(fromRow, fromCol, toRow, toCol, seq, historyLen);
                        pendingForceRequests.put(seq, info);
                        sendForceRequestWithRetry(seq);
                        System.out.println("[DEBUG] 已启动重试机制");
                        boardPanel.clearForceMoveIndicator();
                    } catch (Throwable t) {
                        System.out.println("[DEBUG] 发送强制走子请求失败: " + t);
                        logError(t);
                        JOptionPane.showMessageDialog(ChineseChessFrame.this, "发送强制走子请求失败：" + t.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        boardPanel.clearForceMoveIndicator();
                    }
                } else {
                    gameEngine.forceApplyMove(fromRow, fromCol, toRow, toCol);
                    boardPanel.clearForceMoveIndicator();
                }
            }else {
                JOptionPane.showMessageDialog(ChineseChessFrame.this, "强制走子已被禁用！", "提示", JOptionPane.INFORMATION_MESSAGE);
                boardPanel.clearForceMoveIndicator();
            }
        });
        center.add(boardPanel, BorderLayout.CENTER);
        center.add(createControlPanel(), BorderLayout.SOUTH);

        // 右侧：着法记录
        moveHistoryPanel = new MoveHistoryPanel(gameEngine);
        // 设置步数变化监听器
        moveHistoryPanel.setStepChangeListener(step -> {
            gameEngine.rebuildBoardToStep(step);
            boardPanel.repaint();
            updateStatus();
        });
        rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(moveHistoryPanel, BorderLayout.CENTER);

        // 左侧停靠：最左是设置，右边是“面板”
        ruleSettingsPanel = new RuleSettingsPanel();
        ruleSettingsPanel.setVisible(true);
        ruleSettingsPanel.bindSettings(new RuleSettingsPanel.SettingsBinder() {
            @Override public void setAllowUndo(boolean allowUndo) {
                if (!ruleSettingsLocked) rulesConfig.set(RuleConstants.ALLOW_UNDO, allowUndo, GameRulesConfig.ChangeSource.UI);
                // 联机时不直接禁用，由 updateStatus 按规则和回合判断
                updateStatus();
            }
            @Override public boolean isAllowUndo() { return rulesConfig.getBoolean(RuleConstants.ALLOW_UNDO); }
            @Override public void setAllowForceMove(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_FORCE_MOVE, allow, GameRulesConfig.ChangeSource.UI);}}
            @Override public boolean isAllowForceMove() { return rulesConfig.getBoolean(RuleConstants.ALLOW_FORCE_MOVE);}
            // 特殊玩法的设置：对每个 setter 应用更改并在是主机时同步给客户端
            @Override public void setAllowFlyingGeneral(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_FLYING_GENERAL, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setDisableFacingGenerals(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.DISABLE_FACING_GENERALS, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setPawnCanRetreat(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.PAWN_CAN_RETREAT, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setNoRiverLimit(boolean noLimit) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.NO_RIVER_LIMIT, noLimit, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setAdvisorCanLeave(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ADVISOR_CAN_LEAVE, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setInternationalKing(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.INTERNATIONAL_KING, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setPawnPromotion(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.PAWN_PROMOTION, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setAllowOwnBaseLine(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_OWN_BASE_LINE, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setAllowInsideRetreat(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_INSIDE_RETREAT, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setInternationalAdvisor(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.INTERNATIONAL_ADVISOR, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setAllowElephantCrossRiver(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setAllowAdvisorCrossRiver(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setAllowKingCrossRiver(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_KING_CROSS_RIVER, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setLeftRightConnected(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.LEFT_RIGHT_CONNECTED, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setLeftRightConnectedHorse(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setLeftRightConnectedElephant(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public boolean isAllowFlyingGeneral() { return rulesConfig.getBoolean(RuleConstants.ALLOW_FLYING_GENERAL); }
            @Override public boolean isDisableFacingGenerals() { return rulesConfig.getBoolean(RuleConstants.DISABLE_FACING_GENERALS); }
            @Override public boolean isPawnCanRetreat() { return rulesConfig.getBoolean(RuleConstants.PAWN_CAN_RETREAT); }
            @Override public boolean isNoRiverLimit() { return rulesConfig.getBoolean(RuleConstants.NO_RIVER_LIMIT); }
            @Override public boolean isAdvisorCanLeave() { return rulesConfig.getBoolean(RuleConstants.ADVISOR_CAN_LEAVE); }
            @Override public boolean isInternationalKing() { return rulesConfig.getBoolean(RuleConstants.INTERNATIONAL_KING); }
            @Override public boolean isPawnPromotion() { return rulesConfig.getBoolean(RuleConstants.PAWN_PROMOTION); }
            @Override public boolean isAllowOwnBaseLine() { return rulesConfig.getBoolean(RuleConstants.ALLOW_OWN_BASE_LINE); }
            @Override public boolean isAllowInsideRetreat() { return rulesConfig.getBoolean(RuleConstants.ALLOW_INSIDE_RETREAT); }
            @Override public boolean isInternationalAdvisor() { return rulesConfig.getBoolean(RuleConstants.INTERNATIONAL_ADVISOR); }
            @Override public boolean isAllowElephantCrossRiver() { return rulesConfig.getBoolean(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER); }
            @Override public boolean isAllowAdvisorCrossRiver() { return rulesConfig.getBoolean(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER); }
            @Override public boolean isAllowKingCrossRiver() { return rulesConfig.getBoolean(RuleConstants.ALLOW_KING_CROSS_RIVER); }
            @Override public boolean isLeftRightConnected() { return rulesConfig.getBoolean(RuleConstants.LEFT_RIGHT_CONNECTED); }
            @Override public boolean isLeftRightConnectedHorse() { return rulesConfig.getBoolean(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE); }
            @Override public boolean isLeftRightConnectedElephant() { return rulesConfig.getBoolean(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT); }
            @Override public void setUnblockPiece(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.UNBLOCK_PIECE, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setUnblockHorseLeg(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.UNBLOCK_HORSE_LEG, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public void setUnblockElephantEye(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.UNBLOCK_ELEPHANT_EYE, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public boolean isUnblockPiece() { return rulesConfig.getBoolean(RuleConstants.UNBLOCK_PIECE); }
            @Override public boolean isUnblockHorseLeg() { return rulesConfig.getBoolean(RuleConstants.UNBLOCK_HORSE_LEG); }
            @Override public boolean isUnblockElephantEye() { return rulesConfig.getBoolean(RuleConstants.UNBLOCK_ELEPHANT_EYE); }
            @Override public void setAllowCaptureOwnPiece(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_CAPTURE_OWN_PIECE, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public boolean isAllowCaptureOwnPiece() { return rulesConfig.getBoolean(RuleConstants.ALLOW_CAPTURE_OWN_PIECE); }
            @Override public void setAllowCaptureConversion(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_CAPTURE_CONVERSION, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public boolean isAllowCaptureConversion() { return rulesConfig.getBoolean(RuleConstants.ALLOW_CAPTURE_CONVERSION); }
            @Override public void setDeathMatchUntilVictory(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.DEATH_MATCH_UNTIL_VICTORY, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public boolean isDeathMatchUntilVictory() { return rulesConfig.getBoolean(RuleConstants.DEATH_MATCH_UNTIL_VICTORY); }
            @Override public void setAllowPieceStacking(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_PIECE_STACKING, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public boolean isAllowPieceStacking() { return rulesConfig.getBoolean(RuleConstants.ALLOW_PIECE_STACKING); }
            @Override public void setMaxStackingCount(int count) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.MAX_STACKING_COUNT, count, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public int getMaxStackingCount() { return rulesConfig.getInt(RuleConstants.MAX_STACKING_COUNT); }
            @Override public void setAllowCarryPiecesAbove(boolean allow) { if (!ruleSettingsLocked) { rulesConfig.set(RuleConstants.ALLOW_CARRY_PIECES_ABOVE, allow, GameRulesConfig.ChangeSource.UI); boardPanel.repaint(); } }
            @Override public boolean isAllowCarryPiecesAbove() { return rulesConfig.getBoolean(RuleConstants.ALLOW_CARRY_PIECES_ABOVE); }
        });

        // 面板，带"玩法设置"按钮，点击后切换左侧设置组件
        infoSidePanel = new InfoSidePanel(
                netController,
                boardPanel,
                gameEngine,
                undoButton,
                () -> {
                    ruleSettingsPanel.setVisible(!ruleSettingsPanel.isVisible());
                    ChineseChessFrame.this.pack();
                    ChineseChessFrame.this.revalidate();
                    ChineseChessFrame.this.repaint();
                },
                () -> ruleSettingsPanel.isVisible(),
                this::exportGameState,
                this::importGameState
        );
        infoSidePanel.setVisible(true);
        if (togglePanelButton != null) {
            togglePanelButton.setSelected(true);
            togglePanelButton.setText("隐藏面板");
        }

        westDock = new JPanel(new BorderLayout());
        westDock.add(ruleSettingsPanel, BorderLayout.WEST);
        westDock.add(infoSidePanel, BorderLayout.CENTER);

        // 组装
        mainPanel.add(westDock, BorderLayout.WEST);
        mainPanel.add(center, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);

        // 设置 networkSidePanel 的本地版本显示
        infoSidePanel.setLocalVersion(UpdateInfo.getLatestVersion());
        // 调整 NetworkSidePanel 内部监听以包含设置同步/锁定
        netController.getSession().setListener(new NetworkSession.SyncGameStateListener() {
            @Override
            public void onPeerMove(int fromRow, int fromCol, int toRow, int toCol, int selectedStackIndex) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        Piece piece = gameEngine.getBoard().getPiece(fromRow, fromCol);
                        if (piece == null) return;
                        if (gameEngine.makeMove(fromRow, fromCol, toRow, toCol, null, selectedStackIndex)) {
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
                });
            }
            @Override public void onPeerRestart() { SwingUtilities.invokeLater(() -> {
                gameEngine.restart();
                boardPanel.clearForceMoveIndicator();
                boardPanel.clearRemotePieceHighlight();
                // 清除已处理请求记录
                processedPeerRequests.clear();
                pendingForceRequests.clear();
                boardPanel.repaint();
                updateStatus();
            }); }
            @Override public void onDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> {
                    ruleSettingsLocked = false;
                    setRuleSettingsEnabled(true);
                    // 清除已处理请求记录
                    processedPeerRequests.clear();
                    pendingForceRequests.clear();
                    undoButton.setEnabled(true);
                    boardPanel.setLocalControlsRed(null);
                    infoSidePanel.onDisconnected(reason);
                });
            }
            @Override
            public void onConnected(String peerInfo) {
                SwingUtilities.invokeLater(() -> {
                    infoSidePanel.onConnected(peerInfo);

                    // 修改点 1：如果是主机，连接建立后立即发送完整的游戏状态快照
                    if (netController.isHost()) {
                        try {
                            // 使用 GameEngine 新增的直接获取快照方法
                            JsonObject fullState = gameEngine.getSyncState();
                            netController.getSession().sendSyncGameState(fullState);
                            System.out.println("[SYNC] 主机已发送完整对局状态");
                        } catch (Exception ex) {
                            System.err.println("[SYNC] 发送对局同步失败: " + ex);
                        }
                    }

                    ruleSettingsLocked = !netController.isHost();
                    setRuleSettingsEnabled(netController.isHost());
                    ruleSettingsPanel.refreshFromBinder();
                    updateStatus();
                });
            }
            @Override public void onPong(long sentMillis, long rttMillis) { SwingUtilities.invokeLater(() -> infoSidePanel.onPong(sentMillis, rttMillis)); }
            @Override public void onPeerVersion(String version) { SwingUtilities.invokeLater(() -> infoSidePanel.setPeerVersion(version)); }
            @Override public void onForceMoveRequest(int fromRow, int fromCol, int toRow, int toCol, long seq, int historyLen, int selectedStackIndex) { SwingUtilities.invokeLater(() -> {
                        System.out.println("[DEBUG] 收到强制走子请求: " + fromRow + "," + fromCol + " -> " + toRow + "," + toCol + " (seq=" + seq + ", historyLen=" + historyLen + ")");

                        // 检查是否已经处理过这个请求（防止重复弹窗）
                        if (processedPeerRequests.contains(seq)) {
                            System.out.println("[DEBUG] 该请求已处理过，忽略 (seq=" + seq + ")");
                            return;
                        }

                        // 标记为已处理
                        processedPeerRequests.add(seq);
                        System.out.println("[DEBUG] 标记请求为已处理 (seq=" + seq + ")");

                        // Validate history length first to avoid replay/old requests
                        int localHistory = gameEngine.getMoveHistory().size();
                        System.out.println("[DEBUG] 本地历史长度: " + localHistory + ", 对端历史长度: " + historyLen);
                        if (historyLen >= 0 && historyLen != localHistory) {
                            // Reject: history mismatch
                            System.out.println("[DEBUG] 历史记录不匹配，拒绝强制走子");
                            try { netController.getSession().sendForceMoveReject(fromRow, fromCol, toRow, toCol, seq, "history_mismatch"); } catch (Throwable ignored) {}
                            return;
                        }

                        // 在棋盘上显示对方选中的棋子（黄色高亮）和红色移动指示器
                        System.out.println("[DEBUG] 显示对方棋子高亮和红色指示器");
                        boardPanel.setRemotePieceHighlight(fromRow, fromCol);
                        boardPanel.setForceMoveIndicator(fromRow, fromCol, toRow, toCol);
                        boardPanel.repaint();

                        // Received a force request from peer. Validate locally using a transient MoveValidator
                        MoveValidator tmpValidator = new MoveValidator(gameEngine.getBoard());
                        tmpValidator.setRulesConfig(gameEngine.getRulesConfig());
                        boolean localValid = tmpValidator.isValidMove(fromRow, fromCol, toRow, toCol);
                        System.out.println("[DEBUG] 本地移动有效性: " + localValid);

                        // 总是显示确认对话框让用户决定
                        System.out.println("[DEBUG] 显示确认对话框");
                        int ans = JOptionPane.showConfirmDialog(ChineseChessFrame.this,
                                String.format("对方申请强制走子 %d,%d → %d,%d，是否同意？", fromRow, fromCol, toRow, toCol),
                                "强制走子申请", JOptionPane.YES_NO_OPTION);
                        System.out.println("[DEBUG] 用户选择: " + (ans == JOptionPane.YES_OPTION ? "是" : "否"));

                        if (ans == JOptionPane.YES_OPTION) {
                            try { netController.getSession().sendForceMoveConfirm(fromRow, fromCol, toRow, toCol, seq, selectedStackIndex); } catch (Throwable ignored) {}
                        } else {
                            try { netController.getSession().sendForceMoveReject(fromRow, fromCol, toRow, toCol, seq, "user_rejected"); } catch (Throwable ignored) {}
                        }
                        boardPanel.clearRemotePieceHighlight();
                        boardPanel.clearForceMoveIndicator();
                    }); }
            @Override public void onForceMoveConfirm(int fromRow, int fromCol, int toRow, int toCol, long seq, int selectedStackIndex) { SwingUtilities.invokeLater(() -> {
                         System.out.println("[DEBUG] 收到强制走子确认: " + fromRow + "," + fromCol + " -> " + toRow + "," + toCol + " (seq=" + seq + ")");

                         // Peer confirmed our force request. Find pending and apply.
                         RequestInfo info = pendingForceRequests.remove(seq);
                         if (info != null && info.timer != null) { info.timer.stop(); }
                         try {
                             JOptionPane.showMessageDialog(ChineseChessFrame.this, "对方已同意你的强制走子", "强制走子成功", JOptionPane.INFORMATION_MESSAGE);
                             Piece.Type promotionType = resolveForcePromotionType(fromRow, fromCol, toRow, toCol);
                             boolean applied = false;
                             try {
                                 java.lang.reflect.Method m = gameEngine.getClass().getMethod("forceApplyMove", int.class, int.class, int.class, int.class, Piece.Type.class, int.class);
                                 Object res = m.invoke(gameEngine, fromRow, fromCol, toRow, toCol, promotionType, selectedStackIndex);
                                 if (res instanceof Boolean) applied = (Boolean) res;
                             } catch (NoSuchMethodException nsme) {
                                 applied = gameEngine.makeMove(fromRow, fromCol, toRow, toCol, promotionType, selectedStackIndex);
                             }
                             if (applied) {
                                 boardPanel.clearSelection();
                                 boardPanel.repaint();
                                 updateStatus();
                                 try {
                                     String promoName = promotionType != null ? promotionType.name() : null;
                                     netController.getSession().sendForceMoveApplied(fromRow, fromCol, toRow, toCol, seq, promoName, selectedStackIndex);
                                 } catch (Throwable ignored) {}
                             }
                         } catch (Throwable t) { logError(t); }
                     }); }
            @Override public void onForceMoveApplied(int fromRow, int fromCol, int toRow, int toCol, long seq, String promotionTypeName, int selectedStackIndex) { SwingUtilities.invokeLater(() -> {
                         RequestInfo info = pendingForceRequests.remove(seq);
                         if (info != null && info.timer != null) { info.timer.stop(); }
                         try {
                             Piece.Type promoType = null;
                             try { if (promotionTypeName != null) promoType = Piece.Type.valueOf(promotionTypeName); } catch (Exception ignored) {}
                             boolean applied = false;
                             try {
                                 java.lang.reflect.Method m = gameEngine.getClass().getMethod("forceApplyMove", int.class, int.class, int.class, int.class, Piece.Type.class, int.class);
                                 Object res = m.invoke(gameEngine, fromRow, fromCol, toRow, toCol, promoType, selectedStackIndex);
                                 if (res instanceof Boolean) applied = (Boolean) res;
                             } catch (NoSuchMethodException nsme) {
                                 applied = gameEngine.makeMove(fromRow, fromCol, toRow, toCol, promoType, selectedStackIndex);
                             }
                             if (applied) {
                                 boardPanel.clearSelection();
                                 boardPanel.repaint();
                                 updateStatus();
                             }
                         } catch (Throwable t) { logError(t); }
                     }); }
            @Override public void onForceMoveReject(int fromRow, int fromCol, int toRow, int toCol, long seq, String reason) { SwingUtilities.invokeLater(() -> {
                        System.out.println("[DEBUG] 收到强制走子拒绝: " + fromRow + "," + fromCol + " -> " + toRow + "," + toCol + " (seq=" + seq + ", reason=" + reason + ")");

                        // Peer rejected our force request: stop retry and notify user
                        RequestInfo info = pendingForceRequests.remove(seq);
                        if (info != null && info.timer != null) info.timer.stop();
                        // 清除指示器
                        boardPanel.clearForceMoveIndicator();
                        // 翻译原因
                        String reasonMsg = reason;
                        if ("history_mismatch".equals(reason)) {
                            reasonMsg = "历史记录不匹配";
                        } else if ("user_rejected".equals(reason)) {
                            reasonMsg = "对方拒绝";
                        }
                        // notify user
                        JOptionPane.showMessageDialog(ChineseChessFrame.this, "对方已拒绝你的强制走子", "强制走子被拒绝", JOptionPane.WARNING_MESSAGE);
                    }); }
             @Override public void onSettingsReceived(JsonObject settings) {
                SwingUtilities.invokeLater(() -> {
                    // 转发给 NetworkSidePanel 处理持方同步
                    infoSidePanel.onSettingsReceived(settings);
                    // 处理游戏规则设置同步
                    if (!netController.isHost()) {
                        gameEngine.applySettingsSnapshot(settings);
                        // 在线状态下撤销按钮权限交由 updateStatus 判断
                        ruleSettingsPanel.refreshFromBinder();
                        updateStatus();
                    }
                 });
             }
            @Override public void onPeerUndo() {
                SwingUtilities.invokeLater(() -> {
                    // 对端请求撤销，本地执行一次撤销并刷新UI
                    if (gameEngine.undoLastMove()) {
                        boardPanel.clearForceMoveIndicator();
                        boardPanel.clearRemotePieceHighlight();
                        moveHistoryPanel.refreshHistory();
                        boardPanel.repaint();
                        updateStatus();
                    }
                });
            }
            @Override
            public void onSyncGameStateReceived(JsonObject state) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        System.out.println("[SYNC] 收到对局状态，开始恢复...");
                        // 调用修改后的 GameEngine 方法
                        gameEngine.loadSyncState(state);
                        // 强制刷新所有 UI 组件
                        boardPanel.clearSelection();
                        boardPanel.clearForceMoveIndicator();
                        boardPanel.clearRemotePieceHighlight();
                        boardPanel.repaint();
                        updateStatus();
                        if (ruleSettingsPanel != null) ruleSettingsPanel.refreshFromBinder();
                        moveHistoryPanel.refreshHistory();
                        moveHistoryPanel.showNavigation();
                        JOptionPane.showMessageDialog(ChineseChessFrame.this,
                                "已成功加入对局并同步当前状态！", "同步成功", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        // 详细打印异常堆栈
                        ex.printStackTrace();
                        System.err.println("[SYNC][ERROR] 对局同步失败: " + ex.getMessage());
                        for (StackTraceElement ste : ex.getStackTrace()) {
                            System.err.println("    at " + ste);
                        }
                        JOptionPane.showMessageDialog(ChineseChessFrame.this,
                                "对局同步失败: " + ex.getMessage() + "\n请查看控制台获取详细错误堆栈。",
                                "同步错误", JOptionPane.ERROR_MESSAGE);
                        logError(ex);
                    }
                });
            }
        });

        updateStatus();
    }

    /**
     * 创建控制面板
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(240, 240, 240));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 状态标签（左侧）
        statusLabel = new JLabel();
        statusLabel.setFont(new Font("SimHei", Font.BOLD, 16));
        panel.add(statusLabel, BorderLayout.WEST);

        // 按钮面板
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonBar.setBackground(new Color(240, 240, 240));

        // 撤销按钮
        undoButton = new JButton("撤销");
        undoButton.setFont(new Font("SimHei", Font.PLAIN, 14));
        undoButton.addActionListener(e -> {
            // 本地先尝试撤销（包含规则开关判断）
            if (gameEngine.undoLastMove()) {
                boardPanel.clearForceMoveIndicator();
                boardPanel.clearRemotePieceHighlight();
                // 若处于联机，对端也撤销一步以保持一致
                if (netController.isActive()) {
                    netController.getSession().sendUndo();
                }
                moveHistoryPanel.refreshHistory();
                boardPanel.repaint();
                updateStatus();
            }
        });
        buttonBar.add(undoButton);

        // 重新开始按钮
        restartButton = new JButton("重新开始");
        restartButton.setFont(new Font("SimHei", Font.PLAIN, 14));
        restartButton.addActionListener(e -> {
            gameEngine.restart();
            boardPanel.clearSelection();
            boardPanel.clearForceMoveIndicator();
            boardPanel.clearRemotePieceHighlight();
            // 若处于联机，对端也重开
            if (netController.isActive()) {
                netController.getSession().sendRestart();
            }
            // 隐藏导航面板
            moveHistoryPanel.hideNavigation();
            boardPanel.repaint();
            updateStatus();
        });
        buttonBar.add(restartButton);


        // 单按钮切换"面板"
        togglePanelButton = new JToggleButton("显示面板");
        togglePanelButton.setFont(new Font("SimHei", Font.PLAIN, 14));
        togglePanelButton.addActionListener(e -> {
            boolean show = togglePanelButton.isSelected();
            infoSidePanel.setVisible(show);
            togglePanelButton.setText(show ? "隐藏面板" : "显示面板");
            ChineseChessFrame.this.pack();
            ChineseChessFrame.this.revalidate();
            ChineseChessFrame.this.repaint();
        });
        buttonBar.add(togglePanelButton);

        panel.add(buttonBar, BorderLayout.EAST);

        return panel;
    }

    /**
     * 更新状态标签
     */
    void updateStatus() {
        GameEngine.GameState state = gameEngine.getGameState();
        String status;

        switch (state) {
            case RUNNING:
                String currentPlayer = gameEngine.isRedTurn() ? "红方" : "黑方";
                status = "轮到：" + currentPlayer;
                break;
            case RED_CHECKMATE:
                status = "黑方胜利！";  // 红方被将死 = 黑方胜利
                break;
            case BLACK_CHECKMATE:
                status = "红方胜利！";  // 黑方被将死 = 红方胜利
                break;
            case DRAW:
                status = "游戏平局";
                break;
            default:
                status = "游戏进行中";
        }

        if (statusLabel != null) {
            statusLabel.setText(status);
        }

        // 统一控制撤销按钮的可用性：
        // 离线：按设置开关启用/禁用；
        // 联机：当轮到本地一方时启用（可撤销对方上一步），否则禁用。
        boolean allowUndo = rulesConfig.getBoolean(RuleConstants.ALLOW_UNDO);
        if (!netController.isActive()) {
            undoButton.setEnabled(allowUndo);
        } else {
            Boolean localControls = boardPanel.getLocalControlsRed();
            boolean isLocalTurn = (localControls != null) && (gameEngine.isRedTurn() == localControls);
            undoButton.setEnabled(allowUndo && isLocalTurn);
        }
    }

    @Override
    public void onGameStateChanged(GameEngine.GameState newState) {
        // 游戏状态改变时清除强制走子指示器
        boardPanel.clearForceMoveIndicator();
        boardPanel.clearRemotePieceHighlight();
        updateStatus();
        boardPanel.repaint();
    }

    @Override
    public void onMoveExecuted(Move move) {
        boardPanel.repaint();
        updateStatus();
    }

    private void setRuleSettingsEnabled(boolean enabled) {
        ruleSettingsPanel.setEnabled(enabled);
        enableRecursively(ruleSettingsPanel, enabled);
    }

    private void enableRecursively(Container container, boolean enabled) {
        for (Component c : container.getComponents()) {
            c.setEnabled(enabled);
            if (c instanceof Container) {
                enableRecursively((Container) c, enabled);
            }
        }
    }

    /**
     * 导出游戏状态
     */
    private void exportGameState() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导出残局");
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON文件 (*.json)", "json"));
        fileChooser.setSelectedFile(new File("endgame.json"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            // 确保文件名以.json结尾
            String filePath = file.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".json")) {
                filePath += ".json";
            }

            try {
                GameStateExporter.exportGameState(gameEngine, filePath);
                JOptionPane.showMessageDialog(this,
                    "残局导出成功！\n文件路径: " + filePath,
                    "导出成功",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "残局导出失败: " + ex.getMessage(),
                    "导出错误",
                    JOptionPane.ERROR_MESSAGE);
                logError(ex);
            }
        }
    }

    /**
     * 导入游戏状态
     */
    private void importGameState() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导入残局");
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON文件 (*.json)", "json"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            try {
                GameStateImporter.importGameState(gameEngine, file.getAbsolutePath());

                // 刷新UI
                boardPanel.repaint();
                updateStatus();

                // 刷新规则设置面板
                if (ruleSettingsPanel != null) {
                    ruleSettingsPanel.refreshFromBinder();
                }

                // 刷新着法记录显示并显示导航
                moveHistoryPanel.refreshHistory();
                moveHistoryPanel.showNavigation();

                JOptionPane.showMessageDialog(this,
                    "残局导入成功！",
                    "导入成功",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "导入失败: " + ex.getMessage(),
                    "导入错误",
                    JOptionPane.ERROR_MESSAGE);
                logError(ex);
             }
         }
     }

    // helper: 当当前是主机且已连接时，将设置快照发送给客户端
    private javax.swing.Timer sendSettingsTimer;

    private void initSettingsSendTimer() {
        // debounce: 延迟200ms发送，避免短时间内多次设置变动造成网络抖动
        sendSettingsTimer = new javax.swing.Timer(200, e -> doSendSettingsNow());
        sendSettingsTimer.setRepeats(false);
    }

    private void sendSettingsSnapshotToClient() {
        // 在UI线程被调用：启动/重启定时器以合并快速多次修改
        if (sendSettingsTimer == null) initSettingsSendTimer();
        sendSettingsTimer.restart();
    }

    private void doSendSettingsNow() {
        try {
            if (netController != null && netController.isActive() && netController.isHost()) {
                if (netController.getSession() != null) {
                    JsonObject toSend;
                    synchronized (pendingDiffsLock) {
                        if (pendingDiffs == null || pendingDiffs.entrySet().isEmpty()) {
                            // nothing to send
                            return;
                        }
                        toSend = pendingDiffs;
                        pendingDiffs = new JsonObject();
                    }
                    // send only the changed keys (diff)
                    netController.getSession().sendSettings(toSend);
                }
            }
        } catch (Exception ex) {
            // 防御性捕获，避免 UI 操作抛出未捕获异常
            System.out.println("[DEBUG] 发送设置快照失败: " + ex);
            logError(ex);
         }
     }
    // retry logic for force-move requests
    private void sendForceRequestWithRetry(long seq) {
        RequestInfo info = pendingForceRequests.get(seq);
        if (info == null) return;

        // give up after 3 retries
        if (info.retries++ > 2) {
            pendingForceRequests.remove(seq);
            return;
        }

        // send the request
        try {
            int selectedStackIndex = boardPanel.getSelectedStackIndex();
            netController.getSession().sendForceMoveRequest(info.fr, info.fc, info.tr, info.tc, seq, gameEngine.getMoveHistory().size(), selectedStackIndex);
        } catch (Throwable ignored) {}

        // install a timeout
        info.timer = new javax.swing.Timer(3000, e -> {
            // on timeout, retry sending
            sendForceRequestWithRetry(seq);
        });
        info.timer.setRepeats(false);
        info.timer.start();
    }

    // Centralized error logger to avoid direct printStackTrace calls.
    private static void logError(Throwable t) {
        if (t == null) return;
        System.err.println("[ChineseChessFrame] " + t);
        try { t.printStackTrace(System.err); } catch (Throwable ignored) {}
    }

    // helper: prompt promotion type when a forced move drives a soldier to baseline
    private Piece.Type resolveForcePromotionType(int fromRow, int fromCol, int toRow, int toCol) {
        Board board = gameEngine.getBoard();
        if (!board.isValid(fromRow, fromCol) || !board.isValid(toRow, toCol)) return null;
        Piece p = board.getPiece(fromRow, fromCol);
        if (p == null) return null;
        if (!rulesConfig.getBoolean(RuleConstants.PAWN_PROMOTION)) return null;
        boolean isSoldier = p.getType() == Piece.Type.RED_SOLDIER || p.getType() == Piece.Type.BLACK_SOLDIER;
        if (!isSoldier) return null;
        boolean isAtOpponentBaseLine = (p.isRed() && toRow == 0) || (!p.isRed() && toRow == 9);
        boolean isAtOwnBaseLine = (p.isRed() && toRow == 9) || (!p.isRed() && toRow == 0);
        boolean allowOwnBaseLine = rulesConfig.getBoolean(RuleConstants.ALLOW_OWN_BASE_LINE);
        if (!(isAtOpponentBaseLine || (isAtOwnBaseLine && allowOwnBaseLine))) return null;

        // prompt user for promotion choice
        Piece.Type[] types = p.isRed()
                ? new Piece.Type[]{Piece.Type.RED_CHARIOT, Piece.Type.RED_HORSE, Piece.Type.RED_CANNON, Piece.Type.RED_ELEPHANT, Piece.Type.RED_ADVISOR}
                : new Piece.Type[]{Piece.Type.BLACK_CHARIOT, Piece.Type.BLACK_HORSE, Piece.Type.BLACK_CANNON, Piece.Type.BLACK_ELEPHANT, Piece.Type.BLACK_ADVISOR};
        String[] options = new String[types.length];
        for (int i = 0; i < types.length; i++) options[i] = types[i].getChineseName();
        int choice = JOptionPane.showOptionDialog(this,
                "选择晋升的棋子：",
                "兵卒晋升",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
        if (choice >= 0 && choice < types.length) return types[choice];
        return null;
    }
    public static boolean isNetSessionActive(){
        return netController.isActive();
    }
}
