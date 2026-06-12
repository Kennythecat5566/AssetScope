$ErrorActionPreference = "Stop"

$CurrentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$Principal = [Security.Principal.WindowsPrincipal]::new($CurrentIdentity)
$IsAdministrator = $Principal.IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator
)
if (-not $IsAdministrator) {
    throw "Run this script from an Administrator PowerShell window."
}

$Tailscale = Get-Command tailscale.exe -ErrorAction SilentlyContinue
if (-not $Tailscale) {
    winget install --id Tailscale.Tailscale --exact --accept-source-agreements `
        --accept-package-agreements
    $TailscalePath = Join-Path $env:ProgramFiles "Tailscale\tailscale.exe"
    if (-not (Test-Path -LiteralPath $TailscalePath)) {
        throw "Tailscale installation finished but tailscale.exe was not found."
    }
    $Tailscale = Get-Item -LiteralPath $TailscalePath
}

$RuleName = "AssetScope Server (Tailscale)"
$ExistingRule = Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue
if (-not $ExistingRule) {
    New-NetFirewallRule `
        -DisplayName $RuleName `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort 8787 `
        -Profile Any `
        -RemoteAddress "100.64.0.0/10"
}
else {
    Set-NetFirewallRule `
        -DisplayName $RuleName `
        -Enabled True `
        -Profile Any `
        -Direction Inbound `
        -Action Allow
    Set-NetFirewallPortFilter `
        -AssociatedNetFirewallRule $ExistingRule `
        -Protocol TCP `
        -LocalPort 8787
    Set-NetFirewallAddressFilter `
        -AssociatedNetFirewallRule $ExistingRule `
        -RemoteAddress "100.64.0.0/10"
}

Write-Host "Opening Tailscale login. Sign in with the same account used on Android."
& $Tailscale.FullName up

$TailscaleIp = (& $Tailscale.FullName ip -4 | Select-Object -First 1).Trim()
if (-not $TailscaleIp) {
    throw "Tailscale is installed but no IPv4 address is available yet."
}

Write-Host ""
Write-Host "AssetScope Tailscale URL:"
Write-Host "http://${TailscaleIp}:8787"
Write-Host ""
Write-Host "The firewall permits port 8787 from Tailscale addresses only."
