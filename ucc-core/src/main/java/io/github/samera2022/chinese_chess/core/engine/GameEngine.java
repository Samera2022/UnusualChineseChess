package io.github.samera2022.chinese_chess.core.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.api.io.GameStateSerializer;
import io.github.samera2022.chinese_chess.common.GameStateAccessor;
import io.github.samera2022.chinese_chess.common.GameStateListener;
import io.github.samera2022.chinese_chess.common.GameStatus;
import io.github.samera2022.chinese_chess.common.spi.GameSession;
import io.github.samera2022.chinese_chess.common.spi.ReadonlyBoard;
import io.github.samera2022.chinese_chess.common.spi.SessionListener;
import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.HistoryItem;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.model.RuleChangeRecord;
import io.github.samera2022.chinese_chess.core.rules.CheckDetector;
import io.github.samera2022.chinese_chess.core.rules.MoveValidator;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.common.rules.RuleRegistry;
import io.github.samera2022.chinese_chess.core.rules.RulesConfigProvider;

import java.lang.reflect.Type;
import java.util.*;

/**
 * 游戏引擎 - 管理游戏状态和逻辑
 */
public class GameEngine implements GameStateAccessor, GameSession {
    private final Gson gson;
    private Board board;
    private MoveValidator validator;
    private CheckDetector checkDetector;
    private GameRulesConfig rulesConfig;
    private final RulesConfigProvider.InstanceChangeListener providerListener = (oldInst, newInst) -> {
        try {
            this.rulesConfig = newInst;
            if (this.validator != null) this.validator.setRulesConfig(newInst);
        } catch (Throwable t) {
            System.err.println("[GameEngine] Failed to apply provider instance change: " + t);
        }
    };
    private List<Move> moveHistory;
    private List<RuleChangeRecord> ruleChangeHistory;
    private boolean isRedTurn;
    private GameStatus gameState;
    private final List<GameStateListener> listeners;
    private final Map<SessionListener, GameStateListener> sessionListenerMap = new HashMap<>();

    private Board savedInitialBoard = null;
    private boolean savedInitialIsRedTurn = true;
    private boolean isInReplayMode = false;
    private int currentReplayStep = -1;

    public GameEngine(GameRulesConfig injectedRulesConfig) {
        this.gson = new GsonBuilder()
                .registerTypeAdapter(RuleChangeRecord.class, (com.google.gson.InstanceCreator<RuleChangeRecord>) type -> new RuleChangeRecord())
                .create();
        this.rulesConfig = injectedRulesConfig != null ? injectedRulesConfig : RulesConfigProvider.get();
        boolean tb = this.rulesConfig.getBoolean(RuleRegistry.TOP_BOTTOM_CONNECTED.registryName);
        this.board = tb ? new Board(Board.EXPANDED_ROWS) : new Board(Board.STANDARD_ROWS);
        this.validator = new MoveValidator(board);
        this.validator.setRulesConfig(this.rulesConfig);
        this.checkDetector = new CheckDetector(board, validator);
        RulesConfigProvider.addInstanceChangeListener(providerListener);
        this.moveHistory = new ArrayList<>();
        this.ruleChangeHistory = new ArrayList<>();
        this.isRedTurn = true;
        this.gameState = GameStatus.RUNNING;
        this.listeners = new ArrayList<>();
    }

    public GameEngine() { this(RulesConfigProvider.get()); }

    public void setRulesConfig(GameRulesConfig newConfig) {
        if (newConfig == null) return;
        this.rulesConfig = newConfig;
        if (this.validator != null) this.validator.setRulesConfig(newConfig);
    }

