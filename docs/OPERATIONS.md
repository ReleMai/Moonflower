# Operations Guide

## Start The Platform

```powershell
.\scripts\start-platform.ps1
```

This builds the workspace, launches the packaged Spring Boot server in the background, waits for `/api/health`, and opens the dashboard at [http://127.0.0.1:8080/](http://127.0.0.1:8080/).

Use `-SkipBuild` if you already built the workspace:

```powershell
.\scripts\start-platform.ps1 -SkipBuild
```

## Stop The Platform

```powershell
.\scripts\stop-platform.ps1
```

## Default Operator Login

- Username: `admin`
- Password: `changeme`

Override with environment variables before launch:

```powershell
$env:HAVEN_OPERATOR_USERNAME = "myadmin"
$env:HAVEN_OPERATOR_PASSWORD = "strong-password"
.\scripts\start-platform.ps1
```

## Runtime Data

- SQLite database: [..\server-data\haven-bot.db](</D:/Codex Project/server-data/haven-bot.db>)
- Screenshots: [..\server-data\screenshots](</D:/Codex Project/server-data/screenshots>)
- Per-bot process logs: [..\server-data\logs\bots](</D:/Codex Project/server-data/logs/bots>)
- Packaged server logs: [server-run.log](</D:/Codex Project/Haven and Hearth Custom Client/server-run.log>) and [server-run.err.log](</D:/Codex Project/Haven and Hearth Custom Client/server-run.err.log>)

## Bot Launch Flow

1. Create an account in the dashboard.
2. Create a bot profile and point `client install path` at the built [client\bin](</D:/Codex Project/Haven and Hearth Custom Client/client/bin>) folder or the source [client](</D:/Codex Project/Haven and Hearth Custom Client/client>) folder.
3. Launch the bot from the fleet screen.
4. The server injects:
   - `HAVEN_ACCOUNT_USERNAME`
   - `HAVEN_ACCOUNT_SECRET`
   - `HAVEN_BOT_CHARACTER`
   - `HAVEN_BOT_WORLD`
   - `HAVEN_BOT_ID`
   - `HAVEN_BOT_TOKEN`
   - `HAVEN_BOT_SERVER_URL`
5. The client now uses those values to auto-login and auto-select the preferred character when available.
