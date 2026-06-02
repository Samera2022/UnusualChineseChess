# ucc-server 解耦与高并发架构方案

> 版本: 1.0  
> 日期: 2026-06-01  
> 前置阅读: [P0线程安全改造方案.md](P0线程安全改造方案.md) | [ucc-core并发评估与解耦分析.md](../ucc-core并发评估与解耦分析.md)

---

## 一、架构总览

### 1.1 目标架构

```
┌──────────────────────────────────────────────────────────────┐
│                      ucc-app (Swing 客户端)                   │
│                      WebSocket / gRPC                         │
└──────────────────────────┬───────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│                     ucc-server (高并发网络服务端)               │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ │
│  │ Netty/WS     │ │ RoomManager  │ │ TrainingOrchestrator │ │
│  │ 接入层        │ │ 房间/匹配     │ │ 分布式训练编排         │ │
│  └──────────────┘ └──────────────┘ └──────────┬───────────┘ │
│                                               │              │
│  ┌────────────────────────────────────────────▼───────────┐ │
│  │              BatchingEngine (生产者-消费者)              │ │
│  │  ┌──────────────────┐    ┌──────────────────────────┐  │ │
│  │  │ 100-120 Workers  │───▶│ Disruptor RingBuffer     │──┤ │
│  │  │ (自博弈 + MCTS)   │    │ (batch_size=64, 5ms窗口) │  │ │
│  │  └──────────────────┘    └───────────┬──────────────┘  │ │
│  │                                      │                  │ │
│  │                       ┌──────────────▼──────────────┐  │ │
│  │                       │ BatchAssembler → gRPC →    │  │ │
│  │                       │ ucc-ai Python 推理服务      │  │ │
│  │                       └─────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  依赖:                                                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │ ucc-core │  │ucc-common│  │ ucc-api  │                  │
│  │ (规则引擎) │  │ (数据模型) │  │ (序列化)  │                  │
│  └──────────┘  └──────────┘  └──────────┘                  │
└──────────────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────┐
│              ucc-ai (改造后的训练/推理模块)                     │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐ │
│  │InferenceServer │  │ TrainingWorker │  │ ReplayBuffer   │ │
│  │ (gRPC, GPU批处理)│  │ (异步训练)     │  │ (Redis/共享存储) │ │
│  └────────────────┘  └────────────────┘  └────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 模块依赖关系

```
ucc-common ◀── ucc-core ◀── ucc-server
    ▲              ▲              │
    │              │         ucc-app (客户端)
    │              │              │
    └── ucc-api ◀──┘              │
                                  │
                            ucc-ai (独立部署，通过 gRPC 通信)
```

关键设计决策：

1. **ucc-server 依赖 ucc-core**：复用棋盘引擎、规则引擎、MCTS
2. **ucc-server 不依赖 ucc-app**：Swing GUI 与服务端完全解耦
3. **✅ ucc-ai 采用「模式 A：独立进程 gRPC」部署**（详见 [ucc-ai部署模式对比.md](ucc-ai部署模式对比.md)）

### 🔒 最终决策：模式 A — ucc-ai 作为独立进程

经全面对比分析（独立进程 gRPC vs 内嵌 DJL/JNI vs 混合架构），**确定采用模式 A**。

```
┌───────────────────────┐          gRPC/Protobuf          ┌──────────────────────┐
│  ucc-server (Java 21) │ ◀────────────────────────────▶  │  ucc-ai Python        │
│  JVM 独立进程           │     localhost:50051            │  独立进程 (GPU)        │
│  - 房间/匹配/训练编排    │                                │  - InferenceServer    │
│  - BatchingEngine     │                                │  - TrainingWorker    │
└───────────────────────┘                                └──────────────────────┘
```

**选择理由**（加权总分 9.1/10 vs 模式 B 的 4.1/10）：

| 维度 | 模式 A 优势 |
|------|------------|
| 故障隔离 | GPU/PT 崩溃 → 不影响正在进行的网络对局 |
| 代码复用 | 现有 ~2000 行 Python AI 代码（model.py/selfplay.py/train.py）几乎零改动 |
| 热更新 | 训练产出新权重 → 重启 Python (<5s)，Java 端 gRPC 自动重连 |
| 扩缩容 | GPU 升级独立于 Java 服务端，可加 GPU 机器水平扩展 |
| PyTorch 生态 | TensorRT、AMP、FSDP 等全部可用 |

**不再考虑的其他方案**：
- ❌ 模式 B（DJL 内嵌）：需重写 ~1500 行 Python 为 Java，训练仍需 Python → 两份推理代码并存
- ❌ 模式 C（混合 DJL+gRPC）：架构复杂度高，Phase 1-3 暂不需要，可留待后续优化

---

## 二、通信协议设计（第一阶段）

### 2.1 Protobuf 消息定义

在 `ucc-common/src/main/proto/` 下定义所有通信协议：

```protobuf
// ucc_chess.proto
syntax = "proto3";
package ucc;

