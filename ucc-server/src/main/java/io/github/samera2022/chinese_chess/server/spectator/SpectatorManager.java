package io.github.samera2022.chinese_chess.server.spectator;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 观战管理器：管理每个房间的 WebSocket 观战者列表。
 * 线程安全：ConcurrentHashMap + CopyOnWriteArrayList
 */
public class SpectatorManager {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Channel>> roomSpectators = new ConcurrentHashMap<>();

    /**
     * 添加观战者到指定房间。
     * 使用 computeIfAbsent 原子初始化列表。
     */
    public void addSpectator(String roomId, Channel ch) {
        roomSpectators.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(ch);
    }

    /**
     * 从指定房间移除观战者。
     * 若列表为空，同时清理 room 条目。
     */
    public void removeSpectator(String roomId, Channel ch) {
        CopyOnWriteArrayList<Channel> spectators = roomSpectators.get(roomId);
        if (spectators != null) {
            spectators.remove(ch);
            if (spectators.isEmpty()) {
                roomSpectators.remove(roomId);
            }
        }
    }

    /**
     * 向指定房间的所有观战者广播 JSON 消息。
     * 使用 retainedDuplicate() 避免 Netty 引用计数问题。
     * 自动移除已断开的连接（惰性清理）。
     */
    public void broadcast(String roomId, String json) {
        CopyOnWriteArrayList<Channel> spectators = roomSpectators.get(roomId);
        if (spectators == null || spectators.isEmpty()) return;

        TextWebSocketFrame frame = new TextWebSocketFrame(json);
        for (Channel ch : spectators) {
            if (ch.isActive()) {
                ch.writeAndFlush(frame.retainedDuplicate());
            } else {
                // 惰性清理失效连接
                spectators.remove(ch);
            }
        }
        // 如果列表为空，清理 room 条目
        if (spectators.isEmpty()) {
            roomSpectators.remove(roomId);
        }
    }

    /**
     * 获取房间观战者数量
     */
    public int getSpectatorCount(String roomId) {
        CopyOnWriteArrayList<Channel> spectators = roomSpectators.get(roomId);
        return spectators == null ? 0 : spectators.size();
    }
}
