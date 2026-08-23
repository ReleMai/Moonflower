// =============================================================================
// Map Page — Full-screen map tab controller
// =============================================================================
// Lazy-initialized by app.js when the Map tab is first activated.
// Wires up MapRenderer, panel tabs, tool selection, dialogs, screenshots,
// game icons, settlements, notes, markers, activity feed, layer selector,
// and WebSocket real-time events.
// =============================================================================

(function () {
    'use strict';

    let mapRenderer = null;
    const server = () => MoonflowerConfig.defaultServer;
    const screenshots = []; // in-memory gallery
    let initialized = false;

    // ── Helpers ─────────────────────────────────────────────────────────

    function $(id) { return document.getElementById(id); }
    function $$(sel) { return document.querySelectorAll(sel); }

    function toast(message, type = 'info', duration = 3000) {
        if (typeof MoonflowerToast === 'function') {
            MoonflowerToast(message, type, duration);
        }
    }

    function escHtml(str) {
        const div = document.createElement('div');
        div.textContent = str || '';
        return div.innerHTML;
    }

    // ── Init Map ────────────────────────────────────────────────────────

    function initMap() {
        const canvas = $('mapCanvas');
        const minimapCanvas = $('minimapCanvas');
        if (!canvas) return;

        mapRenderer = new MapRenderer(canvas, minimapCanvas);
        window._moonflowerMap = mapRenderer;

        // Load all data for current layer
        mapRenderer.reloadAll(server());

        // Auto-fit after initial load
        setTimeout(() => {
            mapRenderer.fitToView();
            updateEmptyState();
        }, 2000);

        // Map click handler for placing markers/notes
        mapRenderer.onMapClick = (wx, wy, event) => {
            const tool = mapRenderer.activeTool;
            if (tool === 'marker') openMarkerDialog(wx, wy);
            else if (tool === 'note') openNoteDialog(wx, wy);
        };

        mapRenderer.onCameraChange = () => {
            mapRenderer.renderMinimap();
        };

        // When layer changes, reload all layer-specific data
        mapRenderer.onLayerChange = (layer) => {
            mapRenderer.reloadAll(server());
            updateStats();
            refreshAllLists();
        };
    }

    function updateEmptyState() {
        const el = $('mapEmptyState');
        if (!el) return;
        el.classList.toggle('visible', mapRenderer && mapRenderer.tiles.length === 0);
    }

    // ── Stats ───────────────────────────────────────────────────────────

    async function updateStats() {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/stats?server=${encodeURIComponent(server())}`);
            const data = await res.json();

            const setCount = (id, val) => { const el = $(id); if (el) el.textContent = val; };
            setCount('mapTileCount', data.tiles || 0);
            setCount('mapMarkerCount', mapRenderer ? mapRenderer.markers.length : 0);
            setCount('mapIconCount', mapRenderer ? mapRenderer.gameIcons.length : 0);
            setCount('mapNoteCount', mapRenderer ? mapRenderer.notes.length : 0);

            if (data.bounds) {
                const b = $('mapBounds');
                if (b) b.textContent = `(${data.bounds.minX},${data.bounds.minY}) → (${data.bounds.maxX},${data.bounds.maxY})`;
            }

            // Update layer selector if new layers appeared
            if (data.layers) {
                updateLayerSelector(data.layers);
            }
        } catch (err) {
            console.warn('[MapPage] Stats failed:', err);
        }
    }

    // ── Layer Selector ──────────────────────────────────────────────────

    function initLayerSelector() {
        const sel = $('layerSelect');
        if (!sel) return;

        sel.addEventListener('change', () => {
            const layer = parseInt(sel.value, 10);
            if (mapRenderer) {
                mapRenderer.setLayer(layer);
            }
        });
    }

    function updateLayerSelector(layers) {
        const sel = $('layerSelect');
        if (!sel) return;

        const current = parseInt(sel.value, 10);
        const sorted = [...layers].sort((a, b) => b - a); // surface first

        // Only rebuild if options changed
        const existing = Array.from(sel.options).map(o => parseInt(o.value, 10));
        if (JSON.stringify(sorted) === JSON.stringify(existing)) return;

        sel.innerHTML = '';
        for (const layer of sorted) {
            const opt = document.createElement('option');
            opt.value = layer;
            if (layer === 0) opt.textContent = 'Surface';
            else if (layer < 0) opt.textContent = `Cave Level ${Math.abs(layer)}`;
            else opt.textContent = `Layer ${layer}`;
            if (layer === current) opt.selected = true;
            sel.appendChild(opt);
        }
    }

    // ── Panel Tabs ──────────────────────────────────────────────────────

    function initPanelTabs() {
        for (const tab of $$('.panel-tab')) {
            tab.addEventListener('click', () => {
                const targetId = tab.dataset.panel;
                $$('.panel-tab').forEach(t => t.classList.remove('active'));
                $$('.panel-content').forEach(p => p.classList.remove('active'));
                tab.classList.add('active');
                const panel = $(targetId);
                if (panel) panel.classList.add('active');
            });
        }
    }

    // ── Tool Selection ──────────────────────────────────────────────────

    function initToolSelection() {
        const tools = ['Pan', 'Marker', 'Note', 'Region'];
        const viewport = $('mapViewport');

        for (const name of tools) {
            const btn = $(`btnTool${name}`);
            if (!btn) continue;
            btn.addEventListener('click', () => {
                tools.forEach(t => {
                    const b = $(`btnTool${t}`);
                    if (b) b.classList.remove('active');
                });
                btn.classList.add('active');
                const tool = name.toLowerCase();
                if (mapRenderer) mapRenderer.activeTool = tool;

                if (viewport) {
                    viewport.className = 'map-viewport';
                    if (tool !== 'pan') viewport.classList.add(`tool-${tool}`);
                }
            });
        }
    }

    // ── Visibility Toggles ──────────────────────────────────────────────

    function initVisibilityToggles() {
        const toggles = [
            { btnId: 'btnToggleGrid',        chkId: 'chkLayerGrid',        prop: 'showGrid' },
            { btnId: 'btnToggleMarkers',     chkId: 'chkLayerMarkers',     prop: 'showMarkers' },
            { btnId: 'btnToggleGameIcons',   chkId: 'chkLayerGameIcons',   prop: 'showGameIcons' },
            { btnId: 'btnToggleSettlements', chkId: 'chkLayerSettlements', prop: 'showSettlements' },
            { btnId: 'btnToggleNotes',       chkId: 'chkLayerNotes',       prop: 'showNotes' },
            { btnId: 'btnToggleRegions',     chkId: 'chkLayerRegions',     prop: 'showRegions' },
            { btnId: 'btnToggleBots',        chkId: 'chkShowBots',         prop: 'showBots' }
        ];

        for (const t of toggles) {
            const btn = $(t.btnId);
            const chk = $(t.chkId);

            const sync = (active) => {
                if (btn) btn.classList.toggle('active', active);
                if (chk) chk.checked = active;
                if (mapRenderer) { mapRenderer[t.prop] = active; mapRenderer.queueRender(); }
            };

            if (btn) btn.addEventListener('click', () => sync(!mapRenderer?.[t.prop]));
            if (chk) chk.addEventListener('change', () => sync(chk.checked));
        }
    }

    // ── Zoom Controls ───────────────────────────────────────────────────

    function initZoomControls() {
        $('btnZoomIn')?.addEventListener('click', () => mapRenderer?.zoomIn());
        $('btnZoomOut')?.addEventListener('click', () => mapRenderer?.zoomOut());
        $('btnFitMap')?.addEventListener('click', () => mapRenderer?.fitToView());
    }

    // ── Marker Dialog ───────────────────────────────────────────────────

    function openMarkerDialog(wx, wy) {
        const dialog = $('markerDialog');
        if (!dialog) return;
        $('markerDialogX').value = wx.toFixed(2);
        $('markerDialogY').value = wy.toFixed(2);
        $('markerForm').reset();
        $('markerDialogX').value = wx.toFixed(2);
        $('markerDialogY').value = wy.toFixed(2);
        populateMarkerCategories();
        dialog.showModal();
    }

    function populateMarkerCategories() {
        const sel = $('markerCategorySelect');
        if (!sel || sel.children.length > 0) return;
        const cats = MoonflowerMarkers.categories.length > 0
            ? MoonflowerMarkers.categories
            : ['default', 'resource', 'danger', 'settlement', 'landmark', 'camp', 'road', 'water', 'mine', 'farm'];
        for (const c of cats) {
            const opt = document.createElement('option');
            opt.value = c;
            opt.textContent = c.charAt(0).toUpperCase() + c.slice(1);
            sel.appendChild(opt);
        }
    }

    function initMarkerDialog() {
        const dialog = $('markerDialog');
        const form = $('markerForm');
        if (!dialog || !form) return;

        $('markerDialogCancel')?.addEventListener('click', () => dialog.close());

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const fd = new FormData(form);
            const result = await MoonflowerMarkers.createMarker(
                server(),
                parseFloat(fd.get('x') || $('markerDialogX').value),
                parseFloat(fd.get('y') || $('markerDialogY').value),
                fd.get('name'),
                fd.get('category'),
                '📍',
                fd.get('color'),
                fd.get('description')
            );
            dialog.close();
            if (result && !result.error) {
                toast('Marker placed', 'success');
                mapRenderer?.loadMarkers(server());
                refreshMarkerList();
                updateStats();
            } else {
                toast('Failed to place marker', 'error');
            }
        });
    }

    // ── Note Dialog ─────────────────────────────────────────────────────

    function openNoteDialog(wx, wy) {
        const dialog = $('noteDialog');
        if (!dialog) return;
        $('noteForm').reset();
        $('noteDialogX').value = wx.toFixed(2);
        $('noteDialogY').value = wy.toFixed(2);
        $('noteScreenshotData').value = '';
        $('noteScreenshotPreview').style.display = 'none';
        $('noteScreenshotClear').style.display = 'none';
        $('noteDropzone').style.display = '';
        dialog.showModal();
    }

    function initNoteDialog() {
        const dialog = $('noteDialog');
        const form = $('noteForm');
        if (!dialog || !form) return;

        $('noteDialogCancel')?.addEventListener('click', () => dialog.close());

        // Screenshot file upload
        const fileInput = $('noteScreenshotFile');
        const dropzone = $('noteDropzone');
        const preview = $('noteScreenshotPreview');
        const clearBtn = $('noteScreenshotClear');

        if (dropzone && fileInput) {
            dropzone.addEventListener('click', () => fileInput.click());
            dropzone.addEventListener('dragover', (e) => { e.preventDefault(); dropzone.classList.add('dragover'); });
            dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
            dropzone.addEventListener('drop', (e) => {
                e.preventDefault();
                dropzone.classList.remove('dragover');
                const file = e.dataTransfer.files[0];
                if (file && file.type.startsWith('image/')) handleScreenshotFile(file);
            });
        }

        if (fileInput) {
            fileInput.addEventListener('change', () => {
                if (fileInput.files[0]) handleScreenshotFile(fileInput.files[0]);
            });
        }

        if (clearBtn) {
            clearBtn.addEventListener('click', () => {
                $('noteScreenshotData').value = '';
                if (preview) preview.style.display = 'none';
                clearBtn.style.display = 'none';
                if (dropzone) dropzone.style.display = '';
                if (fileInput) fileInput.value = '';
            });
        }

        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const fd = new FormData(form);
            const payload = {
                server: server(),
                x: parseFloat(fd.get('x') || $('noteDialogX').value),
                y: parseFloat(fd.get('y') || $('noteDialogY').value),
                layer: mapRenderer?.currentLayer || 0,
                title: fd.get('title'),
                text: fd.get('text') || '',
                icon: fd.get('icon') || '📝',
                color: fd.get('color') || '#e8d5a3',
                screenshot: $('noteScreenshotData').value || null
            };

            try {
                const res = await fetch(`${MoonflowerConfig.serverUrl}/api/notes`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const result = await res.json();
                dialog.close();
                if (result && !result.error) {
                    toast('Note added', 'success');
                    mapRenderer?.loadNotes(server());
                    refreshNoteList();
                    updateStats();
                } else {
                    toast(result.error || 'Failed to add note', 'error');
                }
            } catch (err) {
                dialog.close();
                toast('Failed to add note', 'error');
            }
        });
    }

    function handleScreenshotFile(file) {
        const reader = new FileReader();
        reader.onload = (e) => {
            const dataUrl = e.target.result;
            $('noteScreenshotData').value = dataUrl;
            const preview = $('noteScreenshotPreview');
            if (preview) {
                preview.src = dataUrl;
                preview.style.display = 'block';
            }
            const dropzone = $('noteDropzone');
            if (dropzone) dropzone.style.display = 'none';
            const clearBtn = $('noteScreenshotClear');
            if (clearBtn) clearBtn.style.display = '';
        };
        reader.readAsDataURL(file);
    }

    // ── Note Detail Dialog ──────────────────────────────────────────────

    function openNoteDetail(note) {
        const dialog = $('noteDetailDialog');
        if (!dialog) return;

        $('noteDetailTitle').textContent = note.title || 'Note';
        $('noteDetailText').textContent = note.text || '';

        const ssImg = $('noteDetailScreenshot');
        if (ssImg) {
            if (note.screenshot) {
                ssImg.src = note.screenshot;
                ssImg.style.display = 'block';
            } else {
                ssImg.style.display = 'none';
            }
        }

        const meta = $('noteDetailMeta');
        if (meta) {
            meta.textContent = `${note.x?.toFixed?.(1) ?? note.x}, ${note.y?.toFixed?.(1) ?? note.y} · Layer: ${mapRenderer?.getLayerName(note.layer || 0) || 'Surface'}`;
        }

        // Wire delete button
        $('noteDetailDelete').onclick = async () => {
            try {
                await fetch(`${MoonflowerConfig.serverUrl}/api/notes/${note.id}`, { method: 'DELETE' });
                dialog.close();
                toast('Note deleted', 'info');
                mapRenderer?.loadNotes(server());
                refreshNoteList();
                updateStats();
            } catch (err) {
                toast('Delete failed', 'error');
            }
        };

        $('noteDetailClose')?.addEventListener('click', () => dialog.close(), { once: true });
        dialog.showModal();
    }

    // ── Screenshots ─────────────────────────────────────────────────────

    function initScreenshots() {
        $('btnScreenshot')?.addEventListener('click', () => captureScreenshot(false));
        $('btnTakeScreenshot')?.addEventListener('click', () => captureScreenshot(false));
        $('btnScreenshotFull')?.addEventListener('click', () => captureScreenshot(true));
        $('ssDialogClose')?.addEventListener('click', () => $('screenshotDialog')?.close());
        $('ssDialogDownload')?.addEventListener('click', downloadScreenshot);
        $('ssDialogCopy')?.addEventListener('click', copyScreenshotToClipboard);
    }

    function captureScreenshot(fullMap = false) {
        if (!mapRenderer) return;

        const flash = $('screenshotFlash');
        if (flash) {
            flash.classList.remove('active');
            void flash.offsetWidth;
            flash.classList.add('active');
        }

        const format = $('ssFormat')?.value || 'png';
        const options = {
            format,
            includeMarkers:     $('ssIncludeMarkers')?.checked ?? true,
            includeGameIcons:   $('ssIncludeGameIcons')?.checked ?? true,
            includeSettlements: $('ssIncludeSettlements')?.checked ?? true,
            includeNotes:       $('ssIncludeNotes')?.checked ?? true,
            includeGrid:        $('ssIncludeGrid')?.checked ?? false,
            fullMap
        };

        const dataUrl = mapRenderer.captureScreenshot(options);

        const entry = {
            id: Date.now(),
            dataUrl,
            format,
            fullMap,
            timestamp: new Date().toLocaleTimeString()
        };
        screenshots.unshift(entry);
        if (screenshots.length > 20) screenshots.pop();
        refreshScreenshotGallery();

        const dialog = $('screenshotDialog');
        const previewImg = $('screenshotPreviewImg');
        if (dialog && previewImg) {
            previewImg.src = dataUrl;
            previewImg._format = format;
            dialog.showModal();
        }

        toast('Screenshot captured', 'success');
    }

    function downloadScreenshot() {
        const img = $('screenshotPreviewImg');
        if (!img || !img.src) return;
        const a = document.createElement('a');
        a.href = img.src;
        a.download = `moonflower-map-${Date.now()}.${img._format || 'png'}`;
        a.click();
    }

    async function copyScreenshotToClipboard() {
        const img = $('screenshotPreviewImg');
        if (!img || !img.src) return;
        try {
            const res = await fetch(img.src);
            const blob = await res.blob();
            await navigator.clipboard.write([new ClipboardItem({ [blob.type]: blob })]);
            toast('Copied to clipboard', 'success');
        } catch (err) {
            toast('Copy failed — try downloading instead', 'error');
        }
    }

    function refreshScreenshotGallery() {
        const gallery = $('screenshotGallery');
        if (!gallery) return;

        if (screenshots.length === 0) {
            gallery.innerHTML = '<div class="list-empty">No screenshots taken</div>';
            return;
        }

        gallery.innerHTML = '';
        for (const ss of screenshots) {
            const el = document.createElement('div');
            el.className = 'screenshot-thumb';
            el.innerHTML = `
                <img src="${ss.dataUrl}" alt="Screenshot">
                <div class="screenshot-thumb-info">
                    <span>${ss.fullMap ? 'Full Map' : 'Current View'}</span>
                    <span>${ss.timestamp} · ${ss.format.toUpperCase()}</span>
                </div>
            `;
            el.addEventListener('click', () => {
                const dialog = $('screenshotDialog');
                const previewImg = $('screenshotPreviewImg');
                if (dialog && previewImg) {
                    previewImg.src = ss.dataUrl;
                    previewImg._format = ss.format;
                    dialog.showModal();
                }
            });
            gallery.appendChild(el);
        }
    }

    // ── Export Data ──────────────────────────────────────────────────────

    function initExport() {
        $('btnExportData')?.addEventListener('click', () => {
            if (!mapRenderer) return;
            const data = {
                server: server(),
                layer: mapRenderer.currentLayer,
                tiles: mapRenderer.tiles.map(t => ({ x: t.x, y: t.y })),
                markers: mapRenderer.markers,
                gameIcons: mapRenderer.gameIcons,
                settlements: mapRenderer.settlements,
                notes: mapRenderer.notes,
                regions: mapRenderer.regions,
                exported_at: new Date().toISOString()
            };
            const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = `moonflower-export-${Date.now()}.json`;
            a.click();
            URL.revokeObjectURL(a.href);
            toast('Map data exported', 'success');
        });
    }

    // ── Marker List ─────────────────────────────────────────────────────

    function refreshMarkerList() {
        const list = $('markerList');
        if (!list || !mapRenderer) return;

        let markers = mapRenderer.markers || [];
        const search = $('markerSearch')?.value?.toLowerCase() || '';

        if (search) {
            markers = markers.filter(m => (m.name || '').toLowerCase().includes(search));
        }

        if (markers.length === 0) {
            list.innerHTML = '<div class="list-empty">No markers yet</div>';
            return;
        }

        list.innerHTML = '';
        for (const m of markers) {
            const el = document.createElement('div');
            el.className = 'list-item';
            el.innerHTML = `
                <div class="list-item-icon marker" style="color:${m.color || '#c9a84c'}" title="${m.category || 'default'}">📍</div>
                <div class="list-item-body">
                    <div class="list-item-name">${escHtml(m.name)}</div>
                    <div class="list-item-meta">${m.x?.toFixed?.(1) ?? m.x}, ${m.y?.toFixed?.(1) ?? m.y} · ${m.category || 'default'}</div>
                </div>
                <div class="list-item-actions">
                    <button title="Go to" data-action="goto" data-x="${m.x}" data-y="${m.y}">⌖</button>
                    <button class="delete" title="Delete" data-action="delete-marker" data-id="${m.id}">×</button>
                </div>
            `;
            list.appendChild(el);
        }
    }

    // ── Game Icon List ──────────────────────────────────────────────────

    function refreshIconList() {
        const list = $('iconList');
        if (!list || !mapRenderer) return;

        let icons = mapRenderer.gameIcons || [];
        const search = $('iconSearch')?.value?.toLowerCase() || '';
        const filter = document.querySelector('#iconFilterChips .chip.active')?.dataset?.filter || 'all';

        if (filter !== 'all') {
            icons = icons.filter(i => i.icon_type === filter);
        }
        if (search) {
            icons = icons.filter(i => (i.label || i.res_name || '').toLowerCase().includes(search));
        }

        if (icons.length === 0) {
            list.innerHTML = '<div class="list-empty">No game icons detected — launch the TileSync plugin</div>';
            return;
        }

        list.innerHTML = '';
        for (const gi of icons) {
            const cat = MapRenderer.GAME_ICON_CATEGORIES[gi.icon_type] || MapRenderer.GAME_ICON_CATEGORIES.object;
            const el = document.createElement('div');
            el.className = 'list-item';
            el.innerHTML = `
                <div class="list-item-icon game-icon" style="color:${cat.color}">${cat.icon}</div>
                <div class="list-item-body">
                    <div class="list-item-name">${escHtml(gi.label || gi.res_name)}</div>
                    <div class="list-item-meta">${gi.x?.toFixed?.(1) ?? gi.x}, ${gi.y?.toFixed?.(1) ?? gi.y} · ${gi.icon_type}</div>
                </div>
                <div class="list-item-actions">
                    <button title="Go to" data-action="goto" data-x="${gi.x}" data-y="${gi.y}">⌖</button>
                </div>
            `;
            list.appendChild(el);
        }
    }

    // ── Settlement List ─────────────────────────────────────────────────

    function refreshSettlementList() {
        const list = $('settlementList');
        if (!list || !mapRenderer) return;

        let settlements = mapRenderer.settlements || [];
        const search = $('settlementSearch')?.value?.toLowerCase() || '';
        const filter = document.querySelector('#settlementFilterChips .chip.active')?.dataset?.filter || 'all';

        if (filter !== 'all') {
            settlements = settlements.filter(s => s.type === filter);
        }
        if (search) {
            settlements = settlements.filter(s => (s.name || s.owner || '').toLowerCase().includes(search));
        }

        if (settlements.length === 0) {
            list.innerHTML = '<div class="list-empty">No settlements detected</div>';
            return;
        }

        list.innerHTML = '';
        for (const s of settlements) {
            const isVillage = s.type === 'village';
            const el = document.createElement('div');
            el.className = 'list-item';
            el.innerHTML = `
                <div class="list-item-icon settlement" style="color:${isVillage ? '#c9a84c' : '#d4763a'}">${isVillage ? '⛏' : '🏴'}</div>
                <div class="list-item-body">
                    <div class="list-item-name">${escHtml(s.name || s.owner || 'Unknown')}</div>
                    <div class="list-item-meta">${s.x?.toFixed?.(1) ?? s.x}, ${s.y?.toFixed?.(1) ?? s.y} · ${s.type}${s.owner ? ' · ' + escHtml(s.owner) : ''}</div>
                </div>
                <div class="list-item-actions">
                    <button title="Go to" data-action="goto" data-x="${s.x}" data-y="${s.y}">⌖</button>
                </div>
            `;
            list.appendChild(el);
        }
    }

    // ── Note List ───────────────────────────────────────────────────────

    function refreshNoteList() {
        const list = $('noteList');
        if (!list || !mapRenderer) return;

        let notes = mapRenderer.notes || [];
        const search = $('noteSearch')?.value?.toLowerCase() || '';

        if (search) {
            notes = notes.filter(n => (n.title || n.text || '').toLowerCase().includes(search));
        }

        if (notes.length === 0) {
            list.innerHTML = '<div class="list-empty">No notes yet — click the map to add one</div>';
            return;
        }

        list.innerHTML = '';
        for (const n of notes) {
            const el = document.createElement('div');
            el.className = 'list-item';
            el.innerHTML = `
                <div class="list-item-icon note" style="color:${n.color || '#e8d5a3'}">${n.icon || '📝'}</div>
                <div class="list-item-body">
                    <div class="list-item-name">${escHtml(n.title)}${n.screenshot ? ' 📷' : ''}</div>
                    <div class="list-item-meta">${n.x?.toFixed?.(1) ?? n.x}, ${n.y?.toFixed?.(1) ?? n.y}${n.text ? ' · ' + escHtml(n.text.substring(0, 40)) : ''}</div>
                </div>
                <div class="list-item-actions">
                    <button title="Go to" data-action="goto" data-x="${n.x}" data-y="${n.y}">⌖</button>
                    <button title="View" data-action="view-note" data-id="${n.id}">👁</button>
                    <button class="delete" title="Delete" data-action="delete-note" data-id="${n.id}">×</button>
                </div>
            `;
            list.appendChild(el);
        }
    }

    function refreshAllLists() {
        refreshMarkerList();
        refreshIconList();
        refreshSettlementList();
        refreshNoteList();
        refreshBotList();
    }

    // ── List Actions (event delegation) ─────────────────────────────────

    function initListActions() {
        // Attach event delegation to each list
        const lists = ['markerList', 'iconList', 'settlementList', 'noteList'];
        for (const listId of lists) {
            $(listId)?.addEventListener('click', handleListAction);
        }

        async function handleListAction(e) {
            const btn = e.target.closest('[data-action]');
            if (!btn) return;
            const action = btn.dataset.action;

            if (action === 'goto') {
                const x = parseFloat(btn.dataset.x);
                const y = parseFloat(btn.dataset.y);
                mapRenderer?.panTo(x, y);
            } else if (action === 'delete-marker') {
                const id = btn.dataset.id;
                await MoonflowerMarkers.deleteMarker(id);
                toast('Marker deleted', 'info');
                mapRenderer?.loadMarkers(server());
                setTimeout(refreshMarkerList, 500);
                updateStats();
            } else if (action === 'delete-note') {
                const id = btn.dataset.id;
                try {
                    await fetch(`${MoonflowerConfig.serverUrl}/api/notes/${id}`, { method: 'DELETE' });
                    toast('Note deleted', 'info');
                    mapRenderer?.loadNotes(server());
                    setTimeout(refreshNoteList, 500);
                    updateStats();
                } catch (err) {
                    toast('Delete failed', 'error');
                }
            } else if (action === 'view-note') {
                const id = parseInt(btn.dataset.id, 10);
                const note = (mapRenderer?.notes || []).find(n => n.id === id);
                if (note) openNoteDetail(note);
            }
        }

        // Filter chips for game icons
        $('iconFilterChips')?.addEventListener('click', (e) => {
            const chip = e.target.closest('.chip');
            if (!chip) return;
            $$('#iconFilterChips .chip').forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            refreshIconList();
        });

        // Filter chips for settlements
        $('settlementFilterChips')?.addEventListener('click', (e) => {
            const chip = e.target.closest('.chip');
            if (!chip) return;
            $$('#settlementFilterChips .chip').forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            refreshSettlementList();
        });

        // Search inputs
        $('markerSearch')?.addEventListener('input', () => refreshMarkerList());
        $('iconSearch')?.addEventListener('input', () => refreshIconList());
        $('settlementSearch')?.addEventListener('input', () => refreshSettlementList());
        $('noteSearch')?.addEventListener('input', () => refreshNoteList());

        // Add buttons
        $('btnAddMarker')?.addEventListener('click', () => {
            if (!mapRenderer) return;
            const cx = mapRenderer.canvas.width / 2;
            const cy = mapRenderer.canvas.height / 2;
            const world = mapRenderer.screenToWorld(
                cx + mapRenderer.canvas.getBoundingClientRect().left,
                cy + mapRenderer.canvas.getBoundingClientRect().top
            );
            openMarkerDialog(world.x, world.y);
        });
        $('btnAddNote')?.addEventListener('click', () => {
            if (!mapRenderer) return;
            const cx = mapRenderer.canvas.width / 2;
            const cy = mapRenderer.canvas.height / 2;
            const world = mapRenderer.screenToWorld(
                cx + mapRenderer.canvas.getBoundingClientRect().left,
                cy + mapRenderer.canvas.getBoundingClientRect().top
            );
            openNoteDialog(world.x, world.y);
        });
    }

    // ── Bot Tracking Panel (per-bot expandable cards) ───────────────────

    let botWalkMode = false;
    let botWalkTarget = null;   // botId that will receive the walk command
    let trackingEnabled = true; // master on/off for bot tracking
    const expandedBots = new Set(); // track which cards are expanded

    function refreshBotList() {
        const list = $('botList');
        if (!list || !mapRenderer) return;

        const bots = mapRenderer.bots || [];
        const countEl = $('botOnlineCount');
        const onlineCount = bots.filter(b => b.status !== 'offline').length;
        if (countEl) countEl.textContent = `${onlineCount} online`;

        if (bots.length === 0) {
            list.innerHTML = '<div class="list-empty">No clients connected — launch Moonflower in-game</div>';
            return;
        }

        // Preserve scroll position
        const scrollTop = list.scrollTop;

        list.innerHTML = '';
        for (const bot of bots) {
            list.appendChild(buildBotCard(bot));
        }

        list.scrollTop = scrollTop;
    }

    function buildBotCard(bot) {
        const statusColor = getStatusColor(bot.status);
        const isExpanded = expandedBots.has(bot.botId);
        const card = document.createElement('div');
        card.className = `bot-card${isExpanded ? ' expanded' : ''}`;
        card.dataset.botId = bot.botId;

        const status = bot.status || 'idle';
        const name = bot.name || bot.botId;
        const coordStr = `${bot.x?.toFixed?.(1) ?? '?'}, ${bot.y?.toFixed?.(1) ?? '?'}`;
        const tileStr = bot.tileX !== undefined ? ` · T(${bot.tileX}, ${bot.tileY})` : '';

        card.innerHTML = `
            <div class="bot-card-header">
                <div class="bot-card-avatar" style="background:${statusColor}22;color:${statusColor}">
                    🧑
                    <div class="bot-card-dot ${status}"></div>
                </div>
                <div class="bot-card-info">
                    <div class="bot-card-name">${escHtml(name)}</div>
                    <div class="bot-card-meta">${coordStr} · <span class="bot-card-status-pill ${status}">${status}</span></div>
                </div>
                <div class="bot-card-actions">
                    <button title="Pan to bot" data-action="goto-bot" data-bot-id="${bot.botId}">⌖</button>
                    <button class="bot-card-toggle" title="Expand" data-action="toggle-bot" data-bot-id="${bot.botId}">▸</button>
                </div>
            </div>
            <div class="bot-card-body">
                <div class="bot-card-body-inner">
                    <div class="bot-card-coords" data-coords-for="${bot.botId}">${coordStr}${tileStr}</div>
                    <div class="bot-section">
                        <h4>Commands</h4>
                        <div class="bot-cmd-row">
                            <button class="panel-btn" data-cmd="walk" data-bot-id="${bot.botId}">🚶 Walk To</button>
                            <button class="panel-btn" data-cmd="stop" data-bot-id="${bot.botId}">⏹ Stop</button>
                        </div>
                        <div class="bot-cmd-row">
                            <button class="panel-btn" data-cmd="forage-start" data-bot-id="${bot.botId}">🌿 Forage</button>
                            <button class="panel-btn" data-cmd="forage-stop" data-bot-id="${bot.botId}">✕ Stop Forage</button>
                        </div>
                    </div>
                    <div class="bot-section">
                        <h4>Nearby Objects</h4>
                        <div class="bot-nearby-list scrollable-list" data-nearby-for="${bot.botId}">
                            <div class="list-empty">Expand to load</div>
                        </div>
                    </div>
                    <div class="bot-section">
                        <h4>Command History</h4>
                        <div class="bot-cmd-history scrollable-list" data-history-for="${bot.botId}">
                            <div class="list-empty">No commands</div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // If expanded, kick off data loading
        if (isExpanded) {
            setTimeout(() => {
                refreshBotNearbyList(bot.botId);
                refreshBotCommandHistory(bot.botId);
            }, 50);
        }

        return card;
    }

    function getStatusColor(status) {
        const colors = {
            idle: '#4ade80', moving: '#facc15', busy: '#f97316',
            gathering: '#c9a84c', foraging: '#34d399', offline: '#666'
        };
        return colors[status] || colors.idle;
    }

    function toggleBotCard(botId) {
        if (expandedBots.has(botId)) {
            expandedBots.delete(botId);
        } else {
            expandedBots.add(botId);
        }

        const card = document.querySelector(`.bot-card[data-bot-id="${botId}"]`);
        if (!card) return;

        card.classList.toggle('expanded', expandedBots.has(botId));

        // Load data when expanding
        if (expandedBots.has(botId)) {
            refreshBotNearbyList(botId);
            refreshBotCommandHistory(botId);
            // Also select this bot on the map for highlight
            mapRenderer.selectedBotId = botId;
            mapRenderer.queueRender();
            const bot = (mapRenderer.bots || []).find(b => b.botId === botId);
            if (bot && bot.x !== undefined) mapRenderer.panTo(bot.x, bot.y);
        }
    }

    async function refreshBotNearbyList(botId) {
        const list = document.querySelector(`[data-nearby-for="${botId}"]`);
        if (!list) return;

        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/bots/${botId}/nearby?server=${encodeURIComponent(server())}`);
            const data = await res.json();
            const objects = data.objects || [];

            if (objects.length === 0) {
                list.innerHTML = '<div class="list-empty">No objects nearby</div>';
                return;
            }

            list.innerHTML = '';
            for (const obj of objects.slice(0, 30)) {
                const shortName = (obj.name || 'unknown').split('/').pop();
                const el = document.createElement('div');
                el.className = 'nearby-item interactable';
                el.innerHTML = `
                    <span class="nearby-item-name" title="${escHtml(obj.name)}">${escHtml(shortName)}</span>
                    <span class="nearby-item-dist">${obj.dist?.toFixed?.(0) ?? '?'}u</span>
                    <span class="nearby-item-interact">⚡</span>
                `;
                el.addEventListener('click', () => openBotInteractDialog(botId, obj));
                list.appendChild(el);
            }
        } catch (err) {
            list.innerHTML = '<div class="list-empty">Failed to load</div>';
        }
    }

    async function refreshBotCommandHistory(botId) {
        const list = document.querySelector(`[data-history-for="${botId}"]`);
        if (!list) return;

        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/bots/commands/history?botId=${botId}&server=${encodeURIComponent(server())}&limit=15`);
            const data = await res.json();
            const commands = data.commands || [];

            if (commands.length === 0) {
                list.innerHTML = '<div class="list-empty">No commands sent</div>';
                return;
            }

            list.innerHTML = '';
            for (const cmd of commands) {
                const el = document.createElement('div');
                el.className = 'cmd-history-item';
                el.innerHTML = `
                    <span class="cmd-history-badge ${cmd.status}"></span>
                    <span class="cmd-history-text">${escHtml(cmd.command)}${cmd.menu_option ? ' (' + escHtml(cmd.menu_option) + ')' : (cmd.menuOption ? ' (' + escHtml(cmd.menuOption) + ')' : '')}</span>
                    <span class="cmd-history-status">${cmd.status}</span>
                `;
                list.appendChild(el);
            }
        } catch (err) {
            list.innerHTML = '<div class="list-empty">Failed to load</div>';
        }
    }

    async function sendBotCommand(botId, command, params = {}) {
        if (!botId) { toast('No bot specified', 'error'); return; }

        const payload = {
            botId: botId,
            server: server(),
            command,
            ...params
        };

        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/bots/commands`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await res.json();
            if (data.error) {
                toast(`Command failed: ${data.error}`, 'error');
            } else {
                toast(`Command sent: ${command}`, 'success');
                refreshBotCommandHistory(botId);
            }
        } catch (err) {
            toast('Failed to send command', 'error');
        }
    }

    function openBotInteractDialog(botId, obj) {
        const dialog = $('botInteractDialog');
        if (!dialog) return;

        $('botInteractTarget').textContent = (obj.name || 'unknown').split('/').pop();
        $('botInteractGobId').value = obj.gobId || obj.gob_id || '';
        $('botInteractBotId').value = botId;
        $('botInteractMenuOption').value = '';
        dialog.showModal();
    }

    function initBotInteractDialog() {
        const dialog = $('botInteractDialog');
        if (!dialog) return;

        $('botInteractCancel')?.addEventListener('click', () => dialog.close());
        $('botInteractSubmit')?.addEventListener('click', () => {
            const gobId = parseInt($('botInteractGobId').value, 10);
            const botId = $('botInteractBotId').value;
            const menuOption = $('botInteractMenuOption').value || '';

            if (!gobId || !botId) { toast('Missing data', 'error'); return; }

            sendBotCommand(botId, 'interact', {
                gobId: gobId,
                menuOption: menuOption || undefined,
                targetX: 0,
                targetY: 0
            });
            dialog.close();
        });
    }

    function initBotPanel() {
        // Tracking master toggle
        const chkTracking = $('chkTrackingEnabled');
        if (chkTracking) {
            chkTracking.checked = trackingEnabled;
            chkTracking.addEventListener('change', () => {
                trackingEnabled = chkTracking.checked;
                const badge = $('botTrackingBadge');
                if (badge) badge.textContent = trackingEnabled ? 'ON' : 'OFF';
                if (badge) badge.className = `panel-badge ${trackingEnabled ? 'tracking-on' : 'tracking-off'}`;
                if (trackingEnabled) {
                    // Resuming — fetch fresh data immediately
                    mapRenderer?.loadBots(server());
                    setTimeout(refreshBotList, 500);
                    toast('Bot tracking enabled', 'success');
                } else {
                    toast('Bot tracking paused', 'info');
                }
            });
        }

        // Delegated event handler for all bot card actions
        $('botList')?.addEventListener('click', (e) => {
            // Toggle expand
            const header = e.target.closest('.bot-card-header');
            const actionBtn = e.target.closest('[data-action]');
            const cmdBtn = e.target.closest('[data-cmd]');

            if (cmdBtn) {
                e.stopPropagation();
                const botId = cmdBtn.dataset.botId;
                const cmd = cmdBtn.dataset.cmd;
                if (cmd === 'walk') {
                    botWalkMode = true;
                    botWalkTarget = botId;
                    const viewport = $('mapViewport');
                    if (viewport) viewport.classList.add('tool-bot-walk');
                    toast('Click on the map to set walk destination', 'info');
                } else {
                    sendBotCommand(botId, cmd);
                }
                return;
            }

            if (actionBtn) {
                e.stopPropagation();
                const action = actionBtn.dataset.action;
                const botId = actionBtn.dataset.botId;
                if (action === 'goto-bot') {
                    const bot = (mapRenderer?.bots || []).find(b => b.botId === botId);
                    if (bot) mapRenderer.panTo(bot.x, bot.y);
                } else if (action === 'toggle-bot') {
                    toggleBotCard(botId);
                }
                return;
            }

            if (header) {
                const card = header.closest('.bot-card');
                if (card) toggleBotCard(card.dataset.botId);
            }
        });

        // Bot walk mode — intercept map clicks
        const canvas = $('mapCanvas');
        if (canvas) {
            canvas.addEventListener('click', (e) => {
                if (!botWalkMode || !botWalkTarget) return;
                botWalkMode = false;
                const viewport = $('mapViewport');
                if (viewport) viewport.classList.remove('tool-bot-walk');

                const world = mapRenderer.screenToWorld(e.clientX, e.clientY);
                sendBotCommand(botWalkTarget, 'walk', { targetX: world.x, targetY: world.y });
                botWalkTarget = null;
            });
        }
    }

    function initBotWebSocket() {
        MoonflowerWS.on('bot:position', (data) => {
            if (!trackingEnabled || !mapRenderer || !data) return;
            mapRenderer.updateBot(data);
            refreshBotList();
        });

        MoonflowerWS.on('bot:status', (data) => {
            if (!trackingEnabled || !mapRenderer || !data) return;
            mapRenderer.updateBot(data);
            refreshBotList();
            if (data.status === 'offline') {
                addActivityItem('tile', `Bot "${data.name || data.botId}" went offline`, '');
            }
        });

        MoonflowerWS.on('bot:nearby', (data) => {
            if (!trackingEnabled || !mapRenderer || !data) return;
            if (expandedBots.has(data.botId)) {
                refreshBotNearbyList(data.botId);
            }
        });

        MoonflowerWS.on('bot:command', (data) => {
            if (!trackingEnabled || !mapRenderer || !data) return;
            if (expandedBots.has(data.botId)) {
                refreshBotCommandHistory(data.botId);
            }
            if (data.status === 'completed') {
                addActivityItem('tile', `Command "${data.command}" completed on ${data.name || data.botId}`, '');
            } else if (data.status === 'failed') {
                addActivityItem('tile', `Command "${data.command}" failed on ${data.name || data.botId}`, '');
            }
        });

        // Periodic bot list refresh (catch offline status)
        setInterval(() => {
            if (trackingEnabled && mapRenderer) {
                mapRenderer.loadBots(server());
                setTimeout(refreshBotList, 1000);
            }
        }, 15000);
    }

    // ── Stitch / Clear Actions ──────────────────────────────────────────

    function initActions() {
        $('mapBtnStitch')?.addEventListener('click', async () => {
            try {
                await fetch(`${MoonflowerConfig.serverUrl}/api/stitch`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ server: server() })
                });
                toast('Stitch started', 'info');
            } catch (err) {
                toast('Stitch failed', 'error');
            }
        });

        $('mapBtnClear')?.addEventListener('click', async () => {
            if (!confirm('Clear ALL tiles? This cannot be undone.')) return;
            try {
                await fetch(`${MoonflowerConfig.serverUrl}/api/tiles/clear`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ server: server() })
                });
                MapRenderer.imageCache.clear();
                if (mapRenderer) {
                    mapRenderer.tiles = [];
                    mapRenderer.queueRender();
                }
                updateStats();
                updateEmptyState();
                toast('All tiles cleared', 'info');
            } catch (err) {
                toast('Clear failed', 'error');
            }
        });

        // Origin controls
        $('mapBtnSetOrigin')?.addEventListener('click', async () => {
            const ox = parseInt($('originXInput')?.value) || 0;
            const oy = parseInt($('originYInput')?.value) || 0;
            try {
                await fetch(`${MoonflowerConfig.serverUrl}/api/origin`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ server: server(), origin_x: ox, origin_y: oy, label: 'User' })
                });
                const display = $('mapOriginDisplay');
                if (display) display.textContent = `${ox}, ${oy}`;
                toast('Origin updated', 'info');
            } catch (err) {
                toast('Failed to set origin', 'error');
            }
        });

        // Load current origin
        loadOrigin();
    }

    async function loadOrigin() {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/origin?server=${encodeURIComponent(server())}`);
            const data = await res.json();
            const display = $('mapOriginDisplay');
            if (display) display.textContent = `${data.origin_x}, ${data.origin_y}`;
            if ($('originXInput')) $('originXInput').value = data.origin_x;
            if ($('originYInput')) $('originYInput').value = data.origin_y;
        } catch (err) {
            // Non-critical
        }
    }

    // ── Activity Feed ───────────────────────────────────────────────────

    function addActivityItem(type, text, coords) {
        const feed = $('mapActivityFeed');
        if (!feed) return;

        const empty = feed.querySelector('.list-empty');
        if (empty) empty.remove();

        const el = document.createElement('div');
        el.className = 'activity-item';
        el.innerHTML = `
            <span class="activity-badge ${type}">${getBadgeIcon(type)}</span>
            <span class="activity-text">${escHtml(text)}${coords ? ` (${coords})` : ''}</span>
            <span class="activity-time">${new Date().toLocaleTimeString()}</span>
        `;
        feed.insertBefore(el, feed.firstChild);
        while (feed.children.length > 50) feed.removeChild(feed.lastChild);
    }

    function getBadgeIcon(type) {
        const icons = {
            tile: '◻', live: '↑', stitch: '⊞', marker: '◉',
            icon: '◆', settlement: '⛏', note: '📝', layer: '▤'
        };
        return icons[type] || '•';
    }

    // ── WebSocket Events (map-specific) ─────────────────────────────────

    /* Debounce tile reloads during bulk uploads */
    let _tileReloadTimer = null;
    let _pendingTileCount = 0;
    function debouncedTileReload() {
        _pendingTileCount++;
        if (_tileReloadTimer) clearTimeout(_tileReloadTimer);
        _tileReloadTimer = setTimeout(() => {
            console.log(`[MapPage] Loading tiles (${_pendingTileCount} updates batched)`);
            mapRenderer?.loadTiles(server());
            updateStats();
            updateEmptyState();
            _pendingTileCount = 0;
            _tileReloadTimer = null;
        }, 500); /* Wait 500ms for more events before reloading */
    }

    function initMapWebSocket() {
        MoonflowerWS.on('connected', () => updateConnectionBadge(true));
        MoonflowerWS.on('disconnected', () => updateConnectionBadge(false));

        MoonflowerWS.on('tile:update', (tile) => {
            addActivityItem('tile', 'Tile updated', `${tile?.x}, ${tile?.y}`);
            debouncedTileReload();
        });

        MoonflowerWS.on('tile:live', (tile) => {
            addActivityItem('live', 'Live tile', `${tile?.x}, ${tile?.y}`);
            debouncedTileReload();
        });

        MoonflowerWS.on('tile:activity', (data) => {
            if (data?.x !== undefined) {
                addActivityItem('tile', data.action || 'activity', `${data.x}, ${data.y}`);
            }
        });

        MoonflowerWS.on('stitch:complete', (data) => {
            addActivityItem('stitch', `Stitch complete — ${data?.tile_count || '?'} tiles`, '');
            toast('Map stitch complete', 'success');
        });

        MoonflowerWS.on('marker:create', () => {
            addActivityItem('marker', 'Marker added', '');
            mapRenderer?.loadMarkers(server());
            refreshMarkerList();
            updateStats();
        });

        MoonflowerWS.on('marker:delete', () => {
            mapRenderer?.loadMarkers(server());
            refreshMarkerList();
            updateStats();
        });

        // Game icon events
        MoonflowerWS.on('game-icon:create', (data) => {
            addActivityItem('icon', 'Game icon detected', `${data?.x}, ${data?.y}`);
            mapRenderer?.loadGameIcons(server());
            refreshIconList();
            updateStats();
        });

        MoonflowerWS.on('game-icon:update', (data) => {
            addActivityItem('icon', 'Game icon updated', `${data?.x}, ${data?.y}`);
            mapRenderer?.loadGameIcons(server());
            refreshIconList();
            updateStats();
        });

        MoonflowerWS.on('game-icon:bulk', (data) => {
            addActivityItem('icon', `${data?.count || 0} game icons synced`, '');
            mapRenderer?.loadGameIcons(server());
            refreshIconList();
            updateStats();
        });

        MoonflowerWS.on('game-icon:delete', () => {
            mapRenderer?.loadGameIcons(server());
            refreshIconList();
            updateStats();
        });

        // Settlement events
        MoonflowerWS.on('settlement:update', (data) => {
            addActivityItem('settlement', `Settlement: ${data?.name || 'detected'}`, `${data?.x}, ${data?.y}`);
            mapRenderer?.loadSettlements(server());
            refreshSettlementList();
            updateStats();
        });

        MoonflowerWS.on('settlement:delete', () => {
            mapRenderer?.loadSettlements(server());
            refreshSettlementList();
            updateStats();
        });

        // Note events
        MoonflowerWS.on('note:create', () => {
            addActivityItem('note', 'Note added', '');
            mapRenderer?.loadNotes(server());
            refreshNoteList();
            updateStats();
        });

        MoonflowerWS.on('note:update', () => {
            mapRenderer?.loadNotes(server());
            refreshNoteList();
        });

        MoonflowerWS.on('note:delete', () => {
            mapRenderer?.loadNotes(server());
            refreshNoteList();
            updateStats();
        });

        // Layer change from plugin
        MoonflowerWS.on('layer:change', (data) => {
            if (data?.layer !== undefined && mapRenderer) {
                addActivityItem('layer', `Layer changed to ${mapRenderer.getLayerName(data.layer)}`, '');
                mapRenderer.setLayer(data.layer);
                const sel = $('layerSelect');
                if (sel) sel.value = data.layer;
            }
        });

        // Update badge to current state
        updateConnectionBadge(MoonflowerWS.connected);
    }

    function updateConnectionBadge(connected) {
        const navDot = $('navConnDot');
        const navText = $('navConnText');
        if (navDot) navDot.classList.toggle('connected', connected);
        if (navText) navText.textContent = connected ? 'Live' : 'Offline';
    }

    // ── Clear activity button ───────────────────────────────────────────

    function initActivityClear() {
        $('btnClearActivity')?.addEventListener('click', () => {
            const feed = $('mapActivityFeed');
            if (feed) feed.innerHTML = '<div class="list-empty">Waiting for activity...</div>';
        });
    }

    // ── Main Init (called by app.js on first Map tab activation) ────────

    function initMapPage() {
        if (initialized) return;
        initialized = true;

        initMap();
        initPanelTabs();
        initToolSelection();
        initVisibilityToggles();
        initZoomControls();
        initLayerSelector();
        initMarkerDialog();
        initNoteDialog();
        initScreenshots();
        initExport();
        initListActions();
        initActions();
        initActivityClear();
        initMapWebSocket();
        initBotPanel();
        initBotInteractDialog();
        initBotWebSocket();

        MoonflowerMarkers.loadCategories();

        updateStats();

        // Periodic refresh
        setInterval(() => {
            updateStats();
            refreshAllLists();
        }, 30000);

        // Initial list population after data loads
        setTimeout(() => {
            refreshAllLists();
        }, 3000);
    }

    // Export init function for app.js to call
    window._initMapPage = initMapPage;

})();
