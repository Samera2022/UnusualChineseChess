package io.github.samera2022.chinese_chess.core.engine;

import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.common.spi.SimulationContext;
import io.github.samera2022.chinese_chess.core.rules.MoveValidator;
import io.github.samera2022.chinese_chess.core.rules.RulesConfigProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class SimulationBoard extends Board implements SimulationContext {

    private final MoveValidator validator;
    private final Deque<UndoRecord> undoStack = new ArrayDeque<>();
    private final List<Move> simulatedMoves = new ArrayList<>();

    public SimulationBoard(Board source) {
        super(source.getRows(), false);
        for (int r = 0; r < source.getRows(); r++) {
            for (int c = 0; c < source.getCols(); c++) {
                List<Piece> stack = source.getStack(r, c);
                for (Piece p : stack) {
                    Piece copy = new Piece(p.getType(), r, c);
                    pushToStack(r, c, copy);
                }
            }
        }
        this.turn = source.turn;
        this.validator = new MoveValidator(this, RulesConfigProvider.get());
    }

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

        Piece capturedTop = getPiece(toRow, toCol);
        List<Piece> capturedStack = capturedTop != null
                ? new ArrayList<>(getStack(toRow, toCol))
                : Collections.emptyList();

        UndoRecord record = new UndoRecord();
        record.fromRow = fromRow;
        record.fromCol = fromCol;
        record.toRow = toRow;
        record.toCol = toCol;
        record.movedPiece = piece;
        record.capturedTop = capturedTop;
        record.capturedStack = capturedStack;
        undoStack.push(record);

        removePiece(fromRow, fromCol);
        piece.move(toRow, toCol);
        setPiece(toRow, toCol, piece);

        Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedTop);
        simulatedMoves.add(move);

        flipTurn();
        return true;
    }

    @Override
    public boolean simulateUndo() {
        if (undoStack.isEmpty()) {
            return false;
        }

        UndoRecord record = undoStack.pop();

        removePiece(record.toRow, record.toCol);

        record.movedPiece.move(record.fromRow, record.fromCol);
        pushToStack(record.fromRow, record.fromCol, record.movedPiece);

        if (!record.capturedStack.isEmpty()) {
            for (Piece p : record.capturedStack) {
                Piece restored = new Piece(p.getType(), record.toRow, record.toCol);
                pushToStack(record.toRow, record.toCol, restored);
            }
        }

        if (!simulatedMoves.isEmpty()) {
            simulatedMoves.remove(simulatedMoves.size() - 1);
        }

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
     * 检查当前规则配置是否为纯标准规则。
     * 任何扩展规则开启时返回 false，此时 generateLegalMoves 回退到全遍历以确保正确性。
     */
    private boolean isStandardRulesOnly() {
        // 检查所有会影响走法生成的扩展规则
        return !RulesConfigProvider.get().getBoolean("top_bottom_connected")
            && !RulesConfigProvider.get().getBoolean("left_right_connected")
            && !RulesConfigProvider.get().getBoolean("international_king")
            && !RulesConfigProvider.get().getBoolean("international_advisor")
            && !RulesConfigProvider.get().getBoolean("pawn_can_retreat")
            && !RulesConfigProvider.get().getBoolean("allow_inside_retreat")
            && !RulesConfigProvider.get().getBoolean("allow_flying_general")
            && !RulesConfigProvider.get().getBoolean("no_river_limit")
            && !RulesConfigProvider.get().getBoolean("unblock_piece")
            && !RulesConfigProvider.get().getBoolean("allow_capture_own_piece")
            && !RulesConfigProvider.get().getBoolean("allow_piece_stacking");
    }

    @Override
    public List<Move> generateLegalMoves() {
        boolean currentTurnIsRed = isRedTurn();
        int H = getRows(), W = getCols();

        // 如果存在扩展规则，回退到全遍历以确保正确性
        if (!isStandardRulesOnly()) {
            List<Move> moves = new ArrayList<>();
            for (int fr = 0; fr < H; fr++) {
                for (int fc = 0; fc < W; fc++) {
                    Piece piece = getPiece(fr, fc);
                    if (piece == null || piece.isRed() != currentTurnIsRed) continue;
                    for (int tr = 0; tr < H; tr++) {
                        for (int tc = 0; tc < W; tc++) {
                            if (fr == tr && fc == tc) continue;
                            if (!isValidMove(fr, fc, tr, tc)) continue;
                            moves.add(new Move(fr, fc, tr, tc, piece, getPiece(tr, tc)));
                        }
                    }
                }
            }
            return moves;
        }

        // 标准规则：用候选位置加速
        List<Move> moves = new ArrayList<>();
        for (int fr = 0; fr < H; fr++) {
            for (int fc = 0; fc < W; fc++) {
                Piece piece = getPiece(fr, fc);
                if (piece == null || piece.isRed() != currentTurnIsRed) continue;
                for (int[] t : getCandidateTargets(fr, fc, piece)) {
                    if (!isValidMove(fr, fc, t[0], t[1])) continue;
                    moves.add(new Move(fr, fc, t[0], t[1], piece, getPiece(t[0], t[1])));
                }
            }
        }
        return moves;
    }

    /**
     * 根据棋子类型生成候选目标位置，仅生成该棋子可能走到的格子，
     * 绕过棋盘上的其他棋子进行走法生成
     */
    private List<int[]> getCandidateTargets(int fr, int fc, Piece piece) {
        int H = getRows();
        int W = getCols();
        switch (piece.getType()) {
            case RED_KING:
            case BLACK_KING:
                return kingTargets(fr, fc, H, W, piece);
            case RED_ADVISOR:
            case BLACK_ADVISOR:
                return advisorTargets(fr, fc, H, W, piece);
            case RED_ELEPHANT:
            case BLACK_ELEPHANT:
                return elephantTargets(fr, fc, H, W, piece);
            case RED_HORSE:
            case BLACK_HORSE:
                return horseTargets(fr, fc, H, W, piece);
            case RED_CHARIOT:
            case BLACK_CHARIOT:
                return chariotTargets(fr, fc, H, W, piece);
            case RED_CANNON:
            case BLACK_CANNON:
                return cannonTargets(fr, fc, H, W, piece);
            case RED_SOLDIER:
            case BLACK_SOLDIER:
                return soldierTargets(fr, fc, H, W, piece);
            default:
                return Collections.emptyList();
        }
    }

    private void addIfValid(List<int[]> list, int r, int c, int H, int W) {
        if (r >= 0 && r < H && c >= 0 && c < W) {
            list.add(new int[]{r, c});
        }
    }

    private void addIfValid(List<int[]> list, int r, int c, int H, int W, int modR, int modC) {
        int rr = (r % modR + modR) % modR;
        int cc = (c % modC + modC) % modC;
        if (rr >= 0 && rr < H && cc >= 0 && cc < W) {
            list.add(new int[]{rr, cc});
        }
    }

    private List<int[]> kingTargets(int fr, int fc, int H, int W, Piece p) {
        List<int[]> list = new ArrayList<>();
        addIfValid(list, fr-1, fc, H, W);
        addIfValid(list, fr+1, fc, H, W);
        addIfValid(list, fr, fc-1, H, W);
        addIfValid(list, fr, fc+1, H, W);
        return list;
    }

    private List<int[]> advisorTargets(int fr, int fc, int H, int W, Piece p) {
        List<int[]> list = new ArrayList<>();
        addIfValid(list, fr-1, fc-1, H, W);
        addIfValid(list, fr-1, fc+1, H, W);
        addIfValid(list, fr+1, fc-1, H, W);
        addIfValid(list, fr+1, fc+1, H, W);
        return list;
    }

    private List<int[]> elephantTargets(int fr, int fc, int H, int W, Piece p) {
        List<int[]> list = new ArrayList<>();
        addIfValid(list, fr-2, fc-2, H, W);
        addIfValid(list, fr-2, fc+2, H, W);
        addIfValid(list, fr+2, fc-2, H, W);
        addIfValid(list, fr+2, fc+2, H, W);
        return list;
    }

    private List<int[]> horseTargets(int fr, int fc, int H, int W, Piece p) {
        List<int[]> list = new ArrayList<>();
        addIfValid(list, fr-2, fc-1, H, W);
        addIfValid(list, fr-2, fc+1, H, W);
        addIfValid(list, fr+2, fc-1, H, W);
        addIfValid(list, fr+2, fc+1, H, W);
        addIfValid(list, fr-1, fc-2, H, W);
        addIfValid(list, fr-1, fc+2, H, W);
        addIfValid(list, fr+1, fc-2, H, W);
        addIfValid(list, fr+1, fc+2, H, W);
        return list;
    }

    private List<int[]> chariotTargets(int fr, int fc, int H, int W, Piece p) {
        List<int[]> list = new ArrayList<>();
        // 上
        for (int r = fr-1; r >= 0; r--) {
            list.add(new int[]{r, fc});
            if (getPiece(r, fc) != null) break;
        }
        // 下
        for (int r = fr+1; r < H; r++) {
            list.add(new int[]{r, fc});
            if (getPiece(r, fc) != null) break;
        }
        // 左
        for (int c = fc-1; c >= 0; c--) {
            list.add(new int[]{fr, c});
            if (getPiece(fr, c) != null) break;
        }
        // 右
        for (int c = fc+1; c < W; c++) {
            list.add(new int[]{fr, c});
            if (getPiece(fr, c) != null) break;
        }
        return list;
    }

    private List<int[]> cannonTargets(int fr, int fc, int H, int W, Piece p) {
        List<int[]> list = new ArrayList<>();
        // 上：遇到第一个棋子前可以走，遇到后只能吃（跨过第一个棋子的下一个有棋子位置）
        boolean found = false;
        for (int r = fr-1; r >= 0; r--) {
            if (!found) {
                if (getPiece(r, fc) == null) {
                    list.add(new int[]{r, fc});  // 空格可走
                } else {
                    found = true;  // 找到炮架
                }
            } else {
                if (getPiece(r, fc) != null) {
                    list.add(new int[]{r, fc});  // 炮架后的第一个棋子可吃
                    break;
                }
            }
        }
        // 下
        found = false;
        for (int r = fr+1; r < H; r++) {
            if (!found) {
                if (getPiece(r, fc) == null) {
                    list.add(new int[]{r, fc});
                } else {
                    found = true;
                }
            } else {
                if (getPiece(r, fc) != null) {
                    list.add(new int[]{r, fc});
                    break;
                }
            }
        }
        // 左
        found = false;
        for (int c = fc-1; c >= 0; c--) {
            if (!found) {
                if (getPiece(fr, c) == null) {
                    list.add(new int[]{fr, c});
                } else {
                    found = true;
                }
            } else {
                if (getPiece(fr, c) != null) {
                    list.add(new int[]{fr, c});
                    break;
                }
            }
        }
        // 右
        found = false;
        for (int c = fc+1; c < W; c++) {
            if (!found) {
                if (getPiece(fr, c) == null) {
                    list.add(new int[]{fr, c});
                } else {
                    found = true;
                }
            } else {
                if (getPiece(fr, c) != null) {
                    list.add(new int[]{fr, c});
                    break;
                }
            }
        }
        return list;
    }

    private List<int[]> soldierTargets(int fr, int fc, int H, int W, Piece p) {
        List<int[]> list = new ArrayList<>();
        boolean isRed = p.isRed();
        // 前：红向上(row减小)，黑向下(row增大)
        int forward = isRed ? -1 : 1;
        addIfValid(list, fr+forward, fc, H, W);
        // 左右（仅在过河后可用，但 isValidMove 会拦）
        addIfValid(list, fr, fc-1, H, W);
        addIfValid(list, fr, fc+1, H, W);
        return list;
    }

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
