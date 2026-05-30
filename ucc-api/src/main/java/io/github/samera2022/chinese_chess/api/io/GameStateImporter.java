package io.github.samera2022.chinese_chess.api.io;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.common.GameStateAccessor;
import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.model.RuleChangeRecord;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏状态导入器 - 从JSON文件导入游戏状态
 */
public class GameStateImporter {
    private static final Gson gson = new Gson();

    /**
     * 从JSON文件导入游戏状态
     * @param engine 游戏状态访问器
     * @param filePath 导入文件路径
     * @throws IOException 文件读取异常
     * @throws IllegalArgumentException 文件格式错误
     */
    public static void importGameState(GameStateAccessor engine, String filePath) throws IOException {
        JsonObject root;

        // 读取文件
        try (FileReader reader = new FileReader(filePath)) {
            root = gson.fromJson(reader, JsonObject.class);
        }

        if (root == null) {
            throw new IllegalArgumentException("无效的JSON文件");
        }

        // 使用通用的导入逻辑
        importGameState(engine, root);
    }

    /**
     * 从JsonObject导入游戏状态（用于网络同步等场景）
     */
    public static void importGameState(GameStateAccessor engine, JsonObject root) {
        if (root == null) {
            throw new IllegalArgumentException("无效的JSON对象");
        }
        // 1. 导入玩法设置
        if (root.has("settings")) {
            importSettings(engine, root.getAsJsonObject("settings"));
        }
        // 2. 导入棋盘状态
        if (!root.has("boardState")) {
            throw new IllegalArgumentException("缺少棋盘状态信息");
        }
        importBoardState(engine, root.getAsJsonObject("boardState"));
        // 3. 导入游戏基本信息
        if (root.has("gameInfo")) {
            importGameInfo(engine, root.getAsJsonObject("gameInfo"));
        }
        // 4. 导入着法记录
        if (root.has("moveHistory")) {
            engine.clearMoveHistory();
            importMoveHistory(engine, root.getAsJsonArray("moveHistory"));
        }
        // 5. 导入玩法变更记录
        if (root.has("ruleChangeHistory")) {
            engine.clearRuleChangeHistory();
            importRuleChangeHistory(engine, root.getAsJsonArray("ruleChangeHistory"));
        }
        // 6. 重建初始状态（用于回放）
        rebuildInitialStateForReplay(engine);
        // 7. 通知监听器刷新历史记录
        engine.notifyListenersRefresh();
    }

    /**
     * 重建初始状态用于回放
     */
    private static void rebuildInitialStateForReplay(GameStateAccessor engine) {
        // 保存当前的最终状态和规则
        BoardState finalBoard = engine.getBoardState();
        boolean finalIsRedTurn = engine.isRedTurn();
        List<Move> savedMoves = new ArrayList<>(engine.getMoveHistory());
        List<RuleChangeRecord> savedRuleChanges = new ArrayList<>(engine.getRuleChangeHistory());
        JsonObject savedSettings = engine.getRulesSnapshot();

        // 清空引擎状态，但不重置规则
        engine.restart();

        // 如果有着法记录，需要反推初始状态
        if (!savedMoves.isEmpty()) {
            // 恢复最终规则，以便撤销时使用
            engine.applyRulesSnapshot(savedSettings);

            // 先将棋盘设置为最终状态
            engine.loadBoardState(finalBoard);
            engine.setRedTurn(finalIsRedTurn);

            // 将所有着法和规则变更临时放入引擎历史记录中，以便撤销
            engine.clearMoveHistory();
            for (Move m : savedMoves) engine.addMoveToHistory(m);
            engine.clearRuleChangeHistory();
            for (RuleChangeRecord r : savedRuleChanges) engine.addRuleChangeToHistory(r);

            // 反向撤销所有着法来获得初始状态
            while (!engine.getMoveHistory().isEmpty()) {
                engine.undoLastMove();
            }
        }

        // 现在引擎处于初始状态，保存它
        engine.saveInitialStateForReplay();

        // 恢复到最终状态
        engine.loadBoardState(finalBoard);
        engine.setRedTurn(finalIsRedTurn);
        engine.applyRulesSnapshot(savedSettings);

        // 恢复历史记录
        engine.clearMoveHistory();
        for (Move m : savedMoves) engine.addMoveToHistory(m);
        engine.clearRuleChangeHistory();
        for (RuleChangeRecord r : savedRuleChanges) engine.addRuleChangeToHistory(r);
    }


