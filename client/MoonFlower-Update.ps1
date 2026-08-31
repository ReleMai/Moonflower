[CmdletBinding()]
param(
    [switch]$NoUpdate,
    [switch]$CheckOnly,
    [switch]$NoLaunch,
    [switch]$Steam,
    [string]$FeedUri = 'https://github.com/ReleMai/Moonflower/releases/download/moonflower-latest/moonflower-update.json',
    [string]$FeedPath,
    [string]$CacheRoot,
    [string]$CurrentPackageRoot
)

$ErrorActionPreference = 'Stop'
$script:ExitCode = 0

function Write-MoonFlowerStatus {
    param([string]$Message)
    Write-Host "MoonFlower: $Message"
}

function Enter-UpdateLock {
    param([string]$LockPath)

    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        try {
            return [System.IO.File]::Open($LockPath, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
        } catch [System.IO.IOException] {
            Start-Sleep -Milliseconds 250
        }
    }
    return $null
}

function Resolve-PackageRoot {
    param([string]$RequestedRoot)

    if (-not [string]::IsNullOrWhiteSpace($RequestedRoot)) {
        return [System.IO.Path]::GetFullPath($RequestedRoot)
    }
    if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'hafen.jar') -PathType Leaf) {
        return $PSScriptRoot
    }
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'bin'))
}

function Read-UpdateFeed {
    if (-not [string]::IsNullOrWhiteSpace($FeedPath)) {
        return Get-Content -LiteralPath $FeedPath -Raw | ConvertFrom-Json
    }

    $response = Invoke-WebRequest -Uri $FeedUri -UseBasicParsing -TimeoutSec 12
    $content = $response.Content
    if ($content -is [byte[]]) {
        $content = [System.Text.Encoding]::UTF8.GetString($content)
    }
    return $content | ConvertFrom-Json
}

function Test-MissingUpdateFeed {
    param([System.Exception]$Failure)

    for ($current = $Failure; $null -ne $current; $current = $current.InnerException) {
        if ($null -ne $current.Response -and $null -ne $current.Response.StatusCode) {
            if ([int]$current.Response.StatusCode -eq 404) {
                return $true
            }
        }
        if ($current.Message -match '(?i)(\(404\)|404\s+Not Found)') {
            return $true
        }
    }
    return $false
}

function Assert-UpdateFeed {
    param($Feed)

    if ($Feed.schemaVersion -ne 1) {
        throw "Unsupported update-feed schema: $($Feed.schemaVersion)"
    }
    if ($Feed.channel -ne 'stable') {
        throw "Unexpected update channel: $($Feed.channel)"
    }
    if ([string]$Feed.repository -ne 'ReleMai/Moonflower') {
        throw "Unexpected update repository: $($Feed.repository)"
    }
    if ([string]$Feed.commit -notmatch '^[0-9a-f]{40}$') {
        throw 'The update feed has an invalid commit identifier.'
    }
    if ([string]$Feed.package.sha256 -notmatch '^[0-9a-f]{64}$') {
        throw 'The update feed has an invalid package hash.'
    }

    $packageSize = [int64]$Feed.package.size
    if ($packageSize -lt 1 -or $packageSize -gt 1073741824) {
        throw "The update package size is outside the allowed range: $packageSize"
    }

    if ([string]::IsNullOrWhiteSpace($FeedPath)) {
        $packageUri = [Uri]$Feed.package.url
        if ($packageUri.Scheme -ne 'https' -or $packageUri.Host -ne 'github.com') {
            throw "The update package must use the approved GitHub HTTPS host: $packageUri"
        }
        if (-not $packageUri.AbsolutePath.StartsWith('/ReleMai/Moonflower/releases/download/', [StringComparison]::OrdinalIgnoreCase)) {
            throw "The update package is outside the approved MoonFlower release path: $packageUri"
        }
    }
}

