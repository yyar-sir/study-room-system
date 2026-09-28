@echo off
setlocal
title Study Room - Backend (port 8080)
cd /d "%~dp0backend"

echo ============================================================
echo   Study Room Reservation System  -  Backend
echo   http://127.0.0.1:8080
echo ============================================================
echo.

if not exist "target\study-room-0.1.0.jar" (
    echo [ERROR] Build artifact not found:
    echo         %CD%\target\study-room-0.1.0.jar
    echo.
    echo   Rebuild it with Maven ^(Maven is NOT on PATH^):
    echo     "D:\apache-maven-3.9.11\bin\mvn.cmd" -o package -DskipTests
    echo   If Maven complains about missing plugins, drop the -o flag
    echo   ^(that requires internet access^).
    echo.
    pause
    exit /b 1
)

if not exist "src\main\resources\application-local.yml" (
    echo [WARN] application-local.yml is missing.
    echo        Database password and JWT secret will fall back to the
    echo        placeholders in application.yml, and login will fail.
    echo.
)

echo Starting backend. First start takes about 10-15 seconds.
echo Keep this window OPEN while you are using the system.
echo.

java -jar "target\study-room-0.1.0.jar"

echo.
echo ------------------------------------------------------------
echo Backend process has exited.
echo If this was not intentional, scroll up for the reason.
echo The most common one: port 8080 is already in use by another
echo instance ^(check for a leftover backend window^).
echo ------------------------------------------------------------
pause
