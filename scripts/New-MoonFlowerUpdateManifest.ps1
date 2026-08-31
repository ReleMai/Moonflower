[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PackagePath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$Commit,
    [Parameter(Mandatory = $true)][ValidatePattern('^https://github\.com/')][string]$AssetUrl,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')][string]$Repository,
    [ValidateSet('stable')][string]$Channel = 'stable'
)

$ErrorActionPreference = 'Stop'

$package = Get-Item -LiteralPath $PackagePath
$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputParent = Split-Path -Parent $outputFullPath
if ($outputParent) {
    New-Item -ItemType Directory -Path $outputParent -Force | Out-Null
}

$manifest = [ordered]@{
    schemaVersion = 1
    channel = $Channel
    repository = $Repository
    commit = $Commit.ToLowerInvariant()
    publishedAt = [DateTimeOffset]::UtcNow.ToString('o')
    package = [ordered]@{
        url = $AssetUrl
        size = $package.Length
        sha256 = (Get-FileHash -LiteralPath $package.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $outputFullPath -Encoding UTF8
Write-Host "Created MoonFlower update feed for $($manifest.commit.Substring(0, 12))."
