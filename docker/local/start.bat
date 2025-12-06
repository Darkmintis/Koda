@echo off
REM Koda Local Development Starter for Windows
REM This script starts all required services for local development

echo ======================================
echo   Koda Local Development Setup
echo ======================================
echo.

REM Check if Docker is running
docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker is not running!
    echo Please start Docker Desktop and try again.
    pause
    exit /b 1
)

echo [1/4] Starting infrastructure services...
cd /d "%~dp0"
docker compose up -d

echo.
echo [2/4] Waiting for services to be ready...
timeout /t 10 /nobreak >nul

echo.
echo [3/4] Checking service health...
docker compose ps

echo.
echo ======================================
echo   Services Started Successfully!
echo ======================================
echo.
echo Service URLs:
echo   - Mailhog (emails):  http://localhost:8025
echo   - MinIO Console:     http://localhost:9001
echo     Login: koda_access_key / koda_secret_key
echo.
echo Database:
echo   - PostgreSQL: localhost:5432
echo   - Redis:      localhost:6379
echo.
echo [4/4] Next steps:
echo   1. Copy .env.example to project root as .env
echo   2. Run backend:  cd backend ^&^& scripts\repl
echo   3. Run frontend: cd frontend ^&^& npm run dev
echo.
echo Press any key to open Mailhog in browser...
pause >nul
start http://localhost:8025
