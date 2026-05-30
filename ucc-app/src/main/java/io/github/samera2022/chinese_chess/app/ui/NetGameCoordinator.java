package io.github.samera2022.chinese_chess.app.ui;

import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.common.UpdateInfo;
import io.github.samera2022.chinese_chess.common.spi.GameSession;
import io.github.samera2022.chinese_chess.core.engine.GameEngine;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.api.net.NetModeController;
import io.github.samera2022.chinese_chess.api.net.NetworkSession;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 网络对局协调器 - 网络会话生命周期管理、强制走子发送/接收、持方同步、棋盘布置模式、设置同步。
 */
public class NetGameCoordinator {

    private final GameSession session;
    private final GameEngine engineForSync;
    private final NetModeController netController;
    private final BoardPanel boardPanel;
    private final MoveHistoryPanel moveHistoryPanel;
    private final RuleSettingsPanel ruleSettingsPanel;
    private final InfoSidePanel infoSidePanel;
    private final ForceMoveHandler forceMoveHandler;
    private final GameController gameController;
    private final JButton undoButton;
    private final JFrame parentFrame;

    // pending diffs to send to client (key -> new value)
    private final Object pendingDiffsLock = new Object();
    private JsonObject pendingDiffs = new JsonObject();

    // timer for debounced settings sending
    private javax.swing.Timer sendSettingsTimer;

    // rule change listener to collect diffs
    private final GameRulesConfig.RuleChangeListener diffsCollector = (key, oldVal, newVal, source) -> {
        if (key == null || source == GameRulesConfig.ChangeSource.NETWORK
                || source == GameRulesConfig.ChangeSource.INTERNAL_CONSISTENCY) return;
        synchronized (pendingDiffsLock) {
            if (newVal instanceof Boolean) {
                pendingDiffs.addProperty(key, (Boolean) newVal);
            } else if (newVal instanceof Number) {
                pendingDiffs.addProperty(key, (Number) newVal);
            } else {
                pendingDiffs.addProperty(key, String.valueOf(newVal));
            }
        }
        SwingUtilities.invokeLater(this::sendSettingsSnapshotToClient);
    };

    public NetGameCoordinator(GameSession session, GameEngine engineForSync, NetModeController netController,
                              BoardPanel boardPanel, MoveHistoryPanel moveHistoryPanel,
                              RuleSettingsPanel ruleSettingsPanel, InfoSidePanel infoSidePanel,
                              ForceMoveHandler forceMoveHandler, GameController gameController,
                              JButton undoButton, JFrame parentFrame) {
        this.session = session;
        this.engineForSync = engineForSync;
        this.netController = netController;
        this.boardPanel = boardPanel;
        this.moveHistoryPanel = moveHistoryPanel;
        this.ruleSettingsPanel = ruleSettingsPanel;
        this.infoSidePanel = infoSidePanel;
        this.forceMoveHandler = forceMoveHandler;
        this.gameController = gameController;
        this.undoButton = undoButton;
        this.parentFrame = parentFrame;
    }

