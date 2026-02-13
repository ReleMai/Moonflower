// =============================================================================
// Haven Cartographer - Map Server
// =============================================================================
// Main entry point for the map hosting server with REST API, WebSocket
// real-time updates, and the web-based map viewer.
// =============================================================================

const express = require('express');
const cors = require('cors');
const path = require('path');
const { WebSocketServer } = require('ws');
const http = require('http');

const config = require('../shared/config');
const { initDatabase } = require('../database/db');
const apiRoutes = require('./routes/api');
const tileRoutes = require('./routes/tiles');
const markerRoutes = require('./routes/markers');
const regionRoutes = require('./routes/regions');
const liveTileRoutes = require('./routes/live-tiles');
const gameIconRoutes = require('./routes/game-icons');
const settlementRoutes = require('./routes/settlements');
const noteRoutes = require('./routes/notes');
const botRoutes = require('./routes/bots');
const { setupWebSocket } = require('./websocket');

// -----------------------------------------------------------------------------
// Server initialization
// -----------------------------------------------------------------------------

const app = express();
const server = http.createServer(app);

// Middleware
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true }));

// Serve the web map viewer
app.use(express.static(path.join(__dirname, '../viewer')));

// Serve tile images
app.use('/tiles', express.static(path.resolve(config.storage.tilesDir)));
app.use('/maps', express.static(path.resolve(config.storage.mapsDir)));
app.use('/live', express.static(path.resolve(config.storage.liveTilesDir || 'data/live')));

// -----------------------------------------------------------------------------
// API Routes
// -----------------------------------------------------------------------------

app.use('/api', apiRoutes);
app.use('/api/tiles/live', liveTileRoutes);
app.use('/api/tiles', tileRoutes);
app.use('/api/markers', markerRoutes);
app.use('/api/regions', regionRoutes);
app.use('/api/game-icons', gameIconRoutes);
app.use('/api/settlements', settlementRoutes);
app.use('/api/notes', noteRoutes);
app.use('/api/bots', botRoutes);

// Stitch endpoint
app.post('/api/stitch', async (req, res) => {
    try {
        const { stitchMap } = require('../stitcher/stitch');
        const targetServer = req.body.server || config.haven.defaultServer;
        const result = await stitchMap(targetServer);
        res.json(result || { message: 'No tiles to stitch' });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Health check
app.get('/api/health', (req, res) => {
    res.json({
        status: 'ok',
        name: 'Haven Cartographer',
        version: '1.0.0',
        uptime: process.uptime()
    });
});

// Explicit route for /map → map.html (standalone live map page)
app.get('/map', (req, res) => {
    res.sendFile(path.join(__dirname, '../viewer/map.html'));
});

// Fallback to viewer for SPA routing (single-page app — all tabs in one page)
app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, '../viewer/index.html'));
});

// -----------------------------------------------------------------------------
// WebSocket for real-time map updates
// -----------------------------------------------------------------------------

const wss = new WebSocketServer({ server, path: '/ws' });
setupWebSocket(wss);

// -----------------------------------------------------------------------------
// Start
// -----------------------------------------------------------------------------

async function start() {
    // Initialize database
    await initDatabase();

    const { host, port } = config.server;

    server.listen(port, host, () => {
        console.log('='.repeat(60));
        console.log('  Haven Cartographer - Map Server');
        console.log('='.repeat(60));
        console.log(`  HTTP:      http://${host}:${port}`);
        console.log(`  WebSocket: ws://${host}:${port}/ws`);
        console.log(`  Viewer:    http://${host}:${port}`);
        console.log(`  API:       http://${host}:${port}/api`);
        console.log('='.repeat(60));
    });

    // Start the tile collector in the same process so they share the DB
    if (process.env.HC_ENABLE_COLLECTOR !== 'false') {
        try {
            const { TileCollector } = require('../collector/watcher');
            const collector = new TileCollector();
            collector.start();

            // Register collector ref with API for status/control endpoints
            const apiModule = require('./routes/api');
            if (apiModule.setCollectorRef) apiModule.setCollectorRef(collector);
        } catch (err) {
            console.error('[Server] Failed to start collector:', err.message);
        }
    }
}

start().catch(err => {
    console.error('Failed to start Haven Cartographer:', err);
    process.exit(1);
});

module.exports = { app, server, wss };