// ═══════════════════════════════════════════
// 棋盘局面
// ═══════════════════════════════════════════
message BoardStateProto {
    int32 rows = 1;
    int32 cols = 2;
    bool red_turn = 3;
    repeated StackEntry entries = 4;

    message StackEntry {
        int32 row = 1;
        int32 col = 2;
        repeated PieceType piece_types = 3;
    }
}

// ═══════════════════════════════════════════
// 棋子类型枚举
// ═══════════════════════════════════════════
enum PieceType {
    RED_KING = 0;
    RED_ADVISOR = 1;
    RED_ELEPHANT = 2;
    RED_HORSE = 3;
    RED_CHARIOT = 4;
    RED_CANNON = 5;
    RED_SOLDIER = 6;
    BLACK_KING = 7;
    BLACK_ADVISOR = 8;
    BLACK_ELEPHANT = 9;
    BLACK_HORSE = 10;
    BLACK_CHARIOT = 11;
    BLACK_CANNON = 12;
    BLACK_SOLDIER = 13;
}

// ═══════════════════════════════════════════
// 规则配置
// ═══════════════════════════════════════════
message RulesConfigProto {
    // 27 个布尔位 + 1 个连续值
    repeated float rule_vector = 1;  // 长度 28
}

// ═══════════════════════════════════════════
// 着法
// ═══════════════════════════════════════════
message MoveProto {
    int32 from_row = 1;
    int32 from_col = 2;
    int32 to_row = 3;
    int32 to_col = 4;
    int32 selected_stack_index = 5;  // -1 表示不从堆栈选择
}

// ═══════════════════════════════════════════
// 推理请求（Java → Python）
// ═══════════════════════════════════════════
message InferenceRequest {
    repeated BoardStateProto boards = 1;     // 批量局面（最多 128）
    repeated RulesConfigProto rules = 2;     // 每个局面对应的规则
}

// ═══════════════════════════════════════════
// 推理响应（Python → Java）
// ═══════════════════════════════════════════
message InferenceResponse {
    repeated PolicyValuePair results = 1;

    message PolicyValuePair {
        repeated float policy = 1;   // 长度 = rows × cols
        float value = 2;             // ∈ [-1, 1]
    }
}

// ═══════════════════════════════════════════
// 服务定义（gRPC）
// ═══════════════════════════════════════════
service InferenceService {
    // 批量推理：Java 发送一批局面，Python 返回策略和价值
    rpc BatchInfer(InferenceRequest) returns (InferenceResponse);

    // 健康检查
    rpc Ping(google.protobuf.Empty) returns (google.protobuf.Empty);
}

service TrainingService {
    // 推送训练样本
    rpc PushSamples(stream TrainingSample) returns (google.protobuf.Empty);

    // 拉取最新模型权重
    rpc PullWeights(google.protobuf.Empty) returns (ModelWeights);
}

message TrainingSample {
    BoardStateProto board = 1;
    RulesConfigProto rules = 2;
    repeated float policy = 3;
    float value = 4;
}

message ModelWeights {
    bytes weights_data = 1;  // PyTorch state_dict 序列化
    int32 iteration = 2;
}

