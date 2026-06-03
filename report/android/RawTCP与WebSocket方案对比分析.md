# Raw TCP Socket 与 WebSocket 方案对比分析

> 针对 Unusual Chinese Chess 局域网对局场景

---

## 1. 场景约束

在进行技术选型之前, 明确本项目局域网对局的核心需求:

| 需求 | 说明 |
|------|------|
| 通信模式 | 点对点(P2P), 一方作为主机, 另一方作为客户端直连 |
| 数据量 | 极低: 每次走子传输 4-6 个整数坐标 + 状态标记 |
| 实时性 | 对延迟不敏感: 回合制游戏, 人类操作间隔数秒 |
| 跨平台 | **需要桌面端(Windows) 与 Android 端互相联机** |
| 拓扑 | 只有 2 个节点参与, 不存在多人广播 |
| 防火墙 | 纯局域网, 不涉及 NAT 穿透 |

---

## 2. 两种方案的架构图

### 方案 A: Raw TCP Socket (桌面端当前方案)

```
┌───────────────────────────────────────────────┐
│  桌面端 (主机)                                  │
│  NetworkSession.startServer(port)              │
│  ├── ServerSocket.accept() → Socket            │
│  ├── BufferedReader.readLine()                 │
│  ├── PrintWriter.println()                     │
│  └── 协议: "MOVE 0 4 0 5 -1\n"               │
└──────────────────────┬────────────────────────┘
                       │ TCP 连接 (同一局域网)
┌──────────────────────▼────────────────────────┐
│  桌面端/Android (客户端)                        │
│  NetworkSession.connect(host, port)            │
│  ├── new Socket(host, port)                    │
│  ├── BufferedReader.readLine()                 │
│  ├── PrintWriter.println()                     │
│  └── 协议: "MOVE 0 4 0 5 -1\n"               │
└───────────────────────────────────────────────┘
```

### 方案 B: WebSocket (Android 端当前方案)

```
┌───────────────────────────────────────────────┐
│  桌面端/Android (主机)                          │
│  Java-WebSocket WebSocketServer               │
│  ├── onMessage() 回调                          │
│  ├── conn.send()                               │
│  └── 协议: {"cmd":"MOVE","from":[0,4],"to":[0,5]}│
└──────────────────────┬────────────────────────┘
                       │ WebSocket (同一局域网)
┌──────────────────────▼────────────────────────┐
│  Android (客户端)                               │
│  OkHttp WebSocket                              │
│  ├── onMessage() 回调                           │
│  ├── webSocket.send()                          │
│  └── 协议: {"cmd":"MOVE","from":[0,4],"to":[0,5]}│
└───────────────────────────────────────────────┘
```

---

## 3. 全面对比

### 3.1 技术难度与工作量

| 维度 | Raw TCP Socket | WebSocket |
|------|---------------|-----------|
| 当前桌面端实现 | ✅ **已有** — `NetworkSession.java` (630行), 已部署调试成熟 | ❌ **无** — 桌面端需要额外开发 |
| 当前 Android 端实现 | ❌ **无** — 需重写为 TCP | ✅ **已有** — `LobbyPanel.java` 中实现 |
| 跨平台一致性 | ✅ **单一实现, 两平台通用** | ❌ **桌面端需另写一套** |
| 第三方依赖数 | **0** (Java 标准库) | **2** (OkHttp + Java-WebSocket) |
| APK 体积影响 | 0 KB | ~350 KB (OkHttp + Java-WebSocket) |

**分析**: Raw TCP 的优势在于桌面端已经有一套经过充分测试、承载了完整协议(包括签名验证、强制走子、状态同步等)的 `NetworkSession.java`, 约 630 行代码。Android 端使用它意味着:
- 协议实现完全相同, 无兼容性问题
- 0 额外第三方依赖
- 修改协议时只需改一处

### 3.2 协议格式对比

| 维度 | Raw TCP (当前桌面方案) | WebSocket (当前 Android 方案) |
|------|----------------------|------------------------------|
| 消息示例 | `MOVE 0 4 0 5 -1` | `{"cmd":"MOVE","fromRow":0,"fromCol":4,"toRow":0,"toCol":5}` |
| 消息大小 (每步走子) | ~20 字节 | ~65 字节 (JSON) |
| 序列化/反序列化 | `line.split(" ")` 按空格解析 | `JsonParser.parseString()` + Gson |
| 解析复杂度 | **O(1) 极低** | O(n) (JSON 解析有开销) |
| 可读性/调试难度 | 纯文本, 一目了然 | JSON 更结构化的但肉眼扫描稍慢 |
| 协议扩展性 | 按位置解析, 新增字段需改解析逻辑 | 按 key 解析, 新增字段不影响旧解析 |

