"""
训练 gRPC 服务 — 接收 Java TrainingOrchestrator 推送的样本并执行训练。

功能:
  - PushSamples: 接收流式 TrainingSample，存入本地 ReplayBuffer
  - PullWeights: 返回当前模型权重的序列化数据
  - 后台训练线程: 每收集到足够样本后触发 train_step

通信架构:
  Java TrainingServiceClient (gRPC 客户端)
    → PushSamples(stream) → Python train_server.py (gRPC 服务端)
    ← PullWeights(Empty)  ←

用法:
    python train_server.py --port 50052 --checkpoint checkpoints/model_final.pt
"""

import argparse
import io
import os
import threading
import time
import traceback
from concurrent import futures
from typing import Dict, List, Optional, Tuple

import grpc
import numpy as np
import torch

try:
    import ucc_chess_pb2
    import ucc_chess_pb2_grpc
    HAS_PROTO = True
except ImportError:
    HAS_PROTO = False
    import sys
    print("⚠️  Proto 未编译，运行 compile_proto.py 以启用 gRPC", file=sys.stderr)

from model import MiniResNet
from selfplay import (
    DEVICE,
    NUM_CHANNELS,
    BOARD_COLS,
    EXPANDED_ROWS,
    STANDARD_ROWS,
    RULE_FULL_DIM,
    board_to_tensor,
    rule_vector_to_numpy,
    get_rows_for_rules,
)
from train import ReplayBuffer, train_step


# ═══════════════════════════════════════════════════════════════════════════
# 常量
# ═══════════════════════════════════════════════════════════════════════════

DEFAULT_TRAIN_PORT = 50052
"""训练 gRPC 服务默认端口。"""

MIN_SAMPLES_TO_TRAIN = 128
"""触发训练的最小样本数。"""

TRAIN_BATCH_SIZE = 64
"""训练 batch 大小。"""

TRAIN_INTERVAL_SECONDS = 5.0
"""后台训练线程轮询间隔（秒）。"""


# ═══════════════════════════════════════════════════════════════════════════
# TrainingServicer — gRPC 训练服务实现
# ═══════════════════════════════════════════════════════════════════════════

