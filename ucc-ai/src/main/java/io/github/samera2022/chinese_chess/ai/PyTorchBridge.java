package io.github.samera2022.chinese_chess.ai;

/**
 * PyTorch 模型桥接类 —— Java 端加载 TorchScript 模型并提供神经网络推理。
 *
 * <h3>职责</h3>
 * <p>加载训练好的 TorchScript 模型 (.pt 文件)，为 MCTS 搜索提供神经网络评估
 * （局面 → 策略概率分布 + 胜率值）。</p>
 *
 * <h3>Phase 1 基线实现</h3>
 * <p>由于纯 Java 无法直接加载 PyTorch 模型，当前 Phase 1 提供 fallback 机制：</p>
 * <ul>
 *   <li>不实际加载 PyTorch 模型</li>
 *   <li>策略返回均匀分布（每格 {@code 1.0f / (rows * cols)}），价值返回 0（中立）</li>
 *   <li>均匀策略概率可供 MCTSAgent 在"无模型"模式下使用，
 *       与纯随机 Rollout 区分——MCTS 树搜索本身会利用 UCB 探索机制逐步收敛</li>
 * </ul>
 *
 * <h3>设计原则：可替换</h3>
 * <p>本类设计为可替换组件。后续 Phase 通过修改 {@link #loadModel(String)} 和
 * {@link #evaluate(float[][][], float[])} 的内部逻辑接入真实 TorchScript，
 * 无需修改调用方（如 {@link RuleAwareAI}、{@link MCTSAgent}）的代码。</p>
 *
 * <h3>Phase 2+ 扩展方向</h3>
 * <ul>
 *   <li><b>JPype</b>: 在 JVM 中启动 Python 解释器，直接调用 PyTorch 推理</li>
 *   <li><b>JNI + LibTorch</b>: 通过 C++ LibTorch 加载模型，性能最优</li>
 *   <li><b>gRPC</b>: 远程调用 Python 推理服务，适合分布式部署</li>
 * </ul>
 *
 * <h3>输入/输出规范</h3>
 * <table>
 *   <caption>神经网络输入</caption>
 *   <tr><th>参数</th><th>形状</th><th>说明</th></tr>
 *   <tr><td>boardTensor</td><td>{@code [14][rows][cols]}</td>
 *       <td>14 通道棋盘表示（7 种棋子 × 2 方），每通道为二值掩码</td></tr>
 *   <tr><td>ruleVector</td><td>{@code [28]}</td>
 *       <td>规则向量：前 27 位为布尔规则开关，第 28 位为连续值
 *           （如 max_stacking_count 归一化到 [0,1]）</td></tr>
 * </table>
 * <table>
 *   <caption>神经网络输出</caption>
 *   <tr><th>索引</th><th>含义</th><th>范围</th></tr>
 *   <tr><td>0–(N-1)</td><td>策略概率分布（N = rows × cols，每格一个概率）</td><td>[0, 1]，和为 1</td></tr>
 *   <tr><td>N</td><td>局面价值（胜率）</td><td>[-1, 1]，正值表示当前方优势</td></tr>
 * </table>
 *
 * @see MCTSAgent
 * @see RuleAwareAI
 */
public class PyTorchBridge {

    /** 策略输出与价值输出之间的分隔索引偏移 */
    private static final int VALUE_OFFSET = 1;

    /** 棋盘张量期望通道数（7 种棋子 × 2 方） */
    private static final int EXPECTED_CHANNELS = 14;

    /** 规则向量期望长度（27 布尔 + 1 连续值） */
    private static final int EXPECTED_RULE_VECTOR_LENGTH = 28;

    /** 模型文件路径（Phase 1 仅保存引用，不实际加载） */
    private final String modelPath;

    /** 模型是否已成功加载 */
    private boolean modelLoaded;

    // ══════════════════════════════════════════════
    // 构造函数
    // ══════════════════════════════════════════════

    /**
     * 构造 PyTorchBridge，记录模型路径。
     *
     * <p>Phase 1 不实际加载模型，仅保存路径引用。
     * 后续可调用 {@link #loadModel(String)} 重新加载。</p>
     *
     * @param modelPath TorchScript 模型文件路径（.pt 文件），可为 {@code null}
     */
    public PyTorchBridge(String modelPath) {
        this.modelPath = modelPath;
        this.modelLoaded = false;
    }

    /**
     * 无参构造函数。
     *
     * <p>模型路径为空，后续需调用 {@link #loadModel(String)} 加载模型。</p>
     */
    public PyTorchBridge() {
        this(null);
    }

    // ══════════════════════════════════════════════
    // 静态工厂方法
    // ══════════════════════════════════════════════

