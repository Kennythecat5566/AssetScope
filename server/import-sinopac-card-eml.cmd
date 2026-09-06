@echo off
setlocal
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
  echo Server environment is missing. Run setup.cmd first.
  pause
  exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0import-sinopac-card-eml.ps1" %*
if errorlevel 1 pause
