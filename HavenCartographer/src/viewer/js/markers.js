// =============================================================================
// Markers — Client-side marker management
// =============================================================================

const MoonflowerMarkers = {
    categories: [],

    async loadCategories() {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/markers/categories`);
            const data = await res.json();
            this.categories = data.categories || [];
        } catch (err) {
            console.warn('[Markers] Failed to load categories:', err);
        }
    },

    async createMarker(server, x, y, name, category, icon, color, description) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/markers`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ server, x, y, name, category, icon, color, description })
            });
            return await res.json();
        } catch (err) {
            console.error('[Markers] Create failed:', err);
            return null;
        }
    },

    async deleteMarker(id) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/markers/${id}`, {
                method: 'DELETE'
            });
            return await res.json();
        } catch (err) {
            console.error('[Markers] Delete failed:', err);
            return null;
        }
    },

    async updateMarker(id, updates) {
        try {
            const res = await fetch(`${MoonflowerConfig.serverUrl}/api/markers/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(updates)
            });
            return await res.json();
        } catch (err) {
            console.error('[Markers] Update failed:', err);
            return null;
        }
    }
};
