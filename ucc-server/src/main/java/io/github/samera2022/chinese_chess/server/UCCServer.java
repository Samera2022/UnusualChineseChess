package io.github.samera2022.chinese_chess.server;

import io.github.samera2022.chinese_chess.core.rules.GameRulesConfig;
import io.github.samera2022.chinese_chess.server.config.ServerConfig;
import io.github.samera2022.chinese_chess.server.match.MatchmakingServiceImpl;
import io.github.samera2022.chinese_chess.server.net.GrpcInferenceClient;
import io.github.samera2022.chinese_chess.server.net.NettyWsServer;
import io.github.samera2022.chinese_chess.server.room.RoomManager;
import io.github.samera2022.chinese_chess.server.train.BatchingEngine;
import io.github.samera2022.chinese_chess.server.train.TrainingOrchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UCC 服务端主入口。
 *
 * <p>支持两种启动模式：
 * <ul>
 *   <li><b>默认</b>：启动 WebSocket 网络对战服务 + 匹配系统</li>
 *   <li><b>--train</b>：启动网络对战 + 课程学习训练流水线</li>
 * </ul>
 */
public class UCCServer {

    private static final Logger logger = LoggerFactory.getLogger(UCCServer.class);

    public static void main(String[] args) {
        boolean trainMode = false;
        for (String arg : args) {
            if ("--train".equals(arg)) {
                trainMode = true;
            }
        }

        ServerConfig config = ServerConfig.getInstance();
        logger.info("UCCServer starting, trainMode={}", trainMode);

        // ── 初始化 RoomManager ──
        RoomManager roomManager = new RoomManager(config.getRoomTimeoutMinutes());

        // ── 初始化 MatchmakingService ──
        MatchmakingServiceImpl matchmakingService = new MatchmakingServiceImpl(roomManager);

        // ── 初始化 gRPC 推理客户端（连接 Python）──
        GrpcInferenceClient inferenceClient = new GrpcInferenceClient(config);

        // ── 初始化 BatchingEngine ──
        BatchingEngine batchingEngine = new BatchingEngine(config, inferenceClient);

        // ── 启动 Netty WebSocket 服务端 ──
        NettyWsServer wsServer = new NettyWsServer(
                config.getWsPort(), roomManager, matchmakingService);

        try {
            wsServer.start();
            logger.info("UCCServer started on port {}", config.getWsPort());
        } catch (Exception e) {
            logger.error("Failed to start WebSocket server", e);
            System.exit(1);
        }

        // ── 训练模式：同步执行训练，完成后退出 ──
        if (trainMode) {
            TrainingOrchestrator orchestrator = new TrainingOrchestrator(
                    batchingEngine, config, null /* RedisReplayBuffer */);
            try {
                orchestrator.startCurriculumTraining();
                logger.info("Training completed, shutting down");
            } catch (Exception e) {
                logger.error("Training failed", e);
            }
            wsServer.stop();
            roomManager.shutdown();
            inferenceClient.shutdown();
            System.exit(0);
        }

        // ── 非训练模式：等待网络服务（保持 JVM 运行）──
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
