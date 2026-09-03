[CmdletBinding()]
param(
    [string]$RepoPath,
    [string]$Remote = 'origin',
    [string]$WorktreeRoot,
    [switch]$NoBuild,
    [switch]$KeepWorktree,
    [switch]$ListOnly
)

$ErrorActionPreference = 'Stop'
$script:RepoRoot = $null
$script:Remote = $Remote
$script:WorktreeRoot = $null
$script:CurrentBranchInfo = $null
$script:ProgressDialog = $null

function ConvertTo-RedactedText {
    param([AllowNull()][string]$Text)

    if ($null -eq $Text) {
        return ''
    }
    return $Text -replace '(?i)(https?://)([^/\s@]+)@', '$1***@'
}

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [string]$WorkingPath = $script:RepoRoot,
        [switch]$AllowFailure
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& git -C $WorkingPath @Arguments 2>&1 | ForEach-Object { [string]$_ })
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

function Get-RepositoryRoot {
    param([string]$RequestedPath)

    if ([string]::IsNullOrWhiteSpace($RequestedPath)) {
        $RequestedPath = Split-Path -Parent $PSScriptRoot
    }
    if (-not (Test-Path -LiteralPath $RequestedPath -PathType Container)) {
        throw "Repository path does not exist: $RequestedPath"
    }
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw 'Git is not available on PATH.'
    }
    $script:RepoRoot = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $RequestedPath).Path)
    $result = Invoke-Git @('rev-parse', '--show-toplevel')
    $reportedRoot = $result.Output | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($reportedRoot)) {
        throw "Git did not report a repository root for $RequestedPath"
    }
    $script:RepoRoot = [System.IO.Path]::GetFullPath($reportedRoot.Trim())
    return $script:RepoRoot
}

function Get-CurrentRepositoryInfo {
    $branchResult = Invoke-Git @('branch', '--show-current')
    $branch = [string](($branchResult.Output | Select-Object -First 1) -as [string])
    $branch = $branch.Trim()
    if ([string]::IsNullOrWhiteSpace($branch)) {
        $branch = '(detached HEAD)'
    }

    $commitResult = Invoke-Git @('rev-parse', 'HEAD')
    $commit = [string](($commitResult.Output | Select-Object -First 1) -as [string])
    $commit = $commit.Trim()
    $statusResult = Invoke-Git @('status', '--porcelain=v1', '--untracked-files=all')

    return [pscustomobject]@{
        Branch = $branch
        Commit = $commit
        CommitShort = if ($commit.Length -gt 12) { $commit.Substring(0, 12) } else { $commit }
        WorktreeState = if (@($statusResult.Output).Count -eq 0) { 'clean' } else { 'dirty' }
    }
}

function Get-GitSingleLine {
    param([string[]]$Arguments)

    $result = Invoke-Git $Arguments
    $value = [string](($result.Output | Select-Object -First 1) -as [string])
    return $value.Trim()
}

function Get-AvailableBranches {
    param([object]$Worker)

    Report-OperationProgress -Worker $Worker -Percent 5 -Stage 'FETCH' -Message ("Fetching remote branch refs from '{0}'..." -f $script:Remote) -Indeterminate -WriteLog
    $null = Invoke-Git @('fetch', '--prune', $script:Remote)
    Report-OperationProgress -Worker $Worker -Percent 22 -Stage 'DISCOVER' -Message 'Remote refs fetched. Reading branch commits and local relationships...' -WriteLog

    $script:CurrentBranchInfo = Get-CurrentRepositoryInfo
    $format = '%(refname:strip=3)' + "`t" + '%(objectname)' + "`t" + '%(committerdate:iso-strict)'
    $result = Invoke-Git @('for-each-ref', ('--format={0}' -f $format), ('refs/remotes/{0}' -f $script:Remote))
    $branchLines = @($result.Output)
    $branches = @()
    $validBranchCount = @($branchLines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
    $processed = 0
    foreach ($line in $branchLines) {
        $parts = $line -split "`t", 3
        if ($parts.Count -lt 2) {
            continue
        }
        $name = $parts[0]
        if ([string]::IsNullOrWhiteSpace($name) -or $name -eq 'HEAD') {
            continue
        }
        Assert-RefComponent $name 'remote branch'
        $commit = $parts[1]
        $updated = if ($parts.Count -ge 3) { $parts[2] } else { 'unknown' }
        $remoteRef = 'refs/remotes/{0}/{1}' -f $script:Remote, $name
        $subject = Get-GitSingleLine @('show', '-s', '--format=%s', $remoteRef)
        $author = Get-GitSingleLine @('show', '-s', '--format=%an', $remoteRef)
        $localResult = Invoke-Git @('rev-parse', '--verify', ('refs/heads/{0}' -f $name)) -AllowFailure
        $localCommit = [string](($localResult.Output | Select-Object -First 1) -as [string])
        $localCommit = $localCommit.Trim()
        if ($localResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($localCommit)) {
            $localState = 'not present as a local branch'
        } elseif ($localCommit -eq $commit) {
            $localState = 'local branch matches remote'
        } else {
            $relationship = Invoke-Git @('rev-list', '--left-right', '--count', ('refs/heads/{0}...{1}' -f $name, $remoteRef)) -AllowFailure
            $relationshipText = [string](($relationship.Output | Select-Object -First 1) -as [string])
            $relationshipText = $relationshipText.Trim()
            if ($relationship.ExitCode -eq 0 -and $relationshipText -match '^\s*(\d+)\s+(\d+)\s*$') {
                $localState = 'local {0} commit(s) ahead, {1} behind' -f $Matches[1], $Matches[2]
            } else {
                $localState = 'local branch differs'
            }
        }
        $subject = ($subject -replace '[\t\r\n]+', ' ').Trim()
        if ([string]::IsNullOrWhiteSpace($subject)) {
            $subject = '(no commit message)'
        }
        $author = ($author -replace '[\t\r\n]+', ' ').Trim()
        if ([string]::IsNullOrWhiteSpace($author)) {
            $author = 'unknown author'
        }
        $commitShort = if ($commit.Length -gt 12) { $commit.Substring(0, 12) } else { $commit }
        $branches += [pscustomobject]@{
            Name = $name
            Commit = $commit
            CommitShort = $commitShort
            UpdatedAt = $updated
            Subject = $subject
            Author = $author
            LocalCommit = $localCommit
            LocalState = $localState
            DisplayName = '{0}  [{1}]  {2}' -f $name, $commitShort, $subject
        }
        $processed++
        $branchPercent = if ($validBranchCount -gt 0) { 22 + [int](48 * ($processed / [double]$validBranchCount)) } else { 70 }
        Report-OperationProgress -Worker $Worker -Percent $branchPercent -Stage 'DISCOVER' -Message ("Read {0} of {1} remote branch(es)." -f $processed, $validBranchCount)
    }
    Report-OperationProgress -Worker $Worker -Percent 75 -Stage 'READY' -Message ("Found {0} remote branch(es) in '{1}'." -f $branches.Count, $script:Remote) -WriteLog
    return @($branches | Sort-Object @{ Expression = { if ($_.Name -eq 'testing') { 0 } elseif ($_.Name -eq 'main') { 1 } else { 2 } } }, Name)
}

function Get-ManagedWorktreePath {
    param([string]$BranchName)

    $slug = $BranchName -replace '[^A-Za-z0-9._-]', '-'
    $slug = $slug.Trim([char[]]'.-')
    if ([string]::IsNullOrWhiteSpace($slug)) {
        $slug = 'branch'
    }
    if ($slug.Length -gt 48) {
        $slug = $slug.Substring(0, 48).TrimEnd([char[]]'.-')
    }
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($BranchName)
        $digest = $sha.ComputeHash($bytes)
        $hash = ([BitConverter]::ToString($digest).Replace('-', '')).Substring(0, 10).ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
    $name = '{0}-{1}-{2}' -f $slug, $hash, [Guid]::NewGuid().ToString('N').Substring(0, 8)
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $script:WorktreeRoot $name))
    $rootPrefix = $script:WorktreeRoot.TrimEnd([char]92, [char]47) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $candidate.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The generated test worktree escaped its managed root.'
    }
    return $candidate
}

