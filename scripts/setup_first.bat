@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo   UCC First-time Setup
echo ============================================================

set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

echo [1/5] Checking JDK...
java -version 2>&1 | findstr "21" >nul
if !ERRORLEVEL! neq 0 (
    echo [WARN] JDK version may not be 21
    java -version 2>&1
) else (
    echo [OK] JDK 21 found
)

echo [2/5] Checking Maven...
where mvn >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Maven not found
    pause
    exit /b 1
)
echo [OK] Maven found

echo [3/5] Checking Python...
set "PYTHON_DIR=%PROJECT_ROOT%\ucc-ai\python"
set "PYTHON=%PROJECT_ROOT%\.venv-cpu\Scripts\python.exe"
if not exist "%PYTHON%" (
    where python >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] Python not found
        pause
        exit /b 1
    )
    set "PYTHON=python"
)
%PYTHON% --version
echo [OK]

echo [4/5] Installing Python gRPC dependencies...
%PYTHON% -m pip install grpcio grpcio-tools protobuf --quiet
if !ERRORLEVEL! neq 0 (
    echo [WARN] pip install failed, manual: pip install grpcio grpcio-tools protobuf
) else (
    echo [OK]
)

echo [5/5] Maven compile + Protobuf...
call mvn clean compile -DskipTests -q
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Compilation failed
    pause
    exit /b 1
)
cd /d "%PYTHON_DIR%"
%PYTHON% compile_proto.py 2>nul
cd /d "%PROJECT_ROOT%"
echo [OK]

echo ============================================================
echo   Setup complete!
echo   Next steps:
echo     scripts\start_server.bat
echo     scripts\start_training.bat
echo ============================================================
pause