**分析**: 对于本项目(中国象棋走子), 消息极其简单(起始坐标 + 目标坐标 + 索引), Raw TCP 的纯文本行协议在简单性上完胜。JSON 带来的结构化优势在此场景下价值有限, 因为消息结构几乎不会频繁变动。

### 3.3 性能对比 (理论)

| 维度 | Raw TCP | WebSocket |
|------|---------|-----------|
| 握手延迟 | **1 RTT** (TCP 三次握手) | **2 RTT** (TCP 握手 + WS Upgrade) |
| 单消息开销 | **0** (纯 payload) | **6-14 字节** (WebSocket 帧头) |
| 消息吞吐 (受限于回合制) | 无关紧要 | 无关紧要 |
| CPU 开销 | 极低 | 略高 (JSON 序列化) |

**分析**: 对于回合制中国象棋(人类操作间隔 >1 秒), 性能差异完全可以忽略。两种方案在延迟和吞吐上都不会成为瓶颈。WebSocket 多出的 1 个 RTT 握手在局域网环境下(<1ms)没有实际影响。

### 3.4 连接管理

| 维度 | Raw TCP | WebSocket |
|------|---------|-----------|
| 半开连接检测 | ❌ 需应用层心跳 | ✅ 有 Ping/Pong 帧 (但本项目已自己实现 PING) |
| 断线重连 | 需应用层实现 | 需应用层实现 (两者无本质区别) |
| 同时服务多客户端 | 需应用层多线程 | 框架内置连接池 |
| 优雅关闭 | 需应用层 `DISCONNECT` 指令 | 有 Close 帧 |

**分析**: 本项目只涉及 2 人对局(1 个客户端连接), WebSocket 内置的多客户端能力无用武之地。桌面端的 `NetworkSession` 已经实现了 PING/PONG 心跳和 DISCONNECT 指令, 半开检测需求已满足。

### 3.5 跨平台兼容性

| 维度 | Raw TCP | WebSocket |
|------|---------|-----------|
| Windows (桌面端当前) | ✅ `java.net.Socket` | ✅ OkHttp/Java-WebSocket |
| Android | ✅ `java.net.Socket` | ✅ OkHttp |
| 需要统一协议 | ✅ 直接用同一份代码 | ❌ 桌面和 Android 需分别实现 |
| 与 ucc-server 集成 | ❌ 与 Netty WS 不兼容 | ✅ 可连接 ucc-server 的 WebSocket |

**分析**: 这里有一个重要权衡:
- **Raw TCP** 只在 2 人对局域网对局时使用, 不能与 `ucc-server` (Netty WebSocket) 通信
- **WebSocket** 既可以用于点对点局域网, 理论上也可以连接 `ucc-server` 的 Netty WebSocket 端口

但需注意: 连接 `ucc-server` 意味着需要一个中央服务器, 这对"纯局域网对局"的需求来说不是必需的。而且 `ucc-server` 的协议格式与 Android `LobbyPanel` 的协议也未必一致。

### 3.6 安全性

