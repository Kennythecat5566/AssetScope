$ErrorActionPreference = "Stop"
$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ServerRoot

if (-not (Test-Path ".venv\Scripts\python.exe")) {
    throw "Server environment is missing. Run .\setup.ps1 first."
}

& ".venv\Scripts\python.exe" -m uvicorn app.main:app --host 0.0.0.0 --port 8787

