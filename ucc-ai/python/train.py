"""
训练流水线与课程学习策略 — 强化学习 AI 引擎。

实现 AlphaZero 风格强化学习训练循环，包含：
  - ReplayBuffer 经验回放缓冲区
  - 课程学习（Curriculum Learning）三阶段策略
  - 双模型对弈评估
  - 模型保存与 TorchScript 导出

参考资料:
  - report/rl/强化学习接入方案.md §7.1-7.3
  - ucc-ai/python/model.py: MiniResNet 神经网络
  - ucc-ai/python/selfplay.py: 自我对弈与 MCTS 搜索
"""

import json
import os
import random
import sys
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import torch
import torch.nn as nn
from torch.optim import Adam, AdamW

from model import MiniResNet
from selfplay import (
    # ── 常量 ──
    NUM_CHANNELS,
    RULE_BOOL_DIM,
    RULE_FULL_DIM,
    EXPANDED_ROWS,
    STANDARD_ROWS,
    BOARD_COLS,
    DEVICE,
    # ── 棋盘转换 ──
    board_to_tensor,
    rule_vector_to_numpy,
    get_rows_for_rules,
    # ── 规则向量生成 ──
    make_all_false_rule_vector,
    make_random_rule_vector,
    make_random_rule_vector_with_few_enabled,
    # ── MCTS & 自我对弈 ──
    mcts_search,
    selfplay_game,
    # ── 模拟数据（mock 训练用） ──
    _generate_mock_board_state,
    _generate_mock_legal_moves,
    _apply_mock_move,
)


# ═══════════════════════════════════════════════════════════════════════════
# ReplayBuffer — 经验回放缓冲区
# ═══════════════════════════════════════════════════════════════════════════

class ReplayBuffer:
    """固定容量的循环经验回放缓冲区。

    内部以 list 存储 (board_tensor, rule_vec, policy, value) 元组。
    写入指针循环覆盖：当缓冲区满时，最旧的样本被新样本替换。

    Attributes:
        max_size: 缓冲区最大容量。
        buffer: 内部 list，存储样本元组。
        ptr: 当前写入位置（循环覆盖指针）。
    """

    def __init__(self, max_size: int = 100000):
        """
        Args:
            max_size: 缓冲区最大容量。超出后循环覆盖最旧样本。
        """
        self.max_size = max_size
        self.buffer: List[Tuple[np.ndarray, np.ndarray, np.ndarray, float]] = []
        self.ptr = 0

    def push(
        self,
        board: Dict[str, Any],
        rules: List[float],
        policy: np.ndarray,
        value: float,
        rows: int = 10,
        cols: int = 9,
    ) -> None:
        """将训练样本添加到缓冲区。

        Args:
            board: BoardState JSON 字典（含 rows, cols, entries, redTurn）。
            rules: 23 维规则向量 list（22 布尔 + 1 连续值）。
            policy: MCTS 访问计数分布，shape [rows * cols]。
            value: 局面价值（从当前玩家视角，∈ [-1, 1]）。
            rows: 棋盘行数。
            cols: 棋盘列数。
        """
        board_tensor = board_to_tensor(board, rows, cols)          # [14, H, W]
        rule_vec = rule_vector_to_numpy(rules)                      # [23]
        policy_arr = np.asarray(policy, dtype=np.float32)           # [cells]

        sample = (board_tensor, rule_vec, policy_arr, float(value))

        if len(self.buffer) < self.max_size:
            self.buffer.append(sample)
        else:
            self.buffer[self.ptr] = sample

        self.ptr = (self.ptr + 1) % self.max_size

    def sample(
        self, batch_size: int
    ) -> Tuple[torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor]:
        """随机采样一个 batch。

        Args:
            batch_size: 采样数量。若 batch_size > len(buffer)，则返回全部样本。

        Returns:
            (board_batch, rule_batch, policy_batch, value_batch):
              - board_batch:  [B, 14, H, W]  float32
              - rule_batch:   [B, 23]         float32
              - policy_batch: [B, H*W]        float32
              - value_batch:  [B, 1]          float32
        """
        n = min(batch_size, len(self.buffer))
        samples = random.sample(self.buffer, n)

        boards = np.stack([s[0] for s in samples])      # [B, 14, H, W]
        rules = np.stack([s[1] for s in samples])        # [B, 23]
        policies = np.stack([s[2] for s in samples])     # [B, H*W]
        values = np.array([[s[3]] for s in samples], dtype=np.float32)  # [B, 1]

        return (
            torch.from_numpy(boards).to(DEVICE),
            torch.from_numpy(rules).to(DEVICE),
            torch.from_numpy(policies).to(DEVICE),
            torch.from_numpy(values).to(DEVICE),
        )

    def __len__(self) -> int:
        """返回当前缓冲区中的样本数量。"""
        return len(self.buffer)

    def save(self, path: str) -> None:
        """将缓冲区保存为 JSON 文件。

        numpy 数组会被转为嵌套列表以便 JSON 序列化。
        注意：大缓冲区（>100k 样本）可能产生数百 MB 的 JSON 文件。

        Args:
            path: 输出 JSON 文件路径。
        """
        data: List[Dict[str, Any]] = []
        for board_tensor, rule_vec, policy, value in self.buffer:
            data.append({
                "board_tensor": board_tensor.tolist(),
                "rule_vec": rule_vec.tolist(),
                "policy": policy.tolist(),
                "value": value,
            })

        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)

    def load(self, path: str) -> None:
        """从 JSON 文件加载缓冲区。

        会清空当前缓冲区内容后加载。

        Args:
            path: JSON 文件路径。
        """
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)

        self.buffer.clear()
        for item in data:
            board_tensor = np.array(item["board_tensor"], dtype=np.float32)
            rule_vec = np.array(item["rule_vec"], dtype=np.float32)
            policy = np.array(item["policy"], dtype=np.float32)
            value = float(item["value"])
            self.buffer.append((board_tensor, rule_vec, policy, value))

        self.ptr = len(self.buffer) % self.max_size if self.max_size > 0 else 0


