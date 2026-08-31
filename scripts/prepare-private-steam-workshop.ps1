[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipChecks,
    [string]$StagePath
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$clientRoot = Join-Path $repoRoot "client"
$binRoot = Join-Path $clientRoot "bin"
$privateRoot = Join-Path (Join-Path $repoRoot ".recovery") "steam-workshop"
$localWorkshopIdPath = Join-Path $privateRoot "workshop-id.txt"

if ([string]::IsNullOrWhiteSpace($StagePath)) {
    $StagePath = Join-Path $privateRoot "package"
}

$privateRoot = [System.IO.Path]::GetFullPath($privateRoot)
$StagePath = [System.IO.Path]::GetFullPath($StagePath)
$privatePrefix = $privateRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
if (($StagePath -eq $privateRoot) -or
    -not $StagePath.StartsWith($privatePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The staging directory must be a child of $privateRoot. Resolved path: $StagePath"
}

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

    throw "Apache Ant was not found on PATH or at $fallback."
}

function Assert-NativeSuccess {
    param([string]$Description)

    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

function Invoke-ClientCheck {
    param(
        [string]$Description,
        [string[]]$JavaArguments
    )

    Write-Host $Description
    & java @JavaArguments
    Assert-NativeSuccess $Description
}

function Copy-RequiredFile {
    param(
        [string]$RelativePath,
        [switch]$Optional
    )

    $source = Join-Path $binRoot $RelativePath
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        if ($Optional) {
            return
        }
        throw "Required packaged file is missing: $source"
    }

    $destination = Join-Path $StagePath $RelativePath
    $parent = Split-Path -Parent $destination
    if ($parent) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    Copy-Item -LiteralPath $source -Destination $destination -Force
}

function Get-PropertyValue {
    param(
        [string]$Path,
        [string]$Name
    )

    $prefix = "$Name="
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line.StartsWith($prefix, [System.StringComparison]::Ordinal)) {
            return $line.Substring($prefix.Length).Trim()
        }
    }
    return $null
}

if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot "assert-client-stopped.ps1")

    Push-Location $clientRoot
    try {
        Write-Host "Building a clean MoonFlower client package..."
        & (Resolve-AntCommand) clean deftgt
        Assert-NativeSuccess "Clean Ant client package"
    } finally {
        Pop-Location
    }
} else {
    Write-Warning "Skipping the build. The staged package may not contain current source changes."
}

if (-not (Test-Path -LiteralPath (Join-Path $binRoot "hafen.jar") -PathType Leaf)) {
    throw "client/bin/hafen.jar is missing. Close MoonFlower and run this script without -SkipBuild."
}

$jarEntries = @(& jar tf (Join-Path $binRoot "hafen.jar"))
Assert-NativeSuccess "Inspect packaged hafen.jar"
$forbiddenJarEntries = $jarEntries | Where-Object {
    $_ -match '^haven/botcontrol/' -or
    $_ -match '^haven/automated/mapper/' -or
    $_ -match '^haven/automated/cookbook/' -or
    $_ -eq 'haven/GitHubVersionFetcher.class'
}
if ($forbiddenJarEntries) {
    throw "Web/operator or credential-saving classes remain in hafen.jar: $($forbiddenJarEntries -join ', ')"
}

if (-not $SkipChecks) {
    Push-Location $clientRoot
    try {
        Invoke-ClientCheck "MoonFlower identity checks" @("-cp", "bin/*", "haven.MoonFlowerChecks")
        Invoke-ClientCheck "Inventory slot-lock checks" @("-Dhaven.uiscale=1", "-cp", "bin/*", "haven.InventorySlotLockChecks")
        Invoke-ClientCheck "Container window placement checks" @("-Dhaven.uiscale=1", "-cp", "bin/*", "haven.ContainerWindowPlacementChecks")
        Invoke-ClientCheck "Cookbook checks" @("-Dhaven.uiscale=1", "-cp", "bin/*", "haven.cookbook.CookbookChecks")
        Invoke-ClientCheck "Fishing checks" @("-Dhaven.uiscale=1", "-cp", "bin/*", "haven.fishing.FishingChecks")
        Invoke-ClientCheck "Feasting checks" @("-Dhaven.uiscale=1", "-cp", "bin/*", "haven.feasting.FeastingChecks")
        Invoke-ClientCheck "Combat assist checks" @("-Dhaven.uiscale=1", "-cp", "bin/*", "haven.combat.CombatAssistChecks")
        Invoke-ClientCheck "Ring of Brodgar wiki checks" @("-Dhaven.uiscale=1", "-cp", "bin/*", "haven.wiki.WikiChecks")
        Invoke-ClientCheck "Resource update checks" @("-cp", "bin/*", "haven.Resource", "find-updates")
    } finally {
        Pop-Location
    }
} else {
    Write-Warning "Skipping packaged client checks. This output is not ready to upload."
}