class TrainingServicer(ucc_chess_pb2_grpc.TrainingServiceServicer):
    """TrainingService gRPC 服务端实现。

    PushSamples:
        接收 Java 端流式推送的 TrainingSample，将 protobuf 消息解析为
        (board_tensor, rule_vec, policy, value) 格式存入 ReplayBuffer。

    PullWeights:
        将当前模型的 state_dict 序列化为 bytes 返回。客户端（Java 端）
        可通过此接口获取训练后的权重以更新推理模型。
    """

    def __init__(
        self,
        model: MiniResNet,
        optimizer: torch.optim.Optimizer,
        replay_buffer: ReplayBuffer,
        train_config: Optional[Dict] = None,
    ):
        """
        Args:
            model: MiniResNet 模型实例。
            optimizer: PyTorch 优化器。
            replay_buffer: 经验回放缓冲区。
            train_config: 训练配置字典，可包含:
                - batch_size (int): 默认 64
                - min_samples (int): 触发训练的最小样本数，默认 128
                - train_epochs (int): 每次触发训练的 epoch 数，默认 1
                - lr (float): 学习率
        """
        self.model = model
        self.optimizer = optimizer
        self.buffer = replay_buffer

        # 训练参数
        cfg = train_config or {}
        self.batch_size = cfg.get("batch_size", TRAIN_BATCH_SIZE)
        self.min_samples = cfg.get("min_samples", MIN_SAMPLES_TO_TRAIN)
        self.train_epochs = cfg.get("train_epochs", 1)

        # 样本计数器（线程安全用锁）
        self._lock = threading.Lock()
        self._samples_pushed_since_last_train = 0
        self._total_samples_received = 0

        # 迭代计数器（用于 PullWeights 返回）
        self._training_iteration = 0

        # 模型权重缓存（线程安全）
        self._weights_cache: Optional[bytes] = None
        self._update_weights_cache()

    # ── PushSamples: gRPC 客户端流式接收 ──────────────────────────────

    def PushSamples(self, request_iterator, context):
        """接收 Java 端流式推送的训练样本，存入 ReplayBuffer。

        proto TrainingSample 格式:
            - board: BoardStateProto (局面)
            - rules: RulesConfigProto (规则向量)
            - policy: repeated float (策略分布)
            - value: float (胜负结果)
        """
        received = 0
        for proto_sample in request_iterator:
            try:
                # 1. 将 protobuf BoardStateProto 转换为 Python dict
                board_dict = _proto_board_to_dict(proto_sample.board)

                # 2. 规则向量
                rules_list = list(proto_sample.rules.rule_vector)

                # 3. 策略分布 numpy 数组
                policy = np.array(proto_sample.policy, dtype=np.float32)

                # 4. 价值标签
                value = float(proto_sample.value)

                # 5. 校验数据完整性
                rows = board_dict.get("rows", STANDARD_ROWS)
                cols = board_dict.get("cols", BOARD_COLS)
                expected_policy_len = rows * cols
                if len(policy) != expected_policy_len:
                    # 长度不符时裁剪或填充
                    if len(policy) > expected_policy_len:
                        policy = policy[:expected_policy_len]
                    else:
                        policy = np.pad(policy, (0, expected_policy_len - len(policy)))

                # 6. 推入 ReplayBuffer（复用 train.py 中定义的 push 接口）
                self.buffer.push(
                    board=board_dict,
                    rules=rules_list,
                    policy=policy,
                    value=value,
                    rows=rows,
                    cols=cols,
                )
                received += 1

            except Exception as e:
                print(f"  [!] PushSamples 解析样本出错: {e}", flush=True)
                traceback.print_exc()
                continue

        # 更新计数器
        with self._lock:
            self._samples_pushed_since_last_train += received
            self._total_samples_received += received

        print(
            f"  [TrainingServicer] PushSamples: 收到 {received} 条样本, "
            f"buffer={len(self.buffer)}, "
            f"待训练={self._samples_pushed_since_last_train}",
            flush=True,
        )

        return ucc_chess_pb2.Empty()

    # ── PullWeights: 返回当前模型权重 ─────────────────────────────────

    def PullWeights(self, request, context):
        """返回当前模型的序列化权重。

        Returns:
            ModelWeights 消息，包含:
            - weights_data: bytes (模型 state_dict 的 torch.save 序列化)
            - iteration: int (当前训练迭代次数)
        """
        # 总是返回最新缓存的权重
        weights_bytes = self._weights_cache
        if weights_bytes is None:
            self._update_weights_cache()
            weights_bytes = self._weights_cache or b""

        print(
            f"  [TrainingServicer] PullWeights: iteration={self._training_iteration}, "
            f"size={len(weights_bytes)} bytes",
            flush=True,
        )

        return ucc_chess_pb2.ModelWeights(
            weights_data=weights_bytes,
            iteration=self._training_iteration,
        )

    # ── 后台训练循环 ──────────────────────────────────────────────────

    def _train_step_once(self) -> Tuple[float, float, float]:
        """执行一次训练步（采样 batch + train_step）。"""
        if len(self.buffer) < self.batch_size:
            return 0.0, 0.0, 0.0

        batch = self.buffer.sample(self.batch_size)
        total_loss, policy_loss, value_loss = train_step(
            self.model, batch, self.optimizer
        )
        return total_loss, policy_loss, value_loss

    def _update_weights_cache(self):
        """将当前模型 state_dict 序列化到内存。"""
        try:
            buf = io.BytesIO()
            # 只保存模型权重（不含优化器状态），减少传输体积
            torch.save(
                {"model_state_dict": self.model.state_dict()},
                buf,
            )
            with self._lock:
                self._weights_cache = buf.getvalue()
        except Exception as e:
            print(f"  [!] 权重序列化失败: {e}", flush=True)

    def training_loop(self):
        """后台训练线程主循环。

        每 TRAIN_INTERVAL_SECONDS 检查一次缓冲区，若累积的样本数
        达到 min_samples，则触发多个 epoch 的训练。
        """
        print("  [TrainingLoop] 后台训练线程已启动", flush=True)

        while True:
            try:
                time.sleep(TRAIN_INTERVAL_SECONDS)

                # 检查是否需要触发训练
                with self._lock:
                    pending = self._samples_pushed_since_last_train

                if pending < self.min_samples:
                    continue

                if len(self.buffer) < self.batch_size:
                    continue

                # ── 执行训练 ──
                model.train()
                total_loss_sum = 0.0
                policy_loss_sum = 0.0
                value_loss_sum = 0.0

                for epoch in range(self.train_epochs):
                    tl, pl, vl = self._train_step_once()
                    total_loss_sum += tl
                    policy_loss_sum += pl
                    value_loss_sum += vl

                avg_total = total_loss_sum / self.train_epochs
                avg_policy = policy_loss_sum / self.train_epochs
                avg_value = value_loss_sum / self.train_epochs

                with self._lock:
                    self._training_iteration += 1
                    self._samples_pushed_since_last_train = 0

                # 更新权重缓存（供 PullWeights 返回）
                self._update_weights_cache()

                print(
                    f"  [TrainingLoop] Iter {self._training_iteration}: "
                    f"total={avg_total:.4f}, policy={avg_policy:.4f}, "
                    f"value={avg_value:.4f}, buffer={len(self.buffer)}",
                    flush=True,
                )

            except Exception as e:
                print(f"  [!] 训练循环异常: {e}", flush=True)
                traceback.print_exc()


