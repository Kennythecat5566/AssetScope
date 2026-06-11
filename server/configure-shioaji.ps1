$ErrorActionPreference = "Stop"

$EnvPath = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path -LiteralPath $EnvPath)) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot ".env.example") -Destination $EnvPath
}

function Read-SecretFromClipboard([string]$Name) {
    Read-Host "Copy the $Name, then press Enter (do not paste it into this window)"
    $ClipboardValue = Get-Clipboard -Raw
    Set-Clipboard -Value ""
    $Value = "$ClipboardValue".Trim()
    if ($Value.Contains("`r") -or $Value.Contains("`n")) {
        throw "$Name must contain exactly one line."
    }
    if ($Value.Length -lt 10) {
        throw "$Name must contain at least 10 characters; received $($Value.Length)."
    }
    Write-Host "$Name received ($($Value.Length) characters)."
    return $Value
}

function Set-EnvValue([string[]]$Lines, [string]$Name, [string]$Value) {
    $Prefix = "$Name="
    $Found = $false
    $Updated = foreach ($Line in $Lines) {
        if ($Line.StartsWith($Prefix)) {
            $Found = $true
            "$Prefix$Value"
        }
        else {
            $Line
        }
    }
    if (-not $Found) {
        $Updated += "$Prefix$Value"
    }
    return $Updated
}

$ApiKey = Read-SecretFromClipboard "Shioaji API Key"
$SecretKey = Read-SecretFromClipboard "Shioaji Secret Key"

$Lines = @(Get-Content -LiteralPath $EnvPath -Encoding UTF8)
$Lines = Set-EnvValue $Lines "ASSETSCOPE_SHIOAJI_ENABLED" "true"
$Lines = Set-EnvValue $Lines "ASSETSCOPE_SHIOAJI_API_KEY" $ApiKey
$Lines = Set-EnvValue $Lines "ASSETSCOPE_SHIOAJI_SECRET_KEY" $SecretKey
$Utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($EnvPath, $Lines, $Utf8WithoutBom)

Write-Host "Shioaji settings saved to server/.env."
Write-Host "AssetScope will load Taiwan stock positions and the settlement account balance."
