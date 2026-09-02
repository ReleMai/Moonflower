[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$RepoPath,
    [string]$Remote = 'origin',
    [string]$Branch = 'main',
    [ValidateRange(5, 1440)][int]$IntervalMinutes = 30,
    [string]$TaskName = 'MoonFlower Source Sync',
    [string]$StateRoot,
    [switch]$Build,
    [switch]$Install,
    [switch]$Uninstall,
    [switch]$Status,
    [switch]$RunNow
)

$ErrorActionPreference = 'Stop'

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

function Get-Task {
    return Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
}

function Show-TaskStatus {
    $task = Get-Task
    if ($null -eq $task) {
        Write-Host ("Scheduled task '{0}' is not installed." -f $TaskName)
        return
    }
    $info = Get-ScheduledTaskInfo -TaskName $TaskName
    [pscustomobject]@{
        TaskName = $task.TaskName
        State = $task.State
        LastRunTime = $info.LastRunTime
        LastTaskResult = $info.LastTaskResult
        NextRunTime = $info.NextRunTime
        Action = $task.Actions.Execute
        Arguments = $task.Actions.Arguments
    } | Format-List
}

function Get-WindowsPowerShellPath {
    $candidate = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "Windows PowerShell was not found at $candidate"
    }
    return $candidate
}

function ConvertTo-TaskArgumentString {
    param(
        [string]$ScriptPath,
        [string]$ResolvedRepoPath
    )

    $parts = @(
        '-NoProfile',
        '-ExecutionPolicy', 'Bypass',
        '-File', ('"{0}"' -f $ScriptPath),
        '-RepoPath', ('"{0}"' -f $ResolvedRepoPath),
        '-Remote', ('"{0}"' -f $Remote),
        '-Branch', ('"{0}"' -f $Branch)
    )
    if ($Build) {
        $parts += '-Build'
    }
    if (-not [string]::IsNullOrWhiteSpace($StateRoot)) {
        $parts += @('-StateRoot', ('"{0}"' -f ([System.IO.Path]::GetFullPath($StateRoot))))
    }
    return ($parts -join ' ')
}

function Install-SourceSyncTask {
    if ([string]::IsNullOrWhiteSpace($RepoPath)) {
        $RepoPath = Split-Path -Parent $PSScriptRoot | Resolve-Path | Select-Object -ExpandProperty Path
    } else {
        if (-not (Test-Path -LiteralPath $RepoPath -PathType Container)) {
            throw "Repository path does not exist: $RepoPath"
        }
        $RepoPath = (Resolve-Path -LiteralPath $RepoPath).Path
    }
    $RepoPath = [System.IO.Path]::GetFullPath($RepoPath)
    $syncScript = Join-Path $PSScriptRoot 'MoonFlower-SourceSync.ps1'
    if (-not (Test-Path -LiteralPath $syncScript -PathType Leaf)) {
        throw "Source-sync script is missing: $syncScript"
    }
    Assert-RefComponent $Remote 'remote'
    Assert-RefComponent $Branch 'branch'
    if ([string]::IsNullOrWhiteSpace($TaskName) -or $TaskName.Contains([char]0)) {
        throw 'TaskName must contain a non-empty task name without null characters.'
    }

    if (-not (Get-Command New-ScheduledTaskAction -ErrorAction SilentlyContinue) -or
        -not (Get-Command New-ScheduledTaskTrigger -ErrorAction SilentlyContinue) -or
        -not (Get-Command Register-ScheduledTask -ErrorAction SilentlyContinue)) {
        throw 'The Windows ScheduledTasks PowerShell module is unavailable.'
    }

    $powershellPath = Get-WindowsPowerShellPath
    $actionArguments = ConvertTo-TaskArgumentString $syncScript $RepoPath
    $action = New-ScheduledTaskAction `
        -Execute $powershellPath `
        -Argument $actionArguments `
        -WorkingDirectory $RepoPath
    $trigger = New-ScheduledTaskTrigger `
        -Once `
        -At (Get-Date).AddMinutes(1) `
        -RepetitionInterval (New-TimeSpan -Minutes $IntervalMinutes) `
        -RepetitionDuration (New-TimeSpan -Days 3650)
    $settings = New-ScheduledTaskSettingsSet `
        -StartWhenAvailable `
        -MultipleInstances IgnoreNew `
        -ExecutionTimeLimit (New-TimeSpan -Minutes 30)
    $userId = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $principal = New-ScheduledTaskPrincipal `
        -UserId $userId `
        -LogonType Interactive `
        -RunLevel Limited

    $description = 'Developer-only MoonFlower source sync. Skips dirty, mismatched, or diverged checkouts.'
    if ($PSCmdlet.ShouldProcess($TaskName, 'register or replace scheduled task')) {
        Register-ScheduledTask `
            -TaskName $TaskName `
            -Action $action `
            -Trigger $trigger `
            -Settings $settings `
            -Principal $principal `
            -Description $description `
            -Force | Out-Null
        Write-Host ("Installed '{0}' every {1} minute(s) for {2}." -f $TaskName, $IntervalMinutes, $RepoPath)
        if ($Build) {
            Write-Host 'The task will rebuild the client only after a fast-forward and only when the client is stopped.'
        }
    }
}

try {
    $operations = @()
    if ($Install) { $operations += '-Install' }
    if ($Uninstall) { $operations += '-Uninstall' }
    if ($Status) { $operations += '-Status' }
    if ($RunNow) { $operations += '-RunNow' }
    $operationCount = $operations.Count
    if ($operationCount -gt 1) {
        throw 'Choose only one of -Install, -Uninstall, -Status, or -RunNow.'
    }

    if ($Status) {
        Show-TaskStatus
        exit 0
    }

    if ($Uninstall) {
        if ($null -eq (Get-Task)) {
            Write-Host ("Scheduled task '{0}' is not installed." -f $TaskName)
            exit 0
        }
        if ($PSCmdlet.ShouldProcess($TaskName, 'unregister scheduled task')) {
            Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
            Write-Host ("Removed scheduled task '{0}'." -f $TaskName)
        }
        exit 0
    }

    if ($RunNow) {
        if ($null -eq (Get-Task)) {
            throw ("Scheduled task '{0}' is not installed." -f $TaskName)
        }
        if ($PSCmdlet.ShouldProcess($TaskName, 'start scheduled task')) {
            Start-ScheduledTask -TaskName $TaskName
            Write-Host ("Started scheduled task '{0}'." -f $TaskName)
        }
        exit 0
    }

    Install-SourceSyncTask
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
