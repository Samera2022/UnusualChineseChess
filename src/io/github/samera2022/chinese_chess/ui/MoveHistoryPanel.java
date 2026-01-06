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

    public MoveHistoryPanel(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        gameEngine.addGameStateListener(this);

        setLayout(new BorderLayout());
        setBorder(new TitledBorder("着法记录"));

        // 创建文本区
        moveTextArea = new JTextArea(10, 20);
        moveTextArea.setFont(new Font("Courier New", Font.PLAIN, 12));
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
     * 更新着法记录显示
     */
    private void updateMoveHistory() {
        StringBuilder sb = new StringBuilder();
        List<Move> moves = gameEngine.getMoveHistory();

        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            if (i % 2 == 0) {
                // 红方着法
                sb.append((i / 2 + 1)).append(". ");
            }

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
        updateMoveHistory();
    }
}

