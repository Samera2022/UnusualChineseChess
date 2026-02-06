package io.github.samera2022.chinese_chess.ui;

import io.github.samera2022.chinese_chess.GameConfig;
import io.github.samera2022.chinese_chess.engine.Board;
import io.github.samera2022.chinese_chess.engine.GameEngine;
import io.github.samera2022.chinese_chess.model.Piece;
import io.github.samera2022.chinese_chess.rules.MoveValidator;
import io.github.samera2022.chinese_chess.rules.RulesConfigProvider;
import io.github.samera2022.chinese_chess.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.rules.RuleRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * 棋盘UI - 显示棋盘和棋子
 */
public class BoardPanel extends JPanel {
    private GameEngine gameEngine;
    private GameRulesConfig rulesConfig = RulesConfigProvider.get();
    private final GameRulesConfig.RuleChangeListener ruleChangeListener = (key, oldV, newV, src) -> {
        // when specific config changes, keep validator updated (debounced updates not necessary here)
        syncValidatorFromConfig();
    };
    private RulesConfigProvider.InstanceChangeListener providerListener;

    private int cellSize = GameConfig.CELL_SIZE;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private int selectedStackIndex = -1; // 从堆栈中选择的棋子索引
    private java.util.List<Point> validMoves = new java.util.ArrayList<>();

    // 强制走子相关的字段
    private int forceMoveFromRow = -1;
    private int forceMoveFromCol = -1;
    private int forceMoveToRow = -1;
    private int forceMoveToCol = -1;

