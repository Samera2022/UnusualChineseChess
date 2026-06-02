@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo ============================================================
echo   UCC-AI 真实引擎训练（GPU 加速）
echo ============================================================
echo.

set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

echo [1/3] Maven 编译 Java 模块...
call mvn clean package -pl ucc-core,ucc-common,ucc-ai -am -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo [错误] Maven 编译失败
    pause
    exit /b 1
)
call mvn dependency:copy-dependencies -pl ucc-ai -DoutputDirectory=target/lib -DincludeScope=runtime -q
echo        Maven 编译成功
echo.

echo [2/3] Python 环境（CUDA PyTorch）...
set PYTHONIOENCODING=utf-8
set "PYTHON_DIR=%PROJECT_ROOT%\ucc-ai\python"

REM 用系统 Python 3.10（CUDA 版 PyTorch）
set "PYTHON=C:\Users\Samera2022\AppData\Local\Programs\Python\Python310\python.exe"

%PYTHON% -c "import torch; print(f'       PyTorch {torch.__version__}, CUDA: {torch.cuda.is_available()}, GPU: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else \"N/A\"}')"
if %ERRORLEVEL% neq 0 (
    echo [错误] Python 或 PyTorch 未安装
    pause
    exit /b 1
)
echo.

echo [3/3] 启动训练（use_mock=False，Java 真实引擎 + GPU）...
echo.

cd /d "%PYTHON_DIR%"

%PYTHON% -c "import sys; sys.stdout.reconfigure(encoding='utf-8', line_buffering=True); from train import train_main; train_main(use_mock=False, num_iterations=500, games_per_iteration=10, batch_size=64, num_simulations=50, save_dir='checkpoints_real')"

echo.
echo ============================================================
echo   训练结束
echo ============================================================
pause
