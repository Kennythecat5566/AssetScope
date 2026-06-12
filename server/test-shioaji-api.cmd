@echo off
setlocal
cd /d "%~dp0"
echo This runs the official SinoPac stock test in SIMULATION mode.
echo No real order will be placed.
set /p CONFIRM=Type SIMULATION to continue: 
if not "%CONFIRM%"=="SIMULATION" (
    echo Cancelled.
    exit /b 1
)
".venv\Scripts\python.exe" -m app.tools.shioaji_api_test
pause
