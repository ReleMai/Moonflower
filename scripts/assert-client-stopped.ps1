$ErrorActionPreference = "Stop"

function Test-HavenClientCommandLine {
    param([AllowNull()][string]$CommandLine)

    if ([string]::IsNullOrWhiteSpace($CommandLine)) {
        return $false
    }

    # The standalone launcher uses `java -jar`, while Steam's Haven Launcher
    # puts hafen.jar inside a quoted -classpath. Either process holds client
    # files open and must block a clean/package deployment.
    return $CommandLine -match '(?i)hafen\.jar'
}

$runningClients = Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -in @("java.exe", "javaw.exe") -and
        (Test-HavenClientCommandLine $_.CommandLine)
    }

if ($runningClients) {
    $processIds = ($runningClients.ProcessId | Sort-Object) -join ", "
    throw "MoonFlower is running (PID: $processIds). Close the client before rebuilding or refreshing packaged files. Replacing hafen.jar while Java is using it can kill the UI thread with NoClassDefFoundError and leave a frozen white window."
}

Write-Host "Client deployment check passed: no running hafen.jar process."
