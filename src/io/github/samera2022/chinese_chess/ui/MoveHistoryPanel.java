package io.github.samera2022.chinese_chess.ui;

import io.github.samera2022.chinese_chess.engine.GameEngine;
import io.github.samera2022.chinese_chess.model.Move;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

/**
 * 着法记录面板 - 显示游戏的着法历史
 */
public class MoveHistoryPanel extends JPanel implements GameEngine.GameStateListener {
    private GameEngine gameEngine;
    private JTextArea moveTextArea;
    private JScrollPane scrollPane;

    // 导航控制
    private JPanel navigationPanel;
    private JButton prevButton;
    private JButton nextButton;
    private JLabel stepLabel;
    private int currentStep = -1; // -1表示显示当前实际状态
    private boolean isInReplayMode = false;
    private StepChangeListener stepChangeListener;

    /**
     * 步数变化监听器接口
     */
    public interface StepChangeListener {
        void onStepChanged(int step);
    }

    public MoveHistoryPanel(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        gameEngine.addGameStateListener(this);

        setLayout(new BorderLayout());
        setBorder(new TitledBorder("着法记录"));

        // 创建导航面板
        navigationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
        prevButton = new JButton("上一步");
        nextButton = new JButton("下一步");
        stepLabel = new JLabel("第0步");

        prevButton.setFont(new Font("SimHei", Font.PLAIN, 12));
        nextButton.setFont(new Font("SimHei", Font.PLAIN, 12));
        stepLabel.setFont(new Font("SimHei", Font.BOLD, 12));

        prevButton.addActionListener(e -> previousStep());
        nextButton.addActionListener(e -> nextStep());

        navigationPanel.add(prevButton);
        navigationPanel.add(stepLabel);
        navigationPanel.add(nextButton);
        navigationPanel.setVisible(false); // 初始隐藏

        add(navigationPanel, BorderLayout.NORTH);

        // 创建文本区
        moveTextArea = new JTextArea(10, 18);
        moveTextArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        moveTextArea.setEditable(false);
        moveTextArea.setLineWrap(true);
        moveTextArea.setWrapStyleWord(true);

        // 创建滚动窗格
        scrollPane = new JScrollPane(moveTextArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);

        updateMoveHistory();
    }

    /**
     * 显示导航面板（用于导入残局后）
     */
    public void showNavigation() {
        List<Move> moves = gameEngine.getMoveHistory();
        if (moves.size() > 0) {
            isInReplayMode = true;
            currentStep = moves.size(); // 初始显示最后一步
            gameEngine.setReplayMode(true, currentStep);
            navigationPanel.setVisible(true);
            updateNavigationButtons();
        }
    }

    /**
     * 隐藏导航面板（用于重新开始后）
     */
    public void hideNavigation() {
        isInReplayMode = false;
        currentStep = -1;
        gameEngine.setReplayMode(false, -1);
        navigationPanel.setVisible(false);
    }

    /**
     * 设置步数变化监听器
     */
    public void setStepChangeListener(StepChangeListener listener) {
        this.stepChangeListener = listener;
    }

    /**
     * 供外部调用：立即刷新着法记录显示
     */
    public void refreshHistory() {
        updateMoveHistory();
    }

    /**
     * 上一步
     */
    private void previousStep() {
        if (currentStep > 0) {
            currentStep--;
            gameEngine.setReplayMode(true, currentStep);
            updateBoardToStep(currentStep);
            updateNavigationButtons();
        }
    }

    /**
     * 下一步
     */
    private void nextStep() {
        List<Move> moves = gameEngine.getMoveHistory();
        if (currentStep < moves.size()) {
            currentStep++;
            gameEngine.setReplayMode(true, currentStep);
            updateBoardToStep(currentStep);
            updateNavigationButtons();
        }
    }

    /**
     * 更新棋盘到指定步数
     */
    private void updateBoardToStep(int step) {
        stepLabel.setText("第" + step + "步");

        // 通知监听器步数已变化
        if (stepChangeListener != null) {
            stepChangeListener.onStepChanged(step);
        }
    }

    /**
     * 更新导航按钮状态
     */
    private void updateNavigationButtons() {
        List<Move> moves = gameEngine.getMoveHistory();
        prevButton.setEnabled(currentStep > 0);
        nextButton.setEnabled(currentStep < moves.size());
        stepLabel.setText("第" + currentStep + "步");
    }

    /**
     * 更新着法记录显示
     */
    private void updateMoveHistory() {
        StringBuilder sb = new StringBuilder();
        List<Move> moves = gameEngine.getMoveHistory();

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            // 单步编号：每一步（半回合）都独立编号
            sb.append(i + 1).append(". ");
            sb.append(move.toString()).append("\n");
        }

        moveTextArea.setText(sb.toString());

        // 滚动到底部
        moveTextArea.setCaretPosition(moveTextArea.getDocument().getLength());
    }

    @Override
    public void onGameStateChanged(GameEngine.GameState newState) {
        updateMoveHistory();
    }

    @Override
    public void onMoveExecuted(Move move) {
        // 如果在回放模式中走子，退出回放模式并更新显示
        if (isInReplayMode) {
            isInReplayMode = false;
            currentStep = -1;
            // 检查是否还需要显示导航面板
            List<Move> moves = gameEngine.getMoveHistory();
            if (moves.size() == 0) {
                navigationPanel.setVisible(false);
            } else {
                // 更新到最新状态
                currentStep = moves.size();
                updateNavigationButtons();
            }
        }
        updateMoveHistory();
    }
}
