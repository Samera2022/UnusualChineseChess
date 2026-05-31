@echo off
chcp 65001 >nul 2>&1
setlocal

echo ============================================================
echo   UCC-AI Mock 训练（纯 Python，不依赖 Java）
echo ============================================================
echo.

set "PROJECT_ROOT=%~dp0.."
set PYTHONIOENCODING=utf-8
set "PYTHON_DIR=%PROJECT_ROOT%\ucc-ai\python"

set "PYTHON=%PROJECT_ROOT%\.venv-cpu\Scripts\python.exe"

echo [1/2] 检查 Python / PyTorch...
%PYTHON% -c "import torch; print(f'       PyTorch {torch.__version__}, CUDA: {torch.cuda.is_available()}')" 2>nul
if %ERRORLEVEL% neq 0 (
    echo [错误] Python 或 PyTorch 未安装
    pause
    exit /b 1
)
echo.

echo [2/2] 启动训练（use_mock=True，纯 Python 模拟）...
echo.
echo   产物在 ^<项目^>\ucc-ai\python\checkpoints\
echo.

cd /d "%PYTHON_DIR%"
%PYTHON% -c "import sys; sys.stdout.reconfigure(encoding='utf-8', line_buffering=True); from train import train_main; train_main(use_mock=True, num_iterations=500, games_per_iteration=20, batch_size=128, num_simulations=50, save_dir='checkpoints')"

echo.
echo ============================================================
echo   训练结束
echo ============================================================
pause
