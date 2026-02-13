// =============================================================================
// Haven Cartographer - Database (sql.js SQLite)
// =============================================================================
// In-memory SQLite database with file persistence. Stores tiles, markers,
// regions, activity logs, and map metadata.
// =============================================================================

const initSqlJs = require('sql.js');
const fs = require('fs');
const path = require('path');
const config = require('../shared/config');

let db = null;

// ---------------------------------------------------------------------------
// Database initialization
// ---------------------------------------------------------------------------

async function initDatabase() {
    const SQL = await initSqlJs();
    const dbPath = path.resolve(config.storage.dbPath);
    const dbDir = path.dirname(dbPath);

    if (!fs.existsSync(dbDir)) {
        fs.mkdirSync(dbDir, { recursive: true });
    }

    // Load existing DB or create new
    if (fs.existsSync(dbPath)) {
        const buffer = fs.readFileSync(dbPath);
        db = new SQL.Database(buffer);
        console.log('[DB] Loaded existing database');
    } else {
        db = new SQL.Database();
        console.log('[DB] Created new database');
    }

    createSchema();
    runMigrations();
    setupAutoSave(dbPath);

    return db;
}

// ---------------------------------------------------------------------------
// Schema
// ---------------------------------------------------------------------------

function createSchema() {
    db.run(`
        -- Maps table (supports multiple map sessions)
        CREATE TABLE IF NOT EXISTS maps (
            id TEXT PRIMARY KEY,
            server TEXT NOT NULL,
            name TEXT DEFAULT 'Default',
            description TEXT DEFAULT '',
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now')),
            is_active INTEGER DEFAULT 1
        );

        -- Best tile per coordinate (layer: 0=surface, -1=cave1, -2=cave2, …)
        CREATE TABLE IF NOT EXISTS tiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            x INTEGER NOT NULL,
            y INTEGER NOT NULL,
            layer INTEGER DEFAULT 0,
            file_path TEXT NOT NULL,
            hash TEXT,
            is_fog INTEGER DEFAULT 0,
            collected_at TEXT DEFAULT (datetime('now')),
            session_id TEXT,
            source TEXT DEFAULT 'collector',
            UNIQUE(server, map_id, x, y, layer)
        );

        -- All tile versions ever collected
        CREATE TABLE IF NOT EXISTS tile_versions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            x INTEGER NOT NULL,
            y INTEGER NOT NULL,
            layer INTEGER DEFAULT 0,
            file_path TEXT NOT NULL,
            hash TEXT,
            collected_at TEXT DEFAULT (datetime('now')),
            session_id TEXT,
            source TEXT DEFAULT 'collector'
        );

        -- Tiles uploaded via live API
        CREATE TABLE IF NOT EXISTS live_tiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            x INTEGER NOT NULL,
            y INTEGER NOT NULL,
            layer INTEGER DEFAULT 0,
            session TEXT,
            file_path TEXT NOT NULL,
            uploaded_at TEXT DEFAULT (datetime('now'))
        );

        -- Activity log
        CREATE TABLE IF NOT EXISTS tile_activity_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            action TEXT NOT NULL,
            x INTEGER,
            y INTEGER,
            details TEXT,
            timestamp TEXT DEFAULT (datetime('now'))
        );

        -- Point-of-interest markers
        CREATE TABLE IF NOT EXISTS markers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            x REAL NOT NULL,
            y REAL NOT NULL,
            layer INTEGER DEFAULT 0,
            name TEXT NOT NULL,
            category TEXT DEFAULT 'default',
            icon TEXT DEFAULT '📍',
            color TEXT DEFAULT '#c9a84c',
            description TEXT DEFAULT '',
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now'))
        );

        -- Named regions & borders
        CREATE TABLE IF NOT EXISTS regions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            layer INTEGER DEFAULT 0,
            name TEXT NOT NULL,
            type TEXT DEFAULT 'area',
            color TEXT DEFAULT '#c9a84c',
            opacity REAL DEFAULT 0.3,
            points TEXT NOT NULL,
            description TEXT DEFAULT '',
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now'))
        );

        -- Coordinate master (origin tracking)
        CREATE TABLE IF NOT EXISTS master_coords (
            server TEXT PRIMARY KEY,
            origin_x INTEGER DEFAULT 0,
            origin_y INTEGER DEFAULT 0,
            label TEXT DEFAULT 'Spawn',
            updated_at TEXT DEFAULT (datetime('now'))
        );

        -- Resource locations
        CREATE TABLE IF NOT EXISTS resources (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            x REAL NOT NULL,
            y REAL NOT NULL,
            type TEXT NOT NULL,
            name TEXT,
            data TEXT,
            discovered_at TEXT DEFAULT (datetime('now'))
        );

        -- Collection sessions
        CREATE TABLE IF NOT EXISTS sessions (
            id TEXT PRIMARY KEY,
            server TEXT NOT NULL,
            started_at TEXT DEFAULT (datetime('now')),
            ended_at TEXT,
            tile_count INTEGER DEFAULT 0,
            source TEXT DEFAULT 'collector'
        );

        -- Stitch operation history
        CREATE TABLE IF NOT EXISTS stitches (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            tile_count INTEGER,
            output_path TEXT,
            started_at TEXT DEFAULT (datetime('now')),
            completed_at TEXT,
            status TEXT DEFAULT 'pending'
        );

        -- Game icons — objects detected by game plugin (resources, animals, etc.)
        CREATE TABLE IF NOT EXISTS game_icons (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            x REAL NOT NULL,
            y REAL NOT NULL,
            layer INTEGER DEFAULT 0,
            res_name TEXT NOT NULL,
            icon_type TEXT NOT NULL DEFAULT 'object',
            label TEXT DEFAULT '',
            first_seen TEXT DEFAULT (datetime('now')),
            last_seen TEXT DEFAULT (datetime('now')),
            UNIQUE(server, map_id, layer, res_name, x, y)
        );

        -- Settlements — auto-detected villages & claims
        CREATE TABLE IF NOT EXISTS settlements (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            x REAL NOT NULL,
            y REAL NOT NULL,
            layer INTEGER DEFAULT 0,
            name TEXT NOT NULL,
            type TEXT NOT NULL DEFAULT 'claim',
            owner TEXT DEFAULT '',
            radius REAL DEFAULT 5,
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now')),
            UNIQUE(server, map_id, name, type)
        );

        -- Map notes — user annotations with text + optional screenshot
        CREATE TABLE IF NOT EXISTS map_notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            server TEXT NOT NULL,
            map_id TEXT DEFAULT 'default',
            x REAL NOT NULL,
            y REAL NOT NULL,
            layer INTEGER DEFAULT 0,
            title TEXT NOT NULL,
            text TEXT DEFAULT '',
            screenshot TEXT DEFAULT '',
            color TEXT DEFAULT '#d4c5a0',
            icon TEXT DEFAULT '📝',
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT DEFAULT (datetime('now'))
        );

        -- Bot positions — latest known position per bot per server
        CREATE TABLE IF NOT EXISTS bot_positions (
            bot_id TEXT NOT NULL,
            server TEXT NOT NULL,
            name TEXT DEFAULT 'Unknown',
            type TEXT DEFAULT 'player',
            x REAL DEFAULT 0,
            y REAL DEFAULT 0,
            tile_x INTEGER DEFAULT 0,
            tile_y INTEGER DEFAULT 0,
            status TEXT DEFAULT 'offline',
            layer INTEGER DEFAULT 0,
            first_seen TEXT DEFAULT (datetime('now')),
            last_seen TEXT DEFAULT (datetime('now')),
            UNIQUE(bot_id, server)
        );

        -- Bot commands — queued commands from web UI to game plugin
        CREATE TABLE IF NOT EXISTS bot_commands (
            id TEXT PRIMARY KEY,
            bot_id TEXT NOT NULL,
            server TEXT NOT NULL,
            command TEXT NOT NULL,
            target_x REAL,
            target_y REAL,
            gob_id INTEGER,
            menu_option TEXT,
            status TEXT DEFAULT 'pending',
            error TEXT,
            created_at TEXT DEFAULT (datetime('now')),
            completed_at TEXT
        );
    `);

    console.log('[DB] Schema initialized');
}

