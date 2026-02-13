// =============================================================================
// Map Renderer — Canvas-based tile map with pan/zoom, layers, game icons,
// settlements, notes, markers, regions, minimap, screenshots
// =============================================================================

class MapRenderer {
    static imageCache = new Map();

    /** Game icon category definitions — mirrors server game-icons.js */
    static GAME_ICON_CATEGORIES = {
        tree:      { icon: '🌳', color: '#2d6a2e' },
        bush:      { icon: '🌿', color: '#4a8a3a' },
        herb:      { icon: '🌱', color: '#3a8a5c' },
        mushroom:  { icon: '🍄', color: '#9e6e9e' },
        rock:      { icon: '🪨', color: '#7a7a7a' },
        ore:       { icon: '⛏',  color: '#b58a4a' },
        animal:    { icon: '🐾', color: '#b58a4a' },
        water:     { icon: '💧', color: '#4a7fb5' },
        building:  { icon: '🏠', color: '#8a7b6b' },
        sign:      { icon: '🪧', color: '#c9a84c' },
        gate:      { icon: '🚪', color: '#6b5b4b' },
        vehicle:   { icon: '🛶', color: '#6b8a6b' },
        container: { icon: '📦', color: '#8a7b5b' },
        food:      { icon: '🍖', color: '#c9644c' },
        curiosity: { icon: '✨', color: '#c9a84c' },
        bone:      { icon: '🦴', color: '#d4c5a0' },
        clay:      { icon: '🏺', color: '#b58a5a' },
        mine:      { icon: '⛰',  color: '#6a5a4a' },
        dungeon:   { icon: '🕳',  color: '#4a3a3a' },
        player:    { icon: '👤', color: '#4a7fb5' },
        object:    { icon: '📌', color: '#b8c9d4' }
    };

    constructor(canvas, minimapCanvas = null) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.tiles = [];
        this.markers = [];
        this.regions = [];
        this.gameIcons = [];
        this.settlements = [];
        this.notes = [];
        this.bots = [];
        this.selectedBotId = null;
        this.tileSize = MoonflowerConfig.tileSize;

        // Layer system: 0 = surface, -1 = cave level 1, -2 = cave level 2, etc.
        this.currentLayer = 0;
        this.availableLayers = [0];

        // Camera state
        this.camera = { x: 0, y: 0, zoom: 1 };
        this.isDragging = false;
        this.dragStart = { x: 0, y: 0 };
        this.cameraStart = { x: 0, y: 0 };

        // Active tool — external code sets this to 'pan'|'marker'|'note'|'region'
        this.activeTool = 'pan';

        // Display options
        this.showGrid = true;
        this.showMarkers = true;
        this.showRegions = true;
        this.showGameIcons = true;
        this.showSettlements = true;
        this.showNotes = true;
        this.showBots = true;

        // Callbacks
        this.onMapClick = null;     // (worldX, worldY, event) => {}
        this.onCameraChange = null; // () => {}
        this.onLayerChange = null;  // (layer) => {}

        // Minimap
        this.minimapCanvas = minimapCanvas;
        this.minimapCtx = minimapCanvas ? minimapCanvas.getContext('2d') : null;

        // Render loop
        this.renderQueued = false;

