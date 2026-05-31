package io.github.samera2022.chinese_chess.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Move;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.core.engine.Board;
import io.github.samera2022.chinese_chess.core.engine.SimulationBoard;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.core.rules.RulesConfigProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java 命令行入口类 —— 供 Python 训练脚本通过 subprocess 调用。
 *
 * <h3>支持的命令</h3>
 * <ul>
 *   <li>{@code simulate}  – 模拟走子，返回合法性</li>
 *   <li>{@code legal_moves} – 生成当前回合方所有合法走法</li>
 *   <li>{@code evaluate}   – 启发式局面评估</li>
 *   <li>{@code new_game}   – 创建新棋盘</li>
 * </ul>
 *
 * <p>所有输入/输出均为 JSON，通过 stdin 参数和 stdout 交互。</p>
 */
public class PyBridge {

    private static final Gson GSON = new GsonBuilder().create();

    /**
     * 入口方法。
     *
     * @param args 命令行参数，格式为 {@code --key value --key value ...}
     */
    public static void main(String[] args) {
        try {
            Map<String, String> argMap = parseArgs(args);

            String command = argMap.get("--command");
            if (command == null || command.isEmpty()) {
                outputError("Missing required argument: --command");
                return;
            }

            switch (command) {
                case "simulate":
                    handleSimulate(argMap);
                    break;
                case "legal_moves":
                    handleLegalMoves(argMap);
                    break;
                case "evaluate":
                    handleEvaluate(argMap);
                    break;
                case "new_game":
                    handleNewGame(argMap);
                    break;
                default:
                    outputError("Unknown command: " + command);
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            outputError(msg != null ? msg : e.getClass().getSimpleName());
        }
    }

    // ──────────────────────────────────────────────
    // 命令行参数解析
    // ──────────────────────────────────────────────

    /**
     * 将 {@code --key value} 形式的参数解析为有序映射。
     * 没有 value 的开关参数值设为 {@code "true"}。
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i];
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    map.put(key, args[++i]);
                } else {
                    map.put(key, "true");
                }
            }
        }
        return map;
    }

    // ──────────────────────────────────────────────
    // 命令处理器
    // ──────────────────────────────────────────────

    /**
     * 处理 {@code simulate} 命令。
     *
     * <p>检查走子合法性；若合法则执行模拟走子。</p>
     */
    private static void handleSimulate(Map<String, String> argMap) {
        BoardState boardState = parseBoardState(requireArg(argMap, "--board"));
        JsonObject moveJson = GSON.fromJson(requireArg(argMap, "--move"), JsonObject.class);

        int fromRow = moveJson.get("fromRow").getAsInt();
        int fromCol = moveJson.get("fromCol").getAsInt();
        int toRow   = moveJson.get("toRow").getAsInt();
        int toCol   = moveJson.get("toCol").getAsInt();

        applyRulesSnapshot(argMap);

        Board board = Board.fromState(boardState);
        SimulationBoard simBoard = new SimulationBoard(board);

        boolean legal = simBoard.isValidMove(fromRow, fromCol, toRow, toCol);
        if (legal) {
            simBoard.simulateMove(fromRow, fromCol, toRow, toCol);
        }

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("legal", legal);
        System.out.println(GSON.toJson(result));
    }

    /**
     * 处理 {@code legal_moves} 命令。
     *
     * <p>返回当前回合方所有合法走法列表。</p>
     */
    private static void handleLegalMoves(Map<String, String> argMap) {
        requireArg(argMap, "--board");
        requireRules(argMap);
        BoardState boardState = parseBoardState(argMap.get("--board"));
        applyRulesSnapshot(argMap);

        Board board = Board.fromState(boardState);
        SimulationBoard simBoard = new SimulationBoard(board);
        List<Move> moves = simBoard.generateLegalMoves();

        JsonArray movesArray = new JsonArray();
        for (Move m : moves) {
            JsonObject mj = new JsonObject();
            mj.addProperty("fromRow", m.getFromRow());
            mj.addProperty("fromCol", m.getFromCol());
            mj.addProperty("toRow",   m.getToRow());
            mj.addProperty("toCol",   m.getToCol());
            movesArray.add(mj);
        }

        JsonObject result = new JsonObject();
        result.add("moves", movesArray);
        System.out.println(GSON.toJson(result));
    }

    /**
     * 处理 {@code evaluate} 命令。
     *
     * <p>返回当前局面的启发式评估分值（红方优势为正）。</p>
     */
    private static void handleEvaluate(Map<String, String> argMap) {
        BoardState boardState = parseBoardState(requireArg(argMap, "--board"));
        applyRulesSnapshot(argMap);

        Board board = Board.fromState(boardState);
        SimulationBoard simBoard = new SimulationBoard(board);

        int score = simBoard.evaluate();

        JsonObject result = new JsonObject();
        result.addProperty("score", score);
        System.out.println(GSON.toJson(result));
    }

    /**
     * 处理 {@code new_game} 命令。
     *
     * <p>创建新棋盘并返回完整的 BoardState JSON。</p>
     */
    private static void handleNewGame(Map<String, String> argMap) {
        requireRules(argMap);
        int rows = 10;
        if (argMap.containsKey("--rows")) {
            try {
                rows = Integer.parseInt(argMap.get("--rows"));
            } catch (NumberFormatException e) {
                outputError("Invalid --rows argument: " + argMap.get("--rows") + ". Must be an integer.");
                return;
            }
        }
        applyRulesSnapshot(argMap);

        Board board = new Board(rows);
        BoardState state = board.toState();

        System.out.println(GSON.toJson(state));
    }

    // ──────────────────────────────────────────────
    // 工具方法
    // ──────────────────────────────────────────────

    /**
     * 如果提供了 {@code --rules} 参数，则解析 JSON 并应用到全局
     * {@link GameRulesConfig}，使后续创建的 {@link SimulationBoard} 使用正确规则。
     */
    private static void applyRulesSnapshot(Map<String, String> argMap) {
        String rulesJson = argMap.get("--rules");
        if (rulesJson == null || rulesJson.isEmpty()) {
            return;
        }
        JsonObject rulesObj = GSON.fromJson(rulesJson, JsonObject.class);
        GameRulesConfig config = new GameRulesConfig();
        config.applySnapshot(rulesObj, GameRulesConfig.ChangeSource.API);
        RulesConfigProvider.replace(config);
    }

    /**
     * 从 JSON 字符串解析 {@link BoardState}。
     *
     * <p>手动构造以兼容 {@link BoardState} 的 final 字段和参数化构造函数。</p>
     */
    private static BoardState parseBoardState(String json) {
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        int rows = obj.get("rows").getAsInt();
        int cols = obj.get("cols").getAsInt();

        // 解析 turn 字段，缺省为红方回合（兼容旧版 BoardState JSON）
        boolean redTurn = obj.has("redTurn") ? obj.get("redTurn").getAsBoolean() : true;

        JsonArray entriesArr = obj.getAsJsonArray("entries");
        List<BoardState.StackEntry> entries = new ArrayList<>();
        for (JsonElement e : entriesArr) {
            JsonObject entryObj = e.getAsJsonObject();
            int row = entryObj.get("row").getAsInt();
            int col = entryObj.get("col").getAsInt();

            JsonArray typesArr = entryObj.getAsJsonArray("pieceTypes");
            List<Piece.Type> types = new ArrayList<>();
            for (JsonElement t : typesArr) {
                types.add(Piece.Type.valueOf(t.getAsString()));
            }
            entries.add(new BoardState.StackEntry(row, col, types));
        }
        return new BoardState(rows, cols, entries, redTurn);
    }

    /**
     * 获取必需参数，若缺失则抛出异常。
     */
    private static String requireArg(Map<String, String> argMap, String key) {
        String value = argMap.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        return value;
    }

    /**
     * 校验 --rules 参数已提供，若缺失则抛出异常。
     */
    private static void requireRules(Map<String, String> argMap) {
        String rulesJson = argMap.get("--rules");
        if (rulesJson == null || rulesJson.isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: --rules");
        }
    }

    /**
     * 输出错误 JSON 到 stdout。
     */
    private static void outputError(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        System.out.println(GSON.toJson(error));
    }
}
