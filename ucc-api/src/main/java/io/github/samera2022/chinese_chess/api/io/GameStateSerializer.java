package io.github.samera2022.chinese_chess.api.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.common.GameStateAccessor;
import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.model.RuleChangeRecord;

/**
 * 游戏状态序列化器 - 提供 JSON 序列化/反序列化静态方法，
 * 将 GameEngine 中 getSyncState() 和 loadSyncState() 的逻辑提取到此。
 */
public class GameStateSerializer {
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(RuleChangeRecord.class,
                (com.google.gson.InstanceCreator<RuleChangeRecord>) type -> new RuleChangeRecord())
            .create();

    /**
     * 将当前游戏状态序列化为 JsonObject。
     * @param engine 游戏状态访问器
     * @return 序列化后的 JsonObject
     */
    public static JsonObject serialize(GameStateAccessor engine) {
        JsonObject root = new JsonObject();
        root.addProperty("isRedTurn", engine.isRedTurn());
        root.addProperty("gameState", engine.getGameStatus().name());
        root.add("rulesConfig", engine.getRulesSnapshot());
        root.add("moveHistory", gson.toJsonTree(engine.getMoveHistory()));
        root.add("ruleChangeHistory", gson.toJsonTree(engine.getRuleChangeHistory()));

        BoardState boardState = engine.getBoardState();
        JsonArray boardArray = new JsonArray();
        for (BoardState.StackEntry entry : boardState.getEntries()) {
            JsonObject cellObj = new JsonObject();
            cellObj.addProperty("row", entry.row);
            cellObj.addProperty("col", entry.col);
            JsonArray stackArr = new JsonArray();
            for (Piece.Type type : entry.pieceTypes) {
                stackArr.add(type.name());
            }
            cellObj.add("stack", stackArr);
            boardArray.add(cellObj);
        }
        root.add("boardData", boardArray);
        return root;
    }

    /**
     * 从 JsonObject 反序列化并应用游戏状态。
     * @param engine 游戏状态访问器
     * @param root 包含序列化状态的 JsonObject
     */
    public static void deserialize(GameStateAccessor engine, JsonObject root) {
        if (root == null) return;

        // 恢复规则配置
        if (root.has("rulesConfig")) {
            engine.applyRulesSnapshot(root.getAsJsonObject("rulesConfig"));
        }
        // 恢复当前走子方
        if (root.has("isRedTurn")) {
            engine.setRedTurn(root.get("isRedTurn").getAsBoolean());
        }
        // gameState 由 GameEngine.loadSyncState 内部自行恢复（通过 GameStatus.valueOf）
        // GameStateSerializer 通过 GameStateAccessor 接口无法直接设置 gameState 字段

        // 恢复着法记录
        engine.clearMoveHistory();
        if (root.has("moveHistory")) {
            JsonArray moves = root.getAsJsonArray("moveHistory");
            for (JsonElement el : moves) {
                Move move = gson.fromJson(el, Move.class);
                engine.addMoveToHistory(move);
            }
        }

        // 恢复玩法变更记录
        engine.clearRuleChangeHistory();
        if (root.has("ruleChangeHistory")) {
            JsonArray changes = root.getAsJsonArray("ruleChangeHistory");
            for (JsonElement el : changes) {
                RuleChangeRecord record = gson.fromJson(el, RuleChangeRecord.class);
                engine.addRuleChangeToHistory(record);
            }
        }

        // 恢复棋盘数据
        engine.clearBoardPieces();
        if (root.has("boardData")) {
            JsonArray boardData = root.getAsJsonArray("boardData");
            for (JsonElement el : boardData) {
                JsonObject cell = el.getAsJsonObject();
                int r = cell.get("row").getAsInt();
                int c = cell.get("col").getAsInt();
                JsonArray stackArr = cell.getAsJsonArray("stack");
                for (int i = 0; i < stackArr.size(); i++) {
                    Piece.Type type = Piece.Type.valueOf(stackArr.get(i).getAsString());
                    engine.addPieceToBoard(r, c, type);
                }
            }
        }

        // 保存初始状态快照
        engine.saveInitialStateForReplay();

        // 通知监听器刷新
        engine.notifyListenersRefresh();
    }
}
