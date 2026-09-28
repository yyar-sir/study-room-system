@echo off
setlocal
title Study Room - Launcher

echo ============================================================
echo   Study Room Reservation System  -  one-click start
echo ============================================================
echo.

echo [1/3] MySQL
echo       Nothing to do: service "MySQL84" is set to start automatically
echo       with Windows and also holds the demo data.
echo.

echo [2/3] Starting the backend in its own window...
start "Study Room Backend (8080)" cmd /k "%~dp0start-backend.bat"
echo       waiting ~16s for it to come up...
timeout /t 16 /nobreak >nul
echo.

echo [3/3] Starting the frontend dev server in its own window...
start "Study Room Frontend (5173)" cmd /k "%~dp0start-frontend.bat"
echo       waiting ~8s for Vite to be ready...
timeout /t 8 /nobreak >nul
echo.

echo Opening http://127.0.0.1:5173 in your browser...
start "" "http://127.0.0.1:5173"

echo.
echo ============================================================
echo   Demo accounts   (password for all three:  123456)
echo     13900000000   owner      logs in to /owner/dashboard
echo     13900000001   staff      logs in to /staff/workbench
echo     13900000002   user       logs in to /user/home
echo.
echo   To STOP the system: close the two windows titled
echo     "Study Room Backend (8080)"
echo     "Study Room Frontend (5173)"
echo   (closing this launcher window does NOT stop them)
echo ============================================================
echo.
pause
