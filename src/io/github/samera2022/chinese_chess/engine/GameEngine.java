package io.github.samera2022.chinese_chess.engine;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.model.HistoryItem;
import io.github.samera2022.chinese_chess.model.Move;
import io.github.samera2022.chinese_chess.model.Piece;

import io.github.samera2022.chinese_chess.model.RuleChangeRecord;
import io.github.samera2022.chinese_chess.rules.CheckDetector;
import io.github.samera2022.chinese_chess.rules.MoveValidator;
import io.github.samera2022.chinese_chess.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.rules.RuleRegistry;
import io.github.samera2022.chinese_chess.rules.RulesConfigProvider;
import io.github.samera2022.chinese_chess.ui.RuleSettingsPanel;

import java.util.*;

/**
 * 游戏引擎 - 管理游戏状态和逻辑
 */
public class GameEngine {
    private final Gson gson = new Gson();
    private Board board;
    private MoveValidator validator;
    private CheckDetector checkDetector;
    // injected rules config (may be the provider.get()). Engine does not own lifecycle.
    private GameRulesConfig rulesConfig;
    private final RulesConfigProvider.InstanceChangeListener providerListener = (oldInst, newInst) -> {
        // update local reference and refresh validator
        try {
            this.rulesConfig = newInst;
            if (this.validator != null) this.validator.setRulesConfig(newInst);
        } catch (Throwable ignored) {}
    };
    private List<Move> moveHistory;
    private List<RuleChangeRecord> ruleChangeHistory;
    private boolean isRedTurn;
    private GameState gameState;
    private List<GameStateListener> listeners;

    // 回放功能支持
    private Board savedInitialBoard = null;
    private boolean savedInitialIsRedTurn = true;
    private boolean isInReplayMode = false;
    private int currentReplayStep = -1;


    public enum GameState {
        RUNNING,
        RED_CHECKMATE,
        BLACK_CHECKMATE,
        DRAW
    }

    public interface GameStateListener {
        void onGameStateChanged(GameState newState);
        void onMoveExecuted(Move move);
    }

    public GameEngine(GameRulesConfig injectedRulesConfig) {
        // injectedRulesConfig can be RulesConfigProvider.get() or any other instance
        this.rulesConfig = injectedRulesConfig != null ? injectedRulesConfig : RulesConfigProvider.get();
        this.board = new Board();
        this.validator = new MoveValidator(board);
        this.validator.setRulesConfig(this.rulesConfig);
        this.checkDetector = new CheckDetector(board, validator);
        // register provider-level listener to support hot-replace
        RulesConfigProvider.addInstanceChangeListener(providerListener);
        this.moveHistory = new ArrayList<>();
        this.ruleChangeHistory = new ArrayList<>();
        this.isRedTurn = true;
        this.gameState = GameState.RUNNING;
        this.listeners = new ArrayList<>();
    }

    // convenience no-arg ctor keeps backward compatibility (uses provider.get())
    public GameEngine() { this(RulesConfigProvider.get()); }

    public void setRulesConfig(GameRulesConfig newConfig) {
        if (newConfig == null) return;
        this.rulesConfig = newConfig;
        if (this.validator != null) this.validator.setRulesConfig(newConfig);
    }

