# Haven & Hearth Custom Client Platform

This repository contains a source-built Hurricane client plus a local,
single-operator control platform. The client integration has been ported from
Hurricane `v1.59b` to `v1.69` (upstream commit `045b1f598a...`). The Java
client, Spring server, React dashboard, and Python media gateway build and pass
their current automated checks.

The first real Haven account login is intentionally left as a supervised manual
verification step. Build and local-platform health do not prove live game
protocol compatibility.

## Layout

- `client/` - Hurricane visible client with local bot-control integration.
- `shared-protocol/` - Java command, event, state, and task models.
- `server/` - loopback Spring Boot control server and SQLite persistence.
- `web/` - React operator dashboard.
- `media-gateway/` - WebRTC bridge and rolling replay/MP4 capture.
- `scripts/` - Windows build, backup, start, and stop helpers.
- `docs/` - provenance, architecture, operations, verification, and roadmap.
- `artifacts/` - ignored historical evidence; never an operational dependency.

## Security Boundary

`artifacts/legacy-launcher/autohaven-socrates556.jar` contains embedded login
material and is treated as compromised. It is ignored by Git and must never be
executed, copied into a release, or used as a credential source.

The server and media gateway bind to `127.0.0.1` by default. Keep that boundary
unless remote exposure is deliberately designed and secured. Override the
development operator credentials before startup:

```powershell
$env:HAVEN_OPERATOR_USERNAME = "myadmin"
$env:HAVEN_OPERATOR_PASSWORD = "use-a-long-unique-password"
```

## Build And Start

Prerequisites are Java 21 or newer, Maven, Node/npm, Python, and Apache Ant.
Ant is resolved from `PATH`, with `C:\apache-ant\bin\ant.bat` as a compatibility
fallback.

```powershell
.\scripts\build-all.ps1
.\scripts\start-platform.ps1 -SkipBuild
```

The dashboard is served at [http://127.0.0.1:8080/](http://127.0.0.1:8080/)
and the WebRTC gateway health endpoint at
[http://127.0.0.1:8091/health](http://127.0.0.1:8091/health).

Stop both services with:

```powershell
.\scripts\stop-platform.ps1
```

Build outputs:

- `client/bin/hafen.jar` and the runnable `client/bin/Play.bat`
- `server/target/server-0.1.0-SNAPSHOT.jar`
- `web/dist/`

## Client Data Safety

Back up preferences, maps/caches, and legacy client databases before the first
live login:

```powershell
.\scripts\backup-client-data.ps1
```

Hurricane preferences/maps remain under `%APPDATA%\Haven and Hearth`. Mutable
custom databases now live under `%APPDATA%\Haven and Hearth\Hurricane`, so an
Ant clean cannot delete them. Packaged seed databases are migrated there on
first use.

See `docs/OPERATIONS.md`, `docs/DATA_BACKUP.md`, and
`docs/UPSTREAM_PROVENANCE.md` for the repeatable operating and update process.
