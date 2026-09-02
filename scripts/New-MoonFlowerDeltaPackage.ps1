[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PreviousPackagePath,
    [Parameter(Mandatory = $true)][string]$CurrentPackagePath,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$FromCommit,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$ToCommit,
    [Parameter(Mandatory = $true)][string]$OutputPath
)

$ErrorActionPreference = 'Stop'
$maxExpandedBytes = [int64]1610612736

function Get-NormalizedZipPath {
    param([string]$EntryName)

    if ([string]::IsNullOrWhiteSpace($EntryName)) {
        return $null
    }

    $normalized = $EntryName.Replace('\', '/')
    if ($normalized.EndsWith('/')) {
        return $null
    }
    if ($normalized.StartsWith('/') -or
        $normalized -match '^[A-Za-z]:' -or
        $normalized.Contains([char]0) -or
        $normalized -match '(^|/)\.{1,2}(/|$)') {
        throw "The package contains an unsafe path: $EntryName"
    }
    return $normalized
}

function Get-ZipEntryMap {
    param([System.IO.Compression.ZipArchive]$Archive)

    $entries = @{}
    foreach ($entry in $Archive.Entries) {
        $path = Get-NormalizedZipPath $entry.FullName
        if ($null -eq $path) {
            continue
        }
        if ($entries.ContainsKey($path)) {
            throw "The package contains duplicate file entries: $path"
        }
        $entries[$path] = $entry
    }
    return $entries
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

function New-FileDescriptor {
    param(
        [string]$Path,
        [int64]$Size,
        [string]$Sha256
    )

    return [ordered]@{
        path = $Path
        size = $Size
        sha256 = $Sha256
    }
}

$previousFullPath = [System.IO.Path]::GetFullPath($PreviousPackagePath)
$currentFullPath = [System.IO.Path]::GetFullPath($CurrentPackagePath)
$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
foreach ($path in @($previousFullPath, $currentFullPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Package archive was not found: $path"
    }
}
if ($previousFullPath -eq $currentFullPath -or
    $outputFullPath -eq $previousFullPath -or
    $outputFullPath -eq $currentFullPath) {
    throw 'The delta output must be different from both package archives.'
}

$outputParent = Split-Path -Parent $outputFullPath
if ($outputParent) {
    New-Item -ItemType Directory -Path $outputParent -Force | Out-Null
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$previousArchive = [System.IO.Compression.ZipFile]::OpenRead($previousFullPath)
$currentArchive = [System.IO.Compression.ZipFile]::OpenRead($currentFullPath)
$patchStream = $null
$patchArchive = $null
try {
    $previousEntries = Get-ZipEntryMap $previousArchive
    $currentEntries = Get-ZipEntryMap $currentArchive
    $targetFiles = New-Object System.Collections.ArrayList
    $changedFiles = New-Object System.Collections.ArrayList
    $deletedFiles = New-Object System.Collections.ArrayList
    $targetExpandedBytes = [int64]0

    foreach ($path in @($currentEntries.Keys | Sort-Object)) {
        $currentEntry = $currentEntries[$path]
        $targetExpandedBytes += [int64]$currentEntry.Length
        if ($targetExpandedBytes -gt $maxExpandedBytes) {
            throw 'The current package exceeds the 1.5 GB expanded safety limit.'
        }

        $currentHash = Get-ZipEntrySha256 $currentEntry
        $descriptor = New-FileDescriptor $path ([int64]$currentEntry.Length) $currentHash
        [void]$targetFiles.Add($descriptor)

        $previousEntry = $previousEntries[$path]
        $changed = $true
        if ($null -ne $previousEntry -and
            [int64]$previousEntry.Length -eq [int64]$currentEntry.Length) {
            $changed = (Get-ZipEntrySha256 $previousEntry) -ne $currentHash
        }
        if ($changed) {
            [void]$changedFiles.Add($descriptor)
        }
    }

    foreach ($path in @($previousEntries.Keys | Sort-Object)) {
        if (-not $currentEntries.ContainsKey($path)) {
            [void]$deletedFiles.Add($path)
        }
    }

    $metadata = [ordered]@{
        schemaVersion = 1
        fromCommit = $FromCommit.ToLowerInvariant()
        commit = $ToCommit.ToLowerInvariant()
        files = @($targetFiles)
        changed = @($changedFiles)
        deleted = @($deletedFiles)
    }

    $patchStream = [System.IO.File]::Open($outputFullPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    $patchArchive = [System.IO.Compression.ZipArchive]::new($patchStream, [System.IO.Compression.ZipArchiveMode]::Create, $false)

    $metadataEntry = $patchArchive.CreateEntry('.moonflower-patch.json', [System.IO.Compression.CompressionLevel]::Optimal)
    $metadataWriter = [System.IO.StreamWriter]::new($metadataEntry.Open(), [System.Text.UTF8Encoding]::new($false))
    try {
        $metadataWriter.Write(($metadata | ConvertTo-Json -Depth 8))
    } finally {
        $metadataWriter.Dispose()
    }

    foreach ($descriptor in @($changedFiles)) {
        $currentEntry = $currentEntries[[string]$descriptor.path]
        $patchEntry = $patchArchive.CreateEntry([string]$descriptor.path, [System.IO.Compression.CompressionLevel]::Optimal)
        $input = $currentEntry.Open()
        $output = $patchEntry.Open()
        try {
            $input.CopyTo($output, 1048576)
        } finally {
            $output.Dispose()
            $input.Dispose()
        }
    }
} finally {
    if ($null -ne $patchArchive) {
        $patchArchive.Dispose()
    }
    if ($null -ne $patchStream) {
        $patchStream.Dispose()
    }
    $currentArchive.Dispose()
    $previousArchive.Dispose()
}

$patchInfo = Get-Item -LiteralPath $outputFullPath
Write-Host "Created MoonFlower delta package: $($changedFiles.Count) changed, $($deletedFiles.Count) deleted, $($targetFiles.Count) target files, $($patchInfo.Length) bytes."
