"""
神经网络架构定义 — 强化学习 AI 引擎。

Phase 0-1: MiniResNet（MVP 架构，用于规则感知验证实验）
Phase 3+:  ChessTransformer（拓扑感知架构，变长棋盘 + 环面拓扑 + 堆叠棋子）

参考资料:
  - report/rl/强化学习接入方案.md §5.1-5.2
  - ucc-core RuleEncoder: 规则向量编码维度
"""

import torch
import torch.nn as nn


class ResidualBlock(nn.Module):
    """
    残差块：两个 3×3 卷积 + BatchNorm + ReLU，带残差连接。

    输出 = ReLU(conv_block(x) + x)
    """

    def __init__(self, channels: int):
        """
        Args:
            channels: 输入/输出通道数（保持不变）。
        """
        super().__init__()
        self.conv1 = nn.Conv2d(channels, channels, kernel_size=3, padding=1)
        self.bn1 = nn.BatchNorm2d(channels)
        self.conv2 = nn.Conv2d(channels, channels, kernel_size=3, padding=1)
        self.bn2 = nn.BatchNorm2d(channels)
        self.relu = nn.ReLU(inplace=True)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        identity = x
        out = self.relu(self.bn1(self.conv1(x)))
        out = self.bn2(self.conv2(out))
        out += identity
        out = self.relu(out)
        return out


class MiniResNet(nn.Module):
    """
    改进版 ResNet — 用于规则感知强化学习训练。

    改进点（相比原版）：
      - 规则向量通过 FiLM（Feature-wise Linear Modulation）注入到卷积特征中，
        而非仅在最后拼接。这让规则信息能更早地影响特征提取。
      - 策略头增加 BatchNorm 和 Dropout 防止过拟合。
      - 价值头增加隐藏层容量。
      - 支持可配置的残差块数和滤波器数。

    输入:
      - 棋盘张量:  [B, 14, H, W]   (14 通道 = 7 种棋子 × 2 方)
      - 规则向量:   [B, rule_dim]   (默认 28 维，来自 RuleEncoder：27 布尔 + 1 连续值)

    输出:
      - policy: [B, board_h * board_w]  每格落子概率（未 softmax）
      - value:  [B, 1]                 局面胜率评估 ∈ [-1, 1]
    """

    def __init__(
        self,
        board_channels: int = 14,
        board_h: int = 10,
        board_w: int = 9,
        rule_dim: int = 28,
        num_res_blocks: int = 5,
        filters: int = 128,
    ):
        """
        Args:
            board_channels: 棋盘输入通道数（默认 14：7 种棋子 × 红黑双方）。
            board_h:       棋盘行数。
            board_w:       棋盘列数。
            rule_dim:      规则向量维度（来自 RuleEncoder，默认 28：27 布尔 + 1 连续值）。
            num_res_blocks: 残差块数量（默认 5，增加表达能力）。
            filters:       卷积滤波器数（默认 128，增加容量）。
        """
        super().__init__()
        self.board_h = board_h
        self.board_w = board_w
        self.board_cells = board_h * board_w

        # ── 棋盘编码器 ──
        self.board_conv = nn.Sequential(
            nn.Conv2d(board_channels, filters, kernel_size=3, padding=1),
            nn.BatchNorm2d(filters),
            nn.ReLU(inplace=True),
        )

        # ── 残差块 ──
        self.res_blocks = nn.Sequential(
            *[ResidualBlock(filters) for _ in range(num_res_blocks)]
        )

        # ── 规则编码器（FiLM 风格：生成 scale 和 bias） ──
        self.rule_fc = nn.Sequential(
            nn.Linear(rule_dim, 128),
            nn.ReLU(inplace=True),
            nn.Linear(128, 64),
            nn.ReLU(inplace=True),
        )
        # FiLM: 规则向量生成对卷积特征的 scale 和 bias
        self.film_gamma = nn.Linear(64, filters)  # scale
        self.film_beta = nn.Linear(64, filters)   # bias

        # ── 策略头 (policy head) ──
        self.policy_conv = nn.Sequential(
            nn.Conv2d(filters, 4, kernel_size=1),
            nn.BatchNorm2d(4),
            nn.ReLU(inplace=True),
        )
        self.policy_fc = nn.Sequential(
            nn.Linear(4 * board_h * board_w + 64, 256),
            nn.ReLU(inplace=True),
            nn.Dropout(0.1),
            nn.Linear(256, board_h * board_w),
        )

        # ── 价值头 (value head) ──
        self.value_conv = nn.Sequential(
            nn.Conv2d(filters, 2, kernel_size=1),
            nn.BatchNorm2d(2),
            nn.ReLU(inplace=True),
        )
        self.value_fc = nn.Sequential(
            nn.Linear(2 * board_h * board_w + 64, 256),
            nn.ReLU(inplace=True),
            nn.Dropout(0.1),
            nn.Linear(256, 64),
            nn.ReLU(inplace=True),
            nn.Linear(64, 1),
            nn.Tanh(),
        )

    def forward(
        self,
        board: torch.Tensor,
        rule_vec: torch.Tensor,
    ) -> tuple[torch.Tensor, torch.Tensor]:
        """
        Args:
            board:    [B, board_channels, H, W]  棋盘张量。
            rule_vec: [B, rule_dim]              规则向量。

        Returns:
            (policy, value):
              - policy: [B, board_h * board_w]  每格落子 logit。
              - value:  [B, 1]                  局面胜率 ∈ [-1, 1]。
        """
        # 棋盘特征提取
        x = self.board_conv(board)       # [B, filters, H, W]
        x = self.res_blocks(x)           # [B, filters, H, W]

        # 规则编码 + FiLM 调制
        r = self.rule_fc(rule_vec)       # [B, 64]
        gamma = self.film_gamma(r).unsqueeze(-1).unsqueeze(-1)  # [B, filters, 1, 1]
        beta = self.film_beta(r).unsqueeze(-1).unsqueeze(-1)    # [B, filters, 1, 1]
        x = x * (1.0 + gamma) + beta    # FiLM: 特征调制

        # ── 策略头 ──
        p = self.policy_conv(x)          # [B, 4, H, W]
        p = p.flatten(1)                 # [B, 4*H*W]
        p = torch.cat([p, r], dim=1)     # [B, 4*H*W + 64]
        policy = self.policy_fc(p)       # [B, H*W]

        # ── 价值头 ──
        v = self.value_conv(x)           # [B, 2, H, W]
        v = v.flatten(1)                 # [B, 2*H*W]
        v = torch.cat([v, r], dim=1)     # [B, 2*H*W + 64]
        value = self.value_fc(v)         # [B, 1]

        return policy, value


