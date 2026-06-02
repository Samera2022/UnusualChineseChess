"""
高性能批量推理服务（gRPC + GPU 批处理）。
接收 Java BatchingEngine 发来的批量 BoardState，批量前向传播。

使用方式:
    python inference_server.py --port 50051                            # 随机权重启动
    python inference_server.py --model checkpoints/best.pt --port 50051 # 加载 checkpoint 启动
"""
import argparse
import grpc
import logging
import os
import torch
import numpy as np
from concurrent import futures
from typing import Optional

logger = logging.getLogger(__name__)

try:
    import ucc_chess_pb2
    import ucc_chess_pb2_grpc
    HAS_PROTO = True
except ImportError:
    HAS_PROTO = False
    import sys
    print("⚠️  Proto 未编译，运行 compile_proto.py 以启用 gRPC", file=sys.stderr)

from model import MiniResNet
from selfplay import board_to_tensor, rule_vector_to_numpy


class InferenceServicer:
    """gRPC InferenceService 实现 — 批量推理 + 模型热更新。"""

    def __init__(self, model_path: Optional[str] = None, device: str = "cuda"):
        self.device = torch.device(device if torch.cuda.is_available() else "cpu")
        self.model = MiniResNet(
            board_channels=14, board_h=18, board_w=9,
            rule_dim=28, num_res_blocks=5, filters=128,
        ).to(self.device)

        if model_path is None or not os.path.isfile(model_path):
            if model_path is not None:
                logger.warning("模型文件 %s 不存在，使用随机权重初始化", model_path)
            else:
                logger.info("未指定模型文件，使用随机权重初始化")
            self._iteration = 0
        else:
            logger.info("加载模型 checkpoint: %s", model_path)
            checkpoint = torch.load(model_path, map_location=self.device)
            self.model.load_state_dict(checkpoint["model_state_dict"])
            self._iteration = checkpoint.get("iteration", 0)

        self.model.eval()
        self.use_amp = self.device.type == "cuda"

    def UpdateModel(self, request, context):
        """热更新模型权重（强化学习闭环 — 训练→推理→更强的自我对弈）。"""
        weights_bytes = request.weights_data
        iteration = request.iteration
        if not weights_bytes:
            context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
            context.set_details("Empty weights data")
            return ucc_chess_pb2.Empty()

        try:
            import io
            buffer = io.BytesIO(weights_bytes)
            state_dict = torch.load(buffer, map_location=self.device, weights_only=True)
            self.model.load_state_dict(state_dict)
            self.model.eval()
            self._iteration = iteration
            logger.info(f"模型热更新完成 (iteration={iteration}, {len(weights_bytes)} bytes)")
        except Exception as e:
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(f"Failed to load weights: {e}")
        return ucc_chess_pb2.Empty()

    def Ping(self, request, context):
        """健康检查。"""
        return ucc_chess_pb2.Empty()

    def BatchInfer(self, request, context):
        batch_size = len(request.boards)
        if batch_size == 0:
            return ucc_chess_pb2.InferenceResponse()

        # 模型固定使用 18×9 输入（初始化时 board_h=18, board_w=9）
        # 10×9 的棋盘会被 pad 到 18×9（多出的 8 行全零）
        rows = 18
        cols = 9

        board_tensors = np.zeros((batch_size, 14, rows, cols), dtype=np.float32)
        rule_tensors = np.zeros((batch_size, 28), dtype=np.float32)

        for i, (board_proto, rules_proto) in enumerate(
            zip(request.boards, request.rules)):
            board_dict = proto_to_dict(board_proto)
            # board_to_tensor 按 board_dict 中的实际 rows 填充，多余的保持零
            actual_rows = board_dict.get("rows", 10)
            partial = board_to_tensor(board_dict, actual_rows, cols)
            board_tensors[i, :, :actual_rows, :] = partial
            rule_tensors[i] = np.array(rules_proto.rule_vector, dtype=np.float32)

        board_batch = torch.from_numpy(board_tensors).to(self.device)
        rule_batch = torch.from_numpy(rule_tensors).to(self.device)

        with torch.no_grad():
            if self.use_amp:
                with torch.cuda.amp.autocast():
                    policy_logits, values = self.model(board_batch, rule_batch)
            else:
                policy_logits, values = self.model(board_batch, rule_batch)

        policy_probs = torch.softmax(policy_logits, dim=1).cpu().numpy()
        values_np = values.cpu().numpy()

        response = ucc_chess_pb2.InferenceResponse()
        for i in range(batch_size):
            pv = response.results.add()
            pv.policy.extend(policy_probs[i].tolist())
            pv.value = float(values_np[i][0])
        return response

    def infer_numpy(self, board_tensors: np.ndarray, rule_tensors: np.ndarray):
        board_batch = torch.from_numpy(board_tensors).to(self.device)
        rule_batch = torch.from_numpy(rule_tensors).to(self.device)
        with torch.no_grad():
            if self.use_amp:
                with torch.cuda.amp.autocast():
                    policy_logits, values = self.model(board_batch, rule_batch)
            else:
                policy_logits, values = self.model(board_batch, rule_batch)
        policy_probs = torch.softmax(policy_logits, dim=1).cpu().numpy()
        values_np = values.cpu().numpy()
        return policy_probs, values_np


