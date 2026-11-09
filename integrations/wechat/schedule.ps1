# 校序微信同步：用户主动开启/关闭 Windows 定时任务；不包含凭据或聊天内容。
# .\integrations\wechat\schedule.ps1 status|install|remove
# 安装任务只在本人手动确认成功同步后执行，保留原 DPAPI 设备凭据。
# 首次必须先在校序绑定设备，然后手动 preview 和 sync 验证成功。
[CmdletBinding()]
param(
  [Parameter(Position=0)]
  [ValidateSet("status","install","remove")]
  [string]$Command="status"
)
$ErrorActionPreference="Stop"
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
  throw "此脚本仅支持 Windows 任务计划程序。"
}
Import-Module ScheduledTasks -ErrorAction Stop
$taskName="Xiaoxu WeChat Personal Tasks"
$existing=Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($Command -eq "status") {
  if ($null -eq $existing) {
    Write-Output "未启用自动同步；现有手动同步功能不受影响。"
  } else {
    $actionText=($existing.Actions | Select-Object -First 1).Arguments
    $scope=if ($actionText -like "*scheduled-run.ps1*") { "北京时间 07:00–23:00" } else { "旧版全天时段或外部任务（需要核对）" }
    Write-Output ("定时任务状态：" + $existing.State + "。每 15 分钟触发；采集时段：" + $scope + "。仅本人登录且电脑运行时生效。")
    $info=Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction SilentlyContinue
    if ($null -ne $info) {
      Write-Output ("最近计划任务结果：" + $info.LastTaskResult + "；上次运行：" + $info.LastRunTime + "；下次触发：" + $info.NextRunTime)
    }
  }
  exit 0
}
if ($Command -eq "remove") {
  if ($null -eq $existing) {
    Write-Output "没有已安装的微信自动同步任务。"
    exit 0
  }
  Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction Stop
  Write-Output "已关闭自动同步。没有删除本机配置和已审核的校序任务；可在校序网页撤销设备授权。"
  exit 0
}

$runner=Join-Path $PSScriptRoot "run.ps1"
$windowRunner=Join-Path $PSScriptRoot "scheduled-run.ps1"
$config=Join-Path $env:USERPROFILE ".wechat-cli\xiaoxu-bridge.json"
$state=Join-Path $env:USERPROFILE ".wechat-cli\xiaoxu-sync-state.json"
if (-not (Test-Path -LiteralPath $runner -PathType Leaf) -or
    -not (Test-Path -LiteralPath $windowRunner -PathType Leaf) -or
    -not (Test-Path -LiteralPath $config -PathType Leaf)) {
  throw "请先在校序绑定电脑，并执行 run.ps1 setup。"
}
if (-not (Test-Path -LiteralPath $state -PathType Leaf)) {
  throw "请先手动执行 run.ps1 preview 和 run.ps1 sync，确认真实同步成功后再开启定时任务。"
}
if ($runner.Contains('"') -or $windowRunner.Contains('"')) {
  throw "工作目录包含不受支持的引号字符。"
}
# 此命令只包含脚本路径，设备凭据使用现有 Python 采集器的 Windows DPAPI。
$exe=Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
$argsLine='-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -File "' + $windowRunner + '" run'
$legacyArgsLine='-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -File "' + $runner + '" sync'
$action=New-ScheduledTaskAction -Execute $exe -Argument $argsLine -WorkingDirectory $PSScriptRoot
$trigger=New-ScheduledTaskTrigger -Daily -At "00:00"
$repeat=New-CimInstance -ClientOnly -Namespace "root/Microsoft/Windows/TaskScheduler" -ClassName "MSFT_TaskRepetitionPattern" -Property @{Interval="PT15M";Duration="P1D";StopAtDurationEnd=$false}
$trigger.Repetition=$repeat
$settings=New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 5) -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$currentUser=[Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal=New-ScheduledTaskPrincipal -UserId $currentUser -LogonType Interactive -RunLevel Limited
if ($null -ne $existing) {
  $oldAction=$existing.Actions | Select-Object -First 1
  # Never silently replace another task with the same name and a different action.
  if ($oldAction.Execute -ne $exe -or
      ($oldAction.Arguments -ne $argsLine -and $oldAction.Arguments -ne $legacyArgsLine)) {
    throw "检测到同名但内容不同的定时任务；未覆盖。请手动核对后再处理。"
  }
}
$registerArgs=@{
  TaskName=$taskName
  Action=$action
  Trigger=$trigger
  Settings=$settings
  Principal=$principal
  Description="北京时间07:00–23:00每15分钟尝试校序微信同步（仅在电脑登录时）"
  Force=$true
}
Register-ScheduledTask @registerArgs | Out-Null
Write-Output "已安装北京时间 07:00–23:00 每 15 分钟同步守卫。计划任务按本机时间每15分钟触发，窗口外不读取微信。"
Write-Output "只在当前 Windows 用户登录且电脑运行时读取；电脑关机/休眠期间不采集，醒来会按游标尝试补读。"
Write-Output "停止命令：.\integrations\wechat\schedule.ps1 remove"
