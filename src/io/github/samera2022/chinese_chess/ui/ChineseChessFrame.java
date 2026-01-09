package io.github.samera2022.chinese_chess.ui;

import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.engine.GameEngine;
import io.github.samera2022.chinese_chess.io.GameStateExporter;
import io.github.samera2022.chinese_chess.io.GameStateImporter;
import io.github.samera2022.chinese_chess.model.Move;
import io.github.samera2022.chinese_chess.net.NetModeController;
import io.github.samera2022.chinese_chess.net.NetworkSession;
import io.github.samera2022.chinese_chess.rules.RuleConstants;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.Objects;

/**
 * 主窗口 - 中国象棋游戏的GUI
 */
public class ChineseChessFrame extends JFrame implements GameEngine.GameStateListener {
    private GameEngine gameEngine;
    private BoardPanel boardPanel;
    private MoveHistoryPanel moveHistoryPanel;
    private JPanel rightPanel;
    private JToggleButton togglePanelButton;
    private JButton undoButton;
    private JButton restartButton;

    // 新的右侧网络面板
    private NetModeController netController = new NetModeController();
    private NetworkSidePanel networkSidePanel;
    private RuleSettingsPanel ruleSettingsPanel;
    private boolean ruleSettingsLocked = false;
    private JPanel westDock;

    // 状态标签（左侧）
    private JLabel statusLabel;

