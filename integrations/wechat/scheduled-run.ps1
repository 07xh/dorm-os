# 校序：北京时间窗口守卫。仅在 07:00–23:00 启动已有 Windows 微信同步器。
# 计划任务本身每 15 分钟触发；该守卫以 UTC+8 判断，不依赖电脑时区。
# check-time 只用于无副作用测试，绝不读取微信、设备凭据或云端。
[CmdletBinding()]
param(
  [Parameter(Position=0)]
  [ValidateSet("run","check-time")]
  [string]$Command="run",
  [string]$AtUtc
)
$ErrorActionPreference="Stop"
if ($AtUtc -and $Command -ne "check-time") {
  throw "仅 check-time 允许指定测试时间。"
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
$allowed=($minute -ge 7*60 -and $minute -le 23*60)
if ($Command -eq "check-time") {
  if ($allowed) { Write-Output "RUN" } else { Write-Output "SKIP" }
  exit 0
}
if (-not $allowed) {
  Write-Output "跳过：当前不在北京时间 07:00–23:00 同步窗口。"
  exit 0
}
$runner=Join-Path $PSScriptRoot "run.ps1"
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
  throw "未找到现有 Windows 微信采集启动脚本。"
}
& $runner sync
exit $LASTEXITCODE
