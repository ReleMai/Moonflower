// =============================================================================
// Moonflower Client - Preload (Context Bridge)
// =============================================================================
// Exposes safe IPC to the renderer process.
// =============================================================================

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('moonflower', {
    // Game control
    launchGame: () => ipcRenderer.invoke('launch-game'),
    killGame: () => ipcRenderer.invoke('kill-game'),
    isGameRunning: () => ipcRenderer.invoke('is-game-running'),
    getGameState: () => ipcRenderer.invoke('get-game-state'),

    // Settings
    getSettings: () => ipcRenderer.invoke('get-settings'),
    setSettings: (settings) => ipcRenderer.invoke('set-settings', settings),
    browseDirectory: () => ipcRenderer.invoke('browse-directory'),
    browseFile: (filters) => ipcRenderer.invoke('browse-file', filters),
    detectPaths: () => ipcRenderer.invoke('detect-paths'),

    // Window controls
    minimize: () => ipcRenderer.send('window-minimize'),
    maximize: () => ipcRenderer.send('window-maximize'),
    close: () => ipcRenderer.send('window-close'),
    openExternal: (url) => ipcRenderer.invoke('open-external', url),

    // Tile collection
    setTileCollection: (enabled) => ipcRenderer.invoke('set-tile-collection', enabled),
    getTileCollection: () => ipcRenderer.invoke('get-tile-collection'),

    // Events
    onGameStateChange: (callback) => ipcRenderer.on('game-state-change', (e, state) => callback(state)),

    // Debug
    getDebugLog: () => ipcRenderer.invoke('get-debug-log')
});