    /**
     * 创建 PyTorchBridge 实例并尝试加载指定路径的模型。
     *
     * <p>Phase 1 不实际加载模型（{@link #isModelLoaded()} 返回 {@code false}）。
     * Phase 2+ 集成 JPype/JNI 后，此方法将真正加载 TorchScript 模型。</p>
     *
     * @param modelPath TorchScript 模型文件路径（.pt 文件）
     * @return PyTorchBridge 实例，已（尝试）加载模型
     */
    public static PyTorchBridge create(String modelPath) {
        PyTorchBridge bridge = new PyTorchBridge(modelPath);
        bridge.loadModel(modelPath);
        return bridge;
    }

    /**
     * 创建一个 Phase 1 基线（无模型 fallback）的 PyTorchBridge 实例。
     *
     * <p>返回的实例始终使用均匀策略分布和零价值评估，
     * 适用于"无模型"模式下的 MCTS 搜索。</p>
     *
     * @return 无模型的 PyTorchBridge 实例
     */
    public static PyTorchBridge createFallback() {
        return new PyTorchBridge(null);
    }

    // ══════════════════════════════════════════════
    // 核心推理方法
    // ══════════════════════════════════════════════

    /**
     * 对给定局面进行神经网络评估。
     *
     * <h4>输入</h4>
     * <ul>
     *   <li>{@code boardTensor}: 形状为 {@code [14][rows][cols]} 的三维数组，
     *       14 通道依次为：红方将/士/象/马/车/炮/兵、黑方将/士/象/马/车/炮/卒。
     *       每通道为二值掩码（1.0 = 该位置有该棋子，0.0 = 无）。</li>
     *   <li>{@code ruleVector}: 长度为 23 的规则向量。
     *       前 22 位：布尔规则开关（1.0 = 开启，0.0 = 关闭）；
     *       第 23 位：连续值（如 max_stacking_count / 16f 归一化到 [0,1]）。</li>
     * </ul>
     *
     * <h4>输出</h4>
     * <p>长度为 {@code rows * cols + 1} 的 {@code float[]}：</p>
     * <ul>
     *   <li>索引 0 到 {@code rows * cols - 1}：策略概率分布（每格一个概率，按行优先排列，
     *       所有值之和为 1）</li>
     *   <li>索引 {@code rows * cols}：局面价值，范围 [-1, 1]，
     *       正值表示当前回合方优势</li>
     * </ul>
     *
     * <h4>Phase 1 行为</h4>
     * <p>策略返回均匀分布（每格概率 = {@code 1.0f / (rows * cols)}），价值返回 0（中立）。
     * 均匀分布提供平坦先验，MCTS 树搜索利用 UCB 公式：
     * <pre>UCB = exploitation + sqrt(2 * ln(parentVisits) / childVisits)</pre>
     * 在平坦先验下仍能通过探索项逐步收敛到更优策略。</p>
     *
     * @param boardTensor 14 通道棋盘张量，形状 {@code [14][rows][cols]}
     * @param ruleVector  23 位规则向量（22 布尔 + 1 连续值）
     * @return 长度为 {@code rows * cols + 1} 的 float 数组，
     *         前 N 位策略概率分布，第 N+1 位价值
     * @throws IllegalArgumentException 如果输入为 {@code null} 或维度不合法
     */
    public float[] evaluate(float[][][] boardTensor, float[] ruleVector) {
        // ── 输入校验 ──
        validateInputs(boardTensor, ruleVector);

        int rows = boardTensor[0].length;
        int cols = boardTensor[0][0].length;
        int policySize = rows * cols;
        float[] output = new float[policySize + VALUE_OFFSET];

        // ── Phase 1: 策略 → 均匀分布 ──
        float uniformProb = 1.0f / policySize;
        for (int i = 0; i < policySize; i++) {
            output[i] = uniformProb;
        }

        // ── Phase 1: 价值 → 0（中立） ──
        output[policySize] = 0.0f;

        return output;
    }

    // ══════════════════════════════════════════════
    // 模型加载与释放
    // ══════════════════════════════════════════════

    /**
     * 加载 TorchScript 模型。
     *
     * <p><b>Phase 1</b>: 不实际加载，仅设置 {@code modelLoaded = false}。</p>
     *
     * <p><b>Phase 2+ 实现示例（JPype）</b>:</p>
     * <pre>{@code
     * // 在 JVM 中启动 Python 解释器并加载 TorchScript 模型
     * PyModule torch = PyModule.importModule("torch");
     * this.torchModel = torch.call("jit.load", modelPath);
     * this.modelLoaded = true;
     * }</pre>
     *
     * <p><b>Phase 2+ 实现示例（JNI + LibTorch）</b>:</p>
     * <pre>{@code
     * // 通过 JNI 调用 native 方法加载 LibTorch 模型
     * this.nativeHandle = nativeLoadModel(modelPath);
     * this.modelLoaded = (this.nativeHandle != 0);
     * }</pre>
     *
     * @param modelPath TorchScript 模型文件路径（.pt 文件）
     */
    public void loadModel(String modelPath) {
        // Phase 1: 不实际加载模型
        // Phase 2+: 在此处实现真实加载逻辑（JPype / JNI / gRPC）
        this.modelLoaded = false;
    }