    public ChineseChessFrame() {
        setTitle("不同寻常的中国象棋 - Unusual Chinese Chess");
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/UnusualChineseChess.png"))).getImage());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // 初始化游戏引擎
        gameEngine = new GameEngine();
        gameEngine.addGameStateListener(this);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 棋盘 + 控制栏
        JPanel center = new JPanel(new BorderLayout());
        boardPanel = new BoardPanel(gameEngine);
        // 设置本地走子监听：若在联机中，发送到对端
        boardPanel.setLocalMoveListener((fr, fc, tr, tc) -> {
            if (netController.isActive()) {
                netController.getSession().sendMove(fr, fc, tr, tc);
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
                if (ruleSettingsLocked) return;
                gameEngine.setAllowUndo(allowUndo);
                undoButton.setEnabled(allowUndo && !netController.isActive());
            }
            @Override public boolean isAllowUndo() { return gameEngine.isAllowUndo(); }
            @Override public void setAllowFlyingGeneral(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.ALLOW_FLYING_GENERAL, allow); boardPanel.repaint(); } }
            @Override public void setPawnCanRetreat(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.PAWN_CAN_RETREAT, allow); boardPanel.repaint(); } }
            @Override public void setNoRiverLimit(boolean noLimit) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.NO_RIVER_LIMIT, noLimit); boardPanel.repaint(); } }
            @Override public void setAdvisorCanLeave(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.ADVISOR_CAN_LEAVE, allow); boardPanel.repaint(); } }
            @Override public void setInternationalKing(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.INTERNATIONAL_KING, allow); boardPanel.repaint(); } }
            @Override public void setPawnPromotion(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.PAWN_PROMOTION, allow); boardPanel.repaint(); } }
            @Override public void setAllowOwnBaseLine(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.ALLOW_OWN_BASE_LINE, allow); boardPanel.repaint(); } }
            @Override public void setAllowInsideRetreat(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.ALLOW_INSIDE_RETREAT, allow); boardPanel.repaint(); } }
            @Override public void setInternationalAdvisor(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.INTERNATIONAL_ADVISOR, allow); boardPanel.repaint(); } }
            @Override public void setAllowElephantCrossRiver(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER, allow); boardPanel.repaint(); } }
            @Override public void setAllowAdvisorCrossRiver(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER, allow); boardPanel.repaint(); } }
            @Override public void setAllowKingCrossRiver(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.ALLOW_KING_CROSS_RIVER, allow); boardPanel.repaint(); } }
            @Override public void setLeftRightConnected(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.LEFT_RIGHT_CONNECTED, allow); boardPanel.repaint(); } }
            @Override public void setLeftRightConnectedHorse(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE, allow); boardPanel.repaint(); } }
            @Override public void setLeftRightConnectedElephant(boolean allow) { if (!ruleSettingsLocked) { gameEngine.getRulesConfig().set(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT, allow); boardPanel.repaint(); } }
            @Override public void setNoRiverLimitPawn(boolean allow) { if (!ruleSettingsLocked) gameEngine.setNoRiverLimitPawn(allow); boardPanel.repaint(); }
            @Override public boolean isAllowFlyingGeneral() { return gameEngine.isSpecialRuleEnabled(RuleConstants.ALLOW_FLYING_GENERAL); }
            @Override public boolean isPawnCanRetreat() { return gameEngine.isSpecialRuleEnabled(RuleConstants.PAWN_CAN_RETREAT); }
            @Override public boolean isNoRiverLimit() { return gameEngine.isSpecialRuleEnabled(RuleConstants.NO_RIVER_LIMIT); }
            @Override public boolean isAdvisorCanLeave() { return gameEngine.isSpecialRuleEnabled(RuleConstants.ADVISOR_CAN_LEAVE); }
            @Override public boolean isInternationalKing() { return gameEngine.isSpecialRuleEnabled(RuleConstants.INTERNATIONAL_KING); }
            @Override public boolean isPawnPromotion() { return gameEngine.isSpecialRuleEnabled(RuleConstants.PAWN_PROMOTION); }
            @Override public boolean isAllowOwnBaseLine() { return gameEngine.isSpecialRuleEnabled(RuleConstants.ALLOW_OWN_BASE_LINE); }
            @Override public boolean isAllowInsideRetreat() { return gameEngine.isSpecialRuleEnabled(RuleConstants.ALLOW_INSIDE_RETREAT); }
            @Override public boolean isInternationalAdvisor() { return gameEngine.isSpecialRuleEnabled(RuleConstants.INTERNATIONAL_ADVISOR); }
            @Override public boolean isAllowElephantCrossRiver() { return gameEngine.isSpecialRuleEnabled(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER); }
            @Override public boolean isAllowAdvisorCrossRiver() { return gameEngine.isSpecialRuleEnabled(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER); }
            @Override public boolean isAllowKingCrossRiver() { return gameEngine.isSpecialRuleEnabled(RuleConstants.ALLOW_KING_CROSS_RIVER); }
            @Override public boolean isLeftRightConnected() { return gameEngine.isSpecialRuleEnabled(RuleConstants.LEFT_RIGHT_CONNECTED); }
            @Override public boolean isLeftRightConnectedHorse() { return gameEngine.isSpecialRuleEnabled(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE); }
            @Override public boolean isLeftRightConnectedElephant() { return gameEngine.isSpecialRuleEnabled(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT); }
            @Override public boolean isNoRiverLimitPawn() { return gameEngine.isNoRiverLimitPawn(); }
            @Override public void setUnblockPiece(boolean allow) { if (!ruleSettingsLocked) gameEngine.setUnblockPiece(allow); boardPanel.repaint(); }
            @Override public void setUnblockHorseLeg(boolean allow) { if (!ruleSettingsLocked) gameEngine.setUnblockHorseLeg(allow); boardPanel.repaint(); }
            @Override public void setUnblockElephantEye(boolean allow) { if (!ruleSettingsLocked) gameEngine.setUnblockElephantEye(allow); boardPanel.repaint(); }
            @Override public boolean isUnblockPiece() { return gameEngine.isUnblockPiece(); }
            @Override public boolean isUnblockHorseLeg() { return gameEngine.isUnblockHorseLeg(); }
            @Override public boolean isUnblockElephantEye() { return gameEngine.isUnblockElephantEye(); }
            @Override public void setAllowCaptureOwnPiece(boolean allow) { if (!ruleSettingsLocked) gameEngine.setAllowCaptureOwnPiece(allow); boardPanel.repaint(); }
            @Override public boolean isAllowCaptureOwnPiece() { return gameEngine.isAllowCaptureOwnPiece(); }
            @Override public void setAllowPieceStacking(boolean allow) { if (!ruleSettingsLocked) gameEngine.setAllowPieceStacking(allow); boardPanel.repaint(); }
            @Override public boolean isAllowPieceStacking() { return gameEngine.isAllowPieceStacking(); }
            @Override public void setMaxStackingCount(int count) { if (!ruleSettingsLocked) gameEngine.setMaxStackingCount(count); boardPanel.repaint(); }
            @Override public int getMaxStackingCount() { return gameEngine.getMaxStackingCount(); }
        });

        // 面板，带"玩法设置"按钮，点击后切换左侧设置组件
        networkSidePanel = new NetworkSidePanel(
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
                () -> exportGameState(),
                () -> importGameState()
        );
        networkSidePanel.setVisible(true);
        if (togglePanelButton != null) {
            togglePanelButton.setSelected(true);
            togglePanelButton.setText("隐藏面板");
        }

        westDock = new JPanel(new BorderLayout());
        westDock.add(ruleSettingsPanel, BorderLayout.WEST);
        westDock.add(networkSidePanel, BorderLayout.CENTER);

        // 组装
        mainPanel.add(westDock, BorderLayout.WEST);
        mainPanel.add(center, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);

        // 调整 NetworkSidePanel 内部监听以包含设置同步/锁定
        netController.getSession().setListener(new NetworkSession.Listener() {
            @Override public void onPeerMove(int fromRow, int fromCol, int toRow, int toCol) { SwingUtilities.invokeLater(() -> { gameEngine.makeMove(fromRow, fromCol, toRow, toCol); boardPanel.repaint(); }); }
            @Override public void onPeerRestart() { SwingUtilities.invokeLater(() -> { gameEngine.restart(); boardPanel.repaint(); }); }
            @Override public void onDisconnected(String reason) {
                SwingUtilities.invokeLater(() -> {
                    ruleSettingsLocked = false;
                    setRuleSettingsEnabled(true);
                    undoButton.setEnabled(true);
                    boardPanel.setLocalControlsRed(null);
                    networkSidePanel.onDisconnected(reason);
                });
            }
            @Override public void onConnected(String peerInfo) {
                SwingUtilities.invokeLater(() -> {
                    networkSidePanel.onConnected(peerInfo);
                    if (netController.isHost()) {
                        JsonObject snap = gameEngine.getSettingsSnapshot();
                        netController.getSession().sendSettings(snap);
                    }
                    ruleSettingsLocked = !netController.isHost();
                    setRuleSettingsEnabled(netController.isHost());
                    ruleSettingsPanel.refreshFromBinder();
                });
            }
            @Override public void onPong(long sentMillis, long rttMillis) { SwingUtilities.invokeLater(() -> networkSidePanel.onPong(sentMillis, rttMillis)); }
            @Override public void onSettingsReceived(JsonObject settings) {
                SwingUtilities.invokeLater(() -> {
                    if (!netController.isHost() && settings != null) {
                        gameEngine.applySettingsSnapshot(settings);
                        undoButton.setEnabled(gameEngine.isAllowUndo() && !netController.isActive());
                        ruleSettingsPanel.refreshFromBinder();
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
            if (gameEngine.undoLastMove()) {
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
            networkSidePanel.setVisible(show);
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
    private void updateStatus() {
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
    }

    @Override
    public void onGameStateChanged(GameEngine.GameState newState) {
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
                    "导出失败: " + ex.getMessage(),
                    "导出错误",
                    JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
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
                ex.printStackTrace();
            }
        }
    }
}
