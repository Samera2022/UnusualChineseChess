package io.github.samera2022.chinese_chess.common.spi;

import io.github.samera2022.chinese_chess.common.model.Piece;
import java.util.List;

public interface ReadonlyBoard {
    int getRows();
    int getCols();
    Piece getPiece(int row, int col);
    List<Piece> getStack(int row, int col);
    int getStackSize(int row, int col);
    List<Piece> getRedPieces();
    List<Piece> getBlackPieces();
    Piece getRedKing();
    Piece getBlackKing();
    boolean isValid(int row, int col);
}
