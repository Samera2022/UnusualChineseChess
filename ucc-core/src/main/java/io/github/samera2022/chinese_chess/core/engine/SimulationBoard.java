package io.github.samera2022.chinese_chess.core.engine;

import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.common.spi.SimulationContext;
import io.github.samera2022.chinese_chess.core.rules.MoveValidator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 模拟棋盘 —— 用于 AI 搜索树的增量棋盘模拟。
 * <p>
 * 继承 {@link Board}（共享棋盘数据结构和操作方法），
 * 实现 {@link SimulationContext} 接口，为搜索算法提供轻量级的
 * 走子/撤消能力。通过内部类 {@link UndoRecord} 记录每步操作的
 * 完整状态，实现 O(1) 级撤消，避免每次 {@code deepCopy()} 的
 * 全量深拷贝开销。
 * </p>
 */
public class SimulationBoard extends Board implements SimulationContext {

    /** 走子合法性校验器 */
    private final MoveValidator validator;

    /** 增量撤消栈：最近一步在栈顶 */
    private final Deque<UndoRecord> undoStack = new ArrayDeque<>();

    /** 已执行的模拟走子历史 */
    private final List<Move> simulatedMoves = new ArrayList<>();

    /**
     * 从源 {@link Board} 深拷贝构造模拟棋盘。
     * <p>
     * 遍历源棋盘的所有位置和堆栈，为每个棋子创建新的 {@link Piece}
     * 拷贝并推入对应位置，同时复制回合状态。
     * </p>
     *
     * @param source 源棋盘，不可为 {@code null}
     */
    public SimulationBoard(Board source) {
        super(source.getRows(), false);
        // 深拷贝所有棋子及堆栈
        for (int r = 0; r < source.getRows(); r++) {
            for (int c = 0; c < source.getCols(); c++) {
                List<Piece> stack = source.getStack(r, c);
                for (Piece p : stack) {
                    Piece copy = new Piece(p.getType(), r, c);
                    pushToStack(r, c, copy);
                }
            }
        }
        // 复制回合状态
        this.turn = source.turn;
        // 初始化走子校验器（使用 this 自身作为棋盘引用）
        this.validator = new MoveValidator(this);
    }

    // ──────────────── SimulationContext 实现 ────────────────

    @Override
    public ReadonlyBoard getBoard() {
        return this;
    }

    @Override
    public boolean simulateMove(int fromRow, int fromCol, int toRow, int toCol) {
        Piece piece = getPiece(fromRow, fromCol);
        if (piece == null) {
            return false;
        }

        // 记录目标位置原有状态
        Piece capturedTop = getPiece(toRow, toCol);
        List<Piece> capturedStack = capturedTop != null
                ? new ArrayList<>(getStack(toRow, toCol))
                : Collections.emptyList();

        // 保存撤消记录
        UndoRecord record = new UndoRecord();
        record.fromRow = fromRow;
        record.fromCol = fromCol;
        record.toRow = toRow;
        record.toCol = toCol;
        record.movedPiece = piece;
        record.capturedTop = capturedTop;
        record.capturedStack = capturedStack;
        undoStack.push(record);

        // 执行走子：移除源位置棋子
        removePiece(fromRow, fromCol);
        // 将棋子移动到目标位置
        piece.move(toRow, toCol);
        setPiece(toRow, toCol, piece);

        // 记录走子历史
        Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedTop);
        simulatedMoves.add(move);

