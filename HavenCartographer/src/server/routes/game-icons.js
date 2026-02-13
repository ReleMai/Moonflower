// =============================================================================
// Game Icons Routes — In-game object icons broadcast by the plugin
// =============================================================================
// The Java plugin detects gobs (game objects) and sends their resource names
// and positions here. The map viewer renders them with matching icons.
// =============================================================================

const express = require('express');
const router = express.Router();
const { getDb, resolveMapId, logActivity } = require('../../database/db');
const { broadcastGameIconUpdate } = require('../websocket');

function parseRows(result) {
    if (!result || result.length === 0) return [];
    const cols = result[0].columns;
    return result[0].values.map(row => {
        const obj = {};
        cols.forEach((col, i) => { obj[col] = row[i]; });
        return obj;
    });
}

// Haven icon type → category mapping
const ICON_CATEGORIES = {
    tree:      { icon: '🌲', color: '#3a8a5c', label: 'Tree' },
    bush:      { icon: '🌿', color: '#6b9e6e', label: 'Bush' },
    herb:      { icon: '🌱', color: '#3a8a5c', label: 'Herb' },
    mushroom:  { icon: '🍄', color: '#9e6e9e', label: 'Mushroom' },
    rock:      { icon: '🪨', color: '#7a7a7a', label: 'Rock' },
    ore:       { icon: '⛏',  color: '#b58a4a', label: 'Ore' },
    animal:    { icon: '🐾', color: '#b58a4a', label: 'Animal' },
    water:     { icon: '💧', color: '#4a7fb5', label: 'Water' },
    building:  { icon: '🏠', color: '#8a7b6b', label: 'Building' },
    sign:      { icon: '📜', color: '#c9a84c', label: 'Sign' },
    gate:      { icon: '🚪', color: '#8a7b6b', label: 'Gate' },
    vehicle:   { icon: '🚢', color: '#7a7a7a', label: 'Vehicle' },
    container: { icon: '📦', color: '#8a7b6b', label: 'Container' },
    food:      { icon: '🍖', color: '#d4763a', label: 'Food' },
    curiosity: { icon: '✨', color: '#c9a84c', label: 'Curiosity' },
    bone:      { icon: '🦴', color: '#d4c5a0', label: 'Bone' },
    clay:      { icon: '🧱', color: '#b58a4a', label: 'Clay' },
    mine:      { icon: '⛰',  color: '#7a7a7a', label: 'Mine Entrance' },
    dungeon:   { icon: '🕳', color: '#5a5a5a', label: 'Dungeon' },
    player:    { icon: '🧑', color: '#c94444', label: 'Player' },
    object:    { icon: '📍', color: '#b8c9d4', label: 'Object' }
};

// ── GET /api/game-icons — List game icons (with layer + bounds filter) ──

