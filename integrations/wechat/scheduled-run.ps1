# 校序：单一计划任务的时间分流守卫（UTC+8，不依赖电脑时区）。
# 主任务有两个互斥触发器：07:00 起每 15 分钟采集、23:10 单次日报。
# 07:00–23:00 执行 sync（失败本地安全重试 3 次 / 间隔 20 秒并留痕）；
# 23:10–23:59 只生成本地日报，不读微信、不联网、不推进游标。
# 其余时间直接跳过，因此全天任何时刻最多只有一个任务实例在干活。
# check-time / check-mode 只用于无副作用测试，绝不读取微信、设备凭据或云端。
[CmdletBinding()]
param(
  [Parameter(Position=0)]
  [ValidateSet("run","check-time","check-mode")]
  [string]$Command="run",
  [string]$AtUtc
)
$ErrorActionPreference="Stop"
if ($AtUtc -and $Command -ne "check-time" -and $Command -ne "check-mode") {
  throw "仅 check-time / check-mode 允许指定测试时间。"
}
if ($AtUtc) {
  $parsed=[datetimeoffset]::MinValue
  if (-not [datetimeoffset]::TryParseExact(
    $AtUtc, "yyyy-MM-ddTHH:mm:ssZ",
    [System.Globalization.CultureInfo]::InvariantCulture,
    [System.Globalization.DateTimeStyles]::AssumeUniversal,
    [ref]$parsed)) { throw "测试时间必须是带 Z 的完整 UTC 时间。" }
  $utc=$parsed.ToUniversalTime()
} else {
  $utc=[datetimeoffset]::UtcNow
}
$beijing=$utc.ToOffset([TimeSpan]::FromHours(8))
$minute=$beijing.Hour*60+$beijing.Minute
# 首次触发 07:00，最后触发 23:00。23:01 起拒绝补运行。
$syncAllowed=($minute -ge 7*60 -and $minute -le 23*60)
$digestAllowed=($minute -ge (23*60+10) -and $minute -le (23*60+59))
if ($Command -eq "check-time") {
  if ($syncAllowed) { Write-Output "RUN" } else { Write-Output "SKIP" }
  exit 0
}
if ($Command -eq "check-mode") {
  if ($digestAllowed) { Write-Output "DIGEST" }
  elseif ($syncAllowed) { Write-Output "SYNC" }
  else { Write-Output "SKIP" }
  exit 0
}
if (-not $syncAllowed -and -not $digestAllowed) {
  Write-Output "跳过：当前不在北京时间 07:00–23:00 同步窗口或 23:10–23:59 日报窗口。"
  exit 0
}
$runner=Join-Path $PSScriptRoot "run.ps1"
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
  throw "未找到现有 Windows 微信采集启动脚本。"
}
# 每次尝试都在独立子进程里跑，否则 run.ps1 内的 exit 会终止当前宿主进程，
# 调度层拿不到退出码也无法重试。重试只在失败后进行：成功的 sync 立即退出，
# 不会重复处理；游标与候选去重仍由 collector 负责。
$psExe=Join-Path $env:WINDIR "System32\WindowsPowerShell\v1.0\powershell.exe"
if (-not (Test-Path -LiteralPath $psExe -PathType Leaf)) { $psExe="powershell.exe" }
$maxAttempts=3
if ($env:XIAOXU_MAX_ATTEMPTS) { $maxAttempts=[int]$env:XIAOXU_MAX_ATTEMPTS }
# 短间隔：让整轮重试在网络超时场景下也能在 1-2 分钟内结束，
# 避免长时间挂起的任务实例被电源/会话策略终止；长期补跑交给 15 分钟触发。
$delaySec=20
if ($env:XIAOXU_RETRY_DELAY_SEC) { $delaySec=[int]$env:XIAOXU_RETRY_DELAY_SEC }
function Write-SchedulerLog([int]$Attempt,[int]$Code,[string]$Message,[string]$CommandName="sync") {
  try {
    $dir=Join-Path $env:USERPROFILE ".wechat-cli"
    if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
      New-Item -ItemType Directory -Path $dir | Out-Null
    }
    $entry=[ordered]@{
      at=[datetimeoffset]::UtcNow.ToOffset([TimeSpan]::FromHours(8)).ToString("yyyy-MM-ddTHH:mm:sszzz")
      source="scheduler"
      command=$CommandName
      attempt=$Attempt
      exitCode=$Code
      error=$Message
    }
    Add-Content -LiteralPath (Join-Path $dir "xiaoxu-run-log.jsonl") `
      -Value ($entry | ConvertTo-Json -Compress) -Encoding UTF8
  } catch {
    # 记录失败不得反过来阻断同步；collector 自身也写同类失败证据。
  }
}
if ($digestAllowed) {
  # 23:10 触发器走这里。sync_digest.py 自带同一把单实例锁与 open("x") 幂等，
  # 重复触发既不会与采集中突，也不会重写已生成的报告。
  $python=Join-Path $env:USERPROFILE ".wechat-cli\py312-venv\Scripts\python.exe"
  $digest=Join-Path $PSScriptRoot "sync_digest.py"
  if (-not (Test-Path -LiteralPath $python -PathType Leaf) -or
      -not (Test-Path -LiteralPath $digest -PathType Leaf)) {
    throw "本地 Python 3.12 环境或日报脚本不可用。"
  }
  & $python $digest render
  $code=$LASTEXITCODE
  if ($code -ne 0) {
    Write-SchedulerLog 1 $code "日报生成失败：未写入任何报告" "digest"
    exit 1
  }
  exit 0
}
for ($attempt=1; $attempt -le $maxAttempts; $attempt++) {
  & $psExe -NoLogo -NoProfile -NonInteractive -File $runner sync
  $code=$LASTEXITCODE
  if ($code -eq 0) { exit 0 }
  Write-SchedulerLog $attempt $code "同步失败：准备安全重试（不推进游标、不重复处理已成功消息）"
  if ($attempt -lt $maxAttempts -and $delaySec -gt 0) { Start-Sleep -Seconds $delaySec }
}
Write-SchedulerLog $maxAttempts 1 "同步连续失败：已用尽本地重试，等待下一次 15 分钟触发补跑"
exit 1
