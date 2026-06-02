@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo   UCC Server Startup Script
echo ============================================================

set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

echo [1/3] Checking JDK...
java -version 2>&1 | findstr "21" >nul
if !ERRORLEVEL! neq 0 (
    echo [WARN] JDK version may not be 21
    java -version 2>&1
)

echo [2/3] Compiling ucc-server...
call mvn compile -pl ucc-server -am -q
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Compilation failed
    pause
    exit /b 1
)
echo [OK] Compiled successfully

echo [3/3] Starting ucc-server (port 8080)
echo Press Ctrl+C to stop safely

call mvn exec:java -pl ucc-server -Dexec.mainClass="io.github.samera2022.chinese_chess.server.UCCServer" -q

echo Server stopped
pause
