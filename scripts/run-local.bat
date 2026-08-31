@echo off
REM Vykronis - run all platform services locally with mvn spring-boot:run.
REM Each service starts in its own cmd window with its own HTTP port (plan §14).
REM
REM Usage (from the repo root):
REM   scripts\run-local.bat
REM   scripts\run-local.bat skiptest     ^(skip the initial build^)

setlocal enabledelayedexpansion
cd /d "%~dp0.."

set SKIP=false
if /I "%1"=="skiptest" set SKIP=true

echo Building the multi-module project first...
if "%SKIP%"=="true" (
    echo [skip] using existing build output.
) else (
    call mvn -q clean package -DskipTests
    if errorlevel 1 (
        echo Build failed. Exiting.
        exit /b 1
    )
)

REM Service : module path : port : title
set "S1=platform/api-gateway;8080;api-gateway"
set "S2=platform/ingestion-service;8081;ingestion-service"
set "S3=platform/event-service;8082;event-service"
set "S4=platform/correlation-engine;8083;correlation-engine"
set "S5=platform/incident-service;8084;incident-service"
set "S6=platform/agent-orchestrator;8085;agent-orchestrator"
set "S7=platform/policy-service;8086;policy-service"
set "S8=platform/remediation-service;8087;remediation-service"

for %%S in (1 2 3 4 5 6 7 8) do (
    for /f "tokens=1,2,3 delims=;" %%A in ("!S%%S!") do (
        start "Vykronis - %%C (:%%B)" cmd /k "mvn -q -pl %%A spring-boot:run"
        timeout /t 3 /nobreak >nul
    )
)

echo.
echo All 8 services launching in separate windows.
echo Check: scripts\health-check.ps1 -Wait
endlocal
