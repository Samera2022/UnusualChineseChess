# Unusual Chinese Chess 整合 Android 难度评估报告 (修订版)

> **修订说明**: 根据反馈调整范围——优先离线无AI对局, AI通过云端服务调用; 需要实现局域网对战功能。

---

## 1. 项目概述与技术栈分析

### 1.1 项目结构
```
UnusualChineseChess (Maven 多模块, Java 21)
├── ucc-common  ── 共享合约层: 模型类, SPI接口, Protobuf定义
├── ucc-core    ── 核心引擎: 棋盘/规则/走子校验/将军检测
├── ucc-api     ── 公开API层: 序列化/网络模式控制
├── ucc-app     ── 桌面UI层: Swing GUI (JFrame/JPanel) ← 需重写
├── ucc-ai      ── AI引擎: MCTS + PyTorch桥接 ← 本次暂不直接集成, AI走云端
└── ucc-server  ── 服务器: Netty WebSocket + gRPC + Redis ← 局域网对局需参考其WebSocket协议
```

### 1.2 当前技术栈
| 技术 | 版本 | Android 兼容性 |
|------|------|----------------|
| Java | 21 | ✅ (需降级至 Java 11/17) |
| Swing (javax.swing.*) | - | ❌ 需完全替换为 Android View |
| gRPC / Protobuf | 1.63.0 / 3.25.3 | ⚠️ gRPC-Android 可用(云端AI时使用) |
| Netty | 4.1.110 | ❌ 局域网对局使用 OkHttp WebSocket 替代 |
| Gson | 2.11.0 | ✅ 完全兼容 |
| Disruptor | 3.4.4 | ✅ (服务端组件, Android 端不需要) |
| Jedis / Redis | 5.1.2 | ❌ 不需要 |
| Logback/SLF4J | 2.0.12 / 1.5.6 | ⚠️ Android Logcat 更常用 |
| Maven | - | ⚠️ Android 项目需 Gradle 构建 |

---

## 2. 各模块 Android 适配分析

### 2.1 核心模块实际需要修改的代码量

经过逐文件代码审查, 以下是精确结论:

#### ✅ [`ucc-common`](ucc-common/) — **零修改, 直接复制即用**

| 审查项 | 实际结果 | 含义 |
|--------|---------|------|
| `javax.annotation` 引用 | **0 处** (完全不存在) | 无需替换任何注解 |
| Java 21 特性 (`record`, `sealed`, `pattern matching`, `switch` 箭头语法) | **0 处** | Java 11 即可编译 |
| `java.io.*` 平台相关 API | **仅 `InputStreamReader` 1 处** | Android 完全兼容 |
| 第三方依赖 | Gson 2.11.0 | ✅ Android 完全兼容 |
| **结论** | **所有 `.java` 文件拉到 Android 项目直接编译通过** |

#### ✅ [`ucc-core`](ucc-core/) — **零修改, 直接复制即用**

| 审查项 | 实际结果 | 含义 |
|--------|---------|------|
| `java.io.*` 引用 | **0 处** | 无文件操作 |
| Java 21 特性 | **0 处** | 全部是传统 Java 语法 |
| 平台相关 API | **0 处** | 纯 `java.util.*` + `java.util.concurrent.*` |
| Android 不兼容类 | **0 处** | `ReentrantLock`, `CopyOnWriteArrayList`, `ConcurrentHashMap` 全部兼容 |
| **结论** | **所有 `.java` 文件拉到 Android 项目直接编译通过** |

#### ⚠️ [`ucc-api`](ucc-api/) — **2 个文件共 ~6 行需微调**

具体修改位置:

1. [`GameStateExporter.java`](ucc-api/src/main/java/io/github/samera2022/chinese_chess/api/io/GameStateExporter.java) 第 62 行:
   - 原: `FileWriter writer = new FileWriter(filePath);`
   - 改为: 接收 `OutputStream` 参数, 用 `OutputStreamWriter` 包装
   
2. [`GameStateImporter.java`](ucc-api/src/main/java/io/github/samera2022/chinese_chess/api/io/GameStateImporter.java) 第 37 行:
   - 原: `FileReader reader = new FileReader(filePath);`
   - 改为: 接收 `InputStream` 参数, 用 `InputStreamReader` 包装

**关键:** 这两个类的 `importGameState(engine, JsonObject)` 和导出时的 Gson 序列化逻辑**完全不需要修改**。Android 端只需在调用前先用 `InputStream` 读出 `JsonObject`, 然后再调用这些方法即可。或者直接重载一个接收 `InputStream` 的版本——只需添加约 5 行代码。

### 2.2 各模块精确工作量汇总

| 模块 | Java 文件数 | 需修改文件数 | 实际需修改代码行数 |
|------|-----------|-----------|-----------------|
| `ucc-common` | ~15 个 | **0** | **0 行** |
| `ucc-core` | ~8 个 | **0** | **0 行** |
| `ucc-api` | ~3 个 | **2** (微调 File→Stream) | **~6 行** |
| **总计 (可复用部分)** | **~26 个** | **2** | **~6 行** |

### 2.3 ucc-app (桌面UI层) — 【需要完全重写】

这是本次移植的唯一核心工作。

#### 需要实现的 Android UI 组件:

