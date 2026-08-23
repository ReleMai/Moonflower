// =============================================================================
// Haven Cartographer - Config Loader
// =============================================================================
// Loads config.json and merges environment variable overrides.
// =============================================================================

const fs = require('fs');
const path = require('path');

const configPath = path.resolve(__dirname, '../../config.json');
let config = {};

try {
    const raw = fs.readFileSync(configPath, 'utf-8');
    config = JSON.parse(raw);
} catch (err) {
    console.warn('[Config] Could not load config.json, using defaults:', err.message);
}

// Default config structure
const defaults = {
    haven: {
        clientPath: 'C:\\Program Files (x86)\\Steam\\steamapps\\common\\Haven',
        dataPath: '%APPDATA%\\Haven and Hearth',
        mapPath: '../HavenData/map',
        servers: ['game.havenandhearth.com'],
        defaultServer: 'game.havenandhearth.com',
        tileSize: 100
    },
    server: {
        host: '127.0.0.1',
        port: 3300
    },
    collector: {
        watchInterval: 2000,
        autoStitch: true,
        stitchThreshold: 10,
        archiveProcessed: true
    },
    stitcher: {
        tileSize: 100,
        outputFormat: 'png',
        outputQuality: 90,
        maxCanvasSize: 16384,
        chunkSize: 50
    },
    storage: {
        dbPath: 'data/cartographer.db',
        tilesDir: 'data/tiles',
        mapsDir: 'data/maps',
        exportsDir: 'data/exports',
        liveTilesDir: 'data/live',
        screenshotsDir: 'data/screenshots'
    },
    map: {
        defaultZoom: 1,
        minZoom: 0.1,
        maxZoom: 10,
        gridEnabled: true,
        gridColor: 'rgba(255,255,255,0.1)'
    }
};

// Deep merge helper
function deepMerge(target, source) {
    const result = { ...target };
    for (const key of Object.keys(source)) {
        if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
            result[key] = deepMerge(target[key] || {}, source[key]);
        } else {
            result[key] = source[key];
        }
    }
    return result;
}

const merged = deepMerge(defaults, config);

// Environment variable overrides
if (process.env.HC_HOST) merged.server.host = process.env.HC_HOST;
if (process.env.HC_PORT) merged.server.port = parseInt(process.env.HC_PORT, 10);
if (process.env.HC_MAP_PATH) merged.haven.mapPath = process.env.HC_MAP_PATH;
if (process.env.HC_ENABLE_COLLECTOR) merged.collector.enabled = process.env.HC_ENABLE_COLLECTOR !== 'false';
if (process.env.HC_AUTO_STITCH) merged.collector.autoStitch = process.env.HC_AUTO_STITCH === 'true';
if (process.env.HC_DEFAULT_SERVER) merged.haven.defaultServer = process.env.HC_DEFAULT_SERVER;

module.exports = merged;
