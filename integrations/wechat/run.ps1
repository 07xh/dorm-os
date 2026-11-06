# 校序本机微信采集：使用已验证的 Python 3.12 虚拟环境，不依赖系统默认 Python。
# 仅加载微信工具源码；三方依赖从 py312-venv 读取，不再混用捆绑 deps。
param(
 [Parameter(Position=0)]
 [ValidateSet("setup","preview","sync","status","verify")]
 [string]$Command="status"
)
$ErrorActionPreference="Stop"
$homeCli=Join-Path $env:USERPROFILE ".wechat-cli"
$toolDir=Join-Path $homeCli "tool"
$python=Join-Path (Join-Path $homeCli "py312-venv") "Scripts\python.exe"
$collector=Join-Path $PSScriptRoot "collector.py"
if(!(Test-Path -LiteralPath (Join-Path $toolDir "entry.py") -PathType Leaf)){
 Write-Error "未找到微信读取工具。请先安装 wechat-cli-plus。"
 exit 1
}
if(!(Test-Path -LiteralPath $python -PathType Leaf)){
 Write-Error "未找到 py312-venv 虚拟环境。请先创建 Python 3.12 环境并安装 click、pycryptodome、zstandard。"
 exit 1
}
if(!(Test-Path -LiteralPath $collector -PathType Leaf)){
 Write-Error "未找到校序 collector.py；请保持三个微信桥接脚本在同一目录。"
 exit 1
}
# 覆盖父进程的旧 PYTHONPATH，确保不会加载 deps 中不匹配的原生扩展。
$env:PYTHONPATH=$toolDir
& $python $collector $Command
exit $LASTEXITCODE