// ═══════════════════════════════════════════
// WebSocket 对局协议（用于客户端 ↔ 服务端）
// ═══════════════════════════════════════════
message WsMessage {
    enum Type {
        // 大厅
        CREATE_ROOM = 0;
        JOIN_ROOM = 1;
        START_MATCHMAKING = 2;
        CANCEL_MATCHMAKING = 3;
        MATCH_FOUND = 4;
        ROOM_LIST = 5;

        // 对局
        SUBMIT_MOVE = 10;
        OPPONENT_MOVE = 11;
        REQUEST_UNDO = 12;
        OPPONENT_UNDO = 13;
        RESIGN = 14;
        GAME_OVER = 15;

        // 同步
        SYNC_GAME_STATE = 20;
        REQUEST_SYNC = 21;

        // 观战
        SPECTATE = 30;
        SPECTATE_UPDATE = 31;

        // 系统
        ERROR = 99;
        PING = 100;
        PONG = 101;
    }
    Type type = 1;
    string room_code = 2;
    string player_id = 3;
    bytes payload = 4;  // 序列化的具体消息
    int64 timestamp = 5;
}
```

### 2.2 Java ↔ Python 通信方式对比

| 方式 | 序列化速度 | 连接模型 | 适用场景 | 推荐度 |
|------|-----------|----------|----------|--------|
| **gRPC + Protobuf** | ⭐⭐⭐⭐⭐ | 长连接、双向流 | 批量推理、训练样本传输 | ✅ 首选 |
| JSON over stdin/stdout | ⭐⭐ | 每请求新进程/管道 | 当前 PyBridge 模式 | ❌ 淘汰 |
| Redis pub/sub | ⭐⭐⭐ | 消息队列 | ReplayBuffer 共享 | 🟡 辅助 |
| ZeroMQ | ⭐⭐⭐⭐ | 长连接 | 低延迟推理 | 🟡 备选 |

**决策**: 主通信采用 **gRPC + Protobuf**，辅助用 **Redis** 共享 ReplayBuffer。

---

## 三、ucc-server 模块设计（第二阶段）

### 3.1 Maven 模块结构

```
ucc-server/
├── pom.xml
├── src/main/java/io/github/samera2022/chinese_chess/server/
│   ├── UCCServer.java                    # 启动入口
│   │
│   ├── net/                               # 网络层
│   │   ├── NettyWsServer.java            # Netty WebSocket 服务端
│   │   ├── WsSessionHandler.java         # WebSocket 帧处理
│   │   ├── ProtobufCodec.java            # Protobuf 编解码器
│   │   └── GrpcInferenceClient.java      # gRPC 推理客户端（调用 Python）
│   │
│   ├── room/                              # 对局管理
│   │   ├── RoomManager.java             # 房间生命周期管理
│   │   ├── ServerRoom.java              # 单个对局房间（包装 GameEngine）
│   │   └── RoomConfig.java              # 房间配置（规则、时间限制等）
│   │
│   ├── match/                             # 匹配系统
│   │   ├── MatchmakingServiceImpl.java  # 实现 MatchmakingService SPI
│   │   ├── MatchQueue.java              # 匹配队列
│   │   └── EloRatingService.java        # ELO 评分（可选）
│   │
│   ├── spectator/                         # 观战
│   │   └── SpectatorManager.java        # 观战者管理 + 广播
│   │
│   ├── train/                             # 训练基础设施
│   │   ├── BatchingEngine.java          # 生产者-消费者批处理引擎
│   │   ├── SelfPlayWorker.java          # 自博弈 Worker（虚拟线程）
│   │   ├── InferenceBatcher.java        # 批量推理组装器
│   │   ├── TranspositionTable.java      # LRU 置换表（局面缓存）
│   │   └── TrainingOrchestrator.java    # 训练流水线编排
│   │
│   └── config/
│       └── ServerConfig.java             # 服务端配置（端口、线程数等）
│
├── src/main/resources/
│   └── server.properties
│
└── src/test/java/...
```

### 3.2 核心类设计

#### 3.2.1 `ServerRoom` — 对局房间

```java
/**
 * 单个对局房间，包装 GameEngine 实例。
 * 每个房间完全独立，由各自的虚拟线程管理。
 */
public class ServerRoom {
    private final String roomId;
    private final GameEngine engine;          // 核心引擎（P0 改造后线程安全）
    private final String redPlayerId;
    private final String blackPlayerId;
    private final List<String> spectators = new CopyOnWriteArrayList<>();
    private final AtomicReference<RoomStatus> status = new AtomicReference<>(RoomStatus.WAITING);
    private final long createdAt = System.currentTimeMillis();

    public ServerRoom(String roomId, String redPlayer, String blackPlayer,
                      GameRulesConfig rules) {
        this.roomId = roomId;
        this.redPlayerId = redPlayer;
        this.blackPlayerId = blackPlayer;
        this.engine = new GameEngine(rules);
    }

