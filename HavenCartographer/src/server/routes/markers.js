// =============================================================================
// Marker Routes - Point-of-interest markers
// =============================================================================

const express = require('express');
const router = express.Router();
const { getDb, resolveMapId } = require('../../database/db');
const { broadcastMarkerUpdate } = require('../websocket');
const config = require('../../shared/config');

// Helper: parse rows from sql.js result
function parseRows(result) {
    if (!result[0]) return [];
    const cols = result[0].columns;
    return result[0].values.map(row => {
        const obj = {};
        cols.forEach((col, i) => { obj[col] = row[i]; });
        return obj;
    });
}

// GET /api/markers - List all markers
router.get('/', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || config.haven.defaultServer;
        const mapId = resolveMapId(req, server);

        const result = db.exec(
            'SELECT * FROM markers WHERE server = ? AND map_id = ? ORDER BY created_at DESC',
            [server, mapId]
        );

        res.json({ server, map_id: mapId, markers: parseRows(result) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/markers/categories - Marker categories
router.get('/categories', (req, res) => {
    res.json({
        categories: [
            { id: 'default', name: 'Default', icon: '📍', color: '#c9a84c' },
            { id: 'village', name: 'Village', icon: '🏘️', color: '#8B4513' },
            { id: 'mine', name: 'Mine', icon: '⛏️', color: '#808080' },
            { id: 'resource', name: 'Resource', icon: '🌿', color: '#228B22' },
            { id: 'water', name: 'Water', icon: '💧', color: '#4169E1' },
            { id: 'danger', name: 'Danger', icon: '⚠️', color: '#DC143C' },
            { id: 'quest', name: 'Quest', icon: '❗', color: '#FFD700' },
            { id: 'landmark', name: 'Landmark', icon: '🏔️', color: '#DEB887' },
            { id: 'farm', name: 'Farm', icon: '🌾', color: '#DAA520' },
            { id: 'hunting', name: 'Hunting', icon: '🦌', color: '#8B0000' }
        ]
    });
});

// POST /api/markers - Create a marker
router.post('/', (req, res) => {
    try {
        const db = getDb();
        const { server, x, y, name, category, icon, color, description, map_id } = req.body;
        const mapId = map_id || 'default';

        if (!server || x === undefined || y === undefined || !name) {
            return res.status(400).json({ error: 'Missing required fields: server, x, y, name' });
        }

        db.run(`
            INSERT INTO markers (server, map_id, x, y, name, category, icon, color, description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        `, [server, mapId, x, y, name, category || 'default', icon || '📍', color || '#c9a84c', description || '']);

        const result = db.exec('SELECT last_insert_rowid()');
        const id = result[0]?.values[0]?.[0];

        const marker = { id, server, map_id: mapId, x, y, name, category, icon, color, description };
        broadcastMarkerUpdate(marker, 'create');

        res.json({ success: true, marker });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// PUT /api/markers/:id - Update a marker
router.put('/:id', (req, res) => {
    try {
        const db = getDb();
        const id = parseInt(req.params.id);
        const { x, y, name, category, icon, color, description } = req.body;

        db.run(`
            UPDATE markers SET
                x = COALESCE(?, x),
                y = COALESCE(?, y),
                name = COALESCE(?, name),
                category = COALESCE(?, category),
                icon = COALESCE(?, icon),
                color = COALESCE(?, color),
                description = COALESCE(?, description),
                updated_at = datetime('now')
            WHERE id = ?
        `, [x, y, name, category, icon, color, description, id]);

        broadcastMarkerUpdate({ id }, 'update');
        res.json({ success: true, id });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// DELETE /api/markers/:id - Delete a marker
router.delete('/:id', (req, res) => {
    try {
        const db = getDb();
        const id = parseInt(req.params.id);

        db.run('DELETE FROM markers WHERE id = ?', [id]);
        broadcastMarkerUpdate({ id }, 'delete');

        res.json({ deleted: true, id });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

module.exports = router;
