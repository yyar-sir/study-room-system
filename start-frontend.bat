@echo off
setlocal
title Study Room - Frontend (port 5173)
cd /d "%~dp0frontend"

echo ============================================================
echo   Study Room Reservation System  -  Frontend dev server
echo   http://127.0.0.1:5173
echo ============================================================
echo.

if not exist "node_modules" (
    echo [ERROR] node_modules not found. Install dependencies first:
    echo         cd /d "%CD%"
    echo         npm install
    echo.
    pause
    exit /b 1
)

echo Starting Vite dev server...
echo Keep this window OPEN while you are using the system.
echo.
echo Tip: the dev server proxies /api to the backend on port 8080,
echo      so start the backend FIRST.
echo.

call npm run dev

echo.
echo ------------------------------------------------------------
echo Frontend dev server has stopped.
echo ------------------------------------------------------------
pause
