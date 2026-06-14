$ErrorActionPreference = "Stop"

$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ServerRoot
$PythonPath = Join-Path $ServerRoot ".venv\Scripts\python.exe"
$EnvPath = Join-Path $ServerRoot ".env"
$SetupPath = Join-Path $ServerRoot "setup.ps1"
$StartPath = Join-Path $ServerRoot "start.ps1"

Set-Location $ServerRoot

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor DarkGreen
}

function New-ApiToken {
    $Bytes = New-Object byte[] 32
    $Generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $Generator.GetBytes($Bytes)
    }
    finally {
        $Generator.Dispose()
    }
    return [Convert]::ToBase64String($Bytes)
}

function Initialize-EnvironmentFile {
    if (-not (Test-Path -LiteralPath $EnvPath)) {
        Copy-Item `
            -LiteralPath (Join-Path $ServerRoot ".env.example") `
            -Destination $EnvPath
        Write-Host "Created server\.env."
    }

    $Content = Get-Content -LiteralPath $EnvPath -Raw
    $TokenPattern = "(?m)^ASSETSCOPE_API_TOKEN=(.*)$"
    $TokenRegex = [regex]::new($TokenPattern)
    $TokenMatch = $TokenRegex.Match($Content)
    $CurrentToken = if ($TokenMatch.Success) {
        $TokenMatch.Groups[1].Value.Trim()
    }
    else {
        ""
    }

    if (
        $CurrentToken.Length -ge 16 -and
        $CurrentToken -ne "replace-with-a-long-random-token"
    ) {
        return
    }

    $NewToken = New-ApiToken
    if ($TokenMatch.Success) {
        $Content = $TokenRegex.Replace(
            $Content,
            "ASSETSCOPE_API_TOKEN=$NewToken",
            1
        )
    }
    else {
        $Content = "ASSETSCOPE_API_TOKEN=$NewToken`r`n$Content"
    }
    $Utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllText($EnvPath, $Content, $Utf8WithoutBom)
    Write-Host "Generated a secure API Token in server\.env."
}

function Get-PrivateIPv4 {
    $Addresses = Get-NetIPConfiguration -ErrorAction SilentlyContinue |
        Where-Object {
            $_.NetAdapter.Status -eq "Up" -and
            $_.IPv4DefaultGateway -and
            $_.IPv4Address
        } |
        ForEach-Object { $_.IPv4Address.IPAddress } |
        Where-Object {
            $_ -match "^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)"
        }
    return $Addresses | Select-Object -First 1
}

Write-Host "AssetScope Server - One-click startup" -ForegroundColor DarkGreen
Write-Host "Project: $ProjectRoot"

if (-not (Test-Path -LiteralPath $PythonPath)) {
    Write-Step "Preparing the Python environment (first run)"
    & $SetupPath
    if ($LASTEXITCODE -ne 0) {
        throw "Server environment setup failed."
    }
}

Write-Step "Checking server configuration"
Initialize-EnvironmentFile

$PrivateIp = Get-PrivateIPv4
Write-Host "Local health check: http://127.0.0.1:8787/health"
if ($PrivateIp) {
    Write-Host "Phone server URL: http://${PrivateIp}:8787" -ForegroundColor Cyan
}
else {
    Write-Host "No active private IPv4 address was found."
    Write-Host "Connect the computer to Wi-Fi or Ethernet, then run this script again."
}
Write-Host "The API Token is stored in server\.env."
Write-Host "Run server\allow-firewall.cmd once as Administrator if the phone cannot connect."

Write-Step "Starting AssetScope Server"
& $StartPath