    /**
     * 接收并验证走子。
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

    public BoardState getBoardState() { return engine.getBoardState(); }
    public RoomStatus getStatus() { return status.get(); }
    // ...
}
```

#### 3.2.2 `BatchingEngine` — 批量推理引擎

```java
/**
 * 生产者-消费者模型的核心：Worker 产出推理请求 → RingBuffer → BatchAssembler → gRPC → Python。
 *
 * 架构：
 *   - 100-120 个 SelfPlayWorker（虚拟线程）并发自博弈
 *   - 每个 Worker 持有一个 SimulationBoard 副本 + MCTSAgent
 *   - MCTS 叶节点评估时，将 BoardState 推入 Disruptor RingBuffer
 *   - 独立 BatchAssembler 线程从 RingBuffer 批量捞出，
 *     组装为 InferenceRequest (batch=64, 5ms 超时) 发往 Python
 */
public class BatchingEngine {
    // LMAX Disruptor RingBuffer 大小：2^14 = 16384
    private static final int RING_BUFFER_SIZE = 16384;
    private static final int BATCH_SIZE = 64;
    private static final long BATCH_TIMEOUT_MS = 5;

    private final RingBuffer<InferenceEvent> ringBuffer;
    private final GrpcInferenceClient grpcClient;
    private final ExecutorService workerPool;
    private final TranspositionTable tt;

    public BatchingEngine(ServerConfig config) {
        this.grpcClient = new GrpcInferenceClient(config.getPythonInferenceHost(),
                                                   config.getPythonInferencePort());
        this.tt = new TranspositionTable(config.getTtMaxEntries());
        this.ringBuffer = createRingBuffer();

        // 启动 BatchAssembler（消费者线程）
        Thread assembler = new Thread(this::batchLoop, "BatchAssembler");
        assembler.setDaemon(true);
        assembler.start();

        // 创建虚拟线程池（Java 21）
        this.workerPool = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 启动指定数量的自博弈 Worker。
     */
    public void startSelfPlay(int numWorkers, GameRulesConfig rules) {
        for (int i = 0; i < numWorkers; i++) {
            workerPool.submit(new SelfPlayWorker(i, rules, this));
        }
    }

    // ── RingBuffer 消费者 ──
    private void batchLoop() {
        List<InferenceEvent> batch = new ArrayList<>(BATCH_SIZE);
        long lastFlush = System.currentTimeMillis();

        while (!Thread.currentThread().isInterrupted()) {
            // 从 RingBuffer 拉取事件
            long sequence = ringBuffer.next();
            InferenceEvent event = ringBuffer.get(sequence);
            batch.add(event);
            ringBuffer.publish(sequence);

            // 凑够 BATCH_SIZE 或超时 → 发送
            if (batch.size() >= BATCH_SIZE ||
                System.currentTimeMillis() - lastFlush >= BATCH_TIMEOUT_MS) {
                flushBatch(batch);
                batch.clear();
                lastFlush = System.currentTimeMillis();
            }
        }
    }

    private void flushBatch(List<InferenceEvent> batch) {
        if (batch.isEmpty()) return;
        // 组装 Protobuf 请求
        InferenceRequest request = buildRequest(batch);
        // 调用 Python gRPC
        InferenceResponse response = grpcClient.batchInfer(request);
        // 将结果写回各事件的 CompletableFuture
        for (int i = 0; i < batch.size(); i++) {
            batch.get(i).future.complete(response.getResults(i));
        }
    }

    /**
     * 提交单个推理请求，返回 CompletableFuture。
     * MCTS Worker 调用此方法获取神经网络评估。
     */
    public CompletableFuture<InferenceResponse.PolicyValuePair>
           submitInference(BoardState state, float[] ruleVector) {
        CompletableFuture<InferenceResponse.PolicyValuePair> future = new CompletableFuture<>();
        long seq = ringBuffer.next();
        InferenceEvent event = ringBuffer.get(seq);
        event.board = state;
        event.ruleVector = ruleVector;
        event.future = future;
        ringBuffer.publish(seq);
        return future;
    }
}
```

#### 3.2.3 `SelfPlayWorker` — 自博弈工作线程

```java
/**
 * 单个自博弈 Worker。
 * 每个 Worker 运行在独立的虚拟线程上，持有独立的 SimulationBoard + MCTSAgent。
 */
public class SelfPlayWorker implements Runnable {
    private final int workerId;
    private final GameRulesConfig rules;
    private final BatchingEngine batchingEngine;
    private final TrainingDataCollector collector;
    private final MCTSAgent mctsAgent;

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            // 初始化新棋盘
            Board board = new Board(getRows(rules));
            SimulationContext ctx = new SimulationBoard(board);

