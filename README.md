# MoonFlower

MoonFlower is a unified Haven & Hearth workspace. It contains the complete
source-built Java client (including fishing, cookbook, feasting, and combat
helpers) together with local operator services, cartography tools, an Electron
launcher, and Java plugins.

Live account login and current game-server behavior remain user-supervised
validation steps. Passing local builds does not prove live protocol behavior.

## Branch And Client Workflow

This repository uses two normal branches:

- `main` is the stable, GitHub-default branch.
- `testing` is the active integration branch for current client changes and
  supervised local testing.

The generated `client/bin` package is not source control. After switching
branches, rebuild before judging what the client contains. Use
`\.\client\Play.bat -NoUpdate` to run the locally built package; the plain
`Play.bat` path checks the stable update feed first. See
[docs/PROJECT_MAP.md](docs/PROJECT_MAP.md) for the full map and status command.

## Where The Client Systems Live

The full Java client is under `client/`:

- Fishing automation: `client/src/haven/automated/FishingBot.java`
- Manual/helper catch tracking: `client/src/haven/automated/FishingCatchTracker.java`
- Fishing UI, journal, map spots, equipment, and checks: `client/src/haven/fishing/`
- Cookbook: `client/src/haven/cookbook/`
- Feasting helper: `client/src/haven/feasting/`
- Combat assistance and estimated animal health: `client/src/haven/combat/`
- Main client integration: `client/src/haven/GameUI.java`

The normal Fishing action and the Fishing Helper are deliberately separate.
The helper is an explicit visible client control, not a replacement for native
Fishing.

## Repository Layout

- `client/` - source-built MoonFlower Java client and visible helpers
- `shared-protocol/`, `server/`, `web/`, `media-gateway/` - loopback operator platform
- `HavenCartographer/` - Node.js map server and web viewer
- `MoonflowerClient/` - Electron launcher and embedded map
- `MoonflowerPlugin/` - Java plugin suite for compatible Haven clients
- `scripts/` - build, backup, verification, branch testing, source-sync, and
  private packaging helpers
- `docs/` - architecture, operations, verification, roadmap, and active tasks

## Build And Verify The Java Client

Prerequisites are Java 21 or newer and Apache Ant. Close every running
MoonFlower/Haven client before building; the build guard prevents replacing the
packaged JAR underneath a live JVM.

```powershell
Push-Location client
ant clean deftgt
java -cp "bin/*" haven.MoonFlowerChecks
java '-Dhaven.uiscale=1' -cp "bin/*" haven.cookbook.CookbookChecks
java '-Dhaven.uiscale=1' -cp "bin/*" haven.fishing.FishingChecks
java '-Dhaven.uiscale=1' -cp "bin/*" haven.feasting.FeastingChecks
java '-Dhaven.uiscale=1' -cp "bin/*" haven.combat.CombatAssistChecks
Pop-Location
```

For the loopback operator platform, Maven, Node/npm, and Python are also
required:

```powershell
.\scripts\build-all.ps1
.\scripts\start-platform.ps1 -SkipBuild
```

The dashboard is served at `http://127.0.0.1:8080/`. Stop the services with
`.\scripts\stop-platform.ps1`.

## Companion Cartography And Launcher Tools

The pre-existing companion projects remain available alongside the full client:

```powershell
Set-Location HavenCartographer
npm install
npm run setup
npm start
```

The cartographer listens on `http://127.0.0.1:3300/` by default. The Electron
launcher can be started from `MoonflowerClient` after `npm install`, and the
plugin suite can be built with `MoonflowerPlugin\build.bat`.

## Client Data And Security

Back up mutable client data before updates or supervised live validation:

```powershell
.\scripts\backup-client-data.ps1
```

Preferences are stored in `%APPDATA%\Haven and Hearth\MoonFlower-prefs.xml`.
Cookbook, fishing, route, and other mutable databases live under
`%APPDATA%\Haven and Hearth\MoonFlower` and are not part of source builds.

The historical launcher artifact under `artifacts/legacy-launcher` contains
embedded login material. It is ignored by Git and must never be executed,
copied into a release, or used as a credential source. Keep all operator and
cartography services bound to loopback unless exposure is deliberately designed
and secured.

Private Steam packaging is documented in
`docs/STEAM_PRIVATE_PUBLISHING.md`. Preparation and publishing are separate;
the publishing helper refuses public visibility and the inherited Hurricane
Workshop item ID.

See `docs/OPERATIONS.md`, `docs/DATA_BACKUP.md`, and `docs/VERIFICATION.md` for
the repeatable operating and validation process.
