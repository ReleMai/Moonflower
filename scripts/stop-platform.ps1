$ErrorActionPreference = "Stop"

$processes = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -like "*server-0.1.0-SNAPSHOT.jar*" }
$gatewayProcesses = Get-CimInstance Win32_Process |
    Where-Object { $_.CommandLine -like "*media-gateway*app.py*" }

if (-not $processes) {
    Write-Host "No Haven bot server process found."
}

foreach ($process in $processes) {
    Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Host "Stopped server PID $($process.ProcessId)."
}

if (-not $gatewayProcesses) {
    Write-Host "No media gateway process found."
    exit 0
}

foreach ($process in $gatewayProcesses) {
    Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
    Write-Host "Stopped media gateway PID $($process.ProcessId)."
}