    /**
     * 尝试执行一步棋
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        return makeMove(fromRow, fromCol, toRow, toCol, null, -1);
    }

    /**
     * 尝试执行一步棋（带晋升选项）
     * @param promotionType 如果兵到达底线，晋升为此类型（null表示不晋升或不需要晋升）
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol, Piece.Type promotionType) {
        return makeMove(fromRow, fromCol, toRow, toCol, promotionType, -1);
    }

    /**
     * 尝试执行一步棋（支持从堆栈中选择）
     * @param promotionType 如果兵到达底线，晋升为此类型（null表示不晋升或不需要晋升）
     * @param selectedStackIndex 从堆栈中选择的棋子索引（-1表示不从堆栈选择，使用顶部棋子）
     */
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol, Piece.Type promotionType, int selectedStackIndex) {
        if (gameState != GameState.RUNNING) {
            return false;
        }

        // 如果处于回放模式，需要截断后续的着法记录
        if (isInReplayMode && currentReplayStep >= 0 && currentReplayStep < moveHistory.size()) {
            // 保留前 currentReplayStep 步，删除后面的
            moveHistory = new ArrayList<>(moveHistory.subList(0, currentReplayStep));
            // 退出回放模式
            isInReplayMode = false;
            currentReplayStep = -1;
        }

        // 获取源位置的堆栈
        List<Piece> fromStack = board.getStack(fromRow, fromCol);
        if (fromStack.isEmpty()) {
            return false;
        }

        // 确定要移动的棋子
        Piece piece;
        List<Piece> movedStack = new ArrayList<>(); // 随之移动的堆栈中的其他棋子
        // 背负是否真正启用：必须启用堆叠且最大堆叠数>1 且允许背负
        boolean carryEnabled = RuleSettingsPanel.isEnabled("allowPieceStacking") && this.rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName) > 1
                && RuleSettingsPanel.isEnabled("allowCarryPiecesAbove");

        if (selectedStackIndex >= 0 && selectedStackIndex < fromStack.size()) {
            // 从堆栈中选择特定的棋子
            piece = fromStack.get(selectedStackIndex);
            // 只有在启用背负时，该棋子上方的所有棋子才会跟随移动
            if (carryEnabled) {
                for (int i = selectedStackIndex + 1; i < fromStack.size(); i++) {
                    movedStack.add(fromStack.get(i));
                }
            }
        } else if (selectedStackIndex == -1) {
            // 使用顶部棋子（默认行为）
            piece = board.getPiece(fromRow, fromCol);
        } else {
            // 无效的索引
            return false;
        }

        if (piece == null) {
            return false;
        }

        // 检查是否是当前玩家的棋子
        if (piece.isRed() != isRedTurn) {
            return false;
        }

        // 验证着法合法性（使用选定的棋子进行验证）
        if (!validator.isValidMove(fromRow, fromCol, toRow, toCol, selectedStackIndex)) {
            return false;
        }

        // 执行移动
        Piece capturedPiece = board.getPiece(toRow, toCol);
        boolean convertedCapture = false;

        // 判断是否为堆叠移动
        boolean isStackingMove = false;
        if (this.rulesConfig.getBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName)
                && this.rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName) > 1
                && capturedPiece != null && capturedPiece.isRed() == piece.isRed()) {
            int stackSize = board.getStackSize(toRow, toCol);
            if (stackSize < this.rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName)) {
                isStackingMove = true;
                capturedPiece = null; // 堆叠时不吃子，只堆叠
            }
        }

        // 允许俘虏：吃子改为转换归己方，原棋子保持不动
        Piece convertedPiece = null;
        if (capturedPiece != null && !isStackingMove && this.rulesConfig.getBoolean(RuleRegistry.ALLOW_CAPTURE_CONVERSION.registryName)) {
            Piece.Type targetType = capturedPiece.getType();
            convertedPiece = new Piece(convertPieceTypeToSide(targetType, piece.isRed()), toRow, toCol);
            convertedCapture = true;
            board.setPiece(toRow, toCol, convertedPiece);
        } else if (capturedPiece != null && !isStackingMove) {
            board.removePiece(toRow, toCol);
        }

        boolean movedPiece = !convertedCapture;
        if (movedPiece) {
            // 如果从堆栈中选择了棋子，需要先移除源位置的该棋子及其上方的棋子
            if (selectedStackIndex >= 0) {
                // 如果启用背负，移除选定的棋子及其上方的所有棋子
                if (carryEnabled) {
                    // 计算需要移除的棋子数量（选中的 + 上方的所有）
                    int countToRemove = fromStack.size() - selectedStackIndex;
                    for (int i = 0; i < countToRemove; i++) {
                        board.popTop(fromRow, fromCol);
                    }
                } else {
                    // 模式二：只移除选定的棋子，保留上方的棋子，且不能颠倒顺序
                    // 策略：手动暂存上方的棋子 -> 弹出到底 -> 还原上方的棋子

                    // 1. 暂存上方的棋子（保持顺序）
                    List<Piece> piecesAbove = new ArrayList<>();
                    for (int i = selectedStackIndex + 1; i < fromStack.size(); i++) {
                        piecesAbove.add(fromStack.get(i));
                    }

                    // 2. 连续弹出，直到把选中的那个棋子也弹出来
                    // 比如 Stack [A, B, C, D]，选中 B(index 1)。Size 4.
                    // piecesAbove = [C, D].
                    // 需要弹出 D, C, B。共 4 - 1 = 3 次。
                    int countToPop = fromStack.size() - selectedStackIndex;
                    for (int k = 0; k < countToPop; k++) {
                        board.popTop(fromRow, fromCol);
                    }

                    // 3. 将暂存的上方棋子原样放回去
                    for (Piece p : piecesAbove) {
                        board.pushToStack(fromRow, fromCol, p);
                    }
                }
            } else {
                // 默认移除顶部棋子
                board.removePiece(fromRow, fromCol);
            }

            piece.move(toRow, toCol);
            if (isStackingMove) {
                board.pushToStack(toRow, toCol, piece);
            } else {
                board.setPiece(toRow, toCol, piece);
            }

            // 将随之移动的棋子堆栈也移动到目标位置（仅在启用背负时）
            if (carryEnabled) {
                for (Piece p : movedStack) {
                    p.move(toRow, toCol);
                    board.pushToStack(toRow, toCol, p);
                }
            }
        }

        // 检查兵卒晋升（仅在棋子实际移动时处理）
        if (movedPiece && this.rulesConfig.getBoolean(RuleRegistry.PAWN_PROMOTION.registryName) &&
                (piece.getType() == Piece.Type.RED_SOLDIER || piece.getType() == Piece.Type.BLACK_SOLDIER)) {
            boolean isAtBaseLine = (piece.isRed() && toRow == 0) || (!piece.isRed() && toRow == 9);
            if (isAtBaseLine && promotionType != null) {
                board.setPiece(toRow, toCol, new Piece(promotionType, toRow, toCol));
            }
        }

        Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedPiece);
        if (convertedCapture) {
            move.setCaptureConversion(true);
            move.setConvertedPiece(convertedPiece);
        }
        if (isStackingMove) {
            move.setStacking(true);
            move.setStackBefore(new ArrayList<>(board.getStack(toRow, toCol)));
        }
        if (selectedStackIndex >= 0) {
            move.setSelectedStackIndex(selectedStackIndex);
            move.setMovedStack(new ArrayList<>(movedStack));
        }
        moveHistory.add(move);

        // 切换回合
        isRedTurn = !isRedTurn;

        // 通知监听器
        for (GameStateListener listener : listeners) {
            listener.onMoveExecuted(move);
        }


        // 检查游戏状态
        checkGameState();

        return true;
    }

    /**
     * Check whether a move would be considered valid under current rules (without applying it).
     */
    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        return isValidMove(fromRow, fromCol, toRow, toCol, -1);
    }

    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, int selectedStackIndex) {
        if (gameState != GameState.RUNNING) return false;
        List<Piece> fromStack = board.getStack(fromRow, fromCol);
        if (fromStack == null || fromStack.isEmpty()) return false;
        Piece piece = selectedStackIndex >= 0 && selectedStackIndex < fromStack.size() ? fromStack.get(selectedStackIndex) : board.getPiece(fromRow, fromCol);
        if (piece == null) return false;
        // ensure current-turn ownership
        if (piece.isRed() != isRedTurn) return false;
        try {
            return validator.isValidMove(fromRow, fromCol, toRow, toCol, selectedStackIndex);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Force-apply a move without local validation. Intended to be used when peer confirms authority.
     */
    public boolean forceApplyMove(int fromRow, int fromCol, int toRow, int toCol) {
        return forceApplyMove(fromRow, fromCol, toRow, toCol, null, -1);
    }

    public boolean forceApplyMove(int fromRow, int fromCol, int toRow, int toCol, Piece.Type promotionType, int selectedStackIndex) {
        if (gameState != GameState.RUNNING) return false;

        if (isInReplayMode && currentReplayStep >= 0 && currentReplayStep < moveHistory.size()) {
            moveHistory = new ArrayList<>(moveHistory.subList(0, currentReplayStep));
            isInReplayMode = false;
            currentReplayStep = -1;
        }

        List<Piece> fromStack = board.getStack(fromRow, fromCol);
        if (fromStack == null || fromStack.isEmpty()) return false;

        Piece piece;
        List<Piece> movedStack = new ArrayList<>();
        boolean carryEnabled = RuleSettingsPanel.isEnabled("allowPieceStacking") && this.rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName) > 1
                && RuleSettingsPanel.isEnabled("allowCarryPiecesAbove");

        if (selectedStackIndex >= 0 && selectedStackIndex < fromStack.size()) {
            piece = fromStack.get(selectedStackIndex);
            if (carryEnabled) {
                for (int i = selectedStackIndex + 1; i < fromStack.size(); i++) movedStack.add(fromStack.get(i));
            }
        } else if (selectedStackIndex == -1) {
            piece = board.getPiece(fromRow, fromCol);
        } else return false;

        if (piece == null) return false;

        Piece capturedPiece = board.getPiece(toRow, toCol);
        boolean convertedCapture = false;
        Piece convertedPiece = null;

        boolean isStackingMove = false;
        if (this.rulesConfig.getBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName)
                && this.rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName) > 1
                && capturedPiece != null && capturedPiece.isRed() == piece.isRed()) {
            int stackSize = board.getStackSize(toRow, toCol);
            if (stackSize < this.rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName)) {
                isStackingMove = true;
                capturedPiece = null;
            }
        }

        if (capturedPiece != null && !isStackingMove && this.rulesConfig.getBoolean(RuleRegistry.ALLOW_CAPTURE_CONVERSION.registryName)) {
            Piece.Type targetType = capturedPiece.getType();
            convertedPiece = new Piece(convertPieceTypeToSide(targetType, piece.isRed()), toRow, toCol);
            convertedCapture = true;
            board.setPiece(toRow, toCol, convertedPiece);
        } else if (capturedPiece != null && !isStackingMove) {
            board.removePiece(toRow, toCol);
        }

        boolean movedPiece = !convertedCapture;
        if (movedPiece) {
            if (selectedStackIndex >= 0) {
                if (carryEnabled) {
                    int countToRemove = fromStack.size() - selectedStackIndex;
                    for (int i = 0; i < countToRemove; i++) board.popTop(fromRow, fromCol);
                } else {
                    List<Piece> piecesAbove = new ArrayList<>();
                    for (int i = selectedStackIndex + 1; i < fromStack.size(); i++) piecesAbove.add(fromStack.get(i));
                    int countToPop = fromStack.size() - selectedStackIndex;
                    for (int k = 0; k < countToPop; k++) board.popTop(fromRow, fromCol);
                    for (Piece p : piecesAbove) board.pushToStack(fromRow, fromCol, p);
                }
            } else {
                board.removePiece(fromRow, fromCol);
            }

            piece.move(toRow, toCol);
            if (isStackingMove) board.pushToStack(toRow, toCol, piece);
            else board.setPiece(toRow, toCol, piece);

            if (carryEnabled) {
                for (Piece p : movedStack) {
                    p.move(toRow, toCol);
                    board.pushToStack(toRow, toCol, p);
                }
            }
        }

        if (movedPiece && this.rulesConfig.getBoolean(RuleRegistry.PAWN_PROMOTION.registryName)
                && (piece.getType() == Piece.Type.RED_SOLDIER || piece.getType() == Piece.Type.BLACK_SOLDIER)) {
            boolean isAtBaseLine = (piece.isRed() && toRow == 0) || (!piece.isRed() && toRow == 9);
            if (isAtBaseLine && promotionType != null) board.setPiece(toRow, toCol, new Piece(promotionType, toRow, toCol));
        }

        Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedPiece);
        if (convertedCapture) { move.setCaptureConversion(true); move.setConvertedPiece(convertedPiece); }
        if (isStackingMove) { move.setStacking(true); move.setStackBefore(new ArrayList<>(board.getStack(toRow, toCol))); }
        if (selectedStackIndex >= 0) { move.setSelectedStackIndex(selectedStackIndex); move.setMovedStack(new ArrayList<>(movedStack)); }
        move.setForceMove(true); // 标记为强制走子
        moveHistory.add(move);

        isRedTurn = !isRedTurn;
        for (GameStateListener listener : listeners) listener.onMoveExecuted(move);
        checkGameState();
        return true;
    }

    /**
     * 检查兵卒是否需要晋升
     */
    public boolean needsPromotion(int row, int col) {
        if (!this.rulesConfig.getBoolean(RuleRegistry.PAWN_PROMOTION.registryName)) {
            return false;
        }
        Piece piece = board.getPiece(row, col);
        if (piece == null) {
            return false;
        }
        if (piece.getType() != Piece.Type.RED_SOLDIER && piece.getType() != Piece.Type.BLACK_SOLDIER) {
            return false;
        }
        // 红兵到达第0行（黑方底线），黑卒到达第9行（红方底线）
        return (piece.isRed() && row == 0) || (!piece.isRed() && row == 9);
    }

    /**
     * 检查游戏状态（是否有一方被将死）
     */
    private void checkGameState() {
        // 获取两个王
        Piece redKing = board.getRedKing();
        Piece blackKing = board.getBlackKing();

        if (redKing == null) {
            gameState = GameState.RED_CHECKMATE;  // 红王被吃 → 红方败 → RED_CHECKMATE
            notifyGameStateChanged();
            return;
        }

        if (blackKing == null) {
            gameState = GameState.BLACK_CHECKMATE;  // 黑王被吃 → 黑方败 → BLACK_CHECKMATE
            notifyGameStateChanged();
            return;
        }

        // 检查黑方是否被将死
        if (checkDetector.isCheckmate(false)) {
            gameState = GameState.BLACK_CHECKMATE;  // 黑方被将死 → 黑方败
            notifyGameStateChanged();
            return;
        }

        // 检查红方是否被将死
        if (checkDetector.isCheckmate(true)) {
            gameState = GameState.RED_CHECKMATE;  // 红方被将死 → 红方败
            notifyGameStateChanged();
            return;
        }
    }

    private void notifyGameStateChanged() {
        for (GameStateListener listener : listeners) {
            listener.onGameStateChanged(gameState);
        }
    }

    /**
     * 撤销上一步棋
     */
    public boolean undoLastMove() {
        if (!this.rulesConfig.getBoolean(RuleRegistry.ALLOW_UNDO.registryName)) {
            return false;
        }
        if (moveHistory.isEmpty()) {
            return false;
        }

        Move lastMove = moveHistory.remove(moveHistory.size() - 1);

        // 处理从堆栈中选择的棋子的撤销
        if (lastMove.getSelectedStackIndex() >= 0) {
            // 从目标位置移除该棋子及其跟随的棋子
            Piece piece = lastMove.getPiece();
            List<Piece> movedStack = lastMove.getMovedStack();

            // 先移除跟随的棋子（逆序）
            if (movedStack != null) {
                for (int i = movedStack.size() - 1; i >= 0; i--) {
                    board.popTop(lastMove.getToRow(), lastMove.getToCol());
                }
            }
            // 移除主棋子
            board.popTop(lastMove.getToRow(), lastMove.getToCol());

            // 必须考虑源位置可能已经有其他棋子（虽然在当前规则下，源位置被抽走后，剩下的棋子会落下）
            // 但是我们要插回到正确的位置

            // 获取当前源位置的堆栈
            // Stack: [A, C]. Removed B (Index 1).
            // undo needs to put B back at Index 1.
            // Result: [A, B, C].

            // 策略：弹出所有 index >= selectedStackIndex 的棋子，放入 B，再放回。
            int targetIndex = lastMove.getSelectedStackIndex();
            List<Piece> currentStack = board.getStack(lastMove.getFromRow(), lastMove.getFromCol());
            List<Piece> piecesAbove = new ArrayList<>();

            // 弹出目前在该位置之上的所有棋子
            int countToPop = currentStack.size() - targetIndex;
            for(int k=0; k<countToPop; k++) {
                // popTop 返回的是被移除的棋子，我们假设 Board 没有返回它，所以要先 get
                // 但是 popTop 逻辑依赖 board 实现。
                // 既然我们在 move 时手动管理了顺序，undo 时如果仅仅是 push 回去可能变成 [A, C, B]。
                // 所以这里也需要手动重组。
                Piece p = currentStack.get(currentStack.size() - 1 - k);
                piecesAbove.add(0, p); // 保持顺序插入头部
            }
            // 执行真正的 pop
            for(int k=0; k<countToPop; k++) {
                board.popTop(lastMove.getFromRow(), lastMove.getFromCol());
            }

            // 将主棋子移回原位置
            piece.move(lastMove.getFromRow(), lastMove.getFromCol());
            board.pushToStack(lastMove.getFromRow(), lastMove.getFromCol(), piece);

            // 将跟随的棋子也移回原位置 (如果是 Carry Mode)
            if (movedStack != null) {
                for (Piece p : movedStack) {
                    p.move(lastMove.getFromRow(), lastMove.getFromCol());
                    board.pushToStack(lastMove.getFromRow(), lastMove.getFromCol(), p);
                }
            }

            // 将原来在上面的棋子放回去 (如果是 Extract Mode，上面可能有 C)
            for(Piece p : piecesAbove) {
                board.pushToStack(lastMove.getFromRow(), lastMove.getFromCol(), p);
            }
        }
        // 处理堆叠撤销
        else if (lastMove.isStacking()) {
            // 从目标位置弹出该棋子
            board.popTop(lastMove.getToRow(), lastMove.getToCol());
            // 将棋子放回原位置
            Piece piece = lastMove.getPiece();
            piece.move(lastMove.getFromRow(), lastMove.getFromCol());
            board.setPiece(lastMove.getFromRow(), lastMove.getFromCol(), piece);
        } else {
            if (lastMove.isCaptureConversion()) {
                // 移除转换后的棋子，恢复被俘棋子，原棋子保持不动
                board.setPiece(lastMove.getToRow(), lastMove.getToCol(), null);
                if (lastMove.getCapturedPiece() != null) {
                    board.setPiece(lastMove.getToRow(), lastMove.getToCol(), lastMove.getCapturedPiece());
                }
            } else {
                Piece piece = lastMove.getPiece();
                piece.move(lastMove.getFromRow(), lastMove.getFromCol());
                board.setPiece(lastMove.getFromRow(), lastMove.getFromCol(), piece);
                board.setPiece(lastMove.getToRow(), lastMove.getToCol(), null);

                if (lastMove.getCapturedPiece() != null) {
                    Piece captured = lastMove.getCapturedPiece();
                    board.setPiece(lastMove.getToRow(), lastMove.getToCol(), captured);
                }
            }
        }

        // 切换回合
        isRedTurn = !isRedTurn;

        // 恢复游戏状态
        gameState = GameState.RUNNING;
        notifyGameStateChanged();

        return true;
    }

    /**
     * 撤销最近一步棋（悔棋），支持堆叠和吃子恢复
     */
    public boolean undoMove() {
        if (moveHistory == null || moveHistory.isEmpty()) return false;
        Move lastMove = moveHistory.remove(moveHistory.size() - 1);
        int fromRow = lastMove.getFromRow();
        int fromCol = lastMove.getFromCol();
        int toRow = lastMove.getToRow();
        int toCol = lastMove.getToCol();
        Piece movedPiece = lastMove.getPiece();
        Piece capturedPiece = lastMove.getCapturedPiece();
        boolean isStacking = lastMove.isStacking();
        List<Piece> stackBefore = lastMove.getStackBefore();
        int selectedStackIndex = lastMove.getSelectedStackIndex();
        List<Piece> movedStack = lastMove.getMovedStack();
        boolean captureConversion = lastMove.isCaptureConversion();
        Piece convertedPiece = lastMove.getConvertedPiece();

        // 1. 移除目标位置的棋子（包括堆叠/转换后的棋子）
        board.clearStack(toRow, toCol);

        // 2. 恢复堆叠状态
        if (isStacking && stackBefore != null) {
            for (Piece p : stackBefore) {
                board.pushToStack(toRow, toCol, new Piece(p.getType(), toRow, toCol));
            }
        } else if (captureConversion && convertedPiece != null && convertedPiece != null) {
            // 恢复被吃棋子（俘虏转换）
            board.setPiece(toRow, toCol, capturedPiece.copyAt(toRow, toCol));
        } else if (capturedPiece != null) {
            // 恢复被吃棋子
            board.setPiece(toRow, toCol, capturedPiece.copyAt(toRow, toCol));
        }

        // 3. 恢复源位置的棋子（包括堆叠/背负）
        if (selectedStackIndex >= 0 && movedStack != null && !movedStack.isEmpty()) {
            // 恢复背负棋子
            board.pushToStack(fromRow, fromCol, movedPiece.copyAt(fromRow, fromCol));
            for (Piece p : movedStack) {
                board.pushToStack(fromRow, fromCol, p.copyAt(fromRow, fromCol));
            }
        } else {
            board.setPiece(fromRow, fromCol, movedPiece.copyAt(fromRow, fromCol));
        }

        // 4. 切换回合
        isRedTurn = !isRedTurn;

        // 5. 通知监听器
        for (GameStateListener listener : listeners) {
            listener.onMoveExecuted(null); // 可自定义撤销通知
        }
        return true;
    }

    /**
     * 重新开始游戏
     */
    public void restart() {
        board.reset();
        moveHistory.clear();
        ruleChangeHistory.clear();
        isRedTurn = true;
        gameState = GameState.RUNNING;
        // 清理回放相关缓存，避免回放残留影响新对局
        savedInitialBoard = null;
        savedInitialIsRedTurn = true;
        isInReplayMode = false;
        currentReplayStep = -1;
        notifyGameStateChanged();
    }

    public Board getBoard() {
        return board;
    }

    public List<Move> getMoveHistory() {
        return new ArrayList<>(moveHistory);
    }

    public void clearMoveHistory() {
        moveHistory.clear();
    }

    public void clearRuleChangeHistory() {
        ruleChangeHistory.clear();
    }

    public void addMoveToHistory(Move move) {
        moveHistory.add(move);
    }

    public void addRuleChangeToHistory(RuleChangeRecord ruleChange) {
        ruleChangeHistory.add(ruleChange);
    }

    public List<RuleChangeRecord> getRuleChangeHistory() {
        return new ArrayList<>(ruleChangeHistory);
    }

    /**
     * 获取合并的历史记录（着法和玩法变更）
     * @return 合并后的历史记录列表，按时间顺序排列
     */
    public List<HistoryItem> getCombinedHistory() {
        List<HistoryItem> combined = new ArrayList<>();
        int moveIndex = 0;
        int ruleIndex = 0;

        // 先插入所有 afterMoveIndex == -1 的规则变动（即走第一颗子前的变动）
        while (ruleIndex < ruleChangeHistory.size()) {
            RuleChangeRecord ruleChange = ruleChangeHistory.get(ruleIndex);
            if (ruleChange.getAfterMoveIndex() == -1) {
                combined.add(ruleChange);
                ruleIndex++;
            } else {
                break;
            }
        }

        while (moveIndex < moveHistory.size() || ruleIndex < ruleChangeHistory.size()) {
            // 插入所有 afterMoveIndex < moveIndex 且 != -1 的规则变动
            while (ruleIndex < ruleChangeHistory.size()) {
                RuleChangeRecord ruleChange = ruleChangeHistory.get(ruleIndex);
                if (ruleChange.getAfterMoveIndex() != -1 && ruleChange.getAfterMoveIndex() < moveIndex) {
                    combined.add(ruleChange);
                    ruleIndex++;
                } else {
                    break;
                }
            }

            // 插入当前 move
            if (moveIndex < moveHistory.size()) {
                combined.add(moveHistory.get(moveIndex));
                moveIndex++;
            }
        }

        // 插入剩余规则变动
        while (ruleIndex < ruleChangeHistory.size()) {
            combined.add(ruleChangeHistory.get(ruleIndex));
            ruleIndex++;
        }

        return combined;
    }

    /**
     * 保存当前棋盘状态作为回放起点（用于导入残局后）
     */
    public void saveInitialStateForReplay() {
        savedInitialBoard = board.deepCopy();
        savedInitialIsRedTurn = isRedTurn;
    }

    /**
     * 重建棋盘到指定步数（用于回放）
     * @param step 步数，0表示初始状态，n表示执行了前n步
     */
    public void rebuildBoardToStep(int step) {
        if (savedInitialBoard == null) {
            return; // 没有保存的初始状态，无法回放
        }

        // 清空当前棋盘
        board.clearBoard();

        // 从保存的初始状态复制棋子
        for (int row = 0; row < board.getRows(); row++) {
            for (int col = 0; col < board.getCols(); col++) {
                Piece piece = savedInitialBoard.getPiece(row, col);
                if (piece != null) {
                    Piece pieceCopy = new Piece(piece.getType(), row, col);
                    board.putPieceFresh(row, col, pieceCopy); // 同步棋子列表
                }
            }
        }

        // 恢复初始轮次
        isRedTurn = savedInitialIsRedTurn;

        // 执行前step步着法
        List<Move> moves = getMoveHistory();
        for (int i = 0; i < step && i < moves.size(); i++) {
            Move move = moves.get(i);

            Piece piece = board.getPiece(move.getFromRow(), move.getFromCol());
            if (piece == null) continue;

            Piece targetPiece = board.getPiece(move.getToRow(), move.getToCol());
            if (targetPiece != null) {
                board.removePiece(move.getToRow(), move.getToCol());
            }

            if (move.isCaptureConversion() && move.getConvertedPiece() != null) {
                // 原棋子不动，只在目标格放置转换后的己方棋子
                board.setPiece(move.getToRow(), move.getToCol(), new Piece(move.getConvertedPiece().getType(), move.getToRow(), move.getToCol()));
            } else {
                piece.move(move.getToRow(), move.getToCol());
                board.setPiece(move.getToRow(), move.getToCol(), piece);
                board.setPiece(move.getFromRow(), move.getFromCol(), null);
            }

            isRedTurn = !isRedTurn;
        }

        gameState = GameState.RUNNING;
    }

    /**
     * 设置回放模式状态
     */
    public void setReplayMode(boolean inReplayMode, int step) {
        this.isInReplayMode = inReplayMode;
        this.currentReplayStep = step;
    }

    /**
     * 获取是否处于回放模式
     */
    public boolean isInReplayMode() {
        return isInReplayMode;
    }

    /**
     * 获取当前回放步数
     */
    public int getCurrentReplayStep() {
        return currentReplayStep;
    }

    public boolean isRedTurn() {
        return isRedTurn;
    }

    public void setRedTurn(boolean isRedTurn) {
        this.isRedTurn = isRedTurn;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void addGameStateListener(GameStateListener listener) {
        listeners.add(listener);
    }

    public void removeGameStateListener(GameStateListener listener) {
        listeners.remove(listener);
    }

    // NOTE: direct access to rulesConfig should be used by callers (getBoolean/getInt/toJson).
    /**
     * 获取设置快照（供联机同步）
     */
    public JsonObject getSettingsSnapshot() {
        return this.rulesConfig.toJson();
    }

    /**
     * 应用设置快照（供联机同步）
     */
    public void applySettingsSnapshot(JsonObject snapshot) {
        if (snapshot == null) return;
        // apply per-key so listeners receive NETWORK as the change source
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : snapshot.entrySet()) {
            String key = e.getKey();
            com.google.gson.JsonElement el = e.getValue();
            if (el == null || el.isJsonNull()) {
                this.rulesConfig.set(key, null, GameRulesConfig.ChangeSource.NETWORK);
            } else if (el.isJsonPrimitive()) {
                com.google.gson.JsonPrimitive p = el.getAsJsonPrimitive();
                if (p.isBoolean()) {
                    this.rulesConfig.set(key, p.getAsBoolean(), GameRulesConfig.ChangeSource.NETWORK);
                } else if (p.isNumber()) {
                    // most rule numbers are ints
                    try {
                        int iv = p.getAsInt();
                        this.rulesConfig.set(key, iv, GameRulesConfig.ChangeSource.NETWORK);
                    } catch (NumberFormatException ex) {
                        this.rulesConfig.set(key, p.getAsString(), GameRulesConfig.ChangeSource.NETWORK);
                    }
                } else if (p.isString()) {
                    this.rulesConfig.set(key, p.getAsString(), GameRulesConfig.ChangeSource.NETWORK);
                }
            } else {
                // ignore complex types for now
            }
        }
        // refresh validator reference
        validator.setRulesConfig(this.rulesConfig);
    }

    /**
     * 获取规则配置对象
     */
    public GameRulesConfig getRulesConfig() {
        // return the engine's configured rulesConfig (injected or updated via provider replace)
        return this.rulesConfig;
    }

    /**
     * Graceful shutdown for engine-managed resources. Currently it will shutdown the rules change notifier.
     * This centralizes resource cleanup so callers (UI, JVM shutdown hooks) can call a single method.
     */
     public void shutdown() {
         try {
            // Unregister provider listener to avoid leaks
            RulesConfigProvider.removeInstanceChangeListener(providerListener);
         } catch (Throwable ignored) {}
         // future: close other engine-managed resources here
     }

    private Piece.Type convertPieceTypeToSide(Piece.Type type, boolean toRed) {
        switch (type) {
            case RED_KING:
            case BLACK_KING:
                return toRed ? Piece.Type.RED_KING : Piece.Type.BLACK_KING;
            case RED_ADVISOR:
            case BLACK_ADVISOR:
                return toRed ? Piece.Type.RED_ADVISOR : Piece.Type.BLACK_ADVISOR;
            case RED_ELEPHANT:
            case BLACK_ELEPHANT:
                return toRed ? Piece.Type.RED_ELEPHANT : Piece.Type.BLACK_ELEPHANT;
            case RED_HORSE:
            case BLACK_HORSE:
                return toRed ? Piece.Type.RED_HORSE : Piece.Type.BLACK_HORSE;
            case RED_CHARIOT:
            case BLACK_CHARIOT:
                return toRed ? Piece.Type.RED_CHARIOT : Piece.Type.BLACK_CHARIOT;
            case RED_CANNON:
            case BLACK_CANNON:
                return toRed ? Piece.Type.RED_CANNON : Piece.Type.BLACK_CANNON;
            case RED_SOLDIER:
            case BLACK_SOLDIER:
                return toRed ? Piece.Type.RED_SOLDIER : Piece.Type.BLACK_SOLDIER;
            default: return type;
        }
    }

    /**
     * 获取完整的游戏状态快照（用于联机同步）
     */
    public JsonObject getSyncState() {
        JsonObject root = new JsonObject();

        // 1. 基础状态
        root.addProperty("isRedTurn", isRedTurn);
        root.addProperty("gameState", gameState.name());

        // 2. 规则配置
        root.add("rulesConfig", rulesConfig.toJson());

        // 3. 历史记录 (着法)
        // 使用 Gson 直接序列化 List<Move>
        root.add("moveHistory", gson.toJsonTree(moveHistory));

        // 4. 规则变更记录
        root.add("ruleChangeHistory", gson.toJsonTree(ruleChangeHistory));

        // 5. 棋盘上的棋子 (包含堆叠信息)
        JsonArray boardArray = new JsonArray();
        for (int r = 0; r < board.getRows(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                List<Piece> stack = board.getStack(r, c);
                if (stack != null && !stack.isEmpty()) {
                    JsonObject cellObj = new JsonObject();
                    cellObj.addProperty("row", r);
                    cellObj.addProperty("col", c);

                    JsonArray stackArr = new JsonArray();
                    for (Piece p : stack) {
                        // 只需要保存类型，因为 Piece(Type, r, c) 构造函数会根据 Type 确定颜色
                        stackArr.add(p.getType().name());
                    }
                    cellObj.add("stack", stackArr);
                    boardArray.add(cellObj);
                }
            }
        }
        root.add("boardData", boardArray);

        return root;
    }

    /**
     * 从快照中恢复游戏状态（覆盖当前状态）
     * 注意：移除了内部 try-catch，将异常抛出给调用者处理
     */
    public void loadSyncState(JsonObject root) {
        if (root == null) return;

        // 1. 恢复规则配置
        if (root.has("rulesConfig")) {
            applySettingsSnapshot(root.getAsJsonObject("rulesConfig"));
        }

        // 2. 恢复基础状态
        if (root.has("isRedTurn")) {
            this.isRedTurn = root.get("isRedTurn").getAsBoolean();
        }
        if (root.has("gameState")) {
            try {
                this.gameState = GameState.valueOf(root.get("gameState").getAsString());
            } catch (Exception e) {
                this.gameState = GameState.RUNNING;
            }
        }

        // 3. 恢复历史记录
        this.moveHistory.clear();
        if (root.has("moveHistory")) {
            JsonArray moves = root.getAsJsonArray("moveHistory");
            for (JsonElement el : moves) {
                Move m = gson.fromJson(el, Move.class);
                this.moveHistory.add(m);
            }
        }

        // 4. 恢复规则变更记录
        this.ruleChangeHistory.clear();
        if (root.has("ruleChangeHistory")) {
            JsonArray changes = root.getAsJsonArray("ruleChangeHistory");
            for (JsonElement el : changes) {
                RuleChangeRecord r = gson.fromJson(el, RuleChangeRecord.class);
                this.ruleChangeHistory.add(r);
            }
        }

        // 5. 恢复棋盘
        // === 修改点：安全清空棋盘，不依赖 board.clearBoard() ===
        // 假设 board.removePiece 会移除该位置的一个棋子（或顶部棋子）
        // 我们循环调用直到该位置为空
        for (int r = 0; r < board.getRows(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                while (board.getPiece(r, c) != null) {
                    // 使用 removePiece 逐个清理
                    board.removePiece(r, c);
                }
            }
        }

        if (root.has("boardData")) {
            JsonArray boardData = root.getAsJsonArray("boardData");
            for (JsonElement el : boardData) {
                JsonObject cell = el.getAsJsonObject();
                int r = cell.get("row").getAsInt();
                int c = cell.get("col").getAsInt();
                JsonArray stackArr = cell.getAsJsonArray("stack");

                // 重建堆栈：按顺序放入
                // stackArr[0] 是底部，stackArr[last] 是顶部
                for (int i = 0; i < stackArr.size(); i++) {
                    String typeName = stackArr.get(i).getAsString();
                    Piece.Type type = Piece.Type.valueOf(typeName);
                    Piece piece = new Piece(type, r, c);

                    if (i == 0) {
                        board.setPiece(r, c, piece);
                    } else {
                        board.pushToStack(r, c, piece);
                    }
                }
            }
        }

        // 6. === 新增：重置回放系统的初始状态 ===
        // 为了让加入方能够正常回放历史，我们需要假定一个初始状态。
        // 如果是标准开局，我们创建一个新的标准棋盘作为回放起点。
        // 这样 rebuildBoardToStep(0) 就能工作了。
        Board standardBoard = new Board(); // 创建一个标准初始棋盘
        this.savedInitialBoard = standardBoard.deepCopy();
        this.savedInitialIsRedTurn = true;
        this.isInReplayMode = false;
        this.currentReplayStep = -1;

        // 刷新验证器状态
        this.validator.setRulesConfig(this.rulesConfig);

        // 通知监听器 UI 更新
        notifyGameStateChanged();
    }
}
