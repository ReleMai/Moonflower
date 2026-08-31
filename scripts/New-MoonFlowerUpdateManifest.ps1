[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$PackagePath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{40}$')][string]$Commit,
    [Parameter(Mandatory = $true)][ValidatePattern('^https://github\.com/')][string]$AssetUrl,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')][string]$Repository,
    [ValidateSet('stable')][string]$Channel = 'stable',
    [string]$PreviousFeedPath
)

$ErrorActionPreference = 'Stop'

$package = Get-Item -LiteralPath $PackagePath
$outputFullPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputParent = Split-Path -Parent $outputFullPath
if ($outputParent) {
    New-Item -ItemType Directory -Path $outputParent -Force | Out-Null
}

$manifest = [ordered]@{
    # Keep schema 1 for installed launchers that predate rollback metadata.
    # JSON consumers ignore the optional previous field they do not understand.
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

if (-not [string]::IsNullOrWhiteSpace($PreviousFeedPath)) {
    $previous = Get-Content -LiteralPath $PreviousFeedPath -Raw | ConvertFrom-Json
    $previousPublishedAt = [DateTimeOffset]::MinValue
    if ($previous.schemaVersion -notin @(1, 2) -or
        [string]$previous.channel -ne $Channel -or
        [string]$previous.repository -ne $Repository -or
        [string]$previous.commit -notmatch '^[0-9a-f]{40}$' -or
        [string]$previous.package.url -notmatch '^https://github\.com/' -or
        [string]$previous.package.sha256 -notmatch '^[0-9a-f]{64}$' -or
        [int64]$previous.package.size -lt 1 -or
        -not [DateTimeOffset]::TryParse([string]$previous.publishedAt, [ref]$previousPublishedAt)) {
        throw 'The previous update feed is invalid or incompatible.'
    }
    if ([string]$previous.commit -ne $manifest.commit) {
        $manifest['previous'] = [ordered]@{
            commit = [string]$previous.commit
            publishedAt = $previousPublishedAt.ToUniversalTime().ToString('o')
            package = [ordered]@{
                url = [string]$previous.package.url
                size = [int64]$previous.package.size
                sha256 = [string]$previous.package.sha256
            }
        }
    }
}

$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $outputFullPath -Encoding UTF8
Write-Host "Created MoonFlower update feed for $($manifest.commit.Substring(0, 12))."
