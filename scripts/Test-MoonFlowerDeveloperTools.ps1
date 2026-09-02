[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceSync = Join-Path $PSScriptRoot 'MoonFlower-SourceSync.ps1'
$branchSelector = Join-Path $PSScriptRoot 'BranchSelector.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('moonflower-developer-tools-check-' + [Guid]::NewGuid().ToString('N'))
$remotePath = Join-Path $testRoot 'remote.git'
$publisherPath = Join-Path $testRoot 'publisher'
$runnerPath = Join-Path $testRoot 'runner'
$stateRoot = Join-Path $testRoot 'state'
$worktreeRoot = Join-Path $testRoot 'worktrees'

function Invoke-GitTest {
    param(
        [string]$WorkingPath,
        [string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& git -C $WorkingPath @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw ('Git test command failed with exit code {0}: {1}' -f $exitCode, (($output | Select-Object -First 8) -join ' '))
    }
    return $output
}

function Get-TestCommit {
    param([string]$WorkingPath)
    return (Invoke-GitTest $WorkingPath @('rev-parse', 'HEAD') | Select-Object -First 1).Trim()
}

function Invoke-ChildScript {
    param(
        [string]$ScriptPath,
        [string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $ScriptPath @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Assert-Equal {
    param(
        [string]$Expected,
        [string]$Actual,
        [string]$Message
    )
    if ($Expected -ne $Actual) {
        throw ('{0} Expected: {1}; actual: {2}' -f $Message, $Expected, $Actual)
    }
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

try {
    New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
    & git init --bare $remotePath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize the temporary bare remote.' }
    & git init $publisherPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize the temporary publisher checkout.' }
    $null = Invoke-GitTest $publisherPath @('config', 'user.name', 'MoonFlower developer-tool test')
    $null = Invoke-GitTest $publisherPath @('config', 'user.email', 'developer-tools@example.invalid')
    $null = Invoke-GitTest $publisherPath @('branch', '-M', 'main')
    $null = Invoke-GitTest $publisherPath @('remote', 'add', 'origin', $remotePath)

    Set-Content -LiteralPath (Join-Path $publisherPath 'README.md') -Value 'one' -Encoding UTF8
    $null = Invoke-GitTest $publisherPath @('add', '--', 'README.md')
    $null = Invoke-GitTest $publisherPath @('commit', '-m', 'one')
    $null = Invoke-GitTest $publisherPath @('push', '-u', 'origin', 'main')
    $commitOne = Get-TestCommit $publisherPath

    & git clone --branch main $remotePath $runnerPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not clone the temporary runner checkout.' }
    $null = Invoke-GitTest $runnerPath @('config', 'user.name', 'MoonFlower developer-tool test')
    $null = Invoke-GitTest $runnerPath @('config', 'user.email', 'developer-tools@example.invalid')

    Set-Content -LiteralPath (Join-Path $publisherPath 'README.md') -Value 'two' -Encoding UTF8
    $null = Invoke-GitTest $publisherPath @('add', '--', 'README.md')
    $null = Invoke-GitTest $publisherPath @('commit', '-m', 'two')
    $null = Invoke-GitTest $publisherPath @('push', 'origin', 'main')
    $commitTwo = Get-TestCommit $publisherPath

    $featureBranch = 'feature/test'
    $null = Invoke-GitTest $publisherPath @('checkout', '-b', $featureBranch)
    Set-Content -LiteralPath (Join-Path $publisherPath 'FEATURE.md') -Value 'branch' -Encoding UTF8
    $null = Invoke-GitTest $publisherPath @('add', '--', 'FEATURE.md')
    $null = Invoke-GitTest $publisherPath @('commit', '-m', 'feature branch')
    $null = Invoke-GitTest $publisherPath @('push', '-u', 'origin', $featureBranch)
    $null = Invoke-GitTest $publisherPath @('checkout', 'main')

    $checkOnly = Invoke-ChildScript $sourceSync @(
        '-RepoPath', $runnerPath,
        '-Remote', 'origin',
        '-Branch', 'main',
        '-StateRoot', $stateRoot,
        '-CheckOnly'
    )
    Assert-Equal '2' ([string]$checkOnly.ExitCode) ('Check-only sync should report an available fast-forward. Output: {0}' -f ($checkOnly.Output -join ' | '))
    Assert-Equal $commitOne (Get-TestCommit $runnerPath) 'Check-only sync must not change HEAD.'

    $sync = Invoke-ChildScript $sourceSync @(
        '-RepoPath', $runnerPath,
        '-Remote', 'origin',
        '-Branch', 'main',
        '-StateRoot', $stateRoot
    )
    Assert-Equal '0' ([string]$sync.ExitCode) 'Clean source sync should succeed.'
    Assert-Equal $commitTwo (Get-TestCommit $runnerPath) 'Clean source sync should fast-forward to origin/main.'

    Set-Content -LiteralPath (Join-Path $publisherPath 'README.md') -Value 'three' -Encoding UTF8
    $null = Invoke-GitTest $publisherPath @('add', '--', 'README.md')
    $null = Invoke-GitTest $publisherPath @('commit', '-m', 'three')
    $null = Invoke-GitTest $publisherPath @('push', 'origin', 'main')
    $dirtyMarker = Join-Path $runnerPath 'local-change.txt'
    Set-Content -LiteralPath $dirtyMarker -Value 'must remain' -Encoding UTF8

    $dirtySync = Invoke-ChildScript $sourceSync @(
        '-RepoPath', $runnerPath,
        '-Remote', 'origin',
        '-Branch', 'main',
        '-StateRoot', $stateRoot
    )
    Assert-Equal '0' ([string]$dirtySync.ExitCode) 'Dirty source sync should skip safely.'
    Assert-Equal $commitTwo (Get-TestCommit $runnerPath) 'Dirty source sync must not move HEAD.'
    Assert-True (Test-Path -LiteralPath $dirtyMarker -PathType Leaf) 'Dirty source sync must preserve the untracked file.'

    $branches = Invoke-ChildScript $branchSelector @(
        '-RepoPath', $runnerPath,
        '-Remote', 'origin',
        '-WorktreeRoot', $worktreeRoot,
        '-ListOnly'
    )
    Assert-Equal '0' ([string]$branches.ExitCode) 'Branch list mode should succeed.'
    $branchText = $branches.Output -join "`n"
    Assert-True ($branchText -match '(?m)^main\t') 'Branch list should include main.'
    Assert-True ($branchText -match '(?m)^feature/test\t') 'Branch list should include the remote feature branch.'
    Assert-Equal 'main' ((Invoke-GitTest $runnerPath @('branch', '--show-current') | Select-Object -First 1).Trim()) 'Branch listing must not switch the runner branch.'

    Write-Host 'MoonFlower developer-tool checks passed: clean fast-forward, check-only, dirty checkout protection, and isolated branch discovery.' -ForegroundColor Green
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
