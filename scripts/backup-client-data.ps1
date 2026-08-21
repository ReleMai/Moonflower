param(
    [string]$DestinationRoot
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($DestinationRoot)) {
    $DestinationRoot = Join-Path $root ".recovery\client-data-$stamp"
}
$destination = [System.IO.Path]::GetFullPath($DestinationRoot)
$workspace = [System.IO.Path]::GetFullPath($root)
if ($destination -eq $workspace) {
    throw "The backup destination cannot be the workspace root."
}
if (Test-Path -LiteralPath $destination) {
    $existing = Get-ChildItem -LiteralPath $destination -Force
    if ($existing.Count -gt 0) {
        throw "The backup destination must be empty: $destination"
    }
}

New-Item -ItemType Directory -Force -Path $destination | Out-Null

$sources = [System.Collections.Generic.List[object]]::new()
$appData = [Environment]::GetFolderPath([Environment+SpecialFolder]::ApplicationData)
if (-not [string]::IsNullOrWhiteSpace($appData)) {
    $havenData = [System.IO.Path]::GetFullPath((Join-Path $appData "Haven and Hearth"))
    $havenPrefix = $havenData.TrimEnd('\') + '\'
    if ($destination -eq $havenData -or $destination.StartsWith($havenPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "The backup destination cannot be inside the source data directory: $havenData"
    }
    if (Test-Path -LiteralPath $havenData) {
        $sources.Add([pscustomobject]@{ Path = $havenData; Name = "appdata-haven-and-hearth" })
    }
}

foreach ($relative in @(
    "client\static_data.db",
    "client\hitboxes.db",
    "client\saved_routes.db",
    "client\bin\static_data.db",
    "client\bin\hitboxes.db",
    "client\bin\saved_routes.db"
)) {
    $path = Join-Path $root $relative
    if (Test-Path -LiteralPath $path) {
        $safeName = $relative.Replace("\", "-")
        $sources.Add([pscustomobject]@{ Path = $path; Name = "legacy-$safeName" })
    }
}

if ($sources.Count -eq 0) {
    Write-Host "No existing Hurricane client data was found. Created empty backup folder $destination"
    exit 0
}

foreach ($source in $sources) {
    $target = Join-Path $destination $source.Name
    Copy-Item -LiteralPath $source.Path -Destination $target -Recurse -Force
    Write-Host "Backed up $($source.Path)"
}

Write-Host "Client data backup complete: $destination"
