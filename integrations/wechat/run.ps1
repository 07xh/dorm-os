# 在已通过本地验证的 PowerShell/Python 3.12 环境启动校序采集桥接。
# 不安装 Python/微信、不读取任何未选择的群、不保存明文设备凭据。
param(
 [Parameter(Position=0)]
 [ValidateSet("setup","preview","sync","status")]
 [string]$Command="status"
)
$ErrorActionPreference="Stop"
$homeCli=Join-Path $env:USERPROFILE ".wechat-cli"
$toolDir=Join-Path $homeCli "tool"
$depsX64=Join-Path $homeCli "deps_x64"
$deps=Join-Path $homeCli "deps"
if(!(Test-Path (Join-Path $toolDir "entry.py"))){
 Write-Error "未找到已安装的 Wechat-Todo；请先验证本机 wechat-cli 环境。"
 exit 1
}
if(!(Test-Path $depsX64)){
 Write-Error "未找到已安装的兼容 x64 依赖。请沿用之前验证通过的 Python 3.12 环境。"
 exit 1
}
$env:PYTHONPATH="$toolDir;$depsX64;$deps"
$pythonSelector="-V:Astral/CPython3.12.15"
# 用户之前亲自验证成功的 py launcher；不回退到默认 Python 3.15。
& py $pythonSelector (Join-Path $PSScriptRoot "collector.py") $Command
exit $LASTEXITCODE