function Test-InstalledVersion {
    param([string]$VersionRoot, [string]$Commit)

    $markerPath = Join-Path $VersionRoot '.moonflower-version.json'
    $jarPath = Join-Path $VersionRoot 'hafen.jar'
    if (-not (Test-Path -LiteralPath $markerPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        return $false
    }
    try {
        $marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
        return ([string]$marker.commit -eq $Commit)
    } catch {
        return $false
    }
}

function Expand-VerifiedPackage {
    param([string]$ArchivePath, [string]$DestinationRoot)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $destinationFull = [System.IO.Path]::GetFullPath($DestinationRoot)
    $destinationPrefix = $destinationFull.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    New-Item -ItemType Directory -Path $destinationFull -Force | Out-Null

    $archive = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        $expandedSize = [int64]0
        foreach ($entry in $archive.Entries) {
            $expandedSize += [int64]$entry.Length
            if ($expandedSize -gt 1610612736) {
                throw 'The expanded update package exceeds the 1.5 GB safety limit.'
            }

            $relativePath = $entry.FullName.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
            if ([string]::IsNullOrWhiteSpace($relativePath)) {
                continue
            }
            $destinationPath = [System.IO.Path]::GetFullPath((Join-Path $destinationFull $relativePath))
            if (-not $destinationPath.StartsWith($destinationPrefix, [StringComparison]::OrdinalIgnoreCase)) {
                throw "The update archive contains an unsafe path: $($entry.FullName)"
            }

            if ($entry.FullName.EndsWith('/')) {
                New-Item -ItemType Directory -Path $destinationPath -Force | Out-Null
                continue
            }
            $parent = Split-Path -Parent $destinationPath
            if ($parent) {
                New-Item -ItemType Directory -Path $parent -Force | Out-Null
            }
            $input = $entry.Open()
            try {
                $output = [System.IO.File]::Open($destinationPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
                try {
                    $input.CopyTo($output)
                } finally {
                    $output.Dispose()
                }
            } finally {
                $input.Dispose()
            }
        }
    } finally {
        $archive.Dispose()
    }

    foreach ($required in @('hafen.jar', 'manifest.json', 'Play.bat', 'MoonFlower-Update.ps1')) {
        if (-not (Test-Path -LiteralPath (Join-Path $destinationFull $required) -PathType Leaf)) {
            throw "The update package is incomplete; missing $required."
        }
    }
}

function Install-Update {
    param($Feed, [string]$VersionsRoot, [string]$DownloadsRoot)

    $commit = [string]$Feed.commit
    $targetRoot = Join-Path $VersionsRoot $commit
    if (Test-InstalledVersion $targetRoot $commit) {
        return $targetRoot
    }

    New-Item -ItemType Directory -Path $DownloadsRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $VersionsRoot -Force | Out-Null
    $token = [Guid]::NewGuid().ToString('N')
    $downloadPath = Join-Path $DownloadsRoot "$commit-$token.zip"
    $stagingRoot = Join-Path $VersionsRoot ".staging-$commit-$token"
    try {
        Write-MoonFlowerStatus "downloading build $($commit.Substring(0, 12))..."
        if (-not [string]::IsNullOrWhiteSpace($FeedPath)) {
            Copy-Item -LiteralPath ([string]$Feed.package.url) -Destination $downloadPath
        } else {
            Invoke-WebRequest -Uri ([string]$Feed.package.url) -OutFile $downloadPath -UseBasicParsing -TimeoutSec 120
        }

        $download = Get-Item -LiteralPath $downloadPath
        if ($download.Length -ne [int64]$Feed.package.size) {
            throw "Downloaded package size mismatch. Expected $($Feed.package.size), received $($download.Length)."
        }
        $actualHash = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne [string]$Feed.package.sha256) {
            throw "Downloaded package hash mismatch. Expected $($Feed.package.sha256), received $actualHash."
        }

        Expand-VerifiedPackage $downloadPath $stagingRoot
        $marker = [ordered]@{
            schemaVersion = 1
            commit = $commit
            installedAt = [DateTimeOffset]::UtcNow.ToString('o')
        }
        $marker | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stagingRoot '.moonflower-version.json') -Encoding UTF8
        [System.IO.File]::WriteAllText((Join-Path $stagingRoot '.in-use.lock'), '')

        if (Test-Path -LiteralPath $targetRoot) {
            Remove-Item -LiteralPath $targetRoot -Recurse -Force
        }
        Move-Item -LiteralPath $stagingRoot -Destination $targetRoot
        return $targetRoot
    } finally {
        if (Test-Path -LiteralPath $downloadPath) {
            Remove-Item -LiteralPath $downloadPath -Force
        }
        if (Test-Path -LiteralPath $stagingRoot) {
            Remove-Item -LiteralPath $stagingRoot -Recurse -Force
        }
    }
}

function Read-ActiveVersion {
    param([string]$StatePath)

    if (-not (Test-Path -LiteralPath $StatePath -PathType Leaf)) {
        return $null
    }
    try {
        $state = Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
        $candidate = [System.IO.Path]::GetFullPath([string]$state.path)
        if (Test-InstalledVersion $candidate ([string]$state.commit)) {
            return $candidate
        }
    } catch {
        Write-Warning "MoonFlower ignored an invalid updater state file: $($_.Exception.Message)"
    }
    return $null
}

