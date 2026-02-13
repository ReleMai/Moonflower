// =============================================================================
// Tile Routes - CRUD for map tiles
// =============================================================================

const express = require('express');
const router = express.Router();
const path = require('path');
const fs = require('fs');
const { getDb, resolveMapId, logActivity } = require('../../database/db');
const { broadcastTileUpdate, broadcastTileActivity } = require('../websocket');
const config = require('../../shared/config');

// GET /api/tiles - List tiles (optional bounds filter)
router.get('/', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || config.haven.defaultServer;
        const mapId = resolveMapId(req, server);

        let sql = 'SELECT * FROM tiles WHERE server = ? AND map_id = ?';
        const params = [server, mapId];

        // Layer filter (default: surface = 0)
        const layer = parseInt(req.query.layer) || 0;
        sql += ' AND layer = ?';
        params.push(layer);

        if (req.query.minX) { sql += ' AND x >= ?'; params.push(parseInt(req.query.minX)); }
        if (req.query.maxX) { sql += ' AND x <= ?'; params.push(parseInt(req.query.maxX)); }
        if (req.query.minY) { sql += ' AND y >= ?'; params.push(parseInt(req.query.minY)); }
        if (req.query.maxY) { sql += ' AND y <= ?'; params.push(parseInt(req.query.maxY)); }

        const result = db.exec(sql, params);
        const tiles = [];
        if (result[0]) {
            const cols = result[0].columns;
            for (const row of result[0].values) {
                const tile = {};
                cols.forEach((col, i) => { tile[col] = row[i]; });
                tiles.push(tile);
            }
        }

        res.json({ server, map_id: mapId, tiles });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/tiles/bounds - Coordinate bounds
router.get('/bounds', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || config.haven.defaultServer;
        const mapId = resolveMapId(req, server);

        const result = db.exec(`
            SELECT MIN(x) as minX, MAX(x) as maxX, MIN(y) as minY, MAX(y) as maxY
            FROM tiles WHERE server = ? AND map_id = ?
        `, [server, mapId]);

        if (result[0] && result[0].values[0][0] !== null) {
            const [minX, maxX, minY, maxY] = result[0].values[0];
            res.json({ server, map_id: mapId, bounds: { minX, maxX, minY, maxY } });
        } else {
            res.json({ server, map_id: mapId, bounds: null });
        }
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/tiles/at/:x/:y - Get specific tile metadata
router.get('/at/:x/:y', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || config.haven.defaultServer;
        const mapId = resolveMapId(req, server);
        const x = parseInt(req.params.x);
        const y = parseInt(req.params.y);

        const result = db.exec(
            'SELECT * FROM tiles WHERE server = ? AND map_id = ? AND x = ? AND y = ?',
            [server, mapId, x, y]
        );

        if (result[0] && result[0].values.length > 0) {
            const tile = {};
            result[0].columns.forEach((col, i) => { tile[col] = result[0].values[0][i]; });
            res.json(tile);
        } else {
            res.status(404).json({ error: 'Tile not found' });
        }
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/tiles/image/:x/:y - Serve tile PNG
router.get('/image/:x/:y', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || config.haven.defaultServer;
        const mapId = resolveMapId(req, server);
        const x = parseInt(req.params.x);
        const y = parseInt(req.params.y);

        const result = db.exec(
            'SELECT file_path FROM tiles WHERE server = ? AND map_id = ? AND x = ? AND y = ? AND layer = ?',
            [server, mapId, x, y, parseInt(req.query.layer) || 0]
        );

        if (result[0] && result[0].values.length > 0) {
            const filePath = result[0].values[0][0];
            const resolved = path.isAbsolute(filePath) ? filePath : path.resolve(filePath);

            if (fs.existsSync(resolved)) {
                res.sendFile(resolved);
            } else {
                res.status(404).json({ error: 'Tile file not found on disk' });
            }
        } else {
            res.status(404).json({ error: 'Tile not in database' });
        }
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/tiles - Register a tile (from collector)
router.post('/', (req, res) => {
    try {
        const db = getDb();
        const { server, x, y, file_path, hash, session_id, source, map_id, layer } = req.body;
        const mapId = map_id || 'default';
        const lyr = layer ?? 0;

        if (!server || x === undefined || y === undefined || !file_path) {
            return res.status(400).json({ error: 'Missing required fields: server, x, y, file_path' });
        }

        // Upsert into tiles (best tile per coord per layer)
        db.run(`
            INSERT INTO tiles (server, map_id, x, y, layer, file_path, hash, session_id, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(server, map_id, x, y, layer) DO UPDATE SET
                file_path = excluded.file_path,
                hash = excluded.hash,
                collected_at = datetime('now'),
                session_id = excluded.session_id,
                source = excluded.source
        `, [server, mapId, x, y, lyr, file_path, hash || null, session_id || null, source || 'collector']);

        // Also store in tile_versions
        db.run(`
            INSERT INTO tile_versions (server, map_id, x, y, layer, file_path, hash, session_id, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        `, [server, mapId, x, y, lyr, file_path, hash || null, session_id || null, source || 'collector']);

        const tile = { server, map_id: mapId, x, y, layer: lyr, file_path };
        logActivity(server, mapId, 'add', x, y, `Tile registered from ${source || 'collector'}`);
        broadcastTileUpdate(tile);
        broadcastTileActivity({ server, map_id: mapId, action: 'add', x, y });

        res.json({ success: true, tile });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// DELETE /api/tiles/at/:x/:y - Remove a tile
router.delete('/at/:x/:y', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || config.haven.defaultServer;
        const mapId = resolveMapId(req, server);
        const x = parseInt(req.params.x);
        const y = parseInt(req.params.y);

        db.run('DELETE FROM tiles WHERE server = ? AND map_id = ? AND x = ? AND y = ?',
            [server, mapId, x, y]);

        logActivity(server, mapId, 'delete', x, y, 'Tile removed');
        broadcastTileActivity({ server, map_id: mapId, action: 'delete', x, y });

        res.json({ deleted: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/tiles/clear - Clear ALL tiles for a server
router.post('/clear', (req, res) => {
    try {
        const db = getDb();
        const server = req.body.server || config.haven.defaultServer;
        const mapId = req.body.map_id || 'default';

        db.run('DELETE FROM tiles WHERE server = ? AND map_id = ?', [server, mapId]);
        logActivity(server, mapId, 'clear', null, null, 'All tiles cleared');
        broadcastTileActivity({ server, map_id: mapId, action: 'clear' });

        res.json({ cleared: true, server, map_id: mapId });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/tiles/activity - Activity log
router.get('/activity', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || config.haven.defaultServer;
        const mapId = resolveMapId(req, server);
        const limit = parseInt(req.query.limit) || 50;

        const result = db.exec(
            'SELECT * FROM tile_activity_log WHERE server = ? AND map_id = ? ORDER BY timestamp DESC LIMIT ?',
            [server, mapId, limit]
        );

        const activity = [];
        if (result[0]) {
            const cols = result[0].columns;
            for (const row of result[0].values) {
                const entry = {};
                cols.forEach((col, i) => { entry[col] = row[i]; });
                activity.push(entry);
            }
        }

        res.json({ server, activity });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

module.exports = router;