router.get('/', (req, res) => {
    try {
        const db = getDb();
        const server = req.query.server || 'game.havenandhearth.com';
        const mapId = resolveMapId(req, server);
        const layer = parseInt(req.query.layer) || 0;

        let sql = 'SELECT * FROM game_icons WHERE server = ? AND map_id = ? AND layer = ?';
        const params = [server, mapId, layer];

        if (req.query.icon_type) { sql += ' AND icon_type = ?'; params.push(req.query.icon_type); }

        sql += ' ORDER BY last_seen DESC';
        if (req.query.limit) { sql += ' LIMIT ?'; params.push(parseInt(req.query.limit)); }

        const result = db.exec(sql, params);
        res.json({ icons: parseRows(result) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── GET /api/game-icons/categories — Available icon categories ──────────

router.get('/categories', (req, res) => {
    res.json({ categories: ICON_CATEGORIES });
});

// ── POST /api/game-icons — Upsert icons (bulk from plugin) ─────────────

router.post('/', (req, res) => {
    try {
        const db = getDb();
        const { server, icons, layer } = req.body;
        const srv = server || 'game.havenandhearth.com';
        const mapId = req.body.map_id || 'default';
        const lyr = layer ?? 0;

        if (!icons || !Array.isArray(icons)) {
            return res.status(400).json({ error: 'icons array required' });
        }

        let inserted = 0;
        let updated = 0;

        for (const icon of icons) {
            const { x, y, res_name, icon_type, label } = icon;
            if (x === undefined || y === undefined || !res_name) continue;

            const iType = icon_type || classifyResName(res_name);
            const iLabel = label || ICON_CATEGORIES[iType]?.label || '';
            const iLayer = icon.layer ?? lyr;

            db.run(`
                INSERT INTO game_icons (server, map_id, x, y, layer, res_name, icon_type, label)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(server, map_id, layer, res_name, x, y) DO UPDATE SET
                    last_seen = datetime('now'),
                    icon_type = excluded.icon_type,
                    label = excluded.label
            `, [srv, mapId, x, y, iLayer, res_name, iType, iLabel]);

            inserted++;
        }

        broadcastGameIconUpdate({ server: srv, count: inserted, layer: lyr }, 'bulk');
        res.json({ success: true, inserted, updated });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── POST /api/game-icons/single — Single icon upsert ───────────────────

router.post('/single', (req, res) => {
    try {
        const db = getDb();
        const { server, x, y, layer, res_name, icon_type, label } = req.body;
        const srv = server || 'game.havenandhearth.com';
        const mapId = req.body.map_id || 'default';
        const lyr = layer ?? 0;

        if (x === undefined || y === undefined || !res_name) {
            return res.status(400).json({ error: 'x, y, res_name required' });
        }

        const iType = icon_type || classifyResName(res_name);
        const iLabel = label || ICON_CATEGORIES[iType]?.label || '';

        db.run(`
            INSERT INTO game_icons (server, map_id, x, y, layer, res_name, icon_type, label)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(server, map_id, layer, res_name, x, y) DO UPDATE SET
                last_seen = datetime('now'),
                icon_type = excluded.icon_type,
                label = excluded.label
        `, [srv, mapId, x, y, lyr, res_name, iType, iLabel]);

        const icon = { server: srv, x, y, layer: lyr, res_name, icon_type: iType, label: iLabel };
        broadcastGameIconUpdate(icon, 'create');

        res.json({ success: true, icon });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── DELETE /api/game-icons/:id — Delete single icon ─────────────────────

router.delete('/:id', (req, res) => {
    try {
        const db = getDb();
        const result = db.exec('SELECT * FROM game_icons WHERE id = ?', [req.params.id]);
        const rows = parseRows(result);
        db.run('DELETE FROM game_icons WHERE id = ?', [req.params.id]);
        if (rows.length > 0) broadcastGameIconUpdate(rows[0], 'delete');
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ── POST /api/game-icons/clear — Clear all icons for server/layer ───────

router.post('/clear', (req, res) => {
    try {
        const db = getDb();
        const srv = req.body.server || 'game.havenandhearth.com';
        const mapId = req.body.map_id || 'default';
        const layer = req.body.layer;

        if (layer !== undefined) {
            db.run('DELETE FROM game_icons WHERE server = ? AND map_id = ? AND layer = ?', [srv, mapId, layer]);
        } else {
            db.run('DELETE FROM game_icons WHERE server = ? AND map_id = ?', [srv, mapId]);
        }
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Classify a game resource name into an icon category
function classifyResName(name) {
    if (!name) return 'object';
    const n = name.toLowerCase();
    if (n.includes('/trees/'))     return 'tree';
    if (n.includes('/bushes/'))    return 'bush';
    if (n.includes('/herbs/'))     return 'herb';
    if (n.includes('/mush'))       return 'mushroom';
    if (n.includes('/bumlings/'))  return 'rock';
    if (n.includes('/ore'))        return 'ore';
    if (n.includes('/animals/') || n.includes('/kritter/')) return 'animal';
    if (n.includes('/arch/'))      return 'building';
    if (n.includes('/sign'))       return 'sign';
    if (n.includes('/gate'))       return 'gate';
    if (n.includes('/vehicle/'))   return 'vehicle';
    if (n.includes('/container/')) return 'container';
    if (n.includes('mine'))        return 'mine';
    if (n.includes('cellar') || n.includes('dungeon')) return 'dungeon';
    if (n.includes('/player'))     return 'player';
    return 'object';
}

module.exports = router;