function Write-ActiveVersion {
    param([string]$StatePath, [string]$VersionRoot, [string]$Commit)

    $state = [ordered]@{
        schemaVersion = 1
        commit = $Commit
        path = $VersionRoot
        activatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
    $temporaryStatePath = "$StatePath.$([Guid]::NewGuid().ToString('N')).tmp"
    try {
        $state | ConvertTo-Json | Set-Content -LiteralPath $temporaryStatePath -Encoding UTF8
        if (Test-Path -LiteralPath $StatePath -PathType Leaf) {
            [System.IO.File]::Replace($temporaryStatePath, $StatePath, $null)
        } else {
            Move-Item -LiteralPath $temporaryStatePath -Destination $StatePath
        }
    } finally {
        if (Test-Path -LiteralPath $temporaryStatePath) {
            Remove-Item -LiteralPath $temporaryStatePath -Force
        }
    }
}

function Invoke-MoonFlower {
    param([string]$PackageRoot)

    $jarPath = Join-Path $PackageRoot 'hafen.jar'
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "MoonFlower could not find hafen.jar in $PackageRoot"
    }

    $lock = $null
    $lockPath = Join-Path $PackageRoot '.in-use.lock'
    if (Test-Path -LiteralPath $lockPath -PathType Leaf) {
        $lock = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::Read)
    }
    try {
        Push-Location $PackageRoot
        try {
            $steamValue = if ($Steam) { 'true' } else { 'false' }
            $javaArguments = @(
                '-Dsun.java2d.uiScale.enabled=false',
                '-Dsun.java2d.win.uiScaleX=1.0',
                '-Dsun.java2d.win.uiScaleY=1.0',
                '-Xss8m', '-Xms1024m', '-Xmx4096m',
                '--add-exports', 'java.base/java.lang=ALL-UNNAMED',
                '--add-exports', 'java.desktop/sun.awt=ALL-UNNAMED',
                '--add-exports', 'java.desktop/sun.java2d=ALL-UNNAMED',
                "-DrunningThroughSteam=$steamValue",
                '-jar', 'hafen.jar'
            )
            & java @javaArguments
            $script:ExitCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
    } finally {
        if ($lock) {
            $lock.Dispose()
        }
    }
}

$packagedRoot = Resolve-PackageRoot $CurrentPackageRoot
if ([string]::IsNullOrWhiteSpace($CacheRoot)) {
    $localData = [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
    $CacheRoot = Join-Path $localData 'MoonFlower\AutoUpdate'
}
$CacheRoot = [System.IO.Path]::GetFullPath($CacheRoot)
$versionsRoot = Join-Path $CacheRoot 'versions'
$downloadsRoot = Join-Path $CacheRoot 'downloads'
$statePath = Join-Path $CacheRoot 'current.json'
New-Item -ItemType Directory -Path $CacheRoot -Force | Out-Null

$selectedRoot = Read-ActiveVersion $statePath
if ($NoUpdate -or $env:MOONFLOWER_UPDATE_DISABLED -eq '1') {
    Write-MoonFlowerStatus 'automatic update check skipped.'
} else {
    $updateLock = Enter-UpdateLock (Join-Path $CacheRoot 'update.lock')
    if (-not $updateLock) {
        Write-Warning 'MoonFlower skipped this update check because another launcher is updating the cache.'
        if ($CheckOnly) {
            $script:ExitCode = 2
        }
    } else {
        try {
            try {
                # Re-read after taking the lock in case another launcher just activated a build.
                $selectedRoot = Read-ActiveVersion $statePath
                $feed = Read-UpdateFeed
                Assert-UpdateFeed $feed
                $commit = [string]$feed.commit
                if ($CheckOnly) {
                    $installed = if (Test-InstalledVersion (Join-Path $versionsRoot $commit) $commit) { 'installed' } else { 'available' }
                    Write-MoonFlowerStatus "stable build $($commit.Substring(0, 12)) is $installed."
                    exit 0
                }
                $selectedRoot = Install-Update $feed $versionsRoot $downloadsRoot
                Write-ActiveVersion $statePath $selectedRoot $commit
                Write-MoonFlowerStatus "using stable build $($commit.Substring(0, 12))."
            } catch {
                if (Test-MissingUpdateFeed $_.Exception) {
                    Write-MoonFlowerStatus 'the stable update feed has not been published yet.'
                } else {
                    Write-Warning "MoonFlower could not update: $($_.Exception.Message)"
                }
                if ($CheckOnly) {
                    $script:ExitCode = 1
                }
                if ($selectedRoot) {
                    Write-MoonFlowerStatus 'using the last verified downloaded build.'
                } else {
                    Write-MoonFlowerStatus 'using the packaged fallback build.'
                }
            }
        } finally {
            $updateLock.Dispose()
        }
    }
}

if ($CheckOnly) {
    exit $script:ExitCode
}
if (-not $selectedRoot) {
    $selectedRoot = $packagedRoot
}
if (-not $NoLaunch) {
    Invoke-MoonFlower $selectedRoot
}
exit $script:ExitCode