// ---------------------------------------------------------------------------
// Migrations — add columns that may be missing from older databases
// ---------------------------------------------------------------------------

function runMigrations() {
    const migrations = [
        { table: 'tiles', column: 'layer', sql: 'ALTER TABLE tiles ADD COLUMN layer INTEGER DEFAULT 0' },
        { table: 'tile_versions', column: 'layer', sql: 'ALTER TABLE tile_versions ADD COLUMN layer INTEGER DEFAULT 0' },
        { table: 'live_tiles', column: 'layer', sql: 'ALTER TABLE live_tiles ADD COLUMN layer INTEGER DEFAULT 0' },
        { table: 'markers', column: 'layer', sql: 'ALTER TABLE markers ADD COLUMN layer INTEGER DEFAULT 0' },
        { table: 'regions', column: 'layer', sql: 'ALTER TABLE regions ADD COLUMN layer INTEGER DEFAULT 0' },
    ];

    for (const m of migrations) {
        try {
            const info = db.exec(`PRAGMA table_info(${m.table})`);
            const cols = info[0] ? info[0].values.map(r => r[1]) : [];
            if (!cols.includes(m.column)) {
                db.run(m.sql);
                console.log(`[DB] Migration: added ${m.column} to ${m.table}`);
            }
        } catch (err) {
            // Table might not exist yet or column already exists — safe to skip
        }
    }

    // Rebuild tiles table UNIQUE index to include layer column
    // SQLite cannot ALTER UNIQUE constraints, so we must rebuild the table
    try {
        const indexInfo = db.exec("SELECT sql FROM sqlite_master WHERE type='table' AND name='tiles'");
        if (indexInfo[0] && indexInfo[0].values[0]) {
            const createSql = indexInfo[0].values[0][0];
            // Check if the UNIQUE constraint already includes layer
            if (createSql.includes('UNIQUE(server, map_id, x, y)') && !createSql.includes('UNIQUE(server, map_id, x, y, layer)')) {
                console.log('[DB] Migration: rebuilding tiles table to add layer to UNIQUE constraint...');
                db.run('ALTER TABLE tiles RENAME TO tiles_old');
                db.run(`
                    CREATE TABLE tiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        server TEXT NOT NULL,
                        map_id TEXT DEFAULT 'default',
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        layer INTEGER DEFAULT 0,
                        file_path TEXT NOT NULL,
                        hash TEXT,
                        is_fog INTEGER DEFAULT 0,
                        collected_at TEXT DEFAULT (datetime('now')),
                        session_id TEXT,
                        source TEXT DEFAULT 'collector',
                        UNIQUE(server, map_id, x, y, layer)
                    )
                `);
                db.run(`
                    INSERT INTO tiles (id, server, map_id, x, y, layer, file_path, hash, is_fog, collected_at, session_id, source)
                    SELECT id, server, map_id, x, y, COALESCE(layer, 0), file_path, hash, is_fog, collected_at, session_id, source
                    FROM tiles_old
                `);
                db.run('DROP TABLE tiles_old');
                console.log('[DB] Migration: tiles table rebuilt with UNIQUE(server, map_id, x, y, layer)');
            }
        }
    } catch (err) {
        console.error('[DB] Migration error rebuilding tiles table:', err.message);
    }

    console.log('[DB] Migrations complete');
}