# ═══════════════════════════════════════════════════════════════════════════
# 训练步
# ═══════════════════════════════════════════════════════════════════════════

def train_step(
    model: MiniResNet,
    batch: Tuple[torch.Tensor, torch.Tensor, torch.Tensor, torch.Tensor],
    optimizer: torch.optim.Optimizer,
    grad_clip: float = 1.0,
) -> Tuple[float, float, float]:
    """执行一次训练步（单个 batch 的前向 + 反向传播）。

    损失函数（AlphaZero 标准）：
      policy_loss  = CrossEntropy(policy_logits, target_policy)  软目标交叉熵
      value_loss   = MSE(value, target_value)
      total_loss   = policy_loss + value_loss

    改进点：
      - 梯度裁剪（grad_clip）防止梯度爆炸
      - 策略损失使用 label smoothing 防止过拟合

    Args:
        model: MiniResNet 模型（训练模式）。
        batch: (board_batch, rule_batch, policy_batch, value_batch) 四元组。
        optimizer: PyTorch 优化器（如 AdamW）。
        grad_clip: 梯度裁剪阈值（L2 范数）。

    Returns:
        (total_loss, policy_loss, value_loss) 均为 Python float。
    """
    board_batch, rule_batch, target_policy, target_value = batch

    model.train()
    optimizer.zero_grad()

    # 前向传播
    policy_logits, value = model(board_batch, rule_batch)

    # 策略损失：交叉熵（软目标 — 用 log_softmax + 点积实现）
    # 添加 label smoothing: 将 5% 的概率质量均匀分配到所有动作
    smoothing = 0.05
    num_actions = target_policy.shape[1]
    smoothed_policy = (1.0 - smoothing) * target_policy + smoothing / num_actions

    log_probs = torch.log_softmax(policy_logits, dim=1)
    policy_loss = -(smoothed_policy * log_probs).sum(dim=1).mean()

    # 价值损失：MSE
    value_loss = nn.functional.mse_loss(
        value.squeeze(-1), target_value.squeeze(-1)
    )

    # 总损失
    total_loss = policy_loss + value_loss

    # 反向传播 + 梯度裁剪
    total_loss.backward()
    torch.nn.utils.clip_grad_norm_(model.parameters(), grad_clip)
    optimizer.step()

    return float(total_loss.item()), float(policy_loss.item()), float(value_loss.item())


# ═══════════════════════════════════════════════════════════════════════════
# 课程学习 — 规则向量生成
# ═══════════════════════════════════════════════════════════════════════════

