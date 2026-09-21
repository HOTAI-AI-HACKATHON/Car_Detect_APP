@echo off
title CarGuard Admin Launcher

echo ========================================================
echo  CarGuard AI - Starting Streamlit Admin Dashboard
echo ========================================================
echo.

cd server
uv run streamlit run admin_app.py --server.port 8501

pause
