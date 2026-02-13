// =============================================================================
// Regions — Client-side region management
// =============================================================================

const MoonflowerRegions = {
    types: [],

    async loadTypes() {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/regions/types`);
            const data = await res.json();
            this.types = data.types || [];
        } catch (err) {
            console.warn('[Regions] Failed to load types:', err);
        }
    },

    async createRegion(server, name, type, color, opacity, points, description) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/regions`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ server, name, type, color, opacity, points, description })
            });
            return await res.json();
        } catch (err) {
            console.error('[Regions] Create failed:', err);
            return null;
        }
    },

    async deleteRegion(id) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/regions/${id}`, {
                method: 'DELETE'
            });
            return await res.json();
        } catch (err) {
            console.error('[Regions] Delete failed:', err);
            return null;
        }
    }
};
