$ErrorActionPreference = "Stop"

function Test-HavenClientCommandLine {
    param([AllowNull()][string]$CommandLine)

    if ([string]::IsNullOrWhiteSpace($CommandLine)) {
        return $false
    }

    return $CommandLine -match '(?i)(?:^|\s)-jar\s+(?:"[^"]*hafen\.jar"|[^\s]*hafen\.jar)(?:\s|$)'
}

$runningClients = Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -in @("java.exe", "javaw.exe") -and
        (Test-HavenClientCommandLine $_.CommandLine)
    }

if ($runningClients) {
    $processIds = ($runningClients.ProcessId | Sort-Object) -join ", "
    throw "MoonFlower is running (PID: $processIds). Close the client before rebuilding client/bin. Replacing hafen.jar while Java is using it can kill the UI thread with NoClassDefFoundError and leave a frozen white window."
}

Write-Host "Client deployment check passed: no running hafen.jar process."
