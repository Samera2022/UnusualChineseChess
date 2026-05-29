<# 
  .SYNOPSIS
    本地完整构建脚本，参照 .github/workflows/official-release.yml 流程
  .DESCRIPTION
    1. mvn clean package -DskipTests
    2. 依次调用 .build/build.ps1 生成 jar / zip / exe
    3. 自动设置本地 Enigma Virtual Box 路径
  .NOTES
    产物输出到 output/ 目录，与 CI 流程一致
#>

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

# ── 本地 EVB 路径 ──────────────────────────────────
$env:EVB_CONSOLE_PATH = "E:\Program Files (x86)\Enigma Virtual Box\enigmavbconsole.exe"

Write-Host "╔══════════════════════════════════════════╗" -ForegroundColor Magenta
Write-Host "║   Local Full Build (参照 CI 流程)        ║" -ForegroundColor Magenta
Write-Host "╚══════════════════════════════════════════╝" -ForegroundColor Magenta

# ── Step 1: Maven 编译 ─────────────────────────────
Write-Host "`n[Step 1/4] Maven clean package..." -ForegroundColor Cyan
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Maven build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "[OK] Maven build succeeded." -ForegroundColor Green

# ── Step 2: 生成 JAR ──────────────────────────────
Write-Host "`n[Step 2/4] Building JAR..." -ForegroundColor Cyan
& "$ScriptDir\.build\build.ps1" -Type jar
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] JAR packaging failed." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "[OK] JAR packaging succeeded." -ForegroundColor Green

# ── Step 3: 生成 ZIP ──────────────────────────────
Write-Host "`n[Step 3/4] Building ZIP..." -ForegroundColor Cyan
& "$ScriptDir\.build\build.ps1" -Type zip
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] ZIP packaging failed." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "[OK] ZIP packaging succeeded." -ForegroundColor Green

# ── Step 4: 生成 EXE ──────────────────────────────
Write-Host "`n[Step 4/4] Building EXE..." -ForegroundColor Cyan
& "$ScriptDir\.build\build.ps1" -Type exe
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] EXE packaging failed." -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "[OK] EXE packaging succeeded." -ForegroundColor Green

# ── 完成 ──────────────────────────────────────────
Write-Host "`n╔══════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║   All builds completed successfully!     ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════════╝" -ForegroundColor Green

Get-ChildItem "$ScriptDir\output\*.jar", "$ScriptDir\output\*.zip", "$ScriptDir\output\*.exe" -ErrorAction SilentlyContinue |
    ForEach-Object { Write-Host "  → $($_.Name)  ($('{0:N2} MB' -f ($_.Length / 1MB)))" -ForegroundColor White }
