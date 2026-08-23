// =============================================================================
// Moonflower Client — Map Renderer
// =============================================================================
// Canvas-based tile map with pan, zoom, and grid overlay.
// Consumes HavenCartographer's REST API for tile data.
// by ReleMai
// =============================================================================

class ClientMapRenderer {
    constructor(canvas, serverUrl) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.serverUrl = serverUrl.replace(/\/$/, '');
        this.tiles = [];
        this.imageCache = {};
        this.loadingCache = {};
        this.showGrid = false;
        this.tileSize = 100;

        this.camera = { x: 0, y: 0, zoom: 1 };
        this._drag = { active: false, startX: 0, startY: 0, camStartX: 0, camStartY: 0 };

        this.resize();
        this._bindEvents();
        this._animate();
    }

    resize() {
        const rect = this.canvas.parentElement.getBoundingClientRect();
        const dpr = window.devicePixelRatio || 1;
        this.canvas.width = rect.width * dpr;
        this.canvas.height = rect.height * dpr;
        this.canvas.style.width = rect.width + 'px';
        this.canvas.style.height = rect.height + 'px';
        this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        this.canvasW = rect.width;
        this.canvasH = rect.height;
    }

    _bindEvents() {
        // Pan
        this.canvas.addEventListener('mousedown', (e) => {
            if (e.button !== 0) return;
            this._drag.active = true;
            this._drag.startX = e.clientX;
            this._drag.startY = e.clientY;
            this._drag.camStartX = this.camera.x;
            this._drag.camStartY = this.camera.y;
            this.canvas.style.cursor = 'grabbing';
        });
        window.addEventListener('mousemove', (e) => {
            if (!this._drag.active) return;
            const dx = e.clientX - this._drag.startX;
            const dy = e.clientY - this._drag.startY;
            this.camera.x = this._drag.camStartX + dx / this.camera.zoom;
            this.camera.y = this._drag.camStartY + dy / this.camera.zoom;
        });
        window.addEventListener('mouseup', () => {
            this._drag.active = false;
            this.canvas.style.cursor = 'grab';
        });

        // Zoom
        this.canvas.addEventListener('wheel', (e) => {
            e.preventDefault();
            const factor = e.deltaY < 0 ? 1.15 : 0.87;
            this.zoomBy(factor, e.clientX, e.clientY);
        }, { passive: false });
    }

    zoomBy(factor, cx, cy) {
        const oldZoom = this.camera.zoom;
        this.camera.zoom = Math.max(0.05, Math.min(20, this.camera.zoom * factor));
        if (cx !== undefined && cy !== undefined) {
            const rect = this.canvas.getBoundingClientRect();
            const mx = cx - rect.left;
            const my = cy - rect.top;
            const dx = mx - this.canvasW / 2;
            const dy = my - this.canvasH / 2;
            this.camera.x += dx * (1 / oldZoom - 1 / this.camera.zoom);
            this.camera.y += dy * (1 / oldZoom - 1 / this.camera.zoom);
        }
    }

    screenToWorld(screenX, screenY) {
        const rect = this.canvas.getBoundingClientRect();
        const cx = screenX - rect.left;
        const cy = screenY - rect.top;
        return {
            x: (cx - this.canvasW / 2) / this.camera.zoom - this.camera.x,
            y: (cy - this.canvasH / 2) / this.camera.zoom - this.camera.y
        };
    }

    async loadTiles(server) {
        try {
            const res = await fetch(`${this.serverUrl}/api/tiles?server=${encodeURIComponent(server)}`);
            const data = await res.json();
            this.tiles = data.tiles || [];
        } catch {
            this.tiles = [];
        }
    }

    fitToView() {
        if (this.tiles.length === 0) return;
        const xs = this.tiles.map(t => t.x);
        const ys = this.tiles.map(t => t.y);
        const minX = Math.min(...xs), maxX = Math.max(...xs);
        const minY = Math.min(...ys), maxY = Math.max(...ys);
        const worldW = (maxX - minX + 1) * this.tileSize;
        const worldH = (maxY - minY + 1) * this.tileSize;
        const cx = (minX + maxX + 1) / 2 * this.tileSize;
        const cy = (minY + maxY + 1) / 2 * this.tileSize;
        const zx = (this.canvasW - 40) / worldW;
        const zy = (this.canvasH - 40) / worldH;
        this.camera.zoom = Math.max(0.05, Math.min(10, Math.min(zx, zy)));
        this.camera.x = -cx;
        this.camera.y = -cy;
    }

    _getTileImage(tile) {
        const key = `${tile.x}_${tile.y}`;
        if (this.imageCache[key]) return this.imageCache[key];
        if (this.loadingCache[key]) return null;

        this.loadingCache[key] = true;
        const img = new Image();
        img.crossOrigin = 'anonymous';
        img.onload = () => {
            this.imageCache[key] = img;
            delete this.loadingCache[key];
        };
        img.onerror = () => { delete this.loadingCache[key]; };
        const server = tile.server || 'game.havenandhearth.com';
        img.src = `${this.serverUrl}/api/tiles/image/${tile.x}/${tile.y}?server=${encodeURIComponent(server)}`;
        return null;
    }

    _animate() {
        this._draw();
        requestAnimationFrame(() => this._animate());
    }

    _draw() {
        const ctx = this.ctx;
        const w = this.canvasW;
        const h = this.canvasH;
        const z = this.camera.zoom;
        const camX = this.camera.x;
        const camY = this.camera.y;

        // Background
        ctx.fillStyle = '#080b0a';
        ctx.fillRect(0, 0, w, h);

        ctx.save();
        ctx.translate(w / 2, h / 2);
        ctx.scale(z, z);
        ctx.translate(camX, camY);

        // Draw tiles
        const ts = this.tileSize;
        for (const tile of this.tiles) {
            const px = tile.x * ts;
            const py = tile.y * ts;

            // Culling
            const sx = (px + camX) * z + w / 2;
            const sy = (py + camY) * z + h / 2;
            const ss = ts * z;
            if (sx + ss < -50 || sy + ss < -50 || sx > w + 50 || sy > h + 50) continue;

            const img = this._getTileImage(tile);
            if (img) {
                ctx.drawImage(img, px, py, ts, ts);
            } else {
                // Placeholder
                ctx.fillStyle = '#111814';
                ctx.fillRect(px, py, ts, ts);
                ctx.fillStyle = '#1f2822';
                ctx.fillRect(px + 2, py + 2, ts - 4, ts - 4);
            }
        }

        // Grid overlay
        if (this.showGrid && z > 0.3) {
            ctx.strokeStyle = 'rgba(201,168,76,0.08)';
            ctx.lineWidth = 0.5 / z;
            for (const tile of this.tiles) {
                ctx.strokeRect(tile.x * ts, tile.y * ts, ts, ts);
            }
        }

        ctx.restore();

        // Vignette
        const grad = ctx.createRadialGradient(w / 2, h / 2, w * 0.35, w / 2, h / 2, w * 0.75);
        grad.addColorStop(0, 'transparent');
        grad.addColorStop(1, 'rgba(8,11,10,0.45)');
        ctx.fillStyle = grad;
        ctx.fillRect(0, 0, w, h);

        // "No tiles" message
        if (this.tiles.length === 0) {
            ctx.fillStyle = '#c9a84c88';
            ctx.font = '13px Inter, system-ui, sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('No tiles collected yet', w / 2, h / 2);
            ctx.textAlign = 'start';
        }
    }
}
