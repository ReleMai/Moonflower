[CmdletBinding()]
param(
    [string]$RepoPath,
    [string]$Remote = 'origin',
    [string]$Branch = 'main',
    [string]$StateRoot,
    [switch]$Build,
    [switch]$CheckOnly,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
$script:ExitCode = 0
$script:Quiet = [bool]$Quiet
$script:RepoRoot = $null
$script:LogPath = $null

function ConvertTo-RedactedText {
    param([AllowNull()][string]$Text)

    if ($null -eq $Text) {
        return ''
    }
    return $Text -replace '(?i)(https?://)([^/\s@]+)@', '$1***@'
}

function Write-SourceSyncLog {
    param(
        [string]$Message,
        [ValidateSet('INFO', 'WARN', 'ERROR')][string]$Level = 'INFO'
    )

    $safeMessage = ConvertTo-RedactedText $Message
    $line = '{0} [{1}] {2}' -f [DateTimeOffset]::Now.ToString('o'), $Level, $safeMessage
    if (-not $script:Quiet) {
        switch ($Level) {
            'WARN' { Write-Warning $safeMessage }
            'ERROR' { Write-Error $safeMessage -ErrorAction Continue }
            default { Write-Host $safeMessage }
        }
    }
    if ($script:LogPath) {
        Add-Content -LiteralPath $script:LogPath -Value $line -Encoding UTF8
    }
}

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& git -C $script:RepoRoot @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        $detail = ConvertTo-RedactedText (($output | Select-Object -First 8) -join ' ')
        if ([string]::IsNullOrWhiteSpace($detail)) {
            $detail = 'no diagnostic output'
        }
        throw ('Git operation failed with exit code {0}: {1}' -f $exitCode, $detail)
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Assert-RefComponent {
    param(
        [string]$Value,
        [string]$Label
    )

    if ([string]::IsNullOrWhiteSpace($Value) -or
        $Value.StartsWith('-') -or
        $Value.Contains('\') -or
        $Value.Contains([char]0) -or
        $Value -match '[\x00-\x20~^:?*\[]' -or
        $Value.Contains('..') -or
        $Value.Contains('//') -or
        $Value.StartsWith('/') -or
        $Value.EndsWith('/') -or
        $Value.EndsWith('.lock', [StringComparison]::OrdinalIgnoreCase) -or
        $Value -match '(^|/)\.' -or
        $Value -match '@\{') {
        throw ('The {0} is not a safe Git ref component: {1}' -f $Label, $Value)
    }
}

function Enter-SourceSyncLock {
    param([string]$LockPath)

    try {
        return [System.IO.File]::Open(
            $LockPath,
            [System.IO.FileMode]::OpenOrCreate,
            [System.IO.FileAccess]::ReadWrite,
            [System.IO.FileShare]::None)
    } catch [System.IO.IOException] {
        return $null
    }
}

function Get-RepositoryRoot {
    param([string]$RequestedPath)

    if ([string]::IsNullOrWhiteSpace($RequestedPath)) {
        $RequestedPath = Split-Path -Parent $PSScriptRoot
    }
    if (-not (Test-Path -LiteralPath $RequestedPath -PathType Container)) {
        throw "Repository path does not exist: $RequestedPath"
    }
    $resolvedPath = (Resolve-Path -LiteralPath $RequestedPath).Path
    $script:RepoRoot = [System.IO.Path]::GetFullPath($resolvedPath)

    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw 'Git is not available on PATH.'
    }
    $result = Invoke-Git @('rev-parse', '--show-toplevel')
    $reportedRoot = $result.Output | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($reportedRoot)) {
        throw "Git did not report a repository root for $RequestedPath"
    }
    $script:RepoRoot = [System.IO.Path]::GetFullPath($reportedRoot.Trim())
    return $script:RepoRoot
}

function Get-CurrentBranch {
    $result = Invoke-Git @('symbolic-ref', '--quiet', '--short', 'HEAD') -AllowFailure
    if ($result.ExitCode -ne 0) {
        return $null
    }
    return ($result.Output | Select-Object -First 1).Trim()
}

