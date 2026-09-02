$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$updater = Join-Path $repoRoot 'client\MoonFlower-Update.ps1'
$manifestGenerator = Join-Path $PSScriptRoot 'New-MoonFlowerUpdateManifest.ps1'
$deltaGenerator = Join-Path $PSScriptRoot 'New-MoonFlowerDeltaPackage.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("moonflower-updater-check-" + [Guid]::NewGuid().ToString('N'))
$packageOneRoot = Join-Path $testRoot 'package-one'
$packageTwoRoot = Join-Path $testRoot 'package-two'
$cacheRoot = Join-Path $testRoot 'cache'
$archiveOnePath = Join-Path $testRoot 'moonflower-one.zip'
$archiveTwoPath = Join-Path $testRoot 'moonflower-two.zip'
$publishedFeedOnePath = Join-Path $testRoot 'published-one.json'
$feedOnePath = Join-Path $testRoot 'feed-one.json'
$feedTwoPath = Join-Path $testRoot 'feed-two.json'
$deltaPackagePath = Join-Path $testRoot 'moonflower-two.patch.zip'
$deltaFeedPath = Join-Path $testRoot 'delta-feed.json'
$deltaFallbackFeedPath = Join-Path $testRoot 'delta-fallback-feed.json'
$schemaTwoFeedPath = Join-Path $testRoot 'schema-two-feed.json'
$legacyFeedPath = Join-Path $testRoot 'legacy-feed.json'
$corruptFeedPath = Join-Path $testRoot 'corrupt-feed.json'
$commitOne = '0123456789abcdef0123456789abcdef01234567'
$commitTwo = '89abcdef0123456789abcdef0123456789abcdef'
$commitThree = 'fedcba9876543210fedcba9876543210fedcba98'

foreach ($launcherName in @('Play.bat', 'Play_WithSteam.bat')) {
    $launcherPath = Join-Path $repoRoot "client\$launcherName"
    $launcher = Get-Content -LiteralPath $launcherPath -Raw
    if ($launcher -notmatch 'set "PSModulePath="') {
        throw "$launcherName does not isolate Windows PowerShell from inherited module paths."
    }
}

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

function Read-StatePath {
    $state = Get-Content -LiteralPath (Join-Path $cacheRoot 'current.json') -Raw | ConvertFrom-Json
    return [string]$state.path
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
    Set-Content -LiteralPath (Join-Path $packageOneRoot 'obsolete.txt') -Value 'remove-me' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $packageOneRoot 'unchanged.txt') -Value 'keep-me' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $packageTwoRoot 'unchanged.txt') -Value 'keep-me' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $packageTwoRoot 'new-file.txt') -Value 'new-file' -Encoding UTF8
    Compress-Archive -Path (Join-Path $packageOneRoot '*') -DestinationPath $archiveOnePath
    Compress-Archive -Path (Join-Path $packageTwoRoot '*') -DestinationPath $archiveTwoPath

    & $manifestGenerator `
        -PackagePath $archiveOnePath `
        -OutputPath $publishedFeedOnePath `
        -Commit $commitOne `
        -AssetUrl "https://github.com/ReleMai/Moonflower/releases/download/moonflower-build-$commitOne/moonflower-client.zip" `
        -Repository 'ReleMai/Moonflower'

    $publishedOne = Read-Feed $publishedFeedOnePath
    if ($publishedOne.schemaVersion -ne 1 -or $null -ne $publishedOne.previous) {
        throw 'A first backward-compatible feed must not invent a previous build.'
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

    & $deltaGenerator `
        -PreviousPackagePath $archiveOnePath `
        -CurrentPackagePath $archiveTwoPath `
        -FromCommit $commitOne `
        -ToCommit $commitTwo `
        -OutputPath $deltaPackagePath

    & $manifestGenerator `
        -PackagePath $archiveTwoPath `
        -OutputPath $feedTwoPath `
        -Commit $commitTwo `
        -AssetUrl "https://github.com/ReleMai/Moonflower/releases/download/moonflower-build-$commitTwo/moonflower-client.zip" `
        -Repository 'ReleMai/Moonflower' `
        -PreviousFeedPath $publishedFeedOnePath `
        -PatchPath $deltaPackagePath `
        -PatchUrl "https://github.com/ReleMai/Moonflower/releases/download/moonflower-build-$commitTwo/moonflower-client.patch.zip" `
        -PatchFromCommit $commitOne

    $feedTwo = Read-Feed $feedTwoPath
    if ($feedTwo.schemaVersion -ne 1 -or
        [string]$feedTwo.previous.commit -ne $commitOne -or
        [string]$feedTwo.patch.fromCommit -ne $commitOne) {
        throw 'The schema 1 extension did not preserve the previous verified build.'
    }
    $feedTwoRaw = Get-Content -LiteralPath $feedTwoPath -Raw
    $isoTimestamps = [regex]::Matches($feedTwoRaw, '"publishedAt"\s*:\s*"\d{4}-\d{2}-\d{2}T[^" ]+(?:Z|[+-]\d{2}:\d{2})"')
    if ($isoTimestamps.Count -ne 2) {
        throw 'Extended feed timestamps are not normalized ISO-8601 values.'
    }
    $feedTwo.package.url = $archiveTwoPath
    $feedTwo.previous.package.url = $archiveOnePath
    Write-Feed $feedTwo $feedTwoPath

    $deltaFeed = Read-Feed $feedTwoPath
    $deltaFeed.package.url = Join-Path $testRoot 'full-package-must-not-be-read.zip'
    $deltaFeed.patch.url = $deltaPackagePath
    Write-Feed $deltaFeed $deltaFeedPath
    Invoke-UpdaterCheck -FeedPath $deltaFeedPath
    if ((Read-StateCommit) -ne $commitTwo) {
        throw 'Delta install did not activate the new build.'
    }
    $activeRoot = Read-StatePath
    $activeJar = Get-Content -LiteralPath (Join-Path $activeRoot 'hafen.jar') -Raw
    if ($activeJar -ne "two-hafen.jar`r`n" -and $activeJar -ne "two-hafen.jar`n") {
        throw 'Delta install did not apply the changed payload.'
    }
    if ((Test-Path -LiteralPath (Join-Path $activeRoot 'obsolete.txt') -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $activeRoot 'new-file.txt') -PathType Leaf)) {
        throw 'Delta install did not apply file additions and deletions.'
    }

    Invoke-UpdaterCheck -FeedPath $feedTwoPath
    Invoke-UpdaterCheck -FeedPath $feedTwoPath -Rollback
    Remove-Item -LiteralPath (Join-Path $cacheRoot "versions\$commitTwo") -Recurse -Force
    $deltaFallbackFeed = Read-Feed $feedTwoPath
    $deltaFallbackFeed.patch.url = $archiveTwoPath
    Write-Feed $deltaFallbackFeed $deltaFallbackFeedPath
    Invoke-UpdaterCheck -FeedPath $deltaFallbackFeedPath
    if ((Read-StateCommit) -ne $commitTwo) {
        throw 'A failed delta download did not fall back to the full package.'
    }

    $schemaTwo = Read-Feed $feedTwoPath
    $schemaTwo.schemaVersion = 2
    Write-Feed $schemaTwo $schemaTwoFeedPath
    Invoke-UpdaterCheck -FeedPath $schemaTwoFeedPath -CheckOnly

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

    Write-Host 'MoonFlower updater checks passed: schema compatibility, delta install, full-package fallback, stable install, cached/downloaded rollback, and corruption fallback.'
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
