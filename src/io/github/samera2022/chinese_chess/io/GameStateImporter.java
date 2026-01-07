package io.github.samera2022.chinese_chess.io;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.engine.Board;
import io.github.samera2022.chinese_chess.engine.GameEngine;
import io.github.samera2022.chinese_chess.model.Move;
import io.github.samera2022.chinese_chess.model.Piece;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏状态导入器 - 从JSON文件导入游戏状态
 */
public class GameStateImporter {
    private static final Gson gson = new Gson();

    /**
     * 从JSON文件导入游戏状态
     * @param gameEngine 游戏引擎
     * @param filePath 导入文件路径
     * @throws IOException 文件读取异常
     * @throws IllegalArgumentException 文件格式错误
     */
    public static void importGameState(GameEngine gameEngine, String filePath) throws IOException {
        JsonObject root;

        // 读取文件
        try (FileReader reader = new FileReader(filePath)) {
            root = gson.fromJson(reader, JsonObject.class);
        }

        if (root == null) {
            throw new IllegalArgumentException("无效的JSON文件");
        }

        // 1. 导入玩法设置（必须先设置，因为会影响后续的移动验证）
        if (root.has("settings")) {
            importSettings(gameEngine, root.getAsJsonObject("settings"));
        }

        // 2. 导入棋盘状态（这是最终状态）
        if (!root.has("boardState")) {
            throw new IllegalArgumentException("缺少棋盘状态信息");
        }
        importBoardState(gameEngine, root.getAsJsonObject("boardState"));

        // 3. 导入游戏基本信息
        if (root.has("gameInfo")) {
            importGameInfo(gameEngine, root.getAsJsonObject("gameInfo"));
        }

        // 4. 导入着法记录
        if (root.has("moveHistory")) {
            importMoveHistory(gameEngine, root.getAsJsonArray("moveHistory"));
        }

        // 5. 重建初始状态（用于回放）
        rebuildInitialStateForReplay(gameEngine, root);
    }

    /**
     * 重建初始状态用于回放
     */
    private static void rebuildInitialStateForReplay(GameEngine gameEngine, JsonObject root) {
        // 保存当前的最终状态
        Board finalBoard = gameEngine.getBoard().deepCopy();
        boolean finalIsRedTurn = gameEngine.isRedTurn();

        // 重新导入初始棋盘状态（不执行着法）
        importBoardState(gameEngine, root.getAsJsonObject("boardState"));

        // 获取着法记录
        List<Move> moves = gameEngine.getMoveHistory();

        // 如果有着法记录，需要反推初始状态
        if (moves.size() > 0) {
            // 清空着法记录，临时保存
            List<Move> savedMoves = new ArrayList<>(moves);
            gameEngine.clearMoveHistory();

            // 反向撤销所有着法来获得初始状态
            Board board = gameEngine.getBoard();
            boolean isRedTurn = finalIsRedTurn;

            // 从后往前撤销每一步
            for (int i = savedMoves.size() - 1; i >= 0; i--) {
                Move move = savedMoves.get(i);

                // 获取目标位置的棋子
                Piece piece = board.getPiece(move.getToRow(), move.getToCol());
                if (piece != null) {
                    // 移回原位置
                    piece.move(move.getFromRow(), move.getFromCol());
                    board.setPiece(move.getFromRow(), move.getFromCol(), piece);
                    board.setPiece(move.getToRow(), move.getToCol(), null);

                    // 恢复被吃的棋子
                    if (move.getCapturedPiece() != null) {
                        Piece captured = new Piece(
                            move.getCapturedPiece().getType(),
                            move.getToRow(),
                            move.getToCol()
                        );
                        board.setPiece(move.getToRow(), move.getToCol(), captured);
                    }
                }

                // 切换回合（反向）
                isRedTurn = !isRedTurn;
            }

            // 现在board是初始状态，保存它
            gameEngine.setRedTurn(isRedTurn);
            gameEngine.saveInitialStateForReplay();

            // 恢复着法记录
            for (Move move : savedMoves) {
                gameEngine.addMoveToHistory(move);
            }

            // 恢复到最终状态
            board.clearBoard();
            for (int row = 0; row < board.getRows(); row++) {
                for (int col = 0; col < board.getCols(); col++) {
                    Piece piece = finalBoard.getPiece(row, col);
                    if (piece != null) {
                        Piece pieceCopy = new Piece(piece.getType(), row, col);
                        board.setPiece(row, col, pieceCopy);
                    }
                }
            }
            gameEngine.setRedTurn(finalIsRedTurn);
        } else {
            // 没有着法记录，当前状态就是初始状态
            gameEngine.saveInitialStateForReplay();
        }
    }