    /** 初始化网络会话监听器，注册diffs收集器到 rulesConfig */
    public void install() {
        // register diffs collector to collect rule changes for network sync
        GameRulesConfig rulesConfig = engineForSync.getRulesConfig();
        if (rulesConfig != null) rulesConfig.addRuleChangeListener(diffsCollector);

        netController.getSession().setListener(new NetworkSession.SyncGameStateListener() {
            @Override
            public void onPeerMove(int fromRow, int fromCol, int toRow, int toCol, int selectedStackIndex) {
                SwingUtilities.invokeLater(() -> gameController.onPeerMove(fromRow, fromCol, toRow, toCol, selectedStackIndex));
            }

            @Override
            public void onPeerRestart() {
                SwingUtilities.invokeLater(() -> gameController.onPeerRestart());
            }

            @Override
            public void onDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> onDisconnectedImpl(reason));
            }

            @Override
            public void onConnected(String peerInfo) {
                SwingUtilities.invokeLater(() -> onConnectedImpl(peerInfo));
            }

            @Override
            public void onPong(long sentMillis, long rttMillis) {
                SwingUtilities.invokeLater(() -> infoSidePanel.onPong(sentMillis, rttMillis));
            }

            @Override
            public void onPeerVersion(String version) {
                SwingUtilities.invokeLater(() -> infoSidePanel.setPeerVersion(version));
            }

            @Override
            public void onForceMoveRequest(int fromRow, int fromCol, int toRow, int toCol, long seq, int historyLen, int selectedStackIndex) {
                SwingUtilities.invokeLater(() -> forceMoveHandler.onPeerForceMoveRequest(fromRow, fromCol, toRow, toCol, seq, historyLen, selectedStackIndex));
            }

            @Override
            public void onForceMoveConfirm(int fromRow, int fromCol, int toRow, int toCol, long seq, int selectedStackIndex) {
                SwingUtilities.invokeLater(() -> {
                    forceMoveHandler.onPeerForceMoveConfirm(fromRow, fromCol, toRow, toCol, seq, selectedStackIndex);
                    gameController.updateStatus();
                });
            }

            @Override
            public void onForceMoveApplied(int fromRow, int fromCol, int toRow, int toCol, long seq, String promotionTypeName, int selectedStackIndex) {
                SwingUtilities.invokeLater(() -> {
                    forceMoveHandler.onPeerForceMoveApplied(fromRow, fromCol, toRow, toCol, seq, promotionTypeName, selectedStackIndex);
                    gameController.updateStatus();
                });
            }

            @Override
            public void onForceMoveReject(int fromRow, int fromCol, int toRow, int toCol, long seq, String reason) {
                SwingUtilities.invokeLater(() -> forceMoveHandler.onPeerForceMoveReject(fromRow, fromCol, toRow, toCol, seq, reason));
            }

            @Override
            public void onPeerUndo() {
                SwingUtilities.invokeLater(() -> gameController.onPeerUndo());
            }

            @Override
            public void onSettingsReceived(JsonObject settings) {
                SwingUtilities.invokeLater(() -> onSettingsReceivedImpl(settings));
            }

            @Override
            public void onSyncGameStateReceived(JsonObject state) {
                SwingUtilities.invokeLater(() -> onSyncGameStateReceivedImpl(state));
            }
        });
    }

    private void onConnectedImpl(String peerInfo) {
        infoSidePanel.onConnected(peerInfo);

        // 如果是主机，连接建立后立即发送完整的游戏状态快照
        if (netController.isHost()) {
            try {
                JsonObject fullState = engineForSync.getSyncState();
                netController.getSession().sendSyncGameState(fullState);
                System.out.println("[SYNC] 主机已发送完整对局状态");
            } catch (Exception ex) {
                System.err.println("[SYNC] 发送对局同步失败: " + ex);
            }
        }

        gameController.setRuleSettingsEnabled(netController.isHost());
        ruleSettingsPanel.refreshUI();
        gameController.updateStatus();
    }

    private void onDisconnectedImpl(String reason) {
        gameController.setRuleSettingsEnabled(true);
        forceMoveHandler.clearPendingRequests();
        undoButton.setEnabled(true);
        boardPanel.setLocalControlsRed(null);
        infoSidePanel.onDisconnected(reason);
        // 停止设置发送定时器
        if (sendSettingsTimer != null) {
            sendSettingsTimer.stop();
            sendSettingsTimer = null;
        }
    }

    private void onSettingsReceivedImpl(JsonObject settings) {
        // 检查是否是棋盘修改消息
        if (settings.has("cmd")) {
            String cmd = settings.get("cmd").getAsString();
            if ("BOARD_SETUP_PLACE".equals(cmd)) {
                int row = settings.get("row").getAsInt();
                int col = settings.get("col").getAsInt();
                String pieceTypeName = settings.get("pieceType").getAsString();
                Piece.Type pieceType = Piece.Type.valueOf(pieceTypeName);
                boardPanel.placePieceInSetupMode(row, col, pieceType);
                return;
            } else if ("BOARD_SETUP_REMOVE".equals(cmd)) {
                int row = settings.get("row").getAsInt();
                int col = settings.get("col").getAsInt();
                boardPanel.removePieceInSetupMode(row, col);
                return;
            }
        }

        // 转发给 InfoSidePanel 处理持方同步
        infoSidePanel.onSettingsReceived(settings);
        // 处理游戏规则设置同步
        if (!netController.isHost()) {
            engineForSync.applySettingsSnapshot(settings);
            gameController.updateStatus();
        }
    }

    private void onSyncGameStateReceivedImpl(JsonObject state) {
        try {
            System.out.println("[SYNC] 收到对局状态，开始恢复...");
            engineForSync.loadSyncState(state);
            // 强制刷新所有 UI 组件
            boardPanel.clearSelection();
            boardPanel.clearForceMoveIndicator();
            boardPanel.clearRemotePieceHighlight();
            boardPanel.repaint();
            gameController.updateStatus();
            if (ruleSettingsPanel != null) ruleSettingsPanel.refreshUI();
            moveHistoryPanel.refreshHistory();
            moveHistoryPanel.showNavigation();
            JOptionPane.showMessageDialog(parentFrame,
                    "已成功加入对局并同步当前状态！", "同步成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("[SYNC][ERROR] 对局同步失败: " + ex.getMessage());
            for (StackTraceElement ste : ex.getStackTrace()) {
                System.err.println("    at " + ste);
            }
            JOptionPane.showMessageDialog(parentFrame,
                    "对局同步失败: " + ex.getMessage() + "\n请查看控制台获取详细错误堆栈。",
                    "同步错误", JOptionPane.ERROR_MESSAGE);
            // 将异常堆栈复制到剪贴板
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            StringSelection selection = new StringSelection(sw.toString());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            logError(ex);
        }
    }

    // ── 设置同步（debounced） ──

    private void initSettingsSendTimer() {
        sendSettingsTimer = new javax.swing.Timer(200, e -> doSendSettingsNow());
        sendSettingsTimer.setRepeats(false);
    }

    private void sendSettingsSnapshotToClient() {
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
                            return;
                        }
                        toSend = pendingDiffs;
                        pendingDiffs = new JsonObject();
                    }
                    netController.getSession().sendSettings(toSend);
                }
            }
        } catch (Exception ex) {
            System.out.println("[DEBUG] 发送设置快照失败: " + ex);
            logError(ex);
        }
    }

    /** 停止设置发送定时器（在断开连接时调用） */
    public void stopSettingsTimer() {
        if (sendSettingsTimer != null) {
            sendSettingsTimer.stop();
            sendSettingsTimer = null;
        }
    }

    /** 关闭时清理 */
    public void shutdown() {
        stopSettingsTimer();
        GameRulesConfig rulesConfig = engineForSync.getRulesConfig();
        if (rulesConfig != null) {
            try { rulesConfig.removeRuleChangeListener(diffsCollector); } catch (Throwable ignored) {}
        }
    }

    public boolean isNetSessionActive() {
        return netController.isActive();
    }

    private static void logError(Throwable t) {
        if (t == null) return;
        System.err.println("[NetGameCoordinator] " + t);
        try { t.printStackTrace(System.err); } catch (Throwable ignored) {}
    }
}
