// =============================================================================
// WebSocket — Real-time connection to Haven Cartographer
// =============================================================================

const MoonflowerWS = {
    ws: null,
    connected: false,
    reconnectTimer: null,
    reconnectDelay: 3000,
    maxReconnectDelay: 30000,
    listeners: {},

    connect() {
        if (this.ws && this.ws.readyState <= 1) return; // CONNECTING or OPEN

        try {
            this.ws = new WebSocket(MoonflowerConfig.wsUrl);

            this.ws.onopen = () => {
                console.log('[WS] Connected');
                this.connected = true;
                this.reconnectDelay = 3000;
                this.emit('connected');
                this.updateStatusDot(true);

                // Subscribe to default server
                this.send({ type: 'subscribe', server: MoonflowerConfig.defaultServer });
            };

            this.ws.onmessage = (event) => {
                try {
                    const msg = JSON.parse(event.data);
                    this.emit(msg.type, msg.data, msg);
                } catch (err) {
                    console.warn('[WS] Invalid message:', err);
                }
            };

            this.ws.onclose = () => {
                console.log('[WS] Disconnected');
                this.connected = false;
                this.emit('disconnected');
                this.updateStatusDot(false);
                this.scheduleReconnect();
            };

            this.ws.onerror = (err) => {
                console.error('[WS] Error:', err);
            };
        } catch (err) {
            console.error('[WS] Connection failed:', err);
            this.scheduleReconnect();
        }
    },

    send(data) {
        if (this.ws && this.ws.readyState === 1) {
            this.ws.send(JSON.stringify(data));
        }
    },

    scheduleReconnect() {
        if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
        this.reconnectTimer = setTimeout(() => {
            console.log('[WS] Reconnecting...');
            this.connect();
        }, this.reconnectDelay);
        this.reconnectDelay = Math.min(this.reconnectDelay * 1.5, this.maxReconnectDelay);
    },

    on(event, callback) {
        if (!this.listeners[event]) this.listeners[event] = [];
        this.listeners[event].push(callback);
    },

    off(event, callback) {
        if (!this.listeners[event]) return;
        this.listeners[event] = this.listeners[event].filter(cb => cb !== callback);
    },

    emit(event, ...args) {
        if (this.listeners[event]) {
            for (const cb of this.listeners[event]) {
                try { cb(...args); } catch (err) { console.error(`[WS] Listener error (${event}):`, err); }
            }
        }
    },

    updateStatusDot(connected) {
        // Legacy .status-dot elements (hub page)
        const dots = document.querySelectorAll('.status-dot');
        dots.forEach(dot => dot.classList.toggle('connected', connected));
        const textEl = document.getElementById('serverStatusText');
        if (textEl) textEl.textContent = connected ? 'Connected' : 'Disconnected';

        // New .conn-dot element (map page)
        const connDots = document.querySelectorAll('.conn-dot');
        connDots.forEach(dot => dot.classList.toggle('connected', connected));
        const connText = document.querySelector('.conn-text');
        if (connText) connText.textContent = connected ? 'Live' : 'Offline';
    }
};
