$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

function Resolve-AntCommand {
    foreach ($name in @("ant.bat", "ant")) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) {
            return $command.Source
        }
    }

    $fallback = "C:\apache-ant\bin\ant.bat"
    if (Test-Path -LiteralPath $fallback) {
        return $fallback
    }

    throw "Apache Ant was not found on PATH or at $fallback. Install Ant and add its bin directory to PATH."
}

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
    & (Join-Path $PSScriptRoot "assert-client-stopped.ps1")
    Stop-PackagedServer

    Write-Host "Building web..."
    Push-Location (Join-Path $root "web")
    try {
        npm run lint
        npm run build
    } finally {
        Pop-Location
    }

    Write-Host "Packaging server..."
    mvn -pl server -am package

    Write-Host "Packaging client..."
    Push-Location (Join-Path $root "client")
    try {
        $antCommand = Resolve-AntCommand
        & $antCommand deftgt
    } finally {
        Pop-Location
    }

    Write-Host "Preparing WebRTC media gateway..."
    & (Join-Path $PSScriptRoot "setup-media-gateway.ps1")
} finally {
    Pop-Location
}
