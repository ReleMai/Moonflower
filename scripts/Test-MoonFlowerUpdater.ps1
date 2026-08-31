$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$updater = Join-Path $repoRoot 'client\MoonFlower-Update.ps1'
$manifestGenerator = Join-Path $PSScriptRoot 'New-MoonFlowerUpdateManifest.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("moonflower-updater-check-" + [Guid]::NewGuid().ToString('N'))
$packageOneRoot = Join-Path $testRoot 'package-one'
$packageTwoRoot = Join-Path $testRoot 'package-two'
$cacheRoot = Join-Path $testRoot 'cache'
$archiveOnePath = Join-Path $testRoot 'moonflower-one.zip'
$archiveTwoPath = Join-Path $testRoot 'moonflower-two.zip'
$publishedFeedOnePath = Join-Path $testRoot 'published-one.json'
$feedOnePath = Join-Path $testRoot 'feed-one.json'
$feedTwoPath = Join-Path $testRoot 'feed-two.json'
$legacyFeedPath = Join-Path $testRoot 'legacy-feed.json'
$corruptFeedPath = Join-Path $testRoot 'corrupt-feed.json'
$commitOne = '0123456789abcdef0123456789abcdef01234567'
$commitTwo = '89abcdef0123456789abcdef0123456789abcdef'
$commitThree = 'fedcba9876543210fedcba9876543210fedcba98'

function New-TestPackage {
    param([string]$Root, [string]$Label)

    New-Item -ItemType Directory -Path $Root -Force | Out-Null
    foreach ($file in @('hafen.jar', 'manifest.json', 'Play.bat', 'MoonFlower-Update.ps1')) {
        Set-Content -LiteralPath (Join-Path $Root $file) -Value "$Label-$file" -Encoding UTF8
    }
}

function Read-Feed {
    param([string]$Path)
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

function Write-Feed {
    param($Feed, [string]$Path)
    $Feed | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Read-StateCommit {
    $state = Get-Content -LiteralPath (Join-Path $cacheRoot 'current.json') -Raw | ConvertFrom-Json
    return [string]$state.commit
}

function Invoke-UpdaterCheck {
    param(
        [string]$FeedPath,
        [switch]$Rollback,
        [switch]$CheckOnly,
        [int]$ExpectedExitCode = 0
    )

    $parameters = @{
        FeedPath = $FeedPath
        CacheRoot = $cacheRoot
        CurrentPackageRoot = $packageOneRoot
        NoLaunch = $true
        Rollback = [bool]$Rollback
        CheckOnly = [bool]$CheckOnly
    }
    & $updater @parameters
    if ($LASTEXITCODE -ne $ExpectedExitCode) {
        throw "Updater check failed with exit code $LASTEXITCODE; expected $ExpectedExitCode."
    }
}

try {
    New-TestPackage $packageOneRoot 'one'
    New-TestPackage $packageTwoRoot 'two'
    Compress-Archive -Path (Join-Path $packageOneRoot '*') -DestinationPath $archiveOnePath
    Compress-Archive -Path (Join-Path $packageTwoRoot '*') -DestinationPath $archiveTwoPath

    & $manifestGenerator `
        -PackagePath $archiveOnePath `
        -OutputPath $publishedFeedOnePath `
        -Commit $commitOne `
        -AssetUrl "https://github.com/ReleMai/Moonflower/releases/download/moonflower-build-$commitOne/moonflower-client.zip" `
        -Repository 'ReleMai/Moonflower'

    $publishedOne = Read-Feed $publishedFeedOnePath
    if ($publishedOne.schemaVersion -ne 2 -or $null -ne $publishedOne.previous) {
        throw 'A first schema 2 feed must not invent a previous build.'
    }

    $localOne = Read-Feed $publishedFeedOnePath
    $localOne.package.url = $archiveOnePath
    Write-Feed $localOne $feedOnePath

    $legacy = Read-Feed $feedOnePath
    $legacy.schemaVersion = 1
    $legacy.PSObject.Properties.Remove('previous')
    Write-Feed $legacy $legacyFeedPath
    Invoke-UpdaterCheck -FeedPath $legacyFeedPath
    if ((Read-StateCommit) -ne $commitOne) {
        throw 'Schema 1 compatibility did not activate the expected build.'
    }

    & $manifestGenerator `
        -PackagePath $archiveTwoPath `
        -OutputPath $feedTwoPath `
        -Commit $commitTwo `
        -AssetUrl "https://github.com/ReleMai/Moonflower/releases/download/moonflower-build-$commitTwo/moonflower-client.zip" `
        -Repository 'ReleMai/Moonflower' `
        -PreviousFeedPath $publishedFeedOnePath

    $feedTwo = Read-Feed $feedTwoPath
    if ($feedTwo.schemaVersion -ne 2 -or [string]$feedTwo.previous.commit -ne $commitOne) {
        throw 'Schema 2 feed did not preserve the previous verified build.'
    }
    $feedTwo.package.url = $archiveTwoPath
    $feedTwo.previous.package.url = $archiveOnePath
    Write-Feed $feedTwo $feedTwoPath

    Invoke-UpdaterCheck -FeedPath $feedTwoPath
    if ((Read-StateCommit) -ne $commitTwo) {
        throw 'Stable schema 2 install did not activate the new build.'
    }

    Invoke-UpdaterCheck -FeedPath $feedTwoPath -Rollback -CheckOnly
    Invoke-UpdaterCheck -FeedPath $feedTwoPath -Rollback
    if ((Read-StateCommit) -ne $commitOne) {
        throw 'Cached rollback did not activate the previous build.'
    }

    Invoke-UpdaterCheck -FeedPath $feedTwoPath
    Remove-Item -LiteralPath (Join-Path $cacheRoot "versions\$commitOne") -Recurse -Force
    Invoke-UpdaterCheck -FeedPath $feedTwoPath -Rollback
    if ((Read-StateCommit) -ne $commitOne -or
        -not (Test-Path -LiteralPath (Join-Path $cacheRoot "versions\$commitOne\hafen.jar") -PathType Leaf)) {
        throw 'Downloaded rollback did not verify and install the previous build.'
    }

    Invoke-UpdaterCheck -FeedPath $feedOnePath -Rollback -CheckOnly -ExpectedExitCode 1
    if ((Read-StateCommit) -ne $commitOne) {
        throw 'An unavailable rollback changed the active build.'
    }

    Invoke-UpdaterCheck -FeedPath $feedTwoPath
    $corrupt = Read-Feed $feedTwoPath
    $corrupt.commit = $commitThree
    $corrupt.package.sha256 = ('0' * 64)
    Write-Feed $corrupt $corruptFeedPath
    Invoke-UpdaterCheck -FeedPath $corruptFeedPath
    if ((Read-StateCommit) -ne $commitTwo) {
        throw 'A failed package verification replaced the last-known-good version.'
    }

    Write-Host 'MoonFlower updater checks passed: schema compatibility, stable install, cached/downloaded rollback, and corruption fallback.'
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
