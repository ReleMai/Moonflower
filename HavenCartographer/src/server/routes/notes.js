// =============================================================================
// Notes Routes — User annotations with text + optional screenshot
// =============================================================================

const express = require('express');
const router = express.Router();
const { getDb, resolveMapId, logActivity } = require('../../database/db');
const { broadcastNoteUpdate } = require('../websocket');

function parseRows(result) {
    if (!result || result.length === 0) return [];
    const cols = result[0].columns;
    return result[0].values.map(row => {
        const obj = {};
        cols.forEach((col, i) => { obj[col] = row[i]; });
        return obj;
    });
}

// ── GET /api/notes — List all notes (layer-filtered) ────────────────────

router.get('/', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || 'game.havenandhearth.com';
        const mapId = resolveMapId(req, server);
        const layer = parseInt(req.query.layer) || 0;

        const result = db.exec(
            'SELECT * FROM map_notes WHERE server = ? AND map_id = ? AND layer = ? ORDER BY created_at DESC',
            [server, mapId, layer]
        );
        res.json({ notes: parseRows(result) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── GET /api/notes/:id — Get single note ────────────────────────────────

router.get('/:id', (req, res) => {
    try {
        const db = getDb();
        const result = db.exec('SELECT * FROM map_notes WHERE id = ?', [req.params.id]);
        const rows = parseRows(result);
        if (rows.length === 0) return res.status(404).json({ error: 'Note not found' });
        res.json({ note: rows[0] });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── POST /api/notes — Create a note ─────────────────────────────────────

router.post('/', (req, res) => {
    try {
        const db = getDb();
        const { server, x, y, layer, title, text, screenshot, color, icon } = req.body;

        if (!title || x === undefined || y === undefined) {
            return res.status(400).json({ error: 'title, x, and y required' });
        }

        const srv = server || 'game.havenandhearth.com';
        const mapId = req.body.map_id || 'default';

        db.run(
            `INSERT INTO map_notes (server, map_id, x, y, layer, title, text, screenshot, color, icon)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            [srv, mapId, x, y, layer ?? 0, title, text || '', screenshot || '', color || '#d4c5a0', icon || '📝']
        );

        const result = db.exec('SELECT last_insert_rowid() as id');
        const id = result[0]?.values[0]?.[0];
        const note = { id, server: srv, map_id: mapId, x, y, layer: layer ?? 0, title, text, screenshot: screenshot || '', color: color || '#d4c5a0', icon: icon || '📝' };

        logActivity(srv, mapId, 'note_create', x, y, title);
        broadcastNoteUpdate(note, 'create');

        res.json({ success: true, note });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── PUT /api/notes/:id — Update a note ──────────────────────────────────

router.put('/:id', (req, res) => {
    try {
        const db = getDb();
        const { title, text, screenshot, color, icon, x, y, layer } = req.body;
        const fields = [];
        const values = [];

        if (title !== undefined)      { fields.push('title = ?');      values.push(title); }
        if (text !== undefined)       { fields.push('text = ?');       values.push(text); }
        if (screenshot !== undefined) { fields.push('screenshot = ?'); values.push(screenshot); }
        if (color !== undefined)      { fields.push('color = ?');      values.push(color); }
        if (icon !== undefined)       { fields.push('icon = ?');       values.push(icon); }
        if (x !== undefined)          { fields.push('x = ?');          values.push(x); }
        if (y !== undefined)          { fields.push('y = ?');          values.push(y); }
        if (layer !== undefined)      { fields.push('layer = ?');      values.push(layer); }

        if (fields.length === 0) return res.status(400).json({ error: 'No fields to update' });

        fields.push("updated_at = datetime('now')");
        values.push(req.params.id);

        db.run(`UPDATE map_notes SET ${fields.join(', ')} WHERE id = ?`, values);

        const result = db.exec('SELECT * FROM map_notes WHERE id = ?', [req.params.id]);
        const rows = parseRows(result);
        if (rows.length > 0) broadcastNoteUpdate(rows[0], 'update');

        res.json({ success: true, note: rows[0] || null });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── DELETE /api/notes/:id — Delete a note ───────────────────────────────

router.delete('/:id', (req, res) => {
    try {
        const db = getDb();
        const result = db.exec('SELECT * FROM map_notes WHERE id = ?', [req.params.id]);
        const rows = parseRows(result);
        db.run('DELETE FROM map_notes WHERE id = ?', [req.params.id]);

        if (rows.length > 0) {
            logActivity(rows[0].server, rows[0].map_id, 'note_delete', rows[0].x, rows[0].y, rows[0].title);
            broadcastNoteUpdate(rows[0], 'delete');
        }

        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

module.exports = router;
