@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo ============================================================
echo   UCC-AI 真实引擎训练（Java PyBridge + Python）
echo ============================================================
echo.

REM ── 定位项目根目录（脚本所在目录的上一级） ──
set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

echo [1/3] Maven 编译 Java 模块（ucc-core, ucc-common, ucc-ai）...
call mvn clean package -pl ucc-core,ucc-common,ucc-ai -am -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo [错误] Maven 编译失败，请检查 Java/Maven 环境
    pause
    exit /b 1
)
echo        Maven 编译成功

echo        Maven 拷贝运行时依赖 (gson)...
call mvn dependency:copy-dependencies -pl ucc-ai -DoutputDirectory=target/lib -DincludeScope=runtime -q
if %ERRORLEVEL% neq 0 (
    echo [警告] 依赖拷贝失败，训练可能因 ClassNotFoundException 中断
)
echo        依赖就绪
echo.

echo [2/3] 设置 Python 环境...
set PYTHONIOENCODING=utf-8
set "PYTHON_DIR=%PROJECT_ROOT%\ucc-ai\python"

REM 使用项目内的 CPU 虚拟环境
set "PYTHON=%PROJECT_ROOT%\.venv-cpu\Scripts\python.exe"

REM 检查 Python 和 PyTorch
%PYTHON% -c "import torch; print(f'       PyTorch {torch.__version__}, CUDA: {torch.cuda.is_available()}')"
if %ERRORLEVEL% neq 0 (
    echo [错误] Python 或 PyTorch 未安装
    pause
    exit /b 1
)
echo.

echo [3/3] 启动训练（use_mock=False，Java 真实引擎）...
echo.
echo   提示：训练会持续数天，产物保存在 ^<项目^>\ucc-ai\python\checkpoints\
echo   按 Ctrl+C 可安全中断（会保存当前检查点）
echo.

cd /d "%PYTHON_DIR%"

%PYTHON% -c "import sys; sys.stdout.reconfigure(encoding='utf-8', line_buffering=True); from train import train_main; train_main(use_mock=False, num_iterations=500, games_per_iteration=10, batch_size=64, num_simulations=50, save_dir='checkpoints_real')"

echo.
echo ============================================================
echo   训练结束
echo   检查点保存位置: %PYTHON_DIR%\checkpoints_real
echo ============================================================
pause