    /**
     * 导入棋盘状态
     */
    private static void importBoardState(GameEngine gameEngine, JsonObject boardState) {
        Board board = gameEngine.getBoard();

        // 清空棋盘
        board.clearBoard();

        // 导入棋子
        if (boardState.has("pieces")) {
            JsonArray pieces = boardState.getAsJsonArray("pieces");
            for (JsonElement element : pieces) {
                JsonObject pieceObj = element.getAsJsonObject();

                String typeName = pieceObj.get("type").getAsString();
                int row = pieceObj.get("row").getAsInt();
                int col = pieceObj.get("col").getAsInt();

                try {
                    Piece.Type type = Piece.Type.valueOf(typeName);
                    Piece piece = new Piece(type, row, col);
                    board.setPiece(row, col, piece);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("未知的棋子类型: " + typeName);
                }
            }
        }
    }

    /**
     * 导入游戏基本信息
     */
    private static void importGameInfo(GameEngine gameEngine, JsonObject gameInfo) {
        if (gameInfo.has("isRedTurn")) {
            boolean isRedTurn = gameInfo.get("isRedTurn").getAsBoolean();
            gameEngine.setRedTurn(isRedTurn);
        }

        // 游戏状态会在导入后自动检测，所以不需要手动设置
    }

    /**
     * 导入游戏设置
     */
    private static void importSettings(GameEngine gameEngine, JsonObject settings) {
        // 基本设置
        if (settings.has("allowUndo")) {
            gameEngine.setAllowUndo(settings.get("allowUndo").getAsBoolean());
        }

        if (settings.has("showHints")) {
            gameEngine.setShowHints(settings.get("showHints").getAsBoolean());
        }

        // 特殊规则
        if (settings.has("specialRules")) {
            JsonObject specialRules = settings.getAsJsonObject("specialRules");
            for (String key : specialRules.keySet()) {
                boolean value = specialRules.get(key).getAsBoolean();
                gameEngine.setSpecialRule(key, value);
            }
        }
    }

    /**
     * 导入着法记录
     */
    private static void importMoveHistory(GameEngine gameEngine, JsonArray moveHistory) {
        // 清空当前着法记录
        gameEngine.clearMoveHistory();

        Board board = gameEngine.getBoard();

        // 恢复每一步着法记录
        for (JsonElement element : moveHistory) {
            JsonObject moveObj = element.getAsJsonObject();

            int fromRow = moveObj.get("fromRow").getAsInt();
            int fromCol = moveObj.get("fromCol").getAsInt();
            int toRow = moveObj.get("toRow").getAsInt();
            int toCol = moveObj.get("toCol").getAsInt();

            String pieceTypeName = moveObj.get("pieceType").getAsString();
            Piece.Type pieceType = Piece.Type.valueOf(pieceTypeName);

            // 重建移动的棋子对象（注意：这里的坐标是移动后的位置）
            Piece piece = new Piece(pieceType, toRow, toCol);

            // 重建被吃的棋子（如果有）
            Piece capturedPiece = null;
            if (moveObj.has("capturedPieceType")) {
                String capturedTypeName = moveObj.get("capturedPieceType").getAsString();
                Piece.Type capturedType = Piece.Type.valueOf(capturedTypeName);
                capturedPiece = new Piece(capturedType, toRow, toCol);
            }

            // 创建Move对象并添加到历史记录
            Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedPiece);
            gameEngine.addMoveToHistory(move);
        }
    }
}

