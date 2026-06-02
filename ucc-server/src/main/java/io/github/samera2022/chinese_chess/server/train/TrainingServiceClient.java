package io.github.samera2022.chinese_chess.server.train;

import io.github.samera2022.chinese_chess.ai.TrainingDataCollector;
import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.proto.BoardStateProto;
import io.github.samera2022.chinese_chess.common.proto.ModelWeights;
import io.github.samera2022.chinese_chess.common.proto.PieceType;
import io.github.samera2022.chinese_chess.common.proto.RulesConfigProto;
import io.github.samera2022.chinese_chess.common.proto.TrainingSample;
import io.github.samera2022.chinese_chess.common.proto.TrainingServiceGrpc;
import io.github.samera2022.chinese_chess.server.config.ServerConfig;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC 训练客户端，通过 Protobuf 连接 Python train_server.py。
 *
 * <p>提供两个 RPC 调用：
 * <ul>
 *   <li>{@link #pushSamples} — 将一批训练样本流式推送给 Python 端</li>
 *   <li>{@link #pullWeights} — 从 Python 端拉取训练后的模型权重</li>
 * </ul>
 *
 * <p>与 {@link io.github.samera2022.chinese_chess.server.net.GrpcInferenceClient} 共用同一
 * gRPC 目标主机，但使用独立的端口（由 {@code server.grpc.training_port} 配置，默认 50052）。
 */
public class TrainingServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(TrainingServiceClient.class);

    private final String host;
    private final int port;
    private ManagedChannel channel;
    private TrainingServiceGrpc.TrainingServiceBlockingStub blockingStub;
    private TrainingServiceGrpc.TrainingServiceStub asyncStub;
    private volatile boolean connected;

    public TrainingServiceClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public TrainingServiceClient(ServerConfig config) {
        this(config.getGrpcHost(), config.getGrpcTrainingPort());
    }

    /**
     * 建立 gRPC 连接。
     *
     * @return true 表示连接成功或已连接
     */
    public boolean connect() {
        if (connected) return true;
        try {
            channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
            blockingStub = TrainingServiceGrpc.newBlockingStub(channel);
            asyncStub = TrainingServiceGrpc.newStub(channel);
            connected = true;
            logger.info("TrainingService gRPC connected to {}:{}", host, port);
            return true;
        } catch (Exception e) {
            logger.error("TrainingService gRPC connect failed {}:{} - {}", host, port, e.getMessage());
            return false;
        }
    }

    /**
     * 将一批训练样本流式推送给 Python 训练服务。
     *
     * <p>将 Java 端的 {@link TrainingDataCollector.TrainingSample} 列表转换为 protobuf
     * {@link TrainingSample} 消息，通过 gRPC 客户端流式发送。
     *
     * @param samples 训练样本列表，每个样本含 board、ruleVector、policy、value
     * @return true 表示推送成功
     */
    public boolean pushSamples(List<TrainingDataCollector.TrainingSample> samples) {
        if (!connected && !connect()) {
            logger.warn("TrainingService not connected, skipping pushSamples");
            return false;
        }

        final CountDownLatch finishLatch = new CountDownLatch(1);
        final AtomicReference<Boolean> success = new AtomicReference<>(false);

        StreamObserver<io.github.samera2022.chinese_chess.common.proto.Empty> responseObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(io.github.samera2022.chinese_chess.common.proto.Empty value) {
                        // 服务端返回 Empty，不做处理
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.error("TrainingService.PushSamples failed", t);
                        success.set(false);
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        logger.debug("TrainingService.PushSamples completed");
                        success.set(true);
                        finishLatch.countDown();
                    }
                };

        StreamObserver<TrainingSample> requestObserver = asyncStub.pushSamples(responseObserver);

        try {
            for (TrainingDataCollector.TrainingSample sample : samples) {
                TrainingSample protoSample = toProtoSample(sample);
                requestObserver.onNext(protoSample);
            }
            requestObserver.onCompleted();

            // 等待完成或超时（30 秒）
            if (!finishLatch.await(30, TimeUnit.SECONDS)) {
                logger.warn("TrainingService.PushSamples timeout after 30s");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("TrainingService.PushSamples interrupted", e);
            requestObserver.onError(e);
            return false;
        } catch (Exception e) {
            logger.error("TrainingService.PushSamples error", e);
            requestObserver.onError(e);
            return false;
        }

        return success.get();
    }

    /**
     * 从 Python 训练服务拉取训练后的模型权重。
     *
     * @return 模型权重字节数组，若失败返回 null
     */
    public byte[] pullWeights() {
        if (!connected && !connect()) {
            logger.warn("TrainingService not connected, skipping pullWeights");
            return null;
        }

        try {
            ModelWeights response = blockingStub.pullWeights(
                    io.github.samera2022.chinese_chess.common.proto.Empty.newBuilder().build());
            byte[] weightsData = response.getWeightsData().toByteArray();
            logger.info("Pulled model weights, iteration={}, size={} bytes",
                    response.getIteration(), weightsData.length);
            return weightsData;
        } catch (Exception e) {
            logger.error("TrainingService.PullWeights failed", e);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 内部辅助方法
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 将 Java TrainingSample 转换为 protobuf TrainingSample 消息。
     */
    private TrainingSample toProtoSample(TrainingDataCollector.TrainingSample sample) {
        TrainingSample.Builder builder = TrainingSample.newBuilder();

        // 1. board → BoardStateProto
        BoardState state = sample.board;
        BoardStateProto.Builder boardPb = BoardStateProto.newBuilder();
        boardPb.setRows(state.getRows());
        boardPb.setCols(state.getCols());
        boardPb.setRedTurn(state.isRedTurn());
        for (BoardState.StackEntry entry : state.getEntries()) {
            BoardStateProto.StackEntry.Builder entryPb = BoardStateProto.StackEntry.newBuilder();
            entryPb.setRow(entry.row);
            entryPb.setCol(entry.col);
            for (Piece.Type pt : entry.pieceTypes) {
                entryPb.addPieceTypes(PieceType.valueOf(pt.name()));
            }
            boardPb.addEntries(entryPb.build());
        }
        builder.setBoard(boardPb.build());

        // 2. rules → RulesConfigProto
        RulesConfigProto.Builder rulePb = RulesConfigProto.newBuilder();
        for (float v : sample.rules) {
            rulePb.addRuleVector(v);
        }
        builder.setRules(rulePb.build());

        // 3. policy
        for (float v : sample.policy) {
            builder.addPolicy(v);
        }

        // 4. value
        builder.setValue(sample.value);

        return builder.build();
    }

    /**
     * 关闭 gRPC 连接。
     */
    public void shutdown() {
        connected = false;
        if (channel != null) {
            try {
                channel.shutdown();
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
            channel = null;
        }
        blockingStub = null;
        asyncStub = null;
    }

    public boolean reconnect() {
        shutdown();
        return connect();
    }

    public boolean isConnected() {
        return connected;
    }
}
