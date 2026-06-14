$ErrorActionPreference = "Stop"

$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ServerRoot
$PythonPath = Join-Path $ServerRoot ".venv\Scripts\python.exe"
$EnvPath = Join-Path $ServerRoot ".env"
$SetupPath = Join-Path $ServerRoot "setup.ps1"
$StartPath = Join-Path $ServerRoot "start.ps1"
$TailscaleSetupPath = Join-Path $ServerRoot "setup-tailscale.ps1"

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

function Get-TailscaleExecutable {
    $Command = Get-Command tailscale.exe -ErrorAction SilentlyContinue
    if ($Command) {
        return $Command.Source
    }

    $InstalledPath = Join-Path $env:ProgramFiles "Tailscale\tailscale.exe"
    if (Test-Path -LiteralPath $InstalledPath) {
        return $InstalledPath
    }
    return $null
}

function Install-TailscaleIfNeeded {
    $TailscalePath = Get-TailscaleExecutable
    $FirewallRule = Get-NetFirewallRule `
        -DisplayName "AssetScope Server (Tailscale)" `
        -ErrorAction SilentlyContinue

    if ($TailscalePath -and $FirewallRule) {
        return $TailscalePath
    }

    Write-Host "Tailscale or its AssetScope firewall rule needs first-time setup."
    Write-Host "Approve the Windows administrator prompt to continue."
    $Arguments = @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", "`"$TailscaleSetupPath`""
    )
    $Process = Start-Process `
        -FilePath "powershell.exe" `
        -Verb RunAs `
        -ArgumentList $Arguments `
        -Wait `
        -PassThru
    if ($Process.ExitCode -ne 0) {
        throw "Tailscale setup failed or was cancelled."
    }

    $TailscalePath = Get-TailscaleExecutable
    if (-not $TailscalePath) {
        throw "Tailscale was not found after setup."
    }
    return $TailscalePath
}

function Start-Tailscale {
    $TailscalePath = Install-TailscaleIfNeeded
    $TailscaleDirectory = Split-Path -Parent $TailscalePath
    $TailscaleUi = Join-Path $TailscaleDirectory "tailscale-ipn.exe"

    $Status = $null
    try {
        $Status = & $TailscalePath status --json 2>$null |
            ConvertFrom-Json
    }
    catch {
        # Start the Windows client below and retry.
    }

    if (-not $Status -or $Status.BackendState -ne "Running") {
        if (
            (Test-Path -LiteralPath $TailscaleUi) -and
            -not (Get-Process -Name "tailscale-ipn" -ErrorAction SilentlyContinue)
        ) {
            Start-Process -FilePath $TailscaleUi -WindowStyle Hidden
            Start-Sleep -Seconds 3
        }

        Write-Host "Connecting Tailscale..."
        & $TailscalePath up
        if ($LASTEXITCODE -ne 0) {
            throw "Tailscale could not connect. Complete its login and try again."
        }
    }

    $TailscaleIp = (& $TailscalePath ip -4 2>$null |
        Select-Object -First 1).Trim()
    if (-not $TailscaleIp) {
        throw "Tailscale is running but no IPv4 address is available."
    }
    return $TailscaleIp
}

Write-Host "AssetScope VPN + Server - One-click startup" -ForegroundColor DarkGreen
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

Write-Step "Starting Tailscale VPN"
$TailscaleIp = Start-Tailscale
Write-Host "Tailscale is connected." -ForegroundColor Green
Write-Host "Remote phone URL: http://${TailscaleIp}:8787" -ForegroundColor Cyan

$PrivateIp = Get-PrivateIPv4
Write-Host "Local health check: http://127.0.0.1:8787/health"
if ($PrivateIp) {
    Write-Host "Home Wi-Fi URL: http://${PrivateIp}:8787" -ForegroundColor Cyan
}
else {
    Write-Host "No active private IPv4 address was found."
    Write-Host "Connect the computer to Wi-Fi or Ethernet, then run this script again."
}
Write-Host "The API Token is stored in server\.env."

Write-Step "Starting AssetScope Server"
& $StartPath