def _make_curriculum_rule_vector(
    iteration: int,
    num_iterations: int,
) -> List[float]:
    """根据课程学习阶段生成规则向量。

    三阶段策略（来自强化学习接入方案 §7.2）：
      - 第 1 阶段（前 10% 迭代）：仅标准规则（全部 false）。
      - 第 2 阶段（10%-50% 迭代）：扩展规则随机开启 2-8 条
        （跳过前 3 条基础 UI 规则）。
      - 第 3 阶段（50%-100% 迭代）：全规则空间 27 位随机 + 连续堆叠值随机。

    Args:
        iteration: 当前迭代编号（0-based）。
        num_iterations: 总迭代次数。

    Returns:
        长度 RULE_FULL_DIM 的规则向量 list（27 布尔 + 1 连续值）。
    """
    progress = iteration / max(num_iterations, 1)

    if progress < 0.10:
        # 阶段 1: 仅标准规则（全 false）
        return make_all_false_rule_vector()

    elif progress < 0.50:
        # 阶段 2: 扩展规则随机开启 2-8 条
        bool_part = [0.0] * RULE_BOOL_DIM
        # 从索引 3 开始（跳过 allow_undo、show_hints、allow_force_move）
        num_enabled = random.randint(2, 8)
        pool = list(range(3, RULE_BOOL_DIM))
        chosen = random.sample(pool, min(num_enabled, len(pool)))
        for idx in chosen:
            bool_part[idx] = 1.0
        # 阶段 2 不涉及堆叠，max_stacking_count = 0
        return bool_part + [0.0]

    else:
        # 阶段 3: 全规则空间随机
        return make_random_rule_vector()


# ═══════════════════════════════════════════════════════════════════════════
# 双模型对弈评估
# ═══════════════════════════════════════════════════════════════════════════

def _play_eval_game(
    model_red: MiniResNet,
    model_black: MiniResNet,
    rule_vector: List[float],
    max_steps: int = 200,
    rows: int = 10,
    cols: int = 9,
    use_mock: bool = False,
    num_simulations: int = 200,
    c_puct: float = 2.0,
    temperature: float = 0.1,
) -> float:
    """运行一局双模型对弈（model_red 执红 vs model_black 执黑）。

    每步根据当前回合方选择对应的模型进行 MCTS 搜索。
    低温度（默认 0.1）使走法接近贪心，用于评估模型实力。

    Args:
        model_red: 执红方的模型。
        model_black: 执黑方的模型。
        rule_vector: 23 维规则向量。
        max_steps: 最大走子步数（防止无限循环）。
        rows: 棋盘行数。
        cols: 棋盘列数。
        use_mock: False 时通过 Java PyBridge 引擎获取真实局面数据。
        num_simulations: 每步 MCTS 模拟次数。
        c_puct: UCB 探索系数。
        temperature: 策略温度（评估时建议低温度如 0.1）。

    Returns:
        1.0  = 红胜（model_red 胜）
        -1.0 = 黑胜（model_black 胜）
        0.0  = 平局（达到最大步数）
    """
    cells = rows * cols

    # 创建初始棋盘
    if use_mock:
        board_state = _generate_mock_board_state(rows, cols)
    else:
        # 实际环境通过 PyBridge 创建
        from selfplay import query_java_new_game
        result = query_java_new_game(rule_vector, rows=rows)
        if "error" in result:
            return 0.0
        board_state = result

    for _step in range(max_steps):
        is_red_turn = board_state.get("redTurn", True)
        current_model = model_red if is_red_turn else model_black

        # MCTS 搜索
        action_probs, _training_target = mcts_search(
            model=current_model,
            board_state=board_state,
            rule_vector=rule_vector,
            num_simulations=num_simulations,
            c_puct=c_puct,
            temperature=temperature,
            rows=rows,
            cols=cols,
            use_mock=use_mock,
        )

        # 无合法着法 → 当前回合方被将死/困毙
        if action_probs.sum() < 1e-12:
            return -1.0 if is_red_turn else 1.0

        # 采样着法
        move_idx = int(np.random.choice(cells, p=action_probs))

        # 匹配着法
        if use_mock:
            legal_moves = _generate_mock_legal_moves(board_state, rows, cols)
        else:
            from selfplay import query_java_legal_moves
            legal_moves = query_java_legal_moves(board_state, rule_vector)

        chosen_move: Optional[Dict[str, int]] = None
        for move in legal_moves:
            idx = move["toRow"] * cols + move["toCol"]
            if idx == move_idx:
                chosen_move = move
                break

        if chosen_move is None:
            if legal_moves:
                chosen_move = random.choice(legal_moves)
            else:
                return -1.0 if is_red_turn else 1.0

        # 执行着法
        if use_mock:
            board_state = _apply_mock_move(board_state, chosen_move, rows, cols)
        else:
            from selfplay import query_java_engine
            result = query_java_engine(board_state, rule_vector, chosen_move)
            if not result.get("success") or not result.get("legal"):
                return -1.0 if is_red_turn else 1.0
            board_state = _apply_mock_move(board_state, chosen_move, rows, cols)

    # 达到最大步数 → 平局
    return 0.0


