package io.github.samera2022.chinese_chess.ui;

import io.github.samera2022.chinese_chess.engine.GameEngine;
import io.github.samera2022.chinese_chess.model.Move;

import javax.swing.*;
import java.awt.*;

/**
 * 主窗口 - 中国象棋游戏的GUI
 */
public class ChineseChessFrame extends JFrame implements GameEngine.GameStateListener {
    private GameEngine gameEngine;
    private BoardPanel boardPanel;
    private MoveHistoryPanel moveHistoryPanel;
    // 新增：右侧容器与右侧列表
    private JPanel rightPanel;
    private JPanel extraListPanel;
    private DefaultListModel<String> extraListModel;
    private JList<String> extraList;
    private JPanel controlPanel;
    private JLabel statusLabel;
    private JButton undoButton;
    private JButton restartButton;
    // 新增：切换右侧列表显示的按钮
    private JToggleButton toggleExtraListButton;

    public ChineseChessFrame() {
        setTitle("中国象棋 - Offline Mode");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // 初始化游戏引擎
        gameEngine = new GameEngine();
        gameEngine.addGameStateListener(this);

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());

        // 创建左侧面板（棋盘）
        JPanel leftPanel = new JPanel(new BorderLayout());
        boardPanel = new BoardPanel(gameEngine);
        leftPanel.add(boardPanel, BorderLayout.CENTER);
        leftPanel.add(createControlPanel(), BorderLayout.SOUTH);

        // 创建右侧面板（着法记录 + 新增列表）
        moveHistoryPanel = new MoveHistoryPanel(gameEngine);
        rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(moveHistoryPanel, BorderLayout.CENTER);
        createExtraListPanel();
        rightPanel.add(extraListPanel, BorderLayout.EAST);

        // 将左右两个面板添加到主面板
        mainPanel.add(leftPanel, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);

        updateStatus();
    }

    // 新增：构建右侧列表面板
    private void createExtraListPanel() {
        extraListModel = new DefaultListModel<>();
        // 可按需填充默认项目；此处保留为空
        extraList = new JList<>(extraListModel);
        extraList.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        JScrollPane sp = new JScrollPane(extraList);
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        extraListPanel = new JPanel(new BorderLayout());
        extraListPanel.setBorder(BorderFactory.createTitledBorder("附加列表"));
        extraListPanel.add(sp, BorderLayout.CENTER);
        extraListPanel.setPreferredSize(new Dimension(160, 0)); // 固定右侧宽度
        extraListPanel.setVisible(false); // 默认隐藏，由按钮控制
    }

    /**
     * 创建控制面板
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(240, 240, 240));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 状态标签
        statusLabel = new JLabel();
        statusLabel.setFont(new Font("SimHei", Font.BOLD, 16));
        panel.add(statusLabel, BorderLayout.WEST);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(new Color(240, 240, 240));

        // 撤销按钮
        undoButton = new JButton("撤销");
        undoButton.setFont(new Font("SimHei", Font.PLAIN, 14));
        undoButton.addActionListener(e -> {
            if (gameEngine.undoLastMove()) {
                boardPanel.repaint();
                updateStatus();
            }
        });
        buttonPanel.add(undoButton);

        // 重新开始按钮
        restartButton = new JButton("重新开始");
        restartButton.setFont(new Font("SimHei", Font.PLAIN, 14));
        restartButton.addActionListener(e -> {
            gameEngine.restart();
            boardPanel.repaint();
            updateStatus();
        });
        buttonPanel.add(restartButton);

        // 切换“右侧新增列表”按钮
        toggleExtraListButton = new JToggleButton("显示右侧列表");
        toggleExtraListButton.setFont(new Font("SimHei", Font.PLAIN, 14));
        toggleExtraListButton.addActionListener(e -> {
            boolean show = toggleExtraListButton.isSelected();
            extraListPanel.setVisible(show);
            toggleExtraListButton.setText(show ? "隐藏右侧列表" : "显示右侧列表");
            // 重新布局并调整窗口大小，确保非可调整窗口也能适配宽度变化
            ChineseChessFrame.this.pack();
            ChineseChessFrame.this.revalidate();
            ChineseChessFrame.this.repaint();
        });
        buttonPanel.add(toggleExtraListButton);

        panel.add(buttonPanel, BorderLayout.EAST);

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
                status = "红方胜利！";
                break;
            case BLACK_CHECKMATE:
                status = "黑方胜利！";
                break;
            case DRAW:
                status = "游戏平局";
                break;
            default:
                status = "游戏进行中";
        }

        statusLabel.setText(status);
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


}