    /**
     * 根据当前 top_bottom_connected 重建棋盘（标准 10 行 ↔ 对称 18 行）
     */
    public void rebuildBoardForTopBottom() {
        boolean tb = rulesConfig.getBoolean(RuleRegistry.TOP_BOTTOM_CONNECTED.registryName);
        int newRows = tb ? Board.EXPANDED_ROWS : Board.STANDARD_ROWS;
        if (board.getRows() == newRows) return;
        this.board = new Board(newRows);
        this.validator = new MoveValidator(board);
        this.validator.setRulesConfig(this.rulesConfig);
        this.checkDetector = new CheckDetector(board, validator);
        this.moveHistory.clear();
        this.ruleChangeHistory.clear();
        this.isRedTurn = true;
        this.gameState = GameStatus.RUNNING;
        this.savedInitialBoard = null;
        this.savedInitialIsRedTurn = true;
        this.isInReplayMode = false;
        this.currentReplayStep = -1;
        notifyGameStateChanged();
    }

    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        return makeMove(fromRow, fromCol, toRow, toCol, null, -1);
    }

    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol, Piece.Type promotionType) {
        return makeMove(fromRow, fromCol, toRow, toCol, promotionType, -1);
    }

    @Override
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol, Piece.Type promotionType, int selectedStackIndex) {
        if (gameState != GameStatus.RUNNING) {
            return false;
        }

        if (isInReplayMode && currentReplayStep >= 0 && currentReplayStep < moveHistory.size()) {
            moveHistory = new ArrayList<>(moveHistory.subList(0, currentReplayStep));
            isInReplayMode = false;
            currentReplayStep = -1;
        }

        List<Piece> fromStack = board.getStack(fromRow, fromCol);
        if (fromStack.isEmpty()) {
            return false;
        }

        Piece piece;
        List<Piece> movedStack = new ArrayList<>();
        boolean carryEnabled = rulesConfig.getBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName) &&
                rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName) > 1 &&
                rulesConfig.getBoolean(RuleRegistry.ALLOW_CARRY_PIECES_ABOVE.registryName);

        if (selectedStackIndex >= 0 && selectedStackIndex < fromStack.size()) {
            piece = fromStack.get(selectedStackIndex);
            if (carryEnabled) {
                for (int i = selectedStackIndex + 1; i < fromStack.size(); i++) {
                    movedStack.add(fromStack.get(i));
                }
            }
        } else if (selectedStackIndex == -1) {
            piece = board.getPiece(fromRow, fromCol);
        } else {
            return false;
        }

        if (piece == null || piece.isRed() != isRedTurn || !validator.isValidMove(fromRow, fromCol, toRow, toCol, selectedStackIndex)) {
            return false;
        }

        Piece capturedPiece = board.getPiece(toRow, toCol);
        boolean convertedCapture = false;
        boolean isStackingMove = false;

        if (rulesConfig.getBoolean(RuleRegistry.ALLOW_PIECE_STACKING.registryName) &&
                rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName) > 1 &&
                capturedPiece != null && capturedPiece.isRed() == piece.isRed()) {
            int stackSize = board.getStackSize(toRow, toCol);
            if (stackSize < rulesConfig.getInt(RuleRegistry.MAX_STACKING_COUNT.registryName)) {
                isStackingMove = true;
                capturedPiece = null;
            }
        }

        Piece convertedPiece = null;
        if (capturedPiece != null && !isStackingMove && rulesConfig.getBoolean(RuleRegistry.ALLOW_CAPTURE_CONVERSION.registryName)) {
            convertedPiece = new Piece(convertPieceTypeToSide(capturedPiece.getType(), piece.isRed()), toRow, toCol);
            convertedCapture = true;
            board.setPiece(toRow, toCol, convertedPiece);
        } else if (capturedPiece != null && !isStackingMove) {
            board.removePiece(toRow, toCol);
        }

        if (!convertedCapture) {
            if (selectedStackIndex >= 0) {
                if (carryEnabled) {
                    int countToRemove = fromStack.size() - selectedStackIndex;
                    for (int i = 0; i < countToRemove; i++) {
                        board.popTop(fromRow, fromCol);
                    }
                } else {
                    board.removeFromStack(fromRow, fromCol, selectedStackIndex);
                }
            } else {
                board.removePiece(fromRow, fromCol);
            }

            piece.move(toRow, toCol);
            if (isStackingMove) {
                board.pushToStack(toRow, toCol, piece);
            } else {
                board.setPiece(toRow, toCol, piece);
            }

            if (carryEnabled) {
                for (Piece p : movedStack) {
                    p.move(toRow, toCol);
                    board.pushToStack(toRow, toCol, p);
                }
            }
        }

        if (!convertedCapture && rulesConfig.getBoolean(RuleRegistry.PAWN_PROMOTION.registryName) &&
                (piece.getType() == Piece.Type.RED_SOLDIER || piece.getType() == Piece.Type.BLACK_SOLDIER)) {
            boolean tb2 = rulesConfig.getBoolean(RuleRegistry.TOP_BOTTOM_CONNECTED.registryName);
            int oppoRow, ownRow;
            if (tb2) {
                oppoRow = piece.isRed() ? 4 : 13;
                ownRow = piece.isRed() ? 13 : 4;
            } else {
                oppoRow = piece.isRed() ? 0 : (board.getRows() - 1);
                ownRow = piece.isRed() ? (board.getRows() - 1) : 0;
            }
            boolean isAtOpponentBaseLine = toRow == oppoRow;
            boolean isAtOwnBaseLine = toRow == ownRow;
            boolean allowOwnBaseLine = rulesConfig.getBoolean(RuleRegistry.ALLOW_OWN_BASE_LINE.registryName);
            if ((isAtOpponentBaseLine || (isAtOwnBaseLine && allowOwnBaseLine)) && promotionType != null) {
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

        isRedTurn = !isRedTurn;
        for (GameStateListener listener : listeners) {
            listener.onMoveExecuted(move);
        }
        checkGameState();
        return true;
    }

    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        return isValidMove(fromRow, fromCol, toRow, toCol, -1);
    }

    @Override
    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, int selectedStackIndex) {
        if (gameState != GameStatus.RUNNING) return false;
        List<Piece> fromStack = board.getStack(fromRow, fromCol);
        if (fromStack == null || fromStack.isEmpty()) return false;
        Piece piece = selectedStackIndex >= 0 && selectedStackIndex < fromStack.size() ? fromStack.get(selectedStackIndex) : board.getPiece(fromRow, fromCol);
        if (piece == null || piece.isRed() != isRedTurn) return false;
        try {
            return validator.isValidMove(fromRow, fromCol, toRow, toCol, selectedStackIndex);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean forceApplyMove(int fromRow, int fromCol, int toRow, int toCol) {
        return forceApplyMove(fromRow, fromCol, toRow, toCol, null, -1);
    }

    public boolean forceApplyMove(int fromRow, int fromCol, int toRow, int toCol, Piece.Type promotionType, int selectedStackIndex) {
        if (gameState != GameStatus.RUNNING) {
            return false;
        }

        // 基本坐标验证
        if (!board.isValid(fromRow, fromCol) || !board.isValid(toRow, toCol)) {
            return false;
        }
        if (fromRow == toRow && fromCol == toCol) {
            return false;
        }

        List<Piece> fromStack = board.getStack(fromRow, fromCol);
        if (fromStack.isEmpty()) {
            return false;
        }

        Piece piece;
        if (selectedStackIndex >= 0 && selectedStackIndex < fromStack.size()) {
            piece = fromStack.get(selectedStackIndex);
        } else if (selectedStackIndex == -1) {
            piece = board.getPiece(fromRow, fromCol);
        } else {
            return false;
        }

        if (piece == null) {
            return false;
        }

        Piece capturedPiece = board.getPiece(toRow, toCol);
        board.removePiece(toRow, toCol); // Remove any piece at the destination

        if (selectedStackIndex >= 0) {
            board.removeFromStack(fromRow, fromCol, selectedStackIndex);
        } else {
            board.removePiece(fromRow, fromCol);
        }

        piece.move(toRow, toCol);
        board.setPiece(toRow, toCol, piece);

        if (promotionType != null) {
            board.setPiece(toRow, toCol, new Piece(promotionType, toRow, toCol));
        }

        Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedPiece);
        moveHistory.add(move);

        isRedTurn = !isRedTurn;
        for (GameStateListener listener : listeners) {
            listener.onMoveExecuted(move);
        }
        checkGameState();
        return true;
    }

    public boolean needsPromotion(int row, int col) {
        if (!rulesConfig.getBoolean(RuleRegistry.PAWN_PROMOTION.registryName)) {
            return false;
        }
        Piece piece = board.getPiece(row, col);
        if (piece == null || (piece.getType() != Piece.Type.RED_SOLDIER && piece.getType() != Piece.Type.BLACK_SOLDIER)) {
            return false;
        }
        boolean tb3 = rulesConfig.getBoolean(RuleRegistry.TOP_BOTTOM_CONNECTED.registryName);
        int oppoLine = tb3 ? (piece.isRed() ? 4 : 13) : (piece.isRed() ? 0 : (board.getRows() - 1));
        return row == oppoLine;
    }

    private void checkGameState() {
        if (rulesConfig.getBoolean(RuleRegistry.DEATH_MATCH_UNTIL_VICTORY.registryName)) {
            int redPieces = 0;
            int blackPieces = 0;
            for (int r = 0; r < board.getRows(); r++) {
                for (int c = 0; c < board.getCols(); c++) {
                    Piece piece = board.getPiece(r, c);
                    if (piece != null) {
                        if (piece.isRed()) {
                            redPieces++;
                        } else {
                            blackPieces++;
                        }
                    }
                }
            }
            if (redPieces == 0) {
                gameState = GameStatus.RED_CHECKMATE;
            } else if (blackPieces == 0) {
                gameState = GameStatus.BLACK_CHECKMATE;
            } else {
                return; // No change
            }
        } else {
            Piece redKing = board.getRedKing();
            Piece blackKing = board.getBlackKing();

            if (redKing == null) {
                gameState = GameStatus.RED_CHECKMATE;
            } else if (blackKing == null) {
                gameState = GameStatus.BLACK_CHECKMATE;
            } else if (checkDetector.isCheckmate(false)) {
                gameState = GameStatus.BLACK_CHECKMATE;
            } else if (checkDetector.isCheckmate(true)) {
                gameState = GameStatus.RED_CHECKMATE;
            } else {
                return; // No change
            }
        }
        notifyGameStateChanged();
    }

    private void notifyGameStateChanged() {
        for (GameStateListener listener : listeners) {
            listener.onGameStateChanged(gameState);
        }
    }

    public boolean undoLastMove() {
        if (!rulesConfig.getBoolean(RuleRegistry.ALLOW_UNDO.registryName) || moveHistory.isEmpty()) {
            return false;
        }

        int lastMoveIndex = moveHistory.size() - 1;
        while (!ruleChangeHistory.isEmpty() && ruleChangeHistory.get(ruleChangeHistory.size() - 1).getAfterMoveIndex() >= lastMoveIndex) {
            RuleChangeRecord record = ruleChangeHistory.remove(ruleChangeHistory.size() - 1);
            rulesConfig.set(record.getRuleKey(), record.getOldValue(), GameRulesConfig.ChangeSource.UNDO);
        }

        Move lastMove = moveHistory.remove(lastMoveIndex);
        undoMoveOnBoard(lastMove);

        isRedTurn = !isRedTurn;
        gameState = GameStatus.RUNNING;
        notifyGameStateChanged();
        return true;
    }

    private void undoMoveOnBoard(Move move) {
        if (move == null) return;
        Piece movedPiece = move.getPiece();
        if (movedPiece == null) return;

        movedPiece.move(move.getFromRow(), move.getFromCol());
        board.setPiece(move.getFromRow(), move.getFromCol(), movedPiece);

        if (move.isStacking()) {
            board.clearStack(move.getToRow(), move.getToCol());
            List<Piece> stackBefore = move.getStackBefore();
            if (stackBefore != null) {
                stackBefore.removeIf(p -> p.equals(movedPiece));
                for (Piece p : stackBefore) {
                    board.pushToStack(move.getToRow(), move.getToCol(), p);
                }
            }
        } else if (move.isCaptureConversion()) {
            board.setPiece(move.getToRow(), move.getToCol(), move.getCapturedPiece());
        } else {
            board.setPiece(move.getToRow(), move.getToCol(), move.getCapturedPiece());
        }

        List<Piece> movedStack = move.getMovedStack();
        if (movedStack != null && !movedStack.isEmpty()) {
            for (Piece p : movedStack) {
                p.move(move.getFromRow(), move.getFromCol());
                board.pushToStack(move.getFromRow(), move.getFromCol(), p);
            }
        }
    }

    @Override
    public void restart() {
        if (rulesConfig.getBoolean(RuleRegistry.TOP_BOTTOM_CONNECTED.registryName)) {
            board.resetSymmetric();
        } else {
            board.reset();
        }
        moveHistory.clear();
        ruleChangeHistory.clear();
        isRedTurn = true;
        gameState = GameStatus.RUNNING;
        savedInitialBoard = null;
        savedInitialIsRedTurn = true;
        isInReplayMode = false;
        currentReplayStep = -1;
        notifyGameStateChanged();
    }

    public Board getBoard() { return board; }

    // ── GameSession: 棋盘信息代理方法 ──

    @Override
    public Piece getPiece(int row, int col) {
        return board.getPiece(row, col);
    }

    @Override
    public List<Piece> getStack(int row, int col) {
        return board.getStack(row, col);
    }

    @Override
    public int getStackSize(int row, int col) {
        return board.getStackSize(row, col);
    }

    @Override
    public int getBoardRows() {
        return board.getRows();
    }

    @Override
    public int getBoardCols() {
        return board.getCols();
    }

    @Override
    public BoardState getBoardState() {
        List<BoardState.StackEntry> entries = new ArrayList<>();
        for (int r = 0; r < board.getRows(); r++) {
            for (int c = 0; c < board.getCols(); c++) {
                List<Piece> stack = board.getStack(r, c);
                if (stack != null && !stack.isEmpty()) {
                    List<Piece.Type> types = new ArrayList<>();
                    for (Piece p : stack) {
                        types.add(p.getType());
                    }
                    entries.add(new BoardState.StackEntry(r, c, types));
                }
            }
        }
        return new BoardState(board.getRows(), board.getCols(), entries);
    }

    @Override
    public List<Move> getMoveHistory() { return new ArrayList<>(moveHistory); }

    @Override
    public void clearMoveHistory() { moveHistory.clear(); }

    @Override
    public void clearRuleChangeHistory() { ruleChangeHistory.clear(); }

    @Override
    public void addMoveToHistory(Move move) { moveHistory.add(move); }

    @Override
    public void addRuleChangeToHistory(RuleChangeRecord ruleChange) { ruleChangeHistory.add(ruleChange); }

    @Override
    public List<RuleChangeRecord> getRuleChangeHistory() { return new ArrayList<>(ruleChangeHistory); }

    public List<HistoryItem> getCombinedHistory() {
        List<HistoryItem> combined = new ArrayList<>();
        combined.addAll(moveHistory);
        combined.addAll(ruleChangeHistory);
        combined.sort(Comparator.comparingDouble(item -> {
            if (item instanceof Move) {
                return moveHistory.indexOf(item);
            } else {
                return ((RuleChangeRecord) item).getAfterMoveIndex() + 0.5;
            }
        }));
        return combined;
    }

    @Override
    public void saveInitialStateForReplay() {
        savedInitialBoard = board.deepCopy();
        savedInitialIsRedTurn = isRedTurn;
    }

    public void rebuildBoardToStep(int step) {
        if (savedInitialBoard == null) return;
        board.clearBoard();
        for (int row = 0; row < board.getRows(); row++) {
            for (int col = 0; col < board.getCols(); col++) {
                Piece piece = savedInitialBoard.getPiece(row, col);
                if (piece != null) {
                    board.putPieceFresh(row, col, new Piece(piece.getType(), row, col));
                }
            }
        }
        isRedTurn = savedInitialIsRedTurn;
        List<Move> moves = getMoveHistory();
        for (int i = 0; i < step && i < moves.size(); i++) {
            Move move = moves.get(i);
            Piece piece = board.getPiece(move.getFromRow(), move.getFromCol());
            if (piece == null) continue;
            if (board.getPiece(move.getToRow(), move.getToCol()) != null) {
                board.removePiece(move.getToRow(), move.getToCol());
            }
            if (move.isCaptureConversion() && move.getConvertedPiece() != null) {
                board.setPiece(move.getToRow(), move.getToCol(), new Piece(move.getConvertedPiece().getType(), move.getToRow(), move.getToCol()));
            } else {
                piece.move(move.getToRow(), move.getToCol());
                board.setPiece(move.getToRow(), move.getToCol(), piece);
                board.setPiece(move.getFromRow(), move.getFromCol(), null);
            }
            isRedTurn = !isRedTurn;
        }
        gameState = GameStatus.RUNNING;
    }

    public void setReplayMode(boolean inReplayMode, int step) {
        this.isInReplayMode = inReplayMode;
        this.currentReplayStep = step;
    }

    public boolean isInReplayMode() { return isInReplayMode; }
    public int getCurrentReplayStep() { return currentReplayStep; }

    @Override
    public boolean isRedTurn() { return isRedTurn; }

    @Override
    public void setRedTurn(boolean isRedTurn) { this.isRedTurn = isRedTurn; }

    public GameStatus getGameState() { return gameState; }

    @Override
    public GameStatus getGameStatus() {
        return gameState;
    }

    // ── GameStateAccessor 接口：common.GameStateListener ──

    @Override
    public void addGameStateListener(GameStateListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeGameStateListener(GameStateListener listener) {
        listeners.remove(listener);
    }

    // ── GameSession: SessionListener 管理 ──

    @Override
    public void addSessionListener(SessionListener listener) {
        GameStateListener wrapper = new GameStateListener() {
            @Override
            public void onGameStateChanged(GameStatus newState) {
                listener.onGameStateChanged(newState);
            }
            @Override
            public void onMoveExecuted(Move move) {
                listener.onMoveExecuted(move);
                listener.onBoardChanged();
            }
        };
        sessionListenerMap.put(listener, wrapper);
        listeners.add(wrapper);
    }

    @Override
    public void removeSessionListener(SessionListener listener) {
        GameStateListener wrapper = sessionListenerMap.remove(listener);
        if (wrapper != null) {
            listeners.remove(wrapper);
        }
    }

    public JsonObject getSettingsSnapshot() { return rulesConfig.toJson(); }

    @Override
    public JsonObject getRulesSnapshot() {
        return getSettingsSnapshot();
    }

    public void applySettingsSnapshot(JsonObject snapshot) {
        if (snapshot == null) return;
        rulesConfig.applySnapshot(snapshot, GameRulesConfig.ChangeSource.NETWORK);
        validator.setRulesConfig(this.rulesConfig);
    }

    @Override
    public void applyRulesSnapshot(JsonObject snapshot) {
        applySettingsSnapshot(snapshot);
    }

    public GameRulesConfig getRulesConfig() { return this.rulesConfig; }

    // ── GameSession: 规则访问代理方法 ──

    @Override
    public boolean getRuleBoolean(String ruleKey) {
        return rulesConfig.getBoolean(ruleKey);
    }

    @Override
    public int getRuleInt(String ruleKey) {
        return rulesConfig.getInt(ruleKey);
    }

    @Override
    public void setRule(String ruleKey, Object value) {
        rulesConfig.set(ruleKey, value, GameRulesConfig.ChangeSource.UI);
    }

    public void shutdown() {
        try {
            RulesConfigProvider.removeInstanceChangeListener(providerListener);
        } catch (Throwable t) {
            System.err.println("[GameEngine] Error during shutdown: " + t);
        }
    }

    private Piece.Type convertPieceTypeToSide(Piece.Type type, boolean toRed) {
        switch (type) {
            case RED_KING: case BLACK_KING: return toRed ? Piece.Type.RED_KING : Piece.Type.BLACK_KING;
            case RED_ADVISOR: case BLACK_ADVISOR: return toRed ? Piece.Type.RED_ADVISOR : Piece.Type.BLACK_ADVISOR;
            case RED_ELEPHANT: case BLACK_ELEPHANT: return toRed ? Piece.Type.RED_ELEPHANT : Piece.Type.BLACK_ELEPHANT;
            case RED_HORSE: case BLACK_HORSE: return toRed ? Piece.Type.RED_HORSE : Piece.Type.BLACK_HORSE;
            case RED_CHARIOT: case BLACK_CHARIOT: return toRed ? Piece.Type.RED_CHARIOT : Piece.Type.BLACK_CHARIOT;
            case RED_CANNON: case BLACK_CANNON: return toRed ? Piece.Type.RED_CANNON : Piece.Type.BLACK_CANNON;
            case RED_SOLDIER: case BLACK_SOLDIER: return toRed ? Piece.Type.RED_SOLDIER : Piece.Type.BLACK_SOLDIER;
            default: return type;
        }
    }

    // ── GameStateAccessor: 板操作方法 ──

    @Override
    public void clearBoardPieces() {
        board.clearBoard();
    }

    @Override
    public void addPieceToBoard(int row, int col, Piece.Type type) {
        Piece piece = new Piece(type, row, col);
        board.pushToStack(row, col, piece);
    }

    @Override
    public void loadBoardState(BoardState state) {
        board.clearBoard();
        if (state == null) return;
        for (BoardState.StackEntry entry : state.getEntries()) {
            for (Piece.Type type : entry.pieceTypes) {
                Piece p = new Piece(type, entry.row, entry.col);
                board.pushToStack(entry.row, entry.col, p);
            }
        }
    }

    @Override
    public void notifyListenersRefresh() {
        // 通知监听器刷新 — 通过 null move 触发 UI 刷新
        for (GameStateListener listener : listeners) {
            listener.onMoveExecuted(null);
        }
        notifyGameStateChanged();
    }

    // ── 同步状态序列化/反序列化 ──

    public JsonObject getSyncState() {
        return GameStateSerializer.serialize(this);
    }

    public void loadSyncState(JsonObject root) {
        if (root == null) return;

        // 恢复 gameState（GameStateSerializer 不处理 gameState 字段）
        if (root.has("gameState")) {
            try {
                this.gameState = GameStatus.valueOf(root.get("gameState").getAsString());
            } catch (Exception e) {
                this.gameState = GameStatus.RUNNING;
            }
        }

        GameStateSerializer.deserialize(this, root);

        // 补充内部状态
        this.savedInitialBoard = board.deepCopy();
        this.savedInitialIsRedTurn = this.isRedTurn;
        this.isInReplayMode = false;
        this.currentReplayStep = -1;
        this.validator.setRulesConfig(this.rulesConfig);
    }
}
