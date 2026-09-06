$ErrorActionPreference = "Stop"
$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ServerRoot

if (-not (Test-Path ".venv\Scripts\python.exe")) {
    throw "Server environment is missing. Run .\setup.ps1 first."
}

function Get-AssetScopeListener {
    Get-NetTCPConnection `
        -LocalPort 8787 `
        -State Listen `
        -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

function Wait-PortRelease {
    param([int]$Port)

    for ($Attempt = 0; $Attempt -lt 20; $Attempt++) {
        $Listener = Get-NetTCPConnection `
            -LocalPort $Port `
            -State Listen `
            -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if (-not $Listener) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

$ExistingListener = Get-AssetScopeListener

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
            $SourceChanged = $Owner -and (
                Get-ChildItem `
                    -LiteralPath (Join-Path $ServerRoot "app") `
                    -Recurse `
                    -File `
                    -Include "*.py" |
                Where-Object {
                    $_.LastWriteTimeUtc -gt $Owner.StartTime.ToUniversalTime()
                } |
                Select-Object -First 1
            )
            if (-not $ConfigChanged -and -not $SourceChanged) {
                Write-Host "AssetScope Server is already running on port 8787."
                exit 0
            }

            Write-Host "AssetScope configuration or source changed. Restarting server..."
            Stop-Process -Id $ExistingListener.OwningProcess -Force
            if (Wait-PortRelease -Port 8787) {
                $ExistingListener = $null
            }
            else {
                $ExistingListener = Get-AssetScopeListener
            }
        }
    } catch {
        # Fall through to the port ownership error below.
    }

    if ($ExistingListener) {
        $Owner = Get-Process `
            -Id $ExistingListener.OwningProcess `
            -ErrorAction SilentlyContinue
        if ($Owner) {
            $OwnerName = $Owner.ProcessName
            throw "Port 8787 is already used by $OwnerName (PID $($ExistingListener.OwningProcess))."
        }

        if (-not (Wait-PortRelease -Port 8787)) {
            throw "Port 8787 is still busy after waiting for the previous process to exit."
        }
    }
}

& ".venv\Scripts\python.exe" -m uvicorn app.main:app --host 0.0.0.0 --port 8787