| 维度 | Raw TCP (当前方案) | WebSocket |
|------|-------------------|-----------|
| 传输加密 | ❌ 明文 (局域网可接受) | ❌ 明文 (ws://, 非 wss://) |
| 身份验证 | ✅ HMAC-SHA256 已实现 | 需自行实现 |
| 重放攻击防护 | ✅ 序列号 + 签名已实现 | 需自行实现 |

**分析**: 桌面端 `NetworkSession` 已经实现了一套 HMAC-SHA256 签名验证 + 临时 token 交换的完整安全方案。如果改用 WebSocket, 这套安全逻辑需要重新实现, 增加了工作量。

---

## 4. 综合评估

### 4.1 评分表

| 评估项 | 权重 | Raw TCP | WebSocket |
|--------|------|---------|-----------|
| 桌面端现有工作量 | 30% | **10/10** (已有完整实现) | 0/10 (需重写) |
| Android 端工作量 | 20% | **8/10** (复用 NetworkSession) | 6/10 (已有 LobbyPanel, 需调整) |
| 协议简单性 | 15% | **9/10** (按空格分割) | 7/10 (JSON 解析) |
| 跨平台兼容性 | 15% | **10/10** (同一份代码) | 5/10 (两份不同实现) |
| 与 ucc-server 互通 | 5% | 0/10 | **8/10** |
| 三方依赖数 | 5% | **10/10** (0 依赖) | 3/10 (2 个库) |
| 可维护性 | 10% | **9/10** (单一协议栈) | 5/10 (两份协议栈) |
| **加权总分** | **100%** | **8.65/10** | **4.35/10** |

### 4.2 建议方案

**推荐: Android 端复用桌面端的 Raw TCP Socket 方案。**

理由:

1. **桌面端 `NetworkSession.java` 已经完整实现**了:
   - ServerSocket 监听/接受连接
   - Socket 客户端连接 + 超时
   - PING/PONG 心跳 + 延迟测量
   - HMAC-SHA256 签名验证 (HmacSHA256 在 Android 上完全可用)
   - 临时 token 交换 (HELLO 协议)
   - 版本协商
   - 完整的走子/悔棋/强制走子/状态同步指令集
   - 断线重连缓存机制 (pendingJsonFrame)

2. **Android 完全支持 `java.net.ServerSocket` 和 `java.net.Socket`**
   - 这是标准 Java API, Android 从 API 1 就支持
   - 不需要任何额外依赖
   - HmacSHA256 在 Android 上可用(`javax.crypto.Mac` 在 Android 上存在)

3. **两平台共用同一份 `NetworkSession.java`**
   - 因为 `ucc-api` 已经被 sourceSets 引用
   - 修改协议时只需改一个文件
   - 桌面端和 Android 端自动同步

### 4.3 实施步骤

```
1. 停止开发 LobbyPanel 中的 WebSocket 客户端/服务端代码
   (或保留 LobbyPanel 仅做 UI/房间管理, 通信底层改调 NetworkSession)

2. Android 端直接使用 ucc-api/net/NetworkSession.java
   ├── 主机端: NetworkSession.startServer(port)
   ├── 客户端: NetworkSession.connect(host, port)
   └── 走子: session.sendMove(fr, fc, tr, tc, stackIndex)

3. 修改 LobbyPanel 的业务逻辑
   ├── 当前: LobbyPanel 自己做 WS 通信 + 房间管理
   └── 改为: LobbyPanel 只做 UI 呈现, 通信委托给 NetworkSession
        (或者更简单: LobbyPanel 只负责输入 IP/端口, 启动 NetworkSession)

4. 删除或移除 LobbyPanel 中的 WebSocket 代码
   ├── 删除 OkHttp WebSocket 客户端逻辑
   ├── 删除 Java-WebSocket 服务端逻辑 (LobbyWebSocketServer)
   └── 删除 mDNS 发现注册/发现的 WebSocket 相关代码
       (mDNS 发现仍可保留, 作为辅助发现手段)
```

### 4.4 需要额外注意

**Android 后台限制**: `NetworkSession` 中的 `ServerSocket.accept()` 在 Android 上需要在后台线程运行(当前已使用独立 `ioThread`)。但 Android 8+ 对后台服务有限制, 如果主机 App 退到后台, 系统可能会杀死线程。建议:
- 使用 `ForegroundService` 运行 TCP 服务器
- 或者在 Activity 的生命周期中管理, 确保屏幕常亮

---

## 5. 结论

| 方案 | 总体评价 |
|------|---------|
| **Raw TCP** (推荐) | 桌面端已有 630 行完整实现, Android 可直接复用, **零额外依赖**, 跨平台同协议, 加**权评分 8.65/10** |
| WebSocket | 多 2 个第三方依赖, 需在桌面端重写整套协议, 两份协议栈维护成本翻倍, 加权评分 4.35/10 |

**核心考量**: 本项目不是浏览器应用, 不需要通过 HTTP 代理或防火墙。两台设备在同一局域网内, Raw TCP 是最直接、最简单的方案。桌面端已经投入了相当的开发精力实现了完整的 `NetworkSession` 协议栈(包括 HMAC 签名、token 交换、强制走子协议等), Android 端复用这套代码是最经济的方式。
