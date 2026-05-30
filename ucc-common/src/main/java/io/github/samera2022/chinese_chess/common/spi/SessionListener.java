package io.github.samera2022.chinese_chess.common.spi;

import io.github.samera2022.chinese_chess.common.GameStatus;
import io.github.samera2022.chinese_chess.common.model.Move;

public interface SessionListener {
    void onGameStateChanged(GameStatus newState);
    void onMoveExecuted(Move move);
    void onBoardChanged();
}