        this.setupEvents();
        this.resize();
    }

    // ── Events ──────────────────────────────────────────────────────────

    setupEvents() {
        this.canvas.addEventListener('mousedown', (e) => {
            if (e.button !== 0) return;
            this.isDragging = true;
            this.dragStart = { x: e.clientX, y: e.clientY };
            this.cameraStart = { x: this.camera.x, y: this.camera.y };
        });

        window.addEventListener('mousemove', (e) => {
            // Coordinate display
            const world = this.screenToWorld(e.clientX, e.clientY);
            const coordEl = document.getElementById('mapCoords');
            if (coordEl) {
                coordEl.textContent = `${Math.floor(world.x)}, ${Math.floor(world.y)}`;
            }

            if (!this.isDragging) return;
            const dx = e.clientX - this.dragStart.x;
            const dy = e.clientY - this.dragStart.y;
            this.camera.x = this.cameraStart.x + dx;
            this.camera.y = this.cameraStart.y + dy;
            this.queueRender();
        });

        window.addEventListener('mouseup', (e) => {
            if (!this.isDragging) return;
            const dx = Math.abs(e.clientX - this.dragStart.x);
            const dy = Math.abs(e.clientY - this.dragStart.y);
            const wasClick = dx < 4 && dy < 4;
            this.isDragging = false;

            // Fire map click if barely moved and not pan tool
            if (wasClick && this.activeTool !== 'pan' && this.onMapClick) {
                const world = this.screenToWorld(e.clientX, e.clientY);
                this.onMapClick(world.x, world.y, e);
            }
            if (this.onCameraChange) this.onCameraChange();
        });

        // Zoom (wheel)
        this.canvas.addEventListener('wheel', (e) => {
            e.preventDefault();
            const zoomFactor = e.deltaY > 0 ? 0.9 : 1.1;
            const newZoom = Math.max(
                MoonflowerConfig.map.minZoom,
                Math.min(MoonflowerConfig.map.maxZoom, this.camera.zoom * zoomFactor)
            );

            const rect = this.canvas.getBoundingClientRect();
            const mx = e.clientX - rect.left;
            const my = e.clientY - rect.top;

            const scale = newZoom / this.camera.zoom;
            this.camera.x = mx - (mx - this.camera.x) * scale;
            this.camera.y = my - (my - this.camera.y) * scale;
            this.camera.zoom = newZoom;

            // Update zoom display
            const zoomEl = document.getElementById('zoomLevel');
            if (zoomEl) zoomEl.textContent = `${this.camera.zoom.toFixed(1)}×`;

            this.queueRender();
            if (this.onCameraChange) this.onCameraChange();
        });

        window.addEventListener('resize', () => this.resize());
    }

    resize() {
        const parent = this.canvas.parentElement;
        if (!parent) return;
        this.canvas.width = parent.clientWidth || window.innerWidth;
        this.canvas.height = parent.clientHeight || 400;
        this.queueRender();
    }

    // ── Layer Management ────────────────────────────────────────────────

    setLayer(layer) {
        if (this.currentLayer === layer) return;
        this.currentLayer = layer;

        // Update layer display
        const layerEl = document.getElementById('currentLayerDisplay');
        if (layerEl) layerEl.textContent = this.getLayerName(layer);

        // Clear image cache when switching layers (tiles differ by layer)
        MapRenderer.imageCache.clear();

        if (this.onLayerChange) this.onLayerChange(layer);
        this.queueRender();
    }

    getLayerName(layer) {
        if (layer === 0) return 'Surface';
        if (layer < 0) return `Cave Level ${Math.abs(layer)}`;
        return `Layer ${layer}`;
    }

    async loadAvailableLayers(server) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/stats?server=${encodeURIComponent(server)}`);
            const data = await res.json();
            if (data.layers && data.layers.length > 0) {
                this.availableLayers = data.layers.sort((a, b) => b - a); // surface first
            } else {
                this.availableLayers = [0];
            }
        } catch (err) {
            console.warn('[MapRenderer] Failed to load layers:', err);
        }
    }

    // ── Coordinate Transforms ───────────────────────────────────────────

    screenToWorld(sx, sy) {
        const rect = this.canvas.getBoundingClientRect();
        return {
            x: (sx - rect.left - this.camera.x) / (this.camera.zoom * this.tileSize),
            y: (sy - rect.top - this.camera.y) / (this.camera.zoom * this.tileSize)
        };
    }

    worldToScreen(wx, wy) {
        return {
            x: wx * this.tileSize * this.camera.zoom + this.camera.x,
            y: wy * this.tileSize * this.camera.zoom + this.camera.y
        };
    }

    // ── Data Loading (all layer-aware) ──────────────────────────────────

    async loadTiles(server) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/tiles?server=${encodeURIComponent(server)}&layer=${this.currentLayer}`);
            const data = await res.json();
            this.tiles = data.tiles || [];
            this.loadTileImages(server);
            this.queueRender();
        } catch (err) {
            console.error('[MapRenderer] Failed to load tiles:', err);
        }
    }

    loadTileImages(server) {
        for (const tile of this.tiles) {
            const key = `${this.currentLayer}_${tile.x}_${tile.y}`;
            if (MapRenderer.imageCache.has(key)) continue;

            const img = new Image();
            img.onload = () => {
                MapRenderer.imageCache.set(key, img);
                this.queueRender();
            };
            img.onerror = () => console.warn(`[MapRenderer] Failed to load tile ${key}`);
            img.src = `${MoonflowerConfig.serverUrl}/api/tiles/image/${tile.x}/${tile.y}?server=${encodeURIComponent(server)}&layer=${this.currentLayer}`;
        }
    }

    async loadMarkers(server) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/markers?server=${encodeURIComponent(server)}`);
            const data = await res.json();
            // Filter markers to current layer (markers without layer default to 0)
            this.markers = (data.markers || []).filter(m => (m.layer || 0) === this.currentLayer);
            this.queueRender();
        } catch (err) {
            console.error('[MapRenderer] Failed to load markers:', err);
        }
    }

    async loadGameIcons(server) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/game-icons?server=${encodeURIComponent(server)}&layer=${this.currentLayer}`);
            const data = await res.json();
            this.gameIcons = data.icons || [];
            this.queueRender();
        } catch (err) {
            console.error('[MapRenderer] Failed to load game icons:', err);
        }
    }

    async loadSettlements(server) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/settlements?server=${encodeURIComponent(server)}&layer=${this.currentLayer}`);
            const data = await res.json();
            this.settlements = data.settlements || [];
            this.queueRender();
        } catch (err) {
            console.error('[MapRenderer] Failed to load settlements:', err);
        }
    }

    async loadNotes(server) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/notes?server=${encodeURIComponent(server)}&layer=${this.currentLayer}`);
            const data = await res.json();
            this.notes = data.notes || [];
            this.queueRender();
        } catch (err) {
            console.error('[MapRenderer] Failed to load notes:', err);
        }
    }

    async loadRegions(server) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/regions?server=${encodeURIComponent(server)}`);
            const data = await res.json();
            this.regions = (data.regions || []).filter(r => (r.layer || 0) === this.currentLayer);
            // Parse points JSON if needed
            for (const r of this.regions) {
                if (typeof r.points === 'string') {
                    try { r.points = JSON.parse(r.points); } catch { r.points = []; }
                }
            }
            this.queueRender();
        } catch (err) {
            console.error('[MapRenderer] Failed to load regions:', err);
        }
    }

    /** Reload all data for the current layer */
    async reloadAll(server) {
        await Promise.all([
            this.loadTiles(server),
            this.loadMarkers(server),
            this.loadGameIcons(server),
            this.loadSettlements(server),
            this.loadNotes(server),
            this.loadRegions(server),
            this.loadAvailableLayers(server),
            this.loadBots(server)
        ]);
    }

    // ── Camera ──────────────────────────────────────────────────────────

    fitToView() {
        if (this.tiles.length === 0) return;

        const minX = Math.min(...this.tiles.map(t => t.x));
        const maxX = Math.max(...this.tiles.map(t => t.x));
        const minY = Math.min(...this.tiles.map(t => t.y));
        const maxY = Math.max(...this.tiles.map(t => t.y));

        const worldW = (maxX - minX + 1) * this.tileSize;
        const worldH = (maxY - minY + 1) * this.tileSize;

        const zoomX = this.canvas.width / worldW;
        const zoomY = this.canvas.height / worldH;
        this.camera.zoom = Math.min(zoomX, zoomY) * 0.9;

        const centerX = (minX + maxX + 1) / 2 * this.tileSize;
        const centerY = (minY + maxY + 1) / 2 * this.tileSize;

        this.camera.x = this.canvas.width / 2 - centerX * this.camera.zoom;
        this.camera.y = this.canvas.height / 2 - centerY * this.camera.zoom;

        const zoomEl = document.getElementById('zoomLevel');
        if (zoomEl) zoomEl.textContent = `${this.camera.zoom.toFixed(1)}×`;

        this.queueRender();
        if (this.onCameraChange) this.onCameraChange();
    }

    zoomIn() {
        const newZoom = Math.min(MoonflowerConfig.map.maxZoom, this.camera.zoom * 1.25);
        this.zoomTo(newZoom);
    }

    zoomOut() {
        const newZoom = Math.max(MoonflowerConfig.map.minZoom, this.camera.zoom * 0.8);
        this.zoomTo(newZoom);
    }

    zoomTo(newZoom) {
        const cx = this.canvas.width / 2;
        const cy = this.canvas.height / 2;
        const scale = newZoom / this.camera.zoom;
        this.camera.x = cx - (cx - this.camera.x) * scale;
        this.camera.y = cy - (cy - this.camera.y) * scale;
        this.camera.zoom = newZoom;

        const zoomEl = document.getElementById('zoomLevel');
        if (zoomEl) zoomEl.textContent = `${this.camera.zoom.toFixed(1)}×`;

        this.queueRender();
        if (this.onCameraChange) this.onCameraChange();
    }

    panTo(worldX, worldY) {
        this.camera.x = this.canvas.width / 2 - worldX * this.tileSize * this.camera.zoom;
        this.camera.y = this.canvas.height / 2 - worldY * this.tileSize * this.camera.zoom;
        this.queueRender();
        if (this.onCameraChange) this.onCameraChange();
    }

    // ── Tile Bounds helper ──────────────────────────────────────────────

    getTileBounds() {
        if (this.tiles.length === 0) return null;
        return {
            minX: Math.min(...this.tiles.map(t => t.x)),
            maxX: Math.max(...this.tiles.map(t => t.x)),
            minY: Math.min(...this.tiles.map(t => t.y)),
            maxY: Math.max(...this.tiles.map(t => t.y))
        };
    }

    // ── Rendering ───────────────────────────────────────────────────────

    queueRender() {
        if (this.renderQueued) return;
        this.renderQueued = true;
        requestAnimationFrame(() => {
            this.render();
            this.renderQueued = false;
        });
    }

    render() {
        const ctx = this.ctx;
        const w = this.canvas.width;
        const h = this.canvas.height;

        // Clear
        ctx.fillStyle = '#050807';
        ctx.fillRect(0, 0, w, h);

        ctx.save();
        ctx.translate(this.camera.x, this.camera.y);
        ctx.scale(this.camera.zoom, this.camera.zoom);

        if (this.showGrid) this.drawGrid(ctx);
        this.drawTiles(ctx);
        if (this.showRegions) this.drawRegions(ctx);
        if (this.showGameIcons) this.drawGameIcons(ctx);
        if (this.showSettlements) this.drawSettlements(ctx);
        if (this.showMarkers) this.drawMarkers(ctx);
        if (this.showNotes) this.drawNotes(ctx);
        if (this.showBots) this.drawBots(ctx);
        this.drawOrigin(ctx);

        ctx.restore();

        // Minimap
        this.renderMinimap();
    }

    drawTiles(ctx) {
        for (const tile of this.tiles) {
            const key = `${this.currentLayer}_${tile.x}_${tile.y}`;
            const img = MapRenderer.imageCache.get(key);
            const px = tile.x * this.tileSize;
            const py = tile.y * this.tileSize;

            if (img) {
                ctx.drawImage(img, px, py, this.tileSize, this.tileSize);
            } else {
                ctx.fillStyle = 'rgba(30, 42, 38, 0.4)';
                ctx.fillRect(px, py, this.tileSize, this.tileSize);
            }
        }
    }

    drawGrid(ctx) {
        if (this.tiles.length === 0) return;

        const bounds = this.getTileBounds();
        const minX = bounds.minX - 1;
        const maxX = bounds.maxX + 2;
        const minY = bounds.minY - 1;
        const maxY = bounds.maxY + 2;

        ctx.strokeStyle = MoonflowerConfig.map.gridColor;
        ctx.lineWidth = 0.5 / this.camera.zoom;

        for (let x = minX; x <= maxX; x++) {
            ctx.beginPath();
            ctx.moveTo(x * this.tileSize, minY * this.tileSize);
            ctx.lineTo(x * this.tileSize, maxY * this.tileSize);
            ctx.stroke();
        }
        for (let y = minY; y <= maxY; y++) {
            ctx.beginPath();
            ctx.moveTo(minX * this.tileSize, y * this.tileSize);
            ctx.lineTo(maxX * this.tileSize, y * this.tileSize);
            ctx.stroke();
        }
    }

    drawMarkers(ctx) {
        const invZoom = 1 / this.camera.zoom;
        for (const marker of this.markers) {
            const cx = marker.x * this.tileSize + this.tileSize / 2;
            const cy = marker.y * this.tileSize + this.tileSize / 2;

            // Pin shape
            const r = Math.max(4, 6 * invZoom);
            ctx.save();

            // Drop shadow
            ctx.shadowColor = 'rgba(0,0,0,0.4)';
            ctx.shadowBlur = 3 * invZoom;
            ctx.shadowOffsetY = 1 * invZoom;

            // Pin body
            ctx.beginPath();
            ctx.arc(cx, cy - r * 0.5, r, 0, Math.PI * 2);
            ctx.fillStyle = marker.color || '#c9a84c';
            ctx.fill();
            ctx.strokeStyle = 'rgba(0,0,0,0.3)';
            ctx.lineWidth = 0.5 * invZoom;
            ctx.stroke();

            ctx.shadowColor = 'transparent';

            // Pin dot
            ctx.beginPath();
            ctx.arc(cx, cy - r * 0.5, r * 0.35, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(0,0,0,0.25)';
            ctx.fill();

            ctx.restore();

            // Name label at higher zoom
            if (this.camera.zoom > 0.4) {
                const fontSize = Math.max(8, 10 * invZoom);
                ctx.font = `500 ${fontSize}px Inter, sans-serif`;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'top';
                ctx.fillStyle = 'rgba(0,0,0,0.5)';
                ctx.fillText(marker.name, cx + 0.5 * invZoom, cy + r + 1 * invZoom + 0.5 * invZoom);
                ctx.fillStyle = '#d4c5a0';
                ctx.fillText(marker.name, cx, cy + r + 1 * invZoom);
            }
        }
    }

    drawGameIcons(ctx) {
        const invZoom = 1 / this.camera.zoom;
        for (const gi of this.gameIcons) {
            const cx = gi.x * this.tileSize + this.tileSize / 2;
            const cy = gi.y * this.tileSize + this.tileSize / 2;
            const cat = MapRenderer.GAME_ICON_CATEGORIES[gi.icon_type] || MapRenderer.GAME_ICON_CATEGORIES.object;
            const icon = cat.icon;
            const color = cat.color;

            // Small circular background
            const r = Math.max(4, 5 * invZoom);
            ctx.save();

            ctx.shadowColor = 'rgba(0,0,0,0.25)';
            ctx.shadowBlur = 2 * invZoom;

            // Filled circle
            ctx.beginPath();
            ctx.arc(cx, cy, r, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.globalAlpha = 0.75;
            ctx.fill();
            ctx.globalAlpha = 1;
            ctx.strokeStyle = 'rgba(255,255,255,0.2)';
            ctx.lineWidth = 0.4 * invZoom;
            ctx.stroke();

            ctx.shadowColor = 'transparent';

            // Emoji icon
            const emojiSize = Math.max(6, 8 * invZoom);
            ctx.font = `${emojiSize}px sans-serif`;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(icon, cx, cy);

            ctx.restore();

            // Label at higher zoom
            if (this.camera.zoom > 0.8 && gi.label) {
                const fontSize = Math.max(6, 8 * invZoom);
                ctx.font = `${fontSize}px Inter, sans-serif`;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'top';
                ctx.fillStyle = 'rgba(0,0,0,0.5)';
                ctx.fillText(gi.label, cx + 0.3 * invZoom, cy + r + 1 * invZoom + 0.3 * invZoom);
                ctx.fillStyle = color;
                ctx.fillText(gi.label, cx, cy + r + 1 * invZoom);
            }
        }
    }

    drawSettlements(ctx) {
        const invZoom = 1 / this.camera.zoom;
        for (const s of this.settlements) {
            const cx = s.x * this.tileSize;
            const cy = s.y * this.tileSize;
            const isVillage = s.type === 'village';
            const color = isVillage ? '#c9a84c' : '#d4763a';

            // Draw radius circle if the settlement has a radius
            if (s.radius && s.radius > 0) {
                const radiusPx = s.radius * this.tileSize;
                ctx.save();
                ctx.beginPath();
                ctx.arc(cx, cy, radiusPx, 0, Math.PI * 2);
                ctx.fillStyle = color;
                ctx.globalAlpha = 0.08;
                ctx.fill();
                ctx.globalAlpha = 0.4;
                ctx.strokeStyle = color;
                ctx.lineWidth = 1 * invZoom;
                ctx.setLineDash([4 * invZoom, 4 * invZoom]);
                ctx.stroke();
                ctx.setLineDash([]);
                ctx.globalAlpha = 1;
                ctx.restore();
            }

            // Settlement name label
            const displayName = s.name || s.owner || '???';
            const baseFontSize = isVillage ? 16 : 12;
            const fontSize = baseFontSize * invZoom;

            ctx.save();
            ctx.font = `600 ${fontSize}px Cinzel, Georgia, serif`;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';

            // Text outline
            ctx.strokeStyle = 'rgba(0,0,0,0.7)';
            ctx.lineWidth = Math.max(1, 2.5 * invZoom);
            ctx.lineJoin = 'round';
            ctx.strokeText(displayName, cx, cy);

            // Fill
            ctx.fillStyle = color;
            ctx.fillText(displayName, cx, cy);

            // Type subtitle
            if (this.camera.zoom > 0.4) {
                const sub = isVillage ? '⛏ Village' : '🏴 Claim';
                const subSize = Math.max(6, 8 * invZoom);
                ctx.font = `400 ${subSize}px Inter, sans-serif`;
                ctx.strokeStyle = 'rgba(0,0,0,0.6)';
                ctx.lineWidth = Math.max(0.5, 1.5 * invZoom);
                ctx.strokeText(sub, cx, cy + fontSize * 0.8);
                ctx.fillStyle = color;
                ctx.globalAlpha = 0.7;
                ctx.fillText(sub, cx, cy + fontSize * 0.8);
                ctx.globalAlpha = 1;
            }

            ctx.restore();
        }
    }

    drawNotes(ctx) {
        const invZoom = 1 / this.camera.zoom;
        for (const note of this.notes) {
            const cx = note.x * this.tileSize;
            const cy = note.y * this.tileSize;
            const color = note.color || '#e8d5a3';
            const noteIcon = note.icon || '📝';

            // Small note icon
            const r = Math.max(5, 7 * invZoom);
            ctx.save();

            ctx.shadowColor = 'rgba(0,0,0,0.3)';
            ctx.shadowBlur = 2 * invZoom;

            // Background circle
            ctx.beginPath();
            ctx.arc(cx, cy, r, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.globalAlpha = 0.85;
            ctx.fill();
            ctx.globalAlpha = 1;
            ctx.strokeStyle = 'rgba(255,255,255,0.3)';
            ctx.lineWidth = 0.5 * invZoom;
            ctx.stroke();

            ctx.shadowColor = 'transparent';

            // Icon
            const icoSize = Math.max(7, 9 * invZoom);
            ctx.font = `${icoSize}px sans-serif`;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(noteIcon, cx, cy);

            ctx.restore();

            // Title at higher zoom
            if (this.camera.zoom > 0.5 && note.title) {
                const fontSize = Math.max(7, 9 * invZoom);
                ctx.font = `500 ${fontSize}px Inter, sans-serif`;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'top';
                ctx.fillStyle = 'rgba(0,0,0,0.5)';
                ctx.fillText(note.title, cx + 0.4 * invZoom, cy + r + 1 * invZoom + 0.4 * invZoom);
                ctx.fillStyle = color;
                ctx.fillText(note.title, cx, cy + r + 1 * invZoom);
            }

            // Screenshot indicator
            if (note.screenshot && this.camera.zoom > 0.6) {
                const sSize = Math.max(5, 6 * invZoom);
                ctx.font = `${sSize}px sans-serif`;
                ctx.fillText('📷', cx + r * 0.8, cy - r * 0.8);
            }
        }
    }

    drawRegions(ctx) {
        const invZoom = 1 / this.camera.zoom;
        for (const region of this.regions) {
            const points = region.points;
            if (!points || points.length < 2) continue;

            ctx.beginPath();
            ctx.moveTo(points[0].x * this.tileSize, points[0].y * this.tileSize);
            for (let i = 1; i < points.length; i++) {
                ctx.lineTo(points[i].x * this.tileSize, points[i].y * this.tileSize);
            }
            ctx.closePath();

            const alpha = Math.round((region.opacity || 0.3) * 255).toString(16).padStart(2, '0');
            ctx.fillStyle = (region.color || '#c9a84c') + alpha;
            ctx.fill();
            ctx.strokeStyle = region.color || '#c9a84c';
            ctx.lineWidth = 1 * invZoom;
            ctx.stroke();

            // Region label at center
            if (this.camera.zoom > 0.3 && region.name) {
                let avgX = 0, avgY = 0;
                for (const p of points) { avgX += p.x; avgY += p.y; }
                avgX = (avgX / points.length) * this.tileSize;
                avgY = (avgY / points.length) * this.tileSize;

                const fontSize = Math.max(8, 11 * invZoom);
                ctx.font = `600 ${fontSize}px Cinzel, Georgia, serif`;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                ctx.fillStyle = region.color || '#c9a84c';
                ctx.globalAlpha = 0.7;
                ctx.fillText(region.name, avgX, avgY);
                ctx.globalAlpha = 1;
            }
        }
    }

    drawOrigin(ctx) {
        const invZoom = 1 / this.camera.zoom;
        const size = 20;
        ctx.strokeStyle = 'rgba(201, 168, 76, 0.4)';
        ctx.lineWidth = 1 * invZoom;
        ctx.beginPath();
        ctx.moveTo(-size, 0);
        ctx.lineTo(size, 0);
        ctx.moveTo(0, -size);
        ctx.lineTo(0, size);
        ctx.stroke();
    }

    // ── Bot Tracking ────────────────────────────────────────────────────

    async loadBots(server) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/bots?server=${encodeURIComponent(server)}`);
            const data = await res.json();
            this.bots = data.bots || [];
            this.queueRender();
        } catch (err) {
            console.error('[MapRenderer] Failed to load bots:', err);
        }
    }

    /** Update or insert a single bot position (called from WS handler) */
    updateBot(botData) {
        const idx = this.bots.findIndex(b => b.botId === botData.botId);
        if (idx >= 0) {
            // Smooth interpolation data for animation
            const prev = this.bots[idx];
            botData._prevX = prev.tileX + (prev.fracX || 0);
            botData._prevY = prev.tileY + (prev.fracY || 0);
            botData._animStart = performance.now();
            this.bots[idx] = botData;
        } else {
            this.bots.push(botData);
        }
        this.queueRender();
    }

    /** Remove a bot by ID */
    removeBot(botId) {
        this.bots = this.bots.filter(b => b.botId !== botId);
        if (this.selectedBotId === botId) this.selectedBotId = null;
        this.queueRender();
    }

    /** Check if a screen coordinate hits a bot. Returns bot or null. */
    getBotAtScreen(screenX, screenY) {
        const world = this.screenToWorld(screenX, screenY);
        const hitRadius = 0.8; // tiles
        for (const bot of this.bots) {
            if (bot.status === 'offline') continue;
            const bx = bot.tileX + (bot.fracX || 0);
            const by = bot.tileY + (bot.fracY || 0);
            const dist = Math.sqrt(Math.pow(world.x - bx, 2) + Math.pow(world.y - by, 2));
            if (dist < hitRadius) return bot;
        }
        return null;
    }

    drawBots(ctx) {
        const invZoom = 1 / this.camera.zoom;
        const now = performance.now();

        for (const bot of this.bots) {
            if (bot.status === 'offline') continue;

            // Interpolated position for smooth animation
            let bx = bot.tileX + (bot.fracX || 0);
            let by = bot.tileY + (bot.fracY || 0);
            if (bot._prevX !== undefined && bot._animStart) {
                const elapsed = now - bot._animStart;
                const duration = 800; // ms interpolation
                const t = Math.min(1, elapsed / duration);
                const ease = t * (2 - t); // ease-out quadratic
                bx = bot._prevX + (bx - bot._prevX) * ease;
                by = bot._prevY + (by - bot._prevY) * ease;
                if (t < 1) this.queueRender(); // Keep animating
            }

            const cx = bx * this.tileSize + this.tileSize / 2;
            const cy = by * this.tileSize + this.tileSize / 2;

            const isSelected = this.selectedBotId === bot.botId;
            const statusColors = {
                idle: '#4a7fb5',
                moving: '#6b9e6e',
                busy: '#d4763a',
                gathering: '#c9a84c',
                foraging: '#3a8a5c',
                offline: '#555555'
            };
            const color = statusColors[bot.status] || '#4a7fb5';
            const r = Math.max(6, (isSelected ? 10 : 8) * invZoom);

            ctx.save();

            // Selection ring (pulsing)
            if (isSelected) {
                const pulse = 0.7 + 0.3 * Math.sin(now / 300);
                ctx.beginPath();
                ctx.arc(cx, cy, r * 1.6, 0, Math.PI * 2);
                ctx.strokeStyle = color;
                ctx.lineWidth = 1.5 * invZoom;
                ctx.globalAlpha = pulse;
                ctx.stroke();
                ctx.globalAlpha = 1;
            }

            // Direction indicator (movement arrow)
            if (bot.moving && bot._prevX !== undefined) {
                const dx = (bot.tileX + (bot.fracX || 0)) - bot._prevX;
                const dy = (bot.tileY + (bot.fracY || 0)) - bot._prevY;
                const len = Math.sqrt(dx * dx + dy * dy);
                if (len > 0.01) {
                    const angle = Math.atan2(dy, dx);
                    const arrowLen = r * 2.5;
                    ctx.beginPath();
                    ctx.moveTo(cx, cy);
                    ctx.lineTo(cx + Math.cos(angle) * arrowLen, cy + Math.sin(angle) * arrowLen);
                    ctx.strokeStyle = color;
                    ctx.lineWidth = 1.5 * invZoom;
                    ctx.globalAlpha = 0.6;
                    ctx.stroke();
                    ctx.globalAlpha = 1;
                }
            }

            // Bot body — filled circle with border
            ctx.shadowColor = 'rgba(0,0,0,0.5)';
            ctx.shadowBlur = 3 * invZoom;
            ctx.shadowOffsetY = 1 * invZoom;

            ctx.beginPath();
            ctx.arc(cx, cy, r, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.fill();
            ctx.strokeStyle = isSelected ? '#ffffff' : 'rgba(255,255,255,0.4)';
            ctx.lineWidth = (isSelected ? 1.5 : 0.8) * invZoom;
            ctx.stroke();

            ctx.shadowColor = 'transparent';

            // Inner icon (person silhouette)
            const iconSize = r * 0.9;
            ctx.font = `${iconSize}px sans-serif`;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillStyle = 'rgba(255,255,255,0.85)';
            ctx.fillText('👤', cx, cy);

            // Status dot (small circle at bottom-right)
            const dotR = Math.max(2, 3 * invZoom);
            const dotX = cx + r * 0.7;
            const dotY = cy + r * 0.7;
            ctx.beginPath();
            ctx.arc(dotX, dotY, dotR, 0, Math.PI * 2);
            ctx.fillStyle = bot.status === 'idle' ? '#4ade80' :
                           bot.status === 'moving' ? '#facc15' :
                           bot.status === 'busy' ? '#f97316' :
                           bot.status === 'gathering' ? '#c9a84c' :
                           bot.status === 'foraging' ? '#34d399' : '#888';
            ctx.fill();
            ctx.strokeStyle = '#000';
            ctx.lineWidth = 0.5 * invZoom;
            ctx.stroke();

            ctx.restore();

            // Name label
            if (this.camera.zoom > 0.3) {
                const fontSize = Math.max(8, (isSelected ? 11 : 9) * invZoom);
                ctx.font = `${isSelected ? '600' : '500'} ${fontSize}px Inter, sans-serif`;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'top';

                const label = bot.name || bot.botId;
                const statusSuffix = bot.status !== 'idle' ? ` (${bot.status})` : '';

                // Text shadow
                ctx.fillStyle = 'rgba(0,0,0,0.6)';
                ctx.fillText(label + statusSuffix, cx + 0.5 * invZoom, cy + r + 2 * invZoom + 0.5 * invZoom);
                // Text
                ctx.fillStyle = isSelected ? '#ffffff' : '#d4c5a0';
                ctx.fillText(label + statusSuffix, cx, cy + r + 2 * invZoom);
            }
        }
    }

    // ── Minimap ─────────────────────────────────────────────────────────

    renderMinimap() {
        if (!this.minimapCtx || this.tiles.length === 0) return;
        const mctx = this.minimapCtx;
        const mw = this.minimapCanvas.width;
        const mh = this.minimapCanvas.height;

        mctx.fillStyle = '#0a0d0c';
        mctx.fillRect(0, 0, mw, mh);

        const bounds = this.getTileBounds();
        if (!bounds) return;

        const tilesW = bounds.maxX - bounds.minX + 1;
        const tilesH = bounds.maxY - bounds.minY + 1;
        const scale = Math.min((mw - 8) / tilesW, (mh - 8) / tilesH);
        const ox = (mw - tilesW * scale) / 2;
        const oy = (mh - tilesH * scale) / 2;

        // Draw tiles as dots
        mctx.fillStyle = 'rgba(58, 138, 92, 0.6)';
        for (const tile of this.tiles) {
            const tx = (tile.x - bounds.minX) * scale + ox;
            const ty = (tile.y - bounds.minY) * scale + oy;
            mctx.fillRect(tx, ty, Math.max(1, scale - 0.5), Math.max(1, scale - 0.5));
        }

        // Viewport rectangle
        const viewLeft = (-this.camera.x / this.camera.zoom / this.tileSize - bounds.minX) * scale + ox;
        const viewTop = (-this.camera.y / this.camera.zoom / this.tileSize - bounds.minY) * scale + oy;
        const viewW = (this.canvas.width / this.camera.zoom / this.tileSize) * scale;
        const viewH = (this.canvas.height / this.camera.zoom / this.tileSize) * scale;

        mctx.strokeStyle = 'rgba(201, 168, 76, 0.7)';
        mctx.lineWidth = 1;
        mctx.strokeRect(viewLeft, viewTop, viewW, viewH);

        // Layer indicator on minimap
        if (this.currentLayer !== 0) {
            mctx.font = '9px Inter, sans-serif';
            mctx.fillStyle = '#c9a84c';
            mctx.textAlign = 'left';
            mctx.fillText(this.getLayerName(this.currentLayer), 3, mh - 4);
        }
    }

    // ── Screenshot ──────────────────────────────────────────────────────

    captureScreenshot(options = {}) {
        const {
            format = 'png',
            includeMarkers = true,
            includeGameIcons = true,
            includeSettlements = true,
            includeNotes = true,
            includeGrid = false,
            fullMap = false
        } = options;

        // Create offscreen canvas
        let offCanvas, offCtx, cam;

        if (fullMap && this.tiles.length > 0) {
            const bounds = this.getTileBounds();
            const width = (bounds.maxX - bounds.minX + 1) * this.tileSize;
            const height = (bounds.maxY - bounds.minY + 1) * this.tileSize;
            offCanvas = document.createElement('canvas');
            offCanvas.width = width;
            offCanvas.height = height;
            offCtx = offCanvas.getContext('2d');
            cam = {
                x: -bounds.minX * this.tileSize,
                y: -bounds.minY * this.tileSize,
                zoom: 1
            };
        } else {
            offCanvas = document.createElement('canvas');
            offCanvas.width = this.canvas.width;
            offCanvas.height = this.canvas.height;
            offCtx = offCanvas.getContext('2d');
            cam = { ...this.camera };
        }

        // Render to offscreen
        offCtx.fillStyle = '#050807';
        offCtx.fillRect(0, 0, offCanvas.width, offCanvas.height);
        offCtx.save();
        offCtx.translate(cam.x, cam.y);
        offCtx.scale(cam.zoom, cam.zoom);

        // Temporarily swap visibility
        const prev = {
            grid: this.showGrid,
            markers: this.showMarkers,
            gameIcons: this.showGameIcons,
            settlements: this.showSettlements,
            notes: this.showNotes
        };
        this.showGrid = includeGrid;
        this.showMarkers = includeMarkers;
        this.showGameIcons = includeGameIcons;
        this.showSettlements = includeSettlements;
        this.showNotes = includeNotes;

        // Use zoom=1 for full map so invZoom works
        const origZoom = this.camera.zoom;
        this.camera.zoom = cam.zoom;

        if (this.showGrid) this.drawGrid(offCtx);
        this.drawTiles(offCtx);
        if (this.showRegions) this.drawRegions(offCtx);
        if (this.showGameIcons) this.drawGameIcons(offCtx);
        if (this.showSettlements) this.drawSettlements(offCtx);
        if (this.showMarkers) this.drawMarkers(offCtx);
        if (this.showNotes) this.drawNotes(offCtx);

        // Restore
        this.camera.zoom = origZoom;
        this.showGrid = prev.grid;
        this.showMarkers = prev.markers;
        this.showGameIcons = prev.gameIcons;
        this.showSettlements = prev.settlements;
        this.showNotes = prev.notes;

        offCtx.restore();

        const mimeType = format === 'jpeg' ? 'image/jpeg'
            : format === 'webp' ? 'image/webp'
            : 'image/png';
        return offCanvas.toDataURL(mimeType, 0.92);
    }
}
