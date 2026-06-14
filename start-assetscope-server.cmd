@echo off
setlocal
cd /d "%~dp0"

title AssetScope Server
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\one-click-start.ps1"

if errorlevel 1 (
  echo.
  echo AssetScope Server failed to start.
  pause
)
