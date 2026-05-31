# TrainingDataCollector 代码审计报告

## 审计日期
2026-05-31

## 需求对照逐项审计

### 1. 文件位置 ✅ 通过
| 需求 | 实际 |
|------|------|
| `ucc-ai/src/main/java/io/github/samera2022/chinese_chess/ai/TrainingDataCollector.java` | 完全一致 |

---

### 2. 包声明 ✅ 通过
| 需求 | 实际 |
|------|------|
| `io.github.samera2022.chinese_chess.ai` | `package io.github.samera2022.chinese_chess.ai;` |

---

### 3. 类名 ✅ 通过
| 需求 | 实际 |
|------|------|
| `TrainingDataCollector` | `public class TrainingDataCollector` |

---

### 4. 内部类 TrainingSample ⚠️ 功能通过，术语偏差
| 需求字段 | 实际代码 | 判定 |
|----------|---------|------|
| `BoardState board` | `public final BoardState board;` | ✅ |
| `float[] rules` | `public final float[] rules;` | ✅ |
| `float[] policy` | `public final float[] policy;` | ✅ |
| `float value` | `public final float value;` | ✅ |

**说明**: 所有四个字段均为 `public final`，类型完全匹配。

**术语偏差**: 需求描述为"内部类"，Java 严格术语中 `public static class` 属于"静态嵌套类 (static nested class)"，而非"内部类 (inner class)"。非 static 的内部类会隐式持有外部类引用，而 `static` 版本更轻量且适合此场景（样本不需要访问 Collector 实例）。此处的 `static` 修饰符是**更好的设计选择**，不视为缺陷。

---

### 5. 方法签名 ✅ 全部通过

| 需求方法 | 实际签名 | 判定 |
|----------|---------|------|
| `addSample(BoardState state, float[] ruleVector, float[] policy, float value)` | `public void addSample(BoardState state, float[] ruleVector, float[] policy, float value)` | ✅ |
| `getSamples()` | `public List<TrainingSample> getSamples()` | ✅ |
| `size()` | `public int size()` | ✅ |
| `clear()` | `public void clear()` | ✅ |
| `toJson()` | `public String toJson()` | ✅ |

---

### 6. toJson() 序列化 ✅ 通过（附深度分析）

**需求**: 使用 Gson 将所有样本序列化为 JSON 数组，每条格式：
```json
{"board":..., "rules":[...], "policy":[...], "value": float}
```

**实际代码**:
```java
private static final Gson GSON = new GsonBuilder().create();
public String toJson() {
    return GSON.toJson(samples);
}
```

**Gson 默认序列化链路分析**:

`List<TrainingSample>` → JSON 数组 `[...]`。每个 `TrainingSample` 对象的字段：

| 字段 | 类型 | Gson 序列化结果 | 匹配需求？ |
|------|------|----------------|-----------|
| `board` | `BoardState` | JSON Object（含 `rows`/`cols`/`entries`/`redTurn`） | ✅ |
| `rules` | `float[]` | JSON 数字数组 `[0.1, 0.2, ...]` | ✅ |
| `policy` | `float[]` | JSON 数字数组 `[0.05, 0.3, ...]` | ✅ |
| `value` | `float` | JSON 数字 `0.5` | ✅ |

**`BoardState` 序列化深入验证**：

[`BoardState`](ucc-common/src/main/java/io/github/samera2022/chinese_chess/common/model/BoardState.java:5) 的字段均为 `private final`，Gson 通过反射（`Field.setAccessible(true)`）可正常访问所有字段，无论访问修饰符。其内部 `StackEntry` 类 (`public final int row, col` + `List<Piece.Type>`) 同样可被 Gson 正确序列化。`Piece.Type` 枚举默认序列化为枚举常量名（如 `"RED_KING"`）。

**预期输出示例**:
```json
[{"board":{"rows":10,"cols":9,"entries":[{"row":0,"col":0,"pieceTypes":["RED_CHARIOT","RED_HORSE"]}],"redTurn":true},"rules":[0.0,1.0,0.0],"policy":[0.1,0.05,0.0],"value":0.85}]
```

**判定**: ✅ `toJson()` 完全满足需求——使用 Gson 将所有样本序列化为 JSON 数组，每个元素包含 `board`、`rules`、`policy`、`value` 四个字段。

---

### 7. Gson 库引用 ✅ 通过
```java
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
```
使用 `com.google.gson.Gson`，符合需求。

---

## 潜在问题分析（非需求缺陷，但值得关注）

### ⚠️ P1: `float[]` 数组未做防御性拷贝

**问题**: `addSample()` 直接存储外部传入的 `float[] ruleVector` 和 `float[] policy` 引用，未拷贝数组内容。

```java
// 当前代码
public void addSample(BoardState state, float[] ruleVector, float[] policy, float value) {
    samples.add(new TrainingSample(state, ruleVector, policy, value));
}
```

**风险**: 调用方在 `addSample()` 之后修改数组内容，会污染已存储的训练数据。这对于训练数据的完整性是严重隐患。

**建议修复**:
```java
public void addSample(BoardState state, float[] ruleVector, float[] policy, float value) {
    samples.add(new TrainingSample(
        state,
        ruleVector != null ? ruleVector.clone() : null,
        policy != null ? policy.clone() : null,
        value
    ));
}
```

### ⚠️ P2: `getSamples()` 返回的 TrainingSample 中 `float[]` 数组可被修改

**问题**: `getSamples()` 做了 List 的防御性拷贝（`new ArrayList<>(samples)`），但 `TrainingSample` 内部的 `float[] rules` 和 `float[] policy` 是 `public final` 的，返回后调用方可直接修改数组元素。

**风险**: 破坏数据不可变性，违反数据收集器的设计意图。

**建议**: 在 `TrainingSample` 构造函数中对 `rules` 和 `policy` 做 `.clone()`，或将字段改为 `private` 并提供返回副本的 getter。

### ⚠️ P3: 线程安全

`samples` 使用普通的 `ArrayList`，所有方法均无同步。若在多线程环境下（如并行自我对弈），需要添加 `synchronized` 或使用 `CopyOnWriteArrayList`。鉴于需求未提并发场景，此条为低优先级提醒。

### 💡 P4: Gson 实例为 static final

`private static final Gson GSON = new GsonBuilder().create();` — Gson 实例是线程安全的，`static final` 单例是正确的做法。✅

---

## 总结

| 需求项 | 结果 |
|--------|------|
| 1. 文件位置 | ✅ 通过 |
| 2. 包名 | ✅ 通过 |
| 3. 类名 | ✅ 通过 |
| 4. 内部类及字段 | ✅ 通过（`static` 为更优设计） |
| 5. 5 个方法 | ✅ 全部实现，签名正确 |
| 6. toJson() 序列化 | ✅ 通过（Gson 可完整序列化 BoardState 及所有嵌套类型） |
| 7. Gson 库 | ✅ 正确引用 |

**最终判定**: 代码**完全满足**所有 7 项功能需求。存在 2 个数据完整性问题（`float[]` 未防御性拷贝），属于健壮性/安全性优化建议，不影响需求验收。
