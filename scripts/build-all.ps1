$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

function Stop-PackagedServer {
    $processes = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -like "*server-0.1.0-SNAPSHOT.jar*" }
    foreach ($process in $processes) {
        Stop-Process -Id $process.ProcessId -Force
        Write-Host "Stopped running packaged server PID $($process.ProcessId) so the jar can be rebuilt."
    }
}

Push-Location $root
try {
    Stop-PackagedServer

    Write-Host "Building web..."
    Push-Location (Join-Path $root "web")
    try {
        npm run build
    } finally {
        Pop-Location
    }

    Write-Host "Packaging server..."
    mvn -pl server -am package

    Write-Host "Packaging client..."
    Push-Location (Join-Path $root "client")
    try {
        & "C:\apache-ant\bin\ant.bat" deftgt
    } finally {
        Pop-Location
    }

    Write-Host "Preparing WebRTC media gateway..."
    & (Join-Path $PSScriptRoot "setup-media-gateway.ps1")
} finally {
    Pop-Location
}
