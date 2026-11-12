# Legacy digest-task helper. The 23:10 digest now runs as a second trigger of
# the single "Xiaoxu WeChat Personal Tasks" task (see schedule.ps1). This script
# never registers a second task again; install only removes a leftover.
# .\integrations\wechat\digest-schedule.ps1 status|install|remove
[CmdletBinding()]
param(
  [Parameter(Position=0)]
  [ValidateSet("status","install","remove")]
  [string]$Command="status"
)
$ErrorActionPreference="Stop"
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
  throw "This scheduler runs only on Windows."
}
Import-Module ScheduledTasks -ErrorAction Stop
$taskName="Xiaoxu WeChat Candidate Digest"
$mainTaskName="Xiaoxu WeChat Personal Tasks"
$task=Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($Command -eq "status") {
  $main=Get-ScheduledTask -TaskName $mainTaskName -ErrorAction SilentlyContinue
  if ($null -eq $main) {
    Write-Output "Main WeChat task is not installed; no digest trigger."
  } else {
    $starts=@($main.Triggers | ForEach-Object { $_.StartBoundary })
    Write-Output ("Main task state: " + $main.State + " | triggers: " + @($main.Triggers).Count + " | starts: " + ($starts -join ', '))
  }
  if ($null -ne $task) {
    Write-Output ("WARNING: legacy separate digest task still present (State=" + $task.State + "); run install to remove it.")
  } else {
    Write-Output "No separate digest task: single-task mode is active."
  }
  exit 0
}
if ($Command -eq "remove") {
  if ($null -ne $task) {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction Stop
  }
  Write-Output "Legacy digest task removed. The 23:10 digest belongs to the main task; use schedule.ps1 remove to disable both. Existing reports are untouched."
  exit 0
}
# Two tasks sharing the same 15-minute ticks were the source of the duplicate
# instances. The digest is now a 23:10 trigger on the main task, so install is
# idempotent: it only removes a legacy task left by older versions.
if ($null -ne $task) {
  Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction Stop
  Write-Output "Removed legacy separate digest task."
} else {
  Write-Output "No legacy digest task to remove."
}
$main=Get-ScheduledTask -TaskName $mainTaskName -ErrorAction SilentlyContinue
if ($null -eq $main) {
  throw "Main WeChat task is missing. Run schedule.ps1 install first."
}
$hasDigestSlot=$false
foreach ($tr in $main.Triggers) {
  if ($tr.StartBoundary -match 'T23:10:00') { $hasDigestSlot=$true }
}
if (-not $hasDigestSlot) {
  throw "Main task has no 23:10 trigger. Run schedule.ps1 install to upgrade it."
}
Write-Output "Single-task mode active: 07:00-23:00 sync + 23:10 digest, no second task."
Write-Output "This report contains upload evidence only, NOT reviewed or completed task statuses."
