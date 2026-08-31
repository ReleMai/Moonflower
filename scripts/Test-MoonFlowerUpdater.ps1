$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$updater = Join-Path $repoRoot 'client\MoonFlower-Update.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("moonflower-updater-check-" + [Guid]::NewGuid().ToString('N'))
$packageRoot = Join-Path $testRoot 'package'
$cacheRoot = Join-Path $testRoot 'cache'
$archivePath = Join-Path $testRoot 'moonflower-client.zip'
$feedPath = Join-Path $testRoot 'moonflower-update.json'
$commit = '0123456789abcdef0123456789abcdef01234567'

try {
    New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null
    foreach ($file in @('hafen.jar', 'manifest.json', 'Play.bat', 'MoonFlower-Update.ps1')) {
        Set-Content -LiteralPath (Join-Path $packageRoot $file) -Value "test-$file" -Encoding UTF8
    }
    Compress-Archive -Path (Join-Path $packageRoot '*') -DestinationPath $archivePath
    & (Join-Path $PSScriptRoot 'New-MoonFlowerUpdateManifest.ps1') `
        -PackagePath $archivePath `
        -OutputPath $feedPath `
        -Commit $commit `
        -AssetUrl 'https://github.com/ReleMai/Moonflower/releases/download/moonflower-latest/moonflower-client.zip' `
        -Repository 'ReleMai/Moonflower'

    $feed = Get-Content -LiteralPath $feedPath -Raw | ConvertFrom-Json
    $feed.package.url = $archivePath
    $feed | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $feedPath -Encoding UTF8

    & $updater -FeedPath $feedPath -CacheRoot $cacheRoot -CurrentPackageRoot $packageRoot -NoLaunch
    if ($LASTEXITCODE -ne 0) {
        throw "Updater install check failed with exit code $LASTEXITCODE."
    }
    $installedJar = Join-Path $cacheRoot "versions\$commit\hafen.jar"
    if (-not (Test-Path -LiteralPath $installedJar -PathType Leaf)) {
        throw 'Updater did not install the expected versioned JAR.'
    }

    $feed.package.sha256 = ('0' * 64)
    $feed.commit = 'fedcba9876543210fedcba9876543210fedcba98'
    $feed | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $feedPath -Encoding UTF8
    & $updater -FeedPath $feedPath -CacheRoot $cacheRoot -CurrentPackageRoot $packageRoot -NoLaunch
    if ($LASTEXITCODE -ne 0) {
        throw "Updater fallback check failed with exit code $LASTEXITCODE."
    }
    $state = Get-Content -LiteralPath (Join-Path $cacheRoot 'current.json') -Raw | ConvertFrom-Json
    if ($state.commit -ne $commit) {
        throw 'A failed package verification replaced the last-known-good version.'
    }

    Write-Host 'MoonFlower updater checks passed.'
} finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}