function Assert-ClientStopped {
    $guardPath = Join-Path $script:RepoRoot 'scripts\assert-client-stopped.ps1'
    if (-not (Test-Path -LiteralPath $guardPath -PathType Leaf)) {
        throw "The client deployment guard is missing: $guardPath"
    }
    & $guardPath
}

function New-TestWorktree {
    param(
        [string]$BranchName,
        [string]$Commit
    )

    New-Item -ItemType Directory -Path $script:WorktreeRoot -Force | Out-Null
    $path = Get-ManagedWorktreePath $BranchName
    if (Test-Path -LiteralPath $path) {
        throw "The generated test worktree path already exists; no files were overwritten: $path"
    }
    $remoteRef = 'refs/remotes/{0}/{1}' -f $script:Remote, $BranchName
    $null = Invoke-Git @('worktree', 'add', '--detach', $path, $remoteRef)
    $actualCommit = (Invoke-Git @('rev-parse', '--verify', 'HEAD') -WorkingPath $path | Select-Object -ExpandProperty Output | Select-Object -First 1).Trim()
    if ($actualCommit -ne $Commit) {
        throw ('The test worktree resolved to {0}, expected {1}.' -f $actualCommit, $Commit)
    }
    return $path
}

function Remove-TestWorktree {
    param([string]$Path)

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $rootPrefix = $script:WorktreeRoot.TrimEnd([char]92, [char]47) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $fullPath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove a path outside the managed test-worktree root: $Path"
    }
    if (-not (Test-Path -LiteralPath $fullPath)) {
        return
    }
    $null = Invoke-Git @('worktree', 'remove', '--force', '--', $fullPath)
}

