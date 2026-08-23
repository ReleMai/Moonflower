// =============================================================================
// Config — Client-side configuration loader
// =============================================================================

const MoonflowerConfig = {
    serverUrl: window.location.origin,
    wsUrl: `ws://${window.location.host}/ws`,
    defaultServer: 'game.havenandhearth.com',
    tileSize: 100,
    map: {
        defaultZoom: 1,
        minZoom: 0.1,
        maxZoom: 10,
        gridEnabled: true,
        gridColor: 'rgba(255,255,255,0.1)'
    },

    async load() {
        try {
            const res = await fetch(`${this.serverUrl}/api/config`);
            if (res.ok) {
                const data = await res.json();
                this.defaultServer = data.haven?.defaultServer || this.defaultServer;
                this.tileSize = data.haven?.tileSize || this.tileSize;
                Object.assign(this.map, data.map || {});
            }
        } catch (err) {
            console.warn('[Config] Could not load server config:', err.message);
        }
    }
};
