$ErrorActionPreference = "Stop"
$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ServerRoot

if (-not (Test-Path ".venv\Scripts\python.exe")) {
    throw "Server environment is missing. Run .\setup.ps1 first."
}

$ExistingListener = Get-NetTCPConnection `
    -LocalPort 8787 `
    -State Listen `
    -ErrorAction SilentlyContinue |
    Select-Object -First 1

if ($ExistingListener) {
    try {
        $Health = Invoke-RestMethod `
            -Uri "http://127.0.0.1:8787/health" `
            -TimeoutSec 3
        if ($Health.status -eq "ok") {
            $Owner = Get-Process `
                -Id $ExistingListener.OwningProcess `
                -ErrorAction SilentlyContinue
            $EnvFile = Join-Path $ServerRoot ".env"
            $ConfigChanged = $Owner -and
                (Test-Path -LiteralPath $EnvFile) -and
                ((Get-Item -LiteralPath $EnvFile).LastWriteTimeUtc -gt
                    $Owner.StartTime.ToUniversalTime())
            if (-not $ConfigChanged) {
                Write-Host "AssetScope Server is already running on port 8787."
                exit 0
            }

            Write-Host "Configuration changed. Restarting AssetScope Server..."
            Stop-Process -Id $ExistingListener.OwningProcess -Force
            Start-Sleep -Seconds 2
        }
    } catch {
        # Fall through to the port ownership error below.
    }

    $Owner = Get-Process `
        -Id $ExistingListener.OwningProcess `
        -ErrorAction SilentlyContinue
    $OwnerName = if ($Owner) { $Owner.ProcessName } else { "unknown process" }
    throw "Port 8787 is already used by $OwnerName (PID $($ExistingListener.OwningProcess))."
}

& ".venv\Scripts\python.exe" -m uvicorn app.main:app --host 0.0.0.0 --port 8787
