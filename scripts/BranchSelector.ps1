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

function Get-AvailableBranches {
    $null = Invoke-Git @('fetch', '--prune', $script:Remote)
    $format = '%(refname:strip=3)' + "`t" + '%(objectname)' + "`t" + '%(committerdate:iso-strict)'
    $result = Invoke-Git @('for-each-ref', ('--format={0}' -f $format), ('refs/remotes/{0}' -f $script:Remote))
    $branches = @()
    foreach ($line in $result.Output) {
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
        $branches += [pscustomobject]@{
            Name = $name
            Commit = $commit
            CommitShort = if ($commit.Length -gt 12) { $commit.Substring(0, 12) } else { $commit }
            UpdatedAt = $updated
            DisplayName = $name
        }
    }
    return @($branches | Sort-Object @{ Expression = { if ($_.Name -eq 'main') { 0 } else { 1 } } }, Name)
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

function Invoke-TestBuild {
    param([string]$WorktreePath)

    if (-not (Get-Command ant -ErrorAction SilentlyContinue)) {
        throw 'Ant is not available on PATH.'
    }
    $clientPath = Join-Path $WorktreePath 'client'
    Write-Host 'Building the selected branch with ant clean deftgt...' -ForegroundColor Cyan
    Push-Location $clientPath
    try {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $output = @(& ant clean deftgt 2>&1 | ForEach-Object { [string]$_ })
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
    } finally {
        Pop-Location
    }
    $output | ForEach-Object { Write-Host $_ }
    if ($exitCode -ne 0) {
        throw ('The selected branch build failed with exit code {0}.' -f $exitCode)
    }
}

function Invoke-TestLaunch {
    param([string]$WorktreePath)

    $clientPath = Join-Path $WorktreePath 'client'
    $launcherPath = Join-Path $clientPath 'Play.bat'
    if (-not (Test-Path -LiteralPath $launcherPath -PathType Leaf)) {
        throw "The selected worktree does not contain client\Play.bat: $launcherPath"
    }
    Write-Host 'Launching the selected branch with -NoUpdate. The GitHub stable updater is bypassed for this test.' -ForegroundColor Yellow
    Push-Location $clientPath
    try {
        & .\Play.bat -NoUpdate
        return $LASTEXITCODE
    } finally {
        Pop-Location
    }
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
    $form.Text = 'MoonFlower Test Branches'
    $form.ClientSize = New-Object System.Drawing.Size(720, 410)
    $form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
    $form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::FixedDialog
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
    $info.Text = 'The selected remote branch is built in a temporary detached Git worktree. Your current checkout is not switched, pulled, or overwritten.'
    $info.Font = New-Object System.Drawing.Font('Segoe UI', 9.5)
    $info.ForeColor = $colors.Muted
    $info.Location = New-Object System.Drawing.Point(30, 62)
    $info.Size = New-Object System.Drawing.Size(650, 42)
    $form.Controls.Add($info)

    $repoLabel = New-Object System.Windows.Forms.Label
    $repoLabel.Text = 'Repository'
    $repoLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $repoLabel.ForeColor = $colors.Gold
    $repoLabel.Location = New-Object System.Drawing.Point(30, 120)
    $repoLabel.Size = New-Object System.Drawing.Size(90, 22)
    $form.Controls.Add($repoLabel)

    $repoValue = New-Object System.Windows.Forms.Label
    $repoValue.Text = $script:RepoRoot
    $repoValue.Font = New-Object System.Drawing.Font('Consolas', 9)
    $repoValue.ForeColor = $colors.Ivory
    $repoValue.AutoEllipsis = $true
    $repoValue.Location = New-Object System.Drawing.Point(125, 120)
    $repoValue.Size = New-Object System.Drawing.Size(555, 22)
    $form.Controls.Add($repoValue)

    $branchLabel = New-Object System.Windows.Forms.Label
    $branchLabel.Text = 'Remote branch'
    $branchLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $branchLabel.ForeColor = $colors.Gold
    $branchLabel.Location = New-Object System.Drawing.Point(30, 155)
    $branchLabel.Size = New-Object System.Drawing.Size(100, 22)
    $form.Controls.Add($branchLabel)

    $branchBox = New-Object System.Windows.Forms.ComboBox
    $branchBox.DropDownStyle = [System.Windows.Forms.ComboBoxStyle]::DropDownList
    $branchBox.DisplayMember = 'DisplayName'
    $branchBox.Font = New-Object System.Drawing.Font('Segoe UI', 10)
    $branchBox.BackColor = $colors.Surface
    $branchBox.ForeColor = $colors.Ivory
    $branchBox.Location = New-Object System.Drawing.Point(145, 151)
    $branchBox.Size = New-Object System.Drawing.Size(410, 28)
    $form.Controls.Add($branchBox)

    $refresh = New-Object System.Windows.Forms.Button
    $refresh.Text = 'Refresh'
    $refresh.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Bold)
    $refresh.BackColor = $colors.Surface
    $refresh.ForeColor = $colors.Ivory
    $refresh.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
    $refresh.FlatAppearance.BorderColor = $colors.Teal
    $refresh.Location = New-Object System.Drawing.Point(570, 151)
    $refresh.Size = New-Object System.Drawing.Size(110, 28)
    $form.Controls.Add($refresh)

    $commitLabel = New-Object System.Windows.Forms.Label
    $commitLabel.Text = 'Commit: —'
    $commitLabel.Font = New-Object System.Drawing.Font('Consolas', 9)
    $commitLabel.ForeColor = $colors.Muted
    $commitLabel.Location = New-Object System.Drawing.Point(145, 187)
    $commitLabel.Size = New-Object System.Drawing.Size(535, 22)
    $form.Controls.Add($commitLabel)

    $updatedLabel = New-Object System.Windows.Forms.Label
    $updatedLabel.Text = 'Remote update: —'
    $updatedLabel.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $updatedLabel.ForeColor = $colors.Muted
    $updatedLabel.Location = New-Object System.Drawing.Point(145, 209)
    $updatedLabel.Size = New-Object System.Drawing.Size(535, 22)
    $form.Controls.Add($updatedLabel)

    $buildBox = New-Object System.Windows.Forms.CheckBox
    $buildBox.Text = 'Clean-build before launch'
    $buildBox.Checked = $BuildByDefault
    $buildBox.Font = New-Object System.Drawing.Font('Segoe UI', 9.5, [System.Drawing.FontStyle]::Bold)
    $buildBox.ForeColor = $colors.Ivory
    $buildBox.Location = New-Object System.Drawing.Point(145, 242)
    $buildBox.Size = New-Object System.Drawing.Size(300, 26)
    $form.Controls.Add($buildBox)

    $status = New-Object System.Windows.Forms.Label
    $status.Text = 'Ready.'
    $status.Font = New-Object System.Drawing.Font('Segoe UI', 9)
    $status.ForeColor = $colors.Teal
    $status.Location = New-Object System.Drawing.Point(30, 286)
    $status.Size = New-Object System.Drawing.Size(650, 36)
    $status.AutoEllipsis = $true
    $form.Controls.Add($status)

    $cancel = New-Object System.Windows.Forms.Button
    $cancel.Text = 'Cancel'
    $cancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
    $cancel.Font = New-Object System.Drawing.Font('Segoe UI', 10)
    $cancel.BackColor = $colors.Surface
    $cancel.ForeColor = $colors.Ivory
    $cancel.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
    $cancel.FlatAppearance.BorderColor = $colors.Muted
    $cancel.Location = New-Object System.Drawing.Point(385, 345)
    $cancel.Size = New-Object System.Drawing.Size(130, 36)
    $form.Controls.Add($cancel)

    $launch = New-Object System.Windows.Forms.Button
    $launch.Text = 'Build && Launch'
    $launch.DialogResult = [System.Windows.Forms.DialogResult]::OK
    $launch.Font = New-Object System.Drawing.Font('Segoe UI', 10, [System.Drawing.FontStyle]::Bold)
    $launch.BackColor = $colors.Teal
    $launch.ForeColor = $colors.Background
    $launch.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
    $launch.FlatAppearance.BorderColor = $colors.Gold
    $launch.Location = New-Object System.Drawing.Point(530, 345)
    $launch.Size = New-Object System.Drawing.Size(150, 36)
    $form.Controls.Add($launch)

    $form.AcceptButton = $launch
    $form.CancelButton = $cancel

    return @{
        Form = $form
        BranchBox = $branchBox
        CommitLabel = $commitLabel
        UpdatedLabel = $updatedLabel
        BuildBox = $buildBox
        Refresh = $refresh
        Status = $status
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
        foreach ($branch in $Branches) {
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
        return
    }
    $Dialog.CommitLabel.Text = 'Commit: {0}' -f $selected.CommitShort
    $Dialog.UpdatedLabel.Text = 'Remote update: {0}' -f $selected.UpdatedAt
}

function Invoke-SelectedBranch {
    param(
        [object]$SelectedBranch,
        [bool]$Build,
        [bool]$Keep
    )

    if ($null -eq $SelectedBranch) {
        throw 'No branch was selected.'
    }
    Assert-ClientStopped
    Write-Host "Selected branch: $($SelectedBranch.Name) ($($SelectedBranch.CommitShort))" -ForegroundColor Yellow
    $worktreePath = $null
    try {
        $worktreePath = New-TestWorktree $SelectedBranch.Name $SelectedBranch.Commit
        Write-Host "Temporary worktree: $worktreePath" -ForegroundColor DarkGray
        if ($Build) {
            Invoke-TestBuild $worktreePath
        }
        $exitCode = Invoke-TestLaunch $worktreePath
        if ($Keep) {
            Write-Host "Keeping test worktree: $worktreePath" -ForegroundColor Yellow
        } else {
            Remove-TestWorktree $worktreePath
            Write-Host 'Temporary test worktree removed.' -ForegroundColor Green
        }
        return $exitCode
    } catch {
        if ($worktreePath -and $Keep) {
            Write-Warning "Keeping failed test worktree for inspection: $worktreePath"
        } elseif ($worktreePath) {
            try {
                Remove-TestWorktree $worktreePath
            } catch {
                Write-Warning "Could not remove temporary test worktree; inspect it manually: $worktreePath"
            }
        }
        throw
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

    Write-Host "Fetching remote branches from '$script:Remote'..." -ForegroundColor Cyan
    $branches = Get-AvailableBranches
    if ($branches.Count -eq 0) {
        throw "No remote branches were found for '$script:Remote'."
    }

    if ($ListOnly) {
        foreach ($branch in $branches) {
            Write-Output ("{0}`t{1}`t{2}" -f $branch.Name, $branch.CommitShort, $branch.UpdatedAt)
        }
        exit 0
    }

    $dialog = New-BranchSelectorDialog (-not $NoBuild)
    $dialog.BranchBox.add_SelectedIndexChanged({ Update-BranchDetails $dialog })
    $dialog.Refresh.add_Click({
        $previous = if ($dialog.BranchBox.SelectedItem) { $dialog.BranchBox.SelectedItem.Name } else { 'main' }
        $dialog.Status.Text = 'Fetching remote branches...'
        [System.Windows.Forms.Application]::DoEvents()
        try {
            $freshBranches = Get-AvailableBranches
            Set-BranchItems $dialog $freshBranches $previous
            $dialog.Status.Text = ('Found {0} remote branch(es).' -f $freshBranches.Count)
        } catch {
            $dialog.Status.Text = 'Refresh failed.'
            [System.Windows.Forms.MessageBox]::Show(
                $dialog.Form,
                (ConvertTo-RedactedText $_.Exception.Message),
                'MoonFlower Branch Selector',
                [System.Windows.Forms.MessageBoxButtons]::OK,
                [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
        }
    })
    Set-BranchItems $dialog $branches 'main'
    Update-BranchDetails $dialog
    $result = $dialog.Form.ShowDialog()
    $selectedBranch = $dialog.BranchBox.SelectedItem
    $build = [bool]$dialog.BuildBox.Checked
    $dialog.Form.Dispose()

    if ($result -ne [System.Windows.Forms.DialogResult]::OK) {
        Write-Host 'Cancelled.' -ForegroundColor Yellow
        exit 0
    }

    $exitCode = Invoke-SelectedBranch $selectedBranch $build ([bool]$KeepWorktree)
    exit $exitCode
} catch {
    Write-Error (ConvertTo-RedactedText $_.Exception.Message)
    exit 1
}