def evaluate(
    model_a: MiniResNet,
    model_b: MiniResNet,
    rule_vector: Optional[List[float]] = None,
    num_games: int = 100,
    rows: int = 10,
    cols: int = 9,
    use_mock: bool = False,
    num_simulations: int = 200,
) -> float:
    """两个模型对弈 num_games 局，返回 model_a 的胜率。

    model_a 和 model_b 各执红方一半的局数（交替），以消除先手优势。
    每局使用相同的规则向量（如未提供则随机生成）。

    Args:
        model_a: 待评估模型 A。
        model_b: 基准模型 B（通常为旧版本模型）。
        rule_vector: 规则向量。若为 None 则调用 make_random_rule_vector()。
        num_games: 对弈局数。
        rows: 棋盘行数。
        cols: 棋盘列数。
        use_mock: False 时通过 Java PyBridge 引擎获取真实局面数据。
        num_simulations: 每步 MCTS 模拟次数。

    Returns:
        model_a 的胜率 ∈ [0.0, 1.0]（平局计 0.5 分）。
    """
    if rule_vector is None:
        rule_vector = make_random_rule_vector()

    model_a_total = 0.0
    games_played = 0

    for i in range(num_games):
        # 交替执红，消除先手优势
        if i % 2 == 0:
            # model_a 执红，model_b 执黑
            result = _play_eval_game(
                model_red=model_a,
                model_black=model_b,
                rule_vector=rule_vector,
                rows=rows,
                cols=cols,
                use_mock=use_mock,
                num_simulations=num_simulations,
                temperature=0.1,
            )
            # result: 1.0=红胜(a胜), -1.0=黑胜(b胜), 0.0=平
        else:
            # model_b 执红，model_a 执黑
            result = _play_eval_game(
                model_red=model_b,
                model_black=model_a,
                rule_vector=rule_vector,
                rows=rows,
                cols=cols,
                use_mock=use_mock,
                num_simulations=num_simulations,
                temperature=0.1,
            )
            result = -result  # 翻转视角：红胜 → model_a 负

        if result > 0.5:
            model_a_total += 1.0       # model_a 胜
        elif result > -0.5:
            model_a_total += 0.5       # 平局

        games_played += 1

    return model_a_total / max(games_played, 1)


# ═══════════════════════════════════════════════════════════════════════════
# 主训练循环
# ═══════════════════════════════════════════════════════════════════════════

