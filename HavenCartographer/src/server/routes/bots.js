// =============================================================================
// Bot Tracking & Command Routes — REST API for bot management
// =============================================================================
// Handles bot position updates, nearby object reports, command issuing,
// and status queries. All data is stored in-memory with overflow to DB
// for performance. Positions are validated and throttled server-side.
// =============================================================================

const express = require('express');
const router = express.Router();
const { getDb, logActivity } = require('../../database/db');
const {
    broadcastBotPosition,
    broadcastBotStatus,
    broadcastBotNearby,
    broadcastBotCommand
} = require('../websocket');
const config = require('../../shared/config');

// -----------------------------------------------------------------------------
// In-memory bot state (hot path — avoids DB latency)
// -----------------------------------------------------------------------------

/** @type {Map<string, object>} botId → latest position + metadata */
const botPositions = new Map();

/** @type {Map<string, object[]>} botId → nearby objects */
const botNearbyCache = new Map();

/** @type {Map<string, number>} botId → last position update timestamp */
const botLastUpdate = new Map();

// Throttle: ignore position updates faster than MIN_UPDATE_INTERVAL_MS
const MIN_UPDATE_INTERVAL_MS = 500;

// Expire bots after OFFLINE_TIMEOUT_MS of no updates
const OFFLINE_TIMEOUT_MS = 30000;

// Periodic cleanup of stale bots
setInterval(() => {
    const now = Date.now();
    for (const [botId, lastTime] of botLastUpdate) {
        if (now - lastTime > OFFLINE_TIMEOUT_MS) {
            const bot = botPositions.get(botId);
            if (bot && bot.status !== 'offline') {
                bot.status = 'offline';
                bot.timestamp = now;
                broadcastBotStatus({ ...bot, status: 'offline' });
                persistBotPosition(bot);
            }
        }
    }
}, 10000);

// -----------------------------------------------------------------------------
// Position validation
// -----------------------------------------------------------------------------

function validatePosition(data) {
    const errors = [];
    if (!data.botId || typeof data.botId !== 'string') errors.push('Missing botId');
    if (data.x !== undefined && (typeof data.x !== 'number' || isNaN(data.x))) errors.push('Invalid x');
    if (data.y !== undefined && (typeof data.y !== 'number' || isNaN(data.y))) errors.push('Invalid y');
    if (data.server && typeof data.server !== 'string') errors.push('Invalid server');
    return errors;
}

function sanitizeBotData(data) {
    return {
        botId: String(data.botId).slice(0, 64),
        name: String(data.name || 'Unknown').slice(0, 64),
        type: String(data.type || 'player').slice(0, 32),
        x: Number(data.x) || 0,
        y: Number(data.y) || 0,
        tileX: Number(data.tileX) || 0,
        tileY: Number(data.tileY) || 0,
        fracX: Math.max(0, Math.min(1, Number(data.fracX) || 0)),
        fracY: Math.max(0, Math.min(1, Number(data.fracY) || 0)),
        moving: Boolean(data.moving),
        status: String(data.status || 'idle').slice(0, 32),
        layer: Number(data.layer) || 0,
        mineId: data.mineId ? String(data.mineId).slice(0, 64) : null,
        mapId: data.mapId !== undefined ? Number(data.mapId) : null,
        server: String(data.server || config.haven.defaultServer).slice(0, 128),
        timestamp: Number(data.timestamp) || Date.now()
    };
}

// -----------------------------------------------------------------------------
// POST /api/bots/position — Update bot position
// -----------------------------------------------------------------------------

