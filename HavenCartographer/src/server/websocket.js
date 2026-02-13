// =============================================================================
// WebSocket Handler - Real-time map updates
// =============================================================================
// Broadcasts tile additions, marker changes, and region updates to all
// connected map viewer clients in real-time.
// =============================================================================

const clients = new Set();

function setupWebSocket(wss) {
    wss.on('connection', (ws, req) => {
        console.log(`[WS] Client connected (${wss.clients.size} total)`);
        clients.add(ws);

        // Send welcome message
        ws.send(JSON.stringify({
            type: 'connected',
            message: 'Connected to Haven Cartographer',
            timestamp: new Date().toISOString()
        }));

        ws.on('message', (data) => {
            try {
                const msg = JSON.parse(data);
                handleMessage(ws, msg);
            } catch (err) {
                ws.send(JSON.stringify({ type: 'error', message: 'Invalid JSON' }));
            }
        });

        ws.on('close', () => {
            clients.delete(ws);
            console.log(`[WS] Client disconnected (${wss.clients.size} remaining)`);
        });

        ws.on('error', (err) => {
            console.error('[WS] Client error:', err.message);
            clients.delete(ws);
        });
    });

    console.log('[WS] WebSocket server initialized');
}

function handleMessage(ws, msg) {
    switch (msg.type) {
        case 'ping':
            ws.send(JSON.stringify({ type: 'pong', timestamp: new Date().toISOString() }));
            break;

        case 'subscribe':
            ws.subscribedServer = msg.server || null;
            ws.send(JSON.stringify({ type: 'subscribed', server: msg.server }));
            break;

        default:
            ws.send(JSON.stringify({ type: 'unknown', original: msg.type }));
    }
}

// Broadcast a message to all connected clients
function broadcast(type, data, server = null) {
    const message = JSON.stringify({
        type,
        data,
        server,
        timestamp: new Date().toISOString()
    });

    for (const client of clients) {
        if (client.readyState === 1) { // WebSocket.OPEN
            if (!client.subscribedServer || !server || client.subscribedServer === server) {
                client.send(message);
            }
        }
    }
}

// Specific broadcast helpers
function broadcastTileUpdate(tile) {
    broadcast('tile:update', tile, tile.server);
}

function broadcastMarkerUpdate(marker, action = 'update') {
    broadcast(`marker:${action}`, marker, marker.server);
}

function broadcastRegionUpdate(region, action = 'update') {
    broadcast(`region:${action}`, region, region.server);
}

function broadcastStitchComplete(stitch) {
    broadcast('stitch:complete', stitch, stitch.server);
}

function broadcastLiveTileUpdate(tile) {
    broadcast('tile:live', tile, tile.server);
}

function broadcastTileActivity(activity) {
    broadcast('tile:activity', activity, activity.server);
}

function broadcastGameIconUpdate(icon, action = 'update') {
    broadcast(`game-icon:${action}`, icon, icon.server);
}

function broadcastSettlementUpdate(settlement, action = 'update') {
    broadcast(`settlement:${action}`, settlement, settlement.server);
}

function broadcastNoteUpdate(note, action = 'update') {
    broadcast(`note:${action}`, note, note.server);
}

function broadcastLayerChange(data) {
    broadcast('layer:change', data, data.server);
}

function broadcastBotPosition(bot) {
    broadcast('bot:position', bot, bot.server);
}

function broadcastBotStatus(bot) {
    broadcast('bot:status', bot, bot.server);
}

function broadcastBotNearby(data) {
    broadcast('bot:nearby', data, data.server);
}

function broadcastBotCommand(cmd) {
    broadcast('bot:command', cmd, cmd.server);
}

module.exports = {
    setupWebSocket,
    broadcast,
    broadcastTileUpdate,
    broadcastMarkerUpdate,
    broadcastRegionUpdate,
    broadcastStitchComplete,
    broadcastLiveTileUpdate,
    broadcastTileActivity,
    broadcastGameIconUpdate,
    broadcastSettlementUpdate,
    broadcastNoteUpdate,
    broadcastLayerChange,
    broadcastBotPosition,
    broadcastBotStatus,
    broadcastBotNearby,
    broadcastBotCommand
};
