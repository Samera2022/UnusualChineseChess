package io.github.samera2022.chinese_chess.ui;

import io.github.samera2022.chinese_chess.GameConfig;
import io.github.samera2022.chinese_chess.engine.Board;
import io.github.samera2022.chinese_chess.engine.GameEngine;
import io.github.samera2022.chinese_chess.model.Piece;
import io.github.samera2022.chinese_chess.rules.MoveValidator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * 棋盘UI - 显示棋盘和棋子
 */
public class BoardPanel extends JPanel {
    private GameEngine gameEngine;
    private int cellSize = GameConfig.CELL_SIZE;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private java.util.List<Point> validMoves = new java.util.ArrayList<>();

    // 棋盘偏移量（用于居中显示）
    private int offsetX = 0;
    private int offsetY = 0;

    // 颜色定义
    private static final Color BOARD_COLOR = new Color(230, 180, 80);
    private static final Color GRID_COLOR = new Color(0, 0, 0);
    private static final Color SELECTED_COLOR = new Color(255, 255, 0);
    private static final Color VALID_MOVE_COLOR = new Color(0, 255, 0);
    private static final Color RED_PIECE_COLOR = new Color(200, 0, 0);
    private static final Color BLACK_PIECE_COLOR = new Color(50, 50, 50);

