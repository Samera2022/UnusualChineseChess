package io.github.samera2022.chinese_chess.server.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.common.spi.MatchmakingService;
import io.github.samera2022.chinese_chess.server.room.RoomManager;
import io.github.samera2022.chinese_chess.server.room.ServerRoom;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 帧处理器。
 * 解析 JSON 消息（简化版），路由到 RoomManager / MatchmakingService。
 */
public class WsSessionHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Logger logger = LoggerFactory.getLogger(WsSessionHandler.class);
    private static final Gson gson = new Gson();

    private final RoomManager roomManager;
    private final MatchmakingService matchmakingService;
    private final Map<Channel, String> channelPlayerMap = new ConcurrentHashMap<>();
    private final Map<String, Channel> playerChannelMap = new ConcurrentHashMap<>();

    /**
     * 构造 WebSocket 会话处理器。
     *
     * @param roomManager        房间管理器（注入）
     * @param matchmakingService 匹配服务（注入）
     */
    public WsSessionHandler(RoomManager roomManager, MatchmakingService matchmakingService) {
        this.roomManager = roomManager;
        this.matchmakingService = matchmakingService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String text = frame.text();
        logger.debug("Received: {}", text);

        try {
            JsonObject msg = gson.fromJson(text, JsonObject.class);
            if (msg == null || !msg.has("type")) return;

            String type = msg.get("type").getAsString();
            String playerId = msg.has("playerId") ? msg.get("playerId").getAsString()
                    : channelPlayerMap.computeIfAbsent(ctx.channel(),
                            k -> java.util.UUID.randomUUID().toString());

            // 注册/更新 playerId ↔ Channel 双向映射
            Channel oldChannel = playerChannelMap.put(playerId, ctx.channel());
            if (oldChannel != null && oldChannel != ctx.channel()) {
                logger.debug("Player {} channel updated: {} -> {}", playerId, oldChannel, ctx.channel());
            }
            channelPlayerMap.put(ctx.channel(), playerId);

            String roomCode = msg.has("roomCode") ? msg.get("roomCode").getAsString() : null;

            switch (type) {
                case "CREATE_ROOM": {
                    String code = matchmakingService.createRoom(playerId);
                    sendMessage(ctx.channel(), "ROOM_CREATED", "{\"roomCode\":\"" + code + "\"}");
                    break;
                }
                case "JOIN_ROOM": {
                    if (roomCode == null) {
                        sendError(ctx.channel(), "Missing roomCode");
                        break;
                    }
                    String resultCode = matchmakingService.joinRoom(playerId, roomCode);
                    sendMessage(ctx.channel(), "ROOM_JOINED", "{\"roomCode\":\"" + resultCode + "\"}");
                    break;
                }
                case "START_MATCHMAKING": {
                    matchmakingService.startMatchmaking(playerId, matchSession -> {
                        sendMessage(ctx.channel(), "MATCH_FOUND",
                                "{\"matchId\":\"" + matchSession.getMatchId() + "\"}");
                    });
                    break;
                }
                case "CANCEL_MATCHMAKING": {
                    matchmakingService.cancelMatchmaking(playerId);
                    break;
                }
                case "SUBMIT_MOVE": {
                    if (roomCode == null) {
                        sendError(ctx.channel(), "Missing roomCode");
                        break;
                    }
                    int fr = msg.get("fromRow").getAsInt();
                    int fc = msg.get("fromCol").getAsInt();
                    int tr = msg.get("toRow").getAsInt();
                    int tc = msg.get("toCol").getAsInt();
                    int si = msg.has("stackIndex") ? msg.get("stackIndex").getAsInt() : -1;

                    ServerRoom room = roomManager.getRoom(roomCode);
                    if (room == null) {
                        sendError(ctx.channel(), "Room not found");
                        break;
                    }
                    String error = room.submitMove(playerId, fr, fc, tr, tc, si);
                    if (error != null) {
                        sendError(ctx.channel(), error);
                    } else {
                        sendMessage(ctx.channel(), "MOVE_ACCEPTED", "{}");

                        // 通知对手：查找对手的 playerId 并发送 OPPONENT_MOVE
                        String opponentId = playerId.equals(room.getRedPlayerId())
                                ? room.getBlackPlayerId()
                                : room.getRedPlayerId();
                        Channel opponentChannel = playerChannelMap.get(opponentId);
                        if (opponentChannel != null && opponentChannel.isActive()) {
                            String movePayload = String.format(
                                    "{\"fromRow\":%d,\"fromCol\":%d,\"toRow\":%d,\"toCol\":%d,\"stackIndex\":%d}",
                                    fr, fc, tr, tc, si);
                            sendMessage(opponentChannel, "OPPONENT_MOVE", movePayload);
                        } else {
                            logger.debug("Opponent {} not connected for room {}", opponentId, roomCode);
                        }
                    }
                    break;
                }
                default:
                    logger.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error handling message", e);
            sendError(ctx.channel(), "Internal error: " + e.getMessage());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        logger.info("WebSocket 连接已建立: {}", ctx.channel().remoteAddress());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        String playerId = channelPlayerMap.remove(ctx.channel());
        if (playerId != null) {
            playerChannelMap.remove(playerId, ctx.channel());
        }
        logger.info("WebSocket 连接已断开: {}", ctx.channel().remoteAddress());
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("WebSocket 异常: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    /**
     * 向指定 channel 发送 JSON 消息帧。
     *
     * @param channel 目标 channel
     * @param type    消息类型
     * @param payload 消息负载（JSON 字符串）
     */
    private void sendMessage(Channel channel, String type, String payload) {
        String json = String.format("{\"type\":\"%s\",\"payload\":%s}", type, payload);
        channel.writeAndFlush(new TextWebSocketFrame(json));
    }

    /**
     * 向指定 channel 发送错误消息帧。
     *
     * @param channel 目标 channel
     * @param errorMsg 错误描述
     */
    private void sendError(Channel channel, String errorMsg) {
        String json = String.format("{\"type\":\"ERROR\",\"payload\":{\"message\":\"%s\"}}", errorMsg);
        channel.writeAndFlush(new TextWebSocketFrame(json));
    }
}
