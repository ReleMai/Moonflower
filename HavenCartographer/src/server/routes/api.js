// =============================================================================
// API Routes - General project endpoints
// =============================================================================

const express = require('express');
const router = express.Router();
const { getDb, resolveMapId } = require('../../database/db');
const config = require('../../shared/config');

// Module-level collector state
let _collectorRef = null;

function setCollectorRef(collector) { _collectorRef = collector; }

// GET /api/stats - Project statistics
router.get('/stats', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || config.haven.defaultServer;
        const mapId = resolveMapId(req, server);

        const tileCount = db.exec('SELECT COUNT(*) as count FROM tiles WHERE server = ? AND map_id = ?', [server, mapId]);
        const markerCount = db.exec('SELECT COUNT(*) as count FROM markers WHERE server = ? AND map_id = ?', [server, mapId]);
        const regionCount = db.exec('SELECT COUNT(*) as count FROM regions WHERE server = ? AND map_id = ?', [server, mapId]);
        const sessionCount = db.exec('SELECT COUNT(*) as count FROM sessions WHERE server = ?', [server]);
        const iconCount = db.exec('SELECT COUNT(*) as count FROM game_icons WHERE server = ? AND map_id = ?', [server, mapId]);
        const settlementCount = db.exec('SELECT COUNT(*) as count FROM settlements WHERE server = ? AND map_id = ?', [server, mapId]);
        const noteCount = db.exec('SELECT COUNT(*) as count FROM map_notes WHERE server = ? AND map_id = ?', [server, mapId]);

        // Distinct layers
        const layerResult = db.exec('SELECT DISTINCT layer FROM tiles WHERE server = ? AND map_id = ? ORDER BY layer DESC', [server, mapId]);
        const layers = layerResult[0] ? layerResult[0].values.map(r => r[0]) : [0];

        // Get tile bounds
        const bounds = db.exec(`
            SELECT MIN(x) as minX, MAX(x) as maxX, MIN(y) as minY, MAX(y) as maxY
            FROM tiles WHERE server = ? AND map_id = ?
        `, [server, mapId]);

        const origin = db.exec('SELECT * FROM master_coords WHERE server = ?', [server]);

        res.json({
            server,
            map_id: mapId,
            tiles: tileCount[0]?.values[0]?.[0] || 0,
            markers: markerCount[0]?.values[0]?.[0] || 0,
            regions: regionCount[0]?.values[0]?.[0] || 0,
            sessions: sessionCount[0]?.values[0]?.[0] || 0,
            game_icons: iconCount[0]?.values[0]?.[0] || 0,
            settlements: settlementCount[0]?.values[0]?.[0] || 0,
            notes: noteCount[0]?.values[0]?.[0] || 0,
            layers,
            bounds: bounds[0]?.values[0] ? {
                minX: bounds[0].values[0][0],
                maxX: bounds[0].values[0][1],
                minY: bounds[0].values[0][2],
                maxY: bounds[0].values[0][3]
            } : null,
            origin: origin[0]?.values[0] ? {
                origin_x: origin[0].values[0][1],
                origin_y: origin[0].values[0][2],
                label: origin[0].values[0][3]
            } : { origin_x: 0, origin_y: 0, label: 'Spawn' }
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/servers - Known game servers
router.get('/servers', (req, res) => {
    res.json({
        servers: config.haven.servers,
        default: config.haven.defaultServer
    });
});

// GET /api/config - Public configuration
router.get('/config', (req, res) => {
    res.json({
        server: {
            port: config.server.port
        },
        haven: {
            servers: config.haven.servers,
            defaultServer: config.haven.defaultServer,
            tileSize: config.haven.tileSize
        },
        map: config.map,
        collector: {
            autoStitch: config.collector.autoStitch,
            stitchThreshold: config.collector.stitchThreshold
        }
    });
});

// GET /api/collector/status
router.get('/collector/status', (req, res) => {
    res.json({
        running: _collectorRef ? _collectorRef.isRunning : false,
        paused: _collectorRef ? _collectorRef.isPaused : false
    });
});

// POST /api/collector/pause
router.post('/collector/pause', (req, res) => {
    if (_collectorRef && _collectorRef.pause) {
        _collectorRef.pause();
        res.json({ paused: true });
    } else {
        res.status(404).json({ error: 'Collector not available' });
    }
});

// POST /api/collector/resume
router.post('/collector/resume', (req, res) => {
    if (_collectorRef && _collectorRef.resume) {
        _collectorRef.resume();
        res.json({ paused: false });
    } else {
        res.status(404).json({ error: 'Collector not available' });
    }
});

// GET /api/origin - Get map origin for a server
router.get('/origin', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || config.haven.defaultServer;
        const result = db.exec('SELECT origin_x, origin_y, label, updated_at FROM master_coords WHERE server = ?', [server]);
        if (result[0] && result[0].values.length > 0) {
            const [origin_x, origin_y, label, updated_at] = result[0].values[0];
            res.json({ server, origin_x, origin_y, label, updated_at, set: true });
        } else {
            res.json({ server, origin_x: 0, origin_y: 0, label: 'Spawn', updated_at: null, set: false });
        }
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/origin - Set map origin for a server
router.post('/origin', (req, res) => {
    try {
        const db = getDb();
        const server = req.body.server || config.haven.defaultServer;
        const origin_x = parseInt(req.body.origin_x) || 0;
        const origin_y = parseInt(req.body.origin_y) || 0;
        const label = req.body.label || 'Spawn';

        db.run(`
            INSERT INTO master_coords (server, origin_x, origin_y, label, updated_at)
            VALUES (?, ?, ?, ?, datetime('now'))
            ON CONFLICT(server) DO UPDATE SET
                origin_x = excluded.origin_x,
                origin_y = excluded.origin_y,
                label = excluded.label,
                updated_at = datetime('now')
        `, [server, origin_x, origin_y, label]);

        res.json({ success: true, server, origin_x, origin_y, label });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

module.exports = router;
module.exports.setCollectorRef = setCollectorRef;
