@echo off
echo Stopping CarGuard AI services on port 8000 and 8501...

powershell -NoProfile -Command "(Get-NetTCPConnection -LocalPort 8000,8501 -EA 0 | Where-Object OwningProcess -gt 0) | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -EA 0 }"

echo.
echo [OK] All CarGuard services on port 8000 and 8501 have been stopped.
pause
