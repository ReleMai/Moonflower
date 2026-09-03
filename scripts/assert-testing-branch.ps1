[CmdletBinding()]
param(
    [string]$RepoPath
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($RepoPath)) {
    $RepoPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}
$RepoPath = [System.IO.Path]::GetFullPath($RepoPath)
$branch = @(& git -C $RepoPath branch --show-current 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "Could not determine the current Git branch for $RepoPath.`n$($branch -join [Environment]::NewLine)"
}
$branch = (($branch | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine).Trim()

if ($branch -ne 'testing') {
    if ([string]::IsNullOrWhiteSpace($branch)) {
        throw "Refusing local development build: the checkout is detached. Switch to testing first."
    }
    throw "Refusing local development build on '$branch'. Switch to testing first."
}

Write-Host "MoonFlower development branch check passed: testing ($RepoPath)."