function Get-ClientPackagePath {
    param([string]$WorktreePath)

    $candidates = @(
        (Join-Path $WorktreePath 'client\hafen.jar'),
        (Join-Path $WorktreePath 'client\bin\hafen.jar')
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    return $null
}

function Invoke-TestBuild {
    param(
        [string]$WorktreePath,
        [object]$Worker
    )

    if (-not (Get-Command ant -ErrorAction SilentlyContinue)) {
        throw 'Ant is not available on PATH.'
    }
    $clientPath = Join-Path $WorktreePath 'client'
    Report-OperationProgress -Worker $Worker -Percent 35 -Stage 'BUILD' -Message 'Starting Ant clean deftgt in the selected worktree...' -Indeterminate -WriteLog

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $env:ComSpec
    $startInfo.Arguments = '/d /c ant clean deftgt'
    $startInfo.WorkingDirectory = $clientPath
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    $startedAt = Get-Date
    try {
        if (-not $process.Start()) {
            throw 'Ant could not be started.'
        }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $lastHeartbeat = [datetime]::MinValue
        while (-not $process.HasExited) {
            $now = Get-Date
            if (($now - $lastHeartbeat).TotalSeconds -ge 2) {
                $elapsedTime = $now - $startedAt
                $elapsed = '{0:00}:{1:00}' -f [int]$elapsedTime.TotalMinutes, $elapsedTime.Seconds
                Report-OperationProgress -Worker $Worker -Percent 45 -Stage 'BUILD' -Message ("Ant is still running ({0} elapsed). The build output will be summarized here when it finishes." -f $elapsed) -Indeterminate -WriteLog
                $lastHeartbeat = $now
            }
            Start-Sleep -Milliseconds 250
        }
        $stdout = $stdoutTask.Result
        $stderr = $stderrTask.Result
        $exitCode = $process.ExitCode
        $summaryLines = @((($stdout -split "`r?`n") + ($stderr -split "`r?`n")) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Last 16)
        foreach ($line in $summaryLines) {
            Report-OperationProgress -Worker $Worker -Percent 65 -Stage 'BUILD' -Message ([string]$line).Trim() -WriteLog
        }
        if ($exitCode -ne 0) {
            throw ('The selected branch build failed with exit code {0}.' -f $exitCode)
        }
        Report-OperationProgress -Worker $Worker -Percent 70 -Stage 'BUILD' -Message 'Ant clean deftgt completed successfully.' -WriteLog
    } finally {
        $process.Dispose()
    }
}

function Invoke-TestLaunch {
    param(
        [string]$WorktreePath,
        [object]$Worker
    )

    $clientPath = Join-Path $WorktreePath 'client'
    $launcherPath = Join-Path $clientPath 'Play.bat'
    if (-not (Test-Path -LiteralPath $launcherPath -PathType Leaf)) {
        throw "The selected worktree does not contain client\Play.bat: $launcherPath"
    }
    $packagePath = Get-ClientPackagePath $WorktreePath
    if ($null -eq $packagePath) {
        throw 'No generated client package exists in the temporary worktree. Choose Clean build + launch; Run existing build requires client\hafen.jar or client\bin\hafen.jar to already be present.'
    }
    Report-OperationProgress -Worker $Worker -Percent 82 -Stage 'LAUNCH' -Message ("Launching Play.bat -NoUpdate from {0}. The stable GitHub updater is bypassed." -f $packagePath) -WriteLog
    if ($null -ne $script:ProgressDialog -and $script:ProgressDialog.Form.WindowState -ne [System.Windows.Forms.FormWindowState]::Minimized) {
        $script:ProgressDialog.Form.WindowState = [System.Windows.Forms.FormWindowState]::Minimized
        Write-Host 'Selector minimized while the selected client is running; restore it to inspect the live operation status.' -ForegroundColor Yellow
    }
    Push-Location $clientPath
    try {
        & .\Play.bat -NoUpdate 2>&1 | ForEach-Object { Write-Host ([string]$_) }
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($null -eq $exitCode) {
        $exitCode = 0
    }
    Report-OperationProgress -Worker $Worker -Percent 95 -Stage 'LAUNCH' -Message ('The client process ended with exit code {0}.' -f $exitCode) -WriteLog
    return [int]$exitCode
}

function New-BranchSelectorDialog {
    param([bool]$BuildByDefault)

    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    [System.Windows.Forms.Application]::EnableVisualStyles()

    $colors = @{
        Background = [System.Drawing.Color]::FromArgb(17, 27, 42)
        Surface = [System.Drawing.Color]::FromArgb(27, 43, 64)
        Ivory = [System.Drawing.Color]::FromArgb(246, 240, 220)
        Muted = [System.Drawing.Color]::FromArgb(183, 194, 202)
        Teal = [System.Drawing.Color]::FromArgb(79, 195, 177)
        Gold = [System.Drawing.Color]::FromArgb(229, 184, 92)
    }

    $form = New-Object System.Windows.Forms.Form
    $form.Text = 'MoonFlower - Test a Remote Branch'
    $form.ClientSize = New-Object System.Drawing.Size(900, 720)
    $form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
    $form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::Sizable
    $form.MinimumSize = New-Object System.Drawing.Size(820, 650)
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false
    $form.ShowIcon = $false
    $form.BackColor = $colors.Background

    $title = New-Object System.Windows.Forms.Label
    $title.Text = 'Select a branch to test'
    $title.Font = New-Object System.Drawing.Font('Segoe UI', 16, [System.Drawing.FontStyle]::Bold)
    $title.ForeColor = $colors.Ivory
    $title.Location = New-Object System.Drawing.Point(28, 22)
    $title.Size = New-Object System.Drawing.Size(650, 34)
    $form.Controls.Add($title)

    $info = New-Object System.Windows.Forms.Label
    $info.Text = 'Branch data is fetched from the selected remote. Run and Clean build + launch use a temporary detached Git worktree; your current checkout is not switched, pulled, or overwritten.'
    $info.Font = New-Object System.Drawing.Font('Segoe UI', 9.5)
    $info.ForeColor = $colors.Muted
    $info.Location = New-Object System.Drawing.Point(30, 62)
    $info.Size = New-Object System.Drawing.Size(820, 42)
    $form.Controls.Add($info)

    $repoLabel = New-Object System.Windows.Forms.Label
    $repoLabel.Text = 'Repository'
    $repoLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $repoLabel.ForeColor = $colors.Gold
    $repoLabel.Location = New-Object System.Drawing.Point(30, 112)
    $repoLabel.Size = New-Object System.Drawing.Size(90, 22)
    $form.Controls.Add($repoLabel)

    $repoValue = New-Object System.Windows.Forms.Label
    $repoValue.Text = $script:RepoRoot
    $repoValue.Font = New-Object System.Drawing.Font('Consolas', 9)
    $repoValue.ForeColor = $colors.Ivory
    $repoValue.AutoEllipsis = $true
    $repoValue.Location = New-Object System.Drawing.Point(125, 112)
    $repoValue.Size = New-Object System.Drawing.Size(735, 22)
    $form.Controls.Add($repoValue)

    $currentLabel = New-Object System.Windows.Forms.Label
    $currentLabel.Text = 'Current checkout'
    $currentLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $currentLabel.ForeColor = $colors.Gold
    $currentLabel.Location = New-Object System.Drawing.Point(30, 139)
    $currentLabel.Size = New-Object System.Drawing.Size(110, 22)
    $form.Controls.Add($currentLabel)

    $currentValue = New-Object System.Windows.Forms.Label
    $currentValue.Text = 'Loading repository state...'
    $currentValue.Font = New-Object System.Drawing.Font('Consolas', 9)
    $currentValue.ForeColor = $colors.Ivory
    $currentValue.AutoEllipsis = $true
    $currentValue.Location = New-Object System.Drawing.Point(145, 139)
    $currentValue.Size = New-Object System.Drawing.Size(715, 22)
    $form.Controls.Add($currentValue)

    $branchLabel = New-Object System.Windows.Forms.Label
    $branchLabel.Text = 'Remote branch'
    $branchLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $branchLabel.ForeColor = $colors.Gold
    $branchLabel.Location = New-Object System.Drawing.Point(30, 174)
    $branchLabel.Size = New-Object System.Drawing.Size(100, 22)
    $form.Controls.Add($branchLabel)

    $branchBox = New-Object System.Windows.Forms.ComboBox
    $branchBox.DropDownStyle = [System.Windows.Forms.ComboBoxStyle]::DropDownList
    $branchBox.DisplayMember = 'DisplayName'
    $branchBox.Font = New-Object System.Drawing.Font('Segoe UI', 10)
    $branchBox.BackColor = $colors.Surface
    $branchBox.ForeColor = $colors.Ivory
    $branchBox.Location = New-Object System.Drawing.Point(145, 170)
    $branchBox.Size = New-Object System.Drawing.Size(600, 28)
    $branchBox.Enabled = $false
    $form.Controls.Add($branchBox)

    $refresh = New-Object System.Windows.Forms.Button
    $refresh.Text = 'Refresh branch data'
    $refresh.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $refresh.BackColor = $colors.Surface
    $refresh.ForeColor = $colors.Ivory
    $refresh.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
    $refresh.FlatAppearance.BorderColor = $colors.Teal
    $refresh.Location = New-Object System.Drawing.Point(755, 170)
    $refresh.Size = New-Object System.Drawing.Size(135, 28)
    $form.Controls.Add($refresh)

    $commitLabel = New-Object System.Windows.Forms.Label
    $commitLabel.Text = 'Commit: —'
    $commitLabel.Font = New-Object System.Drawing.Font('Consolas', 9)
    $commitLabel.ForeColor = $colors.Muted
    $commitLabel.Location = New-Object System.Drawing.Point(145, 206)
    $commitLabel.Size = New-Object System.Drawing.Size(715, 22)
    $form.Controls.Add($commitLabel)

    $updatedLabel = New-Object System.Windows.Forms.Label
    $updatedLabel.Text = 'Remote update: —'
    $updatedLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $updatedLabel.ForeColor = $colors.Muted
    $updatedLabel.Location = New-Object System.Drawing.Point(145, 230)
    $updatedLabel.Size = New-Object System.Drawing.Size(715, 22)
    $form.Controls.Add($updatedLabel)

    $messageLabel = New-Object System.Windows.Forms.Label
    $messageLabel.Text = 'Last commit: —'
    $messageLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $messageLabel.ForeColor = $colors.Muted
    $messageLabel.AutoEllipsis = $true
    $messageLabel.Location = New-Object System.Drawing.Point(145, 254)
    $messageLabel.Size = New-Object System.Drawing.Size(715, 22)
    $form.Controls.Add($messageLabel)

    $localLabel = New-Object System.Windows.Forms.Label
    $localLabel.Text = 'Local relationship: —'
    $localLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $localLabel.ForeColor = $colors.Muted
    $localLabel.AutoEllipsis = $true
    $localLabel.Location = New-Object System.Drawing.Point(145, 278)
    $localLabel.Size = New-Object System.Drawing.Size(715, 22)
    $form.Controls.Add($localLabel)

    $buildBox = New-Object System.Windows.Forms.Label
    $buildBox.Font = New-Object System.Drawing.Font('Segoe UI', 9.5, [System.Drawing.FontStyle]::Bold)
    $buildBox.ForeColor = $colors.Ivory
    $buildBox.Text = if ($BuildByDefault) { 'Enter key: Clean build + launch' } else { 'Enter key: Run existing build' }
    $buildBox.Location = New-Object System.Drawing.Point(145, 308)
    $buildBox.Size = New-Object System.Drawing.Size(340, 26)
    $form.Controls.Add($buildBox)

    $keepBox = New-Object System.Windows.Forms.CheckBox
    $keepBox.Text = 'Keep temporary worktree for inspection'
    $keepBox.Checked = $false
    $keepBox.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $keepBox.ForeColor = $colors.Muted
    $keepBox.Location = New-Object System.Drawing.Point(490, 308)
    $keepBox.Size = New-Object System.Drawing.Size(350, 26)
    $form.Controls.Add($keepBox)

    $planTitle = New-Object System.Windows.Forms.Label
    $planTitle.Text = 'What the selected action will do'
    $planTitle.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $planTitle.ForeColor = $colors.Gold
    $planTitle.Location = New-Object System.Drawing.Point(30, 344)
    $planTitle.Size = New-Object System.Drawing.Size(400, 22)
    $form.Controls.Add($planTitle)

    $plan = New-Object System.Windows.Forms.TextBox
    $plan.Multiline = $true
    $plan.ReadOnly = $true
    $plan.ScrollBars = [System.Windows.Forms.ScrollBars]::Vertical
    $plan.Font = New-Object System.Drawing.Font('Consolas', 9)
    $plan.BackColor = $colors.Surface
    $plan.ForeColor = $colors.Ivory
    $plan.Location = New-Object System.Drawing.Point(30, 367)
    $plan.Size = New-Object System.Drawing.Size(830, 116)
    $form.Controls.Add($plan)

    $status = New-Object System.Windows.Forms.Label
    $status.Text = 'Ready.'
    $status.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $status.ForeColor = $colors.Teal
    $status.Location = New-Object System.Drawing.Point(30, 495)
    $status.Size = New-Object System.Drawing.Size(650, 22)
    $status.AutoEllipsis = $true
    $form.Controls.Add($status)

    $elapsed = New-Object System.Windows.Forms.Label
    $elapsed.Text = 'Elapsed: 00:00'
    $elapsed.Font = New-Object System.Drawing.Font('Consolas', 9)
    $elapsed.ForeColor = $colors.Muted
    $elapsed.TextAlign = [System.Drawing.ContentAlignment]::MiddleRight
    $elapsed.Location = New-Object System.Drawing.Point(700, 495)
    $elapsed.Size = New-Object System.Drawing.Size(160, 22)
    $form.Controls.Add($elapsed)

    $progress = New-Object System.Windows.Forms.ProgressBar
    $progress.Style = [System.Windows.Forms.ProgressBarStyle]::Continuous
    $progress.Minimum = 0
    $progress.Maximum = 100
    $progress.Value = 0
    $progress.Location = New-Object System.Drawing.Point(30, 522)
    $progress.Size = New-Object System.Drawing.Size(830, 20)
    $form.Controls.Add($progress)

    $activity = New-Object System.Windows.Forms.TextBox
    $activity.Multiline = $true
    $activity.ReadOnly = $true
    $activity.ScrollBars = [System.Windows.Forms.ScrollBars]::Vertical
    $activity.Font = New-Object System.Drawing.Font('Consolas', 8.5)
    $activity.BackColor = [System.Drawing.Color]::FromArgb(12, 19, 30)
    $activity.ForeColor = $colors.Muted
    $activity.Location = New-Object System.Drawing.Point(30, 550)
    $activity.Size = New-Object System.Drawing.Size(830, 90)
    $form.Controls.Add($activity)

    $cancel = New-Object System.Windows.Forms.Button
    $cancel.Text = 'Cancel'
    $cancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
    $cancel.Font = New-Object System.Drawing.Font('Segoe UI', 10)
    $cancel.BackColor = $colors.Surface
    $cancel.ForeColor = $colors.Ivory
    $cancel.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
    $cancel.FlatAppearance.BorderColor = $colors.Muted
    $cancel.Location = New-Object System.Drawing.Point(560, 655)
    $cancel.Size = New-Object System.Drawing.Size(110, 36)
    $form.Controls.Add($cancel)

    $run = New-Object System.Windows.Forms.Button
    $run.Text = 'Run existing build'
    $run.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $run.BackColor = $colors.Surface
    $run.ForeColor = $colors.Ivory
    $run.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
    $run.FlatAppearance.BorderColor = $colors.Muted
    $run.Location = New-Object System.Drawing.Point(680, 655)
    $run.Size = New-Object System.Drawing.Size(105, 36)
    $form.Controls.Add($run)

    $launch = New-Object System.Windows.Forms.Button
    $launch.Text = 'Clean build + launch'
    $launch.DialogResult = [System.Windows.Forms.DialogResult]::OK
    $launch.Font = New-Object System.Drawing.Font('Segoe UI', 10, [System.Drawing.FontStyle]::Bold)
    $launch.BackColor = $colors.Teal
    $launch.ForeColor = $colors.Background
    $launch.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
    $launch.FlatAppearance.BorderColor = $colors.Gold
    $launch.Location = New-Object System.Drawing.Point(795, 655)
    $launch.Size = New-Object System.Drawing.Size(105, 36)
    $form.Controls.Add($launch)

    $form.AcceptButton = if ($BuildByDefault) { $launch } else { $run }
    $form.CancelButton = $cancel

    return @{
        Form = $form
        BranchBox = $branchBox
        CurrentValue = $currentValue
        CommitLabel = $commitLabel
        UpdatedLabel = $updatedLabel
        MessageLabel = $messageLabel
        LocalLabel = $localLabel
        BuildBox = $buildBox
        KeepBox = $keepBox
        Plan = $plan
        Refresh = $refresh
        Run = $run
        Launch = $launch
        Cancel = $cancel
        Status = $status
        Elapsed = $elapsed
        Progress = $progress
        Activity = $activity
    }
}

function Set-BranchItems {
    param(
        [hashtable]$Dialog,
        [object[]]$Branches,
        [string]$PreferredBranch
    )

    $box = $Dialog.BranchBox
    $box.BeginUpdate()
    try {
        $box.Items.Clear()
        foreach ($branch in @($Branches)) {
            [void]$box.Items.Add($branch)
        }
        $selectedIndex = -1
        for ($index = 0; $index -lt $box.Items.Count; $index++) {
            if ($box.Items[$index].Name -eq $PreferredBranch) {
                $selectedIndex = $index
                break
            }
        }
        if ($selectedIndex -lt 0 -and $box.Items.Count -gt 0) {
            $selectedIndex = 0
        }
        $box.SelectedIndex = $selectedIndex
    } finally {
        $box.EndUpdate()
    }
}

function Update-BranchDetails {
    param([hashtable]$Dialog)

    $selected = $Dialog.BranchBox.SelectedItem
    if ($null -eq $selected) {
        $Dialog.CommitLabel.Text = 'Commit: —'
        $Dialog.UpdatedLabel.Text = 'Remote update: —'
        $Dialog.MessageLabel.Text = 'Last commit: —'
        $Dialog.LocalLabel.Text = 'Local relationship: —'
        $Dialog.Plan.Text = 'Select a remote branch to preview the isolated test actions.'
        return
    }
    $current = $script:CurrentBranchInfo
    $currentBranch = if ($null -ne $current) { $current.Branch } else { 'unknown' }
    $currentCommit = if ($null -ne $current) { $current.CommitShort } else { 'unknown' }
    $currentState = if ($null -ne $current) { $current.WorktreeState } else { 'unknown' }
    $Dialog.CommitLabel.Text = 'Remote commit: {0} ({1})' -f $selected.Commit, $selected.CommitShort
    $Dialog.UpdatedLabel.Text = 'Remote update: {0} by {1}' -f $selected.UpdatedAt, $selected.Author
    $Dialog.MessageLabel.Text = 'Last commit message: {0}' -f $selected.Subject
    $Dialog.LocalLabel.Text = 'Local relationship: {0}' -f $selected.LocalState
    $keepAction = if ($Dialog.KeepBox.Checked) { 'keep the temporary worktree' } else { 'remove the temporary worktree after the client exits' }
    $Dialog.Plan.Text = @(
        ('Selected ref : {0}/{1}' -f $script:Remote, $selected.Name),
        ('Remote commit: {0}' -f $selected.Commit),
        ('Current repo : {0} ({1}, {2})' -f $currentBranch, $currentCommit, $currentState),
        '',
        'Run existing build',
        '  1. Create a detached worktree at the selected remote commit.',
        '  2. Check that a generated hafen.jar exists in that worktree.',
        '  3. Launch client\Play.bat -NoUpdate so the stable updater is bypassed.',
        '  Note: a new worktree normally has no ignored client\bin output, so build first when unsure.',
        '',
        'Clean build + launch',
        '  1. Create the detached worktree, then run ant clean deftgt.',
        '  2. Confirm the generated package exists and launch with -NoUpdate.',
        ('Cleanup     : {0}.' -f $keepAction)
    ) -join [Environment]::NewLine
}

function New-ProgressPayload {
    param(
        [int]$Percent,
        [string]$Stage,
        [string]$Message,
        [switch]$Indeterminate,
        [switch]$WriteLog
    )

    return [pscustomobject]@{
        Percent = [Math]::Max(0, [Math]::Min(100, $Percent))
        Stage = $Stage
        Message = $Message
        Indeterminate = [bool]$Indeterminate
        WriteLog = [bool]$WriteLog
    }
}

function Report-OperationProgress {
    param(
        [object]$Worker,
        [int]$Percent,
        [string]$Stage,
        [string]$Message,
        [switch]$Indeterminate,
        [switch]$WriteLog
    )

    $payload = New-ProgressPayload -Percent $Percent -Stage $Stage -Message $Message -Indeterminate:$Indeterminate -WriteLog:$WriteLog
    if ($null -ne $Worker) {
        $Worker.ReportProgress($payload.Percent, $payload)
    } elseif ($null -ne $script:ProgressDialog) {
        Apply-ProgressPayload $script:ProgressDialog $payload
        [System.Windows.Forms.Application]::DoEvents()
    } else {
        Write-Host ('[{0}] {1}' -f $Stage, $Message) -ForegroundColor Cyan
    }
}

function Add-DialogActivity {
    param(
        [hashtable]$Dialog,
        [string]$Message
    )

    if ([string]::IsNullOrWhiteSpace($Message)) {
        return
    }
    $line = '[{0}] {1}' -f (Get-Date).ToString('HH:mm:ss'), $Message
    $Dialog.Activity.AppendText($line + [Environment]::NewLine)
    $Dialog.Activity.SelectionStart = $Dialog.Activity.TextLength
    $Dialog.Activity.ScrollToCaret()
    Write-Host $line
}

function Apply-ProgressPayload {
    param(
        [hashtable]$Dialog,
        [object]$Payload
    )

    if ($null -eq $Payload) {
        return
    }
    $Dialog.Status.Text = '{0}: {1}' -f $Payload.Stage, $Payload.Message
    if ($Payload.Indeterminate) {
        if ($Dialog.Progress.Style -ne [System.Windows.Forms.ProgressBarStyle]::Marquee) {
            $Dialog.Progress.Style = [System.Windows.Forms.ProgressBarStyle]::Marquee
            $Dialog.Progress.MarqueeAnimationSpeed = 25
        }
    } else {
        if ($Dialog.Progress.Style -ne [System.Windows.Forms.ProgressBarStyle]::Continuous) {
            $Dialog.Progress.Style = [System.Windows.Forms.ProgressBarStyle]::Continuous
        }
        $Dialog.Progress.Value = $Payload.Percent
    }
    if ($Payload.WriteLog) {
        Add-DialogActivity $Dialog $Payload.Message
    }
}

function Set-DialogBusy {
    param(
        [hashtable]$Dialog,
        [bool]$Busy
    )

    $Dialog.Busy = $Busy
    $Dialog.BranchBox.Enabled = -not $Busy
    $Dialog.Refresh.Enabled = -not $Busy
    $Dialog.Run.Enabled = -not $Busy
    $Dialog.Launch.Enabled = -not $Busy
    $Dialog.KeepBox.Enabled = -not $Busy
    $Dialog.Cancel.Enabled = -not $Busy
    if ($Busy) {
        $Dialog.OperationStartedAt = Get-Date
        $Dialog.Progress.Style = [System.Windows.Forms.ProgressBarStyle]::Continuous
        $Dialog.Progress.Value = 0
        $Dialog.Timer.Start()
    } else {
        if ($null -ne $Dialog.OperationStartedAt) {
            $Dialog.OperationElapsed = (Get-Date) - $Dialog.OperationStartedAt
        } else {
            $Dialog.OperationElapsed = [timespan]::Zero
        }
        $Dialog.Timer.Stop()
    }
}

function Complete-DialogOperation {
    param([hashtable]$Dialog)

    Set-DialogBusy $Dialog $false
    $Dialog.Elapsed.Text = 'Elapsed: {0}' -f ('{0:00}:{1:00}' -f [int]($Dialog.OperationElapsed.TotalMinutes), $Dialog.OperationElapsed.Seconds)
}

function Start-BranchRefresh {
    param(
        [hashtable]$Dialog,
        [string]$PreferredBranch
    )

    if ($Dialog.Busy) {
        return
    }
    Set-DialogBusy $Dialog $true
    $Dialog.Status.Text = 'FETCH: Starting repository refresh...'
    Add-DialogActivity $Dialog ("Refreshing '{0}' branch information from the repository remote." -f $script:Remote)
    $script:ProgressDialog = $Dialog
    try {
        $freshBranches = @(Get-AvailableBranches)
        if ($freshBranches.Count -eq 0) {
            throw "No remote branches were found for '$script:Remote'."
        }
        $Dialog.ExitCode = $null
        $Dialog.CurrentValue.Text = '{0}  |  commit {1}  |  worktree {2}' -f $script:CurrentBranchInfo.Branch, $script:CurrentBranchInfo.CommitShort, $script:CurrentBranchInfo.WorktreeState
        Set-BranchItems $Dialog $freshBranches $PreferredBranch
        Update-BranchDetails $Dialog
        $Dialog.Progress.Style = [System.Windows.Forms.ProgressBarStyle]::Continuous
        $Dialog.Progress.Value = 100
        $Dialog.Status.Text = ('READY: {0} remote branch(es) available.' -f $freshBranches.Count)
        Add-DialogActivity $Dialog ('Branch information refreshed; selected ref is ready to run.')
    } catch {
        $message = ConvertTo-RedactedText $_.Exception.Message
        if ([string]::IsNullOrWhiteSpace($message)) {
            $message = ConvertTo-RedactedText $_.ToString()
        }
        $Dialog.Status.Text = 'FAILED: Branch refresh could not complete.'
        $Dialog.ExitCode = 1
        Add-DialogActivity $Dialog $message
        [System.Windows.Forms.MessageBox]::Show(
            $Dialog.Form,
            $message,
            'MoonFlower Branch Selector',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    } finally {
        $script:ProgressDialog = $null
        Complete-DialogOperation $Dialog
    }
}

function Invoke-SelectedBranch {
    param(
        [object]$SelectedBranch,
        [bool]$Build,
        [bool]$Keep,
        [object]$Worker
    )

    if ($null -eq $SelectedBranch) {
        throw 'No branch was selected.'
    }
    Report-OperationProgress -Worker $Worker -Percent 3 -Stage 'GUARD' -Message 'Checking that no MoonFlower client is running...' -WriteLog
    Assert-ClientStopped
    Report-OperationProgress -Worker $Worker -Percent 8 -Stage 'SELECT' -Message ("Selected {0}/{1} at commit {2}." -f $script:Remote, $SelectedBranch.Name, $SelectedBranch.Commit) -WriteLog
    $worktreePath = $null
    try {
        Report-OperationProgress -Worker $Worker -Percent 15 -Stage 'WORKTREE' -Message 'Creating a detached temporary worktree at the selected remote commit...' -Indeterminate -WriteLog
        $worktreePath = New-TestWorktree $SelectedBranch.Name $SelectedBranch.Commit
        Report-OperationProgress -Worker $Worker -Percent 28 -Stage 'WORKTREE' -Message ("Temporary worktree ready: {0}" -f $worktreePath) -WriteLog
        if ($Build) {
            Invoke-TestBuild $worktreePath $Worker
        } else {
            Report-OperationProgress -Worker $Worker -Percent 40 -Stage 'BUILD' -Message 'Build skipped because Run existing build was selected.' -WriteLog
        }
        $exitCode = Invoke-TestLaunch $worktreePath $Worker
        if ($Keep) {
            Report-OperationProgress -Worker $Worker -Percent 98 -Stage 'CLEANUP' -Message ("Keeping test worktree for inspection: {0}" -f $worktreePath) -WriteLog
        } else {
            Report-OperationProgress -Worker $Worker -Percent 98 -Stage 'CLEANUP' -Message 'Removing the temporary worktree after the client exited...' -Indeterminate -WriteLog
            Remove-TestWorktree $worktreePath
            Report-OperationProgress -Worker $Worker -Percent 99 -Stage 'CLEANUP' -Message 'Temporary test worktree removed.' -WriteLog
        }
        Report-OperationProgress -Worker $Worker -Percent 100 -Stage 'DONE' -Message ('Selected branch run completed with exit code {0}.' -f $exitCode) -WriteLog
        return [pscustomobject]@{
            ExitCode = $exitCode
            WorktreePath = $worktreePath
        }
    } catch {
        if ($worktreePath -and $Keep) {
            Report-OperationProgress -Worker $Worker -Percent 100 -Stage 'FAILED' -Message ("Operation failed; keeping worktree for inspection: {0}" -f $worktreePath) -WriteLog
        } elseif ($worktreePath) {
            try {
                Remove-TestWorktree $worktreePath
                Report-OperationProgress -Worker $Worker -Percent 100 -Stage 'FAILED' -Message 'Operation failed; temporary worktree was removed.' -WriteLog
            } catch {
                Report-OperationProgress -Worker $Worker -Percent 100 -Stage 'FAILED' -Message ("Operation failed and the temporary worktree could not be removed: {0}" -f $worktreePath) -WriteLog
            }
        }
        throw
    }
}

function Start-SelectedBranchOperation {
    param(
        [hashtable]$Dialog,
        [bool]$Build
    )

    if ($Dialog.Busy) {
        return
    }
    $selected = $Dialog.BranchBox.SelectedItem
    if ($null -eq $selected) {
        [System.Windows.Forms.MessageBox]::Show(
            $Dialog.Form,
            'Select a remote branch before choosing Run existing build or Clean build + launch.',
            'MoonFlower Branch Selector',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information) | Out-Null
        return
    }

    $keep = [bool]$Dialog.KeepBox.Checked
    $operationName = if ($Build) { 'Clean build + launch' } else { 'Run existing build' }
    Set-DialogBusy $Dialog $true
    $Dialog.Status.Text = 'START: Preparing {0}...' -f $operationName
    Add-DialogActivity $Dialog ("Starting '{0}' for {1}/{2} at {3}." -f $operationName, $script:Remote, $selected.Name, $selected.CommitShort)
    $script:ProgressDialog = $Dialog
    $dialogResult = $null
    try {
        $outcome = Invoke-SelectedBranch -SelectedBranch $selected -Build $Build -Keep $keep
        $Dialog.ExitCode = [int]$outcome.ExitCode
        if ($Dialog.ExitCode -eq 0) {
            $Dialog.Status.Text = 'DONE: Selected client exited successfully.'
        } else {
            $Dialog.Status.Text = 'DONE: Selected client exited with code {0}.' -f $Dialog.ExitCode
        }
        Add-DialogActivity $Dialog ('Operation finished with exit code {0}.' -f $Dialog.ExitCode)
        $dialogResult = if ($Dialog.ExitCode -eq 0) { [System.Windows.Forms.DialogResult]::OK } else { [System.Windows.Forms.DialogResult]::Abort }
    } catch {
        $message = ConvertTo-RedactedText $_.Exception.Message
        if ([string]::IsNullOrWhiteSpace($message)) {
            $message = ConvertTo-RedactedText $_.ToString()
        }
        $Dialog.ExitCode = 1
        $Dialog.Status.Text = 'FAILED: The selected operation stopped.'
        Add-DialogActivity $Dialog $message
        [System.Windows.Forms.MessageBox]::Show(
            $Dialog.Form,
            $message,
            'MoonFlower Branch Selector',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    } finally {
        $script:ProgressDialog = $null
        Complete-DialogOperation $Dialog
        if ($Dialog.Form.WindowState -eq [System.Windows.Forms.FormWindowState]::Minimized) {
            $Dialog.Form.WindowState = [System.Windows.Forms.FormWindowState]::Normal
            $Dialog.Form.Activate()
        }
    }
    if ($null -ne $dialogResult) {
        $Dialog.Form.DialogResult = $dialogResult
    }
}

try {
    $null = Get-RepositoryRoot $RepoPath
    Assert-RefComponent $script:Remote 'remote'

    if ([string]::IsNullOrWhiteSpace($WorktreeRoot)) {
        $localData = [Environment]::GetFolderPath([Environment+SpecialFolder]::LocalApplicationData)
        if ([string]::IsNullOrWhiteSpace($localData)) {
            throw 'Windows LocalApplicationData is unavailable; specify -WorktreeRoot explicitly.'
        }
        $WorktreeRoot = Join-Path $localData 'MoonFlower\DeveloperTools\worktrees'
    }
    $script:WorktreeRoot = [System.IO.Path]::GetFullPath($WorktreeRoot)

    if ($ListOnly) {
        Write-Host "Fetching remote branches from '$script:Remote'..." -ForegroundColor Cyan
        $branches = Get-AvailableBranches
        if ($branches.Count -eq 0) {
            throw "No remote branches were found for '$script:Remote'."
        }
        foreach ($branch in $branches) {
            Write-Output ("{0}`t{1}`t{2}`t{3}`t{4}" -f $branch.Name, $branch.CommitShort, $branch.UpdatedAt, $branch.Subject, $branch.LocalState)
        }
        exit 0
    }

    $dialog = New-BranchSelectorDialog (-not $NoBuild)
    $dialog.Busy = $false
    $dialog.ExitCode = $null
    $dialog.OperationStartedAt = $null
    $dialog.OperationElapsed = [timespan]::Zero
    $dialog.KeepBox.Checked = [bool]$KeepWorktree
    $dialog.CurrentValue.Text = 'Waiting for the first repository refresh...'
    $dialog.Timer = New-Object System.Windows.Forms.Timer
    $dialog.Timer.Interval = 1000
    $dialog.Timer.add_Tick({
        if ($Dialog.Busy -and $null -ne $Dialog.OperationStartedAt) {
            $Dialog.OperationElapsed = (Get-Date) - $Dialog.OperationStartedAt
            $Dialog.Elapsed.Text = 'Elapsed: {0}' -f ('{0:00}:{1:00}' -f [int]($Dialog.OperationElapsed.TotalMinutes), $Dialog.OperationElapsed.Seconds)
        }
    })
    $dialog.BranchBox.add_SelectedIndexChanged({
        Update-BranchDetails $dialog
    })
    $dialog.KeepBox.add_CheckedChanged({
        Update-BranchDetails $dialog
    })
    $dialog.Refresh.add_Click({
        $previous = if ($dialog.BranchBox.SelectedItem) { $dialog.BranchBox.SelectedItem.Name } else { 'testing' }
        Start-BranchRefresh $dialog $previous
    })
    $dialog.Run.add_Click({
        Start-SelectedBranchOperation $dialog $false
    })
    $dialog.Launch.add_Click({
        Start-SelectedBranchOperation $dialog $true
    })
    $dialog.Form.add_Shown({
        Start-BranchRefresh $dialog 'testing'
    })
    $dialog.Form.add_FormClosing({
        param($sender, $eventArgs)
        if ($dialog.Busy) {
            $eventArgs.Cancel = $true
            Write-Host 'The selector cannot close while its Git/build/client operation is still running.' -ForegroundColor Yellow
        }
    })
    $result = $dialog.Form.ShowDialog()
    $dialog.Timer.Stop()
    $exitCode = if ($null -ne $dialog.ExitCode) { [int]$dialog.ExitCode } else { 0 }
    $dialog.Form.Dispose()

    if ($result -eq [System.Windows.Forms.DialogResult]::Cancel -and $exitCode -eq 0) {
        Write-Host 'Cancelled.' -ForegroundColor Yellow
    }
    exit $exitCode
} catch {
    Write-Error (ConvertTo-RedactedText $_.Exception.Message)
    exit 1
}
