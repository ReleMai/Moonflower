// =============================================================================
// App — Unified hub controller (tab switching, dashboard, WebSocket)
// =============================================================================
// Manages the top-level tabs (Dashboard, Map, Tools) and all dashboard logic.
// The Map tab is lazy-initialized by map-page.js on first activation.
// =============================================================================

(function () {
    'use strict';

    const server = () => MoonflowerConfig.defaultServer;
    let mapInitialized = false;

    // ── Shared toast (available to all tabs) ────────────────────────────

    window.MoonflowerToast = function (message, type = 'info', duration = 3000) {
        const container = document.getElementById('toastContainer');
        if (!container) return;
        const el = document.createElement('div');
        el.className = `toast ${type}`;
        el.textContent = message;
        container.appendChild(el);
        setTimeout(() => {
            el.classList.add('removing');
            setTimeout(() => el.remove(), 300);
        }, duration);
    };

    // ── Tab Navigation ──────────────────────────────────────────────────

    function initTabs() {
        const tabs = document.querySelectorAll('.nav-tab[data-tab]');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                switchTab(tab.dataset.tab);
            });
        });

        // Dashboard "Open Map Tab" button
        document.getElementById('btnGoToMap')?.addEventListener('click', () => {
            switchTab('map');
        });
    }

    function switchTab(tabName) {
        // Update nav tabs
        document.querySelectorAll('.nav-tab[data-tab]').forEach(t => t.classList.remove('active'));
        const activeTab = document.querySelector(`.nav-tab[data-tab="${tabName}"]`);
        if (activeTab) activeTab.classList.add('active');

        // Update tab views
        document.querySelectorAll('.tab-view').forEach(v => v.classList.remove('active'));
        const tabId = 'tab' + tabName.charAt(0).toUpperCase() + tabName.slice(1);
        const view = document.getElementById(tabId);
        if (view) view.classList.add('active');

        // Lazy-init map on first view
        if (tabName === 'map' && !mapInitialized) {
            mapInitialized = true;
            if (typeof window._initMapPage === 'function') {
                window._initMapPage();
            }
        }

        // Resize canvas when switching to map tab
        if (tabName === 'map' && window._moonflowerMap) {
            setTimeout(() => window._moonflowerMap.resize(), 50);
        }
    }

    // ── Dashboard: Stats ────────────────────────────────────────────────

    async function refreshStats() {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/stats?server=${encodeURIComponent(server())}`);
            const data = await res.json();

            setTextById('statTiles', data.tiles || 0);
            setTextById('statMarkers', data.markers || 0);
            setTextById('statGameIcons', data.game_icons || 0);
            setTextById('statSettlements', data.settlements || 0);
            setTextById('statNotes', data.notes || 0);
            setTextById('statRegions', data.regions || 0);
        } catch (err) {
            console.warn('[App] Stats refresh failed:', err);
        }
    }

    // ── Dashboard: Activity Feed ────────────────────────────────────────

    async function loadActivity() {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/tiles/activity?server=${encodeURIComponent(server())}&limit=20`);
            const data = await res.json();
            const list = document.getElementById('activityList');
            if (!list) return;

            if (data.activity && data.activity.length > 0) {
                list.innerHTML = data.activity.map(a =>
                    `<div class="activity-item">
                        <strong>${a.action}</strong> tile (${a.x}, ${a.y})
                        <span style="float:right;color:var(--text-muted)">${new Date(a.timestamp).toLocaleTimeString()}</span>
                    </div>`
                ).join('');
            } else {
                list.innerHTML = '<div class="activity-empty">No activity yet</div>';
            }
        } catch (err) {
            console.warn('[App] Activity load failed:', err);
        }
    }

    // ── Dashboard: Connection Info ──────────────────────────────────────

    async function checkConnection() {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/health`);
            const data = await res.json();

            setTextById('connServer', data.name || 'Haven Cartographer');
            setTextById('connUptime', formatUptime(data.uptime));
            setTextById('connWs', MoonflowerWS.connected ? 'Connected' : 'Disconnected');
        } catch (err) {
            setTextById('connServer', 'Unreachable');
            setTextById('connUptime', '—');
            setTextById('connWs', 'Error');
        }
    }

    function formatUptime(seconds) {
        if (!seconds) return '—';
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = Math.floor(seconds % 60);
        return `${h}h ${m}m ${s}s`;
    }

    // ── Dashboard: Collector Status ─────────────────────────────────────

    async function checkCollector() {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/collector/status`);
            const data = await res.json();
            const el = document.getElementById('collectorStatus');
            if (el) {
                el.textContent = data.running ? (data.paused ? 'Paused' : 'Running') : 'Stopped';
                el.style.color = data.running ? (data.paused ? 'var(--gold)' : 'var(--forest)') : 'var(--danger)';
            }
        } catch (err) {
            const el = document.getElementById('collectorStatus');
            if (el) { el.textContent = 'Unavailable'; el.style.color = 'var(--text-muted)'; }
        }
    }

    // ── Dashboard: Button Actions ───────────────────────────────────────

    function initDashboardActions() {
        document.getElementById('btnStitch')?.addEventListener('click', async () => {
            try {
                const res = await fetch(`${MoonflowerConfig.serverUrl}/api/stitch`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ server: server() })
                });
                const data = await res.json();
                MoonflowerToast('Stitch started', 'info');
                console.log('[App] Stitch result:', data);
            } catch (err) {
                MoonflowerToast('Stitch failed', 'error');
                console.error('[App] Stitch failed:', err);
            }
        });

        document.getElementById('btnRefresh')?.addEventListener('click', () => {
            refreshStats();
            loadActivity();
            checkConnection();
            checkCollector();
            MoonflowerToast('Dashboard refreshed', 'info');
        });

        document.getElementById('btnClearTiles')?.addEventListener('click', async () => {
            if (!confirm('Clear ALL tiles for this server? This cannot be undone.')) return;
            try {
                await fetch(`${MoonflowerConfig.serverUrl}/api/tiles/clear`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ server: server() })
                });
                refreshStats();
                loadActivity();
                MoonflowerToast('All tiles cleared', 'info');
            } catch (err) {
                MoonflowerToast('Clear failed', 'error');
                console.error('[App] Clear failed:', err);
            }
        });

        document.getElementById('btnPauseCollector')?.addEventListener('click', async () => {
            await fetch(`${MoonflowerConfig.serverUrl}/api/collector/pause`, { method: 'POST' });
            checkCollector();
        });

        document.getElementById('btnResumeCollector')?.addEventListener('click', async () => {
            await fetch(`${MoonflowerConfig.serverUrl}/api/collector/resume`, { method: 'POST' });
            checkCollector();
        });
    }

    // ── Dashboard: Map Preview ──────────────────────────────────────────

    function initMapPreview() {
        const canvas = document.getElementById('mapPreviewCanvas');
        if (!canvas) return;

        const mapRenderer = new MapRenderer(canvas);
        mapRenderer.loadTiles(server());
        mapRenderer.loadMarkers(server());

        // Auto-fit after tiles load
        setTimeout(() => mapRenderer.fitToView(), 2000);
    }

    // ── WebSocket (shared across all tabs) ──────────────────────────────

    function initWebSocket() {
        MoonflowerWS.on('tile:update', () => {
            refreshStats();
            loadActivity();
        });

        MoonflowerWS.on('tile:live', () => {
            refreshStats();
        });

        MoonflowerWS.on('tile:activity', () => {
            loadActivity();
        });

        MoonflowerWS.connect();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    function setTextById(id, text) {
        const el = document.getElementById(id);
        if (el) el.textContent = text;
    }

    // ── Init ────────────────────────────────────────────────────────────

    async function init() {
        await MoonflowerConfig.load();
        initTabs();
        initDashboardActions();
        initMapPreview();
        initWebSocket();

        refreshStats();
        loadActivity();
        checkConnection();
        checkCollector();

        // Periodic dashboard refresh
        setInterval(() => {
            refreshStats();
            checkConnection();
            checkCollector();
        }, 30000);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
