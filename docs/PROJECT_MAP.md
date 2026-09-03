# MoonFlower Project Map

## Normal Git branches

| Branch | Purpose | Rule |
| --- | --- | --- |
| `main` | Stable, GitHub-default source | Keep buildable and merge reviewed changes here |
| `testing` | Current integration and local client testing | Put active feature checkpoints here before promotion |

## Branch rules

- New changes and ordinary local builds start on `testing`.
- `main` is read-only for development. Promote tested work through review or a
  separately authorized release action.
- After switching branches, rebuild before launching; `client/bin` and
  `client/build` are ignored generated output and can otherwise represent a
  different branch.
- Confirm the source/package relationship with
  `scripts/Show-MoonFlowerStatus.ps1`. Do not call a client run a `testing`
  run unless its packaged JAR reports `MATCH` with the `testing` HEAD.
- Use the descriptive timeline conventions below for branch names, commits,
  merges, pull requests, and push/pull/build status updates. The repository
  PR template at `.github/pull_request_template.md` supplies the required
  description sections.

Older branch tips from the cleanup are preserved as local/remote archive tags
under `archive/cleanup-2026-09-02/`. They are historical recovery points, not
part of the normal workflow.

## Descriptive timeline naming

The name should tell a future reader what changed and where it was headed. Use
lowercase kebab-case for branch path components and concrete outcomes rather
than generic words such as `update` or `misc`.

| Item | Format | Example |
| --- | --- | --- |
| Temporary branch | `codex/<type>/<area>-<outcome>-YYYYMMDD` | `codex/feat/foraging-route-planner-20260902` |
| Branch description | `<purpose>; base=<branch>; handoff=<branch>` | `Validate launcher path; base=testing; handoff=main` |
| Commit/update | `<type>(<area>): <imperative outcome>` | `feat(foraging): add bounded route planning` |
| Testing pull request | `<type>(testing): <imperative outcome>` | `fix(testing): prevent stale updater package` |
| Main promotion request | `release(main): promote testing - <scope> - YYYY-MM-DD` | `release(main): promote testing - client tools - 2026-09-02` |
| Merge | `Merge <source> into <destination>: <outcome> - YYYY-MM-DD` | `Merge testing into main: publish supervised client tools - 2026-09-02` |
| Push/pull/build status | `Update: <operation> <branch> <old-sha> -> <new-sha> - <result>` | `Update: push testing 62de69c6 -> 78cfbe20 - remote verified` |

Descriptions should include the relevant paths or behavior, exact verification
results, and any unverified live/runtime boundary. For a push or pull, name the
remote and ref; for a merge or pull request, name both source/head and
destination/base branches plus the commit range. This keeps the history useful
when several client packages or feature areas are moving at once.

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
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\assert-testing-branch.ps1
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
