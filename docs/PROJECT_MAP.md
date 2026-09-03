# MoonFlower Project Map

## Normal Git branches

| Branch | Purpose | Rule |
| --- | --- | --- |
| `main` | Stable, GitHub-default source | Keep buildable and merge reviewed changes here |
| `testing` | Current integration and local client testing | Put active feature checkpoints here before promotion |

Older branch tips from the cleanup are preserved as local/remote archive tags
under `archive/cleanup-2026-09-02/`. They are historical recovery points, not
part of the normal workflow.

## Source, generated output, and support files

| Location | Contents |
| --- | --- |
| `client/` | Source-built MoonFlower Java client, resources, Ant build, and launcher |
| `scripts/` | Build guards, verification, source sync, branch selector, and status tools |
| `docs/` | Project decisions, operations, research, validation, roadmap, and task notes |
| `server/`, `shared-protocol/`, `web/`, `media-gateway/` | Loopback operator platform |
| `HavenCartographer/`, `MoonflowerClient/`, `MoonflowerPlugin/` | Companion cartography, launcher, and plugin projects |
| `references/` | Reference material and source snapshots; not the active client source |
| `client/build/`, `client/bin/` | Ignored generated build/package output |
| `.recovery/`, `artifacts/` | Ignored local backups, runtime state, and retained evidence |

Do not edit generated JARs to make a feature appear. Edit `client/src`, build
the branch, and verify the embedded revision instead.

## Check which client is running

From the repository root:

```powershell
.\scripts\Show-MoonFlowerStatus.ps1
```

The report compares the checked-out source commit with the revision embedded
in `client/build/classes/buildinfo` and `client/bin/hafen.jar`. `MATCH` means
the local package was built from the current source commit. `MISMATCH` means
the package is stale or belongs to another branch.

## Build and run the testing branch

Close all MoonFlower/Haven clients first, then run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\assert-client-stopped.ps1
Push-Location client
ant clean deftgt
Pop-Location
.\scripts\Show-MoonFlowerStatus.ps1
.\client\Play.bat -NoUpdate
```

`-NoUpdate` is important for testing: it launches the package just built in
this checkout instead of asking the stable GitHub feed for a production
package. The branch selector remains available through
`.\client\Play.bat -BranchSelect`; it builds a selected branch in an isolated
temporary worktree.

## Stable branch and source synchronization

Use `main` for the stable source line and `testing` for active work:

```powershell
git fetch origin --prune
git switch main
git pull --ff-only
git switch testing
```

The scheduled source-sync tool is intended for a dedicated clean checkout. It
must not be pointed at a dirty editing checkout, and it never overwrites local
changes. Registration is explicit; it is not installed automatically:

```powershell
.\scripts\Register-MoonFlowerSourceSync.ps1 -RepoPath "C:\Path\To\CleanMirror" -Branch main -Install
```
