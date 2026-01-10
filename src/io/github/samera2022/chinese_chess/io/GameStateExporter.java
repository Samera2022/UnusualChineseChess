package io.github.samera2022.chinese_chess.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.engine.Board;
import io.github.samera2022.chinese_chess.engine.GameEngine;
import io.github.samera2022.chinese_chess.model.Move;
import io.github.samera2022.chinese_chess.model.Piece;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 游戏状态导出器 - 将当前游戏状态导出为JSON文件
 */
public class GameStateExporter {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 导出游戏状态到JSON文件
     * @param gameEngine 游戏引擎
     * @param filePath 导出文件路径
     * @throws IOException 文件写入异常
     */
    public static void exportGameState(GameEngine gameEngine, String filePath) throws IOException {
        JsonObject root = new JsonObject();

        // 1. 当前时间
        root.addProperty("exportTime", LocalDateTime.now().format(formatter));

        // 2. 游戏基本信息
        JsonObject gameInfo = new JsonObject();
        gameInfo.addProperty("isRedTurn", gameEngine.isRedTurn());
        gameInfo.addProperty("gameState", gameEngine.getGameState().name());
        root.add("gameInfo", gameInfo);

        // 3. 当前棋盘状态
        JsonObject boardState = exportBoardState(gameEngine.getBoard());
        root.add("boardState", boardState);

        // 4. 着法记录
        JsonArray moveHistory = exportMoveHistory(gameEngine.getMoveHistory());
        root.add("moveHistory", moveHistory);

        // 5. 玩法设置（包含特殊规则和游戏配置）
        JsonObject settings = exportSettings(gameEngine);
        root.add("settings", settings);

        // 写入文件
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(root, writer);
        }
    }

    /**
     * 导出棋盘状态
     */
    private static JsonObject exportBoardState(Board board) {
        JsonObject boardState = new JsonObject();
        boardState.addProperty("rows", board.getRows());
        boardState.addProperty("cols", board.getCols());

        // 导出所有棋子位置（包括堆栈信息）
        JsonArray pieces = new JsonArray();
        for (int row = 0; row < board.getRows(); row++) {
            for (int col = 0; col < board.getCols(); col++) {
                List<Piece> stack = board.getStack(row, col);
                if (!stack.isEmpty()) {
                    // 导出该位置的堆栈
                    for (int i = 0; i < stack.size(); i++) {
                        Piece piece = stack.get(i);
                        JsonObject pieceObj = new JsonObject();
                        pieceObj.addProperty("type", piece.getType().name());
                        pieceObj.addProperty("row", row);
                        pieceObj.addProperty("col", col);
                        // 添加堆栈索引（0为底部，依次递增）
                        if (stack.size() > 1) {
                            pieceObj.addProperty("stackIndex", i);
                        }
                        pieces.add(pieceObj);
                    }
                }
            }
        }
        boardState.add("pieces", pieces);

        return boardState;
    }

    /**
     * 导出着法记录
     */
    private static JsonArray exportMoveHistory(List<Move> moveHistory) {
        JsonArray moves = new JsonArray();

        for (Move move : moveHistory) {
            JsonObject moveObj = new JsonObject();
            moveObj.addProperty("fromRow", move.getFromRow());
            moveObj.addProperty("fromCol", move.getFromCol());
            moveObj.addProperty("toRow", move.getToRow());
            moveObj.addProperty("toCol", move.getToCol());
            moveObj.addProperty("pieceType", move.getPiece().getType().name());

            if (move.getCapturedPiece() != null) {
                moveObj.addProperty("capturedPieceType", move.getCapturedPiece().getType().name());
            }
            if (move.isCaptureConversion() && move.getConvertedPiece() != null) {
                moveObj.addProperty("convertedPieceType", move.getConvertedPiece().getType().name());
                moveObj.addProperty("captureConversion", true);
            }

            // 导出堆栈选择信息
            if (move.getSelectedStackIndex() >= 0) {
                moveObj.addProperty("selectedStackIndex", move.getSelectedStackIndex());
                List<Piece> movedStack = move.getMovedStack();
                if (movedStack != null && !movedStack.isEmpty()) {
                    JsonArray movedStackArray = new JsonArray();
                    for (Piece p : movedStack) {
                        movedStackArray.add(p.getType().name());
                    }
                    moveObj.add("movedStack", movedStackArray);
                }
            }

            moves.add(moveObj);
        }

        return moves;
    }

    /**
     * 导出游戏设置
     */
    private static JsonObject exportSettings(GameEngine gameEngine) {
        JsonObject settings = new JsonObject();

        // 基本设置
        settings.addProperty("allowUndo", gameEngine.getRulesConfig().getBoolean(io.github.samera2022.chinese_chess.rules.RuleConstants.ALLOW_UNDO));
        settings.addProperty("showHints", gameEngine.getRulesConfig().getBoolean(io.github.samera2022.chinese_chess.rules.RuleConstants.SHOW_HINTS));

        // 特殊规则
        JsonObject specialRules = gameEngine.getRulesConfig().toJson();
        settings.add("specialRules", specialRules);

        return settings;
    }
}
