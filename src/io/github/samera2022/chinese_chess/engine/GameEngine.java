package io.github.samera2022.chinese_chess.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

import java.lang.reflect.Type;
import java.util.*;

/**
 * 游戏引擎 - 管理游戏状态和逻辑
 */
public class GameEngine {
    private final Gson gson;
    private Board board;
    private MoveValidator validator;
    private CheckDetector checkDetector;
    private GameRulesConfig rulesConfig;
    private final RulesConfigProvider.InstanceChangeListener providerListener = (oldInst, newInst) -> {
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
        this.gson = new GsonBuilder()
                .registerTypeAdapter(RuleChangeRecord.class, (com.google.gson.InstanceCreator<RuleChangeRecord>) type -> new RuleChangeRecord())
                .create();
        this.rulesConfig = injectedRulesConfig != null ? injectedRulesConfig : RulesConfigProvider.get();
        this.board = new Board();
        this.validator = new MoveValidator(board);
        this.validator.setRulesConfig(this.rulesConfig);
        this.checkDetector = new CheckDetector(board, validator);
        RulesConfigProvider.addInstanceChangeListener(providerListener);
        this.moveHistory = new ArrayList<>();
        this.ruleChangeHistory = new ArrayList<>();
        this.isRedTurn = true;
        this.gameState = GameState.RUNNING;
        this.listeners = new ArrayList<>();
    }

    public GameEngine() { this(RulesConfigProvider.get()); }

    public void setRulesConfig(GameRulesConfig newConfig) {
        if (newConfig == null) return;
        this.rulesConfig = newConfig;
        if (this.validator != null) this.validator.setRulesConfig(newConfig);
    }

    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        return makeMove(fromRow, fromCol, toRow, toCol, null, -1);
    }

    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol, Piece.Type promotionType) {
        return makeMove(fromRow, fromCol, toRow, toCol, promotionType, -1);
    }

    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol, Piece.Type promotionType, int selectedStackIndex) {
        if (gameState != GameState.RUNNING) {
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

    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, int selectedStackIndex) {
        if (gameState != GameState.RUNNING) return false;
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
        if (gameState != GameState.RUNNING) {
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
        return (piece.isRed() && row == 0) || (!piece.isRed() && row == 9);
    }

    private void checkGameState() {
        Piece redKing = board.getRedKing();
        Piece blackKing = board.getBlackKing();

        if (redKing == null) {
            gameState = GameState.RED_CHECKMATE;
        } else if (blackKing == null) {
            gameState = GameState.BLACK_CHECKMATE;
        } else if (checkDetector.isCheckmate(false)) {
            gameState = GameState.BLACK_CHECKMATE;
        } else if (checkDetector.isCheckmate(true)) {
            gameState = GameState.RED_CHECKMATE;
        } else {
            return; // No change
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
        gameState = GameState.RUNNING;
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

    public void restart() {
        board.reset();
        moveHistory.clear();
        ruleChangeHistory.clear();
        isRedTurn = true;
        gameState = GameState.RUNNING;
        savedInitialBoard = null;
        savedInitialIsRedTurn = true;
        isInReplayMode = false;
        currentReplayStep = -1;
        notifyGameStateChanged();
    }

    public Board getBoard() { return board; }
    public List<Move> getMoveHistory() { return new ArrayList<>(moveHistory); }
    public void clearMoveHistory() { moveHistory.clear(); }
    public void clearRuleChangeHistory() { ruleChangeHistory.clear(); }
    public void addMoveToHistory(Move move) { moveHistory.add(move); }
    public void addRuleChangeToHistory(RuleChangeRecord ruleChange) { ruleChangeHistory.add(ruleChange); }
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
        gameState = GameState.RUNNING;
    }

    public void setReplayMode(boolean inReplayMode, int step) {
        this.isInReplayMode = inReplayMode;
        this.currentReplayStep = step;
    }

    public boolean isInReplayMode() { return isInReplayMode; }
    public int getCurrentReplayStep() { return currentReplayStep; }
    public boolean isRedTurn() { return isRedTurn; }
    public void setRedTurn(boolean isRedTurn) { this.isRedTurn = isRedTurn; }
    public GameState getGameState() { return gameState; }
    public void addGameStateListener(GameStateListener listener) { listeners.add(listener); }
    public void removeGameStateListener(GameStateListener listener) { listeners.remove(listener); }
    public JsonObject getSettingsSnapshot() { return rulesConfig.toJson(); }

    public void applySettingsSnapshot(JsonObject snapshot) {
        if (snapshot == null) return;
        rulesConfig.applySnapshot(snapshot, GameRulesConfig.ChangeSource.NETWORK);
        validator.setRulesConfig(this.rulesConfig);
    }

    public GameRulesConfig getRulesConfig() { return this.rulesConfig; }

    public void shutdown() {
        try {
            RulesConfigProvider.removeInstanceChangeListener(providerListener);
        } catch (Throwable ignored) {}
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

    public JsonObject getSyncState() {
        JsonObject root = new JsonObject();
        root.addProperty("isRedTurn", isRedTurn);
        root.addProperty("gameState", gameState.name());
        root.add("rulesConfig", rulesConfig.toJson());
        root.add("moveHistory", gson.toJsonTree(moveHistory));
        root.add("ruleChangeHistory", gson.toJsonTree(ruleChangeHistory));
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

    public void loadSyncState(JsonObject root) {
        if (root == null) return;
        if (root.has("rulesConfig")) {
            applySettingsSnapshot(root.getAsJsonObject("rulesConfig"));
        }
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
        this.moveHistory.clear();
        if (root.has("moveHistory")) {
            JsonArray moves = root.getAsJsonArray("moveHistory");
            for (JsonElement el : moves) {
                this.moveHistory.add(gson.fromJson(el, Move.class));
            }
        }
        this.ruleChangeHistory.clear();
        if (root.has("ruleChangeHistory")) {
            JsonArray changes = root.getAsJsonArray("ruleChangeHistory");
            for (JsonElement el : changes) {
                this.ruleChangeHistory.add(gson.fromJson(el, RuleChangeRecord.class));
            }
        }
        board.clearBoard();
        if (root.has("boardData")) {
            JsonArray boardData = root.getAsJsonArray("boardData");
            for (JsonElement el : boardData) {
                JsonObject cell = el.getAsJsonObject();
                int r = cell.get("row").getAsInt();
                int c = cell.get("col").getAsInt();
                JsonArray stackArr = cell.getAsJsonArray("stack");
                for (int i = 0; i < stackArr.size(); i++) {
                    Piece.Type type = Piece.Type.valueOf(stackArr.get(i).getAsString());
                    Piece piece = new Piece(type, r, c);
                    if (i == 0) {
                        board.setPiece(r, c, piece);
                    } else {
                        board.pushToStack(r, c, piece);
                    }
                }
            }
        }
        Board standardBoard = new Board();
        this.savedInitialBoard = standardBoard.deepCopy();
        this.savedInitialIsRedTurn = true;
        this.isInReplayMode = false;
        this.currentReplayStep = -1;
        this.validator.setRulesConfig(this.rulesConfig);
        notifyGameStateChanged();
    }
}
