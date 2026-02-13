---
applyTo: '**'
---
# Agent Instructions for Moonflower — Haven & Hearth Platform

> **Comprehensive guide for AI agents working on this multi-component Haven & Hearth toolkit**
> **Version 1.0.0 — February 2026**

---

## Project Identity

| Property | Value |
|----------|-------|
| **Name** | Moonflower |
| **Platform** | Electron 28 + Node.js 20 + Java 25 Plugins |
| **Language** | JavaScript (CommonJS/ES Modules) + Java 25 |
| **Game** | Haven & Hearth (Java-based sandbox MMO by loftar/jorb at Seatribe) |
| **Goal** | Unified toolkit: desktop launcher, live cartography server, in-game plugins |
| **Version** | 1.0.0 |

---

## Vision

Moonflower is a **multi-component platform** for Haven & Hearth combining:

1. **MoonflowerClient** — Electron desktop app: game launcher, embedded map, settings
2. **HavenCartographer** — Node.js map server: tile collection, live upload, stitching, REST API, WebSocket, web viewer
3. **MoonflowerPlugin** — Java plugin suite: forager bot, map opener, live tile sync, player/bot tracking
4. **Web Viewer** — Single-page app with Dashboard + Map tabs, game icons, settlements, notes, markers, regions, layer support

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                  MOONFLOWER CLIENT  (Electron 28)            │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐                  │
│  │ Launcher │  │ Map View │  │ Settings  │                  │
│  └────┬─────┘  └────┬─────┘  └───────────┘                  │
│       │     REST + WebSocket to Cartographer                 │
└───────┼──────────────┼───────────────────────────────────────┘
        │              │
        ▼              ▼