    // 新增：强制走子指示器样式切换
    private boolean style = true; // false=红色空心圆圈，true=紫色移动指示器样式
    public void setForceMoveIndicatorStyle(boolean style) {
        this.style = style;
        repaint();
    }

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
        // register provider instance listener to keep local rulesConfig up-to-date
        // initialize providerListener here to avoid forward-reference issues
        this.providerListener = (oldInst, newInst) -> {
            try {
                if (oldInst != null) {
                    try { oldInst.removeRuleChangeListener(ruleChangeListener); } catch (Throwable ignored) {}
                }
                if (newInst != null) {
                    try { newInst.addRuleChangeListener(ruleChangeListener); } catch (Throwable ignored) {}
                }
                this.rulesConfig = newInst;
                syncValidatorFromConfig();
            } catch (Throwable ignored) {}
        };
        RulesConfigProvider.addInstanceChangeListener(providerListener);
        if (this.rulesConfig != null) this.rulesConfig.addRuleChangeListener(ruleChangeListener);
        setBackground(BOARD_COLOR);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    // 左键：选择棋子
                    handleLeftClick(e);
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    // 右键：尝试移动到该位置
                    handleRightClick(e);
                } else if (e.getButton() == MouseEvent.BUTTON2) {
                    // 中键：尝试强制走子
                    if (rulesConfig.getBoolean(RuleRegistry.ALLOW_FORCE_MOVE.registryName)) handleMiddleClick(e);
                }
            }
        });
    }

    // 供外部设置：本地操控一方（true=红；false=黑；null=不限制）
    public void setLocalControlsRed(Boolean localControlsRed) {
        this.localControlsRed = localControlsRed;
        repaint();
    }

    // 新增：对外暴露当前本地执方（用于联机下撤销按钮的启用判断）
    public Boolean getLocalControlsRed() {
        return localControlsRed;
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
     * 设置强制走子指示器的位置（显示红色圆圈）
     */
    public void setForceMoveIndicator(int fromRow, int fromCol, int toRow, int toCol) {
        this.forceMoveFromRow = fromRow;
        this.forceMoveFromCol = fromCol;
        this.forceMoveToRow = toRow;
        this.forceMoveToCol = toCol;
        repaint();
    }

    /**
     * 清除强制走子指示器
     */
    public void clearForceMoveIndicator() {
        this.forceMoveFromRow = -1;
        this.forceMoveFromCol = -1;
        this.forceMoveToRow = -1;
        this.forceMoveToCol = -1;
        repaint();
    }

    /**
     * 临时显示对方选中的棋子（黄色高亮）
     */
    public void setRemotePieceHighlight(int row, int col) {
        this.selectedRow = row;
        this.selectedCol = col;
        repaint();
    }

    /**
     * 清除远程棋子高亮
     */
    public void clearRemotePieceHighlight() {
        this.selectedRow = -1;
        this.selectedCol = -1;
        repaint();
    }

    /**
     * 清除选择状态（游戏重新开始时调用）
     */
    public void clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        selectedStackIndex = -1;
        validMoves.clear();
        forceMoveFromRow = -1;
        forceMoveFromCol = -1;
        forceMoveToRow = -1;
        forceMoveToCol = -1;
        repaint();
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
    // 左键，中键和右键应该采用相同的逻辑。但是实际操作时，发现只要保证左键正确工作就可以了……
    // 而且，并不建议将判断是否可行单独抽出来变成一个新的方法。新的这个方法本身就依赖大量的变量（而且这些变量在左中右键里都要继续用），反倒不如放在方法里简单。
    /**
     * 处理左键点击 - 点击交点附近时选中棋子
     */
    private void handleLeftClick(MouseEvent e) {
        int displayCol = Math.round((float) (e.getX() - offsetX) / cellSize);
        int displayRow = Math.round((float) (e.getY() - offsetY) / cellSize);
        int[] logical = displayToLogic(displayRow, displayCol);
        int row = logical[0];
        int col = logical[1];
        Board board = gameEngine.getBoard();
        if (!board.isValid(row, col)) {
            return;
        }
        if (row == selectedRow && col == selectedCol) {
            selectedRow = -1;
            selectedCol = -1;
            selectedStackIndex = -1;
            validMoves.clear();
            repaint();
            return;
        }
        Piece piece = board.getPiece(row, col);
//        System.out.println("---PRECHECK---");
//        System.out.println("Piece is Red: "+piece.isRed());
//        System.out.println("Game Engine: "+gameEngine.isRedTurn());
//        System.out.println("Local Controls Red: "+localControlsRed);
//        System.out.println("---PRECHECK---");
        if (rulesConfig.getBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName) && isStackedPiece(piece))
            if (piece.isRed() == gameEngine.isRedTurn())
                if (ChineseChessFrame.isNetSessionActive())
                    if (gameEngine.isRedTurn()==localControlsRed) showStackSelectionForSourceDialog(row, col);
                    else showStackInfoDialog(row, col);
                else showStackSelectionForSourceDialog(row, col);
            else showStackInfoDialog(row, col);
         else {
            if ((!ChineseChessFrame.isNetSessionActive() && (piece == null || piece.isRed() != gameEngine.isRedTurn())) ||
                    ChineseChessFrame.isNetSessionActive() && (piece == null || !(localControlsRed == gameEngine.isRedTurn() && localControlsRed == piece.isRed())))
                return;
            else {
                selectedRow = row;
                selectedCol = col;
                selectedStackIndex = -1;
                calculateValidMoves();
            }
        }
        repaint();
    }

    /**
     * 处理右键点击 - 在选中棋子后，右键尝试移动到该位置（会触发堆叠检查）
     */
    private void handleRightClick(MouseEvent e) {
//        boolean restrictBySide =  ChineseChessFrame.isNetSessionActive();
//        boolean onlyCurrentSide =  !ChineseChessFrame.isNetSessionActive();
//        if (restrictBySide && localControlsRed != null && gameEngine.isRedTurn() != localControlsRed) {
//            return;
//        }
        // 将鼠标点击位置转换回显示坐标（考虑偏移量）
        int displayCol = Math.round((float) (e.getX() - offsetX) / cellSize);
        int displayRow = Math.round((float) (e.getY() - offsetY) / cellSize);
        // 转换为逻辑坐标
        int[] logical = displayToLogic(displayRow, displayCol);
        int toRow = logical[0];
        int toCol = logical[1];
        Board board = gameEngine.getBoard();
        if (!board.isValid(toRow, toCol)) {
            return;
        }
        // 如果没有选择棋子，不处理右键
        if (selectedRow == -1 || selectedCol == -1) {
            return;
        }
        int fromR = selectedRow;
        int fromC = selectedCol;
//        // 联机模式下的回合检查（右键移动时）
//        if (restrictBySide && localControlsRed != null && ChineseChessFrame.isNetSessionActive()) {
//            if (gameEngine.isRedTurn() != localControlsRed) {
//                return;
//            }
//        }
        if (selectedStackIndex >= 0) {
            board.getStack(fromR, fromC);
        } else {
            board.getPiece(fromR, fromC);
        }
//        Piece sourcePiece;
        //        // 新增：本地联机且只允许当前回合方操作自己的棋子

//        if (onlyCurrentSide && (sourcePiece == null || sourcePiece.isRed() != gameEngine.isRedTurn())) {
//            return;
//        }
        // 检查目标位置是否是己方棋子（堆叠情况）
        Piece targetPiece = board.getPiece(toRow, toCol);
        Piece sourcePiece = selectedStackIndex >= 0 ?
            board.getStack(fromR, fromC).get(selectedStackIndex) :
            board.getPiece(fromR, fromC);

        if (sourcePiece != null && targetPiece != null && targetPiece.isRed() == gameEngine.isRedTurn() &&
            targetPiece.isRed() == sourcePiece.isRed()) {
            // 目标是己方棋子，检查是否启用堆叠
            if (gameEngine.getRulesConfig().getBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName)
                    && Integer.parseInt(RuleSettingsPanel.getValue(RuleRegistry.MAX_STACKING_COUNT.registryName)) > 1
            ) {
                // 直接执行堆叠移动（保留堆栈）
                if (gameEngine.makeMove(fromR, fromC, toRow, toCol, null, selectedStackIndex)) {
                    if (localMoveListener != null) {
                        localMoveListener.onLocalMove(fromR, fromC, toRow, toCol);
                    }
                    selectedRow = -1;
                    selectedCol = -1;
                    selectedStackIndex = -1;
                    validMoves.clear();
                }
                repaint();
                return;
            }
        }

        // 不是堆叠情况，执行正常移动
        Piece movingPiece = selectedStackIndex >= 0 ?
            board.getStack(fromR, fromC).get(selectedStackIndex) :
            board.getPiece(fromR, fromC);
        Piece.Type promotionType = null;

        // 检查是否需要晋升：首先验证这是一个有效的移动
        if (movingPiece != null && rulesConfig.getBoolean(RuleRegistry.PAWN_PROMOTION.registryName)) {
            boolean isSoldier = movingPiece.getType() == Piece.Type.RED_SOLDIER ||
                               movingPiece.getType() == Piece.Type.BLACK_SOLDIER;
            boolean isAtOpponentBaseLine = (movingPiece.isRed() && toRow == 0) ||
                                          (!movingPiece.isRed() && toRow == 9);
            boolean isAtOwnBaseLine = (movingPiece.isRed() && toRow == 9) ||
                                     (!movingPiece.isRed() && toRow == 0);
            boolean allowOwnBaseLine = rulesConfig.getBoolean(RuleRegistry.ALLOW_OWN_BASE_LINE.registryName);

            // 只有在兵卒到达底线 且 移动是有效的情况下才弹出晋升对话框
            if (isSoldier && (isAtOpponentBaseLine || (isAtOwnBaseLine && allowOwnBaseLine))) {
                // 先验证移动是否有效（使用临时的 MoveValidator）
                MoveValidator tmpValidator = new MoveValidator(gameEngine.getBoard());
                tmpValidator.setRulesConfig(gameEngine.getRulesConfig());
                if (tmpValidator.isValidMove(fromR, fromC, toRow, toCol, selectedStackIndex)) {
                    promotionType = showPromotionDialog(movingPiece.isRed());
                    if (promotionType == null) {
                        repaint();
                        return;
                    }
                }
            }
        }

        if (gameEngine.makeMove(fromR, fromC, toRow, toCol, promotionType, selectedStackIndex)) {
            if (localMoveListener != null) {
                localMoveListener.onLocalMove(fromR, fromC, toRow, toCol);
            }
            selectedRow = -1;
            selectedCol = -1;
            selectedStackIndex = -1;
            validMoves.clear();
        }

        repaint();
    }

    /**
     * 处理中键点击 - 在选中棋子后，中键点击目标位置申请强制走子
     */
    private void handleMiddleClick(MouseEvent e) {
        boolean restrictBySide =  ChineseChessFrame.isNetSessionActive();
        boolean onlyCurrentSide = !ChineseChessFrame.isNetSessionActive();
        if (restrictBySide && localControlsRed != null && gameEngine.isRedTurn() != localControlsRed) {
            return;
        }
        System.out.println("[DEBUG] [BoardPanel] 中键被点击");
        // 将鼠标点击位置转换回显示坐标（考虑偏移量）
        int displayCol = Math.round((float) (e.getX() - offsetX) / cellSize);
        int displayRow = Math.round((float) (e.getY() - offsetY) / cellSize);
        // 转换为逻辑坐标
        int[] logical = displayToLogic(displayRow, displayCol);
        int toRow = logical[0];
        int toCol = logical[1];
        System.out.println("[DEBUG] [BoardPanel] 目标坐标: " + toRow + "," + toCol);

        Board board = gameEngine.getBoard();
        if (!board.isValid(toRow, toCol)) {
            System.out.println("[DEBUG] [BoardPanel] 目标坐标无效");
            return;
        }
        // 如果没有选择棋子，不处理中键
        if (selectedRow == -1 || selectedCol == -1) {
            System.out.println("[DEBUG] [BoardPanel] 没有选择棋子");
            return;
        }
        System.out.println("[DEBUG] [BoardPanel] 选中棋子: " + selectedRow + "," + selectedCol);

        // 检查选中的棋子是否是己方的（仅在 restrictBySide==true 时检查）
        Piece sourcePiece = selectedStackIndex >= 0 ?
            board.getStack(selectedRow, selectedCol).get(selectedStackIndex) :
            board.getPiece(selectedRow, selectedCol);
        if (restrictBySide) {
            if (sourcePiece == null || sourcePiece.isRed() != gameEngine.isRedTurn() ||
                (localControlsRed != null && sourcePiece.isRed() != localControlsRed)) {
                // 不是己方棋子，不能进行强制走子
                System.out.println("[DEBUG] [BoardPanel] 选中的不是己方棋子");
                return;
            }
        } else if (onlyCurrentSide) {
            if (sourcePiece == null || sourcePiece.isRed() != gameEngine.isRedTurn()) {
                // 不是当前回合方的棋子，不能进行强制走子
                System.out.println("[DEBUG] [BoardPanel] 选中的不是当前回合方的棋子");
                return;
            }
        } else {
            if (sourcePiece == null) {
                System.out.println("[DEBUG] [BoardPanel] 选中的不是有效棋子");
                return;
            }
        }

        System.out.println("[DEBUG] [BoardPanel] 显示强制走子指示器和弹出确认窗体");
        // 显示强制走子指示器（红色圆圈）
        setForceMoveIndicator(selectedRow, selectedCol, toRow, toCol);

        // 弹出确认窗体
        int ans = JOptionPane.showConfirmDialog(this,
                "是否进行强制走子？",
                "强制走子确认",
                JOptionPane.YES_NO_OPTION);

        if (ans == JOptionPane.YES_OPTION) {
            System.out.println("[DEBUG] [BoardPanel] 用户确认强制走子，触发回调");
            // 触发强制走子请求的回调接口
            if (forceMoveRequestListener != null) {
                forceMoveRequestListener.onForceMoveRequest(selectedRow, selectedCol, toRow, toCol);
            }
        } else {
            System.out.println("[DEBUG] [BoardPanel] 用户取消强制走子");
            // 取消操作，清除指示器
            clearForceMoveIndicator();
        }

        repaint();
    }

    /**
     * 强制走子请求监听器接口
     */
    public interface ForceMoveRequestListener {
        void onForceMoveRequest(int fromRow, int fromCol, int toRow, int toCol);
    }
    private ForceMoveRequestListener forceMoveRequestListener;
    public void setForceMoveRequestListener(ForceMoveRequestListener listener) {
        this.forceMoveRequestListener = listener;
    }
    /**
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
     * 显示源位置堆叠棋子选择对话框（己方左键点击堆叠位置）
     * 用于选择要移动的具体棋子
     */
    private void showStackSelectionForSourceDialog(int row, int col) {
        Board board = gameEngine.getBoard();
        java.util.List<Piece> stack = board.getStack(row, col);

        if (stack.isEmpty()) return;

        String[] options = new String[stack.size()];
        for (int i = 0; i < stack.size(); i++) {
            Piece p = stack.get(i);
            options[i] = p.getDisplayName() + " (" + (i + 1) + ")";
        }

        // 根据组合条件动态调整提示文案：启用堆叠 + 最大堆叠数>1 + 允许背负
        boolean carryEnabled = rulesConfig.getBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName)
                && rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName) > 1
                && rulesConfig.getBoolean(RuleRegistry.ALLOW_CARRY_PIECES_ABOVE.registryName);
        String message = carryEnabled
                ? "选择要移动的棋子（选择某个棋子后，该棋子及其上方的所有棋子都会一起移动）："
                : "选择要移动的棋子（选择某个棋子后，仅该棋子会移动；其上方的棋子将留在原位置）：";

        int choice = JOptionPane.showOptionDialog(
            this,
            message,
            "选择要移动的棋子",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[stack.size() - 1]
        );

        if (choice >= 0 && choice < stack.size()) {
            // 设置选中的棋子信息并计算可用移动
            selectedRow = row;
            selectedCol = col;
            selectedStackIndex = choice;
            calculateValidMoves();
        }
    }

    /**
     * 显示堆叠棋子选择对话框（己方点击堆叠目标位置）
     */
    private void showStackSelectionDialog(int toRow, int toCol) {
        Board board = gameEngine.getBoard();
        java.util.List<Piece> stack = board.getStack(toRow, toCol);

        if (stack.isEmpty()) return;

        String[] options = new String[stack.size()];
        for (int i = 0; i < stack.size(); i++) {
            Piece p = stack.get(i);
            options[i] = p.getDisplayName() + " (" + (i + 1) + ")";
        }

        int choice = JOptionPane.showOptionDialog(
            this,
            "选择堆叠棋子中的某一个进行移动：",
            "选择堆叠棋子",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[stack.size() - 1]
        );

        if (choice >= 0 && choice < stack.size()) {
            // 执行移动
            if (gameEngine.makeMove(selectedRow, selectedCol, toRow, toCol, null, selectedStackIndex)) {
                if (localMoveListener != null) {
                    localMoveListener.onLocalMove(selectedRow, selectedCol, toRow, toCol);
                }
            }
            selectedRow = -1;
            selectedCol = -1;
            selectedStackIndex = -1;
            validMoves.clear();
        }
    }

    /**
     * 显示堆叠棋子信息对话框（对方点击堆叠）
     */
    private void showStackInfoDialog(int row, int col) {
        Board board = gameEngine.getBoard();
        java.util.List<Piece> stack = board.getStack(row, col);

        if (stack.isEmpty()) return;

        StringBuilder message = new StringBuilder("目前对方堆叠在此处的棋子有：\n\n");
        for (int i = 0; i < stack.size(); i++) {
            Piece p = stack.get(i);
            message.append((i + 1)).append(". ").append(p.getDisplayName()).append("\n");
        }

        JOptionPane.showMessageDialog(
            this,
            message.toString(),
            "堆叠信息",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * 判断指定棋子是否为“堆叠棋子”
     * @param piece 棋子对象
     * @return 是否为堆叠棋子
     */
    public boolean isStackedPiece(Piece piece) {
        if (piece == null) return false;
        Board board = gameEngine.getBoard();
        java.util.List<Piece> stack = board.getStack(piece.getRow(), piece.getCol());
        return stack.size() > 1 && stack.contains(piece);
    }

    /**
     * 计算所有可能的移动
     */
    private void calculateValidMoves() {
        validMoves.clear();
        Board board = gameEngine.getBoard();
        MoveValidator validator = new MoveValidator(board);
        // 移除所有 validator.set... 调用，因为它们会修改全局配置，导致规则冲突（特别是堆叠和自己吃自己）
        // validator 默认使用全局 rulesConfig，所以它已经拥有最新的规则状态。

        for (int row = 0; row < board.getRows(); row++) {
            for (int col = 0; col < board.getCols(); col++) {
                if (validator.isValidMove(selectedRow, selectedCol, row, col, selectedStackIndex)) {
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
        if (rulesConfig.getBoolean(RuleRegistry.SHOW_HINTS.registryName)) {
            g2d.setColor(VALID_MOVE_COLOR);
            for (Point p : validMoves) {
                int[] display = logicToDisplay(p.x, p.y);
                int moveX = display[1] * cellSize;
                int moveY = display[0] * cellSize;
                g2d.fillOval(moveX - 5, moveY - 5, 10, 10);
            }
        }

        // 绘制强制走子指示器
        if (forceMoveToRow != -1 && forceMoveToCol != -1) {
            int[] display = logicToDisplay(forceMoveToRow, forceMoveToCol);
            int moveX = display[1] * cellSize;
            int moveY = display[0] * cellSize;
            if (!style) {
                // 原红色空心圆圈
                g2d.setColor(new Color(255, 0, 0)); // 红色
                g2d.setStroke(new BasicStroke(3));
                int radius = cellSize / 2;
                g2d.drawOval(moveX - radius, moveY - radius, radius * 2, radius * 2);
            } else {
                // 紫色移动指示器样式（与正常移动指示器相同，但颜色为紫色）
                g2d.setColor(new Color(0, 38, 255)); // 紫色
                g2d.fillOval(moveX - 5, moveY - 5, 10, 10);
            }
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
     * 绘制炮的位置标记（空心圆点）
     */
    private void drawCannonMarks(Graphics2D g2d) {
        g2d.setColor(GRID_COLOR);
        g2d.setStroke(new BasicStroke(1));

        // 黑方炮位置标记：(2,1) 和 (2,7)
        drawCannonMark(g2d, 2, 1);
        drawCannonMark(g2d, 2, 7);

        // 红方炮位置标记：(7,1) 和 (7,7)
        drawCannonMark(g2d, 7, 1);
        drawCannonMark(g2d, 7, 7);
    }

    /**
     * 绘制单个炮的位置标记
     */
    private void drawCannonMark(Graphics2D g2d, int row, int col) {
        int[] display = logicToDisplay(row, col);
        int x = display[1] * cellSize;
        int y = display[0] * cellSize;
        int markRadius = 3;

        g2d.drawOval(x - markRadius, y - markRadius, markRadius * 2, markRadius * 2);
    }

    /**
     * 绘制棋子
     */
    private void drawPieces(Graphics2D g2d, Board board) {
        int pieceRadius = cellSize / 2 - 2;

        for (int row = 0; row < board.getRows(); row++) {
            for (int col = 0; col < board.getCols(); col++) {
                int stackSize = board.getStackSize(row, col);
                if (stackSize == 0) continue;

                java.util.List<Piece> stack = board.getStack(row, col);
                int[] display = logicToDisplay(row, col);
                int x = display[1] * cellSize;
                int y = display[0] * cellSize;

                if (stackSize == 1) {
                    // 单个棋子：正常绘制
                    Piece piece = stack.get(0);
                    drawSinglePiece(g2d, piece, x, y, pieceRadius);
                } else {
                    // 堆叠棋子：绘制错开的效果
                    for (int i = 0; i < stackSize; i++) {
                        Piece piece = stack.get(i);
                        int offsetX = (i % 2 == 0 ? -4 : 4);
                        int offsetY = (i % 2 == 0 ? -4 : 4);
                        drawSinglePiece(g2d, piece, x + offsetX, y + offsetY, pieceRadius);
                    }
                    // 显示堆叠数量徽章
                    drawStackBadge(g2d, stackSize, x, y, pieceRadius);
                }
            }
        }
    }

    /**
     * 绘制单个棋子
     */
    private void drawSinglePiece(Graphics2D g2d, Piece piece, int x, int y, int radius) {
        // 绘制圆形背景
        if (piece.isRed()) {
            g2d.setColor(RED_PIECE_COLOR);
        } else {
            g2d.setColor(BLACK_PIECE_COLOR);
        }
        g2d.fillOval(x - radius, y - radius, radius * 2, radius * 2);

        // 绘制边框
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(x - radius, y - radius, radius * 2, radius * 2);

        // 绘制棋子文字
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SimHei", Font.BOLD, cellSize / 2));
        String text = piece.getDisplayName();
        FontMetrics fm = g2d.getFontMetrics();
        int textX = x - fm.stringWidth(text) / 2;
        int textY = y + fm.getAscent() / 2 - fm.getDescent();
        g2d.drawString(text, textX, textY);
    }

    /**
     * 绘制堆叠数量徽章
     */
    private void drawStackBadge(Graphics2D g2d, int count, int x, int y, int radius) {
        // 在右下角绘制小圆形显示堆叠数量
        int badgeRadius = radius / 3;
        int badgeX = x + radius - badgeRadius;
        int badgeY = y + radius - badgeRadius;

        g2d.setColor(new Color(255, 200, 0)); // 金黄色
        g2d.fillOval(badgeX - badgeRadius, badgeY - badgeRadius, badgeRadius * 2, badgeRadius * 2);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawOval(badgeX - badgeRadius, badgeY - badgeRadius, badgeRadius * 2, badgeRadius * 2);

        // 绘制数字
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Dialog", Font.BOLD, (int) (badgeRadius * 1.2)));
        String text = String.valueOf(count);
        FontMetrics fm = g2d.getFontMetrics();
        int textX = badgeX - fm.stringWidth(text) / 2;
        int textY = badgeY + (fm.getAscent() - fm.getDescent()) / 2;
        g2d.drawString(text, textX, textY);
    }

    @Override
    public Dimension getPreferredSize() {
        Board board = gameEngine.getBoard();
        // 棚子在交点上，需要为左右和上下都留出棋子半径的空间
        int pieceRadius = cellSize / 2 - 2;
        int boardWidth = (board.getCols() - 1) * cellSize;
        int boardHeight = (board.getRows() - 1) * cellSize;
        int width = boardWidth + 2 * pieceRadius;
        int height = boardHeight + 2 * pieceRadius;
        return new Dimension(width, height);
    }

    /**
     * Unregister listeners from provider. Caller (e.g. ChineseChessFrame) should call this on shutdown.
     */
    public void unbind() {
        try {
            RulesConfigProvider.removeInstanceChangeListener(providerListener);
        } catch (Throwable ignored) {}
        try { if (this.rulesConfig != null) this.rulesConfig.removeRuleChangeListener(ruleChangeListener); } catch (Throwable ignored) {}
    }

    private void syncValidatorFromConfig() {
        try {
            MoveValidator validator = new MoveValidator(gameEngine.getBoard());
            validator.setRulesConfig(this.rulesConfig);
            // optional: we don't replace engine's validator here; BoardPanel only uses validator for hints
        } catch (Throwable ignored) {}
    }

    public int getSelectedStackIndex() {
        return selectedStackIndex;
    }
}