router.post('/position', (req, res) => {
    try {
        const errors = validatePosition(req.body);
        if (errors.length > 0) {
            return res.status(400).json({ error: errors.join(', ') });
        }

        const data = sanitizeBotData(req.body);
        const now = Date.now();

        // Throttle check
        const lastTime = botLastUpdate.get(data.botId) || 0;
        if (now - lastTime < MIN_UPDATE_INTERVAL_MS && data.status !== 'offline') {
            return res.json({ success: true, throttled: true });
        }

        // Position jump detection (server-side validation)
        const prev = botPositions.get(data.botId);
        if (prev && data.status !== 'offline' && prev.status !== 'offline') {
            const dist = Math.sqrt(Math.pow(data.x - prev.x, 2) + Math.pow(data.y - prev.y, 2));
            const timeDelta = now - (prev.timestamp || 0);
            // Max reasonable speed: ~200 units/second (running)
            const maxDist = Math.max(500, (timeDelta / 1000) * 200);
            if (dist > maxDist && dist < 100000) {
                // Suspicious jump — accept but flag
                data._flagged = true;
            }
        }

        // Store
        botPositions.set(data.botId, data);
        botLastUpdate.set(data.botId, now);

        // Broadcast to connected web clients
        if (data.status === 'offline') {
            broadcastBotStatus(data);
        } else {
            broadcastBotPosition(data);
        }

        // Persist to DB periodically (every 10th update or on status change)
        const updateCount = (botPositions.get(data.botId)?._updateCount || 0) + 1;
        data._updateCount = updateCount;
        if (updateCount % 10 === 0 || data.status === 'offline' ||
            (prev && prev.status !== data.status)) {
            persistBotPosition(data);
        }

        res.json({ success: true, botId: data.botId });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -----------------------------------------------------------------------------
// POST /api/bots/nearby — Update nearby objects for a bot
// -----------------------------------------------------------------------------

router.post('/nearby', (req, res) => {
    try {
        const { botId, server, objects } = req.body;
        if (!botId) return res.status(400).json({ error: 'Missing botId' });

        const sanitized = (objects || []).slice(0, 50).map(obj => ({
            gobId: Number(obj.gobId) || 0,
            name: String(obj.name || '').slice(0, 128),
            shortName: String(obj.shortName || '').slice(0, 64),
            x: Number(obj.x) || 0,
            y: Number(obj.y) || 0,
            dist: Number(obj.dist) || 0,
            interactable: Boolean(obj.interactable)
        }));

        botNearbyCache.set(botId, sanitized);
        broadcastBotNearby({ botId, server, objects: sanitized });

        res.json({ success: true, count: sanitized.length });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -----------------------------------------------------------------------------
// GET /api/bots — List all tracked bots
// -----------------------------------------------------------------------------

router.get('/', (req, res) => {
    try {
        const server = req.query.server;
        const bots = [];
        const now = Date.now();

        for (const [botId, bot] of botPositions) {
            if (server && bot.server !== server) continue;

            // Auto-offline if stale
            const age = now - (bot.timestamp || 0);
            const status = age > OFFLINE_TIMEOUT_MS ? 'offline' : bot.status;

            bots.push({
                botId: bot.botId,
                name: bot.name,
                type: bot.type,
                x: bot.x,
                y: bot.y,
                tileX: bot.tileX,
                tileY: bot.tileY,
                fracX: bot.fracX,
                fracY: bot.fracY,
                moving: bot.moving,
                status,
                layer: bot.layer,
                server: bot.server,
                timestamp: bot.timestamp,
                age
            });
        }

        res.json({ bots, count: bots.length });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -----------------------------------------------------------------------------
// POST /api/bots/commands — Issue a command to a bot
// -----------------------------------------------------------------------------

router.post('/commands', (req, res) => {
    try {
        const db = getDb();
        const { botId, command, targetX, targetY, gobId, menuOption, server } = req.body;

        if (!botId || !command) {
            return res.status(400).json({ error: 'Missing botId or command' });
        }

        const validCommands = ['walk', 'interact', 'stop', 'forage-start', 'forage-stop'];
        if (!validCommands.includes(command)) {
            return res.status(400).json({ error: 'Invalid command. Valid: ' + validCommands.join(', ') });
        }

        const id = 'cmd-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8);
        const svr = server || config.haven.defaultServer;

        db.run(`
            INSERT INTO bot_commands (id, bot_id, server, command, target_x, target_y, gob_id, menu_option, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending')
        `, [id, botId, svr, command, targetX || null, targetY || null,
            gobId || null, menuOption || null]);

        const cmdData = { id, botId, command, targetX, targetY, gobId, menuOption, server: svr, status: 'pending' };
        broadcastBotCommand(cmdData);
        logActivity(svr, 'default', 'bot_command', targetX || 0, targetY || 0,
            `Command "${command}" issued to bot ${botId}`);

        res.json({ success: true, command: cmdData });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -----------------------------------------------------------------------------
// GET /api/bots/commands — Get pending commands for a bot (polled by plugin)
// -----------------------------------------------------------------------------

router.get('/commands', (req, res) => {
    try {
        const db = getDb();
        const { botId, server, status } = req.query;

        if (!botId) return res.status(400).json({ error: 'Missing botId' });

        const svr = server || config.haven.defaultServer;
        const cmdStatus = status || 'pending';

        const stmt = db.prepare(
            'SELECT * FROM bot_commands WHERE bot_id = ? AND server = ? AND status = ? ORDER BY created_at ASC LIMIT 10'
        );
        stmt.bind([botId, svr, cmdStatus]);

        const commands = [];
        while (stmt.step()) {
            const row = stmt.getAsObject();
            commands.push({
                id: row.id,
                botId: row.bot_id,
                command: row.command,
                targetX: row.target_x,
                targetY: row.target_y,
                gobId: row.gob_id,
                menuOption: row.menu_option,
                status: row.status,
                createdAt: row.created_at
            });
        }
        stmt.free();

        // Mark fetched commands as 'sent'
        for (const cmd of commands) {
            db.run("UPDATE bot_commands SET status = 'sent' WHERE id = ?", [cmd.id]);
        }

        res.json(commands);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -----------------------------------------------------------------------------
// POST /api/bots/commands/status — Update command execution status
// -----------------------------------------------------------------------------

router.post('/commands/status', (req, res) => {
    try {
        const db = getDb();
        const { id, status, error, botId } = req.body;

        if (!id || !status) return res.status(400).json({ error: 'Missing id or status' });

        db.run(
            "UPDATE bot_commands SET status = ?, error = ?, completed_at = datetime('now') WHERE id = ?",
            [status, error || null, id]
        );

        broadcastBotCommand({ id, botId, status, error });
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -----------------------------------------------------------------------------
// GET /api/bots/commands/history — Command history for a bot
// -----------------------------------------------------------------------------

router.get('/commands/history', (req, res) => {
    try {
        const db = getDb();
        const botId = req.query.botId;
        const server = req.query.server || config.haven.defaultServer;
        const limit = Math.min(parseInt(req.query.limit) || 20, 100);

        let query, params;
        if (botId) {
            query = 'SELECT * FROM bot_commands WHERE bot_id = ? AND server = ? ORDER BY created_at DESC LIMIT ?';
            params = [botId, server, limit];
        } else {
            query = 'SELECT * FROM bot_commands WHERE server = ? ORDER BY created_at DESC LIMIT ?';
            params = [server, limit];
        }

        const stmt = db.prepare(query);
        stmt.bind(params);
        const commands = [];
        while (stmt.step()) {
            commands.push(stmt.getAsObject());
        }
        stmt.free();

        res.json({ commands, count: commands.length });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -----------------------------------------------------------------------------
// GET /api/bots/:botId — Get specific bot details + nearby objects
// (Must be after /commands routes to avoid shadowing them)
// -----------------------------------------------------------------------------

router.get('/:botId', (req, res) => {
    try {
        const bot = botPositions.get(req.params.botId);
        if (!bot) return res.status(404).json({ error: 'Bot not found' });

        const nearby = botNearbyCache.get(req.params.botId) || [];
        res.json({ bot, nearby });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -----------------------------------------------------------------------------
// DELETE /api/bots/:botId — Remove a tracked bot
// -----------------------------------------------------------------------------

router.delete('/:botId', (req, res) => {
    try {
        const botId = req.params.botId;
        botPositions.delete(botId);
        botLastUpdate.delete(botId);
        botNearbyCache.delete(botId);

        const db = getDb();
        db.run('DELETE FROM bot_positions WHERE bot_id = ?', [botId]);
        db.run('DELETE FROM bot_commands WHERE bot_id = ?', [botId]);

        broadcastBotStatus({ botId, status: 'removed' });
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -----------------------------------------------------------------------------
// GET /api/bots/:botId/nearby — Get cached nearby objects for a bot
// -----------------------------------------------------------------------------

router.get('/:botId/nearby', (req, res) => {
    try {
        const nearby = botNearbyCache.get(req.params.botId) || [];
        res.json({ objects: nearby, count: nearby.length });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// -----------------------------------------------------------------------------
// DB persistence helper
// -----------------------------------------------------------------------------

function persistBotPosition(data) {
    try {
        const db = getDb();
        db.run(`
            INSERT INTO bot_positions (bot_id, server, name, type, x, y, tile_x, tile_y, status, layer)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bot_id, server) DO UPDATE SET
                name = excluded.name, type = excluded.type,
                x = excluded.x, y = excluded.y,
                tile_x = excluded.tile_x, tile_y = excluded.tile_y,
                status = excluded.status, layer = excluded.layer,
                last_seen = datetime('now')
        `, [data.botId, data.server, data.name, data.type,
            data.x, data.y, data.tileX, data.tileY, data.status, data.layer]);
    } catch (err) {
        console.error('[Bots] DB persist error:', err.message);
    }
}

module.exports = router;
