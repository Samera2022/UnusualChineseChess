package io.github.samera2022.chinese_chess.api.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.common.GameStateAccessor;
import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.model.RuleChangeRecord;

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
     * @param engine 游戏状态访问器
     * @param filePath 导出文件路径
     * @throws IOException 文件写入异常
     */
    public static void exportGameState(GameStateAccessor engine, String filePath) throws IOException {
        JsonObject root = new JsonObject();

        // 1. 当前时间
        root.addProperty("exportTime", LocalDateTime.now().format(formatter));

        // 2. 游戏基本信息
        JsonObject gameInfo = new JsonObject();
        gameInfo.addProperty("isRedTurn", engine.isRedTurn());
        gameInfo.addProperty("gameState", engine.getGameStatus().name());
        root.add("gameInfo", gameInfo);

        // 3. 当前棋盘状态
        JsonObject boardState = exportBoardState(engine.getBoardState());
        root.add("boardState", boardState);

        // 4. 着法记录
        JsonArray moveHistory = exportMoveHistory(engine.getMoveHistory());
        root.add("moveHistory", moveHistory);

        // 5. 玩法变更记录
        JsonArray ruleChangeHistory = exportRuleChangeHistory(engine.getRuleChangeHistory());
        root.add("ruleChangeHistory", ruleChangeHistory);

        // 6. 玩法设置（包含特殊规则和游戏配置）
        JsonObject settings = exportSettings(engine);
        root.add("settings", settings);

        // 写入文件
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(root, writer);
        }
    }

    /**
     * 导出棋盘状态
     */
    private static JsonObject exportBoardState(BoardState boardState) {
        JsonObject result = new JsonObject();
        result.addProperty("rows", boardState.getRows());
        result.addProperty("cols", boardState.getCols());

        // 导出所有棋子位置（包括堆栈信息）
        JsonArray pieces = new JsonArray();
        for (BoardState.StackEntry entry : boardState.getEntries()) {
            List<Piece.Type> types = entry.pieceTypes;
            for (int i = 0; i < types.size(); i++) {
                JsonObject pieceObj = new JsonObject();
                pieceObj.addProperty("type", types.get(i).name());
                pieceObj.addProperty("row", entry.row);
                pieceObj.addProperty("col", entry.col);
                // 添加堆栈索引（0为底部，依次递增）
                if (types.size() > 1) {
                    pieceObj.addProperty("stackIndex", i);
                }
                pieces.add(pieceObj);
            }
        }
        result.add("pieces", pieces);

        return result;
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

            // 导出强制走子标记
            moveObj.addProperty("forceMove", move.isForceMove());

            moves.add(moveObj);
        }

        return moves;
    }

    /**
     * 导出玩法变更记录
     */
    private static JsonArray exportRuleChangeHistory(List<RuleChangeRecord> ruleChangeHistory) {
        JsonArray ruleChanges = new JsonArray();

        for (RuleChangeRecord record : ruleChangeHistory) {
            JsonObject changeObj = new JsonObject();
            changeObj.addProperty("ruleKey", record.getRuleKey());
            changeObj.add("oldValue", gson.toJsonTree(record.getOldValue()));
            changeObj.add("newValue", gson.toJsonTree(record.getNewValue()));
            changeObj.addProperty("afterMoveIndex", record.getAfterMoveIndex());
            ruleChanges.add(changeObj);
        }

        return ruleChanges;
    }

    /**
     * 导出游戏设置
     */
    private static JsonObject exportSettings(GameStateAccessor engine) {
        JsonObject settings = new JsonObject();
        JsonObject rulesSnapshot = engine.getRulesSnapshot();

        // 基本设置
        settings.addProperty("allowUndo",
            rulesSnapshot.has("allow_undo") && rulesSnapshot.get("allow_undo").getAsBoolean());
        settings.addProperty("showHints",
            rulesSnapshot.has("show_hints") && rulesSnapshot.get("show_hints").getAsBoolean());

        // 特殊规则
        settings.add("specialRules", rulesSnapshot);

        return settings;
    }
}
