$ErrorActionPreference = "Stop"

$CurrentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$Principal = [Security.Principal.WindowsPrincipal]::new($CurrentIdentity)
$IsAdministrator = $Principal.IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator
)

if (-not $IsAdministrator) {
    throw "Run this script once from an Administrator PowerShell window."
}

$ExistingRule = Get-NetFirewallRule `
    -DisplayName "AssetScope Server (Private LAN)" `
    -ErrorAction SilentlyContinue

if (-not $ExistingRule) {
    New-NetFirewallRule `
        -DisplayName "AssetScope Server (Private LAN)" `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort 8787 `
        -Profile Private `
        -RemoteAddress LocalSubnet
}

Write-Host "Windows Firewall allows TCP 8787 from the private local subnet."

