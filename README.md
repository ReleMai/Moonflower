# MoonFlower

MoonFlower is a source-built Haven & Hearth client and local single-operator
platform. The repository contains the visible Java client, cookbook and fishing
features, a loopback Spring service, React dashboard, and Python media gateway.

Live account login remains a user-supervised validation step. Local builds and
service health checks do not prove current game-server compatibility.

## Layout

- `client/` — MoonFlower Java client and visible automation helpers
- `shared-protocol/` — command, event, state, and task models
- `server/` — loopback Spring Boot control service and SQLite persistence
- `web/` — React operator dashboard
- `media-gateway/` — WebRTC bridge and rolling replay capture
- `scripts/` — Windows build, backup, start, and stop helpers
- `docs/` — architecture, operations, verification, roadmap, and active tasks

## Security Boundary

The historical launcher artifact under `artifacts/legacy-launcher` contains
embedded login material. It is ignored by Git and must never be executed,
copied into a release, or used as a credential source.

The server and media gateway bind to `127.0.0.1` by default. Keep that boundary
unless remote exposure is deliberately designed and secured. Override the
development operator credentials before startup:

```powershell
$env:HAVEN_OPERATOR_USERNAME = "myadmin"
$env:HAVEN_OPERATOR_PASSWORD = "use-a-long-unique-password"
```

## Build And Start

Prerequisites are Java 21 or newer, Maven, Node/npm, Python, and Apache Ant.
Close the visible MoonFlower client before building; the build guard prevents
live JAR replacement.

```powershell
.\scripts\build-all.ps1
.\scripts\start-platform.ps1 -SkipBuild
```

The dashboard is served at [http://127.0.0.1:8080/](http://127.0.0.1:8080/)
and the media gateway health endpoint at
[http://127.0.0.1:8091/health](http://127.0.0.1:8091/health).

Stop both services with:

```powershell
.\scripts\stop-platform.ps1
```

## Client Data

Back up client data before updates or supervised live validation:

```powershell
.\scripts\backup-client-data.ps1
```

Preferences are stored in `%APPDATA%\Haven and Hearth\MoonFlower-prefs.xml`.
Mutable client databases live under `%APPDATA%\Haven and Hearth\MoonFlower`, so
clean source builds cannot remove cookbook, fishing, route, or static data.

See `docs/OPERATIONS.md`, `docs/DATA_BACKUP.md`, and `docs/VERIFICATION.md` for
the repeatable operating and validation process.

## Private Steam Workshop Package

MoonFlower's Steam path uses an owner-only Haven & Hearth Workshop item. Local
preparation and external publishing are separate commands, and the uploader
refuses public visibility and the inherited Hurricane item ID. See
`docs/STEAM_PRIVATE_PUBLISHING.md` before preparing or publishing a package.

The Steam client-only build excludes the operator bridge, map/cookbook web
uploaders, and external update checker. MoonFlower no longer saves plaintext
accounts, Haven login tokens, host token identifiers, or account names in error
metadata. Use `scripts/clear-local-sensitive-client-data.ps1` with the client
closed to remove legacy sensitive preference keys without printing their values.
