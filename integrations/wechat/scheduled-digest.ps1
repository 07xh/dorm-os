# Daily local-only digest of ALREADY acknowledged WeChat candidate uploads.
# A 5-minute Windows trigger makes the 23:10 Beijing slot independent of PC timezone.
# Do not read WeChat, invoke an Agent, upload messages, or edit any sync cursor.
[CmdletBinding()]
param(
  [Parameter(Position=0)]
  [ValidateSet("run","check-time")]
  [string]$Command="run",
  [string]$AtUtc
)
$ErrorActionPreference="Stop"
if ($AtUtc -and $Command -ne "check-time") {
  throw "Only check-time accepts a test clock."
}
if ($AtUtc) {
  $parsed=[datetimeoffset]::MinValue
  if (-not [datetimeoffset]::TryParseExact(
    $AtUtc, "yyyy-MM-ddTHH:mm:ssZ",
    [System.Globalization.CultureInfo]::InvariantCulture,
    [System.Globalization.DateTimeStyles]::AssumeUniversal,
    [ref]$parsed)) { throw "The test clock must be a full UTC time ending in Z." }
  $utc=$parsed.ToUniversalTime()
} else {
  $utc=[datetimeoffset]::UtcNow
}
$beijing=$utc.ToOffset([TimeSpan]::FromHours(8))
$minute=$beijing.Hour*60+$beijing.Minute
# 23:10 local Beijing is the earliest allowed report time; late same-day
# retries may render an absent file. Never generate the next day's report.
$allowed=($minute -ge (23*60+10) -and $minute -le (23*60+59))
if ($Command -eq "check-time") {
  if ($allowed) { Write-Output "RUN" } else { Write-Output "SKIP" }
  exit 0
}
if (-not $allowed) { exit 0 }
$python=Join-Path $env:USERPROFILE ".wechat-cli\py312-venv\Scripts\python.exe"
$digest=Join-Path $PSScriptRoot "sync_digest.py"
if (-not (Test-Path -LiteralPath $python -PathType Leaf) -or
    -not (Test-Path -LiteralPath $digest -PathType Leaf)) {
  throw "The installed Python 3.12 environment or local digest script is unavailable."
}
& $python $digest render
exit $LASTEXITCODE