# ═══════════════════════════════════════════════════════════════════════════
# 辅助函数
# ═══════════════════════════════════════════════════════════════════════════

def _proto_board_to_dict(board_proto) -> dict:
    """将 protobuf BoardStateProto 转换为 Python dict。

    返回的 dict 格式与 selfplay.py 中的 board_to_tensor 兼容。
    """
    entries = []
    for e in board_proto.entries:
        piece_types = []
        for pt in e.piece_types:
            if HAS_PROTO:
                piece_types.append(ucc_chess_pb2.PieceType.Name(pt))
            else:
                piece_types.append(str(pt))
        entries.append({
            "row": e.row,
            "col": e.col,
            "pieceTypes": piece_types,
        })

    return {
        "rows": board_proto.rows,
        "cols": board_proto.cols,
        "redTurn": board_proto.red_turn,
        "entries": entries,
    }


def _create_model(
    rows: int = EXPANDED_ROWS,
    cols: int = BOARD_COLS,
    num_res_blocks: int = 5,
    filters: int = 128,
    rule_dim: int = RULE_FULL_DIM,
    checkpoint_path: Optional[str] = None,
) -> MiniResNet:
    """创建并加载 MiniResNet 模型。

    Args:
        rows: 棋盘行数。
        cols: 棋盘列数。
        num_res_blocks: 残差块数量。
        filters: 卷积滤波器数。
        rule_dim: 规则向量维度。
        checkpoint_path: 可选检查点路径，加载已有权重。

    Returns:
        MiniResNet 模型实例（eval 模式）。
    """
    model = MiniResNet(
        board_channels=NUM_CHANNELS,
        board_h=rows,
        board_w=cols,
        rule_dim=rule_dim,
        num_res_blocks=num_res_blocks,
        filters=filters,
    ).to(DEVICE)

    if checkpoint_path and os.path.exists(checkpoint_path):
        print(f"  [Model] 加载检查点: {checkpoint_path}", flush=True)
        checkpoint = torch.load(checkpoint_path, map_location=DEVICE)
        model.load_state_dict(checkpoint["model_state_dict"])
        print(f"  [Model] 检查点加载完成", flush=True)
    else:
        print(f"  [Model] 初始化新模型（未加载检查点）", flush=True)

    return model


# ═══════════════════════════════════════════════════════════════════════════
# 服务启动
# ═══════════════════════════════════════════════════════════════════════════

