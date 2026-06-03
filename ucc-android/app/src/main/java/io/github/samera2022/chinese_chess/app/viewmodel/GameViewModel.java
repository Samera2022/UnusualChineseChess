package io.github.samera2022.chinese_chess.app.viewmodel;

import android.graphics.Point;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import io.github.samera2022.chinese_chess.api.net.NetworkSession;
import io.github.samera2022.chinese_chess.common.GameConfig;
import io.github.samera2022.chinese_chess.common.GameStateListener;
import io.github.samera2022.chinese_chess.common.GameStatus;
import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.core.engine.GameEngine;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;

public class GameViewModel extends ViewModel implements GameStateListener {

    private final GameEngine engine;
    private NetworkSession netSession;
    private boolean isHost;

    private final MutableLiveData<BoardState> boardState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRedTurn = new MutableLiveData<>(true);
    private final MutableLiveData<String> gameStatus = new MutableLiveData<>(GameStatus.RUNNING.name());
    private final MutableLiveData<List<Move>> moveHistory = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Point> selectedPosition = new MutableLiveData<>(null);
    private final MutableLiveData<List<Point>> validMoves = new MutableLiveData<>(new ArrayList<>());

    public GameViewModel() {
        this.engine = new GameEngine();
        this.engine.addGameStateListener(this);
        boardState.setValue(engine.getBoardState());
    }

    public GameViewModel(GameRulesConfig rulesConfig) {
        this.engine = new GameEngine(rulesConfig);
        this.engine.addGameStateListener(this);
        boardState.setValue(engine.getBoardState());
    }

    /** 设置网络会话（局域网对局时调用） */
    public void setupNetworkSession(NetworkSession session, boolean isHost) {
        this.netSession = session;
        this.isHost = isHost;
        session.setListener(new NetworkSession.Listener() {
            @Override public void onPeerMove(int fr, int fc, int tr, int tc, int ssi) {
                engine.makeMove(fr, fc, tr, tc);
                refreshLiveData();
            }
            @Override public void onPeerRestart() { engine.restart(); refreshLiveData(); }
            @Override public void onDisconnected(String reason) {
                Log.d("NetSession", "Disconnected: " + reason);
            }
            @Override public void onConnected(String info) {
                Log.d("NetSession", "Connected: " + info);
            }
            @Override public void onPeerUndo() { engine.undoLastMove(); refreshLiveData(); }
            // unused callbacks
            @Override public void onPong(long s, long rtt) {}
            @Override public void onSettingsReceived(JsonObject s) {}
            @Override public void onPeerVersion(String v) {}
            @Override public void onForceMoveRequest(int fr, int fc, int tr, int tc, long seq, int hl, int ssi) {}
            @Override public void onForceMoveConfirm(int fr, int fc, int tr, int tc, long seq, int ssi) {}
            @Override public void onForceMoveReject(int fr, int fc, int tr, int tc, long seq, String r) {}
            @Override public void onForceMoveApplied(int fr, int fc, int tr, int tc, long seq, String p, int ssi) {}
        });
    }

    public void initGame(GameConfig config) {
        engine.restart();
        refreshLiveData();
        gameStatus.setValue(GameStatus.RUNNING.name());
        moveHistory.setValue(new ArrayList<>(engine.getMoveHistory()));
        isRedTurn.setValue(true);
    }

    public void initGame(JsonObject snapshot) {
        engine.loadSyncState(snapshot);
        refreshLiveData();
        gameStatus.setValue(engine.getGameState().name());
        moveHistory.setValue(new ArrayList<>(engine.getMoveHistory()));
        isRedTurn.setValue(engine.isRedTurn());
    }

    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        boolean success = engine.makeMove(fromRow, fromCol, toRow, toCol);
        if (success) {
            refreshLiveData();
            clearSelection();
            // 通过网络同步走子
            if (netSession != null && netSession.isConnected()) {
                netSession.sendMove(fromRow, fromCol, toRow, toCol, -1);
            }
        }
        return success;
    }

    public boolean undoLastMove() {
        boolean success = engine.undoLastMove();
        if (success) {
            refreshLiveData();
            gameStatus.setValue(GameStatus.RUNNING.name());
            clearSelection();
            if (netSession != null && netSession.isConnected()) {
                netSession.sendUndo();
            }
        }
        return success;
    }

    public void restart() {
        engine.restart();
        refreshLiveData();
        isRedTurn.setValue(true);
        gameStatus.setValue(GameStatus.RUNNING.name());
        moveHistory.setValue(new ArrayList<>());
        clearSelection();
        if (netSession != null && netSession.isConnected()) {
            netSession.sendRestart();
        }
    }

    public void selectPiece(int row, int col) {
        Piece piece = engine.getPiece(row, col);
        if (piece == null || piece.isRed() != engine.isRedTurn()) {
            clearSelection();
            return;
        }
        selectedPosition.setValue(new Point(row, col));
        List<Point> moves = new ArrayList<>();
        for (int r = 0; r < engine.getBoardRows(); r++) {
            for (int c = 0; c < engine.getBoardCols(); c++) {
                if (engine.isValidMove(row, col, r, c)) {
                    moves.add(new Point(r, c));
                }
            }
        }
        validMoves.setValue(moves);
    }

    public void clearSelection() {
        selectedPosition.setValue(null);
        validMoves.setValue(new ArrayList<>());
    }

    public GameEngine getEngine() { return engine; }
    public boolean isNetworkActive() { return netSession != null && netSession.isConnected(); }

    public LiveData<BoardState> getBoardStateLD() { return boardState; }
    public LiveData<Boolean> getIsRedTurn() { return isRedTurn; }
    public LiveData<String> getGameStatus() { return gameStatus; }
    public LiveData<List<Move>> getMoveHistory() { return moveHistory; }
    public LiveData<Point> getSelectedPosition() { return selectedPosition; }
    public LiveData<List<Point>> getValidMoves() { return validMoves; }

    @Override
    public void onGameStateChanged(GameStatus newState) {
        gameStatus.postValue(newState.name());
    }

    @Override
    public void onMoveExecuted(Move move) {
        boardState.postValue(engine.getBoardState());
        moveHistory.postValue(new ArrayList<>(engine.getMoveHistory()));
        isRedTurn.postValue(engine.isRedTurn());
    }

    private void refreshLiveData() {
        boardState.setValue(engine.getBoardState());
        moveHistory.setValue(new ArrayList<>(engine.getMoveHistory()));
        isRedTurn.setValue(engine.isRedTurn());
    }

    /** 强制刷新所有 LiveData（用于棋盘重建后更新 UI） */
    public void refreshAll() {
        refreshLiveData();
        gameStatus.setValue(engine.getGameState().name());
        clearSelection();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (netSession != null) netSession.close();
        engine.shutdown();
    }
}
