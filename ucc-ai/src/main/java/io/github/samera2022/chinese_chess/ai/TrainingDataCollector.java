package io.github.samera2022.chinese_chess.ai;

import io.github.samera2022.chinese_chess.common.model.BoardState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 训练数据收集器 —— 在自我对弈过程中收集训练样本。
 *
 * <p>每条训练样本（{@link TrainingSample}）包含：
 * <ul>
 *   <li>{@code boardState}：局面快照</li>
 *   <li>{@code ruleVector}：规则向量（28 位：27 布尔 + 1 连续值）</li>
 *   <li>{@code policyTarget}：MCTS 搜索得出的策略分布（长度 = rows × cols）</li>
 *   <li>{@code valueTarget}：最终胜负结果（1=红胜, -1=黑胜, 0=平局）</li>
 * </ul>
 *
 * <p>用法示例：
 * <pre>{@code
 * TrainingDataCollector collector = new TrainingDataCollector();
 * collector.addSample(boardState, ruleVec, policy, 1.0f);
 * collector.addSample(boardState, ruleVec, policy, -1.0f);
 * String json = collector.toJson();  // 序列化为 JSON 供 PyBridge 传输
 * collector.clear();
 * }</pre>
 *
 * @see PyBridge
 * @see BoardState
 */
public class TrainingDataCollector {

    /** 内部样本列表（线程安全方面：当前设计为单线程自我对弈场景） */
    private final List<TrainingSample> samples = new ArrayList<>();

    /** Gson 实例，用于 JSON 序列化 */
    private static final Gson GSON = new GsonBuilder().create();

    // ══════════════════════════════════════════════
    // 内部类：TrainingSample
    // ══════════════════════════════════════════════

    /**
     * 单条训练样本。
     *
     * <p>所有字段均为 {@code public final}，供 Gson 直接序列化。
     * 字段命名采用 JSON 友好的 camelCase 风格。</p>
     */
    public static class TrainingSample {
        /** 局面快照 */
        public final BoardState board;

        /** 规则向量（28 位：前 27 位布尔规则开关，第 28 位连续值如 max_stacking_count/16） */
        public final float[] rules;

        /** MCTS 搜索得出的策略分布（长度 = rows × cols，按行优先排列） */
        public final float[] policy;

        /** 最终胜负结果：1 = 红胜，-1 = 黑胜，0 = 平局 */
        public final float value;

        /**
         * 构造一条训练样本。
         *
         * @param board  局面快照，不可为 {@code null}
         * @param rules  规则向量（28 位），不可为 {@code null}
         * @param policy MCTS 策略分布（长度 = rows × cols），不可为 {@code null}
         * @param value  胜负结果（1 = 红胜，-1 = 黑胜，0 = 平局）
         */
        public TrainingSample(BoardState board, float[] rules, float[] policy, float value) {
            this.board = board;
            this.rules = rules;
            this.policy = policy;
            this.value = value;
        }
    }

    // ══════════════════════════════════════════════
    // 公共方法
    // ══════════════════════════════════════════════

    /**
     * 添加一条训练样本。
     *
     * @param state      局面快照
     * @param ruleVector 规则向量（28 位：27 布尔 + 1 连续值）
     * @param policy     MCTS 策略分布
     * @param value      胜负结果（1 = 红胜，-1 = 黑胜，0 = 平局）
     */
    public void addSample(BoardState state, float[] ruleVector, float[] policy, float value) {
        samples.add(new TrainingSample(state, ruleVector, policy, value));
    }

    /**
     * 返回所有已收集的训练样本（不可变视图）。
     *
     * @return 训练样本列表的不可变副本
     */
    public List<TrainingSample> getSamples() {
        return Collections.unmodifiableList(new ArrayList<>(samples));
    }

    /**
     * 返回当前收集的样本数量。
     *
     * @return 样本数量
     */
    public int size() {
        return samples.size();
    }

    /**
     * 清空所有已收集的样本。
     */
    public void clear() {
        samples.clear();
    }

    /**
     * 将所有训练样本序列化为 JSON 字符串。
     *
     * <p>输出格式为 JSON 数组，每条样本的结构为：
     * <pre>{@code
     * [
     *   {
     *     "board": { BoardState 的 JSON 表示 },
     *     "rules": [0.0, 1.0, ...],
     *     "policy": [0.01, 0.02, ...],
     *     "value": 1.0
     *   },
     *   ...
     * ]
     * }</pre>
     *
     * <p>该 JSON 字符串可直接通过 {@link PyBridge} 传输给 Python 端进行训练。</p>
     *
     * @return 所有样本的 JSON 数组字符串
     */
    public String toJson() {
        return GSON.toJson(samples);
    }
}