def serve(
    port: int = DEFAULT_TRAIN_PORT,
    checkpoint_path: Optional[str] = None,
    max_workers: int = 4,
    replay_capacity: int = 200000,
    batch_size: int = TRAIN_BATCH_SIZE,
    min_samples: int = MIN_SAMPLES_TO_TRAIN,
    train_epochs: int = 1,
    lr: float = 1e-3,
):
    """启动 gRPC 训练服务。

    Args:
        port: gRPC 服务端口。
        checkpoint_path: 可选检查点路径。
        max_workers: gRPC 线程池大小。
        replay_capacity: ReplayBuffer 最大容量。
        batch_size: 训练 batch 大小。
        min_samples: 触发训练的最小样本数。
        train_epochs: 每次触发训练的 epoch 数。
        lr: 学习率。
    """
    # 强制 stdout 行缓冲
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except Exception:
        pass

    print("=" * 60, flush=True)
    print("train_server.py — 训练 gRPC 服务", flush=True)
    print(f"  端口:           {port}", flush=True)
    print(f"  检查点:         {checkpoint_path or '（新建模型）'}", flush=True)
    print(f"  ReplayBuffer:   {replay_capacity:,}", flush=True)
    print(f"  Batch size:     {batch_size}", flush=True)
    print(f"  Min samples:    {min_samples}", flush=True)
    print(f"  Train epochs:   {train_epochs}", flush=True)
    print(f"  设备:           {DEVICE}", flush=True)
    print("=" * 60, flush=True)

    # ── 1. 创建模型 ──
    model = _create_model(
        rows=EXPANDED_ROWS,
        cols=BOARD_COLS,
        num_res_blocks=5,
        filters=128,
        rule_dim=RULE_FULL_DIM,
        checkpoint_path=checkpoint_path,
    )

    # ── 2. 创建优化器 ──
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=1e-4)

    # ── 3. 创建 ReplayBuffer ──
    replay_buffer = ReplayBuffer(max_size=replay_capacity)
    if checkpoint_path and os.path.exists(checkpoint_path):
        # 尝试从检查点目录加载缓冲区
        buffer_path = os.path.join(
            os.path.dirname(checkpoint_path), "replay_buffer.json"
        )
        if os.path.exists(buffer_path):
            try:
                replay_buffer.load(buffer_path)
                print(f"  [Buffer] 加载了 {len(replay_buffer)} 条历史样本", flush=True)
            except Exception as e:
                print(f"  [Buffer] 加载失败（忽略）: {e}", flush=True)

    # ── 4. 创建 gRPC Servicer ──
    train_config = {
        "batch_size": batch_size,
        "min_samples": min_samples,
        "train_epochs": train_epochs,
        "lr": lr,
    }
    servicer = TrainingServicer(model, optimizer, replay_buffer, train_config)

    # ── 5. 启动后台训练线程 ──
    training_thread = threading.Thread(target=servicer.training_loop, daemon=True)
    training_thread.start()

    # ── 6. 启动 gRPC 服务 ──
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=max_workers),
        options=[
            ("grpc.max_message_length", 100 * 1024 * 1024),
            ("grpc.max_receive_message_length", 100 * 1024 * 1024),
        ],
    )

    if HAS_PROTO:
        ucc_chess_pb2_grpc.add_TrainingServiceServicer_to_server(servicer, server)
        print(f"✅ TrainingService gRPC 服务已注册", flush=True)
    else:
        print(f"⚠️  Proto stub 不可用，无法注册 gRPC 服务", file=sys.stderr, flush=True)
        return

    server.add_insecure_port(f"0.0.0.0:{port}")
    server.start()
    print(f"✅ 训练 gRPC 服务已启动，端口: {port}", flush=True)
    print(f"   等待 Java TrainingOrchestrator 推送样本...", flush=True)

    try:
        server.wait_for_termination()
    except KeyboardInterrupt:
        print("\n  收到中断信号，正在停止服务...", flush=True)
        # 保存缓冲区
        if checkpoint_path:
            buffer_path = os.path.join(
                os.path.dirname(checkpoint_path), "replay_buffer.json"
            )
            try:
                replay_buffer.save(buffer_path)
                print(f"  缓冲区已保存到: {buffer_path}", flush=True)
            except Exception as e:
                print(f"  缓冲区保存失败: {e}", flush=True)
        server.stop(0)


# ═══════════════════════════════════════════════════════════════════════════
# 入口
# ═══════════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="UCC AI 训练 gRPC 服务")
    parser.add_argument("--port", type=int, default=DEFAULT_TRAIN_PORT, help="gRPC 端口")
    parser.add_argument(
        "--checkpoint", type=str, default=None,
        help="模型检查点路径（如 checkpoints/model_final.pt）",
    )
    parser.add_argument("--workers", type=int, default=4, help="gRPC 线程池大小")
    parser.add_argument(
        "--replay-capacity", type=int, default=200000,
        help="ReplayBuffer 容量",
    )
    parser.add_argument("--batch-size", type=int, default=TRAIN_BATCH_SIZE, help="训练 batch 大小")
    parser.add_argument(
        "--min-samples", type=int, default=MIN_SAMPLES_TO_TRAIN,
        help="触发训练的最小样本数",
    )
    parser.add_argument("--train-epochs", type=int, default=1, help="每次触发的训练 epoch 数")
    parser.add_argument("--lr", type=float, default=1e-3, help="学习率")

    args = parser.parse_args()

    if not HAS_PROTO:
        print("错误: proto 模块未编译。请先运行 compile_proto.py", file=sys.stderr)
        sys.exit(1)

    serve(
        port=args.port,
        checkpoint_path=args.checkpoint,
        max_workers=args.workers,
        replay_capacity=args.replay_capacity,
        batch_size=args.batch_size,
        min_samples=args.min_samples,
        train_epochs=args.train_epochs,
        lr=args.lr,
    )


if __name__ == "__main__":
    # 需要导入 sys 用于 __main__
    import sys
    main()