            while (ctx.getBoard().getRows() > 0) {  // 对局未结束
                // MCTS 搜索（内部调用 batchingEngine.submitInference）
                Move bestMove = mctsAgent.findBestMove(ctx, 800, 5000);
                if (bestMove == null) break;

                // 记录训练样本
                collector.addSample(
                    ctx.getBoard().toState(),
                    RuleEncoder.encode(rules),
                    mctsAgent.getLastPolicy(),  // 从 MCTS 获取策略分布
                    ctx.isRedTurn() ? 1.0f : -1.0f  // 临时 value，后续填充
                );

                // 执行走子
                ctx.simulateMove(bestMove.getFromRow(), bestMove.getFromCol(),
                                 bestMove.getToRow(), bestMove.getToCol());

                // 检查终局
                if (isGameOver(ctx)) break;
            }

            // 推送训练样本到 Python 端
            pushTrainingData();
            collector.clear();
        }
    }
}
```

#### 3.2.4 `TranspositionTable` — 局面缓存

```java
/**
 * 基于 LRU 的全局置换表。
 * 利用 128GB 大内存，缓存已评估的局面。
 *
 * Key: BoardState 的 hash（zzHash 或 SHA-256 前 8 字节）
 * Value: (policy, value)
 */
public class TranspositionTable {
    private static final int DEFAULT_MAX_ENTRIES = 10_000_000;

    private final LinkedHashMap<Long, TtEntry> cache;

    public TranspositionTable(int maxEntries) {
        this.cache = new LinkedHashMap<Long, TtEntry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, TtEntry> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public synchronized Optional<TtEntry> get(long hash) {
        return Optional.ofNullable(cache.get(hash));
    }

    public synchronized void put(long hash, float[] policy, float value) {
        cache.put(hash, new TtEntry(policy, value));
    }

    public record TtEntry(float[] policy, float value) {}
}
```

---

## 四、ucc-ai 改造（第三阶段）

### 4.1 新增 Python 推理服务 (gRPC)

```python
# ucc-ai/python/inference_server.py
"""
高性能批量推理服务（gRPC + GPU 批处理）。
接收 Java BatchingEngine 发来的批量 BoardState，批量前向传播。
"""
import grpc
import torch
import numpy as np
from concurrent import futures

import ucc_chess_pb2
import ucc_chess_pb2_grpc
from model import MiniResNet
from selfplay import board_to_tensor, rule_vector_to_numpy


class InferenceServicer(ucc_chess_pb2_grpc.InferenceServiceServicer):
    def __init__(self, model_path: str, device: str = "cuda"):
        self.device = torch.device(device)
        # 加载模型
        checkpoint = torch.load(model_path, map_location=self.device)
        self.model = MiniResNet(
            board_channels=14, board_h=18, board_w=9,
            rule_dim=28, num_res_blocks=5, filters=128,
        ).to(self.device)
        self.model.load_state_dict(checkpoint["model_state_dict"])
        self.model.eval()

        # 启用 AMP（自动混合精度）加速
        self.use_amp = self.device.type == "cuda"

    def BatchInfer(self, request, context):
        """批量推理：接收 Java 端发送的批量 BoardState。"""
        batch_size = len(request.boards)
        if batch_size == 0:
            return ucc_chess_pb2.InferenceResponse()

        # ── 解析 Protobuf → 张量 ──
        # 注意：棋盘尺寸由 top_bottom_connected 决定
        first_rule = request.rules[0].rule_vector
        rows = 18 if first_rule[21] >= 0.5 else 10  # index 21 = top_bottom_connected
        cols = 9

        board_tensors = np.zeros((batch_size, 14, rows, cols), dtype=np.float32)
        rule_tensors = np.zeros((batch_size, 28), dtype=np.float32)

        for i, (board_proto, rules_proto) in enumerate(
            zip(request.boards, request.rules)):
            board_dict = self._proto_to_dict(board_proto)
            board_tensors[i] = board_to_tensor(board_dict, rows, cols)
            rule_tensors[i] = np.array(rules_proto.rule_vector, dtype=np.float32)