def train_main(
    num_iterations: int = 2000,
    games_per_iteration: int = 20,
    batch_size: int = 128,
    lr: float = 2e-3,
    weight_decay: float = 1e-4,
    rows: int = EXPANDED_ROWS,
    cols: int = BOARD_COLS,
    use_mock: bool = False,
    num_simulations: int = 50,
    replay_capacity: int = 100000,
    eval_interval: int = 200,
    eval_games: int = 40,
    win_rate_threshold: float = 0.55,
    save_dir: str = "checkpoints",
    num_res_blocks: int = 5,
    filters: int = 128,
    train_epochs_per_iter: int = 3,
) -> Dict[str, Any]:
    """主训练循环 — AlphaZero 风格强化学习 + 课程学习。

    流程:
      1. 初始化 MiniResNet 模型、AdamW 优化器、ReplayBuffer。
      2. 课程学习三阶段：
         阶段 1 (0%-10%):  仅标准规则（全部 false）
         阶段 2 (10%-50%): 扩展规则随机开启 2-8 条
         阶段 3 (50%-100%): 全规则空间 22 位随机
      3. 每轮迭代：
         a. 根据当前阶段生成规则向量
         b. 调用 selfplay_game 生成 games_per_iteration 局
         c. 将轨迹数据 push 到 ReplayBuffer
         d. 从 buffer 采样 batch_size 条数据，训练 train_epochs_per_iter 次
         e. 执行 train_step 更新模型（含梯度裁剪）
         f. 每 eval_interval 轮评估：新模型 vs 旧模型对战 eval_games 局
         g. 如果新模型胜率 > win_rate_threshold，保存模型（torch.save）
      4. 训练结束后导出 TorchScript（torch.jit.script）。
      5. 保存训练历史为 JSON。

    改进点（相比原版）：
      - 使用 AdamW 优化器（含 weight_decay 正则化）
      - 每轮迭代训练多个 epoch（train_epochs_per_iter）充分利用数据
      - 学习率余弦退火调度
      - 更大的 batch_size 和 games_per_iteration
      - 更大的模型（5 残差块 + 128 滤波器）

    Args:
        num_iterations: 总训练迭代次数。
        games_per_iteration: 每轮迭代生成的对局数。
        batch_size: 训练 batch 大小。
        lr: AdamW 优化器初始学习率。
        weight_decay: L2 正则化系数。
        rows: 棋盘行数（默认 10，上下连通时为 18）。
        cols: 棋盘列数。
        use_mock: False 时通过 Java PyBridge 引擎获取真实局面数据。
        num_simulations: 每步 MCTS 模拟次数。
        replay_capacity: ReplayBuffer 最大容量。
        eval_interval: 评估间隔（每 N 轮）。
        eval_games: 评估时的对弈局数。
        win_rate_threshold: 胜率阈值，超过则保存检查点。
        save_dir: 模型保存目录。
        num_res_blocks: 残差块数量。
        filters: 卷积滤波器数。
        train_epochs_per_iter: 每轮迭代的训练 epoch 数。

    Returns:
        训练历史字典，含 "loss_history"、"eval_history"、"saved_checkpoints"。
    """
    # 强制 stdout 行缓冲：服务器非 TTY 环境也实时输出（否则 print 会积压直到 4KB）
    try:
        sys.stdout.reconfigure(line_buffering=True)  # type: ignore[attr-defined]
    except Exception:
        pass  # 某些环境不支持，忽略

    # ── 非 mock 模式：启动长驻 Java PyBridge 进程 ──
    pybridge = None
    if not use_mock:
        try:
            from pybridge import PyBridgeSession
            pybridge = PyBridgeSession()
            pybridge.start()
            print(f"   Java PyBridge 已启动（长驻进程）")
        except Exception as e:
            print(f"   [!] PyBridge 启动失败: {e}", flush=True)
            print(f"   回退到 mock 模式", flush=True)
            use_mock = True

    cells = rows * cols
    os.makedirs(save_dir, exist_ok=True)

    # ── 初始化模型、优化器、缓冲区 ──
    model = MiniResNet(
        board_channels=NUM_CHANNELS,
        board_h=rows,
        board_w=cols,
        rule_dim=RULE_FULL_DIM,
        num_res_blocks=num_res_blocks,
        filters=filters,
    ).to(DEVICE)
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=weight_decay)

    # 余弦退火学习率调度
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
        optimizer, T_max=num_iterations, eta_min=lr * 0.01
    )

    buffer = ReplayBuffer(max_size=replay_capacity)

    # 保存初始模型参数作为"旧模型"的首次基准
    old_model_state = {
        k: v.clone().detach().cpu() for k, v in model.state_dict().items()
    }

    # ── 训练历史 ──
    history: Dict[str, Any] = {
        "config": {
            "num_iterations": num_iterations,
            "games_per_iteration": games_per_iteration,
            "batch_size": batch_size,
            "lr": lr,
            "rows": rows,
            "cols": cols,
            "use_mock": use_mock,
            "num_simulations": num_simulations,
            "replay_capacity": replay_capacity,
            "eval_interval": eval_interval,
            "eval_games": eval_games,
            "win_rate_threshold": win_rate_threshold,
            "start_time": datetime.now().isoformat(),
        },
        "loss_history": [],
        "eval_history": [],
        "saved_checkpoints": [],
    }

    print("=" * 60)
    print("train_main — 强化学习训练流水线")
    print(f"  总迭代次数:  {num_iterations}")
    print(f"  每轮对局数:  {games_per_iteration}")
    print(f"  Batch 大小:  {batch_size}")
    print(f"  学习率:      {lr}")
    print(f"  棋盘尺寸:    {rows}×{cols}")
    print(f"  模拟次数:    {num_simulations}")
    print(f"  模拟模式:    {'Mock（不调用 Java）' if use_mock else 'Java PyBridge'}")
    print(f"  缓冲区容量:  {replay_capacity:,}")
    print(f"  评估间隔:    每 {eval_interval} 轮")
    print(f"  保存目录:    {save_dir}")
    print("=" * 60)

    for iteration in range(num_iterations):
        # 确定当前阶段
        stage_progress = iteration / max(num_iterations, 1)
        if stage_progress < 0.10:
            stage = 1
        elif stage_progress < 0.50:
            stage = 2
        else:
            stage = 3

        # ── a) 根据当前阶段生成规则向量 ──
        rule_vector = _make_curriculum_rule_vector(iteration, num_iterations)

        # ── b) 自我对弈生成训练数据 ──
        total_samples_collected = 0
        for g in range(games_per_iteration):
            print(f"    对局 {g+1}/{games_per_iteration} …", end="", flush=True)
            trajectory, final_value = selfplay_game(
                model=model,
                rules=rule_vector,
                max_steps=1000,
                rows=rows,
                cols=cols,
                use_mock=use_mock,
                num_simulations=num_simulations,
                temperature=1.0,
                pybridge=pybridge if not use_mock else None,
            )
            print(f" {len(trajectory)} 步, value={final_value:.2f}", flush=True)

            # 将轨迹中每一步推入 ReplayBuffer
            # 价值标签：从当前回合方视角转换最终胜负值
            for step_data in trajectory:
                board_dict = step_data["board"]
                step_rules = step_data["rules"]
                step_policy = np.array(step_data["policy"], dtype=np.float32)

                # 价值标签：final_value 是红方视角的胜负（1=红胜, -1=黑胜）
                # 需要转换为当前回合方视角
                is_red = board_dict.get("redTurn", True)
                step_value = final_value if is_red else -final_value

                buffer.push(
                    board=board_dict,
                    rules=step_rules,
                    policy=step_policy,
                    value=step_value,
                    rows=rows,
                    cols=cols,
                )
                total_samples_collected += 1

        # ── c) & d) & e) 从 buffer 采样并训练（多 epoch） ──
        total_loss, policy_loss, value_loss = 0.0, 0.0, 0.0
        if len(buffer) >= batch_size:
            for _epoch in range(train_epochs_per_iter):
                batch = buffer.sample(batch_size)
                tl, pl, vl = train_step(model, batch, optimizer)
                total_loss += tl
                policy_loss += pl
                value_loss += vl
            # 取平均
            total_loss /= train_epochs_per_iter
            policy_loss /= train_epochs_per_iter
            value_loss /= train_epochs_per_iter
            # 学习率调度（仅在实际训练后 step）
            scheduler.step()

        # 记录损失
        history["loss_history"].append({
            "iteration": iteration + 1,
            "total_loss": total_loss,
            "policy_loss": policy_loss,
            "value_loss": value_loss,
            "stage": stage,
            "lr": optimizer.param_groups[0]["lr"],
        })

        # 进度输出
        log_interval = max(1, num_iterations // 20)
        if (iteration + 1) % log_interval == 0 or iteration < 5:
            current_lr = optimizer.param_groups[0]["lr"]
            print(
                f"  [Iter {iteration + 1:5d}/{num_iterations}] "
                f"stage={stage} "
                f"loss={total_loss:.4f} "
                f"(policy={policy_loss:.4f}, value={value_loss:.4f}) "
                f"lr={current_lr:.6f} "
                f"| new_samples={total_samples_collected} "
                f"buffer={len(buffer):,}"
            )

        # ── f) 定期评估 ──
        if (iteration + 1) % eval_interval == 0:
            print(f"\n  >>> 评估 @ Iter {iteration + 1} (阶段 {stage}) …")

            # 构建旧模型（使用相同架构参数）
            old_model = MiniResNet(
                board_channels=NUM_CHANNELS,
                board_h=rows,
                board_w=cols,
                rule_dim=RULE_FULL_DIM,
                num_res_blocks=num_res_blocks,
                filters=filters,
            ).to(DEVICE)
            old_model.load_state_dict(old_model_state)
            old_model.eval()

            model.eval()
            win_rate = evaluate(
                model_a=model,
                model_b=old_model,
                rule_vector=rule_vector,
                num_games=eval_games,
                rows=rows,
                cols=cols,
                use_mock=use_mock,
                num_simulations=min(num_simulations, 100),
            )

            history["eval_history"].append({
                "iteration": iteration + 1,
                "win_rate": win_rate,
            })

            print(f"  >>> model_a (新模型) 胜率: {win_rate:.2%} "
                  f"(vs 旧模型快照)")

            # ── g) 若胜率 > 阈值，保存模型并更新旧模型快照 ──
            if win_rate >= win_rate_threshold:
                checkpoint_path = os.path.join(
                    save_dir, f"model_iter{iteration + 1:05d}.pt"
                )
                torch.save({
                    "iteration": iteration + 1,
                    "model_state_dict": model.state_dict(),
                    "optimizer_state_dict": optimizer.state_dict(),
                    "win_rate": win_rate,
                }, checkpoint_path)
                history["saved_checkpoints"].append(iteration + 1)
                print(f"  >>> 检查点已保存: {checkpoint_path}")

                # 更新旧模型快照
                old_model_state = {
                    k: v.clone().detach().cpu()
                    for k, v in model.state_dict().items()
                }

    # ═══════════════════════════════════════════════════════════════════
    # 训练结束 — 保存与导出
    # ═══════════════════════════════════════════════════════════════════
    print("\n" + "=" * 60)
    print("训练完成！正在保存与导出...")

    # 保存最终模型
    final_model_path = os.path.join(save_dir, "model_final.pt")
    torch.save({
        "iteration": num_iterations,
        "model_state_dict": model.state_dict(),
        "optimizer_state_dict": optimizer.state_dict(),
    }, final_model_path)
    print(f"  最终模型已保存: {final_model_path}")

    # ── 导出 TorchScript ──
    model.eval()
    try:
        scripted = torch.jit.script(model)
        script_path = os.path.join(save_dir, "model_scripted.pt")
        torch.jit.save(scripted, script_path)
        print(f"  TorchScript 已导出: {script_path}")
    except Exception as e:
        print(f"  TorchScript 导出失败（非关键错误）: {e}")

    # ── 保存训练历史 ──
    history["config"]["end_time"] = datetime.now().isoformat()
    history_path = os.path.join(save_dir, "training_history.json")
    with open(history_path, "w", encoding="utf-8") as f:
        json.dump(history, f, ensure_ascii=False, indent=2)
    print(f"  训练历史已保存: {history_path}")

    print("=" * 60)

    return history


# ═══════════════════════════════════════════════════════════════════════════
# 小规模测试训练
# ═══════════════════════════════════════════════════════════════════════════

def test_train(
    iterations: int = 5,
    games_per_iteration: int = 2,
    use_mock: bool = False,
) -> Dict[str, Any]:
    """小规模测试训练 — 用于快速验证训练流水线完整性。

    参数极小化以便在数秒内完成，不依赖 Java 环境。

    Args:
        iterations: 迭代次数。
        games_per_iteration: 每轮对局数。
        use_mock: False 时通过 Java PyBridge 引擎获取真实局面数据。

    Returns:
        训练历史字典。
    """
    print("=" * 60)
    print("test_train — 小规模训练验证")
    print(f"  iterations={iterations}, games_per_iteration={games_per_iteration}")
    print("=" * 60)

    return train_main(
        num_iterations=iterations,
        games_per_iteration=games_per_iteration,
        batch_size=8,
        lr=1e-3,
        weight_decay=1e-4,
        rows=EXPANDED_ROWS,
        cols=BOARD_COLS,
        use_mock=use_mock,
        num_simulations=20,
        replay_capacity=1000,
        eval_interval=max(1, iterations // 2),
        eval_games=4,
        win_rate_threshold=0.55,
        save_dir="checkpoints_test",
        num_res_blocks=3,
        filters=64,
        train_epochs_per_iter=1,
    )


# ═══════════════════════════════════════════════════════════════════════════
# __main__ 测试块
# ═══════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    import sys

    print("=" * 64)
    print("train.py — 训练流水线单元测试")
    print("=" * 64)

    # ── [1] 测试 ReplayBuffer 基本操作 ──
    print("\n[1] 测试 ReplayBuffer …")
    rb = ReplayBuffer(max_size=10)

    mock_board = _generate_mock_board_state()
    mock_rules = make_all_false_rule_vector()
    mock_policy = np.ones(90, dtype=np.float32) / 90.0

    for i in range(5):
        rb.push(mock_board, mock_rules, mock_policy, 0.5)

    print(f"    push 5 条后 len={len(rb)} (期望: 5)")
    assert len(rb) == 5, f"长度错误: {len(rb)}"

    # 测试采样
    if len(rb) >= 2:
        b_board, b_rules, b_policy, b_value = rb.sample(2)
        print(f"    sample(2) board:  {tuple(b_board.shape)}   (期望: (2, 14, 10, 9))")
        print(f"    sample(2) rules:  {tuple(b_rules.shape)}   (期望: (2, {RULE_FULL_DIM}))")
        print(f"    sample(2) policy: {tuple(b_policy.shape)}  (期望: (2, 90))")
        print(f"    sample(2) value:  {tuple(b_value.shape)}   (期望: (2, 1))")
        assert b_board.shape == (2, 14, 10, 9)
        assert b_rules.shape == (2, RULE_FULL_DIM)
        assert b_policy.shape == (2, 90)
        assert b_value.shape == (2, 1)

    # 测试循环覆盖
    for i in range(10):
        rb.push(mock_board, mock_rules, mock_policy, float(i) * 0.1)
    print(f"    循环覆盖后 len={len(rb)} (期望: 10)")
    assert len(rb) == 10

    # 测试 save/load
    test_save_path = "checkpoints_test/buffer_test.json"
    rb.save(test_save_path)
    print(f"    已保存到: {test_save_path}")

    rb2 = ReplayBuffer(max_size=10)
    rb2.load(test_save_path)
    print(f"    加载后 len={len(rb2)} (期望: 10)")
    assert len(rb2) == 10

    # 验证加载数据完整性
    loaded_board, loaded_rules, loaded_policy, loaded_value = rb2.sample(1)
    assert loaded_board.shape == (1, 14, 10, 9)
    assert loaded_rules.shape == (1, RULE_FULL_DIM)
    assert loaded_policy.shape == (1, 90)
    assert loaded_value.shape == (1, 1)
    print("    ✓ ReplayBuffer 通过")

    # ── [2] 测试 train_step ──
    print("\n[2] 测试 train_step …")
    test_model = MiniResNet(
        board_channels=NUM_CHANNELS,
        board_h=10,
        board_w=9,
        rule_dim=RULE_FULL_DIM,
        num_res_blocks=3,
        filters=64,
    ).to(DEVICE)
    test_optimizer = Adam(test_model.parameters(), lr=1e-3)

    test_batch = rb.sample(4)
    total_l, policy_l, value_l = train_step(test_model, test_batch, test_optimizer)
    print(f"    total_loss={total_l:.4f}, policy_loss={policy_l:.4f}, "
          f"value_loss={value_l:.4f}")
    assert isinstance(total_l, float)
    assert isinstance(policy_l, float)
    assert isinstance(value_l, float)
    print("    ✓ train_step 通过")

    # ── [3] 测试课程学习规则向量生成 ──
    print("\n[3] 测试 _make_curriculum_rule_vector …")
    for test_iter, expected_stage in [
        (0, 1), (49, 1), (50, 2), (249, 2), (250, 3), (499, 3),
    ]:
        rv = _make_curriculum_rule_vector(test_iter, 500)
        bool_sum = sum(rv[:-1])
        print(f"    iter={test_iter:4d}/{500} → bool_sum={bool_sum:.0f} "
              f"(阶段 {expected_stage})")
        if expected_stage == 1:
            assert bool_sum == 0.0, f"阶段1应有0条规则开启，实际{bool_sum}"
    print("    ✓ 课程学习规则向量通过")

    # ── [4] 测试 _play_eval_game ──
    print("\n[4] 测试 _play_eval_game (双模型对弈) …")
    model_a = MiniResNet(
        board_channels=NUM_CHANNELS, board_h=10, board_w=9,
        rule_dim=RULE_FULL_DIM, num_res_blocks=2, filters=32,
    ).to(DEVICE)
    model_b = MiniResNet(
        board_channels=NUM_CHANNELS, board_h=10, board_w=9,
        rule_dim=RULE_FULL_DIM, num_res_blocks=2, filters=32,
    ).to(DEVICE)
    model_a.eval()
    model_b.eval()

    eval_rules = make_all_false_rule_vector()
    game_result = _play_eval_game(
        model_red=model_a,
        model_black=model_b,
        rule_vector=eval_rules,
        max_steps=20,
        rows=10,
        cols=9,
        use_mock=True,
        num_simulations=20,
        temperature=0.1,
    )
    print(f"    对局结果: {game_result} (1=红胜, -1=黑胜, 0=平局)")
    assert game_result in (-1.0, 0.0, 1.0), f"非法结果: {game_result}"
    print("    ✓ _play_eval_game 通过")

    # ── [5] 测试 evaluate ──
    print("\n[5] 测试 evaluate …")
    wr = evaluate(
        model_a=model_a,
        model_b=model_b,
        rule_vector=eval_rules,
        num_games=2,
        rows=10,
        cols=9,
        use_mock=True,
        num_simulations=20,
    )
    print(f"    model_a 胜率: {wr:.2%}")
    assert 0.0 <= wr <= 1.0, f"胜率越界: {wr}"
    print("    ✓ evaluate 通过")

    # ── [6] 运行训练 ──
    if "--full" in sys.argv:
        print("\n[6] 运行 train_main (完整模式 — 改进参数) …")
        history = train_main(
            num_iterations=200,
            games_per_iteration=10,
            batch_size=64,
            lr=2e-3,
            weight_decay=1e-4,
            use_mock=True,
            num_simulations=100,
            replay_capacity=20000,
            eval_interval=40,
            eval_games=20,
            win_rate_threshold=0.55,
            save_dir="checkpoints",
            num_res_blocks=5,
            filters=128,
            train_epochs_per_iter=3,
        )
    else:
        print("\n[6] 运行 test_train (小规模验证) …")
        history = test_train(iterations=5, games_per_iteration=2, use_mock=True)

    print(f"\n  训练历史: loss={len(history['loss_history'])} 条, "
          f"eval={len(history['eval_history'])} 条, "
          f"checkpoints={len(history['saved_checkpoints'])} 个")

    print("\n" + "=" * 64)
    print("train.py 所有测试通过！ ✓")
    print("=" * 64)
