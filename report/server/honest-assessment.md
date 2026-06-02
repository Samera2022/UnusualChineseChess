# 实际完成度诚实评估

| Phase | 任务 | 实际状态 | 说明 |
|-------|------|----------|------|
| P0-1 | GameEngine 线程安全化 | ✅ 真实完成 | ReentrantLock + CopyOnWriteArrayList + ConcurrentHashMap |
| P0-2 | Board 快照化 | ✅ 真实完成 | volatile + synchronized 快照 |
| P0-3 | GameRulesConfig 静态线程池 | ✅ 真实完成 | static ExecutorService |
| P1-1 | CheckDetector SimulationBoard | ✅ 真实完成 | 副本操作 |
| P2-1 | MoveValidator 不可变 | ✅ 真实完成 | final 字段 + 移除 setter |
| P2-2 | Board.turn volatile | ✅ 真实完成 | |
| P1-2 | JDK 21 | ✅ 真实完成 | |
| Phase1 | proto 定义 + Maven 插件 | ✅ 真实完成 | |
| Phase2 | ServerRoom | ✅ 完成 | |
| Phase2 | RoomManager | ✅ 完成 | |
| Phase2 | MatchmakingServiceImpl | ✅ 完成 | 反射 hack 已消除 |
| Phase2 | NettyWsServer | ✅ 完成 | |
| Phase2 | WsSessionHandler | ✅ 完成 | 含对手通知 |
| Phase2 | GrpcInferenceClient | ✅ 真实完成 | 使用 InferenceServiceGrpc stub，BoardState→Proto 正确转换 |
| Phase2 | BatchingEngine | ✅ 真实完成 | boardStateToTensor + GameBatchResult + flushBatch 真实分派 |
| Phase2 | SelfPlayWorker | ✅ 完成 | one-shot，MCTSAgent 纯启发式 MCTS |
| Phase3 | inference_server.py | ✅ 真实完成 | HAS_PROTO 分支启用真实 gRPC 注册 |
| Phase3 | export_onnx.py | ✅ 真实完成 | num_res_blocks=5 已修复 |
| Phase4 | TranspositionTable | ✅ 真实完成 | |
| Phase4 | TrainingOrchestrator | ✅ 完成 | 课程学习 + 评估对战（含 redWins/blackWins 统计） |
| Phase4 | RedisReplayBuffer | ✅ 完成 | |
| Phase5 | WsClient | ✅ 完成 | 5s 超时保护 |
| Phase5 | LobbyPanel | ✅ 完成 | 连接/房间/匹配按钮 |
| Phase5 | SpectatorManager | ✅ 完成 | |