        board_batch = torch.from_numpy(board_tensors).to(self.device)
        rule_batch = torch.from_numpy(rule_tensors).to(self.device)

        # ── 批量前向传播 ──
        with torch.no_grad():
            if self.use_amp:
                with torch.cuda.amp.autocast():
                    policy_logits, values = self.model(board_batch, rule_batch)
            else:
                policy_logits, values = self.model(board_batch, rule_batch)

        policy_probs = torch.softmax(policy_logits, dim=1).cpu().numpy()
        values_np = values.cpu().numpy()

        # ── 组装响应 ──
        response = ucc_chess_pb2.InferenceResponse()
        for i in range(batch_size):
            pv = response.results.add()
            pv.policy.extend(policy_probs[i].tolist())
            pv.value = float(values_np[i][0])

        return response

    def _proto_to_dict(self, board_proto):
        """Protobuf BoardStateProto → 兼容现有 board_to_tensor 的字典。"""
        return {
            "rows": board_proto.rows,
            "cols": board_proto.cols,
            "redTurn": board_proto.red_turn,
            "entries": [
                {
                    "row": e.row,
                    "col": e.col,
                    "pieceTypes": [
                        ucc_chess_pb2.PieceType.Name(pt)
                        for pt in e.piece_types
                    ],
                }
                for e in board_proto.entries
            ],
        }


def serve(model_path: str, port: int = 50051):
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=4),
        options=[
            ('grpc.max_message_length', 100 * 1024 * 1024),  # 100MB
            ('grpc.max_receive_message_length', 100 * 1024 * 1024),
        ],
    )
    ucc_chess_pb2_grpc.add_InferenceServiceServicer_to_server(
        InferenceServicer(model_path), server
    )
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    print(f"✅ 推理服务已启动，端口: {port}")
    server.wait_for_termination()
```

### 4.2 修改 `selfplay.py`：支持 gRPC 推理

新增 `GrpcInferenceClient` Python 类（供自由对战模式使用，实际批量推理由 Java BatchingEngine 驱动）：

```python
# ucc-ai/python/grpc_inference.py
class GrpcInferenceClient:
    """gRPC 推理客户端 — 供 Python 侧自由对战/评估使用。"""
    def __init__(self, host="localhost", port=50051):
        self.channel = grpc.insecure_channel(f"{host}:{port}")
        self.stub = ucc_chess_pb2_grpc.InferenceServiceStub(self.channel)

    def infer_batch(self, boards, rules):
        """批量推理。"""
        request = ucc_chess_pb2.InferenceRequest()
        for b, r in zip(boards, rules):
            request.boards.append(self._dict_to_proto(b))
            request.rules.append(self._rule_to_proto(r))
        return self.stub.BatchInfer(request)
```

### 4.3 GPU 性能调优

| 优化项 | 方法 | 预期提升 |
|--------|------|----------|
| AMP 混合精度 | `torch.cuda.amp.autocast()` | 1.5-2× 速度 |
| ONNX + TensorRT | `torch.onnx.export()` → TensorRT | 2-5× 速度 |
| Batch=128 推理 | gRPC 批量打包 | 填满 GPU 利用率 |
| 模型 warm-up | 启动时跑 10 次 dummy inference | 避免首次延迟 |

---

## 五、ucc-server Netty WebSocket 接入层

### 5.1 WebSocket 协议

客户端（ucc-app / 网页）通过 WebSocket 连接 `ucc-server`，消息格式为 `WsMessage` (Protobuf binary)。

连接流程：

```
Client                          ucc-server
  │                                  │
  │── CONNECT ws://host:8080/chess ──│
  │                                  │
  │── {"type":"CREATE_ROOM", ...} ──▶│
  │◀── {"type":"ROOM_CREATED", room_code:"ABCD"} ──│
  │                                  │
  │   ... 另一玩家 JOIN_ROOM ...      │
  │                                  │
  │◀── {"type":"MATCH_FOUND", ...} ──│
  │                                  │
  │── {"type":"SUBMIT_MOVE", ...} ──▶│
  │◀── {"type":"OPPONENT_MOVE", ...} │
  │                                  │