        // 翻转回合
        flipTurn();
        return true;
    }

    @Override
    public boolean simulateUndo() {
        if (undoStack.isEmpty()) {
            return false;
        }

        UndoRecord record = undoStack.pop();

        // 移除目标位置的移动棋子
        removePiece(record.toRow, record.toCol);

        // 将移动棋子恢复到源位置
        record.movedPiece.move(record.fromRow, record.fromCol);
        pushToStack(record.fromRow, record.fromCol, record.movedPiece);

        // 恢复目标位置原有的堆栈（如果存在）
        if (!record.capturedStack.isEmpty()) {
            for (Piece p : record.capturedStack) {
                Piece restored = new Piece(p.getType(), record.toRow, record.toCol);
                pushToStack(record.toRow, record.toCol, restored);
            }
        }

        // 移除最后一条走子历史
        if (!simulatedMoves.isEmpty()) {
            simulatedMoves.remove(simulatedMoves.size() - 1);
        }

        // 翻转回合
        flipTurn();
        return true;
    }

    @Override
    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        return validator.isValidMove(fromRow, fromCol, toRow, toCol);
    }

    @Override
    public boolean isRedTurn() {
        return turn;
    }

    @Override
    public List<Move> getSimulatedMoves() {
        return new ArrayList<>(simulatedMoves);
    }

    @Override
    public SimulationContext fork() {
        return new SimulationBoard(this);
    }

    /**
     * 启发式局面评估。
     * <p>
     * 采用简单子力优势计算：红方子力总和减去黑方子力总和。
     * 正值表示红方优势，负值表示黑方优势。
     * </p>
     *
     * @return 评估分值
     */
    @Override
    public int evaluate() {
        int score = 0;
        for (int r = 0; r < getRows(); r++) {
            for (int c = 0; c < getCols(); c++) {
                Piece piece = getPiece(r, c);
                if (piece == null) continue;
                int value = getPieceValue(piece);
                score += piece.isRed() ? value : -value;
            }
        }
        return score;
    }

    /**
     * 生成当前回合方的所有合法走法。
     * <p>
     * 遍历整个棋盘，对每个属于当前回合方的棋子，
     * 遍历所有目标位置，通过 {@link #isValidMove} 检查合法性。
     * </p>
     *
     * @return 合法走法列表（可能为空）
     */
    @Override
    public List<Move> generateLegalMoves() {
        List<Move> moves = new ArrayList<>();
        boolean currentTurnIsRed = isRedTurn();

        for (int fr = 0; fr < getRows(); fr++) {
            for (int fc = 0; fc < getCols(); fc++) {
                Piece piece = getPiece(fr, fc);
                if (piece == null || piece.isRed() != currentTurnIsRed) {
                    continue;
                }

                // 遍历所有可能的目标位置
                for (int tr = 0; tr < getRows(); tr++) {
                    for (int tc = 0; tc < getCols(); tc++) {
                        if (fr == tr && fc == tc) continue;
                        if (!isValidMove(fr, fc, tr, tc)) continue;

                        Piece capturedPiece = getPiece(tr, tc);
                        Move move = new Move(fr, fc, tr, tc, piece, capturedPiece);
                        moves.add(move);
                    }
                }
            }
        }
        return moves;
    }

    // ──────────────── 内部辅助方法 ────────────────

    /**
     * 返回棋子的基础子力价值。
     */
    private static int getPieceValue(Piece piece) {
        switch (piece.getType()) {
            case RED_KING:
            case BLACK_KING:
                return 10000;
            case RED_CHARIOT:
            case BLACK_CHARIOT:
                return 900;
            case RED_CANNON:
            case BLACK_CANNON:
                return 450;
            case RED_HORSE:
            case BLACK_HORSE:
                return 400;
            case RED_ELEPHANT:
            case BLACK_ELEPHANT:
                return 200;
            case RED_ADVISOR:
            case BLACK_ADVISOR:
                return 200;
            case RED_SOLDIER:
            case BLACK_SOLDIER:
                return 100;
            default:
                return 0;
        }
    }

    // ──────────────── 内部类：UndoRecord ────────────────

    /**
     * 撤消记录 —— 保存一步走子前的完整状态，用于增量撤消。
     * <p>
     * 记录走子的源/目标坐标、移动的棋子（引用）、目标位置原有棋子
     * 以及完整的原有堆栈。撤消时可按此信息精确恢复。
     * </p>
     */
    static final class UndoRecord {
        int fromRow;
        int fromCol;
        int toRow;
        int toCol;
        Piece movedPiece;
        Piece capturedTop;
        List<Piece> capturedStack;
    }
}
