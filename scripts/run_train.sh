#!/bin/bash
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PYTHON_DIR="$PROJECT_ROOT/ucc-ai/python"
CHECKPOINT_DIR="$PYTHON_DIR/checkpoints"

echo "============================================================"
echo "  UCC 一键训练启动 (Linux)"
echo "============================================================"

# Step 1: 生成初始模型
INIT_MODEL="$CHECKPOINT_DIR/model_init.pt"
if [ ! -f "$INIT_MODEL" ]; then
    echo "[1/5] 生成初始模型..."
    cd "$PYTHON_DIR" && python init_model.py "$CHECKPOINT_DIR"
else
    echo "[1/5] 初始模型已存在，跳过"
fi

# Step 2: 编译并安装到本地仓库（install 确保 exec:java 的跨模块 classpath 正确）
echo "[2/5] 编译 Java 模块..."
cd "$PROJECT_ROOT" && mvn install -DskipTests -pl ucc-core,ucc-common,ucc-ai,ucc-server -am -q
echo "      编译完成"

# Step 3: 启动推理服务 (后台)
echo "[3/5] 启动推理服务 (端口 50051)..."
cd "$PYTHON_DIR" && python inference_server.py \
    --model "$INIT_MODEL" \
    --port 50051 \
    --workers 4 &
INFERENCE_PID=$!
echo "      推理服务 PID: $INFERENCE_PID"
sleep 3

# Step 4: 启动训练服务 (后台)
echo "[4/5] 启动训练服务 (端口 50052)..."
cd "$PYTHON_DIR" && python train_server.py \
    --port 50052 \
    --checkpoint "$INIT_MODEL" \
    --replay-capacity 200000 &
TRAIN_PID=$!
echo "      训练服务 PID: $TRAIN_PID"
sleep 2

# Step 5: 启动 Java 训练（install 后的 jar 在本地仓库，classpath 自动正确解析）
echo "[5/5] 启动 Java 训练引擎..."
cd "$PROJECT_ROOT" && mvn exec:java -pl ucc-server \
    -Dexec.mainClass="io.github.samera2022.chinese_chess.server.UCCServer" \
    -Dexec.args="--train"

# Cleanup
echo "训练完成，清理子进程..."
kill $TRAIN_PID 2>/dev/null || true
kill $INFERENCE_PID 2>/dev/null || true
echo "完成"