```

### 5.2 Netty 管道

```java
public class NettyWsServer {
    public void start(int port) {
        EventLoopGroup bossGroup = new EventLoopGroup(1);
        EventLoopGroup workerGroup = new EventLoopGroup(
            Runtime.getRuntime().availableProcessors() * 2);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(new HttpServerCodec())
                        .addLast(new HttpObjectAggregator(65536))
                        .addLast(new WebSocketServerProtocolHandler("/chess"))
                        .addLast(new ProtobufCodec())          // Protobuf 编解码
                        .addLast(new WsSessionHandler());      // 业务逻辑
                }
            });

        bootstrap.bind(port).sync();
    }
}
```

---

## 六、训练流水线完整流程（第四阶段）

### 6.1 数据流

```
┌───────────────────────────────────────────────────────────────┐
│  ucc-server (Java 21 虚拟线程)                                 │
│                                                                 │
│  ┌────────────┐    ┌────────────┐    ┌─────────────────────┐  │
│  │ 100 Workers │───▶│ Disruptor  │───▶│ BatchAssembler      │  │
│  │ (自博弈+MCTS)│    │ RingBuffer │    │ → gRPC → Python     │  │
│  └────────────┘    └────────────┘    └─────────────────────┘  │
│         │                                                        │
│         │ 每局结束后                                             │
│         ▼                                                        │
│  ┌─────────────────┐    ┌──────────────────────────────────┐  │
│  │TrainingDataColl.│───▶│ Redis ReplayBuffer               │  │
│  │ (训练样本)        │    │ (共享存储，Python 端消费)          │  │
│  └─────────────────┘    └──────────────┬───────────────────┘  │
└────────────────────────────────────────┼──────────────────────┘
                                         │