if (Test-Path -LiteralPath $StagePath) {
    # StagePath was resolved and constrained to .recovery/steam-workshop above.
    Remove-Item -LiteralPath $StagePath -Recurse -Force
}
New-Item -ItemType Directory -Path $StagePath -Force | Out-Null

$requiredFiles = @(
    "builtin-res.jar",
    "gluegen-rt-natives-linux-aarch64.jar",
    "gluegen-rt-natives-linux-amd64.jar",
    "gluegen-rt-natives-linux-armv6hf.jar",
    "gluegen-rt-natives-linux-i586.jar",
    "gluegen-rt-natives-macosx-universal.jar",
    "gluegen-rt-natives-windows-amd64.jar",
    "gluegen-rt-natives-windows-i586.jar",
    "gluegen-rt.jar",
    "hafen-res.jar",
    "hafen.hl",
    "hafen.jar",
    "haven-config.properties",
    "hitboxes.db",
    "jogl-all-natives-linux-aarch64.jar",
    "jogl-all-natives-linux-amd64.jar",
    "jogl-all-natives-linux-armv6hf.jar",
    "jogl-all-natives-linux-i586.jar",
    "jogl-all-natives-macosx-universal.jar",
    "jogl-all-natives-windows-amd64.jar",
    "jogl-all-natives-windows-i586.jar",
    "jogl-all.jar",
    "launcher.hl",
    "lwjgl-awt.jar",
    "lwjgl-fat.jar",
    "lwjgl-opengl-fat.jar",
    "MoonFlower-Update.ps1",
    "Play.bat",
    "Play_Linux.sh",
    "Play_WithSteam.bat",
    "sqlite-jdbc-3.42.0.0.jar",
    "static_data.db",
    "steam_appid.txt",
    "steam-client-image.jpg",
    "steam-client-image.gif",
    "steam-banner.jpg",
    "workshop-description.txt",
    "steamworks4j-natives-linux-amd64.jar",
    "steamworks4j-natives-macosx-universal.jar",
    "steamworks4j-natives-windows-amd64.jar",
    "steamworks4j-natives-windows-i586.jar",
    "steamworks4j.jar"
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($file in $requiredFiles | Where-Object { $_ -like "*.jar" }) {
    $source = Join-Path $binRoot $file
    try {
        $archive = [System.IO.Compression.ZipFile]::OpenRead($source)
        $entryCount = $archive.Entries.Count
        $archive.Dispose()
        if ($entryCount -lt 1) {
            throw "archive has no entries"
        }
    } catch {
        throw "Packaged archive is not a readable ZIP/JAR: $source ($($_.Exception.Message))"
    }
}

foreach ($file in $requiredFiles) {
    Copy-RequiredFile $file
}
Copy-RequiredFile "hafen-panama.jar" -Optional

foreach ($directory in @("AlarmSounds", "MapIconsPresets", "midiFiles", "res")) {
    $source = Join-Path $binRoot $directory
    if (-not (Test-Path -LiteralPath $source -PathType Container)) {
        throw "Required packaged directory is missing: $source"
    }
    Copy-Item -LiteralPath $source -Destination (Join-Path $StagePath $directory) -Recurse -Force
}

$metadataSource = Join-Path $clientRoot "workshop-client.properties"
$metadataDestination = Join-Path $StagePath "workshop-client.properties"
Copy-Item -LiteralPath $metadataSource -Destination $metadataDestination -Force

$visibility = Get-PropertyValue $metadataDestination "visibility"
if ($visibility -ne "private") {
    throw "Workshop metadata must use owner-only private visibility; found: $visibility"
}
if ((Get-PropertyValue $metadataDestination "workshop-id") -ne $null) {
    throw "Tracked Workshop metadata must not contain a workshop-id. Use ignored local state for updates."
}

if (Test-Path -LiteralPath $localWorkshopIdPath -PathType Leaf) {
    $localWorkshopId = (Get-Content -LiteralPath $localWorkshopIdPath -Raw).Trim()
    if ($localWorkshopId -notmatch '^\d+$' -or $localWorkshopId -eq "3423755273") {
        throw "Invalid or inherited local Workshop ID in $localWorkshopIdPath."
    }
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::AppendAllText(
        $metadataDestination,
        [Environment]::NewLine + "workshop-id=$localWorkshopId" + [Environment]::NewLine,
        $utf8NoBom)
}

$licenses = Join-Path $StagePath "licenses"
New-Item -ItemType Directory -Path $licenses -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $clientRoot "COPYING") -Destination (Join-Path $licenses "COPYING") -Force
Copy-Item -LiteralPath (Join-Path (Join-Path $clientRoot "doc") "GPL-3") -Destination (Join-Path $licenses "GPL-3") -Force
Copy-Item -LiteralPath (Join-Path (Join-Path $clientRoot "doc") "LGPL-3") -Destination (Join-Path $licenses "LGPL-3") -Force

