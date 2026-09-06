$ErrorActionPreference = "Stop"

$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvPath = Join-Path $ServerRoot ".env"
Set-Location $ServerRoot

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

$PasswordSecure = Read-Host "SinoPac card PDF password" -AsSecureString
$Bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($PasswordSecure)
try {
    $PlainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Bstr)
    if ([string]::IsNullOrWhiteSpace($PlainPassword)) {
        throw "Password cannot be empty."
    }
    Set-EnvLine "ASSETSCOPE_GMAIL_PDF_ATTACHMENTS_ENABLED" "true"
    Set-EnvLine "ASSETSCOPE_SINOPAC_CARD_PDF_PASSWORD" $PlainPassword
}
finally {
    if ($Bstr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Bstr)
    }
}

Write-Host "SinoPac card PDF password saved to server\.env."
Write-Host "Restart AssetScope Server, then sync the Android app."
