@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-startup-task.ps1"
if errorlevel 1 pause

