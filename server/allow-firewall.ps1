$ErrorActionPreference = "Stop"

$CurrentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$Principal = [Security.Principal.WindowsPrincipal]::new($CurrentIdentity)
$IsAdministrator = $Principal.IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator
)

if (-not $IsAdministrator) {
    throw "Run this script once from an Administrator PowerShell window."
}

function Test-PrivateIPv4([string]$Address) {
    $Octets = $Address.Split(".")
    if ($Octets.Count -ne 4) {
        return $false
    }

    $First = [int]$Octets[0]
    $Second = [int]$Octets[1]
    return $First -eq 10 -or
        ($First -eq 172 -and $Second -ge 16 -and $Second -le 31) -or
        ($First -eq 192 -and $Second -eq 168)
}

$ActiveConfigurations = Get-NetIPConfiguration |
    Where-Object {
        $_.IPv4Address -and
        $_.IPv4DefaultGateway -and
        (Test-PrivateIPv4 $_.IPv4DefaultGateway.NextHop)
    }

foreach ($Configuration in $ActiveConfigurations) {
    $Profile = Get-NetConnectionProfile `
        -InterfaceIndex $Configuration.InterfaceIndex `
        -ErrorAction SilentlyContinue
    if ($Profile -and $Profile.NetworkCategory -eq "Public") {
        Set-NetConnectionProfile `
            -InterfaceIndex $Configuration.InterfaceIndex `
            -NetworkCategory Private
        Write-Host "Changed $($Configuration.InterfaceAlias) to a Private network."
    }
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
else {
    Set-NetFirewallRule `
        -DisplayName "AssetScope Server (Private LAN)" `
        -Enabled True `
        -Profile Private `
        -Direction Inbound `
        -Action Allow
    Set-NetFirewallPortFilter `
        -AssociatedNetFirewallRule $ExistingRule `
        -Protocol TCP `
        -LocalPort 8787
    Set-NetFirewallAddressFilter `
        -AssociatedNetFirewallRule $ExistingRule `
        -RemoteAddress LocalSubnet
}

Write-Host "Windows Firewall allows TCP 8787 from the private local subnet."
