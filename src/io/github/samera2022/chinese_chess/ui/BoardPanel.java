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

    // 在联机模式下：本地是否操控红方；null 表示不限制（离线模式）
    private Boolean localControlsRed = null;

    // 棋盘翻转：true表示黑方在下（翻转180度）
    private boolean boardFlipped = false;

    // 新增：本地走子事件监听
    public interface LocalMoveListener {
        void onLocalMove(int fromRow, int fromCol, int toRow, int toCol);
    }
    private LocalMoveListener localMoveListener;
    public void setLocalMoveListener(LocalMoveListener listener) { this.localMoveListener = listener; }

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

    // 供外部设置：本地操控一方（true=红；false=黑；null=不限制）
    public void setLocalControlsRed(Boolean localControlsRed) {
        this.localControlsRed = localControlsRed;
    }

    // 设置棋盘是否翻转（黑方在下）
    public void setBoardFlipped(boolean flipped) {
        this.boardFlipped = flipped;
        repaint();
    }

    public boolean isBoardFlipped() {
        return boardFlipped;
    }

    /**
     * 将显示坐标转换为逻辑坐标（考虑翻转）
     */
    private int[] displayToLogic(int displayRow, int displayCol) {
        if (boardFlipped) {
            return new int[]{9 - displayRow, 8 - displayCol};
        }
        return new int[]{displayRow, displayCol};
    }

    /**
     * 将逻辑坐标转换为显示坐标（考虑翻转）
     */
    private int[] logicToDisplay(int logicRow, int logicCol) {
        if (boardFlipped) {
            return new int[]{9 - logicRow, 8 - logicCol};
        }
        return new int[]{logicRow, logicCol};
    }

    /**
     * 处理鼠标点击 - 点击交点附近时选中棋子
     */
    private void handleMouseClick(MouseEvent e) {
        // 将鼠标点击位置转换回显示坐标（考虑偏移量）
        int displayCol = Math.round((float) (e.getX() - offsetX) / cellSize);
        int displayRow = Math.round((float) (e.getY() - offsetY) / cellSize);

        // 转换为逻辑坐标
        int[] logical = displayToLogic(displayRow, displayCol);
        int row = logical[0];
        int col = logical[1];

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
            if (piece != null && piece.isRed() == gameEngine.isRedTurn()
                    && (localControlsRed == null || piece.isRed() == localControlsRed)) {
                selectedRow = row;
                selectedCol = col;
                calculateValidMoves();
            }
            repaint();
            return;
        }

        // 已有选择的棋子，尝试移动
        int fromR = selectedRow; int fromC = selectedCol; int toR = row; int toC = col; // 记录坐标

        // 检查是否是兵卒移动到底线需要晋升
        Piece movingPiece = board.getPiece(fromR, fromC);
        Piece.Type promotionType = null;

        if (movingPiece != null && gameEngine.isSpecialRuleEnabled("pawnPromotion")) {
            boolean isSoldier = movingPiece.getType() == Piece.Type.RED_SOLDIER ||
                               movingPiece.getType() == Piece.Type.BLACK_SOLDIER;
            // 对方底线晋升（总是允许）
            boolean isAtOpponentBaseLine = (movingPiece.isRed() && toR == 0) ||
                                          (!movingPiece.isRed() && toR == 9);
            // 己方底线晋升（仅在启用时允许）
            boolean isAtOwnBaseLine = (movingPiece.isRed() && toR == 9) ||
                                     (!movingPiece.isRed() && toR == 0);
            boolean allowOwnBaseLine = gameEngine.isSpecialRuleEnabled("allowOwnBaseLine");

            if (isSoldier && (isAtOpponentBaseLine || (isAtOwnBaseLine && allowOwnBaseLine))) {
                // 弹出晋升选择对话框
                promotionType = showPromotionDialog(movingPiece.isRed());
                if (promotionType == null) {
                    // 用户取消了晋升选择
                    repaint();
                    return;
                }
            }
        }

        if (gameEngine.makeMove(fromR, fromC, toR, toC, promotionType)) {
            // 通知本地走子（供联机发送）
            if (localMoveListener != null) {
                localMoveListener.onLocalMove(fromR, fromC, toR, toC);
            }
            selectedRow = -1;
            selectedCol = -1;
            validMoves.clear();
        } else {
            // 移动失败，重新选择
            selectedRow = row;
            selectedCol = col;
            Piece piece = board.getPiece(row, col);
            if (piece == null || piece.isRed() != gameEngine.isRedTurn()
                    || (localControlsRed != null && piece.isRed() != localControlsRed)) {
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
     * 显示晋升选择对话框
     * @param isRed 是否是红方
     * @return 选择的棋子类型，null表示取消
     */
    private Piece.Type showPromotionDialog(boolean isRed) {
        Piece.Type[] types;

        if (isRed) {
            types = new Piece.Type[]{
                Piece.Type.RED_CHARIOT,    // 車
                Piece.Type.RED_HORSE,      // 马
                Piece.Type.RED_CANNON,     // 炮
                Piece.Type.RED_ELEPHANT,   // 相
                Piece.Type.RED_ADVISOR     // 仕
            };
        } else {
            types = new Piece.Type[]{
                Piece.Type.BLACK_CHARIOT,  // 車
                Piece.Type.BLACK_HORSE,    // 马
                Piece.Type.BLACK_CANNON,   // 砲
                Piece.Type.BLACK_ELEPHANT, // 象
                Piece.Type.BLACK_ADVISOR   // 士
            };
        }

        // 使用每个类型的中文名称作为选项
        String[] options = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            options[i] = types[i].getChineseName();
        }

        int choice = JOptionPane.showOptionDialog(
            this,
            "选择晋升的棋子：",
            "兵卒晋升",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );

        if (choice >= 0 && choice < types.length) {
            return types[choice];
        }
        return null;
    }

    /**
     * 计算所有可能的移动
     */
    private void calculateValidMoves() {
        validMoves.clear();
        Board board = gameEngine.getBoard();
        MoveValidator validator = new MoveValidator(board);
        // 将特殊玩法开关同步到临时校验器，确保指示与实际规则一致
        validator.setAllowFlyingGeneral(gameEngine.isSpecialRuleEnabled("allowFlyingGeneral"));
        validator.setPawnCanRetreat(gameEngine.isSpecialRuleEnabled("pawnCanRetreat"));
        validator.setNoRiverLimit(gameEngine.isSpecialRuleEnabled("noRiverLimit"));
        validator.setAdvisorCanLeave(gameEngine.isSpecialRuleEnabled("advisorCanLeave"));
        validator.setInternationalKing(gameEngine.isSpecialRuleEnabled("internationalKing"));
        validator.setPawnPromotion(gameEngine.isSpecialRuleEnabled("pawnPromotion"));
        validator.setAllowOwnBaseLine(gameEngine.isSpecialRuleEnabled("allowOwnBaseLine"));
        validator.setAllowInsideRetreat(gameEngine.isSpecialRuleEnabled("allowInsideRetreat"));
        validator.setInternationalAdvisor(gameEngine.isSpecialRuleEnabled("internationalAdvisor"));
        validator.setAllowElephantCrossRiver(gameEngine.isSpecialRuleEnabled("allowElephantCrossRiver"));
        validator.setAllowAdvisorCrossRiver(gameEngine.isSpecialRuleEnabled("allowAdvisorCrossRiver"));
        validator.setAllowKingCrossRiver(gameEngine.isSpecialRuleEnabled("allowKingCrossRiver"));
        validator.setLeftRightConnected(gameEngine.isSpecialRuleEnabled("leftRightConnected"));
        validator.setNoRiverLimitPawn(gameEngine.isSpecialRuleEnabled("noRiverLimitPawn"));

        // 新增：调试输出，确保同步到validator
        validator.setUnblockPiece(gameEngine.isUnblockPiece());
        validator.setUnblockHorseLeg(gameEngine.isUnblockHorseLeg());
        validator.setUnblockElephantEye(gameEngine.isUnblockElephantEye());

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

        // 绘制移动指示器（在棋子之后，显示在最上层）
        drawMoveIndicators(g2d, board);
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
            // 边界线贯穿全局，内部线在“河”区域留空
            if (col == 0 || col == board.getCols() - 1) {
                g2d.drawLine(col * cellSize, 0, col * cellSize, boardHeight);
            } else {
                int yTopEnd = 4 * cellSize;      // 河上缘（0-based 第5行之上）
                int yBottomStart = 5 * cellSize; // 河下缘（0-based 第6行之下）
                g2d.drawLine(col * cellSize, 0, col * cellSize, yTopEnd);
                g2d.drawLine(col * cellSize, yBottomStart, col * cellSize, boardHeight);
            }
        }

        // 横线：从第 0 行到第 rows-1 行（共 rows 条线）
        for (int row = 0; row < board.getRows(); row++) {
            int y = row * cellSize;
            if (row == 4 || row == 5) {
                // “楚河汉界”双细线标记
                g2d.drawLine(0, y - 2, boardWidth, y - 2);
                g2d.drawLine(0, y + 2, boardWidth, y + 2);
            } else {
                g2d.drawLine(0, y, boardWidth, y);
            }
        }

        // 绘制"楚河      汉界"文字（翻转时显示"汉界      楚河"）
        g2d.setColor(GRID_COLOR);
        g2d.setFont(new Font("LiSu", Font.BOLD, (int) Math.max(16, cellSize / 1.5)));
        String riverText = boardFlipped ? "汉界      楚河" : "楚河      汉界";
        FontMetrics riverFm = g2d.getFontMetrics();
        int riverTextX = (boardWidth - riverFm.stringWidth(riverText)) / 2;
        int riverTextY = 4 * cellSize + (cellSize / 2) + (riverFm.getAscent() - riverFm.getDescent()) / 2;
        g2d.drawString(riverText, riverTextX, riverTextY);

        // 绘制"宫"（King's palace）
        drawPalace(g2d);

        // 绘制炮的位置标记（空心圆点）
        drawCannonMarks(g2d);
    }

    /**
     * 绘制移动指示器（高亮选中和可能的移动位置）
     */
    private void drawMoveIndicators(Graphics2D g2d, Board board) {
        // 绘制所选棋子的高亮 - 显示在交点周围
        if (selectedRow != -1 && selectedCol != -1) {
            int[] display = logicToDisplay(selectedRow, selectedCol);
            int highlightX = display[1] * cellSize;
            int highlightY = display[0] * cellSize;
            int highlightRadius = cellSize / 2;
            g2d.setColor(new Color(255, 255, 0, 100)); // 半透明黄色
            g2d.fillOval(highlightX - highlightRadius, highlightY - highlightRadius,
                         highlightRadius * 2, highlightRadius * 2);
        }

        // 绘制可能的移动 - 在交点上显示
        g2d.setColor(VALID_MOVE_COLOR);
        for (Point p : validMoves) {
            int[] display = logicToDisplay(p.x, p.y);
            int moveX = display[1] * cellSize;
            int moveY = display[0] * cellSize;
            g2d.fillOval(moveX - 5, moveY - 5, 10, 10);
        }
    }

    /**
     * 绘制"宫"（九宫）
     * 九宫是 3×3 的交点范围：列 3-5，行 0-2 或 7-9
     */
    private void drawPalace(Graphics2D g2d) {
        g2d.setColor(GRID_COLOR);
        g2d.setStroke(new BasicStroke(1));

        // 黑方宫（上方，行 0-2）
        int x1 = 3 * cellSize;
        int y1 = 0;
        int x2 = 5 * cellSize;
        int y2 = 2 * cellSize;
        drawDoublePalaceFrame(g2d, x1, y1, x2, y2);
        // 单黑细线对角线
        g2d.drawLine(x1, y1, x2, y2);
        g2d.drawLine(x2, y1, x1, y2);

        // 红方宫（下方，行 7-9）
        y1 = 7 * cellSize;
        y2 = 9 * cellSize;
        drawDoublePalaceFrame(g2d, x1, y1, x2, y2);
        // 单黑细线对角线
        g2d.drawLine(x1, y1, x2, y2);
        g2d.drawLine(x2, y1, x1, y2);
    }

    // 绘制双黑细线宫框
    private void drawDoublePalaceFrame(Graphics2D g2d, int x1, int y1, int x2, int y2) {
        int width = x2 - x1;
        int height = y2 - y1;
        g2d.drawRect(x1, y1, width, height);
        int inset = 2; // 内缩 2px 的第二条细线
        g2d.drawRect(x1 + inset, y1 + inset, width - 2 * inset, height - 2 * inset);
    }

    /**
     * 绘制棋子
     */
    private void drawPieces(Graphics2D g2d, Board board) {
        for (int row = 0; row < board.getRows(); row++) {
            for (int col = 0; col < board.getCols(); col++) {
                Piece piece = board.getPiece(row, col);
                if (piece != null) {
                    int[] display = logicToDisplay(row, col);
                    drawPiece(g2d, display[0], display[1], piece);
                }
            }
        }
    }

    private void drawPiece(Graphics2D g2d, int displayRow, int displayCol, Piece piece) {
        // 棋子放在棋盘线的交点上（不是格子中心）
        int x = displayCol * cellSize;
        int y = displayRow * cellSize;
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

    /**
     * 绘制炮的位置标记（空心圆点）
     */
    private void drawCannonMarks(Graphics2D g2d) {
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));

        int cannonMarkRadius = 5; // 圆的半径（直径10）

        // 红方炮的位置（初始位置：行7，列1和列7）
        int redCannonY = 7 * cellSize;
        g2d.drawOval(1 * cellSize - cannonMarkRadius, redCannonY - cannonMarkRadius, cannonMarkRadius * 2, cannonMarkRadius * 2);
        g2d.drawOval(7 * cellSize - cannonMarkRadius, redCannonY - cannonMarkRadius, cannonMarkRadius * 2, cannonMarkRadius * 2);

        // 黑方炮的位置（初始位置：行2，列1和列7）
        int blackCannonY = 2 * cellSize;
        g2d.drawOval(1 * cellSize - cannonMarkRadius, blackCannonY - cannonMarkRadius, cannonMarkRadius * 2, cannonMarkRadius * 2);
        g2d.drawOval(7 * cellSize - cannonMarkRadius, blackCannonY - cannonMarkRadius, cannonMarkRadius * 2, cannonMarkRadius * 2);
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
