# ucc-server 开发方案总索引

> 版本: 1.0  
> 日期: 2026-06-01

---

## 文档导航

| 文档 | 内容 | 优先级 |
|------|------|--------|
| [../ucc-core并发评估与解耦分析.md](../ucc-core并发评估与解耦分析.md) | 当前代码审计 + 是否需要解耦的判断依据 | 先读 |
| [P0线程安全改造方案.md](P0线程安全改造方案.md) | ucc-core 线程安全改造（7 项，含代码示例 + 测试用例） | 🔴 立即 |
| [P0改造实现审计报告.md](P0改造实现审计报告.md) | **实际实现审计** — 逐项检查 + 发现 4 个问题 + 编译验证 ✅ | 🔴 必读 |
| [ucc-server解耦与高并发架构方案.md](ucc-server解耦与高并发架构方案.md) | 完整架构设计（Protobuf/gRPC/Netty/BatchingEngine/训练流水线） | 🔴 立即 |
| [ucc-ai部署模式对比.md](ucc-ai部署模式对比.md) | 独立进程(gRPC) vs 内嵌(DJL) vs 混合架构，含决策矩阵 | 🟡 选读 |

---

## 决策概要

```
是否需要解耦出 ucc-server？
  └─ ✅ 是，强烈建议

当前能否支撑高并发？
  └─ ❌ 不能。核心类全部非线程安全，P2P 架构仅支持 2 人局域网

先做什么？
  └─ P0 线程安全改造（GameEngine 加锁、Board 快照化、GameRulesConfig 静态线程池）
     同时定义 .proto 通信协议

ucc-ai 部署方式？
  └─ 🔒 最终决策: 模式 A — 独立进程 gRPC（加权总分 9.1/10）
     ❌ 已排除: 模式 B（DJL 内嵌，4.1/10）、模式 C（混合，暂不需要）

后续路线：
  Phase 1 (1-2周) → Protobuf + P0 改造 + JDK 21
  Phase 2 (3-5周) → ucc-server 核心（Room/Match/BatchingEngine）
  Phase 3 (4-6周) → ucc-ai gRPC 推理服务 + TensorRT 优化
  Phase 4 (7-8周) → 大规模训练 + 评估
  Phase 5 (9-10周) → ucc-app 客户端对接
```

---

## 模块变更总览

| 模块 | 变更类型 | 说明 |
|------|----------|------|
| `ucc-common` | 新增文件 | `src/main/proto/ucc_chess.proto` |
| `ucc-core` | 修改 4 个类 | `GameEngine`, `Board`, `GameRulesConfig`, `CheckDetector` |
| `ucc-api` | 无变更 | — |
| `ucc-app` | Phase 5 修改 | WebSocket 客户端替代 P2P |
| `ucc-ai` | 重大改造 | 新增 inference_server.py (gRPC), export_onnx.py, Redis buffer |
| `ucc-server` | **新建模块** | 完整服务端实现 |
