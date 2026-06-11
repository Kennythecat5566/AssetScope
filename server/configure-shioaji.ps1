$ErrorActionPreference = "Stop"

$EnvPath = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path -LiteralPath $EnvPath)) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot ".env.example") -Destination $EnvPath
}

function Read-SecretText([string]$Prompt) {
    $SecureValue = Read-Host $Prompt -AsSecureString
    $Pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Pointer)
    }
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

$ApiKey = Read-SecretText "Shioaji API Key"
$SecretKey = Read-SecretText "Shioaji Secret Key"
if ($ApiKey.Length -lt 10 -or $SecretKey.Length -lt 10) {
    throw "API Key and Secret Key must each contain at least 10 characters."
}
if ($ApiKey.Contains("`r") -or $ApiKey.Contains("`n") -or
    $SecretKey.Contains("`r") -or $SecretKey.Contains("`n")) {
    throw "Credentials cannot contain line breaks."
}

$Lines = @(Get-Content -LiteralPath $EnvPath -Encoding UTF8)
$Lines = Set-EnvValue $Lines "ASSETSCOPE_SHIOAJI_ENABLED" "true"
$Lines = Set-EnvValue $Lines "ASSETSCOPE_SHIOAJI_API_KEY" $ApiKey
$Lines = Set-EnvValue $Lines "ASSETSCOPE_SHIOAJI_SECRET_KEY" $SecretKey
$Utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($EnvPath, $Lines, $Utf8WithoutBom)

Write-Host "Shioaji settings saved to server/.env."
Write-Host "AssetScope will load Taiwan stock positions and the settlement account balance."