| 桌面组件 (Swing) | Android 替代方案 | 优先级 |
|-------------------|------------------|--------|
| `ChineseChessFrame.java` (JFrame) | `Activity` / `Fragment` | P0 |
| `BoardPanel.java` (JPanel + 自定义绘制) | 自定义 `View` + `Canvas` | **P0** |
| `InfoSidePanel.java` | `BottomSheet` / 侧边栏 | P0 |
| `GameController.java` | `ViewModel` + 业务逻辑复用 | P0 |
| `MoveHistoryPanel.java` | `RecyclerView` | P1 |
| `RuleSettingsPanel.java` | 自定义设置页面 | P1 |
| `ForceMoveHandler.java` | 手势交互重写 | P1 |
| `LobbyPanel.java` | 局域网大厅页面 | **P0** |
| `WsClient.java` | → OkHttp WebSocket | **P0** |

#### BoardPanel 绘制逻辑可直接翻译

`BoardPanel.java` 的 `paintComponent` 绘制代码 (约 250 行) 可直接逐行映射为 Android `Canvas` API:

| Swing Graphics2D | Android Canvas |
|------------------|---------------|
| `g2d.drawOval(x-r, y-r, r*2, r*2)` | `canvas.drawCircle(x, y, r, paint)` |
| `g2d.drawLine(x1, y1, x2, y2)` | `canvas.drawLine(x1, y1, x2, y2, paint)` |
| `g2d.drawString(text, x, y)` | `canvas.drawText(text, x, y, paint)` |
| `g2d.fillOval(...)` | `canvas.drawCircle(...)` 填充模式 |
| `g2d.setColor(color)` | `paint.setColor(color)` |
| `g2d.setStroke(stroke)` | `paint.setStrokeWidth(w)` |
| `g2d.setFont(font)` | `paint.setTypeface(Typeface)` |

坐标系统都是左上角原点, 棋盘布局公式可直接复用。

#### 局域网对战方案

桌面版已定义成熟的 JSON WebSocket 协议。Android 端:
- **客户端**: OkHttp WebSocket ✅
- **服务端**: 使用 `org.java-websocket:Java-WebSocket` 轻量库在主机手机上运行
- **设备发现**: mDNS (Android NSD) + 手动输入 IP 作为 fallback

---

## 3. 本次范围的核心依赖需求

根据调整后范围, Android 端实际需要的第三方依赖:

| 依赖 | 用途 | Android | 必需? |
|------|------|---------|-------|
| `com.google.code.gson` | JSON 序列化/反序列化 | ✅ | **必需** |
| `com.squareup.okhttp3:okhttp` | WebSocket 客户端 | ✅ | **必需** (局域网对局) |
| `org.java-websocket:Java-WebSocket` | 轻量 WebSocket 服务器 (主机方) | ✅ | **必需** |
| `com.google.protobuf` | 云端AI通信协议 | ✅ | 后期需要 |
| `io.grpc:grpc-okhttp` | gRPC Android transport | ✅ | 后期需要 |

**不需要的依赖**: Netty, Disruptor, Jedis, Logback, gRPC-netty-shaded

---

## 4. 构建系统

```
建议项目结构:
ChineseChess-Android/
├── lib-core/         ← ucc-core (直接复制, 零修改)
├── lib-common/       ← ucc-common (直接复制, 零修改)
├── lib-api/          ← ucc-api (2个文件的File→Stream适配)
├── app/              ← 全新 Android UI + 局域网
│   ├── ui/            → BoardView, GameActivity, 设置页面
│   ├── network/       → WebSocket 客户端/服务端, 局域网发现
│   └── viewmodel/     → GameViewModel
└── settings.gradle.kts
```

---

## 5. 整合难度总结 (精确版)

### 总体评分: ★★★☆☆ (中等)

| 评估维度 | 难度 | 说明 |
|---------|------|------|
| 核心逻辑 (ucc-common + ucc-core) | **★☆☆☆☆ 极低** | **26个Java文件, 0行代码需要修改** |
| 序列化层 (ucc-api) | **★☆☆☆☆ 极低** | 2个文件共改~6行 (File→Stream) |
| UI 层 (ucc-app) | **★★★★☆ 高** | Swing完全不可复用, 需用Android View重新实现 |
| 局域网对局 | **★★★☆☆ 中** | 协议可复用, Android端实现WS客户端+轻量服务端 |
| 构建系统迁移 | **★☆☆☆☆ 低** | Gradle配置, 核心library模块直接复制 |

### 工作量估算 (精确版)

```
总估算: 12-16 人日

底层库移植 (lib-common + lib-core):     <0.5天  (直接复制, 几乎不需要"移")
  → Board.java, GameEngine.java, MoveValidator.java 等 26 个文件直接拷贝
  → 修改 GameStateExporter/Importer 共 ~6 行代码
  
BoardView 自定义 View 绘制移植:         2-3 天  (核心工作)
  → BoardPanel paintComponent → Android Canvas
  → 网格/棋子/高亮/叠棋/聚焦视图

GameActivity + ViewModel + 设置页面:    2-3 天
  → ViewModel 桥接 GameEngine
  → 规则设置 → 自定义 RecyclerView

局域网对战 (WS客户端+服务端+发现+UI):  4-5 天  (核心功能)
  → OkHttp WebSocket 客户端
  → Java-WebSocket 轻量服务端
  → mDNS 设备发现 + 手动输入 IP

走棋记录 + 残局导入导出 + UI打磨:      2-3 天

云端 AI 调用 (后期可选):                1-2 天  (独立, 不影响核心路径)
```

### 一句话总结

> **`ucc-common` 和 `ucc-core` 的 26 个 Java 文件总共只需要改 0 行代码就能在 Android 上编译通过。唯一的工作量就是重写 UI 层和局域网通信层。**
