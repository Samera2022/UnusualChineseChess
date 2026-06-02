package io.github.samera2022.chinese_chess.server.net;

import io.github.samera2022.chinese_chess.common.model.BoardState;
import io.github.samera2022.chinese_chess.common.model.BoardState.StackEntry;
import io.github.samera2022.chinese_chess.common.model.Piece;
import io.github.samera2022.chinese_chess.common.proto.InferenceServiceGrpc;
import io.github.samera2022.chinese_chess.common.proto.InferenceRequest;
import io.github.samera2022.chinese_chess.common.proto.InferenceResponse;
import io.github.samera2022.chinese_chess.common.proto.BoardStateProto;
import io.github.samera2022.chinese_chess.common.proto.PieceType;
import io.github.samera2022.chinese_chess.common.proto.RulesConfigProto;
import io.github.samera2022.chinese_chess.server.config.ServerConfig;
import io.github.samera2022.chinese_chess.server.train.BatchingEngine.InferenceClient;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 推理客户端，通过 Protobuf 连接 Python inference_server.py 执行批量推理。
 *
 * <p>返回 {@link List} 每个元素为 {@code [value, policy_0, policy_1, ..., policy_n]}，
 * 即完整策略分布而非压缩后的均值。</p>
 */
public class GrpcInferenceClient implements InferenceClient {

    private static final Logger logger = LoggerFactory.getLogger(GrpcInferenceClient.class);

    private final String host;
    private final int port;
    private ManagedChannel channel;
    private InferenceServiceGrpc.InferenceServiceBlockingStub stub;
    private volatile boolean connected;

    public GrpcInferenceClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public GrpcInferenceClient(ServerConfig config) {
        this(config.getGrpcHost(), config.getGrpcPort());
    }

    public boolean connect() {
        if (connected) return true;
        try {
            channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
            this.stub = InferenceServiceGrpc.newBlockingStub(channel);
            connected = true;
            logger.info("gRPC connected to {}:{}", host, port);
            return true;
        } catch (Exception e) {
            logger.error("gRPC connect failed {}:{} - {}", host, port, e.getMessage());
            return false;
        }
    }

    @Override
    public List<float[]> batchInfer(List<BoardState> states, List<float[]> ruleVectors) {
        if (!connected && !connect()) {
            logger.warn("gRPC not connected, returning mock results");
            return buildFallback(states);
        }

        try {
            InferenceRequest.Builder reqBuilder = InferenceRequest.newBuilder();
            for (int i = 0; i < states.size(); i++) {
                BoardState state = states.get(i);
                BoardStateProto.Builder boardPb = BoardStateProto.newBuilder();
                boardPb.setRows(state.getRows());
                boardPb.setCols(state.getCols());
                boardPb.setRedTurn(state.isRedTurn());
                for (StackEntry entry : state.getEntries()) {
                    BoardStateProto.StackEntry.Builder entryPb = BoardStateProto.StackEntry.newBuilder();
                    entryPb.setRow(entry.row);
                    entryPb.setCol(entry.col);
                    for (Piece.Type pt : entry.pieceTypes) {
                        entryPb.addPieceTypes(PieceType.valueOf(pt.name()));
                    }
                    boardPb.addEntries(entryPb.build());
                }
                reqBuilder.addBoards(boardPb.build());
                RulesConfigProto.Builder rulePb = RulesConfigProto.newBuilder();
                for (float v : ruleVectors.get(i)) rulePb.addRuleVector(v);
                reqBuilder.addRules(rulePb.build());
            }
            InferenceResponse response = stub.batchInfer(reqBuilder.build());
            return parseResponse(response, states);
        } catch (Exception e) {
            logger.error("gRPC batchInfer failed", e);
            return buildFallback(states);
        }
    }

    /**
     * 解析 gRPC 响应，返回完整 policy 分布。
     * <p>每个元素为 {@code [value, policy_0, policy_1, ..., policy_n]}，其中
     * policy 长度 = rows × cols。</p>
     */
    private List<float[]> parseResponse(InferenceResponse response, List<BoardState> states) {
        int count = response.getResultsCount();
        List<float[]> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            var pv = response.getResults(i);
            float value = pv.getValue();
            int policySize = states.get(i).getRows() * states.get(i).getCols();
            float[] boardResult = new float[1 + policySize];
            boardResult[0] = value;
            // 拷贝完整 policy 分布，长度不足的补零
            int copyLen = Math.min(pv.getPolicyCount(), policySize);
            for (int j = 0; j < copyLen; j++) {
                boardResult[1 + j] = pv.getPolicy(j);
            }
            result.add(boardResult);
        }
        return result;
    }

    /**
     * gRPC 不可用时的 fallback：返回 [0.5f, 0, 0, ..., 0]（value=0.5，policy 全零）。
     */
    private List<float[]> buildFallback(List<BoardState> states) {
        List<float[]> fallback = new ArrayList<>(states.size());
        for (BoardState state : states) {
            int policySize = state.getRows() * state.getCols();
            float[] boardResult = new float[1 + policySize];
            boardResult[0] = 0.5f; // value
            // policy 默认为全零（均匀无偏好）
            fallback.add(boardResult);
        }
        return fallback;
    }

    /**
     * 热更新推理模型的权重（强化学习闭环 — 训练→推理→更强的自我对弈）。
     *
     * <p>从 {@code TrainingServiceClient.pullWeights()} 获取训练后的模型权重，
     * 通过 {@code InferenceService.UpdateModel} RPC 推送给 {@code inference_server.py}，
     * 使后续的 {@link #batchInfer} 调用使用更新后的模型。</p>
     *
     * @param weightsData 模型权重的序列化字节（Python torch.save 格式）
     * @param iteration   当前训练迭代编号
     * @return true 表示更新成功
     */
    public boolean updateModel(byte[] weightsData, int iteration) {
        if (!connected && !connect()) {
            logger.warn("gRPC not connected, cannot update model");
            return false;
        }
        try {
            var request = io.github.samera2022.chinese_chess.common.proto.ModelWeights.newBuilder()
                    .setWeightsData(com.google.protobuf.ByteString.copyFrom(weightsData))
                    .setIteration(iteration)
                    .build();
            stub.updateModel(request);
            logger.info("Model weights updated to iteration {} ({} bytes)", iteration, weightsData.length);
            return true;
        } catch (Exception e) {
            logger.error("Failed to update model weights via gRPC", e);
            return false;
        }
    }

    public void shutdown() {
        connected = false;
        stub = null;
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
    }

    public boolean reconnect() { shutdown(); return connect(); }
    public boolean isConnected() { return connected; }
}
