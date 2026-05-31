$ErrorActionPreference = "Stop"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  UCC-AI 真实引擎训练（Java PyBridge + Python）" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# ── 定位项目根目录 ──
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path "$ScriptDir\.."
Set-Location $ProjectRoot

# [1/3] Maven 编译
Write-Host "[1/3] Maven 编译 Java 模块（ucc-core, ucc-common, ucc-ai）..." -ForegroundColor Yellow
mvn clean package -pl ucc-core,ucc-common,ucc-ai -am -DskipTests -q 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[错误] Maven 编译失败" -ForegroundColor Red
    Read-Host "按 Enter 退出"
    exit 1
}
Write-Host "       Maven 编译成功" -ForegroundColor Green
Write-Host ""

# [2/3] Python 环境
Write-Host "[2/3] 检查 Python 环境..." -ForegroundColor Yellow
$env:PYTHONIOENCODING = "utf-8"
$PythonDir = Join-Path $ProjectRoot "ucc-ai\python"

try {
    $torchInfo = python -c "import torch; print(f'PyTorch {torch.__version__}, CUDA: {torch.cuda.is_available()}')" 2>&1
    Write-Host "       $torchInfo" -ForegroundColor Green
} catch {
    Write-Host "[错误] Python 或 PyTorch 未安装" -ForegroundColor Red
    Read-Host "按 Enter 退出"
    exit 1
}
Write-Host ""

# [3/3] 启动训练
Write-Host "[3/3] 启动训练（use_mock=False，Java 真实引擎）..." -ForegroundColor Yellow
Write-Host ""
Write-Host "   提示：训练会持续数天，产物保存在 <项目>\ucc-ai\python\checkpoints_real" -ForegroundColor Gray
Write-Host "   按 Ctrl+C 可安全中断（会保存当前检查点）" -ForegroundColor Gray
Write-Host ""

Set-Location $PythonDir

python -c @"
import sys; sys.stdout.reconfigure(encoding='utf-8', line_buffering=True)
from train import train_main
train_main(
    use_mock=False,
    num_iterations=500,
    games_per_iteration=10,
    batch_size=64,
    num_simulations=50,
    save_dir='checkpoints_real',
)
"@

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   训练结束" -ForegroundColor Cyan
Write-Host "   检查点保存位置: $PythonDir\checkpoints_real" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Read-Host "按 Enter 退出"
