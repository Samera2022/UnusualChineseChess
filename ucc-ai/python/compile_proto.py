#!/usr/bin/env python3
"""编译 ucc_chess.proto → Python gRPC stub."""
import subprocess, sys, os
PROTO_DIR = os.path.join(os.path.dirname(__file__), '../../ucc-common/src/main/proto')
OUT_DIR = os.path.dirname(__file__)

def main():
    proto_file = os.path.join(PROTO_DIR, 'ucc_chess.proto')
    if not os.path.exists(proto_file):
        print(f"Proto file not found: {proto_file}")
        sys.exit(1)
    cmd = [
        sys.executable, '-m', 'grpc_tools.protoc',
        f'--proto_path={PROTO_DIR}',
        f'--python_out={OUT_DIR}',
        f'--grpc_python_out={OUT_DIR}',
        proto_file
    ]
    subprocess.run(cmd, check=True)
    print(f"✅ Compiled {proto_file} → {OUT_DIR}")

if __name__ == '__main__':
    main()
