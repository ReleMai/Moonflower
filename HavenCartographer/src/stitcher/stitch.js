// =============================================================================
// Tile Stitcher - Composites tiles into a single map image
// =============================================================================

const sharp = require('sharp');
const path = require('path');
const fs = require('fs');
const { getDb } = require('../database/db');
const { broadcastStitchComplete } = require('../server/websocket');
const config = require('../shared/config');

async function stitchMap(server, mapId = 'default') {
    const db = getDb();
    const tileSize = config.stitcher.tileSize;

    console.log(`[Stitcher] Starting stitch for ${server} (map: ${mapId})`);

    // Get all tiles
    const result = db.exec(
        'SELECT x, y, file_path FROM tiles WHERE server = ? AND map_id = ? ORDER BY x, y',
        [server, mapId]
    );

    if (!result[0] || result[0].values.length === 0) {
        console.log('[Stitcher] No tiles to stitch');
        return null;
    }

    const tiles = result[0].values.map(row => ({
        x: row[0],
        y: row[1],
        file_path: row[2]
    }));

    // Calculate bounds
    const minX = Math.min(...tiles.map(t => t.x));
    const maxX = Math.max(...tiles.map(t => t.x));
    const minY = Math.min(...tiles.map(t => t.y));
    const maxY = Math.max(...tiles.map(t => t.y));

    const width = (maxX - minX + 1) * tileSize;
    const height = (maxY - minY + 1) * tileSize;

    // Cap canvas size
    if (width > config.stitcher.maxCanvasSize || height > config.stitcher.maxCanvasSize) {
        console.warn(`[Stitcher] Canvas too large (${width}x${height}), skipping`);
        return { error: 'Canvas exceeds maximum size' };
    }

    // Record stitch start
    db.run(
        'INSERT INTO stitches (server, map_id, tile_count, status) VALUES (?, ?, ?, ?)',
        [server, mapId, tiles.length, 'running']
    );
    const stitchResult = db.exec('SELECT last_insert_rowid()');
    const stitchId = stitchResult[0]?.values[0]?.[0];

    try {
        // Build composite layers
        const composites = [];
        for (const tile of tiles) {
            const resolved = path.isAbsolute(tile.file_path) ? tile.file_path : path.resolve(tile.file_path);
            if (!fs.existsSync(resolved)) continue;

            composites.push({
                input: resolved,
                left: (tile.x - minX) * tileSize,
                top: (tile.y - minY) * tileSize
            });
        }

        // Create stitched image
        const outputDir = path.resolve(config.storage.mapsDir, server);
        if (!fs.existsSync(outputDir)) {
            fs.mkdirSync(outputDir, { recursive: true });
        }

        const outputPath = path.join(outputDir, `map_${mapId}_${Date.now()}.png`);

        await sharp({
            create: {
                width,
                height,
                channels: 4,
                background: { r: 0, g: 0, b: 0, alpha: 0 }
            }
        })
        .composite(composites)
        .png({ quality: config.stitcher.outputQuality })
        .toFile(outputPath);

        // Update stitch record
        db.run(
            'UPDATE stitches SET output_path = ?, completed_at = datetime(\'now\'), status = ? WHERE id = ?',
            [outputPath, 'complete', stitchId]
        );

        const stitchData = {
            id: stitchId,
            server,
            map_id: mapId,
            tile_count: tiles.length,
            output_path: outputPath,
            dimensions: { width, height },
            bounds: { minX, maxX, minY, maxY }
        };

        broadcastStitchComplete(stitchData);
        console.log(`[Stitcher] Complete: ${tiles.length} tiles → ${outputPath}`);

        return stitchData;
    } catch (err) {
        db.run(
            'UPDATE stitches SET status = ?, completed_at = datetime(\'now\') WHERE id = ?',
            ['error', stitchId]
        );
        console.error('[Stitcher] Error:', err.message);
        throw err;
    }
}

module.exports = { stitchMap };
