#!/usr/bin/env python3
"""生成随机权重的初始模型，解决鸡生蛋问题（无需预训练模型即可启动推理服务）。"""
import torch
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from model import MiniResNet


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "checkpoints"
    os.makedirs(out_dir, exist_ok=True)

    rows = 18  # EXPANDED_ROWS（同时支持 10×9 和 18×9 推理）
    cols = 9
    model = MiniResNet(14, rows, cols, rule_dim=28, num_res_blocks=5, filters=128)

    path = os.path.join(out_dir, "model_init.pt")
    torch.save({
        "model_state_dict": model.state_dict(),
        "iteration": 0,
        "config": {"rows": rows, "cols": cols, "num_res_blocks": 5, "filters": 128},
    }, path)
    print(f"✅ 初始模型已生成: {path}")
    print(f"   参数量: {sum(p.numel() for p in model.parameters()):,}")


if __name__ == "__main__":
    main()