    /**
     * 导入棋盘状态
     */
    private static void importBoardState(GameStateAccessor engine, JsonObject boardState) {
        engine.clearBoardPieces();

        if (boardState.has("pieces")) {
            JsonArray pieces = boardState.getAsJsonArray("pieces");
            Map<String, List<JsonObject>> positionMap = new HashMap<>();

            for (JsonElement element : pieces) {
                JsonObject pieceObj = element.getAsJsonObject();
                int row = pieceObj.get("row").getAsInt();
                int col = pieceObj.get("col").getAsInt();
                String key = row + "," + col;
                positionMap.computeIfAbsent(key, k -> new ArrayList<>()).add(pieceObj);
            }

            for (List<JsonObject> positionPieces : positionMap.values()) {
                positionPieces.sort((p1, p2) -> {
                    int idx1 = p1.has("stackIndex") ? p1.get("stackIndex").getAsInt() : 0;
                    int idx2 = p2.has("stackIndex") ? p2.get("stackIndex").getAsInt() : 0;
                    return Integer.compare(idx1, idx2);
                });

                for (JsonObject pieceObj : positionPieces) {
                    String typeName = pieceObj.get("type").getAsString();
                    int row = pieceObj.get("row").getAsInt();
                    int col = pieceObj.get("col").getAsInt();
                    try {
                        Piece.Type type = Piece.Type.valueOf(typeName);
                        engine.addPieceToBoard(row, col, type);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("未知的棋子类型: " + typeName);
                    }
                }
            }
        }
    }

    /**
     * 导入游戏基本信息
     */
    private static void importGameInfo(GameStateAccessor engine, JsonObject gameInfo) {
        if (gameInfo.has("isRedTurn")) {
            engine.setRedTurn(gameInfo.get("isRedTurn").getAsBoolean());
        }
    }

    /**
     * 导入游戏设置
     */
    private static void importSettings(GameStateAccessor engine, JsonObject settings) {
        if (settings.has("specialRules")) {
            JsonObject specialRules = settings.getAsJsonObject("specialRules");
            // 使用 applyRulesSnapshot 一次性应用所有规则
            engine.applyRulesSnapshot(specialRules);
        }
    }

    /**
     * 导入着法记录
     */
    private static void importMoveHistory(GameStateAccessor engine, JsonArray moveHistory) {
        engine.clearMoveHistory();
        for (JsonElement element : moveHistory) {
            JsonObject moveObj = element.getAsJsonObject();

            int fromRow = moveObj.get("fromRow").getAsInt();
            int fromCol = moveObj.get("fromCol").getAsInt();
            int toRow = moveObj.get("toRow").getAsInt();
            int toCol = moveObj.get("toCol").getAsInt();

            Piece piece = null;
            if (moveObj.has("pieceType")) {
                piece = new Piece(Piece.Type.valueOf(moveObj.get("pieceType").getAsString()), toRow, toCol);
            }

            Piece capturedPiece = null;
            if (moveObj.has("capturedPieceType")) {
                capturedPiece = new Piece(Piece.Type.valueOf(moveObj.get("capturedPieceType").getAsString()), toRow, toCol);
            }

            Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedPiece);

            if (moveObj.has("capturedStack")) {
                List<Piece> capturedStack = new ArrayList<>();
                for (JsonElement stackEl : moveObj.getAsJsonArray("capturedStack")) {
                    capturedStack.add(gson.fromJson(stackEl, Piece.class));
                }
                move.setCapturedStack(capturedStack);
            }

            if (moveObj.has("movedStack")) {
                List<Piece> movedStack = new ArrayList<>();
                for (JsonElement stackEl : moveObj.getAsJsonArray("movedStack")) {
                    movedStack.add(new Piece(Piece.Type.valueOf(stackEl.getAsString()), toRow, toCol));
                }
                move.setMovedStack(movedStack);
            }

            if (moveObj.has("selectedStackIndex")) {
                move.setSelectedStackIndex(moveObj.get("selectedStackIndex").getAsInt());
            }
            if (moveObj.has("isStacking")) {
                move.setStacking(moveObj.get("isStacking").getAsBoolean());
            }
            if (moveObj.has("forceMove")) {
                move.setForceMove(moveObj.get("forceMove").getAsBoolean());
            }

            engine.addMoveToHistory(move);
        }
    }

    /**
     * 导入玩法变更记录
     */
    private static void importRuleChangeHistory(GameStateAccessor engine, JsonArray ruleChangeHistory) {
        if (ruleChangeHistory == null) return;
        for (JsonElement element : ruleChangeHistory) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject changeObj = element.getAsJsonObject();

            RuleChangeRecord record = gson.fromJson(changeObj, RuleChangeRecord.class);
            if (record != null && record.getRuleKey() != null) {
                engine.addRuleChangeToHistory(record);
            }
        }
    }
}
