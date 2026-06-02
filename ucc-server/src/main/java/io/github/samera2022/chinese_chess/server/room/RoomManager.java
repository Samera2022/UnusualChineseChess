package io.github.samera2022.chinese_chess.server.room;

import io.github.samera2022.chinese_chess.server.room.ServerRoom;
import io.github.samera2022.chinese_chess.server.room.ServerRoom.RoomStatus;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 房间生命周期管理器。
 * 线程安全地管理所有 ServerRoom 实例的创建、查找、关闭和定期清理。
 */
public class RoomManager {

    private static final Logger logger = LoggerFactory.getLogger(RoomManager.class);

    /** 房间ID → 房间实例映射 */
    private final ConcurrentHashMap<String, ServerRoom> rooms = new ConcurrentHashMap<>();

    /** 定期清理过期房间的后台线程池 */
    private final ScheduledExecutorService cleanupExecutor;

    /** 房间超时分钟数（默认30分钟） */
    private final long roomTimeoutMinutes;

    /**
     * 构造 RoomManager 并启动定期清理任务。
     *
     * @param roomTimeoutMinutes 房间超时分钟数，超过此时间未活动的房间将被自动清理
     */
    public RoomManager(long roomTimeoutMinutes) {
        this.roomTimeoutMinutes = roomTimeoutMinutes;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RoomManager-Cleanup");
            t.setDaemon(true);
            return t;
        });
        // 每 60 秒检查一次过期房间
        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredRooms, 60, 60, TimeUnit.SECONDS);
        logger.info("RoomManager 已启动，超时时间={} 分钟，清理间隔=60 秒", roomTimeoutMinutes);
    }

    /**
     * 创建新房间。
     *
     * @param redPlayerId   红方玩家 ID
     * @param blackPlayerId 黑方玩家 ID
     * @param rules         游戏规则配置
     * @return 新创建的 ServerRoom 实例
     */
    public ServerRoom createRoom(String redPlayerId, String blackPlayerId, GameRulesConfig rules) {
        String roomId = UUID.randomUUID().toString().substring(0, 8);
        ServerRoom room = new ServerRoom(roomId, redPlayerId, blackPlayerId, rules);
        rooms.put(roomId, room);
        // 注意：ServerRoom 当前未公开 setStatus 方法，状态由构造器初始化为 WAITING。
        // 若后续 ServerRoom 添加 setStatus 或 start() 方法，可在此处将状态设为 PLAYING。
        logger.info("房间已创建: roomId={}, redPlayer={}, blackPlayer={}", roomId, redPlayerId, blackPlayerId);
        return room;
    }

    /**
     * 根据房间 ID 查找房间。
     *
     * @param roomId 房间唯一标识
     * @return 对应的 ServerRoom 实例，若不存在则返回 null
     */
    public ServerRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 关闭并移除指定房间。
     *
     * @param roomId 房间唯一标识
     * @return 是否成功移除（房间存在且已关闭）
     */
    public boolean closeRoom(String roomId) {
        ServerRoom room = rooms.remove(roomId);
        if (room != null) {
            room.close();
            logger.info("房间已关闭: roomId={}", roomId);
            return true;
        }
        logger.warn("尝试关闭不存在的房间: roomId={}", roomId);
        return false;
    }

    /**
     * 获取所有房间的快照。
     *
     * @return 当前所有 ServerRoom 实例的集合
     */
    public Collection<ServerRoom> getAllRooms() {
        return rooms.values();
    }

    /**
     * 获取当前活跃房间数。
     *
     * @return 房间数量
     */
    public int getActiveRoomCount() {
        return rooms.size();
    }

    /**
     * 关闭管理器：关闭所有房间、清空映射、关闭后台清理线程池。
     */
    public void shutdown() {
        logger.info("RoomManager 正在关闭...");
        // 关闭所有房间
        for (ServerRoom room : rooms.values()) {
            try {
                room.close();
            } catch (Exception e) {
                logger.error("关闭房间时出错: roomId={}", room.getRoomId(), e);
            }
        }
        rooms.clear();
        // 关闭清理线程池
        cleanupExecutor.shutdownNow();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("清理线程池未能在 5 秒内终止");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("等待清理线程池终止时被中断");
        }
        logger.info("RoomManager 已关闭");
    }

    /**
     * 清理过期房间：遍历所有房间，关闭超过超时时间未活动的房间。
     */
    private void cleanupExpiredRooms() {
        long now = System.currentTimeMillis();
        long timeoutMillis = roomTimeoutMinutes * 60_000L;

        for (var entry : rooms.entrySet()) {
            String roomId = entry.getKey();
            ServerRoom room = entry.getValue();
            if (room.getCreatedAt() + timeoutMillis < now) {
                logger.info("清理过期房间: roomId={}, 已存在 {} ms", roomId, now - room.getCreatedAt());
                closeRoom(roomId);
            }
        }
    }
}
