$ErrorActionPreference = "Stop"

$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PythonPath = Join-Path $ServerRoot ".venv\Scripts\python.exe"
$EnvPath = Join-Path $ServerRoot ".env"

Set-Location $ServerRoot

if (-not (Test-Path -LiteralPath $PythonPath)) {
    throw "Server environment is missing. Run setup.cmd first."
}

if (-not (Test-Path -LiteralPath $EnvPath)) {
    Copy-Item ".env.example" ".env"
}

function ConvertFrom-SecureStringToPlainText([securestring]$Value) {
    $Pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Pointer)
    }
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

Write-Host "Firstrade API authorization"
Write-Host "Credentials are used once to create a local session token."
Write-Host "They are not written to server\.env."

& $PythonPath -m pip install -e ".[firstrade]"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to install Firstrade API dependencies."
}

$Username = Read-Host "Firstrade username"
$PasswordSecure = Read-Host "Firstrade password" -AsSecureString
$MfaSecretSecure = Read-Host "TOTP MFA secret (optional; press Enter to skip)" -AsSecureString
$Email = Read-Host "Email MFA address (optional; press Enter to skip)"
$Phone = Read-Host "Phone MFA number (optional; press Enter to skip)"
$PinSecure = Read-Host "PIN MFA (optional; press Enter to skip)" -AsSecureString

$env:ASSETSCOPE_FIRSTRADE_API_USERNAME = $Username
$env:ASSETSCOPE_FIRSTRADE_API_PASSWORD = ConvertFrom-SecureStringToPlainText $PasswordSecure
$env:ASSETSCOPE_FIRSTRADE_API_MFA_SECRET = ConvertFrom-SecureStringToPlainText $MfaSecretSecure
$env:ASSETSCOPE_FIRSTRADE_API_EMAIL = $Email
$env:ASSETSCOPE_FIRSTRADE_API_PHONE = $Phone
$env:ASSETSCOPE_FIRSTRADE_API_PIN = ConvertFrom-SecureStringToPlainText $PinSecure

try {
    & $PythonPath -m app.tools.firstrade_api_authorize
    if ($LASTEXITCODE -ne 0) {
        throw "Firstrade authorization failed."
    }
}
finally {
    Remove-Item Env:\ASSETSCOPE_FIRSTRADE_API_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:\ASSETSCOPE_FIRSTRADE_API_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\ASSETSCOPE_FIRSTRADE_API_MFA_SECRET -ErrorAction SilentlyContinue
    Remove-Item Env:\ASSETSCOPE_FIRSTRADE_API_EMAIL -ErrorAction SilentlyContinue
    Remove-Item Env:\ASSETSCOPE_FIRSTRADE_API_PHONE -ErrorAction SilentlyContinue
    Remove-Item Env:\ASSETSCOPE_FIRSTRADE_API_PIN -ErrorAction SilentlyContinue
}

Set-EnvLine "ASSETSCOPE_FIRSTRADE_API_ENABLED" "true"
Set-EnvLine "ASSETSCOPE_FIRSTRADE_API_PATH" "../firstrade-api-main"

Write-Host "Firstrade API connector is enabled in server\.env."
