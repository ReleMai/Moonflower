[CmdletBinding()]
param(
    [switch]$NoUpdate,
    [switch]$CheckOnly,
    [switch]$NoLaunch,
    [switch]$Steam,
    [switch]$Rollback,
    [switch]$NoDelta,
    [string]$FeedUri = 'https://github.com/ReleMai/Moonflower/releases/download/moonflower-latest/moonflower-update.json',
    [string]$FeedPath,
    [string]$CacheRoot,
    [string]$CurrentPackageRoot,
    [ValidateRange(1, 60)][int]$FeedTimeoutSec = 5
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

    $response = Invoke-WebRequest -Uri $FeedUri -UseBasicParsing -TimeoutSec $FeedTimeoutSec
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

    if ($Feed.schemaVersion -notin @(1, 2)) {
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
    Assert-PackageDescriptor $Feed.package 'stable'

    if ($null -ne $Feed.previous) {
        if ([string]$Feed.previous.commit -notmatch '^[0-9a-f]{40}$' -or
            [string]$Feed.previous.commit -eq [string]$Feed.commit) {
            throw 'The update feed has an invalid previous commit identifier.'
        }
        Assert-PackageDescriptor $Feed.previous.package 'previous'
    }

    if ($null -ne $Feed.patch) {
        try {
            if ($null -eq $Feed.previous -or
                [string]$Feed.patch.fromCommit -ne [string]$Feed.previous.commit) {
                throw 'The update feed delta does not target the declared previous build.'
            }
            Assert-PackageDescriptor $Feed.patch 'delta'
        } catch {
            # Delta metadata is optional. A bad delta must never prevent a
            # valid full package from being used.
            Write-Warning "MoonFlower ignored invalid optional delta metadata: $($_.Exception.Message)"
            $Feed.PSObject.Properties.Remove('patch')
        }
    }
}

function Assert-PackageDescriptor {
    param($Package, [string]$Label)

    if ([string]$Package.sha256 -notmatch '^[0-9a-f]{64}$') {
        throw "The $Label update package has an invalid hash."
    }

    $packageSize = [int64]$Package.size
    if ($packageSize -lt 1 -or $packageSize -gt 1073741824) {
        throw "The $Label update package size is outside the allowed range: $packageSize"
    }

    if ([string]::IsNullOrWhiteSpace($FeedPath)) {
        $packageUri = [Uri]$Package.url
        if ($packageUri.Scheme -ne 'https' -or $packageUri.Host -ne 'github.com') {
            throw "The $Label update package must use the approved GitHub HTTPS host: $packageUri"
        }
        if (-not $packageUri.AbsolutePath.StartsWith('/ReleMai/Moonflower/releases/download/', [StringComparison]::OrdinalIgnoreCase)) {
            throw "The $Label update package is outside the approved MoonFlower release path: $packageUri"
        }
    }
}

function Get-SafeArchivePath {
    param(
        [string]$EntryName,
        [string]$DestinationRoot
    )

    if ([string]::IsNullOrWhiteSpace($EntryName)) {
        return $null
    }

    $relativePath = $EntryName.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    if ($relativePath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $relativePath = $relativePath.TrimEnd([System.IO.Path]::DirectorySeparatorChar)
    }
    if ([string]::IsNullOrWhiteSpace($relativePath)) {
        return $null
    }
    if ([System.IO.Path]::IsPathRooted($relativePath) -or
        $relativePath -match '^[A-Za-z]:' -or
        $relativePath.Contains([char]0) -or
        $relativePath -match '(?i)(^|[\\/])\.\.?(?:[\\/]|$)') {
        throw "The update archive contains an unsafe path: $EntryName"
    }

    $destinationFull = [System.IO.Path]::GetFullPath($DestinationRoot)
    $destinationPath = [System.IO.Path]::GetFullPath((Join-Path $destinationFull $relativePath))
    $destinationPrefix = $destinationFull.TrimEnd([char]92, [char]47) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $destinationPath.StartsWith($destinationPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "The update archive contains an unsafe path: $EntryName"
    }
    return $destinationPath
}

function Get-ZipEntryMap {
    param([System.IO.Compression.ZipArchive]$Archive)

    $entries = @{}
    foreach ($entry in $Archive.Entries) {
        $path = Get-SafeArchivePath $entry.FullName ([System.IO.Path]::GetTempPath())
        if ($null -eq $path -or $entry.FullName.EndsWith('/')) {
            continue
        }
        $key = $entry.FullName.Replace('\', '/')
        if ($entries.ContainsKey($key)) {
            throw "The update archive contains duplicate file entries: $key"
        }
        $entries[$key] = $entry
    }
    return $entries
}

function Get-FileSha256 {
    param([string]$Path)

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-ZipEntrySha256 {
    param([System.IO.Compression.ZipArchiveEntry]$Entry)

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    $input = $Entry.Open()
    try {
        $digest = $sha256.ComputeHash($input)
        return ([BitConverter]::ToString($digest).Replace('-', '').ToLowerInvariant())
    } finally {
        $input.Dispose()
        $sha256.Dispose()
    }
}

function Get-RollbackFeed {
    param($Feed)

    if ($null -eq $Feed.previous) {
        throw 'The stable feed does not provide a previous verified build.'
    }
    return [pscustomobject][ordered]@{
        schemaVersion = 1
        channel = [string]$Feed.channel
        repository = [string]$Feed.repository
        commit = [string]$Feed.previous.commit
        publishedAt = [string]$Feed.previous.publishedAt
        package = $Feed.previous.package
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
    New-Item -ItemType Directory -Path $destinationFull -Force | Out-Null

    $archive = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        $expandedSize = [int64]0
        $seenPaths = @{}
        foreach ($entry in $archive.Entries) {
            $expandedSize += [int64]$entry.Length
            if ($expandedSize -gt 1610612736) {
                throw 'The expanded update package exceeds the 1.5 GB safety limit.'
            }

            $destinationPath = Get-SafeArchivePath $entry.FullName $destinationFull
            if ($null -eq $destinationPath) {
                continue
            }
            if (-not $entry.FullName.EndsWith('/')) {
                $entryKey = $entry.FullName.Replace('\', '/')
                if ($seenPaths.ContainsKey($entryKey)) {
                    throw "The update archive contains duplicate file entries: $entryKey"
                }
                $seenPaths[$entryKey] = $true
            }
        }
    } finally {
        $archive.Dispose()
    }

    # Keep the safety scan explicit, then use the framework extractor to avoid
    # the PowerShell per-entry I/O overhead on a full package.
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ArchivePath, $destinationFull)

    foreach ($required in @('hafen.jar', 'manifest.json', 'Play.bat', 'MoonFlower-Update.ps1')) {
        if (-not (Test-Path -LiteralPath (Join-Path $destinationFull $required) -PathType Leaf)) {
            throw "The update package is incomplete; missing $required."
        }
    }
}

function Read-InstalledCommit {
    param([string]$VersionRoot)

    $markerPath = Join-Path $VersionRoot '.moonflower-version.json'
    if (-not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
        return $null
    }
    try {
        $marker = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
        if ([string]$marker.commit -match '^[0-9a-f]{40}$') {
            return [string]$marker.commit
        }
    } catch {
        return $null
    }
    return $null
}

function Write-VersionMetadata {
    param(
        [string]$VersionRoot,
        [string]$Commit
    )

    $marker = [ordered]@{
        schemaVersion = 1
        commit = $Commit
        installedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
    $marker | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $VersionRoot '.moonflower-version.json') -Encoding UTF8
    [System.IO.File]::WriteAllText((Join-Path $VersionRoot '.in-use.lock'), '')
}

function Enable-FastVersionCopy {
    if ($null -eq ([System.Management.Automation.PSTypeName]'MoonFlowerNativeHardLink').Type) {
        try {
            Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class MoonFlowerNativeHardLink
{
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool CreateHardLink(
        string fileName,
        string existingFileName,
        IntPtr securityAttributes);
}
'@
        } catch {
            return $false
        }
    }
    return $null -ne ([System.Management.Automation.PSTypeName]'MoonFlowerNativeHardLink').Type
}

function Copy-VersionTreeFast {
    param(
        [string]$SourceRoot,
        [string]$DestinationRoot
    )

    $sourceFull = [System.IO.Path]::GetFullPath($SourceRoot)
    $destinationFull = [System.IO.Path]::GetFullPath($DestinationRoot)
    if (-not (Test-InstalledVersion $sourceFull (Read-InstalledCommit $sourceFull))) {
        throw "The delta base is not a verified installed version: $sourceFull"
    }
    New-Item -ItemType Directory -Path $destinationFull -Force | Out-Null
    $sourcePrefix = $sourceFull.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    $canHardLink = Enable-FastVersionCopy

    foreach ($file in @(Get-ChildItem -LiteralPath $sourceFull -File -Recurse -Force)) {
        $relativePath = $file.FullName.Substring($sourcePrefix.Length).Replace('\', '/')
        if ($relativePath -in @('.moonflower-version.json', '.in-use.lock')) {
            continue
        }
        $destinationPath = Get-SafeArchivePath $relativePath $destinationFull
        $parent = Split-Path -Parent $destinationPath
        if ($parent) {
            New-Item -ItemType Directory -Path $parent -Force | Out-Null
        }

        $linked = $false
        if ($canHardLink) {
            try {
                $linked = [MoonFlowerNativeHardLink]::CreateHardLink(
                    $destinationPath,
                    $file.FullName,
                    [IntPtr]::Zero)
            } catch {
                $linked = $false
            }
        }
        if (-not $linked) {
            [System.IO.File]::Copy($file.FullName, $destinationPath, $false)
        }
    }
}

function Assert-PatchRelativePath {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw 'The update delta contains an empty file path.'
    }
    $normalized = $Path.Replace('\', '/')
    if ($normalized.EndsWith('/') -or
        $normalized.StartsWith('/') -or
        $normalized -match '^[A-Za-z]:' -or
        $normalized.Contains([char]0) -or
        $normalized -match '(^|/)\.{1,2}(/|$)') {
        throw "The update delta contains an unsafe file path: $Path"
    }
    return $normalized
}

function Read-PatchPackage {
    param(
        [string]$PatchPath,
        [string]$ExpectedFromCommit,
        [string]$ExpectedCommit
    )

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($PatchPath)
    try {
        $entries = Get-ZipEntryMap $archive
        $metadataName = '.moonflower-patch.json'
        if (-not $entries.ContainsKey($metadataName)) {
            throw 'The update delta is missing .moonflower-patch.json.'
        }

        $reader = [System.IO.StreamReader]::new($entries[$metadataName].Open(), [System.Text.Encoding]::UTF8)
        try {
            $metadata = $reader.ReadToEnd() | ConvertFrom-Json
        } finally {
            $reader.Dispose()
        }

        if ($metadata.schemaVersion -ne 1 -or
            [string]$metadata.fromCommit -ne $ExpectedFromCommit -or
            [string]$metadata.commit -ne $ExpectedCommit) {
            throw 'The update delta metadata does not match the requested build transition.'
        }

        $targetFiles = @{}
        $targetExpandedBytes = [int64]0
        foreach ($descriptor in @($metadata.files)) {
            $path = Assert-PatchRelativePath ([string]$descriptor.path)
            if ($path -in @('.moonflower-patch.json', '.moonflower-version.json', '.in-use.lock') -or
                $targetFiles.ContainsKey($path)) {
                throw "The update delta contains a duplicate or reserved target path: $path"
            }
            if ([string]$descriptor.sha256 -notmatch '^[0-9a-f]{64}$') {
                throw "The update delta has an invalid target hash for $path."
            }
            $size = [int64]$descriptor.size
            if ($size -lt 0) {
                throw "The update delta has an invalid target size for $path."
            }
            $targetExpandedBytes += $size
            if ($targetExpandedBytes -gt 1610612736) {
                throw 'The update delta target exceeds the 1.5 GB safety limit.'
            }
            $targetFiles[$path] = [pscustomobject][ordered]@{
                path = $path
                size = $size
                sha256 = [string]$descriptor.sha256
            }
        }
        if ($targetFiles.Count -eq 0 -or $targetFiles.Count -gt 4096) {
            throw 'The update delta has an invalid target file count.'
        }

        foreach ($required in @('hafen.jar', 'manifest.json', 'Play.bat', 'MoonFlower-Update.ps1')) {
            if (-not $targetFiles.ContainsKey($required)) {
                throw "The update delta target is incomplete; missing $required."
            }
        }

        $changedFiles = @{}
        foreach ($descriptor in @($metadata.changed)) {
            $path = Assert-PatchRelativePath ([string]$descriptor.path)
            if (-not $targetFiles.ContainsKey($path) -or $changedFiles.ContainsKey($path)) {
                throw "The update delta has an invalid changed path: $path"
            }
            if ([int64]$descriptor.size -ne [int64]$targetFiles[$path].size -or
                [string]$descriptor.sha256 -ne [string]$targetFiles[$path].sha256) {
                throw "The update delta changed-file descriptor disagrees with its target manifest: $path"
            }
            $changedFiles[$path] = $targetFiles[$path]
        }
        if ($changedFiles.Count -gt 4096) {
            throw 'The update delta has too many changed files.'
        }

        $deletedFiles = @{}
        foreach ($deleted in @($metadata.deleted)) {
            $path = Assert-PatchRelativePath ([string]$deleted)
            if ($path -in @('.moonflower-patch.json', '.moonflower-version.json', '.in-use.lock') -or
                $targetFiles.ContainsKey($path) -or
                $deletedFiles.ContainsKey($path)) {
                throw "The update delta has an invalid deleted path: $path"
            }
            $deletedFiles[$path] = $true
        }

        foreach ($entryPath in @($entries.Keys)) {
            if ($entryPath -eq $metadataName) {
                continue
            }
            if (-not $changedFiles.ContainsKey($entryPath)) {
                throw "The update delta contains an unlisted payload file: $entryPath"
            }
        }
        foreach ($changedPath in @($changedFiles.Keys)) {
            if (-not $entries.ContainsKey($changedPath)) {
                throw "The update delta is missing changed file payload: $changedPath"
            }
        }

        return [pscustomobject][ordered]@{
            Archive = $archive
            Entries = $entries
            TargetFiles = $targetFiles
            ChangedFiles = $changedFiles
            DeletedFiles = $deletedFiles
        }
    } catch {
        $archive.Dispose()
        throw
    }
}

function Assert-PatchedTree {
    param(
        [string]$Root,
        $Patch
    )

    $rootFull = [System.IO.Path]::GetFullPath($Root)
    $rootPrefix = $rootFull.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    $actualFiles = @{}
    foreach ($file in @(Get-ChildItem -LiteralPath $rootFull -File -Recurse -Force)) {
        $relativePath = $file.FullName.Substring($rootPrefix.Length).Replace('\', '/')
        if ($relativePath -in @('.moonflower-version.json', '.in-use.lock')) {
            continue
        }
        if ($actualFiles.ContainsKey($relativePath)) {
            throw "The reconstructed update contains duplicate files: $relativePath"
        }
        if (-not $Patch.TargetFiles.ContainsKey($relativePath)) {
            throw "The reconstructed update contains an unexpected file: $relativePath"
        }
        $actualFiles[$relativePath] = $file
    }
    if ($actualFiles.Count -ne $Patch.TargetFiles.Count) {
        throw "The reconstructed update has the wrong file count. Expected $($Patch.TargetFiles.Count), found $($actualFiles.Count)."
    }

    foreach ($path in @($Patch.TargetFiles.Keys)) {
        $expected = $Patch.TargetFiles[$path]
        $destinationPath = Get-SafeArchivePath $path $rootFull
        if (-not (Test-Path -LiteralPath $destinationPath -PathType Leaf)) {
            throw "The reconstructed update is missing: $path"
        }
        $actual = Get-Item -LiteralPath $destinationPath
        if ($actual.Length -ne [int64]$expected.size -or
            (Get-FileSha256 $destinationPath) -ne [string]$expected.sha256) {
            throw "The reconstructed update failed verification: $path"
        }
    }
}

function Apply-VerifiedPatch {
    param(
        [string]$PatchPath,
        [string]$BaseRoot,
        [string]$DestinationRoot,
        [string]$FromCommit,
        [string]$ToCommit
    )

    if (-not (Test-InstalledVersion $BaseRoot $FromCommit)) {
        throw "The cached delta base is unavailable: $FromCommit"
    }
    $patch = Read-PatchPackage $PatchPath $FromCommit $ToCommit
    try {
        Copy-VersionTreeFast $BaseRoot $DestinationRoot

        foreach ($path in @($patch.ChangedFiles.Keys | Sort-Object)) {
            $destinationPath = Get-SafeArchivePath $path $DestinationRoot
            if (Test-Path -LiteralPath $destinationPath -PathType Leaf) {
                # The base copy may use a hard link. Remove the link before
                # writing so a changed payload never mutates the base version.
                Remove-Item -LiteralPath $destinationPath -Force
            } elseif (Test-Path -LiteralPath $destinationPath) {
                throw "The delta target path is occupied by a directory: $path"
            }
            $parent = Split-Path -Parent $destinationPath
            if ($parent) {
                New-Item -ItemType Directory -Path $parent -Force | Out-Null
            }

            $input = $patch.Entries[$path].Open()
            try {
                $output = [System.IO.File]::Open($destinationPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
                try {
                    $input.CopyTo($output, 1048576)
                } finally {
                    $output.Dispose()
                }
            } finally {
                $input.Dispose()
            }
        }

        foreach ($path in @($patch.DeletedFiles.Keys | Sort-Object)) {
            $destinationPath = Get-SafeArchivePath $path $DestinationRoot
            if (Test-Path -LiteralPath $destinationPath -PathType Leaf) {
                Remove-Item -LiteralPath $destinationPath -Force
            } elseif (Test-Path -LiteralPath $destinationPath) {
                throw "The delta deleted path is occupied by a directory: $path"
            }
        }

        Assert-PatchedTree $DestinationRoot $patch
    } finally {
        $patch.Archive.Dispose()
    }
}

function Update-MoonFlowerDownloadProgress {
    param(
        [string]$Label,
        [int64]$CompletedBytes,
        [int64]$TotalBytes
    )

    if ($TotalBytes -gt 0) {
        $percent = [int][Math]::Min(100, [Math]::Max(0, [Math]::Floor(($CompletedBytes * 100.0) / $TotalBytes)))
        $status = '{0} - {1:N1} / {2:N1} MB' -f $Label, ($CompletedBytes / 1048576.0), ($TotalBytes / 1048576.0)
    } else {
        $percent = 0
        $status = '{0} - {1:N1} MB' -f $Label, ($CompletedBytes / 1048576.0)
    }
    Write-Progress -Id 17 -Activity 'MoonFlower update' -Status $status -PercentComplete $percent
}

function Download-RemoteArtifact {
    param(
        [Uri]$Uri,
        [string]$DestinationPath,
        [int64]$ExpectedSize,
        [string]$ProgressLabel
    )

    $existingLength = [int64]0
    if (Test-Path -LiteralPath $DestinationPath -PathType Leaf) {
        $existingLength = (Get-Item -LiteralPath $DestinationPath).Length
    }
    $request = [System.Net.HttpWebRequest]::Create($Uri)
    $request.Method = 'GET'
    $request.UserAgent = 'MoonFlowerUpdater/1.0'
    $request.Timeout = 120000
    $request.ReadWriteTimeout = 120000
    $resume = $existingLength -gt 0
    if ($resume) {
        $request.AddRange($existingLength)
    }

    $response = $null
    $input = $null
    $output = $null
    $progressShown = $false
    try {
        $response = $request.GetResponse()
        $statusCode = [int]$response.StatusCode
        if (($resume -and $statusCode -notin @(200, 206)) -or
            (-not $resume -and $statusCode -ne 200)) {
            throw "The update server returned unexpected HTTP status $statusCode."
        }
        $append = $resume -and $statusCode -eq 206
        $mode = if ($append) { [System.IO.FileMode]::Append } else { [System.IO.FileMode]::Create }
        $completedLength = if ($append) { $existingLength } else { [int64]0 }
        $input = $response.GetResponseStream()
        $output = [System.IO.File]::Open($DestinationPath, $mode, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        $buffer = New-Object byte[] 1048576
        $lastProgressAt = [System.DateTime]::UtcNow.AddSeconds(-1)
        Update-MoonFlowerDownloadProgress $ProgressLabel $completedLength $ExpectedSize
        $progressShown = $true
        while (($read = $input.Read($buffer, 0, $buffer.Length)) -gt 0) {
            $output.Write($buffer, 0, $read)
            $completedLength += $read
            $now = [System.DateTime]::UtcNow
            if (($now - $lastProgressAt).TotalMilliseconds -ge 250) {
                Update-MoonFlowerDownloadProgress $ProgressLabel $completedLength $ExpectedSize
                $lastProgressAt = $now
            }
        }
        Update-MoonFlowerDownloadProgress $ProgressLabel $completedLength $ExpectedSize
    } catch {
        $httpResponse = $_.Exception.Response
        if ($null -ne $httpResponse -and [int]$httpResponse.StatusCode -eq 416 -and
            (Test-Path -LiteralPath $DestinationPath -PathType Leaf)) {
            Remove-Item -LiteralPath $DestinationPath -Force
        }
        throw
    } finally {
        if ($null -ne $output) {
            $output.Dispose()
        }
        if ($null -ne $input) {
            $input.Dispose()
        }
        if ($null -ne $response) {
            $response.Dispose()
        }
        if ($progressShown) {
            Write-Progress -Id 17 -Activity 'MoonFlower update' -Completed
        }
    }
}

function Download-VerifiedArtifact {
    param(
        $Descriptor,
        [string]$Commit,
        [string]$Kind,
        [string]$DownloadsRoot
    )

    $partialPath = Join-Path $DownloadsRoot "$Commit.$Kind.partial"
    $expectedSize = [int64]$Descriptor.size
    New-Item -ItemType Directory -Path $DownloadsRoot -Force | Out-Null

    if (-not [string]::IsNullOrWhiteSpace($FeedPath)) {
        Copy-Item -LiteralPath ([string]$Descriptor.url) -Destination $partialPath -Force
    } else {
        $existingSize = if (Test-Path -LiteralPath $partialPath -PathType Leaf) {
            (Get-Item -LiteralPath $partialPath).Length
        } else {
            [int64]0
        }
        if ($existingSize -gt $expectedSize) {
            Remove-Item -LiteralPath $partialPath -Force
            $existingSize = 0
        }
        if ($existingSize -eq $expectedSize) {
            Write-MoonFlowerStatus "verifying cached $Kind $($Commit.Substring(0, 12))..."
        } else {
            $action = if ($existingSize -gt 0) { 'resuming' } else { 'downloading' }
            Write-MoonFlowerStatus "$action $Kind $($Commit.Substring(0, 12))..."
            Download-RemoteArtifact -Uri ([Uri]$Descriptor.url) -DestinationPath $partialPath -ExpectedSize $expectedSize -ProgressLabel "$Kind $($Commit.Substring(0, 12))"
        }
    }

    $artifact = Get-Item -LiteralPath $partialPath
    if ($artifact.Length -ne $expectedSize) {
        if ($artifact.Length -gt $expectedSize) {
            Remove-Item -LiteralPath $partialPath -Force
        }
        throw "Downloaded $Kind size mismatch. Expected $expectedSize, received $($artifact.Length)."
    }
    $actualHash = Get-FileSha256 $partialPath
    if ($actualHash -ne [string]$Descriptor.sha256) {
        Remove-Item -LiteralPath $partialPath -Force
        throw "Downloaded $Kind hash mismatch. Expected $($Descriptor.sha256), received $actualHash."
    }
    return $partialPath
}

function Install-Update {
    param(
        $Feed,
        [string]$VersionsRoot,
        [string]$DownloadsRoot,
        [string]$BaseVersionRoot,
        [switch]$AllowDelta
    )

    $commit = [string]$Feed.commit
    $targetRoot = Join-Path $VersionsRoot $commit
    if (Test-InstalledVersion $targetRoot $commit) {
        return $targetRoot
    }

    New-Item -ItemType Directory -Path $DownloadsRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $VersionsRoot -Force | Out-Null
    $token = [Guid]::NewGuid().ToString('N')
    $stagingRoot = Join-Path $VersionsRoot ".staging-$commit-$token"
    $patchPath = $null
    $fullPath = $null
    try {
        $baseCommit = if ($BaseVersionRoot) { Read-InstalledCommit $BaseVersionRoot } else { $null }
        if ($AllowDelta -and $null -ne $Feed.patch -and
            $BaseVersionRoot -and
            [string]$Feed.patch.fromCommit -eq $baseCommit) {
            try {
                $patchPath = Download-VerifiedArtifact $Feed.patch $commit 'delta' $DownloadsRoot
                Write-MoonFlowerStatus "applying delta from $($baseCommit.Substring(0, 12))..."
                Apply-VerifiedPatch $patchPath $BaseVersionRoot $stagingRoot $baseCommit $commit
                Write-VersionMetadata $stagingRoot $commit
                if (Test-Path -LiteralPath $targetRoot) {
                    Remove-Item -LiteralPath $targetRoot -Recurse -Force
                }
                Move-Item -LiteralPath $stagingRoot -Destination $targetRoot
                Remove-Item -LiteralPath $patchPath -Force -ErrorAction SilentlyContinue
                return $targetRoot
            } catch {
                Write-Warning "MoonFlower delta install failed; falling back to the full package: $($_.Exception.Message)"
                if ($patchPath -and (Test-Path -LiteralPath $patchPath -PathType Leaf)) {
                    Remove-Item -LiteralPath $patchPath -Force -ErrorAction SilentlyContinue
                }
                if (Test-Path -LiteralPath $stagingRoot) {
                    Remove-Item -LiteralPath $stagingRoot -Recurse -Force
                }
            }
        }

        $fullPath = Download-VerifiedArtifact $Feed.package $commit 'full' $DownloadsRoot
        Expand-VerifiedPackage $fullPath $stagingRoot
        Write-VersionMetadata $stagingRoot $commit

        if (Test-Path -LiteralPath $targetRoot) {
            Remove-Item -LiteralPath $targetRoot -Recurse -Force
        }
        Move-Item -LiteralPath $stagingRoot -Destination $targetRoot
        Remove-Item -LiteralPath $fullPath -Force -ErrorAction SilentlyContinue
        return $targetRoot
    } finally {
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
    $backupStatePath = "$StatePath.$([Guid]::NewGuid().ToString('N')).bak"
    try {
        $state | ConvertTo-Json | Set-Content -LiteralPath $temporaryStatePath -Encoding UTF8
        if (Test-Path -LiteralPath $StatePath -PathType Leaf) {
            [System.IO.File]::Replace($temporaryStatePath, $StatePath, $backupStatePath, $true)
        } else {
            Move-Item -LiteralPath $temporaryStatePath -Destination $StatePath
        }
    } finally {
        if (Test-Path -LiteralPath $temporaryStatePath) {
            Remove-Item -LiteralPath $temporaryStatePath -Force
        }
        if (Test-Path -LiteralPath $backupStatePath) {
            Remove-Item -LiteralPath $backupStatePath -Force
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
if ($Rollback -and ($NoUpdate -or $env:MOONFLOWER_UPDATE_DISABLED -eq '1')) {
    Write-Warning 'MoonFlower rollback requires access to the stable update feed; remove -NoUpdate and enable update checks.'
    exit 2
}
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
                $requestedFeed = $feed
                $buildLabel = 'stable'
                if ($Rollback) {
                    $requestedFeed = Get-RollbackFeed $feed
                    Assert-UpdateFeed $requestedFeed
                    $buildLabel = 'previous'
                }
                $commit = [string]$requestedFeed.commit
                if ($CheckOnly) {
                    $installed = if (Test-InstalledVersion (Join-Path $versionsRoot $commit) $commit) { 'installed' } else { 'available' }
                    Write-MoonFlowerStatus "$buildLabel build $($commit.Substring(0, 12)) is $installed."
                    exit 0
                }
                $allowDelta = -not $Rollback -and -not $NoDelta
                $selectedRoot = Install-Update -Feed $requestedFeed -VersionsRoot $versionsRoot -DownloadsRoot $downloadsRoot -BaseVersionRoot $selectedRoot -AllowDelta:$allowDelta
                Write-ActiveVersion $statePath $selectedRoot $commit
                Write-MoonFlowerStatus "using $buildLabel build $($commit.Substring(0, 12))."
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
