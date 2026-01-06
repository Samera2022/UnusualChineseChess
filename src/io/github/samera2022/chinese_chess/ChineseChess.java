package io.github.samera2022.chinese_chess;

import io.github.samera2022.chinese_chess.ui.ChineseChessFrame;

import javax.swing.*;

public class ChineseChess {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChineseChessFrame().setVisible(true));
    }
}