function Get-WorkingTreeChanges {
    $result = Invoke-Git @('status', '--porcelain=v1', '--untracked-files=all')
    return @($result.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-Commit {
    param([string]$Revision)

    $result = Invoke-Git @('rev-parse', '--verify', ($Revision + '^{commit}'))
    return ($result.Output | Select-Object -First 1).Trim()
}

function Test-IsAncestor {
    param(
        [string]$OlderRevision,
        [string]$NewerRevision
    )

    $result = Invoke-Git @('merge-base', '--is-ancestor', $OlderRevision, $NewerRevision) -AllowFailure
    if ($result.ExitCode -gt 1) {
        $detail = ConvertTo-RedactedText (($result.Output | Select-Object -First 4) -join ' ')
        throw ('Git ancestry check failed with exit code {0}: {1}' -f $result.ExitCode, $detail)
    }
    return $result.ExitCode -eq 0
}

function Invoke-SourceSync {
    Assert-RefComponent $Remote 'remote'
    Assert-RefComponent $Branch 'branch'

    $currentBranch = Get-CurrentBranch
    if ([string]::IsNullOrWhiteSpace($currentBranch)) {
        Write-SourceSyncLog 'Skipping: the checkout is detached; automatic sync only updates a named branch.' 'WARN'
        return
    }
    if ($currentBranch -ne $Branch) {
        Write-SourceSyncLog ("Skipping: checkout is on '{0}', but this task is configured for '{1}'." -f $currentBranch, $Branch) 'WARN'
        return
    }

    $changes = Get-WorkingTreeChanges
    if ($changes.Count -gt 0) {
        Write-SourceSyncLog ("Skipping: checkout has {0} local change(s). No files or refs were changed." -f $changes.Count) 'WARN'
        return
    }

    $remoteRef = 'refs/remotes/{0}/{1}' -f $Remote, $Branch
    $fetchRefspec = 'refs/heads/{0}:refs/remotes/{1}/{0}' -f $Branch, $Remote
    Write-SourceSyncLog ("Fetching {0}/{1}." -f $Remote, $Branch)
    $null = Invoke-Git @('fetch', '--prune', $Remote, $fetchRefspec)

    $localCommit = Get-Commit 'HEAD'
    $remoteCommit = Get-Commit $remoteRef
    if ($localCommit -eq $remoteCommit) {
        Write-SourceSyncLog ("Already current at {0}." -f $localCommit.Substring(0, 12))
        if ($Build -and -not (Test-Path -LiteralPath (Join-Path $script:RepoRoot 'client\bin\hafen.jar') -PathType Leaf)) {
            Invoke-ClientBuild
        }
        return
    }

    if (Test-IsAncestor $remoteRef 'HEAD') {
        Write-SourceSyncLog ("Skipping: local branch is ahead of {0}; automatic sync never rewinds it." -f $Remote) 'WARN'
        return
    }
    if (-not (Test-IsAncestor 'HEAD' $remoteRef)) {
        Write-SourceSyncLog ("Skipping: local branch and {0}/{1} have diverged; manual review is required." -f $Remote, $Branch) 'WARN'
        return
    }

    if ($CheckOnly) {
        Write-SourceSyncLog ("Update available: {0} -> {1}." -f $localCommit.Substring(0, 12), $remoteCommit.Substring(0, 12))
        $script:ExitCode = 2
        return
    }

    Write-SourceSyncLog ("Fast-forwarding {0} -> {1}." -f $localCommit.Substring(0, 12), $remoteCommit.Substring(0, 12))
    $null = Invoke-Git @('merge', '--ff-only', '--no-edit', $remoteRef)
    Write-SourceSyncLog ("Source checkout is now at {0}." -f $remoteCommit.Substring(0, 12))

    if ($Build) {
        Invoke-ClientBuild
    }
}

function Invoke-ClientBuild {
    $guardPath = Join-Path $script:RepoRoot 'scripts\assert-client-stopped.ps1'
    if (-not (Test-Path -LiteralPath $guardPath -PathType Leaf)) {
        throw "The client deployment guard is missing: $guardPath"
    }
    try {
        & $guardPath
    } catch {
        Write-SourceSyncLog ("Skipping build: {0}" -f $_.Exception.Message) 'WARN'
        return
    }

    if (-not (Get-Command ant -ErrorAction SilentlyContinue)) {
        throw 'Ant is not available on PATH; source was updated but the client could not be rebuilt.'
    }

    $clientPath = Join-Path $script:RepoRoot 'client'
    Write-SourceSyncLog 'Building the updated client with ant clean deftgt.'
    Push-Location $clientPath
    try {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $buildOutput = @(& ant clean deftgt 2>&1 | ForEach-Object { [string]$_ })
            $buildExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
    } finally {
        Pop-Location
    }
    foreach ($line in $buildOutput) {
        $safeLine = ConvertTo-RedactedText $line
        if ($script:LogPath) {
            Add-Content -LiteralPath $script:LogPath -Value $safeLine -Encoding UTF8
        }
        if (-not $script:Quiet) {
            Write-Host $safeLine
        }
    }
    if ($buildExitCode -ne 0) {
        throw ('Ant build failed with exit code {0}. See {1}.' -f $buildExitCode, $script:LogPath)
    }
    Write-SourceSyncLog 'Client build completed.'
}

try {
    $null = Get-RepositoryRoot $RepoPath
    Assert-RefComponent $Remote 'remote'
    Assert-RefComponent $Branch 'branch'

    if ([string]::IsNullOrWhiteSpace($StateRoot)) {
        $localData = [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
        if ([string]::IsNullOrWhiteSpace($localData)) {
            throw 'Windows LocalApplicationData is unavailable; specify -StateRoot explicitly.'
        }
        $StateRoot = Join-Path $localData 'MoonFlower\DeveloperTools\SourceSync'
    }
    $StateRoot = [System.IO.Path]::GetFullPath($StateRoot)
    New-Item -ItemType Directory -Path $StateRoot -Force | Out-Null
    $script:LogPath = Join-Path $StateRoot 'source-sync.log'
    $lockPath = Join-Path $StateRoot 'source-sync.lock'
    $lock = Enter-SourceSyncLock $lockPath
    if ($null -eq $lock) {
        Write-SourceSyncLog 'Skipping: another source-sync run already holds the lock.' 'WARN'
        exit 0
    }
    try {
        Write-SourceSyncLog ("Checking {0} on {1}/{2}." -f $script:RepoRoot, $Remote, $Branch)
        Invoke-SourceSync
    } finally {
        $lock.Dispose()
    }
} catch {
    $message = ConvertTo-RedactedText $_.Exception.Message
    if ($script:LogPath) {
        Write-SourceSyncLog $message 'ERROR'
    } else {
        Write-Error $message
    }
    $script:ExitCode = 1
}

exit $script:ExitCode
