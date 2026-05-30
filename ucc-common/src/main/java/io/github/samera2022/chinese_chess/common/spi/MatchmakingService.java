package io.github.samera2022.chinese_chess.common.spi;

import java.util.function.Consumer;

public interface MatchmakingService {
    String createRoom(String playerId);
    String joinRoom(String playerId, String roomCode);
    void startMatchmaking(String playerId, Consumer<MatchSession> onMatched);
    void cancelMatchmaking(String playerId);
    void leaveMatch(String playerId);
}
