# Operations Guide

## Build

From the repository root:

Close the visible MoonFlower client before building. `build-all.ps1` and the
client Ant deployment targets intentionally fail while `hafen.jar` is running.
Java loads classes lazily, so rebuilding `client/bin/hafen.jar` underneath a
live client can produce `NoClassDefFoundError` and leave a frozen white window.

```powershell
.\scripts\build-all.ps1
```

This runs the React lint/build gates, packages and tests the Maven reactor,
packages the MoonFlower client with Ant, and prepares the Python media gateway.

## Start And Stop

Set non-default local operator credentials in the same PowerShell session, then
start the packaged platform:

```powershell
$env:HAVEN_OPERATOR_USERNAME = "myadmin"
$env:HAVEN_OPERATOR_PASSWORD = "use-a-long-unique-password"
.\scripts\start-platform.ps1 -SkipBuild
```

The dashboard is at [http://127.0.0.1:8080/](http://127.0.0.1:8080/). Both the
server and media gateway bind to loopback by default. `-NoBrowser` suppresses
opening the dashboard automatically.

```powershell
.\scripts\stop-platform.ps1
```

## Run The Visible Client

First make a data backup:

```powershell
.\scripts\backup-client-data.ps1
```

Then launch `client\bin\Play.bat`. The first real-account login after an
upstream port should be watched directly. Verify login, character selection,
world entry, resources, map data, and clean logout before enabling control
actions.

## Runtime Data

- Control database: `..\server-data\haven-bot.db`
- Bot logs: `..\server-data\logs\bots\`
- Screenshots: `..\server-data\screenshots\`
- Clips: `..\server-data\clips\`
- Server logs: `.recovery\runtime\server-run.log` and
  `.recovery\runtime\server-run.err.log`
- Gateway logs: `.recovery\runtime\media-gateway*.log`
- Client settings/maps: `%APPDATA%\Haven and Hearth\`
- Custom client databases: `%APPDATA%\Haven and Hearth\MoonFlower\`

The default server database path is a sibling of this repository. Do not delete
or relocate it casually; back it up before migrations or destructive tests.

## Bot Launch Flow

1. Create an account in the dashboard.
2. Create a bot and set its client install path to the `client` source folder or
   packaged `client\bin` folder.
3. Launch the bot from the fleet screen.
4. The server launches the packaged `Play.bat` from its own directory and passes
   account/session values through the child-process environment.
5. The client connects to the loopback bot WebSocket, logs in, and selects the
   preferred character when available.

Never print the child environment, credentials, or registration token in logs.

## Health And Troubleshooting

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8091/health
```

If startup fails, stop stale processes first and inspect the four runtime logs.
If the dashboard fails after a source update, rerun `build-all.ps1` so
`web\dist` and the packaged server are synchronized.
