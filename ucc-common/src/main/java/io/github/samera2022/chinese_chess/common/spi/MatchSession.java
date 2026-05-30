package io.github.samera2022.chinese_chess.common.spi;

import io.github.samera2022.chinese_chess.common.model.Piece;
import java.util.UUID;

public interface MatchSession {
    UUID getMatchId();
    String getRedPlayerId();
    String getBlackPlayerId();
    boolean isRedTurn();
    String submitMove(String playerId, int fromRow, int fromCol, int toRow, int toCol,
                      Piece.Type promotionType, int selectedStackIndex);
    boolean requestUndo(String playerId);
    void resign(String playerId);
    String getSyncSnapshotJson();
    boolean isFinished();
}
