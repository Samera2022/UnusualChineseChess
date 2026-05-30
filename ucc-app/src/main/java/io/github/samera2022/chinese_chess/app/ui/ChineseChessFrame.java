package io.github.samera2022.chinese_chess.app.ui;

import io.github.samera2022.chinese_chess.common.UpdateInfo;
import io.github.samera2022.chinese_chess.core.engine.GameEngine;
import io.github.samera2022.chinese_chess.api.io.GameStateExporter;
import io.github.samera2022.chinese_chess.api.io.GameStateImporter;
import io.github.samera2022.chinese_chess.api.net.NetModeController;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.core.rules.RulesConfigProvider;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Objects;

/**
 * 主窗口 - 中国象棋游戏的 GUI。
 * 职责：JFrame 创建、布局组装、窗口生命周期管理、菜单栏工具栏（如需要）、资源清理。
 * 走子协调、网络对局、强制走子逻辑委托给 GameController、NetGameCoordinator、ForceMoveHandler。
 */
public class ChineseChessFrame extends JFrame {

    // 引擎与核心组件
    public final GameEngine gameEngine;
    public final GameRulesConfig rulesConfig;
    public final NetModeController netController = new NetModeController();

    // UI 面板
    public final BoardPanel boardPanel;
    public final MoveHistoryPanel moveHistoryPanel;
    public final InfoSidePanel infoSidePanel;
    public final RuleSettingsPanel ruleSettingsPanel;

    // 控制器
    public final GameController gameController;
    public final NetGameCoordinator netGameCoordinator;
    public final ForceMoveHandler forceMoveHandler;

    // 控制组件
    public JPanel rightPanel;
    public JToggleButton togglePanelButton;
    public JButton undoButton;
    public JButton restartButton;
    private JLabel statusLabel;

    public ChineseChessFrame() {
        setTitle("不同寻常的中国象棋 - Unusual Chinese Chess");
        try {
            setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/UnusualChineseChess.png"))).getImage());
        } catch (Exception e) {
            System.err.println("Warning: Could not load application icon");
        }
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // 初始化游戏引擎
        gameEngine = new GameEngine();
        rulesConfig = RulesConfigProvider.get();

        // 创建 UI 面板
        boardPanel = new BoardPanel(gameEngine,
                () -> netController.isActive(),
                json -> netController.getSession().sendSettings(json),
                null);
        moveHistoryPanel = new MoveHistoryPanel(gameEngine);
        ruleSettingsPanel = new RuleSettingsPanel();
        ruleSettingsPanel.bindConfig(rulesConfig);

        // 先创建控制面板（含状态标签和按钮），以便后续组件获取真实引用
        statusLabel = new JLabel();
        statusLabel.setFont(new Font("SimHei", Font.BOLD, 16));
        undoButton = new JButton("撤销");
        undoButton.setFont(new Font("SimHei", Font.PLAIN, 14));
        restartButton = new JButton("重新开始");
        restartButton.setFont(new Font("SimHei", Font.PLAIN, 14));

        // 创建 InfoSidePanel（需要真实的 undoButton 引用）
        infoSidePanel = new InfoSidePanel(
                netController,
                boardPanel,
                gameEngine,
                undoButton,
                () -> {
                    ruleSettingsPanel.setVisible(!ruleSettingsPanel.isVisible());
                    pack();
                    revalidate();
                    repaint();
                },
                () -> ruleSettingsPanel.isVisible(),
                this::exportGameState,
                this::importGameState
        );
        infoSidePanel.setVisible(true);

        // 创建控制器
        forceMoveHandler = new ForceMoveHandler(gameEngine, netController, boardPanel, this);
        gameController = new GameController(gameEngine, netController, boardPanel, moveHistoryPanel,
                ruleSettingsPanel, infoSidePanel, forceMoveHandler);
        netGameCoordinator = new NetGameCoordinator(gameEngine, gameEngine, netController, boardPanel, moveHistoryPanel,
                ruleSettingsPanel, infoSidePanel, forceMoveHandler, gameController, undoButton, this);

        // 设置状态标签和按钮引用到控制器
        gameController.setUndoButton(undoButton);
        gameController.setStatusLabel(statusLabel);

        // 安装监听器和绑定
        forceMoveHandler.install();
        gameController.install();
        netGameCoordinator.install();

        // 构建 UI 布局（使用已创建的按钮和标签）
        buildLayout();

        // 安装关闭和 JVM 清理
        installShutdownHooks();

        // 设置本地版本
        infoSidePanel.setLocalVersion(UpdateInfo.getLatestVersion());

        // 初始状态刷新
        gameController.updateStatus();

        togglePanelButton.setSelected(true);
        togglePanelButton.setText("隐藏面板");
    }