┌──────────────┐  ┌────────────────────────────────────────────┐
│ Haven Client │  │       HAVEN CARTOGRAPHER  (Node.js)        │
│ (Java/Steam) │  │                                            │
│ ┌──────────┐ │  │  Express :3300                             │
│ │Moonflower│ │  │    ├─ /          → Dashboard + Map viewer  │
│ │Plugin    │─┼──│─→  ├─ /api/*     → REST API                │
│ │Suite     │ │  │    ├─ /ws        → WebSocket (live)        │
│ │-Forager  │ │  │    └─ /data/*    → Static tile images      │
│ │-TileSync │ │  │                                            │
│ │-Map      │ │  │  sql.js ← Tiles/Icons/Settlements/Notes   │
│ │-Tracker  │ │  │  Sharp  ← Image stitching                 │
│ └──────────┘ │  │  ws     ← Real-time broadcast             │
└──────────────┘  └────────────────────────────────────────────┘
```

---

## Workspace Structure

```
Moonflower/
├── README.md
├── .gitignore
├── HavenCartographer/         # Map server (Docker or local Node.js)
│   ├── config.json            # Server configuration
│   ├── docker-compose.yml     # Docker deployment
│   ├── Dockerfile
│   ├── package.json
│   ├── scripts/setup.js       # First-time directory setup
│   └── src/
│       ├── collector/watcher.js       # chokidar file watcher
│       ├── database/db.js             # sql.js schema + migrations
│       ├── server/
│       │   ├── index.js               # Express entry point
│       │   ├── websocket.js           # WebSocket broadcasting
│       │   └── routes/
│       │       ├── api.js             # Stats, health, config, collector
│       │       ├── tiles.js           # Tile CRUD + image serving (layer-aware)
│       │       ├── live-tiles.js      # Live tile upload via multer (layer-aware)
│       │       ├── markers.js         # Point-of-interest markers
│       │       ├── regions.js         # Named areas & borders
│       │       ├── game-icons.js      # Auto-detected game object icons (21 categories)
│       │       ├── settlements.js     # Villages & claims (auto-labeled)
│       │       ├── notes.js           # User annotations + screenshots
│       │       └── bots.js            # Bot tracking & remote commands
│       ├── shared/config.js           # Config loader with env overrides
│       ├── stitcher/stitch.js         # Tile compositor (Sharp)
│       └── viewer/                    # Web UI (served by Express)
│           ├── index.html             # Single-page app (Dashboard + Map tabs)
│           ├── css/
│           │   ├── styles.css         # Base Moonflower dark theme
│           │   └── map-ui.css         # Map toolbar, panels, dialogs
│           └── js/
│               ├── config.js          # Client config loader
│               ├── websocket.js       # WS client connection
│               ├── map-renderer.js    # Canvas map (pan/zoom/layers/icons/settlements/notes)
│               ├── markers.js         # Marker management
│               ├── regions.js         # Region management
│               ├── app.js             # Tab controller + dashboard stats
│               └── map-page.js        # Map tab logic (panels, dialogs, filters, screenshots)
│
├── MoonflowerClient/          # Electron desktop launcher
│   ├── moonflower.bat         # Windows launcher
│   ├── moonflower-dev.bat     # Dev mode (DevTools)
│   ├── package.json
│   └── src/
│       ├── main/main.js       # Electron main process
│       ├── preload/preload.js # Context bridge (safe IPC)
│       └── renderer/
│           ├── index.html     # UI layout
│           ├── styles.css     # Dark forest theme
│           ├── app.js         # View management, game control
│           └── map.js         # Embedded map renderer (canvas)
│
├── MoonflowerPlugin/          # Java plugins for Haven game client
│   ├── build.bat              # Compile + JAR + deploy
│   ├── META-INF/services/
│   │   └── haven.plugins.Plugin  # ServiceLoader registry (4 plugins)
│   └── src/haven/plugins/
│       ├── MoonflowerForager.java     # Foraging bot
│       ├── MoonflowerMap.java         # Opens map in browser
│       ├── MoonflowerTileSync.java    # Live tile upload to server
│       └── MoonflowerTracker.java     # Bot/player tracking + remote commands
│
├── docs/
│   └── haven-hearth-game-systems-research.md  # Game systems reference
│
└── .vscode/prompts/
    └── AgentInstructions.instructions.md  # This file
```

---

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Desktop Shell** | Electron 28 | App wrapper + IPC |
| **Client Storage** | electron-store | Local settings persistence |
| **Win32 Integration** | koffi (FFI) | Game window management |
| **Map Server** | Express 4 | REST API + static files |
| **Real-time** | ws (WebSocket) | Live tile/marker/icon/settlement/note updates |
| **Database** | sql.js (pure JS SQLite) | All persistent data + auto-migration |
| **Image Processing** | Sharp | Tile stitching, zoom levels |
| **File Watching** | chokidar | Auto-detect new Haven map tiles |
| **Upload** | multer | Multipart tile upload from plugin |
| **Container** | Docker (node:20-slim) | Cartographer deployment |
| **Game Plugins** | Java 25 (haven.plugins.Plugin) | In-game automation via ServiceLoader |
| **Plugin Build** | JDK 25 (OpenJDK Temurin) | Compile against Haven's launcher.jar |

---

## Map Features (Current)

### Multi-Layer Support
All spatial data (tiles, markers, game icons, settlements, notes, regions) has a `layer` column:
- `0` = Surface (default)
- `-1` = Cave level 1, `-2` = Cave level 2, etc.
- Plugin reports player's current layer; map auto-switches via WebSocket `layer:change`

### Game Icons (game-icons.js)
Auto-detected game object icons from the plugin. 21 categories:
`tree, bush, herb, mushroom, rock, ore, animal, water, building, sign, gate, vehicle, container, food, curiosity, bone, clay, mine, dungeon, player, object`
- Each has an emoji + color for map rendering
- `classifyResName(name)` maps game resource paths to categories
- Stored in `game_icons` table (UNIQUE on server+map_id+layer+res_name+x+y)

### Settlements (settlements.js)
Auto-detected villages and claims:
- Types: `village`, `claim`
- Include name, owner, radius
- Stored in `settlements` table (UNIQUE on server+map_id+name+type)

### Notes (notes.js)
User-placed annotations with:
- Title, text body, icon (emoji), color
- Optional base64 screenshot (drag-drop upload in dialog)
- Stored in `map_notes` table

### Markers & Regions
- Markers: manual POI pins with name, category, icon, color, description
- Regions: named polygon areas with color/opacity borders

### Bot Tracking (bots.js)
- In-memory hot path for position updates (throttled)
- Nearby object scanning with distance/direction
- Command queue: walk, interact, flower-menu, stop
- Status heartbeat: idle, moving, busy, foraging, offline

---

## Database Schema (sql.js)

Key tables with `layer INTEGER DEFAULT 0`:
- `tiles` — Best tile per coordinate (UNIQUE server+map_id+x+y+layer)
- `tile_versions` — Historical tile versions
- `live_tiles` — Tiles uploaded via live API
- `markers` — POI markers
- `regions` — Named area polygons
- `game_icons` — Auto-detected game objects
- `settlements` — Villages & claims
- `map_notes` — User annotations + screenshots

Supporting tables:
- `maps`, `sessions`, `stitches`, `tile_activity_log`, `master_coords`, `resources`
- `bot_positions`, `bot_commands`

**Auto-migration**: `runMigrations()` in db.js adds missing `layer` columns to older databases on startup via `ALTER TABLE`.

---

## Java Plugin System

### Compilation

```powershell
cd MoonflowerPlugin
javac -source 25 -target 25 ^
  -cp "C:\Program Files (x86)\Steam\steamapps\common\Haven\launcher.jar" ^
  -d build src\haven\plugins\*.java
```

### Modern Java 25 Features (all allowed)

- `var` keyword, lambdas, diamond operator, try-with-resources
- Streams, text blocks, pattern matching, switch expressions
- Records, sealed classes

### Haven Plugin API

```java
public class MyPlugin extends Plugin {
    @Override
    public void load(UI ui) {
        var glob = ui.sess.glob;
        glob.paginae.add(glob.paginafor(Resource.load("paginae/add/myplugin")));
        XTendedPaginae.registerPlugin("myplugin", this);
    }

    @Override
    public void execute(UI ui) {
        // Called when player clicks the plugin icon in-game
    }
}
```

### Key Haven Classes

| Class | Access Path | Purpose |
|-------|-------------|---------|
| `UI` | (param) | Root UI context |
| `GameUI` | `ui.gui` | Main game UI |
| `MapView` | `ui.gui.map` | Map widget (world view) |
| `Gob` | `ui.gui.map.player()` | Player game object |
| `Moving` | `gob.getattr(Moving.class)` | Movement detection |
| `ResDrawable` | `gob.getattr(ResDrawable.class)` | Resource name (`.res.get().name`) |
| `Composite` | `gob.getattr(Composite.class)` | Alt name source |
| `FlowerMenu` | UI child widget | Right-click context menu |
| `FlowerMenu.Petal` | reflection on `opts` field | Menu option (`.name`) |
| `Config` | static | `Config.defserv`, `Config.userhome` |
| `Glob` | `ui.sess.glob` | Global game state |
| `OCache` | `ui.sess.glob.oc` | Object cache (all gobs) |

### Game Interaction Patterns

```java
// Right-click a gob
ui.wdgmsg((Widget) ui.gui.map, "click",
    new Object[]{gob.sc, gob.rc, 3, 0, 0, (int)gob.id, gob.rc, 0, -1});

// Walk to position
ui.wdgmsg((Widget) ui.gui.map, "click",
    new Object[]{dest, dest, 1, 0});

// Check movement / progress
boolean isMoving = ui.gui.map.player().getattr(Moving.class) != null;
boolean inProgress = ui.gui.prog != -1;

// Get all game objects
var gobs = ui.sess.glob.oc.getGobs();

// FlowerMenu handling (reflection)
var optsField = FlowerMenu.class.getDeclaredField("opts");
optsField.setAccessible(true);
var opts = (FlowerMenu.Petal[]) optsField.get(fm);
fm.choose(opts[0]);
```

### Plugin Deployment

```
Plugin JAR → %APPDATA%\Haven and Hearth\plugins\MoonflowerPlugin.jar
ServiceLoader → META-INF/services/haven.plugins.Plugin (lists all 4 classes)
Game restart required after JAR update
```

---

## HavenCartographer API Reference

Base URL: `http://127.0.0.1:3300`

### REST Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/health` | Server health check |
| GET | `/api/stats` | Counts + layers array |
| GET | `/api/servers` | Known game servers |
| GET | `/api/config` | Public configuration |
| GET | `/api/tiles?server=&layer=` | List tiles (layer-filtered) |
| GET | `/api/tiles/bounds?server=` | Coordinate bounds |
| GET | `/api/tiles/image/:x/:y?server=&layer=` | Serve tile PNG |
| POST | `/api/tiles` | Register tile (collector) |
| POST | `/api/tiles/live` | Upload tile (multipart: tile, server, session, x, y, layer) |
| POST | `/api/tiles/clear` | Clear ALL tiles |
| GET | `/api/tiles/activity` | Activity log |
| GET/POST/PUT/DELETE | `/api/markers` | Marker CRUD |
| GET/POST/PUT/DELETE | `/api/regions` | Region CRUD |
| GET/POST/DELETE | `/api/game-icons` | Game icon CRUD (+ `/categories`, `/clear`) |
| GET/POST/PUT/DELETE | `/api/settlements` | Settlement CRUD (+ `/bulk`) |
| GET/POST/PUT/DELETE | `/api/notes` | Note CRUD (with base64 screenshots) |
| GET/POST | `/api/bots/*` | Bot tracking & commands |
| POST | `/api/stitch` | Trigger map stitching |

### WebSocket Events (`ws://localhost:3300/ws`)

| Event | Direction | Description |
|-------|-----------|-------------|
| `tile:update` | S→C | New/updated tile |
| `tile:live` | S→C | Live tile uploaded |
| `tile:activity` | S→C | Activity log entry |
| `marker:create/update/delete` | S→C | Marker changes |
| `region:create/update/delete` | S→C | Region changes |
| `game-icon:update/delete` | S→C | Game icon changes |
| `settlement:update/delete` | S→C | Settlement changes |
| `note:create/update/delete` | S→C | Note changes |
| `layer:change` | S→C | Player changed layer |
| `bot:position/status/nearby/command` | S→C | Bot events |
| `stitch:complete` | S→C | Stitch finished |

---

## Web Viewer Architecture

### Single-Page App (`index.html`)

Two main tabs: **Dashboard** (stats, activity, connection) and **Map** (full map with panels).
Map tab is lazy-initialized on first activation via `window._initMapPage()`.

### Module Loading Order
`config → websocket → map-renderer → markers → regions → app → map-page`

### Map Renderer (canvas)
- Pan/zoom with mouse/wheel
- Layer-aware: all data loading filters by `currentLayer`
- Tile cache keys include layer: `${layer}_${x}_${y}`
- Draws: tiles → regions → game icons → settlements → markers → notes → grid
- Minimap with layer indicator
- Screenshot capture with configurable element inclusion

### Map Panels (7 tabs)
Layers | Markers | Game Icons | Settlements | Notes | Activity | Screenshots
- Game Icons: search + emoji filter chips (8 categories)
- Settlements: search + type filter (All/Villages/Claims)
- Notes: search + add note button → dialog with screenshot upload

---

## Docker Deployment

```powershell
cd HavenCartographer
docker compose down; docker compose build --no-cache; docker compose up -d
# Verify: http://127.0.0.1:3300/api/health
```

### Volume Mounts
- `cartographer-data:/app/data` — Persistent DB + tile storage
- `../HavenData/map:/haven/map:ro` — Read-only game map folder

### Environment Variables
| Variable | Default | Description |
|----------|---------|-------------|
| `HC_HOST` | `0.0.0.0` | Listen address |
| `HC_PORT` | `3300` | Server port |
| `HC_MAP_PATH` | `/haven/map` | Mounted map tiles |
| `HC_ENABLE_COLLECTOR` | `true` | Enable tile collector |
| `HC_AUTO_STITCH` | `true` | Auto-stitch new tiles |
| `HC_DEFAULT_SERVER` | `game.havenandhearth.com` | Default game server |

---

## Coding Standards

### JavaScript
```javascript
const MAX_RECONNECT_DELAY = 30000;   // SCREAMING_SNAKE_CASE constants
let tileCount = 0;                    // camelCase variables
function handleTileUpdate(data) {}    // camelCase functions
class MapRenderer {}                  // PascalCase classes
// const > let; never var
// Template literals over concatenation
// async/await over raw Promises
```

### Java (Java 25)
```java
var glob = ui.sess.glob;
new Thread(() -> forageLoop(), "Moonflower-Forager").start();
```

### CSS
- Dark forest theme (`--bg-body: #0a0d0c`, `--gold: #c9a84c`, `--forest: #3a8a5c`)
- Font stack: Cinzel (display), Inter (body), Consolas (mono)
- Class naming: kebab-case (`tile-overlay`, `marker-panel`)

### Electron Security
- `nodeIntegration: false` / `contextIsolation: true` (always)
- All IPC through `preload.js` context bridge

---

## Build & Run

### MoonflowerPlugin
```powershell
cd MoonflowerPlugin
.\build.bat    # Compile + JAR + deploy to %APPDATA%\Haven and Hearth\plugins\
```

### HavenCartographer (Docker)
```powershell
cd HavenCartographer
docker compose down; docker compose build --no-cache; docker compose up -d
```

### MoonflowerClient
```powershell
cd MoonflowerClient
npm install
.\moonflower.bat
```

---

## Key Paths

| Item | Location |
|------|----------|
| Game Install | `C:\Program Files (x86)\Steam\steamapps\common\Haven` |
| Game JAR | `Haven\launcher.jar` |
| Bundled Java | `Haven\jre\bin\javaw.exe` (OpenJDK 25 Temurin) |
| User Data | `%APPDATA%\Haven and Hearth\` |
| Plugin folder | `%APPDATA%\Haven and Hearth\plugins\` |
| Map tiles | `%APPDATA%\Haven and Hearth\map\` |

---

## Agent Workflow Checklist

- [ ] **Identify sub-project**: MoonflowerPlugin, HavenCartographer, MoonflowerClient, docs
- [ ] **Read this file first** before assumptions about paths, ports, APIs
- [ ] **Java plugins**: Use Java 25 features; run `build.bat` + restart Haven after changes
- [ ] **Cartographer server/viewer**: Docker rebuild required after changes
- [ ] **Verify endpoints** after deploy: `/api/health`, `/`, `/api/stats`
- [ ] **JavaScript conventions**: `const`/`let`, camelCase, async/await
- [ ] **Electron security**: no nodeIntegration, IPC through preload
- [ ] **Layer awareness**: all spatial queries must filter by layer
- [ ] **Port 3300** for HavenCartographer (never 3200)
- [ ] **No deleted files**: `map.html`, `tags.js`, `labels.js`, `tools.js`, `menu.js`, `panels.css` are all gone

---

*Version 1.0.0 — February 2026*
