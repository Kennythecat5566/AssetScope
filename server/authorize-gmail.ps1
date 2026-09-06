$ErrorActionPreference = "Stop"

$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PythonPath = Join-Path $ServerRoot ".venv\Scripts\python.exe"
$EnvPath = Join-Path $ServerRoot ".env"
$CredentialsPath = Join-Path $ServerRoot "google-oauth-client.json"

Set-Location $ServerRoot

if (-not (Test-Path -LiteralPath $PythonPath)) {
    throw "Server environment is missing. Run setup.cmd first."
}

if (-not (Test-Path -LiteralPath $EnvPath)) {
    Copy-Item ".env.example" ".env"
}

function Set-EnvLine([string]$Name, [string]$Value) {
    $Content = Get-Content -LiteralPath $EnvPath -Raw
    $Pattern = "(?m)^$([regex]::Escape($Name))=.*$"
    if ($Content -match $Pattern) {
        $Content = [regex]::Replace($Content, $Pattern, "$Name=$Value", 1)
    }
    else {
        $Content = $Content.TrimEnd() + "`r`n$Name=$Value`r`n"
    }
    $Utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllText($EnvPath, $Content, $Utf8WithoutBom)
}

Write-Host "Gmail credit-card notification authorization"
Write-Host "Scope: https://www.googleapis.com/auth/gmail.readonly"
Write-Host "Place your Google OAuth desktop client JSON here:"
Write-Host $CredentialsPath

if (-not (Test-Path -LiteralPath $CredentialsPath)) {
    throw "Missing google-oauth-client.json. See server\README.md for setup steps."
}

& $PythonPath -m pip install -e ".[gmail]"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install Gmail dependencies."
}

& $PythonPath -m app.tools.gmail_authorize
if ($LASTEXITCODE -ne 0) {
    throw "Gmail authorization failed."
}

Set-EnvLine "ASSETSCOPE_GMAIL_EXPENSES_ENABLED" "true"
Set-EnvLine "ASSETSCOPE_GMAIL_CREDENTIALS_FILE" "google-oauth-client.json"
Set-EnvLine "ASSETSCOPE_GMAIL_TOKEN_FILE" "data/raw/gmail/token.json"

Write-Host "Gmail expense importer is enabled in server\.env."