┌────────────────────────────────────────▼──────────────────────┐
│  ucc-ai Python                                                 │
│                                                                 │
│  ┌───────────────────┐    ┌──────────────────────────────┐    │
│  │ TrainingWorker    │───▶│ 从 Redis 拉取样本 → 训练 →    │    │
│  │ (独立进程)         │    │ 更新模型 → gRPC PushWeights   │    │
│  └───────────────────┘    └──────────────────────────────┘    │
│                                                                 │
│  ┌───────────────────┐                                        │
│  │ InferenceServer   │ ◀── 接收批量推理请求 (来自 Java)        │
│  │ (GPU 批处理)       │ ──▶ 返回 Policy + Value                │
│  └───────────────────┘                                        │
└────────────────────────────────────────────────────────────────┘
```

### 6.2 课程学习 + 并行化

```java
// TrainingOrchestrator.java
public class TrainingOrchestrator {
    public void startCurriculumTraining() {
        int totalIterations = 2000;
        for (int iter = 0; iter < totalIterations; iter++) {
            // 1. 确定当前阶段
            float progress = (float) iter / totalIterations;
            CurriculumStage stage = getStage(progress);

            // 2. 生成规则向量
            float[] ruleVector = CurriculumRuleGenerator.generate(iter, totalIterations);

            // 3. 启动 100 个并行自博弈 Worker
            CompletableFuture<Void> allGames = startParallelGames(100, ruleVector);

            // 4. 等待全部对局完成
            allGames.join();

            // 5. 触发训练
            grpcClient.triggerTraining();

            // 6. 评估（每 50 轮）
            if (iter % 50 == 0) {
                evaluate();
            }
        }
    }
}
```

---

## 七、实施路线图

### Phase 1: 通信基础（第 1-2 周）

| 任务 | 模块 | 产出 |
|------|------|------|
| 定义 `.proto` 文件 | `ucc-common/src/main/proto/` | `ucc_chess.proto` |
| Maven protobuf 插件配置 | `ucc-common/pom.xml` | 自动生成 Java 类 |
| Python protobuf 编译 | `ucc-ai/python/` | `ucc_chess_pb2.py` |
| P0 线程安全改造（全部 4 项） | `ucc-core/` | 线程安全的 GameEngine/Board |
| JDK 21 升级 | 全项目 | `pom.xml` 修改 |

### Phase 2: ucc-server 核心（第 3-5 周）

| 任务 | 模块 | 产出 |
|------|------|------|
| 创建 `ucc-server` Maven 模块 | `ucc-server/pom.xml` | 项目骨架 |
| `ServerRoom` + `RoomManager` | `ucc-server/room/` | 对局管理 |
| `MatchmakingServiceImpl` | `ucc-server/match/` | 匹配系统 |
| Netty WebSocket 服务端 | `ucc-server/net/` | 接入层 |
| `BatchingEngine` + `SelfPlayWorker` | `ucc-server/train/` | 批量推理引擎 |

### Phase 3: ucc-ai 推理服务（第 4-6 周）

| 任务 | 模块 | 产出 |
|------|------|------|
| gRPC InferenceServer | `ucc-ai/python/inference_server.py` | GPU 批量推理 |
| ONNX + TensorRT 导出 | `ucc-ai/python/export_onnx.py` | 推理加速 |
| ReplayBuffer → Redis | `ucc-ai/python/redis_buffer.py` | 共享存储 |
| `GrpcInferenceClient`（Java 侧） | `ucc-server/net/GrpcInferenceClient.java` | 通信客户端 |

### Phase 4: 大规模训练（第 7-8 周）

| 任务 | 模块 | 产出 |
|------|------|------|
| `TranspositionTable` 实现 | `ucc-server/train/` | 局面缓存 |
| `TrainingOrchestrator` 课程学习 | `ucc-server/train/` | 训练编排 |
| 训练监控 + 日志 | `ucc-server/` | 指标收集 |
| 压力测试（100 Workers × 72 核） | 全栈 | 性能报告 |

### Phase 5: 客户端对接（第 9-10 周）

| 任务 | 模块 | 产出 |
|------|------|------|
| `ucc-app` WebSocket 客户端替换 | `ucc-app/` | 直连 ucc-server |
| 房间 UI（创建/加入/匹配） | `ucc-app/ui/` | 大厅界面 |
| 观战功能 | `ucc-server/spectator/` + `ucc-app/` | 观战广播 |

---

## 八、关键性能指标

### 8.1 硬件匹配度

| 硬件 | 利用方式 | 预期效果 |
|------|----------|----------|
| 72 核 CPU（低频） | Java 虚拟线程 100+ 并发 | CPU 利用率 > 80% |
| RTX 3060 12GB | Batch=64~128 批量推理 | GPU 利用率 > 90% |
| 128GB RAM | TranspositionTable (1000万条目 ≈ 2GB) + JVM Heap | 内存充裕 |

### 8.2 预期吞吐量

| 指标 | 当前 (单进程) | 改造后 (ucc-server) |
|------|-------------|---------------------|
| 自我对弈速率 | ~1 局/秒 | ~50-80 局/秒 |
| GPU 推理吞吐 | ~100 pos/s (batch=1) | ~5000-10000 pos/s (batch=64) |
| 网络对局并发 | 1 局 (P2P) | 500+ 局 (有状态服务) |
| 训练样本产出 | ~100/秒 | ~5000-10000/秒 |

---

## 九、依赖清单

### 9.1 新增 Maven 依赖 (`ucc-server/pom.xml`)

```xml
<dependencies>
    <!-- ucc-core 引擎 -->
    <dependency>
        <groupId>io.github.samera2022</groupId>
        <artifactId>ucc-core</artifactId>
    </dependency>

    <!-- Netty WebSocket -->
    <dependency>
        <groupId>io.netty</groupId>
        <artifactId>netty-all</artifactId>
        <version>4.1.110.Final</version>
    </dependency>

    <!-- Protobuf + gRPC -->
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-netty-shaded</artifactId>
        <version>1.63.0</version>
    </dependency>
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-protobuf</artifactId>
        <version>1.63.0</version>
    </dependency>
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-stub</artifactId>
        <version>1.63.0</version>
    </dependency>

    <!-- LMAX Disruptor -->
    <dependency>
        <groupId>com.lmax</groupId>
        <artifactId>disruptor</artifactId>
        <version>3.4.4</version>
    </dependency>

    <!-- Redis (ReplayBuffer 共享存储) -->
    <dependency>
        <groupId>redis.clients</groupId>
        <artifactId>jedis</artifactId>
        <version>5.1.2</version>
    </dependency>

    <!-- 日志 -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.12</version>
    </dependency>
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.5.6</version>
    </dependency>
</dependencies>
```

### 9.2 新增 Python 依赖

```
grpcio>=1.63.0
grpcio-tools>=1.63.0
torch>=2.3.0
onnx>=1.16.0
onnxruntime-gpu>=1.18.0  # 或 tensorrt
redis>=5.0.0
numpy>=1.26.0
```