// ---------------------------------------------------------------------------
// Auto-save to disk
// ---------------------------------------------------------------------------

let saveTimer = null;

function setupAutoSave(dbPath) {
    // Save every 30 seconds
    saveTimer = setInterval(() => {
        saveDatabase(dbPath);
    }, 30000);

    // Save on exit
    process.on('SIGINT', () => {
        saveDatabase(dbPath);
        process.exit(0);
    });
    process.on('SIGTERM', () => {
        saveDatabase(dbPath);
        process.exit(0);
    });
}

function saveDatabase(dbPath) {
    if (!db) return;
    try {
        const data = db.export();
        const buffer = Buffer.from(data);
        fs.writeFileSync(dbPath || path.resolve(config.storage.dbPath), buffer);
    } catch (err) {
        console.error('[DB] Save error:', err.message);
    }
}

// ---------------------------------------------------------------------------
// Access helpers
// ---------------------------------------------------------------------------

function getDb() {
    if (!db) throw new Error('Database not initialized');
    return db;
}

function resolveMapId(req, server) {
    return req.query.map_id || req.body?.map_id || 'default';
}

function logActivity(server, mapId, action, x, y, details) {
    if (!db) return;
    db.run(
        'INSERT INTO tile_activity_log (server, map_id, action, x, y, details) VALUES (?, ?, ?, ?, ?, ?)',
        [server, mapId, action, x, y, details || null]
    );
}

module.exports = {
    initDatabase,
    getDb,
    resolveMapId,
    logActivity,
    saveDatabase
};
