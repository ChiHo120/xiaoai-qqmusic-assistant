param([int]$Port = 8080, [switch]$Install)
$ErrorActionPreference = "Stop"
$serverRoot = $PSScriptRoot
$pythonExe = Join-Path $serverRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $pythonExe)) {
    & uv venv (Join-Path $serverRoot ".venv") --python 3.12
    if ($LASTEXITCODE -ne 0) { throw "Python 环境创建失败，请先安装 uv" }
    $Install = $true
}
if ($Install) {
    & uv pip install --python $pythonExe (Join-Path $serverRoot "upstream") -r (Join-Path $serverRoot "requirements.txt")
    if ($LASTEXITCODE -ne 0) { throw "依赖安装失败" }
}
$env:QQMUSIC_SERVER_HOST = "0.0.0.0"
$env:QQMUSIC_SERVER_PORT = "$Port"
Push-Location (Join-Path $serverRoot "upstream")
try { & $pythonExe web/run.py } finally { Pop-Location }
