# 校序 · 整合验证（独立 QA）真机闪退诊断。只读，无 root，无网络上传。
# 用途：用户先在手机重现一次启动或导入课程时的闪退，再输出最小必要日志。
# 不执行 adb logcat -c、pm clear、uninstall、kill、重启，也不读取学校密码/Cookie。
param(
    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
$package = 'io.github.x07xh.xiaoxu.unifiedqa'
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw '电脑未找到 adb。请安装 Android SDK Platform Tools 并检查 PATH。'
}
$devices = @(& adb devices | Where-Object { $_ -match '^[^\s]+\s+device$' })
if ($devices.Count -ne 1) {
    & adb devices
    throw '请只连接一台手机，并确认已授权 USB 调试。'
}
$installed = @(& adb shell pm path $package)
if (-not (@($installed | Where-Object { $_ -like 'package:*' }).Count -gt 0)) {
    throw '手机没有安装独立 QA 包 io.github.x07xh.xiaoxu.unifiedqa。不会检查或修改正式版。'
}
$android = (& adb shell getprop ro.build.version.release | Out-String).Trim()
$sdk = (& adb shell getprop ro.build.version.sdk | Out-String).Trim()
$model = (& adb shell getprop ro.product.model | Out-String).Trim()
Write-Host ("已确认 QA 包；设备 " + $model + "，Android " + $android + " / API " + $sdk)
Write-Host '现在在手机上重现闪退（可在启动、切换页面或导入课表时发生）。'
Write-Host '此脚本不会主动启动、停止、卸载 App，也不会清理日志。'
[void](Read-Host '闪退发生后，请在电脑按 Enter 采集')

function ADBText {
    param([string[]]$Arguments)
    $result = & adb @Arguments 2>&1 | Out-String
    return $result
}
# 读取 Android 原生 crash 环形缓冲区；不清除历史信息。若没有 FATAL，也保留退出原因。
$crashes = (ADBText -Arguments @('logcat', '-d', '-b', 'crash', '-v', 'threadtime')) -split '\r?\n'
$system = (ADBText -Arguments @('logcat', '-d', '-t', '3000', '-b', 'main', '-b', 'system', '-v', 'threadtime')) -split '\r?\n'
$anchors = New-Object System.Collections.Generic.List[int]
for ($i = 0; $i -lt $crashes.Length; $i++) {
    if ($crashes[$i] -match ('(?:Process|Cmdline):\s*' + [regex]::Escape($package)) -or
        $crashes[$i] -match ([regex]::Escape($package) + '.*(?:FATAL EXCEPTION|Fatal signal)')) {
        $anchors.Add($i)
    }
}
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('--- Android crash buffer: QA process matches only ---')
$seen = @{}
foreach ($i in $anchors) {
    $from = [Math]::Max(0, $i - 7)
    $to = [Math]::Min($crashes.Length - 1, $i + 85)
    for ($j = $from; $j -le $to; $j++) {
        if (-not $seen.ContainsKey($j)) {
            $seen[$j] = $true
            $lines.Add($crashes[$j])
        }
    }
}
if ($anchors.Count -eq 0) {
    $lines.Add('未在 crash 缓冲区找到 QA 进程的 FATAL 记录。可查看以下运行时及退出状态线索。')
}
$lines.Add('--- Recent QA process / runtime / low-memory signals (redaction recommended) ---')
$signals = @($system | Where-Object {
    $_ -match [regex]::Escape($package) -or
    $_ -match '(?i)(AndroidRuntime.*FATAL EXCEPTION|lmkd.*Kill|ProcessRecord.*(died|kill))'
} | Select-Object -Last 120)
foreach ($line in $signals) { $lines.Add($line) }
$lines.Add('--- Process and exit diagnostics ---')
$qaProcessId = (ADBText -Arguments @('shell', 'pidof', $package)).Trim()
$lines.Add('Current process: ' + $(if ($qaProcessId) { $qaProcessId } else { 'not running' }))
try {
    $exitInfo = ADBText -Arguments @('shell', 'dumpsys', 'activity', 'exit-info', $package)
    foreach ($line in (($exitInfo -split '\r?\n') | Select-Object -First 100)) {
        $lines.Add($line)
    }
} catch {
    $lines.Add('Exit-info command unavailable on this device.')
}
$header = @(
    '校序 QA 闪退诊断（只读，未触及正式 App）'
    ('Device: ' + $model + ' | Android: ' + $android + ' | SDK: ' + $sdk)
    ('Package: ' + $package)
    ('Crash record anchors: ' + $anchors.Count)
    ('Collected at: ' + (Get-Date).ToString('s'))
    '提示：发送前检查日志中是否有个人信息；工具不会上传文件。'
    ''
)
if (-not $OutputDirectory) {
    $OutputDirectory = [Environment]::GetFolderPath('Desktop')
    if (-not $OutputDirectory) { $OutputDirectory = $PWD.Path }
}
if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
}
$dest = Join-Path $OutputDirectory '校序-QA-闪退诊断.txt'
@($header + $lines.ToArray()) | Set-Content -LiteralPath $dest -Encoding UTF8
Write-Host ('已保存：' + $dest)
Write-Host ('命中 ' + $anchors.Count + ' 组崩溃记录；如未命中，仍请提供退出状态信息。')
Write-Host '发送前请人工检查并遮盖私人信息。'
