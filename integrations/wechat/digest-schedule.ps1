# Explicit opt-in for the local, privacy-minimal Beijing 23:10 digest.
# Independent from existing WeChat collection schedule. No AI or cloud service.
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
$task=Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($Command -eq "status") {
  if ($null -eq $task) {
    Write-Output "Local daily digest is not scheduled."
  } else {
    Write-Output ("Digest schedule state: " + $task.State)
    $info=Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction SilentlyContinue
    if ($null -ne $info) {
      Write-Output ("Last task result: " + $info.LastTaskResult + "; last run: " + $info.LastRunTime)
    }
  }
  exit 0
}
if ($Command -eq "remove") {
  if ($null -ne $task) {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction Stop
  }
  Write-Output "Digest task disabled. Existing reports and WeChat sync are untouched."
  exit 0
}
$runner=Join-Path $PSScriptRoot "scheduled-digest.ps1"
$python=Join-Path $env:USERPROFILE ".wechat-cli\py312-venv\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $runner -PathType Leaf) -or
    -not (Test-Path -LiteralPath $python -PathType Leaf)) {
  throw "Install the verified Python 3.12 environment and scheduled-digest.ps1 first."
}
if ($runner.Contains('"')) { throw "Path contains unsupported quote." }
$exe=Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
$argsLine='-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -File "' + $runner + '" run'
$action=New-ScheduledTaskAction -Execute $exe -Argument $argsLine -WorkingDirectory $PSScriptRoot
$trigger=New-ScheduledTaskTrigger -Daily -At "00:00"
$repeat=New-CimInstance -ClientOnly -Namespace "root/Microsoft/Windows/TaskScheduler" -ClassName "MSFT_TaskRepetitionPattern" -Property @{
  Interval="PT5M";Duration="P1D";StopAtDurationEnd=$false
}
$trigger.Repetition=$repeat
$settings=New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 2) -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$user=[Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal=New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
if ($null -ne $task) {
  $oldAction=$task.Actions | Select-Object -First 1
  if ($oldAction.Execute -ne $exe -or $oldAction.Arguments -ne $argsLine) {
    throw "Same-named unrelated scheduled task detected; refusing overwrite."
  }
}
$registerArgs=@{
  TaskName=$taskName;Action=$action;Trigger=$trigger;Settings=$settings
  Principal=$principal
  Description="Local-only candidate sync report at Beijing 23:10; no WeChat or Agent access"
  Force=$true
}
Register-ScheduledTask @registerArgs | Out-Null
Write-Output "Enabled local candidate-sync digest: Beijing 23:10 (while signed in and PC is running)."
Write-Output "This report contains upload evidence only, NOT reviewed or completed task statuses."
