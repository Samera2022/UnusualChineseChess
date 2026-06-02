package io.github.samera2022.chinese_chess.server.match;

import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.spi.MatchmakingService;
import io.github.samera2022.chinese_chess.common.spi.MatchSession;
import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.core.rules.RulesConfigProvider;
import io.github.samera2022.chinese_chess.server.room.RoomManager;
import io.github.samera2022.chinese_chess.server.room.ServerRoom;
import io.github.samera2022.chinese_chess.server.room.ServerRoom.RoomStatus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * MatchmakingService 的服务器端实现。
 * 管理房间创建、加入、匹配队列及对局生命周期。
 */
public class MatchmakingServiceImpl implements MatchmakingService {

    private final RoomManager roomManager;
    private final ConcurrentLinkedQueue<MatchQueueEntry> matchQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, Consumer<MatchSession>> pendingConsumers = new ConcurrentHashMap<>();

    /**
     * 追踪 roomCode → blackPlayerId 的映射。
     * 由于 ServerRoom 的 blackPlayerId 是 final 且无公开 setter，
     * joinRoom 时在此处记录第二个玩家信息。
     */
    private final Map<String, String> roomBlackPlayerMap = new ConcurrentHashMap<>();

    /** 匹配队列条目 */
    private record MatchQueueEntry(String playerId, Consumer<MatchSession> onMatched) {}

    /**
     * 构造匹配服务实现。
     *
     * @param roomManager 房间管理器（构造器注入）
     */
    public MatchmakingServiceImpl(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    public String createRoom(String playerId) {
        GameRulesConfig rules = RulesConfigProvider.get();
        // 第二个玩家暂为 null，等待他人加入
        ServerRoom room = roomManager.createRoom(playerId, null, rules);
        return room.getRoomId();
    }

    @Override
    public String joinRoom(String playerId, String roomCode) {
        ServerRoom room = roomManager.getRoom(roomCode);
        if (room == null) {
            return null;
        }
        // 检查房间是否已满（blackPlayerId 已设置，或已在映射中）
        if (room.getBlackPlayerId() != null || roomBlackPlayerMap.containsKey(roomCode)) {
            return null;
        }
        // 不能加入自己创建的房间
        if (playerId.equals(room.getRedPlayerId())) {
            return null;
        }
        roomBlackPlayerMap.put(roomCode, playerId);
        return roomCode;
    }

    @Override
    public void startMatchmaking(String playerId, Consumer<MatchSession> onMatched) {
        // 检查队列中是否有等待者
        MatchQueueEntry opponent = matchQueue.poll();
        if (opponent != null) {
            // 配对成功：创建房间
            GameRulesConfig rules = RulesConfigProvider.get();
            ServerRoom room = roomManager.createRoom(opponent.playerId(), playerId, rules);

            UUID matchId = UUID.randomUUID();
            MatchSessionImpl sessionForOpponent = new MatchSessionImpl(matchId, room);
            MatchSessionImpl sessionForRequester = new MatchSessionImpl(matchId, room);

            // 通知双方
            Consumer<MatchSession> opponentCallback = pendingConsumers.remove(opponent.playerId());
            if (opponentCallback != null) {
                opponentCallback.accept(sessionForOpponent);
            }
            onMatched.accept(sessionForRequester);
        } else {
            // 队列中无人等待，将自己加入队列
            matchQueue.offer(new MatchQueueEntry(playerId, onMatched));
            pendingConsumers.put(playerId, onMatched);
        }
    }

    @Override
    public void cancelMatchmaking(String playerId) {
        matchQueue.removeIf(entry -> entry.playerId().equals(playerId));
        pendingConsumers.remove(playerId);
    }

    @Override
    public void leaveMatch(String playerId) {
        for (ServerRoom room : roomManager.getAllRooms()) {
            String blackId = room.getBlackPlayerId();
            // 检查直接存储在 ServerRoom 中的 blackPlayerId，
            // 以及通过 joinRoom 在 roomBlackPlayerMap 中记录的
            String mappedBlackId = roomBlackPlayerMap.get(room.getRoomId());
            if (playerId.equals(room.getRedPlayerId())
                    || playerId.equals(blackId)
                    || playerId.equals(mappedBlackId)) {
                roomManager.closeRoom(room.getRoomId());
                roomBlackPlayerMap.remove(room.getRoomId());
                break;
            }
        }
    }

    /**
     * MatchSession 的内部实现，代理到 ServerRoom。
     */
    private class MatchSessionImpl implements MatchSession {

        private final UUID matchId;
        private final ServerRoom room;

        MatchSessionImpl(UUID matchId, ServerRoom room) {
            this.matchId = matchId;
            this.room = room;
        }

        @Override
        public UUID getMatchId() {
            return matchId;
        }

        @Override
        public String getRedPlayerId() {
            return room.getRedPlayerId();
        }

        @Override
        public String getBlackPlayerId() {
            String blackId = room.getBlackPlayerId();
            if (blackId != null) {
                return blackId;
            }
            // 若 blackPlayerId 通过 joinRoom 映射存储
            return roomBlackPlayerMap.getOrDefault(room.getRoomId(), null);
        }

        @Override
        public boolean isRedTurn() {
            return room.isRedTurn();
        }

        @Override
        public String submitMove(String playerId, int fromRow, int fromCol, int toRow, int toCol,
                                  Piece.Type promotionType, int selectedStackIndex) {
            // ServerRoom.submitMove 内部调用 engine.makeMove 时 promotionType 参数传 null,
            // 引擎内部通过 needsPromotion 自行处理晋升逻辑
            return room.submitMove(playerId, fromRow, fromCol, toRow, toCol, selectedStackIndex);
        }

        @Override
        public boolean requestUndo(String playerId) {
            return false; // 暂不支持
        }

        @Override
        public void resign(String playerId) {
            room.finish();
        }

        @Override
        public String getSyncSnapshotJson() {
            return room.getSyncStateJson();
        }

        @Override
        public boolean isFinished() {
            RoomStatus status = room.getStatus();
            return status == RoomStatus.FINISHED || status == RoomStatus.CLOSED;
        }
    }
}
