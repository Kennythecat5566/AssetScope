$ErrorActionPreference = "Stop"
$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ServerRoot

if (-not (Test-Path ".venv\Scripts\python.exe")) {
    python -m venv .venv
}

& ".venv\Scripts\python.exe" -m pip install -e ".[dev]"

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Created server\.env. Replace ASSETSCOPE_API_TOKEN before starting."
}

Write-Host "AssetScope server setup is complete."

