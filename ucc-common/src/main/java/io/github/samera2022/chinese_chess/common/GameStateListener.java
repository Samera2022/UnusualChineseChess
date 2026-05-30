package io.github.samera2022.chinese_chess.common;

import io.github.samera2022.chinese_chess.common.model.Move;

public interface GameStateListener {
    void onGameStateChanged(GameStatus newState);
    void onMoveExecuted(Move move);
}
