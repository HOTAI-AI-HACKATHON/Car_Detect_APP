@echo off
chcp 65001 >nul
cd /d "%~dp0"
title CarGuard AI Launcher

echo ========================================================
echo  CarGuard AI - Starting Local Services
echo ========================================================
echo.

REM 自動尋找並加入 Android SDK platform-tools 到 PATH
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools;%PATH%"
)

REM 自動為實體手機或模擬器啟用 USB 端口轉發
echo [*] Checking Android USB / Emulator reverse port forwarding...
adb reverse tcp:8000 tcp:8000 >nul 2>&1
if not errorlevel 1 (
    echo [+] Android reverse 127.0.0.1:8000 forwarded successfully!
) else (
    echo [-] No adb device detected, using Wi-Fi mode.
)
echo.

REM 切換至 server 目錄以啟動背景服務
cd /d "%~dp0server"

REM 1. 啟動 FastAPI 後端與 AI 模型
echo [*] Starting FastAPI Backend on Port 8000...
start "CarGuard_API" cmd /k "uv run uvicorn main:app --host 0.0.0.0 --port 8000 --reload"

REM 2. 啟動 Streamlit 客服管理後台
echo [*] Starting Streamlit Admin Dashboard on Port 8501...
start "CarGuard_Admin" cmd /k "uv run streamlit run admin_app.py --server.port 8501"

cd /d "%~dp0"

echo.
echo ========================================================
echo  Services started successfully:
echo.
echo  - Backend API:       http://localhost:8000
echo  - Swagger API Docs:  http://localhost:8000/docs
echo  - Admin Dashboard:   http://localhost:8501
echo  - Android USB/Emu:   http://127.0.0.1:8000
echo  - Android 模擬器備援: http://10.0.2.2:8000
echo  - Android Wi-Fi IP:  http://192.168.2.102:8000
echo ========================================================
echo.
pause
