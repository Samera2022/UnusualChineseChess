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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java 长驻进程 —— 供 Python 训练脚本通过 stdin/stdout 调用。
 *
 * <h3>协议</h3>
 * <p>Python 端向 stdin 发送一行 JSON，Java 处理后将结果以一行 JSON 输出到 stdout，立即 flush。</p>
 *
 * <h3>支持的命令（JSON 中的 "command" 字段）</h3>
 * <ul>
 *   <li>{@code new_game}   – 创建新棋盘，规则由 "rules" 指定</li>
 *   <li>{@code legal_moves} – 生成当前回合方所有合法走法</li>
 *   <li>{@code simulate}   – 执行走子并返回更新后的 BoardState</li>
 * </ul>
 */
public class PyBridge {

    private static final Gson GSON = new GsonBuilder().create();

    // 内部状态：持有当前棋盘和规则
    private SimulationBoard simBoard;
    private JsonObject currentRules;

    public static void main(String[] args) {
        PyBridge bridge = new PyBridge();
        bridge.run();
    }

    // ── 主循环 ──

    private void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                JsonObject result;
                try {
                    JsonObject request = GSON.fromJson(line, JsonObject.class);
                    String command = request.has("command") ? request.get("command").getAsString() : "";
                    result = dispatch(command, request);
                } catch (Exception e) {
                    result = new JsonObject();
                    result.addProperty("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
                System.out.println(GSON.toJson(result));
                System.out.flush();
            }
        } catch (IOException e) {
            // Python 端关闭管道，正常退出
        }
    }

    // ── 命令分发 ──

    private JsonObject dispatch(String command, JsonObject request) {
        switch (command) {
            case "ping":
                return ok();
            case "new_game":
                return handleNewGame(request);
            case "legal_moves":
                return handleLegalMoves();
            case "simulate":
                return handleSimulate(request);
            default:
                JsonObject err = new JsonObject();
                err.addProperty("error", "Unknown command: " + command);
                return err;
        }
    }

    private static JsonObject ok() {
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        return r;
    }

    // ── 命令处理器（复用内部 simBoard） ──

    private JsonObject handleNewGame(JsonObject request) {
        applyRules(request);

        int rows = request.has("rows") ? request.get("rows").getAsInt() : 10;
        Board board = new Board(rows);
        this.simBoard = new SimulationBoard(board);

        JsonObject result = GSON.toJsonTree(simBoard.toState()).getAsJsonObject();
        result.addProperty("ok", true);
        return result;
    }

    private JsonObject handleLegalMoves() {
        if (simBoard == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "No active game. Send new_game first.");
            return err;
        }

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
        result.addProperty("ok", true);
        return result;
    }

    private JsonObject handleSimulate(JsonObject request) {
        if (simBoard == null) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "No active game. Send new_game first.");
            return err;
        }

        int fromRow = request.get("fromRow").getAsInt();
        int fromCol = request.get("fromCol").getAsInt();
        int toRow   = request.get("toRow").getAsInt();
        int toCol   = request.get("toCol").getAsInt();

        boolean legal = simBoard.isValidMove(fromRow, fromCol, toRow, toCol);
        if (legal) {
            simBoard.simulateMove(fromRow, fromCol, toRow, toCol);
        }

        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("legal", legal);
        result.add("boardState", GSON.toJsonTree(simBoard.toState()));
        return result;
    }

    // ── 工具方法 ──

    private void applyRules(JsonObject request) {
        if (request.has("rules")) {
            JsonObject rulesObj = request.getAsJsonObject("rules");
            this.currentRules = rulesObj;
            GameRulesConfig config = new GameRulesConfig();
            config.applySnapshot(rulesObj, GameRulesConfig.ChangeSource.API);
            RulesConfigProvider.replace(config);
        }
    }
}
