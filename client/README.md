# MoonFlower Client

MoonFlower is a source-built Haven & Hearth client with local quality-of-life
features, a cookbook, a fishing helper and journal, display-only fishing map
spots, and optional integration with the loopback operator platform in this
repository.

The client only connects to additional services when those integrations are
explicitly configured. Normal game traffic continues to use the official
Haven & Hearth services.

## Requirements

- Java 21 or newer for this repository's verified build
- Apache Ant for source builds

## Build

Close MoonFlower before building. The build guard refuses to replace the
packaged JAR while a client is running.

```powershell
ant clean deftgt
```

## Launch

Run `Play.bat` from `client/bin` after a successful build. Linux users can use
`Play_Linux.sh`.

On Windows, `Play.bat` checks the public MoonFlower stable release feed before
each launch. When the matching previous build is cached, the updater prefers a
verified file-level delta; otherwise it uses the full package. A verified build
is installed under
`%LOCALAPPDATA%\MoonFlower\AutoUpdate\versions` and used without replacing a
JAR that is already running. If GitHub is unavailable or verification fails,
the launcher uses the last verified download or the packaged client.

Use `Play.bat -NoUpdate` for one offline launch, or set
`MOONFLOWER_UPDATE_DISABLED=1` to disable checks for the current environment.
See [the auto-update guide](../docs/MOONFLOWER_AUTO_UPDATE.md) for release and
Steam behavior.

## Local Data

Preferences are stored in `%APPDATA%\Haven and Hearth\MoonFlower-prefs.xml`.
MoonFlower databases are stored in `%APPDATA%\Haven and Hearth\MoonFlower` so
clean builds cannot remove live cookbook, fishing, route, or static data.

## Repository

[ReleMai/Moonflower](https://github.com/ReleMai/Moonflower)
