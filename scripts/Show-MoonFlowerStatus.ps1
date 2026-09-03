[CmdletBinding()]
param(
    [string]$RepoPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$PackageRoot,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
$RepoPath = [System.IO.Path]::GetFullPath($RepoPath)

function Invoke-GitText {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = @(& git -C $RepoPath @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git -C `"$RepoPath`" $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }
    return (($output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine).Trim()
}

function Read-BuildRevision {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }
    $text = Get-Content -LiteralPath $Path -Raw
    $match = [regex]::Match($text, '(?im)^\s*git-rev\s*=\s*([0-9a-f]{40})')
    if ($match.Success) {
        return $match.Groups[1].Value
    }
    return $null
}

function Read-PackagedRevision {
    param([string]$JarPath)

    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        return $null
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entry = $archive.GetEntry('buildinfo')
        if ($null -eq $entry) {
            $entry = $archive.Entries | Where-Object { $_.FullName -eq 'buildinfo' } | Select-Object -First 1
        }
        if ($null -eq $entry) {
            return $null
        }

        $reader = New-Object System.IO.StreamReader($entry.Open())
        try {
            $text = $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
        $match = [regex]::Match($text, '(?im)^\s*git-rev\s*=\s*([0-9a-f]{40})')
        if ($match.Success) {
            return $match.Groups[1].Value
        }
        return $null
    } finally {
        $archive.Dispose()
    }
}

$branch = Invoke-GitText @('branch', '--show-current')
if ([string]::IsNullOrWhiteSpace($branch)) {
    $branch = '(detached HEAD)'
}
$sourceCommit = Invoke-GitText @('rev-parse', 'HEAD')
$statusLines = @(& git -C $RepoPath status --porcelain=v1 --untracked-files=all 2>$null)
$worktreeState = if ($statusLines.Count -eq 0) { 'clean' } else { 'dirty' }

$buildInfoPath = Join-Path $RepoPath 'client\build\classes\buildinfo'
$buildRevision = Read-BuildRevision $buildInfoPath
if ([string]::IsNullOrWhiteSpace($PackageRoot)) {
    $PackageRoot = Join-Path $RepoPath 'client\bin'
}
$PackageRoot = [System.IO.Path]::GetFullPath($PackageRoot)
$jarPath = Join-Path $PackageRoot 'hafen.jar'
$packageRevision = Read-PackagedRevision $jarPath
$packageUpdated = $null
if (Test-Path -LiteralPath $jarPath -PathType Leaf) {
    $packageUpdated = (Get-Item -LiteralPath $jarPath).LastWriteTime.ToString('s')
}

$buildState = if ($null -eq $buildRevision) {
    'NOT_BUILT'
} elseif ($buildRevision -eq $sourceCommit) {
    'MATCH'
} else {
    'MISMATCH'
}
$packageState = if ($null -eq $packageRevision) {
    'NOT_PACKAGED'
} elseif ($packageRevision -eq $sourceCommit) {
    'MATCH'
} else {
    'MISMATCH'
}

$result = [ordered]@{
    SourceBranch = $branch
    SourceCommit = $sourceCommit
    Worktree = $worktreeState
    BuildClassesRevision = $buildRevision
    BuildClassesState = $buildState
    PackagedJar = $jarPath
    PackagedJarRevision = $packageRevision
    PackagedJarUpdated = $packageUpdated
    PackagedJarState = $packageState
    LocalTestingLaunch = '.\client\Play.bat -NoUpdate'
    StableLaunch = '.\client\Play.bat'
}

if ($Json) {
    [pscustomobject]$result | ConvertTo-Json -Depth 3
    exit 0
}

Write-Output 'MoonFlower client status'
Write-Output ("  source branch : " + $result.SourceBranch)
Write-Output ("  source commit : " + $result.SourceCommit)
Write-Output ("  worktree     : " + $result.Worktree)
Write-Output ("  build classes: " + $result.BuildClassesState + $(if ($null -ne $result.BuildClassesRevision) { " ($($result.BuildClassesRevision))" } else { '' }))
Write-Output ("  packaged JAR  : " + $result.PackagedJarState + $(if ($null -ne $result.PackagedJarRevision) { " ($($result.PackagedJarRevision))" } else { '' }))
Write-Output ("  JAR path      : " + $result.PackagedJar)
if ($null -ne $result.PackagedJarUpdated) {
    Write-Output ("  JAR updated   : " + $result.PackagedJarUpdated)
}
Write-Output '  local test    : .\client\Play.bat -NoUpdate'
Write-Output '  stable launch : .\client\Play.bat'
