package io.github.samera2022.chinese_chess.server.room;

import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.core.engine.GameEngine;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.common.GameStatus;
import io.github.samera2022.chinese_chess.common.model.BoardState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单个对局房间，包装 GameEngine 实例。
 * 每个房间完全独立，由各自的虚拟线程管理。
 */
public class ServerRoom {

    /** 房间状态枚举 */
    public enum RoomStatus {
        WAITING,
        PLAYING,
        FINISHED,
        CLOSED
    }

    private final String roomId;
    private final GameEngine engine;
    private final String redPlayerId;
    private final String blackPlayerId;
    private final List<String> spectators = new CopyOnWriteArrayList<>();
    private final AtomicReference<RoomStatus> status = new AtomicReference<>(RoomStatus.WAITING);
    private final long createdAt = System.currentTimeMillis();

    /**
     * 构造一个对局房间。
     *
     * @param roomId      房间唯一标识
     * @param redPlayer   红方玩家 ID
     * @param blackPlayer 黑方玩家 ID
     * @param rules       游戏规则配置
     */
    public ServerRoom(String roomId, String redPlayer, String blackPlayer, GameRulesConfig rules) {
        this.roomId = roomId;
        this.redPlayerId = redPlayer;
        this.blackPlayerId = blackPlayer;
        this.engine = new GameEngine(rules);
    }

    /**
     * 接收并验证走子。
     *
     * @param playerId 发起走子的玩家 ID
     * @param fr       起始行
     * @param fc       起始列
     * @param tr       目标行
     * @param tc       目标列
     * @param si       选中堆叠索引
     * @return null 表示成功，否则返回错误信息
     */
    public String submitMove(String playerId, int fr, int fc, int tr, int tc, int si) {
        // 验证回合
        boolean isRedPlayer = playerId.equals(redPlayerId);
        if (isRedPlayer != engine.isRedTurn()) {
            return "不是你的回合";
        }
        // 执行走子
        boolean ok = engine.makeMove(fr, fc, tr, tc, null, si);
        if (!ok) {
            return "非法走子";
        }
        // 检查终局
        GameStatus gs = engine.getGameStatus();
        if (gs == GameStatus.RED_CHECKMATE || gs == GameStatus.BLACK_CHECKMATE) {
            status.set(RoomStatus.FINISHED);
        }
        return null;  // 成功
    }

    /** @return 当前棋盘状态的快照 */
    public BoardState getBoardState() {
        return engine.getBoardState();
    }

    /** @return 当前房间状态 */
    public RoomStatus getStatus() {
        return status.get();
    }

    /** @return 红方玩家 ID */
    public String getRedPlayerId() {
        return redPlayerId;
    }

    /** @return 黑方玩家 ID */
    public String getBlackPlayerId() {
        return blackPlayerId;
    }

    /** @return 房间唯一标识 */
    public String getRoomId() {
        return roomId;
    }

    /** @return 房间创建时间戳 */
    public long getCreatedAt() {
        return createdAt;
    }

    /** 添加观战者 */
    public void addSpectator(String playerId) {
        spectators.add(playerId);
    }

    /** 移除观战者 */
    public void removeSpectator(String playerId) {
        spectators.remove(playerId);
    }

    /** @return 观战者列表的不可变副本 */
    public List<String> getSpectators() {
        return List.copyOf(spectators);
    }

    /**
     * 返回内部引擎实例（包级访问，供 RoomManager 使用）。
     */
    GameEngine getEngine() {
        return engine;
    }

    /**
     * 强制结束对局。
     */
    public void finish() {
        status.set(RoomStatus.FINISHED);
    }

    /**
     * 获取对局同步状态 JSON（用于断线重连）。
     */
    public String getSyncStateJson() {
        return engine.getSyncState().toString();
    }

    /** 关闭房间，设置状态为 CLOSED 并关闭引擎 */
    public void close() {
        status.set(RoomStatus.CLOSED);
        engine.shutdown();
    }

    /** @return 当前是否为红方回合 */
    public boolean isRedTurn() {
        return engine.isRedTurn();
    }
}
