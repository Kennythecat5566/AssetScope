@echo off
setlocal
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
  echo Server environment is missing. Run setup.cmd first.
  pause
  exit /b 1
)

start "" "https://www.firstrade.com/"
".venv\Scripts\python.exe" -m app.download_watcher
if errorlevel 1 pause

