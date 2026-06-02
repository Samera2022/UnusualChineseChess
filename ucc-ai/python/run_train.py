#!/usr/bin/env python3
"""
一键启动训练 — 跨平台入口点
启动: inference_server + train_server + Java UCCServer(训练模式)
"""
import subprocess
import sys
import os
import time
import signal
import atexit

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PYTHON_DIR = os.path.join(PROJECT_ROOT, "ucc-ai", "python")
CHECKPOINT_DIR = os.path.join(PYTHON_DIR, "checkpoints")

processes = []


def cleanup():
    for p in processes:
        try:
            p.terminate()
            p.wait(timeout=5)
        except Exception:
            try:
                p.kill()
            except Exception:
                pass
    print("\n进程已清理")


def main():
    os.makedirs(CHECKPOINT_DIR, exist_ok=True)
    init_model = os.path.join(CHECKPOINT_DIR, "model_init.pt")

    # 1. 生成初始模型
    if not os.path.exists(init_model):
        print("[1/5] 生成初始模型...")
        subprocess.run([sys.executable, "init_model.py", CHECKPOINT_DIR],
                       cwd=PYTHON_DIR, check=True)
    else:
        print("[1/5] 初始模型已存在")

    # 2. Maven 编译并安装到本地仓库（install 确保 exec:java 的跨模块 classpath 正确）
    print("[2/5] 编译 Java...")
    subprocess.run(["mvn", "install", "-DskipTests", "-pl", "ucc-core,ucc-common,ucc-ai,ucc-server", "-am", "-q"],
                   cwd=PROJECT_ROOT, check=True)

    # 3. 启动推理服务
    print("[3/5] 启动推理服务 (50051)...")
    p = subprocess.Popen([sys.executable, "inference_server.py",
                          "--model", init_model, "--port", "50051", "--workers", "4"],
                         cwd=PYTHON_DIR)
    processes.append(p)
    time.sleep(3)

    # 4. 启动训练服务
    print("[4/5] 启动训练服务 (50052)...")
    p = subprocess.Popen([sys.executable, "train_server.py",
                          "--port", "50052", "--checkpoint", init_model,
                          "--replay-capacity", "200000"],
                         cwd=PYTHON_DIR)
    processes.append(p)
    time.sleep(2)

    # 5. 启动 Java 训练（install 后的 jar 在本地仓库，classpath 自动正确解析）
    print("[5/5] 启动 Java 训练引擎...")
    try:
        subprocess.run(["mvn", "exec:java", "-pl", "ucc-server",
                        "-Dexec.mainClass=io.github.samera2022.chinese_chess.server.UCCServer",
                        "-Dexec.args=--train"],
                       cwd=PROJECT_ROOT)
    except KeyboardInterrupt:
        print("\n用户中断，正在清理...")


if __name__ == "__main__":
    atexit.register(cleanup)
    main()
