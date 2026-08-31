# MoonFlower Automatic Updates

## What It Does

The Windows `Play.bat` launcher checks a rolling GitHub release before starting
MoonFlower. Successful `main` branch changes are built on a clean GitHub Actions
runner, checked, packaged, hashed, and published to the `moonflower-latest`
release.

The launcher downloads a new package into a commit-specific directory under:

```text
%LOCALAPPDATA%\MoonFlower\AutoUpdate\versions\<git-commit>
```

It verifies the package size and SHA-256 hash before extraction. Extraction is
path-constrained, and the active version changes only after the complete package
has been verified. A running JAR is never overwritten.

## Normal Workflow

1. Make, review, commit, and push a change to `main`.
2. GitHub Actions builds and checks the complete client remotely.
3. Keep an existing MoonFlower session open; it continues running its original
   code.
4. The next `Play.bat` launch downloads and runs the newest successful build.

Compilation and CI checks do not prove login, server compatibility, or in-world
behavior. Those remain supervised live checks.

## Failure And Offline Behavior

If the feed is unavailable, the download is interrupted, or the hash does not
match, the launcher does not activate the candidate. It starts the last verified
download instead. If there is no downloaded version, it starts the JAR packaged
beside the launcher.

Useful launch options:

```powershell
.\Play.bat -NoUpdate
powershell -File .\MoonFlower-Update.ps1 -CheckOnly
```

Setting `MOONFLOWER_UPDATE_DISABLED=1` also skips the network check.

## Steam And Workshop Builds

GitHub auto-update and the private Steam Workshop are separate release channels.
The official Haven Launcher reads the Workshop package and may start the Java
client directly instead of executing the batch launcher's PowerShell logic.
Therefore:

- GitHub updates do not silently modify the retained private Workshop item.
- The Workshop item remains creator-only and uses the existing audited publish
  scripts.
- A Steam upload still requires explicit authorization and a stopped client.
- `MoonFlower-Update.ps1` is included in future Workshop packages so manual
  `Play.bat` or `Play_WithSteam.bat` launches from that package can use GitHub
  updates.

Automating Workshop publication would require storing Steam publishing
credentials in CI and would weaken the current owner-reviewed release boundary,
so it is intentionally outside this updater.

## Release Safety Boundary

The rolling release workflow publishes only from `main`. It builds in a clean
checkout, so ignored local preferences, account data, databases, map caches,
logs, and recovery files are not present. The GitHub package is public because
the source repository and release are public.

The local Ant build guard remains unchanged. Local packaging and private Steam
publication still refuse to proceed while a client is running.

## Verification

The updater's deterministic corruption and rollback check is:

```powershell
.\scripts\Test-MoonFlowerUpdater.ps1
```

The CI workflow also builds the complete package and runs the focused MoonFlower,
HUD, clock, inventory, cookbook, fishing, feasting, combat, and wiki checks before
publishing the feed.
