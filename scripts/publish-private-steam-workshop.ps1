[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string]$ExpectedManifestSha256,

    [switch]$ConfirmPrivateUpload,
    [string]$Message = "Private MoonFlower build"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$clientBin = Join-Path (Join-Path $repoRoot "client") "bin"
$privateRoot = Join-Path (Join-Path $repoRoot ".recovery") "steam-workshop"
$stagePath = Join-Path $privateRoot "package"
$manifestPath = Join-Path $stagePath "private-publish-manifest.json"
$metadataPath = Join-Path $stagePath "workshop-client.properties"
$localWorkshopIdPath = Join-Path $privateRoot "workshop-id.txt"

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

if (-not $ConfirmPrivateUpload) {
    throw "External Steam upload is disabled. Re-run with -ConfirmPrivateUpload only after reviewing the exact package and manifest hash."
}
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Private package manifest is missing. Run scripts/prepare-private-steam-workshop.ps1 first."
}
if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
    throw "Staged workshop-client.properties is missing."
}

$actualManifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualManifestHash -ne $ExpectedManifestSha256.ToLowerInvariant()) {
    throw "Manifest hash mismatch. Expected $ExpectedManifestSha256 but found $actualManifestHash. Re-audit the package."
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.appId -ne 3051280 -or $manifest.visibility -ne "private") {
    throw "Manifest is not locked to Haven & Hearth AppID 3051280 with private visibility."
}

$actualFiles = Get-ChildItem -LiteralPath $stagePath -File -Recurse |
    Where-Object { $_.FullName -ne $manifestPath }
if ($actualFiles.Count -ne $manifest.files.Count) {
    throw "Package contents changed after preparation. Expected $($manifest.files.Count) files but found $($actualFiles.Count)."
}

foreach ($entry in $manifest.files) {
    $path = Join-Path $stagePath ($entry.path.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Manifest file is missing: $($entry.path)"
    }
    $file = Get-Item -LiteralPath $path
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($file.Length -ne $entry.size -or $hash -ne $entry.sha256) {
        throw "Manifest verification failed for $($entry.path)."
    }
}

$name = Get-PropertyValue $metadataPath "name"
$visibility = Get-PropertyValue $metadataPath "visibility"
$workshopId = Get-PropertyValue $metadataPath "workshop-id"
$appId = (Get-Content -LiteralPath (Join-Path $stagePath "steam_appid.txt") -Raw).Trim()
if ($name -ne "MoonFlower" -or $visibility -ne "private" -or $appId -ne "3051280") {
    throw "Staged metadata is not locked to private MoonFlower publishing under AppID 3051280."
}
if ($workshopId -eq "3423755273") {
    throw "Refusing to update the inherited Hurricane Workshop item."
}
if ($workshopId -and $workshopId -notmatch '^\d+$') {
    throw "Invalid staged Workshop ID: $workshopId"
}

$steam = Get-Process -Name "steam" -ErrorAction SilentlyContinue
if (-not $steam) {
    throw "Steam is not running. Start Steam visibly under the intended owner account before publishing."
}

$fileCount = $manifest.files.Count
$totalBytes = ($manifest.files | Measure-Object -Property size -Sum).Sum
$mode = if ($workshopId) { "UPDATE PRIVATE ITEM $workshopId" } else { "CREATE NEW PRIVATE ITEM" }
$confirmationPhrase = if ($workshopId) { "UPDATE PRIVATE MOONFLOWER $workshopId" } else { "CREATE PRIVATE MOONFLOWER" }

Write-Host ""
Write-Host "Steam upload checkpoint"
Write-Host "AppID: 3051280"
Write-Host "Name: MoonFlower"
Write-Host "Visibility: private (owner only)"
Write-Host "Mode: $mode"
Write-Host "Stage: $stagePath"
Write-Host "Files: $fileCount"
Write-Host "Bytes: $totalBytes"
Write-Host "Manifest SHA-256: $actualManifestHash"
Write-Host ""

$confirmation = Read-Host "Type '$confirmationPhrase' to perform the external Steam upload"
if ($confirmation -cne $confirmationPhrase) {
    throw "Upload cancelled because the confirmation phrase did not match."
}

$javaArguments = @(
    "-Dmoonflower.steamUploadConfirmed=true",
    "-cp",
    "*",
    "haven.SteamWorkshop",
    "upload",
    $stagePath,
    $Message
)

Push-Location $clientBin
try {
    $uploadOutput = @(& java @javaArguments 2>&1)
    $uploadExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

foreach ($line in $uploadOutput) {
    $sanitizedLine = $line.ToString() -replace '(?i)(Steam ID:\s*)\d+', '$1[redacted]'
    Write-Host $sanitizedLine
}

$outputText = ($uploadOutput | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
$createdIdMatch = [regex]::Match($outputText, 'workshop-id=(\d+)')
if ($createdIdMatch.Success) {
    $createdId = $createdIdMatch.Groups[1].Value
    if ($createdId -eq "3423755273") {
        throw "Steam returned the inherited Hurricane item ID; local state was not written."
    }
    New-Item -ItemType Directory -Path $privateRoot -Force | Out-Null
    [System.IO.File]::WriteAllText(
        $localWorkshopIdPath,
        $createdId + [Environment]::NewLine,
        (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Stored the new MoonFlower Workshop ID in ignored local state: $localWorkshopIdPath"
}

if ($uploadExitCode -ne 0) {
    throw "Steam Workshop upload failed with exit code $uploadExitCode. Do not create another item blindly; inspect the saved local ID and Steam state first."
}

Write-Host "Private Workshop upload completed. Verify owner, item ID, and private visibility in Steam before subscribing or launching."
