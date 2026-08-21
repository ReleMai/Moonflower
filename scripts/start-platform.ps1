param(
    [switch]$SkipBuild,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root "server\target\server-0.1.0-SNAPSHOT.jar"
$stdoutLog = Join-Path $root "server-run.log"
$stderrLog = Join-Path $root "server-run.err.log"
$runtimeDir = Join-Path $root "artifacts\runtime"
$gatewayDir = Join-Path $root "media-gateway"
$gatewayPython = Join-Path $gatewayDir ".venv\Scripts\python.exe"
$gatewayApp = Join-Path $gatewayDir "app.py"
$gatewayStdoutLog = Join-Path $runtimeDir "media-gateway.log"
$gatewayStderrLog = Join-Path $runtimeDir "media-gateway.err.log"
$gatewayUrl = "http://127.0.0.1:8091/health"
$dashboardUrl = "http://127.0.0.1:8080/"

function Get-ServerProcess {
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -like "*server-0.1.0-SNAPSHOT.jar*" }
}

function Get-GatewayProcess {
    Get-CimInstance Win32_Process |
        Where-Object { $_.CommandLine -like "*media-gateway*app.py*" }
}

if (-not $SkipBuild) {
    $existingBeforeBuild = Get-ServerProcess
    foreach ($process in $existingBeforeBuild) {
        Stop-Process -Id $process.ProcessId -Force
        Write-Host "Stopped running server PID $($process.ProcessId) before rebuild."
    }
    & (Join-Path $PSScriptRoot "build-all.ps1")
}

if (-not (Test-Path $jarPath)) {
    throw "Server jar not found at $jarPath"
}

if (-not (Test-Path $gatewayPython)) {
    throw "Media gateway Python executable not found at $gatewayPython"
}

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

$existing = Get-ServerProcess
if ($existing) {
    Write-Host "Server already running."
} else {
    $process = Start-Process `
        -FilePath "java" `
        -ArgumentList "-jar `"$jarPath`"" `
        -WorkingDirectory $root `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -WindowStyle Hidden `
        -PassThru
    Write-Host "Started server PID $($process.Id)."
}

$deadline = (Get-Date).AddSeconds(30)
do {
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/health" -TimeoutSec 2
        if ($response.status -eq "ok") {
            Write-Host "Dashboard ready at $dashboardUrl"
            break
        }
    } catch {
        Start-Sleep -Milliseconds 500
    }
} while ((Get-Date) -lt $deadline)

if ((Get-Date) -ge $deadline) {
    throw "Server did not become healthy within 30 seconds. Check $stdoutLog and $stderrLog."
}

$existingGateway = Get-GatewayProcess
if ($existingGateway) {
    Write-Host "Media gateway already running."
} else {
    $gatewayProcess = Start-Process `
        -FilePath $gatewayPython `
        -ArgumentList "`"$gatewayApp`"" `
        -WorkingDirectory $gatewayDir `
        -RedirectStandardOutput $gatewayStdoutLog `
        -RedirectStandardError $gatewayStderrLog `
        -WindowStyle Hidden `
        -PassThru
    Write-Host "Started media gateway PID $($gatewayProcess.Id)."
}

$gatewayDeadline = (Get-Date).AddSeconds(20)
do {
    try {
        $response = Invoke-RestMethod -Uri $gatewayUrl -TimeoutSec 2
        if ($response.status -eq "ok") {
            Write-Host "WebRTC gateway ready."
            if (-not $NoBrowser) {
                Start-Process $dashboardUrl | Out-Null
            }
            exit 0
        }
    } catch {
        Start-Sleep -Milliseconds 500
    }
} while ((Get-Date) -lt $gatewayDeadline)

throw "Media gateway did not become healthy within 20 seconds. Check $gatewayStdoutLog and $gatewayStderrLog."
