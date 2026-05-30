package io.github.samera2022.chinese_chess.common.spi;

import io.github.samera2022.chinese_chess.common.model.Move;
import java.util.List;

public interface SimulationContext {
    ReadonlyBoard getBoard();
    boolean simulateMove(int fromRow, int fromCol, int toRow, int toCol);
    boolean simulateUndo();
    boolean isRedTurn();
    List<Move> getSimulatedMoves();
    SimulationContext fork();
    int evaluate();
    List<Move> generateLegalMoves();
}