    /**
     * 返回模型是否已成功加载。
     *
     * <p>Phase 1 始终返回 {@code false}。
     * Phase 2+ 在真实加载成功后返回 {@code true}。</p>
     *
     * @return 模型已成功加载返回 {@code true}，否则返回 {@code false}
     */
    public boolean isModelLoaded() {
        return modelLoaded;
    }

    /**
     * 释放模型资源。
     *
     * <p>Phase 1 无实际资源需释放。
     * Phase 2+ 实现中应关闭 JNI 句柄、释放 GPU 显存、关闭 gRPC 连接等。</p>
     *
     * <p>调用 {@code close()} 后，{@link #isModelLoaded()} 返回 {@code false}，
     * 后续 {@link #evaluate(float[][][], float[])} 调用将回退到 Phase 1 基线行为。</p>
     */
    public void close() {
        // Phase 1: 无资源需释放
        // Phase 2+ 实现示例:
        //   if (torchModel != null) {
        //       // JPype: torchModel 由 GC 管理，显式置 null 即可
        //       torchModel = null;
        //   }
        //   if (nativeHandle != 0) {
        //       nativeReleaseModel(nativeHandle);
        //       nativeHandle = 0;
        //   }
        this.modelLoaded = false;
    }

    // ══════════════════════════════════════════════
    // 查询方法
    // ══════════════════════════════════════════════

    /**
     * 获取模型文件路径。
     *
     * @return 模型文件路径，可能为 {@code null}（无参构造或 fallback 模式）
     */
    public String getModelPath() {
        return modelPath;
    }

    // ══════════════════════════════════════════════
    // 内部工具方法
    // ══════════════════════════════════════════════

    /**
     * 验证 {@code boardTensor} 和 {@code ruleVector} 的维度合法性。
     *
     * @param boardTensor 棋盘张量
     * @param ruleVector  规则向量
     * @throws IllegalArgumentException 如果任一参数为 {@code null} 或维度不合法
     */
    private void validateInputs(float[][][] boardTensor, float[] ruleVector) {
        if (boardTensor == null) {
            throw new IllegalArgumentException("boardTensor must not be null");
        }
        if (boardTensor.length == 0) {
            throw new IllegalArgumentException("boardTensor must have at least 1 channel");
        }
        if (boardTensor.length != EXPECTED_CHANNELS) {
            throw new IllegalArgumentException(
                "boardTensor must have exactly " + EXPECTED_CHANNELS + " channels"
                + ", got " + boardTensor.length);
        }
        // 确保所有通道维度一致
        int rows = boardTensor[0].length;
        int cols = boardTensor[0][0].length;
        if (rows == 0) {
            throw new IllegalArgumentException("boardTensor must have at least 1 row");
        }
        if (cols == 0) {
            throw new IllegalArgumentException("boardTensor must have at least 1 column");
        }
        for (int c = 1; c < boardTensor.length; c++) {
            if (boardTensor[c] == null) {
                throw new IllegalArgumentException(
                    "boardTensor channel " + c + " must not be null");
            }
            if (boardTensor[c].length != rows) {
                throw new IllegalArgumentException(
                    "boardTensor channel " + c + " row count " + boardTensor[c].length
                    + " does not match channel 0 row count " + rows);
            }
            for (int r = 0; r < rows; r++) {
                if (boardTensor[c][r] == null) {
                    throw new IllegalArgumentException(
                        "boardTensor[" + c + "][" + r + "] must not be null");
                }
                if (boardTensor[c][r].length != cols) {
                    throw new IllegalArgumentException(
                        "boardTensor[" + c + "][" + r + "] column count "
                        + boardTensor[c][r].length
                        + " does not match channel 0 column count " + cols);
                }
            }
        }

        if (ruleVector == null) {
            throw new IllegalArgumentException("ruleVector must not be null");
        }
        if (ruleVector.length != EXPECTED_RULE_VECTOR_LENGTH) {
            throw new IllegalArgumentException(
                "ruleVector length must be " + EXPECTED_RULE_VECTOR_LENGTH
                + ", got " + ruleVector.length);
        }
    }
}
