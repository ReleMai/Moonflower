// =============================================================================
// Moonflower Client — Renderer App Logic
// =============================================================================
// View management, settings, game control, stats, cartographer connection,
// dashboard panels, game tips, and automation controls.
// by ReleMai
// =============================================================================

(function () {
    'use strict';

    // ── State ──
    let dashMapRenderer = null;
    let fullMapRenderer = null;
    let cartoWs = null;
    let cartoConnected = false;
    let dashboardInitialized = false;
    const defaultServer = 'game.havenandhearth.com';
    const activityLog = [];

    // ── Haven & Hearth game tips ──
    const GAME_TIPS = [
        'Forage everything you find early on — each new discovery grants Learning Points.',
        'Dandelions and Clover are everywhere. Pick them up to earn your first LP.',
        'Your FEP threshold equals your highest attribute. Eat diverse foods to raise stats evenly.',
        'Quality matters for everything. A Q50 axe mines much better than a Q10 one.',
        'Satiation reduces hunger restoration — vary your food categories for best results.',
        'Study curiosities at a Study Desk to earn LP. Intelligence determines mental weight capacity.',
        'A Hearthfire is your respawn point. Place it carefully and keep it fueled.',
        'Swimming is deadly — 10% MHP/sec drowning damage when stamina runs out. Be careful!',
        'Personal claims need Presence to stay active. Recharge at your Claim Pole.',
        'The Forager credo is the foundation for many progression paths. Start there.',
        'Build a Symbel table for feasting bonuses — quality furniture multiplies your FEP gains.',
        'Crime skills are intentionally expensive. Each crime costs 1 SHP — choose wisely.',
        'Cave-in damage increases per underground level. Bring Mine Supports!',
        'Moonflower\'s TileSync plugin auto-uploads your explored tiles to build a shared map.',
        'Lure fishing lets you target specific fish. Line + hook combos determine what you catch.',
        'Realm blessings provide passive bonuses to everyone in the territory.',
        'Trees planted on claimed land grow faster. Forestry and Green Thumb help stunted trees.',
        'The Nidbane is summoned from crime scents — it hunts the criminal based on severity.',
        'Gilding chance depends on matching attributes between the item and equipment.',
        'GraalVM 21 gives ~15-20 extra FPS over standard JDK. Consider it for performance.',
        'Bee Skeps near crops accelerate growth — except for wheat, barley, millet, and hemp.',
        'Your MHP formula: 100 × sqrt(CON/10). Constitution is your lifeline.',
        'Metal Plow runs at Run speed vs Wood Plow at Crawl speed. Huge farming upgrade.',
        'Shift+right-click harvests/plants in area — essential for large farms.',
        'Ancestral Shrines can inherit up to 45% of LP/FEP on character death.',
    ];

    // ─────────────────────────────────────────────────────────────────
    //  VIEW MANAGEMENT
    // ─────────────────────────────────────────────────────────────────

    function showView(id) {
        document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
        const view = document.getElementById(id + 'View');
        if (view) view.classList.add('active');
    }

    function showPanel(panelId) {
        document.querySelectorAll('.dash-panel').forEach(p => p.classList.remove('active'));
        document.querySelectorAll('.dash-toolbar-center .toolbar-btn[data-panel]').forEach(b => b.classList.remove('active'));
        const panel = document.getElementById(panelId);
        if (panel) panel.classList.add('active');
        const btn = document.querySelector(`.toolbar-btn[data-panel="${panelId}"]`);
        if (btn) btn.classList.add('active');

        // Initialize map renderers on first show
        if (panelId === 'panelMap' && !fullMapRenderer) {
            initFullMap();
        }
    }

    function showSettings() { document.getElementById('settingsModal').classList.add('active'); }
    function hideSettings() { document.getElementById('settingsModal').classList.remove('active'); }

    // ─────────────────────────────────────────────────────────────────
    //  TITLEBAR
    // ─────────────────────────────────────────────────────────────────

    document.getElementById('btnMinimize')?.addEventListener('click', () => window.moonflower.minimize());
    document.getElementById('btnMaximize')?.addEventListener('click', () => window.moonflower.maximize());
    document.getElementById('btnClose')?.addEventListener('click', () => window.moonflower.close());

    // ─────────────────────────────────────────────────────────────────
    //  INTRO SCREEN
    // ─────────────────────────────────────────────────────────────────

    document.getElementById('btnLaunch')?.addEventListener('click', async () => {
        const btn = document.getElementById('btnLaunch');
        btn.querySelector('.btn-launch-text').textContent = 'Launching...';
        btn.style.pointerEvents = 'none';

        showView('loading');
        const result = await window.moonflower.launchGame();
        if (!result.success) {
            alert('Launch failed: ' + result.error);
            btn.querySelector('.btn-launch-text').textContent = 'Enter the Wilds';
            btn.style.pointerEvents = '';
            showView('intro');
        }
    });

    document.getElementById('linkSettings')?.addEventListener('click', (e) => { e.preventDefault(); showSettings(); });
    document.getElementById('linkDashboard')?.addEventListener('click', (e) => {
        e.preventDefault(); showView('dashboard'); initDashboard();
    });
    document.getElementById('linkFullMap')?.addEventListener('click', (e) => {
        e.preventDefault();
        const url = getCartoUrl() + '/map';
        window.moonflower.openExternal(url);
    });

    // Memory slider
    const memSlider = document.getElementById('memorySlider');
    const memLabel = document.getElementById('memoryLabel');
    if (memSlider && memLabel) {
        memSlider.addEventListener('input', () => {
            memLabel.textContent = memSlider.value + ' MB';
            window.moonflower?.setSettings({ memory: parseInt(memSlider.value) });
        });
    }

    // Loading cancel
    document.getElementById('btnCancel')?.addEventListener('click', async () => {
        await window.moonflower.killGame();
        const btn = document.getElementById('btnLaunch');
        if (btn) {
            btn.querySelector('.btn-launch-text').textContent = 'Enter the Wilds';
            btn.style.pointerEvents = '';
        }
        showView('intro');
    });

    // ─────────────────────────────────────────────────────────────────
    //  GAME STATE
    // ─────────────────────────────────────────────────────────────────

    window.moonflower?.onGameStateChange((state) => {
        switch (state) {
            case 'idle':
                showView('intro');
                const btn = document.getElementById('btnLaunch');
                if (btn) { btn.querySelector('.btn-launch-text').textContent = 'Enter the Wilds'; btn.style.pointerEvents = ''; }
                break;
            case 'launching':
                showView('loading');
                break;
            case 'running':
                showView('dashboard');
                initDashboard();
                break;
            case 'error':
                showView('intro');
                break;
        }

        // Update status indicators
        updateGameStatus(state);
    });

    function updateGameStatus(state) {
        const dot = document.getElementById('gameStatusDot');
        const text = document.getElementById('gameStatusText');
        if (dot) { dot.className = 'status-dot ' + state; }
        if (text) {
            const labels = { idle: 'Game Idle', launching: 'Launching...', running: 'Game Running', error: 'Error' };
            text.textContent = labels[state] || state;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  DASHBOARD INIT
    // ─────────────────────────────────────────────────────────────────

    function initDashboard() {
        if (dashboardInitialized) return;
        dashboardInitialized = true;

        // Dashboard map
        const canvas = document.getElementById('dashboardMapCanvas');
        if (canvas) {
            dashMapRenderer = new ClientMapRenderer(canvas, getCartoUrl());
            dashMapRenderer.loadTiles(defaultServer);
            setTimeout(() => dashMapRenderer.fitToView(), 2000);
            new ResizeObserver(() => dashMapRenderer.resize()).observe(canvas.parentElement);
        }

        refreshStats();
        connectCartographer();
        showRandomTip();
    }

    function initFullMap() {
        const canvas = document.getElementById('fullMapCanvas');
        if (!canvas) return;
        fullMapRenderer = new ClientMapRenderer(canvas, getCartoUrl());
        fullMapRenderer.showGrid = true;
        fullMapRenderer.loadTiles(defaultServer);
        setTimeout(() => fullMapRenderer.fitToView(), 1500);
        new ResizeObserver(() => fullMapRenderer.resize()).observe(canvas.parentElement);

        // Map overlay controls
        document.getElementById('btnMapZoomIn')?.addEventListener('click', () => { fullMapRenderer.zoomBy(1.3); });
        document.getElementById('btnMapZoomOut')?.addEventListener('click', () => { fullMapRenderer.zoomBy(0.7); });
        document.getElementById('btnMapFit')?.addEventListener('click', () => { fullMapRenderer.fitToView(); });

        // Update coordinate display
        canvas.addEventListener('mousemove', (e) => {
            if (!fullMapRenderer) return;
            const coords = fullMapRenderer.screenToWorld(e.clientX, e.clientY);
            const el = document.getElementById('mapCoords');
            if (el) el.textContent = `${Math.round(coords.x)}, ${Math.round(coords.y)}`;
        });

        // Update tile count and zoom
        setInterval(() => {
            if (!fullMapRenderer) return;
            const zoomEl = document.getElementById('mapZoom');
            const countEl = document.getElementById('mapTileCount');
            if (zoomEl) zoomEl.textContent = fullMapRenderer.camera.zoom.toFixed(1) + 'x';
            if (countEl) countEl.textContent = fullMapRenderer.tiles.length + ' tiles';
        }, 500);
    }

    // ─────────────────────────────────────────────────────────────────
    //  DASHBOARD TOOLBAR
    // ─────────────────────────────────────────────────────────────────

    // Panel navigation buttons
    document.querySelectorAll('.dash-toolbar-center .toolbar-btn[data-panel]').forEach(btn => {
        btn.addEventListener('click', () => showPanel(btn.dataset.panel));
    });
    document.getElementById('btnNavIntro')?.addEventListener('click', () => showView('intro'));
    document.getElementById('btnOpenSettings')?.addEventListener('click', showSettings);

    // Stop game
    document.getElementById('btnStopGame')?.addEventListener('click', async () => {
        await window.moonflower.killGame();
    });

    // Tile collection toggle
    const chkTile = document.getElementById('chkTileCollection');
    if (chkTile) {
        window.moonflower?.getTileCollection().then(v => { chkTile.checked = v; });
        chkTile.addEventListener('change', () => window.moonflower.setTileCollection(chkTile.checked));
    }

    // Full map link
    document.getElementById('btnFullMap')?.addEventListener('click', (e) => {
        e.preventDefault();
        window.moonflower.openExternal(getCartoUrl() + '/map');
    });

    // Quick actions
    document.getElementById('btnStitch')?.addEventListener('click', async () => {
        try {
            await fetch(getCartoUrl() + '/api/stitch', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ server: defaultServer }) });
            addActivity('🧵', 'Map stitch triggered');
        } catch { addActivity('❌', 'Stitch failed — cartographer unreachable'); }
    });

    document.getElementById('btnRefreshStats')?.addEventListener('click', () => refreshStats());

    document.getElementById('btnOpenWebMap')?.addEventListener('click', () => {
        window.moonflower.openExternal(getCartoUrl() + '/map');
    });

    document.getElementById('btnClearTiles')?.addEventListener('click', async () => {
        if (!confirm('Clear ALL tiles from the cartographer? This cannot be undone.')) return;
        try {
            await fetch(getCartoUrl() + '/api/tiles/clear', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ server: defaultServer }) });
            addActivity('🗑️', 'All tiles cleared');
            refreshStats();
        } catch { addActivity('❌', 'Clear failed'); }
    });

    document.getElementById('btnNewTip')?.addEventListener('click', showRandomTip);

    // Forager radius slider
    const forageSlider = document.getElementById('forageRadius');
    if (forageSlider) {
        forageSlider.addEventListener('input', () => {
            const val = document.getElementById('forageRadiusVal');
            if (val) val.textContent = forageSlider.value + ' tiles';
        });
    }

    // ─────────────────────────────────────────────────────────────────
    //  LOG PANEL
    // ─────────────────────────────────────────────────────────────────

    document.getElementById('btnRefreshLog')?.addEventListener('click', async () => {
        const log = await window.moonflower.getDebugLog();
        const el = document.getElementById('logContent');
        if (el) { el.textContent = log.join('\n'); el.scrollTop = el.scrollHeight; }
    });
    document.getElementById('btnClearLog')?.addEventListener('click', () => {
        const el = document.getElementById('logContent');
        if (el) el.textContent = '';
    });

    // ─────────────────────────────────────────────────────────────────
    //  SETTINGS
    // ─────────────────────────────────────────────────────────────────

    document.getElementById('btnCloseSettings')?.addEventListener('click', hideSettings);

    document.getElementById('btnBrowseHaven')?.addEventListener('click', async () => {
        const dir = await window.moonflower.browseDirectory();
        if (dir) document.getElementById('settingHavenPath').value = dir;
    });
    document.getElementById('btnBrowseJava')?.addEventListener('click', async () => {
        const file = await window.moonflower.browseFile([{ name: 'Java', extensions: ['exe'] }]);
        if (file) document.getElementById('settingJavaPath').value = file;
    });

    document.getElementById('btnSaveSettings')?.addEventListener('click', async () => {
        const settings = {
            havenPath: document.getElementById('settingHavenPath').value,
            javaPath: document.getElementById('settingJavaPath').value,
            cartographerUrl: document.getElementById('settingCartoUrl').value,
            memory: parseInt(document.getElementById('settingMemory').value) || 2048
        };
        await window.moonflower.setSettings(settings);
        window._moonflowerSettings = settings;
        hideSettings();
        detectPaths();
    });

    async function loadSettings() {
        const settings = await window.moonflower.getSettings();
        window._moonflowerSettings = settings;
        document.getElementById('settingHavenPath').value = settings.havenPath || '';
        document.getElementById('settingJavaPath').value = settings.javaPath || '';
        document.getElementById('settingCartoUrl').value = settings.cartographerUrl || 'http://127.0.0.1:3300';
        document.getElementById('settingMemory').value = settings.memory || 2048;
        if (memSlider) { memSlider.value = settings.memory || 2048; memLabel.textContent = (settings.memory || 2048) + ' MB'; }
    }

    // ─────────────────────────────────────────────────────────────────
    //  CARTOGRAPHER WS
    // ─────────────────────────────────────────────────────────────────

    function connectCartographer() {
        const wsUrl = getCartoUrl().replace('http', 'ws') + '/ws';
        try {
            cartoWs = new WebSocket(wsUrl);
            cartoWs.onopen = () => {
                cartoConnected = true;
                updateCartoStatus(true);
                addActivity('🟢', 'Connected to HavenCartographer');
            };
            cartoWs.onmessage = (event) => {
                try {
                    const msg = JSON.parse(event.data);
                    if (msg.type === 'tile:update' || msg.type === 'tile:live') {
                        refreshStats();
                        if (dashMapRenderer) dashMapRenderer.loadTiles(defaultServer);
                        if (fullMapRenderer) fullMapRenderer.loadTiles(defaultServer);
                        addActivity('🗺️', `Tile updated: ${msg.data?.x}, ${msg.data?.y}`);
                    } else if (msg.type === 'tile:activity') {
                        addActivity('📡', msg.data?.details || 'Tile activity');
                    }
                } catch { /* ignore parse errors */ }
            };
            cartoWs.onclose = () => {
                cartoConnected = false;
                updateCartoStatus(false);
                setTimeout(connectCartographer, 8000);
            };
            cartoWs.onerror = () => { /* will trigger onclose */ };
        } catch {
            setTimeout(connectCartographer, 8000);
        }
    }

    function updateCartoStatus(connected) {
        const dot = document.getElementById('cartoStatusDot');
        const text = document.getElementById('cartoStatusText');
        if (dot) dot.className = 'status-dot ' + (connected ? 'connected' : 'disconnected');
        if (text) text.textContent = connected ? 'Cartographer ✓' : 'Cartographer ✗';

        const connVal = document.getElementById('connCarto');
        if (connVal) connVal.textContent = connected ? 'Connected' : 'Disconnected';
    }

    // ─────────────────────────────────────────────────────────────────
    //  STATS
    // ─────────────────────────────────────────────────────────────────

    async function refreshStats() {
        try {
            const res = await fetch(`${getCartoUrl()}/api/stats?server=${encodeURIComponent(defaultServer)}`);
            const data = await res.json();
            animateStat('dStatTiles', data.tiles || 0);
            animateStat('dStatMarkers', data.markers || 0);
            animateStat('dStatRegions', data.regions || 0);
            animateStat('dStatSessions', data.sessions || 0);
        } catch { /* cartographer not reachable */ }
    }

    function animateStat(id, target) {
        const el = document.getElementById(id);
        if (!el) return;
        const current = parseInt(el.textContent) || 0;
        if (current === target) return;
        const diff = target - current;
        const steps = Math.min(Math.abs(diff), 20);
        let step = 0;
        const interval = setInterval(() => {
            step++;
            const progress = step / steps;
            el.textContent = Math.round(current + diff * progress);
            if (step >= steps) { el.textContent = target; clearInterval(interval); }
        }, 30);
    }

    // ─────────────────────────────────────────────────────────────────
    //  ACTIVITY FEED
    // ─────────────────────────────────────────────────────────────────

    function addActivity(icon, text) {
        const now = new Date();
        const time = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        activityLog.unshift({ icon, text, time });
        if (activityLog.length > 50) activityLog.pop();
        renderActivity();
    }

    function renderActivity() {
        const list = document.getElementById('activityList');
        if (!list) return;
        if (activityLog.length === 0) {
            list.innerHTML = '<div class="activity-empty">Waiting for map data...</div>';
            return;
        }
        list.innerHTML = activityLog.slice(0, 20).map(a =>
            `<div class="activity-item">
                <span class="activity-icon">${a.icon}</span>
                <span class="activity-text">${a.text}</span>
                <span class="activity-time">${a.time}</span>
            </div>`
        ).join('');
    }

    // ─────────────────────────────────────────────────────────────────
    //  GAME TIPS
    // ─────────────────────────────────────────────────────────────────

    function showRandomTip() {
        const tip = GAME_TIPS[Math.floor(Math.random() * GAME_TIPS.length)];
        const el = document.getElementById('gameTip');
        if (el) el.textContent = tip;
        const introTip = document.getElementById('introTip');
        if (introTip) introTip.textContent = 'Tip: ' + GAME_TIPS[Math.floor(Math.random() * GAME_TIPS.length)];
    }

    // ─────────────────────────────────────────────────────────────────
    //  PATH DETECTION
    // ─────────────────────────────────────────────────────────────────

    async function detectPaths() {
        const paths = await window.moonflower.detectPaths();

        setBadge('badgeHaven', 'badgeHavenStatus', paths.havenPath?.exists);
        setBadge('badgeJava', 'badgeJavaStatus', paths.javaPath?.exists);

        // Cartographer check
        try {
            const res = await fetch(getCartoUrl() + '/api/health');
            setBadge('badgeCarto', 'badgeCartoStatus', res.ok);
        } catch {
            setBadge('badgeCarto', 'badgeCartoStatus', false);
        }
    }

    function setBadge(badgeId, statusId, found) {
        const badge = document.getElementById(badgeId);
        const status = document.getElementById(statusId);
        if (badge) { badge.classList.toggle('found', found); badge.classList.toggle('missing', !found); }
        if (status) status.textContent = found ? '✓' : '✗';
    }

    // ─────────────────────────────────────────────────────────────────
    //  FIREFLIES
    // ─────────────────────────────────────────────────────────────────

    function spawnFireflies() {
        const container = document.getElementById('fireflies');
        if (!container) return;
        for (let i = 0; i < 25; i++) {
            const f = document.createElement('div');
            f.className = 'firefly';
            f.style.left = Math.random() * 100 + '%';
            f.style.top = Math.random() * 100 + '%';
            f.style.setProperty('--dur', (8 + Math.random() * 12) + 's');
            f.style.setProperty('--delay', (Math.random() * 10) + 's');
            f.style.setProperty('--dx', (Math.random() * 120 - 60) + 'px');
            f.style.setProperty('--dy', (Math.random() * 80 - 40) + 'px');
            // Random color — mostly gold, some green, some purple
            const colors = ['#c9a84c', '#c9a84c', '#c9a84c', '#6b9e6e', '#b8a0d8'];
            f.style.background = colors[Math.floor(Math.random() * colors.length)];
            f.style.boxShadow = `0 0 6px 2px ${f.style.background}40`;
            container.appendChild(f);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────

    function getCartoUrl() {
        return (window._moonflowerSettings?.cartographerUrl || 'http://127.0.0.1:3300').replace(/\/$/, '');
    }

    // ─────────────────────────────────────────────────────────────────
    //  INIT
    // ─────────────────────────────────────────────────────────────────

    async function init() {
        await loadSettings();
        await detectPaths();
        spawnFireflies();
        showRandomTip();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
