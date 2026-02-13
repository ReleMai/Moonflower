// =============================================================================
// Live Tile Upload Routes - Multipart tile upload from plugin
// =============================================================================

const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { getDb, logActivity } = require('../../database/db');
const { broadcastLiveTileUpdate, broadcastTileUpdate, broadcastTileActivity } = require('../websocket');
const config = require('../../shared/config');

// Configure multer for tile uploads
const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        const server = req.body.server || config.haven.defaultServer;
        const session = req.body.session || 'default';
        const dir = path.resolve(config.storage.liveTilesDir, server, session);

        if (!fs.existsSync(dir)) {
            fs.mkdirSync(dir, { recursive: true });
        }
        cb(null, dir);
    },
    filename: (req, file, cb) => {
        const x = req.body.x || '0';
        const y = req.body.y || '0';
        cb(null, `tile_${x}_${y}.png`);
    }
});

const upload = multer({
    storage,
    limits: { fileSize: 10 * 1024 * 1024 }, // 10MB
    fileFilter: (req, file, cb) => {
        if (file.mimetype.startsWith('image/')) {
            cb(null, true);
        } else {
            cb(new Error('Only image files allowed'), false);
        }
    }
});

// POST /api/tiles/live - Upload a tile PNG
router.post('/', upload.single('tile'), (req, res) => {
    try {
        if (!req.file) {
            return res.status(400).json({ error: 'No tile file uploaded' });
        }

        const db = getDb();
        const server = req.body.server || config.haven.defaultServer;
        const session = req.body.session || 'default';
        const mapId = req.body.map_id || 'default';
        const x = parseInt(req.body.x) || 0;
        const y = parseInt(req.body.y) || 0;
        const layer = parseInt(req.body.layer) || 0;
        const filePath = req.file.path;

        // Store in live_tiles table
        db.run(`
            INSERT INTO live_tiles (server, map_id, x, y, layer, session, file_path)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        `, [server, mapId, x, y, layer, session, filePath]);

        // Also upsert into main tiles table
        db.run(`
            INSERT INTO tiles (server, map_id, x, y, layer, file_path, session_id, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'live')
            ON CONFLICT(server, map_id, x, y, layer) DO UPDATE SET
                file_path = excluded.file_path,
                collected_at = datetime('now'),
                session_id = excluded.session_id,
                source = 'live'
        `, [server, mapId, x, y, layer, filePath, session]);

        const tile = { server, map_id: mapId, x, y, layer, session, file_path: filePath };
        logActivity(server, mapId, 'live_upload', x, y, `Live tile from session ${session}`);

        // Auto-set origin on first tile if not already set
        try {
            const originResult = db.exec('SELECT origin_x FROM master_coords WHERE server = ?', [server]);
            if (!originResult[0] || originResult[0].values.length === 0) {
                db.run(`
                    INSERT INTO master_coords (server, origin_x, origin_y, label, updated_at)
                    VALUES (?, ?, ?, 'First Tile', datetime('now'))
                `, [server, x, y]);
                console.log(`[Live] Auto-set origin to (${x}, ${y}) for ${server}`);
            }
        } catch (originErr) {
            // Non-critical — skip
        }

        broadcastLiveTileUpdate(tile);
        broadcastTileUpdate(tile);
        broadcastTileActivity({ server, map_id: mapId, action: 'live_upload', x, y });

        res.json({ success: true, tile });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

module.exports = router;