def proto_to_dict(board_proto):
    return {
        "rows": board_proto.rows,
        "cols": board_proto.cols,
        "redTurn": board_proto.red_turn,
        "entries": [
            {
                "row": e.row,
                "col": e.col,
                "pieceTypes": [
                    ucc_chess_pb2.PieceType.Name(pt) if HAS_PROTO else str(pt)
                    for pt in e.piece_types
                ],
            }
            for e in board_proto.entries
        ],
    }


def serve(model_path: Optional[str] = None, port: int = 50051, max_workers: int = 4):
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=max_workers),
        options=[
            ('grpc.max_message_length', 100 * 1024 * 1024),
            ('grpc.max_receive_message_length', 100 * 1024 * 1024),
        ],
    )

    if HAS_PROTO:
        servicer = InferenceServicer(model_path)
        ucc_chess_pb2_grpc.add_InferenceServiceServicer_to_server(servicer, server)
        print(f"✅ gRPC InferenceService registered with MiniResNet model")
    else:
        print(f"⚠️ Proto stub not available, running in local-test mode only")

    server.add_insecure_port(f"0.0.0.0:{port}")
    server.start()
    gpu_name = torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU'
    print(f"✅ 推理服务已启动，端口: {port}, 设备: {gpu_name}")
    server.wait_for_termination()


def main():
    parser = argparse.ArgumentParser(description="UCC AI gRPC 推理服务（支持随机权重启动，无需预训练模型）")
    parser.add_argument("--model", type=str, required=False, default=None, help="模型检查点路径（不指定则使用随机权重）")
    parser.add_argument("--port", type=int, default=50051, help="gRPC 端口")
    parser.add_argument("--device", type=str, default="cuda", help="设备 (cuda/cpu)")
    parser.add_argument("--workers", type=int, default=4, help="gRPC 线程池大小")
    parser.add_argument("--test", action="store_true", help="运行本地测试（不启动 gRPC）")
    args = parser.parse_args()

    if args.test:
        print("⚠️ 本地测试模式：使用 numpy 数组直接推理")
        servicer = InferenceServicer(args.model, args.device)
        test_board = np.random.randn(1, 14, 10, 9).astype(np.float32)
        test_rule = np.random.randn(1, 28).astype(np.float32)
        policies, values = servicer.infer_numpy(test_board, test_rule)
        print(f"测试推理完成: policy shape={policies.shape}, value={values[0][0]:.4f}")
    else:
        serve(args.model, args.port, args.workers)


if __name__ == "__main__":
    main()
