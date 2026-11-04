# 校序微信候选任务：可选 Windows 用户级自动同步（不需常驻新程序）。
# 仅操作当前用户的同名任务；不会自动开启，也不包含任何密钥或群聊内容。
# 用法： .\auto-sync.ps1 enable | disable | status
param(
 [Parameter(Position=0)]
 [ValidateSet("enable", "disable", "status")]
 [string]$Action="status"
)
$ErrorActionPreference="Stop"
$taskName="Xiaoxu-Wechat-Candidates"
$runner=Join-Path $PSScriptRoot "run.ps1"
$config=Join-Path $env:USERPROFILE ".wechat-cli\xiaoxu-bridge.json"
$checkpoint=Join-Path $env:USERPROFILE ".wechat-cli\xiaoxu-sync-state.json"

if($env:OS -ne "Windows_NT"){
 throw "自动同步配置仅支持 Windows。"
}
$existing=Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if($Action -eq "status"){
 if(!$existing){
  Write-Host "校序微信自动同步：未启用。手动同步不受影响。"
  exit 0
 }
 $info=Get-ScheduledTaskInfo -TaskName $taskName
 Write-Host "校序微信自动同步：已启用（用户登录期间，每30分钟尝试一次）。"
 Write-Host ("计划状态："+$existing.State+"；上次结果代码："+$info.LastTaskResult)
 exit 0
}
if($Action -eq "disable"){
 if(!$existing){
  Write-Host "校序微信自动同步：本来就未启用。"
  exit 0
 }
 # Never delete other tasks: this fixed name is the only one we create.
 $expected=($existing.Actions | Where-Object {
  $_.Arguments -and $_.Arguments.Contains($runner) -and $_.Arguments.Contains("sync")
 }).Count -ge 1
 if(!$expected){
  throw "检测到同名但非校序创建的任务，拒绝删除。请自行在任务计划程序中检查。"
 }
 Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
 Write-Host "校序微信自动同步已关闭。云端已有待办不会被删除。"
 exit 0
}
if($existing){
 throw "同名任务已经存在，请先运行 status。为保护现有任务，不会强制覆盖。"
}
if(!(Test-Path $runner) -or !(Test-Path $config) -or !(Test-Path $checkpoint)){
 throw "请先完成校序设备绑定、电脑 setup 和手动 sync 验证，再启用自动同步。"
}
$identity=[System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal=New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Limited
$executable=Join-Path $env:WINDIR "System32\WindowsPowerShell\v1.0\powershell.exe"
$arguments='-NoLogo -NoProfile -NonInteractive -File "'+$runner+'" sync'
$action=New-ScheduledTaskAction -Execute $executable -Argument $arguments -WorkingDirectory $PSScriptRoot
# Active only while the user's Windows session is signed in. No wake-up and
# no device password storage, no bypassing login or disabled-session policy.
$trigger=New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2) -RepetitionInterval (New-TimeSpan -Minutes 30) -RepetitionDuration (New-TimeSpan -Days 365)
$settings=New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 10)
Register-ScheduledTask -TaskName $taskName -TaskPath "\" -Action $action -Trigger $trigger -Settings $settings -Principal $principal | Out-Null
Write-Host "校序微信自动同步已启用：每30分钟尝试，需 Windows 用户处于登录状态。"
Write-Host "电脑关机/休眠不会同步；使用 .\auto-sync.ps1 disable 随时关闭。"
