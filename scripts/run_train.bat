@echo off
setlocal enabledelayedexpansion

set "PROJECT_ROOT=%~dp0.."
set "PYTHON_DIR=%PROJECT_ROOT%\ucc-ai\python"
set "CHECKPOINT_DIR=%PYTHON_DIR%\checkpoints"
set "INIT_MODEL=%CHECKPOINT_DIR%\model_init.pt"

echo ============================================================
echo   UCC 一键训练启动 (Windows)
echo ============================================================

:: 清理残留进程（释放上次占用的端口）
echo [0/5] 清理残留进程...
taskkill /fi "WINDOWTITLE eq UCC-Inference" /f >nul 2>&1
taskkill /fi "WINDOWTITLE eq UCC-Training" /f >nul 2>&1
timeout /t 1 /nobreak >nul

:: 寻找 Python
set "PYTHON=%PROJECT_ROOT%\.venv-cpu\Scripts\python.exe"
if not exist "!PYTHON!" set "PYTHON=python"

:: Step 1: 生成初始模型
echo [1/5] 检查初始模型...
if not exist "!INIT_MODEL!" (
    echo       生成初始模型...
    cd /d "!PYTHON_DIR!"
    !PYTHON! init_model.py "!CHECKPOINT_DIR!"
) else (
    echo       初始模型已存在: !INIT_MODEL!
)

:: Step 2: 编译并安装到本地仓库（install 确保 exec:java 的跨模块 classpath 正确）
echo [2/5] 编译 Java 模块...
cd /d "!PROJECT_ROOT!"
call mvn install -DskipTests -pl ucc-core,ucc-common,ucc-ai,ucc-server -am -q
if !ERRORLEVEL! neq 0 (
    echo [错误] 编译失败
    pause
    exit /b 1
)
echo       编译完成

:: Step 3: 启动推理服务（独立CMD窗口，cmd /k 让窗口保持打开不会闪退）
echo [3/5] 启动推理服务 (端口 50051)...
start "UCC-Inference" cmd /k ""!PYTHON!" "!PYTHON_DIR!\inference_server.py" --model "!INIT_MODEL!" --port 50051 --workers 4"
echo       推理服务已启动，窗口标题: UCC-Inference
timeout /t 3 /nobreak >nul

:: Step 4: 启动训练服务（独立CMD窗口，cmd /k 让窗口保持打开不会闪退）
echo [4/5] 启动训练服务 (端口 50052)...
start "UCC-Training" cmd /k ""!PYTHON!" "!PYTHON_DIR!\train_server.py" --port 50052 --checkpoint "!INIT_MODEL!" --replay-capacity 200000"
echo       训练服务已启动，窗口标题: UCC-Training
timeout /t 2 /nobreak >nul

:: Step 5: 启动 Java 训练引擎（compile+exec 一体，确保 proto stubs 在 classpath 中）
echo [5/5] 启动 Java 训练引擎...
cd /d "!PROJECT_ROOT!"
call mvn compile exec:java -pl ucc-server -am -Dexec.mainClass="io.github.samera2022.chinese_chess.server.UCCServer" -Dexec.args="--train" -Dexec.classpathScope=runtime

:: 清理
echo 训练完成，清理进程...
taskkill /fi "WINDOWTITLE eq UCC-Inference" /f >nul 2>&1
taskkill /fi "WINDOWTITLE eq UCC-Training" /f >nul 2>&1
echo 完成
pause
