#!/usr/bin/env python3
"""导出 MiniResNet → ONNX + TensorRT 优化建议."""
import torch, argparse, os
from model import MiniResNet

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--checkpoint', required=True)
    parser.add_argument('--output', default='model.onnx')
    parser.add_argument('--opset', type=int, default=17)
    args = parser.parse_args()
    
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    model = MiniResNet(14, 18, 9, 28, num_res_blocks=5, filters=128).to(device)
    ckpt = torch.load(args.checkpoint, map_location=device)
    model.load_state_dict(ckpt['model_state_dict'])
    model.eval()
    
    dummy_board = torch.randn(1, 14, 18, 9, device=device)
    dummy_rule = torch.randn(1, 28, device=device)
    
    torch.onnx.export(model, (dummy_board, dummy_rule), args.output,
        input_names=['board', 'rule_vector'],
        output_names=['policy', 'value'],
        opset_version=args.opset,
        dynamic_axes={'board': {0: 'batch'}, 'rule_vector': {0: 'batch'},
                      'policy': {0: 'batch'}, 'value': {0: 'batch'}})
    print(f"✅ Exported {args.output} (opset {args.opset})")

if __name__ == '__main__':
    main()