$licenseNotice = @"
MoonFlower private Workshop package

The Haven & Hearth client code under src/haven is covered by GNU LGPL v3.
See COPYING, GPL-3, and LGPL-3 in this directory. Bundled third-party JARs
retain their embedded license and notice files. This package does not claim
that obfuscation or executable encryption creates a confidentiality boundary.
"@
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $licenses "README.txt"), $licenseNotice, $utf8NoBom)

$unexpectedDatabases = Get-ChildItem -LiteralPath $StagePath -File -Recurse |
    Where-Object { $_.Extension -eq ".db" -and $_.Name -notin @("static_data.db", "hitboxes.db") }
if ($unexpectedDatabases) {
    throw "Unexpected mutable database in staged package: $($unexpectedDatabases.FullName -join ', ')"
}

$forbiddenFiles = Get-ChildItem -LiteralPath $StagePath -File -Recurse | Where-Object {
    $_.Name -match '(?i)(credential|password|passwd|session|cookie|private[-_]?key|api[-_]?key)' -or
    $_.Name -match '(?i)(prefs|preferences|savedaccounts)' -or
    $_.Extension -in @(".log", ".bak", ".backup", ".pem", ".key", ".pfx", ".p12")
}
if ($forbiddenFiles) {
    throw "Potentially sensitive file in staged package: $($forbiddenFiles.FullName -join ', ')"
}

$forbiddenPathSegments = Get-ChildItem -LiteralPath $StagePath -Recurse | Where-Object {
    $_.FullName.Substring($StagePath.Length).TrimStart('\', '/') -match '(?i)(^|[\\/])(web|server|media-gateway|botcontrol)([\\/]|$)'
}
if ($forbiddenPathSegments) {
    throw "Web/operator path found in staged package: $($forbiddenPathSegments.FullName -join ', ')"
}

$textExtensions = @('.properties', '.cfg', '.ini')
$literalIpMatches = Get-ChildItem -LiteralPath $StagePath -File -Recurse | Where-Object {
    $_.Extension.ToLowerInvariant() -in $textExtensions
} | Select-String -Pattern '(?<![0-9])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![0-9])'
if ($literalIpMatches) {
    $literalIpPaths = @($literalIpMatches.Path | Select-Object -Unique)
    throw "Literal IP address found in staged text/configuration: $($literalIpPaths -join ', ')"
}

$metadataText = Get-Content -LiteralPath $metadataDestination -Raw
if ($metadataText -match '(?m)^visibility=(?!private$)' -or
    $metadataText -match '3423755273' -or
    $metadataText -match '(?i)Nightdawg|Hurricane|github\.com/ReleMai/Moonflower') {
    throw "Workshop metadata contains public, inherited, or externally identifying publishing state."
}

$appId = (Get-Content -LiteralPath (Join-Path $StagePath "steam_appid.txt") -Raw).Trim()
if ($appId -ne "3051280") {
    throw "Unexpected Steam AppID $appId; expected Haven & Hearth AppID 3051280."
}

$manifestPath = Join-Path $StagePath "private-publish-manifest.json"
$manifestFiles = Get-ChildItem -LiteralPath $StagePath -File -Recurse |
    Where-Object { $_.FullName -ne $manifestPath } |
    Sort-Object FullName
$manifestEntries = foreach ($file in $manifestFiles) {
    $relative = $file.FullName.Substring($StagePath.Length).TrimStart('\', '/').Replace('\', '/')
    [ordered]@{
        path = $relative
        size = $file.Length
        sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}
$manifest = [ordered]@{
    version = 1
    generatedAt = [DateTime]::UtcNow.ToString("o")
    appId = 3051280
    visibility = "private"
    files = @($manifestEntries)
}
$manifestJson = $manifest | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText($manifestPath, $manifestJson + [Environment]::NewLine, $utf8NoBom)

$manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$totalBytes = ($manifestFiles | Measure-Object -Property Length -Sum).Sum
$mode = if ((Get-PropertyValue $metadataDestination "workshop-id") -eq $null) { "CREATE NEW PRIVATE ITEM" } else { "UPDATE EXISTING PRIVATE ITEM" }

Write-Host ""
Write-Host "Private Steam Workshop package prepared."
Write-Host "Mode: $mode"
Write-Host "Stage: $StagePath"
Write-Host "Files: $($manifestFiles.Count)"
Write-Host "Bytes: $totalBytes"
Write-Host "Manifest SHA-256: $manifestHash"
Write-Host "No Steam upload was performed."
