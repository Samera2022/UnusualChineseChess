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
            gameEngine.clearMoveHistory(); // 只清空 moveHistory
            importMoveHistory(gameEngine, root.getAsJsonArray("moveHistory"));
        }

        // 5. 导入玩法变更记录
        if (root.has("ruleChangeHistory")) {
            gameEngine.clearRuleChangeHistory(); // 只清空 ruleChangeHistory
            importRuleChangeHistory(gameEngine, root.getAsJsonArray("ruleChangeHistory"));
        }

        // 6. 重建初始状态（用于回放）
        rebuildInitialStateForReplay(gameEngine, root);

        // 7. 导入完成后，通知监听器刷新历史记录（确保UI端用getCombinedHistory()刷新显示）
        // 这里用null触发所有监听器的onMoveExecuted，实际项目可根据UI刷新机制调整
        try {
            java.lang.reflect.Field listenersField = gameEngine.getClass().getDeclaredField("listeners");
            listenersField.setAccessible(true);
            java.util.List<?> listeners = (java.util.List<?>) listenersField.get(gameEngine);
            for (Object listener : listeners) {
                // 先调用 onMoveExecuted(null)
                java.lang.reflect.Method onMoveExecuted = listener.getClass().getMethod("onMoveExecuted", io.github.samera2022.chinese_chess.model.Move.class);
                onMoveExecuted.invoke(listener, (Object) null);
                // 再调用 onGameStateChanged(GameEngine.GameState.RUNNING)
                java.lang.reflect.Method onGameStateChanged = listener.getClass().getMethod("onGameStateChanged", io.github.samera2022.chinese_chess.engine.GameEngine.GameState.class);
                onGameStateChanged.invoke(listener, io.github.samera2022.chinese_chess.engine.GameEngine.GameState.RUNNING);
            }
        } catch (Exception e) {
            // 反射失败则忽略，不影响主流程
        }
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
        rebuildInitialStateForReplay(gameEngine, root);
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
    private static void rebuildInitialStateForReplay(GameEngine gameEngine, JsonObject root) {
        // 保存当前的最终状态
        Board finalBoard = gameEngine.getBoard().deepCopy();
        boolean finalIsRedTurn = gameEngine.isRedTurn();

        // 重新导入初始棋盘状态（不执行着法）
        importBoardState(gameEngine, root.getAsJsonObject("boardState"));

        // 获取着法记录
        List<Move> moves = gameEngine.getMoveHistory();

        // 如果有着法记录，需要反推初始状态
        if (!moves.isEmpty()) {
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

            // 恢复到最终状态（包括堆栈）
            board.clearBoard();
            for (int row = 0; row < board.getRows(); row++) {
                for (int col = 0; col < board.getCols(); col++) {
                    List<Piece> stack = finalBoard.getStack(row, col);
                    for (Piece piece : stack) {
                        Piece pieceCopy = new Piece(piece.getType(), row, col);
                        board.pushToStack(row, col, pieceCopy);
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

        // 导入棋子 - 先按位置分组，然后按stackIndex排序
        if (boardState.has("pieces")) {
            JsonArray pieces = boardState.getAsJsonArray("pieces");

            // 使用Map来按位置分组棋子
            java.util.Map<String, List<JsonObject>> positionMap = new java.util.HashMap<>();

            for (JsonElement element : pieces) {
                JsonObject pieceObj = element.getAsJsonObject();
                int row = pieceObj.get("row").getAsInt();
                int col = pieceObj.get("col").getAsInt();
                String key = row + "," + col;

                positionMap.computeIfAbsent(key, k -> new ArrayList<>()).add(pieceObj);
            }

            // 对每个位置的棋子按stackIndex排序并添加到棋盘
            for (List<JsonObject> positionPieces : positionMap.values()) {
                // 按stackIndex排序（如果没有stackIndex，则视为0）
                positionPieces.sort((p1, p2) -> {
                    int idx1 = p1.has("stackIndex") ? p1.get("stackIndex").getAsInt() : 0;
                    int idx2 = p2.has("stackIndex") ? p2.get("stackIndex").getAsInt() : 0;
                    return Integer.compare(idx1, idx2);
                });

                // 按顺序添加棋子到堆栈（先添加的在底部）
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
            gameEngine.getRulesConfig().set(RuleRegistry.ALLOW_UNDO.registryName,
                    settings.get("allowUndo").getAsBoolean(),
                    API);
        }

        if (settings.has("showHints")) {
            gameEngine.getRulesConfig().set(RuleRegistry.SHOW_HINTS.registryName,
                    settings.get("showHints").getAsBoolean(),
                    API);
        }

        // 特殊规则 - 直接通过JSON加载到rulesConfig
        if (settings.has("specialRules")) {
            JsonObject specialRules = settings.getAsJsonObject("specialRules");
            // apply per-key to go through set(...) and notify listeners
            for (java.util.Map.Entry<String, JsonElement> e : specialRules.entrySet()) {
                String key = e.getKey();
                JsonElement el = e.getValue();
                if (el == null || el.isJsonNull()) {
                    gameEngine.getRulesConfig().set(key, null, API);
                } else if (el.isJsonPrimitive()) {
                    com.google.gson.JsonPrimitive p = el.getAsJsonPrimitive();
                    if (p.isBoolean()) {
                        gameEngine.getRulesConfig().set(key, p.getAsBoolean(), API);
                    } else if (p.isNumber()) {
                        try {
                            int iv = p.getAsInt();
                            gameEngine.getRulesConfig().set(key, iv, API);
                        } catch (NumberFormatException ex) {
                            gameEngine.getRulesConfig().set(key, p.getAsString(), API);
                        }
                    } else if (p.isString()) {
                        gameEngine.getRulesConfig().set(key, p.getAsString(), API);
                    }
                }
            }
        }

        // 注意：不需要手动同步到MoveValidator，因为它们共享同一个rulesConfig实例
    }

    /**
     * 导入着法记录
     */
    private static void importMoveHistory(GameEngine gameEngine, JsonArray moveHistory) {
        // 清空当前着法记录
        gameEngine.clearMoveHistory();


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

            Piece convertedPiece = null;
            boolean captureConversion = moveObj.has("captureConversion") && moveObj.get("captureConversion").getAsBoolean();
            if (captureConversion && moveObj.has("convertedPieceType")) {
                String convertedTypeName = moveObj.get("convertedPieceType").getAsString();
                Piece.Type convertedType = Piece.Type.valueOf(convertedTypeName);
                convertedPiece = new Piece(convertedType, toRow, toCol);
            }

            // 创建Move对象并添加到历史记录
            Move move = new Move(fromRow, fromCol, toRow, toCol, piece, capturedPiece);
            if (captureConversion) {
                move.setCaptureConversion(true);
                move.setConvertedPiece(convertedPiece);
            }
            // 设置强制走子标记
            if (moveObj.has("forceMove")) {
                move.setForceMove(moveObj.get("forceMove").getAsBoolean());
            }

            // 导入堆栈选择信息
            if (moveObj.has("selectedStackIndex")) {
                int selectedStackIndex = moveObj.get("selectedStackIndex").getAsInt();
                move.setSelectedStackIndex(selectedStackIndex);

                if (moveObj.has("movedStack")) {
                    JsonArray movedStackArray = moveObj.getAsJsonArray("movedStack");
                    List<Piece> movedStack = new ArrayList<>();
                    for (JsonElement stackElement : movedStackArray) {
                        String stackPieceTypeName = stackElement.getAsString();
                        Piece.Type stackPieceType = Piece.Type.valueOf(stackPieceTypeName);
                        movedStack.add(new Piece(stackPieceType, toRow, toCol));
                    }
                    move.setMovedStack(movedStack);
                }
            }

            gameEngine.addMoveToHistory(move);
        }
    }

    /**
     * 导入玩法变更记录
     */
    private static void importRuleChangeHistory(GameEngine gameEngine, JsonArray ruleChangeHistory) {
        for (JsonElement element : ruleChangeHistory) {
            JsonObject changeObj = element.getAsJsonObject();

            String ruleKey = changeObj.get("ruleKey").getAsString();
            String displayName = changeObj.get("displayName").getAsString();
            boolean enabled = changeObj.get("enabled").getAsBoolean();
            int afterMoveIndex = changeObj.get("afterMoveIndex").getAsInt();

            RuleChangeRecord record = new RuleChangeRecord(ruleKey, displayName, enabled, afterMoveIndex);
            gameEngine.addRuleChangeToHistory(record);
        }
    }
}

