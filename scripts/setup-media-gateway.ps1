$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$gatewayDir = Join-Path $root "media-gateway"
$venvPython = Join-Path $gatewayDir ".venv\\Scripts\\python.exe"
$requirements = Join-Path $gatewayDir "requirements.txt"

if (-not (Test-Path $requirements)) {
    throw "Media gateway requirements not found at $requirements"
}

if (-not (Test-Path $venvPython)) {
    Write-Host "Creating media gateway virtual environment..."
    py -3.13 -m venv (Join-Path $gatewayDir ".venv")
}

Write-Host "Installing media gateway dependencies..."
& $venvPython -m pip install -r $requirements
