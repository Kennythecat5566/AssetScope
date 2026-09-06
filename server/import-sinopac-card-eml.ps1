$ErrorActionPreference = "Stop"
$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ServerRoot

if ($args.Count -gt 0) {
    $InputPath = $args[0]
} else {
    $InputPath = Read-Host "Path to SinoPac .eml file"
}

if (-not (Test-Path -LiteralPath $InputPath)) {
    throw "File not found: $InputPath"
}

$PasswordSecure = Read-Host "PDF password" -AsSecureString
$Bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($PasswordSecure)
try {
    $PlainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Bstr)
    $env:ASSETSCOPE_SINOPAC_CARD_PDF_PASSWORD = $PlainPassword
    & ".venv\Scripts\python.exe" -m app.tools.sinopac_card_eml_import --input $InputPath
} finally {
    Remove-Item Env:\ASSETSCOPE_SINOPAC_CARD_PDF_PASSWORD -ErrorAction SilentlyContinue
    if ($Bstr -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Bstr)
    }
}
