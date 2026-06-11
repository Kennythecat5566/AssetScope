$ErrorActionPreference = "Stop"
$ServerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PowerShell = (Get-Command powershell.exe).Source
$Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$ServerRoot\start.ps1`""

$Action = New-ScheduledTaskAction `
    -Execute $PowerShell `
    -Argument $Arguments `
    -WorkingDirectory $ServerRoot
$Trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$Settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1)

Register-ScheduledTask `
    -TaskName "AssetScope Server" `
    -Action $Action `
    -Trigger $Trigger `
    -Settings $Settings `
    -Description "Starts the local AssetScope read-only API at user logon." `
    -Force

Write-Host "AssetScope Server will start when $env:USERNAME signs in."

