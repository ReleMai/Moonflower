# Moonflower — Haven & Hearth Platform

> **Unified toolkit: desktop launcher, live cartography server, in-game plugins**
> **Version 1.0.0 — February 2026**

---

## Overview

Moonflower is a multi-component platform for **Haven & Hearth** (Java-based sandbox MMO by Seatribe) combining:

- **MoonflowerClient** — Electron 28 desktop app: game launcher, embedded map, settings
- **HavenCartographer** — Node.js map server: tile collection, live upload, REST API, WebSocket, web viewer
- **MoonflowerPlugin** — Java 25 plugin suite: forager bot, map opener, live tile sync, player tracking
- **Web Viewer** — Single-page dashboard + map with game icons, settlements, notes, markers, regions

## Architecture

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
│ │Suite     │ │  │    ├─ /ws        → WebSocket (live feed)   │
│ │-Forager  │ │  │    └─ /data/*    → Static tile images      │
│ │-TileSync │ │  │                                            │
│ │-Map      │ │  │  sql.js ← Tiles/Markers/Icons/Settlements │
│ │-Tracker  │ │  │  Sharp  ← Image stitching                 │
│ └──────────┘ │  │  ws     ← Real-time broadcast             │
└──────────────┘  └────────────────────────────────────────────┘
```

## Project Structure

```
Moonflower/
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
│       │       ├── api.js             # Stats, health, config
│       │       ├── tiles.js           # Tile CRUD + image serving
│       │       ├── live-tiles.js      # Live tile upload (multer)
│       │       ├── markers.js         # Point-of-interest markers
│       │       ├── regions.js         # Named areas & borders
│       │       ├── game-icons.js      # Auto-detected game object icons
│       │       ├── settlements.js     # Villages & claims
│       │       ├── notes.js           # User annotations + screenshots
│       │       └── bots.js            # Bot tracking & commands
│       ├── shared/config.js           # Config loader
│       ├── stitcher/stitch.js         # Tile compositor (Sharp)
│       └── viewer/                    # Web UI (served by Express)
│           ├── index.html             # Single-page app (Dashboard + Map)
│           ├── css/
│           │   ├── styles.css         # Base theme
│           │   └── map-ui.css         # Map UI components
│           └── js/
│               ├── config.js          # Client config loader
│               ├── websocket.js       # WS client
│               ├── map-renderer.js    # Canvas map (pan/zoom/layers)
│               ├── markers.js         # Marker management
│               ├── regions.js         # Region management
│               ├── app.js             # Tab controller + dashboard
│               └── map-page.js        # Map tab logic + panels
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
│           ├── app.js         # View & game control
│           └── map.js         # Embedded map renderer
│
├── MoonflowerPlugin/          # Java plugins for Haven game client
│   ├── build.bat              # Compile + JAR + deploy
│   ├── META-INF/services/     # ServiceLoader registry
│   └── src/haven/plugins/
│       ├── MoonflowerForager.java     # Foraging bot
│       ├── MoonflowerMap.java         # Opens map in browser
│       ├── MoonflowerTileSync.java    # Live tile upload
│       └── MoonflowerTracker.java     # Bot tracking + commands
│
├── docs/                      # Research & documentation
│   └── haven-hearth-game-systems-research.md
│
├── .vscode/prompts/           # AI agent instructions
├── .gitignore
└── README.md
```

## Key Features

### Map Server (HavenCartographer)
- **Multi-layer support** — Surface + cave levels (auto-detected from plugin)
- **Game icons** — 21 icon categories auto-classified from game resource names
- **Settlements** — Auto-labeled villages and claims with owner names + radii
- **Notes** — User-placed annotations with text, icons, colors, and screenshot attachments
- **Markers & Regions** — Manual POI markers and named area borders
- **Live tile sync** — Real-time tile upload from game plugin via REST + WebSocket
- **Tile stitching** — Composite map image generation via Sharp
- **Bot tracking** — Live position reporting and remote command queue
- **Database** — sql.js SQLite with auto-migration for schema upgrades

### Desktop Client (MoonflowerClient)
- **Game launcher** — Configurable Java path, memory, server selection
- **Embedded map** — Canvas-based map viewer connected to Cartographer
- **Settings** — electron-store persistence, safe IPC via context bridge

### Game Plugins (MoonflowerPlugin)
- **Forager** — Automated foraging bot with configurable targets
- **TileSync** — Captures and uploads map tiles to Cartographer in real-time
- **Map** — Opens the web map viewer in the default browser
- **Tracker** — Reports player position, nearby objects, accepts remote commands

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Desktop Shell | Electron 28 | App wrapper + IPC |
| Map Server | Express 4 | REST API + static files |
| Real-time | ws | WebSocket broadcasting |
| Database | sql.js | Pure-JS SQLite |
| Image | Sharp | Tile stitching |
| File Watch | chokidar | Auto-detect new tiles |
| Upload | multer | Multipart tile upload |
| Container | Docker (node:20-slim) | Deployment |
| Game Plugins | Java 25 | Haven client plugins |

## Quick Start

### HavenCartographer (Docker — recommended)
```powershell
cd HavenCartographer
docker compose down
docker compose build --no-cache
docker compose up -d
# Verify: http://127.0.0.1:3300
```

### HavenCartographer (Local dev)
```powershell
cd HavenCartographer
npm install
npm run setup          # First time only
npm start              # Server on :3300
```

### MoonflowerClient
```powershell
cd MoonflowerClient
npm install
.\moonflower.bat       # Launch Electron
.\moonflower-dev.bat   # Launch with DevTools
```

### MoonflowerPlugin
```powershell
cd MoonflowerPlugin
.\build.bat            # Compile + JAR → %APPDATA%\Haven and Hearth\plugins\
```

## API Reference

Base URL: `http://127.0.0.1:3300`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/health` | Server health |
| GET | `/api/stats` | Tile/marker/icon/settlement/note counts + layers |
| GET | `/api/tiles?server=&layer=` | List tiles (layer-filtered) |
| GET | `/api/tiles/image/:x/:y` | Serve tile PNG |
| POST | `/api/tiles/live` | Upload tile (multipart) |
| GET/POST/DELETE | `/api/markers` | Marker CRUD |
| GET/POST/DELETE | `/api/regions` | Region CRUD |
| GET/POST/DELETE | `/api/game-icons` | Game icon CRUD |
| GET/POST/DELETE | `/api/settlements` | Settlement CRUD |
| GET/POST/PUT/DELETE | `/api/notes` | Note CRUD (with screenshots) |
| GET/POST | `/api/bots/*` | Bot tracking & commands |
| POST | `/api/stitch` | Trigger map stitching |

### WebSocket (`ws://localhost:3300/ws`)

Events: `tile:update`, `tile:live`, `game-icon:update`, `settlement:update`, `note:create/update/delete`, `layer:change`, `bot:position`, `stitch:complete`

## Game Info

| Property | Value |
|----------|-------|
| Game | Haven & Hearth |
| Developer | Seatribe (loftar & jorb) |
| Platform | Steam / Standalone |
| Java | OpenJDK 25 (Temurin-25.0.2+10) |
| Server | `game.havenandhearth.com` |

## License

MIT
