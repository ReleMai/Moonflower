# MoonFlower Developer Workflow

This document covers two local-only developer tools:

1. synchronizing a clean source checkout with a GitHub branch; and
2. selecting and launching a branch for testing without switching the active
   development checkout.

They are deliberately separate from the normal MoonFlower release updater in
`client/MoonFlower-Update.ps1`. The release updater consumes verified,
commit-addressed packages. The tools in this document operate on source
checkouts and are intended for the repository owner only.

## Research decisions

The implementation follows the documented behavior of the underlying tools:

- [Git fetch](https://git-scm.com/docs/git-fetch) updates remote-tracking
  branches. The sync tool fetches only the configured branch and does not use a
  broad `*:* --force` refspec.
- [Git status porcelain](https://git-scm.com/docs/git-status) is intended for
  stable script parsing, so it is used to detect modified, staged, deleted,
  and untracked files before a source update.
- [Git merge --ff-only](https://git-scm.com/docs/git-merge) refuses to update a
  checkout when the local and remote histories cannot be fast-forwarded. This
  prevents an unattended task from creating a merge commit or resolving a
  conflict on its own.
- [Git worktree](https://git-scm.com/docs/git-worktree) supports multiple
  checkouts and explicitly describes detached throwaway worktrees as useful
  for testing without disturbing existing development.
- Windows [ScheduledTasks PowerShell cmdlets](https://learn.microsoft.com/en-us/powershell/module/scheduledtasks/register-scheduledtask)
  provide the per-user recurring task. The trigger uses the documented
  once-plus-repetition form from
  [New-ScheduledTaskTrigger](https://learn.microsoft.com/en-us/powershell/module/scheduledtasks/new-scheduledtasktrigger).

## Feature A: source checkout synchronization

### User interface

The source sync tool is intentionally headless so it can run from Task
Scheduler and produce a useful log:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\MoonFlower-SourceSync.ps1 `
  -RepoPath 'D:\MoonFlower\main-runner' `
  -Remote origin `
  -Branch main `
  -Build
```

Options:

- `-CheckOnly` fetches and reports whether an update is available without
  changing the checkout. It returns `2` when a fast-forward is available.
- `-Build` runs the guarded `ant clean deftgt` build after a successful
  fast-forward. It also builds an already-current checkout when its packaged
  JAR is missing.
- `-Quiet` keeps scheduled runs out of an interactive console.
- `-StateRoot` changes the private log/lock directory for testing. The default
  is `%LOCALAPPDATA%\MoonFlower\DeveloperTools\SourceSync`.

The normal result codes are:

| Code | Meaning |
| ---: | --- |
| `0` | Already current, successfully updated, or safely skipped. |
| `1` | Git, repository, tool, or build failure. |
| `2` | `-CheckOnly` found a fast-forward update. |

### Safety contract

The tool only updates a checkout when all of these are true:

- the path is a Git worktree;
- the current branch exactly matches the configured branch;
- `git status --porcelain=v1 --untracked-files=all` is empty;
- the configured remote branch can be fetched;
- local `HEAD` is an ancestor of the fetched remote commit; and
- the per-user lock is available.

It skips a dirty, detached, ahead, or diverged checkout without changing its
files. It never rewinds a local branch. A failed build does not cause the
source update to be rolled back; the log records the build failure so the
developer can inspect it.

For that reason, use a dedicated clean clone or clean checkout for automatic
updates. Do not point a recurring task at the checkout where feature work is
being edited. A normal development checkout can still be checked with
`-CheckOnly`, but it will remain safely skipped while dirty or on another
branch.

### Task Scheduler management

Install a per-user task that checks every 30 minutes:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Register-MoonFlowerSourceSync.ps1 `
  -RepoPath 'D:\MoonFlower\main-runner' `
  -Branch main `
  -IntervalMinutes 30 `
  -Build `
  -Install
```

The installer uses the current Windows user, does not store a password, starts
one minute after registration, repeats at the selected interval, ignores an
already-running instance, and starts missed work when available. Use the
following maintenance commands:

```powershell
# Preview registration without changing Task Scheduler.
powershell.exe -File .\scripts\Register-MoonFlowerSourceSync.ps1 `
  -RepoPath 'D:\MoonFlower\main-runner' -WhatIf

# Inspect the exact task and last result.
powershell.exe -File .\scripts\Register-MoonFlowerSourceSync.ps1 -Status

# Run the installed task immediately.
powershell.exe -File .\scripts\Register-MoonFlowerSourceSync.ps1 -RunNow

# Remove only this named task.
powershell.exe -File .\scripts\Register-MoonFlowerSourceSync.ps1 -Uninstall
```

The installer is included in the repository, but it is not registered
automatically by a build or Git push. That keeps a source-control change from
silently creating a persistent Windows task.

## Feature B: branch selector for testing

### User interface

Start it from a source checkout with:

```powershell
.\client\Play.bat -BranchSelect
```

The Windows Forms dialog contains:

- a MoonFlower-themed title and explanation of the isolation boundary;
- repository path and remote branch identity;
- a refreshable branch list populated from `origin`;
- the selected commit and remote update timestamp;
- a checked-by-default `Clean-build before launch` option;
- `Build & Launch`; and
- `Cancel`.

For script-only verification, branch discovery can be exercised without
opening the dialog:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\BranchSelector.ps1 -ListOnly
```

### Runtime structure

The flow is:

```text
client\Play.bat -BranchSelect
        |
        v
scripts\BranchSelector.ps1
        |  fetch origin --prune
        |  show remote branch + commit list
        v
git worktree add --detach %LOCALAPPDATA%\MoonFlower\DeveloperTools\worktrees\...
        |
        |  optional: scripts\assert-client-stopped.ps1 + ant clean deftgt
        v
temporary worktree\client\Play.bat -NoUpdate
        |
        v
client exits -> managed worktree is removed
```

The selected branch is checked out at a detached commit in a generated path
under `%LOCALAPPDATA%\MoonFlower\DeveloperTools\worktrees`. The current
checkout is never checked out, pulled, merged, or reset. The `-NoUpdate` launch
flag is deliberate: a branch test must run the just-built branch package, not
replace it with the stable GitHub release package.

The temporary worktree is removed after the client exits. Pass
`-KeepWorktree` directly to `scripts/BranchSelector.ps1` when a failed test
needs its generated worktree retained for inspection. The cleanup code refuses
to remove paths outside the managed worktree root.

This selector is source-checkout tooling. A packaged release that does not
contain the repository's `scripts` folder will show a clear source-checkout
message when `-BranchSelect` is requested. It does not change GitHub releases,
Steam Workshop content, subscriptions, launcher caches, accounts, or saved
game data.

## Why the two tools are separate

```text
Clean main checkout  --(scheduled fetch + ff-only)-->  current source/package

Active development checkout  --(selector)-->  detached test worktree
                                         \--> build + -NoUpdate launch
```

A cloud-synced folder can be useful for moving files, but it is not the source
of truth for this workflow. Git remains responsible for branch and history
state, while Task Scheduler runs a narrowly scoped sync command. This avoids
having a file-sync service rewrite a live `.git` directory or combine changes
from two machines without a reviewable Git history.

## Verification

Run the deterministic developer-tool checks from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Test-MoonFlowerDeveloperTools.ps1
```

The check creates a temporary bare remote and two disposable clones. It proves
that `-CheckOnly` detects an update, the normal sync fast-forwards a clean
checkout, a dirty checkout is left unchanged, and the branch selector lists
remote branches without switching the test repository's active branch.

The full client build remains a separate guarded verification step. Close all
MoonFlower clients before running `ant clean deftgt`; compilation is not live
login, rendering, server, or in-world verification.