class ChessTransformer(nn.Module):
    """
    Phase 3+ 拓扑感知 Transformer 架构（简化版预留接口）。

    目的（对比 MiniResNet）:
      - 处理**变长棋盘**（10×9 ↔ 18×9），不再是固定尺寸卷积。
      - 处理**环面拓扑**：棋盘上下边缘连通、左右边缘连通，
        位置编码必须捕获这种「甜甜圈面 (torus)」结构。
      - 处理**堆叠棋子**：同一格子可能有多枚棋子（如叠子规则），
        需要序列化表示而非固定通道卷积。

    当前状态: 骨架代码，forward() 返回 None 占位。
    后续 Phase 3+ 将在此填充完整的 Transformer Encoder +
    环面位置编码 + 策略/价值头实现。

    References:
      - report/rl/强化学习接入方案.md §5.2
    """

    def __init__(
        self,
        max_rows: int = 18,
        max_cols: int = 9,
        piece_types: int = 7,
        rule_dim: int = 23,
        d_model: int = 256,
        nhead: int = 8,
        num_layers: int = 4,
        dropout: float = 0.1,
    ):
        """
        Args:
            max_rows:     最大棋盘行数（支持变长棋盘上限）。
            max_cols:     最大棋盘列数。
            piece_types:  棋子种类数（将/士/象/马/车/炮/兵）。
            rule_dim:     规则向量维度。
            d_model:      Transformer 隐藏维度。
            nhead:        多头注意力头数。
            num_layers:   Transformer Encoder 层数。
            dropout:      Dropout 比例。
        """
        super().__init__()
        self.max_rows = max_rows
        self.max_cols = max_cols
        self.d_model = d_model

        # ── 可学习的环面位置编码 ──
        # 对于棋盘上的每个格子 (r, c)，其位置编码由四个信号组成：
        #   sin/cos(r / max_rows), sin/cos(c / max_cols)
        # 并且显式加入「环面连通」信号：
        #   - 上下连通：位置 (0, c) 与 (rows-1, c) 相邻
        #   - 左右连通：位置 (r, 0) 与 (r, cols-1) 相邻
        # 这里使用可学习的嵌入表，让模型自行发现环面结构。
        self.row_embed = nn.Embedding(max_rows, d_model // 2)
        self.col_embed = nn.Embedding(max_cols, d_model // 2)

        # ── 棋子类型嵌入 ──
        self.piece_embed = nn.Embedding(piece_types * 2 + 1, d_model)  # +1 for empty

        # ── 规则编码器 ──
        self.rule_encoder = nn.Sequential(
            nn.Linear(rule_dim, d_model),
            nn.ReLU(inplace=True),
        )

        # ── Transformer Encoder（骨架） ──
        # 将在 Phase 3+ 中接入实际棋盘表示
        # encoder_layer = nn.TransformerEncoderLayer(
        #     d_model=d_model, nhead=nhead, dropout=dropout, batch_first=True
        # )
        # self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=num_layers)

        # ── 占位：策略头与价值头 ──
        self.policy_head = None   # 待实现
        self.value_head = None    # 待实现

    def forward(
        self,
        board_tokens: torch.Tensor | None = None,
        rule_vec: torch.Tensor | None = None,
    ) -> tuple[torch.Tensor | None, torch.Tensor | None]:
        """
        前向传播骨架（Phase 3+ 实现）。

        Args:
            board_tokens: [B, seq_len]  棋盘序列化 token。
            rule_vec:     [B, rule_dim]  规则向量。

        Returns:
            (policy, value) — 当前返回 (None, None) 占位。
        """
        # TODO: Phase 3+ 实现
        #   1. 计算环面位置编码
        #   2. 嵌入棋子 token
        #   3. 拼接规则编码
        #   4. 送入 Transformer Encoder
        #   5. 策略头 / 价值头输出
        return None, None


# ═════════════════════════════════════════════════════════════════════════
# 测试块
# ═════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    print("=" * 60)
    print("测试 MiniResNet 前向传播")
    print("=" * 60)

    # 创建模型（使用新的默认参数）
    model = MiniResNet(
        board_channels=14,
        board_h=10,
        board_w=9,
        rule_dim=28,
        num_res_blocks=5,
        filters=128,
    )
    total_params = sum(p.numel() for p in model.parameters())
    print(f"模型参数量: {total_params:,}")

    # 构造随机输入
    batch_size = 2
    board = torch.randn(batch_size, 14, 10, 9)     # [2, 14, 10, 9]
    rule_vec = torch.randn(batch_size, 28)          # [2, 28] (27 布尔 + 1 连续值)

    # 前向传播
    with torch.no_grad():
        policy, value = model(board, rule_vec)

    print(f"输入 board shape:    {board.shape}")
    print(f"输入 rule_vec shape: {rule_vec.shape}")
    print(f"输出 policy shape:   {policy.shape}   (期望: [2, 90])")
    print(f"输出 value shape:    {value.shape}    (期望: [2, 1])")

    # 验证 shape
    assert policy.shape == (batch_size, 90), \
        f"policy shape 错误: {policy.shape} != (2, 90)"
    assert value.shape == (batch_size, 1), \
        f"value shape 错误: {value.shape} != (2, 1)"
    print("\n✓ MiniResNet 前向传播通过！")

    # 测试兼容旧参数（向后兼容）
    print("\n测试向后兼容（旧参数 num_res_blocks=3, filters=64, rule_dim=23）…")
    model_compat = MiniResNet(
        board_channels=14,
        board_h=10,
        board_w=9,
        rule_dim=23,
        num_res_blocks=3,
        filters=64,
    )
    rule_vec_old = torch.randn(batch_size, 23)
    with torch.no_grad():
        p2, v2 = model_compat(board, rule_vec_old)
    assert p2.shape == (batch_size, 90)
    assert v2.shape == (batch_size, 1)
    compat_params = sum(p.numel() for p in model_compat.parameters())
    print(f"兼容模型参数量: {compat_params:,}")
    print("✓ 向后兼容通过！")

    # ── ChessTransformer ──
    print("\n" + "=" * 60)
    print("测试 ChessTransformer 实例化")
    print("=" * 60)

    ct = ChessTransformer(
        max_rows=18,
        max_cols=9,
        piece_types=7,
        rule_dim=28,
        d_model=256,
        nhead=8,
        num_layers=4,
        dropout=0.1,
    )
    ct_params = sum(p.numel() for p in ct.parameters())
    print(f"模型参数量: {ct_params:,}")
    print("✓ ChessTransformer 实例化成功（forward 返回占位 None）")
