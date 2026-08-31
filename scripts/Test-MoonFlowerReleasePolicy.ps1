[CmdletBinding(DefaultParameterSetName = 'Range')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'Range')]
    [string]$BaseRef,

    [Parameter(Mandatory = $true, ParameterSetName = 'Staged')]
    [switch]$Staged,

    [Parameter(ParameterSetName = 'Range')]
    [switch]$RequireCommitDetails
)

$ErrorActionPreference = 'Stop'

function Invoke-Git {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& git @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed:`n$($output -join [Environment]::NewLine)"
    }
    return $output
}

$repoRoot = (Invoke-Git -Arguments @('rev-parse', '--show-toplevel') | Select-Object -First 1).Trim()
Push-Location $repoRoot
try {
    if ($Staged) {
        $rangeLabel = 'staged changes'
        $changedFiles = @(Invoke-Git -Arguments @('diff', '--cached', '--name-only', '--diff-filter=ACMR'))
        $patch = @(Invoke-Git -Arguments @('diff', '--cached', '--unified=0', '--no-ext-diff', '--no-textconv'))
    } else {
        Invoke-Git -Arguments @('rev-parse', '--verify', "$BaseRef^{commit}") | Out-Null
        $rangeLabel = "$BaseRef..HEAD"
        $changedFiles = @(Invoke-Git -Arguments @('diff', '--name-only', '--diff-filter=ACMR', "$BaseRef..HEAD"))
        $patch = @(Invoke-Git -Arguments @('diff', '--unified=0', '--no-ext-diff', '--no-textconv', "$BaseRef..HEAD"))
    }

    $forbiddenPathPatterns = @(
        '(?i)(^|/)\.env(?:\.|$)',
        '(?i)(^|/)(?:id_rsa|id_ed25519)(?:\.|$)',
        '(?i)\.(?:pem|p12|pfx|key|keystore)$',
        '(?i)(^|/)(?:server-data|map-cache|launcher-cache|credentials?|sessions?)(?:/|$)',
        '(?i)(^|/)(?:cookies?|account-data)(?:\.|/|$)',
        '(?i)(^|/).*\.(?:log|dmp)$',
        '(?i)^artifacts/legacy-launcher/',
        '(?i)autohaven-socrates556\.jar$'
    )

    $pathFailures = [System.Collections.Generic.List[string]]::new()
    foreach ($file in $changedFiles) {
        $normalized = ([string]$file).Trim().Replace('\', '/')
        if ([string]::IsNullOrWhiteSpace($normalized)) {
            continue
        }
        foreach ($pattern in $forbiddenPathPatterns) {
            if ($normalized -match $pattern) {
                $pathFailures.Add($normalized)
                break
            }
        }
    }

    $secretPatterns = @(
        ('-----BEGIN ' + '(?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
        ('gh' + 'p_[A-Za-z0-9]{20,}'),
        ('github_' + 'pat_[A-Za-z0-9_]{20,}'),
        ('AK' + 'IA[A-Z0-9]{16}'),
        ('xox' + '[baprs]-[A-Za-z0-9-]{10,}'),
        ('sk' + '-[A-Za-z0-9]{32,}'),
        '(?i)authorization\s*:\s*(?:bearer|basic)\s+[A-Za-z0-9+/_.=-]{12,}',
        ('(?i)(?:pass' + 'word|passwd|api[_-]?key|client[_-]?secret|access[_-]?token|refresh[_-]?token)\s*[:=]\s*["''](?!changeme|placeholder|example|test|dummy|redacted|<)[^"'']{8,}["'']')
    )

    $contentFailures = [System.Collections.Generic.List[string]]::new()
    foreach ($line in $patch) {
        $text = [string]$line
        if (-not $text.StartsWith('+') -or $text.StartsWith('+++')) {
            continue
        }
        $addedText = $text.Substring(1)
        foreach ($pattern in $secretPatterns) {
            if ($addedText -match $pattern) {
                $preview = if ($addedText.Length -gt 160) { $addedText.Substring(0, 160) + '...' } else { $addedText }
                $contentFailures.Add($preview)
                break
            }
        }
    }

    if ($pathFailures.Count -gt 0 -or $contentFailures.Count -gt 0) {
        if ($pathFailures.Count -gt 0) {
            Write-Error "Forbidden private/runtime paths found in ${rangeLabel}:`n$($pathFailures -join [Environment]::NewLine)" -ErrorAction Continue
        }
        if ($contentFailures.Count -gt 0) {
            Write-Error "Possible secret values found in added lines for ${rangeLabel}. Values are intentionally not reproduced in full.`n$($contentFailures -join [Environment]::NewLine)" -ErrorAction Continue
        }
        throw 'MoonFlower release privacy policy failed.'
    }

    if ($RequireCommitDetails) {
        $commitIds = @(Invoke-Git -Arguments @('rev-list', '--no-merges', '--reverse', "$BaseRef..HEAD"))
        foreach ($commitId in $commitIds) {
            $message = (Invoke-Git -Arguments @('show', '-s', '--format=%B', $commitId)) -join "`n"
            $missing = [System.Collections.Generic.List[string]]::new()
            foreach ($heading in @('Changes:', 'Reason:', 'Verification:', 'Privacy:')) {
                if ($message -notmatch "(?im)^$([regex]::Escape($heading))\s*\S") {
                    $missing.Add($heading)
                }
            }
            if ($missing.Count -gt 0) {
                throw "Commit $($commitId.Substring(0, 12)) is missing detailed message sections: $($missing -join ', ')"
            }
        }
        Write-Host "MoonFlower commit-detail policy passed for $($commitIds.Count) commit(s)."
    }

    Write-Host "MoonFlower release privacy policy passed for $rangeLabel ($($changedFiles.Count) changed file(s))."
} finally {
    Pop-Location
}
