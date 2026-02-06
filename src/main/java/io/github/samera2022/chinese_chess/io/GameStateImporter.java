package io.github.samera2022.chinese_chess.io;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.engine.Board;
import io.github.samera2022.chinese_chess.engine.GameEngine;
import io.github.samera2022.chinese_chess.model.Move;
import io.github.samera2022.chinese_chess.model.Piece;
import io.github.samera2022.chinese_chess.model.RuleChangeRecord;
import io.github.samera2022.chinese_chess.rules.RuleRegistry;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.github.samera2022.chinese_chess.rules.GameRulesConfig.ChangeSource.API;

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

        // 使用通用的导入逻辑
        importGameState(gameEngine, root);
    }

    /**
     * 从JsonObject导入游戏状态（用于网络同步等场景）
     */
    public static void importGameState(GameEngine gameEngine, JsonObject root) {
        if (root == null) {
            throw new IllegalArgumentException("无效的JSON对象");
        }
        // 1. 导入玩法设置
        if (root.has("settings")) {
            importSettings(gameEngine, root.getAsJsonObject("settings"));
        }
        // 2. 导入棋盘状态
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
            gameEngine.clearMoveHistory();
            importMoveHistory(gameEngine, root.getAsJsonArray("moveHistory"));
        }
        // 5. 导入玩法变更记录
        if (root.has("ruleChangeHistory")) {
            gameEngine.clearRuleChangeHistory();
            importRuleChangeHistory(gameEngine, root.getAsJsonArray("ruleChangeHistory"));
        }
        // 6. 重建初始状态（用于回放）
        rebuildInitialStateForReplay(gameEngine);
        // 7. 通知监听器刷新历史记录
        try {
            java.lang.reflect.Field listenersField = gameEngine.getClass().getDeclaredField("listeners");
            listenersField.setAccessible(true);
            java.util.List<?> listeners = (java.util.List<?>) listenersField.get(gameEngine);
            for (Object listener : listeners) {
                java.lang.reflect.Method onMoveExecuted = listener.getClass().getMethod("onMoveExecuted", io.github.samera2022.chinese_chess.model.Move.class);
                onMoveExecuted.invoke(listener, (Object) null);
                java.lang.reflect.Method onGameStateChanged = listener.getClass().getMethod("onGameStateChanged", io.github.samera2022.chinese_chess.engine.GameEngine.GameState.class);
                onGameStateChanged.invoke(listener, io.github.samera2022.chinese_chess.engine.GameEngine.GameState.RUNNING);
            }
        } catch (Exception e) {
            // 反射失败则忽略
        }
    }

    /**
     * 重建初始状态用于回放
     */
    private static void rebuildInitialStateForReplay(GameEngine gameEngine) {
        // 保存当前的最终状态和规则
        Board finalBoard = gameEngine.getBoard().deepCopy();
        boolean finalIsRedTurn = gameEngine.isRedTurn();
        List<Move> savedMoves = new ArrayList<>(gameEngine.getMoveHistory());
        List<RuleChangeRecord> savedRuleChanges = new ArrayList<>(gameEngine.getRuleChangeHistory());
        JsonObject savedSettings = gameEngine.getRulesConfig().toJson();

        // 清空引擎状态，但不重置规则
        gameEngine.restart();

        // 如果有着法记录，需要反推初始状态
        if (!savedMoves.isEmpty()) {
            // 恢复最终规则，以便撤销时使用
            gameEngine.getRulesConfig().applySnapshot(savedSettings, API);
            
            // 先将棋盘设置为最终状态
            gameEngine.getBoard().clearBoard();
            for (int row = 0; row < finalBoard.getRows(); row++) {
                for (int col = 0; col < finalBoard.getCols(); col++) {
                    for (Piece piece : finalBoard.getStack(row, col)) {
                        gameEngine.getBoard().pushToStack(row, col, new Piece(piece.getType(), row, col));
                    }
                }
            }
            gameEngine.setRedTurn(finalIsRedTurn);

            // 将所有着法和规则变更临时放入引擎历史记录中，以便撤销
            gameEngine.clearMoveHistory();
            for(Move m : savedMoves) gameEngine.addMoveToHistory(m);
            gameEngine.clearRuleChangeHistory();
            for(RuleChangeRecord r : savedRuleChanges) gameEngine.addRuleChangeToHistory(r);

            // 反向撤销所有着法来获得初始状态
            while(!gameEngine.getMoveHistory().isEmpty()) {
                gameEngine.undoLastMove();
            }
        }
        
        // 现在引擎处于初始状态，保存它
        gameEngine.saveInitialStateForReplay();

        // 恢复到最终状态
        gameEngine.getBoard().clearBoard();
        for (int row = 0; row < finalBoard.getRows(); row++) {
            for (int col = 0; col < finalBoard.getCols(); col++) {
                for (Piece piece : finalBoard.getStack(row, col)) {
                    gameEngine.getBoard().pushToStack(row, col, new Piece(piece.getType(), row, col));
                }
            }
        }
        gameEngine.setRedTurn(finalIsRedTurn);
        gameEngine.getRulesConfig().applySnapshot(savedSettings, API);
        
        // 恢复历史记录
        gameEngine.clearMoveHistory();
        for(Move m : savedMoves) gameEngine.addMoveToHistory(m);
        gameEngine.clearRuleChangeHistory();
        for(RuleChangeRecord r : savedRuleChanges) gameEngine.addRuleChangeToHistory(r);
    }


    /**
     * 导入棋盘状态
     */
    private static void importBoardState(GameEngine gameEngine, JsonObject boardState) {
        Board board = gameEngine.getBoard();
        board.clearBoard();

        if (boardState.has("pieces")) {
            JsonArray pieces = boardState.getAsJsonArray("pieces");
            java.util.Map<String, List<JsonObject>> positionMap = new java.util.HashMap<>();

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
                        Piece piece = new Piece(type, row, col);
                        board.pushToStack(row, col, piece);
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
    private static void importGameInfo(GameEngine gameEngine, JsonObject gameInfo) {
        if (gameInfo.has("isRedTurn")) {
            gameEngine.setRedTurn(gameInfo.get("isRedTurn").getAsBoolean());
        }
    }

    /**
     * 导入游戏设置
     */
    private static void importSettings(GameEngine gameEngine, JsonObject settings) {
        if (settings.has("specialRules")) {
            JsonObject specialRules = settings.getAsJsonObject("specialRules");
            // 使用 applySnapshot 一次性应用所有规则，并触发一次一致性检查
            gameEngine.getRulesConfig().applySnapshot(specialRules, API);
        }
    }

    /**
     * 导入着法记录
     */
    private static void importMoveHistory(GameEngine gameEngine, JsonArray moveHistory) {
        gameEngine.clearMoveHistory();
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
                    // 假设 movedStack 是字符串数组
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

            gameEngine.addMoveToHistory(move);
        }
    }

    /**
     * 导入玩法变更记录
     */
    private static void importRuleChangeHistory(GameEngine gameEngine, JsonArray ruleChangeHistory) {
        if (ruleChangeHistory == null) return;
        for (JsonElement element : ruleChangeHistory) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject changeObj = element.getAsJsonObject();
            
            RuleChangeRecord record = gson.fromJson(changeObj, RuleChangeRecord.class);
            if (record != null && record.getRuleKey() != null) {
                gameEngine.addRuleChangeToHistory(record);
            }
        }
    }
}
