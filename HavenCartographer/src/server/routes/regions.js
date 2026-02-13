// =============================================================================
// Region Routes - Named areas & borders
// =============================================================================

const express = require('express');
const router = express.Router();
const { getDb, resolveMapId } = require('../../database/db');
const { broadcastRegionUpdate } = require('../websocket');
const config = require('../../shared/config');

function parseRows(result) {
    if (!result[0]) return [];
    const cols = result[0].columns;
    return result[0].values.map(row => {
        const obj = {};
        cols.forEach((col, i) => {
            obj[col] = col === 'points' ? JSON.parse(row[i]) : row[i];
        });
        return obj;
    });
}

// GET /api/regions - List all regions
router.get('/', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || config.haven.defaultServer;
        const mapId = resolveMapId(req, server);

        const result = db.exec(
            'SELECT * FROM regions WHERE server = ? AND map_id = ? ORDER BY created_at DESC',
            [server, mapId]
        );

        res.json({ server, map_id: mapId, regions: parseRows(result) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/regions/types - Region types
router.get('/types', (req, res) => {
    res.json({
        types: [
            { id: 'area', name: 'Area', color: '#c9a84c' },
            { id: 'claim', name: 'Claim', color: '#228B22' },
            { id: 'danger', name: 'Danger Zone', color: '#DC143C' },
            { id: 'resource', name: 'Resource Area', color: '#4169E1' },
            { id: 'biome', name: 'Biome', color: '#DEB887' },
            { id: 'road', name: 'Road', color: '#808080' }
        ]
    });
});

// POST /api/regions - Create a region
router.post('/', (req, res) => {
    try {
        const db = getDb();
        const { server, name, type, color, opacity, points, description, map_id } = req.body;
        const mapId = map_id || 'default';

        if (!server || !name || !points) {
            return res.status(400).json({ error: 'Missing required fields: server, name, points' });
        }

        db.run(`
            INSERT INTO regions (server, map_id, name, type, color, opacity, points, description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `, [server, mapId, name, type || 'area', color || '#c9a84c', opacity || 0.3,
            JSON.stringify(points), description || '']);

        const result = db.exec('SELECT last_insert_rowid()');
        const id = result[0]?.values[0]?.[0];

        const region = { id, server, map_id: mapId, name, type, color, opacity, points, description };
        broadcastRegionUpdate(region, 'create');

        res.json({ success: true, region });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// PUT /api/regions/:id - Update a region
router.put('/:id', (req, res) => {
    try {
        const db = getDb();
        const id = parseInt(req.params.id);
        const { name, type, color, opacity, points, description } = req.body;

        const updates = [];
        const params = [];

        if (name !== undefined) { updates.push('name = ?'); params.push(name); }
        if (type !== undefined) { updates.push('type = ?'); params.push(type); }
        if (color !== undefined) { updates.push('color = ?'); params.push(color); }
        if (opacity !== undefined) { updates.push('opacity = ?'); params.push(opacity); }
        if (points !== undefined) { updates.push('points = ?'); params.push(JSON.stringify(points)); }
        if (description !== undefined) { updates.push('description = ?'); params.push(description); }

        if (updates.length > 0) {
            updates.push("updated_at = datetime('now')");
            params.push(id);
            db.run(`UPDATE regions SET ${updates.join(', ')} WHERE id = ?`, params);
        }

        broadcastRegionUpdate({ id }, 'update');
        res.json({ success: true, id });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// DELETE /api/regions/:id - Delete a region
router.delete('/:id', (req, res) => {
    try {
        const db = getDb();
        const id = parseInt(req.params.id);

        db.run('DELETE FROM regions WHERE id = ?', [id]);
        broadcastRegionUpdate({ id }, 'delete');

        res.json({ deleted: true, id });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

module.exports = router;