    public BoardPanel(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        setBackground(BOARD_COLOR);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMouseClick(e);
            }
        });
    }

    /**
     * 处理鼠标点击 - 点击交点附近时选中棋子
     */
    private void handleMouseClick(MouseEvent e) {
        // 将鼠标点击位置转换回棋盘坐标（考虑偏移量）
        int col = Math.round((float) (e.getX() - offsetX) / cellSize);
        int row = Math.round((float) (e.getY() - offsetY) / cellSize);

        Board board = gameEngine.getBoard();
        if (!board.isValid(row, col)) {
            return;
        }

        // 如果点击的是已选择的格子，取消选择
        if (row == selectedRow && col == selectedCol) {
            selectedRow = -1;
            selectedCol = -1;
            validMoves.clear();
            repaint();
            return;
        }

        // 如果没有选择棋子，选择点击的棋子
        if (selectedRow == -1) {
            Piece piece = board.getPiece(row, col);
            if (piece != null && piece.isRed() == gameEngine.isRedTurn()) {
                selectedRow = row;
                selectedCol = col;
                calculateValidMoves();
            }
            repaint();
            return;
        }

        // 已有选择的棋子，尝试移动
        if (gameEngine.makeMove(selectedRow, selectedCol, row, col)) {
            selectedRow = -1;
            selectedCol = -1;
            validMoves.clear();
        } else {
            // 移动失败，重新选择
            selectedRow = row;
            selectedCol = col;
            Piece piece = board.getPiece(row, col);
            if (piece == null || piece.isRed() != gameEngine.isRedTurn()) {
                selectedRow = -1;
                selectedCol = -1;
                validMoves.clear();
            } else {
                calculateValidMoves();
            }
        }

        repaint();
    }

    /**
     * 计算所有可能的移动
     */
    private void calculateValidMoves() {
        validMoves.clear();
        Board board = gameEngine.getBoard();
        MoveValidator validator = new MoveValidator(board);

        for (int row = 0; row < board.getRows(); row++) {
            for (int col = 0; col < board.getCols(); col++) {
                if (validator.isValidMove(selectedRow, selectedCol, row, col)) {
                    validMoves.add(new Point(row, col));
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Board board = gameEngine.getBoard();

        // 计算棋盘的居中偏移
        int pieceRadius = cellSize / 2 - 2;
        int boardWidth = (board.getCols() - 1) * cellSize;
        int boardHeight = (board.getRows() - 1) * cellSize;
        // 总大小 = 棋盘 + 左边棋子半径 + 右边棋子半径
        int totalWidth = boardWidth + 2 * pieceRadius;
        int totalHeight = boardHeight + 2 * pieceRadius;

        // 计算偏移量，使棋盘在面板中居中
        offsetX = pieceRadius + (getWidth() - totalWidth) / 2;
        offsetY = pieceRadius + (getHeight() - totalHeight) / 2;

        // 保证不会出现负数偏移
        offsetX = Math.max(pieceRadius, offsetX);
        offsetY = Math.max(pieceRadius, offsetY);

        // 应用偏移变换
        g2d.translate(offsetX, offsetY);

        // 绘制棋盘
        drawBoard(g2d, board);

        // 绘制棋子
        drawPieces(g2d, board);
    }

    /**
     * 绘制棋盘
     */
    private void drawBoard(Graphics2D g2d, Board board) {
        // 绘制边框
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));
        // 棋盘边框：从 (0,0) 到最后一个交点
        int boardWidth = (board.getCols() - 1) * cellSize;
        int boardHeight = (board.getRows() - 1) * cellSize;
        g2d.drawRect(0, 0, boardWidth, boardHeight);

        // 绘制网格线
        g2d.setColor(GRID_COLOR);
        g2d.setStroke(new BasicStroke(1));

        // 竖线：从第 0 列到第 cols-1 列（共 cols 条线）
        for (int col = 0; col < board.getCols(); col++) {
            g2d.drawLine(col * cellSize, 0, col * cellSize, boardHeight);
        }

        // 横线：从第 0 行到第 rows-1 行（共 rows 条线）
        for (int row = 0; row < board.getRows(); row++) {
            g2d.drawLine(0, row * cellSize, boardWidth, row * cellSize);
        }

        // 绘制"河"
        g2d.setColor(new Color(100, 100, 100));
        g2d.setFont(new Font("SimHei", Font.PLAIN, 12));
        int riverRow = 4;
        int boardWidth_actual = (board.getCols() - 1) * cellSize;
        g2d.drawString("河", boardWidth_actual / 2 - 10, riverRow * cellSize + cellSize / 2);

        // 绘制"宫"（King's palace）
        drawPalace(g2d);

        // 绘制所选棋子的高亮 - 显示在交点周围
        if (selectedRow != -1 && selectedCol != -1) {
            int highlightX = selectedCol * cellSize;
            int highlightY = selectedRow * cellSize;
            int highlightRadius = cellSize / 2;
            g2d.setColor(new Color(255, 255, 0, 100)); // 半透明黄色
            g2d.fillOval(highlightX - highlightRadius, highlightY - highlightRadius,
                         highlightRadius * 2, highlightRadius * 2);
        }

        // 绘制可能的移动 - 在交点上显示
        g2d.setColor(VALID_MOVE_COLOR);
        for (Point p : validMoves) {
            int moveX = p.y * cellSize;
            int moveY = p.x * cellSize;
            g2d.fillOval(moveX - 5, moveY - 5, 10, 10);
        }
    }

    /**
     * 绘制"宫"（九宫）
     * 九宫是 3×3 的交点范围：列 3-5，行 0-2 或 7-9
     */
    private void drawPalace(Graphics2D g2d) {
        g2d.setColor(new Color(150, 150, 150));
        g2d.setStroke(new BasicStroke(1));

        // 黑方宫（上方，行 0-2）
        int x1 = 3 * cellSize;
        int y1 = 0;
        int x2 = 5 * cellSize;
        int y2 = 2 * cellSize;
        // 绘制宫的框
        g2d.drawRect(x1, y1, x2 - x1, y2 - y1);
        // 绘制宫的对角线
        g2d.drawLine(x1, y1, x2, y2);
        g2d.drawLine(x2, y1, x1, y2);

        // 红方宫（下方，行 7-9）
        y1 = 7 * cellSize;
        y2 = 9 * cellSize;
        // 绘制宫的框
        g2d.drawRect(x1, y1, x2 - x1, y2 - y1);
        // 绘制宫的对角线
        g2d.drawLine(x1, y1, x2, y2);
        g2d.drawLine(x2, y1, x1, y2);
    }

    /**
     * 绘制棋子
     */
    private void drawPieces(Graphics2D g2d, Board board) {
        for (int row = 0; row < board.getRows(); row++) {
            for (int col = 0; col < board.getCols(); col++) {
                Piece piece = board.getPiece(row, col);
                if (piece != null) {
                    drawPiece(g2d, row, col, piece);
                }
            }
        }
    }

    /**
     * 绘制单个棋子 - 棋子位于棋盘线的交点上
     */
    private void drawPiece(Graphics2D g2d, int row, int col, Piece piece) {
        // 棋子放在棋盘线的交点上（不是格子中心）
        int x = col * cellSize;
        int y = row * cellSize;
        int radius = cellSize / 2 - 2;

        // 绘制棋子背景
        if (piece.isRed()) {
            g2d.setColor(RED_PIECE_COLOR);
        } else {
            g2d.setColor(BLACK_PIECE_COLOR);
        }
        g2d.fillOval(x - radius, y - radius, radius * 2, radius * 2);

        // 绘制棋子边框
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(x - radius, y - radius, radius * 2, radius * 2);

        // 绘制棋子文字
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SimHei", Font.BOLD, cellSize / 2));
        FontMetrics fm = g2d.getFontMetrics();
        String text = piece.getDisplayName();
        int textX = x - fm.stringWidth(text) / 2;
        int textY = y + fm.getAscent() / 2 - fm.getDescent();
        g2d.drawString(text, textX, textY);
    }

    @Override
    public Dimension getPreferredSize() {
        Board board = gameEngine.getBoard();
        // 棋子在交点上，需要为左右和上下都留出棋子半径的空间
        int pieceRadius = cellSize / 2 - 2;
        int boardWidth = (board.getCols() - 1) * cellSize;
        int boardHeight = (board.getRows() - 1) * cellSize;
        int width = boardWidth + 2 * pieceRadius;
        int height = boardHeight + 2 * pieceRadius;
        return new Dimension(width, height);
    }
}

