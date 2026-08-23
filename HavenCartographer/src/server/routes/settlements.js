// =============================================================================
// Settlements Routes — Auto-detected villages & claims
// =============================================================================
// The Java plugin detects village stones and personal claims, then sends
// the owner/name and position. The map shows these as automatic labels.
// =============================================================================

const express = require('express');
const router = express.Router();
const { getDb, resolveMapId, logActivity } = require('../../database/db');
const { broadcastSettlementUpdate } = require('../websocket');

function parseRows(result) {
    if (!result || result.length === 0) return [];
    const cols = result[0].columns;
    return result[0].values.map(row => {
        const obj = {};
        cols.forEach((col, i) => { obj[col] = row[i]; });
        return obj;
    });
}

// ── GET /api/settlements — List settlements with layer filter ───────────

router.get('/', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || 'game.havenandhearth.com';
        const mapId = resolveMapId(req, server);
        const layer = parseInt(req.query.layer) || 0;

        let sql = 'SELECT * FROM settlements WHERE server = ? AND map_id = ? AND layer = ?';
        const params = [server, mapId, layer];

        if (req.query.type) { sql += ' AND type = ?'; params.push(req.query.type); }

        sql += ' ORDER BY name ASC';
        const result = db.exec(sql, params);
        res.json({ settlements: parseRows(result) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── POST /api/settlements — Upsert a settlement (from plugin) ──────────

router.post('/', (req, res) => {
    try {
        const db = getDb();
        const { server, x, y, layer, name, type, owner, radius } = req.body;
        const srv = server || 'game.havenandhearth.com';
        const mapId = req.body.map_id || 'default';

        if (!name || x === undefined || y === undefined) {
            return res.status(400).json({ error: 'name, x, and y required' });
        }

        const sType = type || 'claim';
        const sOwner = owner || '';
        const sRadius = radius || 5;
        const sLayer = layer ?? 0;

        db.run(`
            INSERT INTO settlements (server, map_id, x, y, layer, name, type, owner, radius)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(server, map_id, name, type) DO UPDATE SET
                x = excluded.x,
                y = excluded.y,
                layer = excluded.layer,
                owner = excluded.owner,
                radius = excluded.radius,
                updated_at = datetime('now')
        `, [srv, mapId, x, y, sLayer, name, sType, sOwner, sRadius]);

        const result = db.exec('SELECT last_insert_rowid() as id');
        const id = result[0]?.values[0]?.[0];
        const settlement = { id, server: srv, map_id: mapId, x, y, layer: sLayer, name, type: sType, owner: sOwner, radius: sRadius };

        logActivity(srv, mapId, 'settlement_upsert', x, y, `${sType}: ${name}`);
        broadcastSettlementUpdate(settlement, 'create');

        res.json({ success: true, settlement });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── POST /api/settlements/bulk — Bulk upsert from plugin ────────────────

router.post('/bulk', (req, res) => {
    try {
        const db = getDb();
        const { server, settlements } = req.body;
        const srv = server || 'game.havenandhearth.com';
        const mapId = req.body.map_id || 'default';

        if (!settlements || !Array.isArray(settlements)) {
            return res.status(400).json({ error: 'settlements array required' });
        }

        let count = 0;
        for (const s of settlements) {
            if (!s.name || s.x === undefined || s.y === undefined) continue;
            db.run(`
                INSERT INTO settlements (server, map_id, x, y, layer, name, type, owner, radius)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(server, map_id, name, type) DO UPDATE SET
                    x = excluded.x, y = excluded.y, layer = excluded.layer,
                    owner = excluded.owner, radius = excluded.radius,
                    updated_at = datetime('now')
            `, [srv, mapId, s.x, s.y, s.layer ?? 0, s.name, s.type || 'claim', s.owner || '', s.radius || 5]);
            count++;
        }

        broadcastSettlementUpdate({ server: srv, count }, 'bulk');
        res.json({ success: true, count });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── PUT /api/settlements/:id — Update a settlement ──────────────────────

router.put('/:id', (req, res) => {
    try {
        const db = getDb();
        const { name, type, owner, radius, x, y, layer } = req.body;
        const fields = [];
        const values = [];

        if (name !== undefined)   { fields.push('name = ?');   values.push(name); }
        if (type !== undefined)   { fields.push('type = ?');   values.push(type); }
        if (owner !== undefined)  { fields.push('owner = ?');  values.push(owner); }
        if (radius !== undefined) { fields.push('radius = ?'); values.push(radius); }
        if (x !== undefined)      { fields.push('x = ?');      values.push(x); }
        if (y !== undefined)      { fields.push('y = ?');      values.push(y); }
        if (layer !== undefined)  { fields.push('layer = ?');  values.push(layer); }

        if (fields.length === 0) return res.status(400).json({ error: 'No fields to update' });

        fields.push("updated_at = datetime('now')");
        values.push(req.params.id);

        db.run(`UPDATE settlements SET ${fields.join(', ')} WHERE id = ?`, values);

        const result = db.exec('SELECT * FROM settlements WHERE id = ?', [req.params.id]);
        const rows = parseRows(result);
        if (rows.length > 0) broadcastSettlementUpdate(rows[0], 'update');

        res.json({ success: true, settlement: rows[0] || null });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── DELETE /api/settlements/:id — Delete a settlement ───────────────────

router.delete('/:id', (req, res) => {
    try {
        const db = getDb();
        const result = db.exec('SELECT * FROM settlements WHERE id = ?', [req.params.id]);
        const rows = parseRows(result);
        db.run('DELETE FROM settlements WHERE id = ?', [req.params.id]);
        if (rows.length > 0) broadcastSettlementUpdate(rows[0], 'delete');
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

module.exports = router;
