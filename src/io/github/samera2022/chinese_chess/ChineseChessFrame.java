package io.github.samera2022.chinese_chess;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 主窗口 - 中国象棋游戏的GUI
 */
public class ChineseChessFrame extends JFrame implements GameEngine.GameStateListener {
    private GameEngine gameEngine;
    private BoardPanel boardPanel;
    private MoveHistoryPanel moveHistoryPanel;
    private JPanel controlPanel;
    private JLabel statusLabel;
    private JButton undoButton;
    private JButton restartButton;

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

        // 创建右侧面板（着法记录）
        moveHistoryPanel = new MoveHistoryPanel(gameEngine);

        // 将左右两个面板添加到主面板
        mainPanel.add(leftPanel, BorderLayout.CENTER);
        mainPanel.add(moveHistoryPanel, BorderLayout.EAST);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);

        updateStatus();
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChineseChessFrame frame = new ChineseChessFrame();
            frame.setVisible(true);
        });
    }
}

