# Haven Multi-Bot Control Platform

This workspace is now a source monorepo for a visible-client Haven bot platform. It is ready to build and run locally on Windows as a single-operator control plane for multiple Hurricane-based client bots.

- `client/` - Hurricane-based visible client fork with bot-control integration hooks.
- `shared-protocol/` - Shared Java protocol models used by the server.
- `server/` - Spring Boot control server for bots, accounts, tasks, screenshots, audit logs, and WebSockets.
- `web/` - React operator UI.
- `artifacts/legacy-launcher/` - Preserved original launcher bundle and related artifacts.
- `references/webhaven/` - Reference implementation used for protocol and product-shape research.

## Important Security Note

`artifacts/legacy-launcher/autohaven-socrates556.jar` contains embedded login material and should be treated as compromised. Do not reuse it as an operational client artifact.

## Quick Start

### Full Platform

```powershell
.\scripts\start-platform.ps1
```

The packaged dashboard is served from [http://127.0.0.1:8080/](http://127.0.0.1:8080/).

The platform writes runtime state to `../server-data/`.

Default operator credentials:

- username: `admin`
- password: `changeme`

Override with:

- `HAVEN_OPERATOR_USERNAME`
- `HAVEN_OPERATOR_PASSWORD`

To stop the packaged platform:

```powershell
.\scripts\stop-platform.ps1
```

### Development Server

```powershell
.\scripts\start-web.ps1
```

### Packaged Server Only

```powershell
.\scripts\start-server.ps1
```

### Client

The `client/` folder is a Hurricane source fork with bot-control scaffolding added on top. It still uses its own build/runtime flow.

To package all three layers in one pass:

```powershell
.\scripts\build-all.ps1
```

Key build outputs:

- `server/target/server-0.1.0-SNAPSHOT.jar`
- `web/dist/`
- `client/build/hafen.jar`
- `client/bin/`

## Included Features

- Multi-bot fleet dashboard with create/update/delete for bots and accounts
- Launch, stop, pause, resume, abort, queue-clear, and takeover controls
- High-level task execution, route presets, and task presets
- Screenshot capture and low-FPS screenshot streaming
- Read-only live state snapshots for stats, skills, inventory, equipment, and task status
- Operator WebSocket updates, audit trail, and SQLite-backed persistence
- Server-managed bot launch tokens and encrypted account secret storage
- Per-bot process logs in `../server-data/logs/bots/`

## Operations

Detailed run and troubleshooting notes live in [docs/OPERATIONS.md](</D:/Codex Project/Haven and Hearth Custom Client/docs/OPERATIONS.md>).
