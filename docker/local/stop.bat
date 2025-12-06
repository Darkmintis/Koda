@echo off
REM Stop all Koda local development services

echo Stopping Koda local services...
cd /d "%~dp0"
docker compose down

echo.
echo All services stopped.
echo.
echo To also delete data (database, files), run:
echo   docker compose down -v
echo.
pause