    private void buildLayout() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 棋盘 + 控制栏
        JPanel center = new JPanel(new BorderLayout());
        center.add(boardPanel, BorderLayout.CENTER);
        center.add(createControlPanel(), BorderLayout.SOUTH);

        // 右侧：着法记录
        rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(moveHistoryPanel, BorderLayout.CENTER);

        // 左侧停靠：设置面板 + 信息面板
        ruleSettingsPanel.setVisible(true);

        JPanel westDock = new JPanel(new BorderLayout());
        westDock.add(ruleSettingsPanel, BorderLayout.WEST);
        westDock.add(infoSidePanel, BorderLayout.CENTER);

        // 组装
        mainPanel.add(westDock, BorderLayout.WEST);
        mainPanel.add(center, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(240, 240, 240));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 状态标签（左侧）- 使用已在构造函数中创建的实例
        panel.add(statusLabel, BorderLayout.WEST);

        // 按钮面板
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonBar.setBackground(new Color(240, 240, 240));

        // 撤销按钮 - 已在构造函数中创建，此处添加监听
        undoButton.addActionListener(e -> gameController.undo());
        buttonBar.add(undoButton);

        // 重新开始按钮 - 已在构造函数中创建，此处添加监听
        restartButton.addActionListener(e -> gameController.restart());
        buttonBar.add(restartButton);

        // 单按钮切换"面板"
        togglePanelButton = new JToggleButton("显示面板");
        togglePanelButton.setFont(new Font("SimHei", Font.PLAIN, 14));
        togglePanelButton.addActionListener(e -> {
            boolean show = togglePanelButton.isSelected();
            infoSidePanel.setVisible(show);
            togglePanelButton.setText(show ? "隐藏面板" : "显示面板");
            pack();
            revalidate();
            repaint();
        });
        buttonBar.add(togglePanelButton);

        panel.add(buttonBar, BorderLayout.EAST);
        return panel;
    }

    private void installShutdownHooks() {
        // 窗口关闭时清理
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        // JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { shutdown(); } catch (Throwable ignored) {}
        }));
    }

    private void shutdown() {
        try {
            if (gameEngine != null) gameEngine.shutdown();
        } catch (Throwable ignored) {}
        try { gameController.shutdown(); } catch (Throwable ignored) {}
        try { netGameCoordinator.shutdown(); } catch (Throwable ignored) {}
        RulesConfigProvider.shutdown();
    }

    /** 更新状态标签 - 委托给 GameController */
    public void updateStatus() {
        if (gameController != null) {
            gameController.updateStatus();
        }
    }

    // ── 导出/导入游戏状态 ──

    private void exportGameState() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导出残局");
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON文件 (*.json)", "json"));
        fileChooser.setSelectedFile(new File("endgame.json"));

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
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

    private void importGameState() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("导入残局");
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON文件 (*.json)", "json"));

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                GameStateImporter.importGameState(gameEngine, file.getAbsolutePath());
                boardPanel.repaint();
                updateStatus();
                if (ruleSettingsPanel != null) {
                    ruleSettingsPanel.refreshUI();
                }
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

    private static void logError(Throwable t) {
        if (t == null) return;
        System.err.println("[ChineseChessFrame] " + t);
        try { t.printStackTrace(System.err); } catch (Throwable ignored) {}
    }

    /**
     * 程序入口点
     */
    public static void main(String[] args) {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            ChineseChessFrame frame = new ChineseChessFrame();
            frame.setVisible(true);
        });
    }
}
