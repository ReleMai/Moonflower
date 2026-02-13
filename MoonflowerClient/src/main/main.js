// =============================================================================
// Moonflower Client - Electron Main Process
// =============================================================================
// Handles game launching, window creation, IPC, settings, and tile collection.
// =============================================================================

const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');
const Store = require('electron-store');

const store = new Store({
    defaults: {
        havenPath: 'C:\\Program Files (x86)\\Steam\\steamapps\\common\\Haven',
        javaPath: '',
        cartographerUrl: 'http://127.0.0.1:3300',
        server: 'game.havenandhearth.com',
        memory: 2048,
        tileCollection: false
    }
});

let mainWindow = null;
let gameProcess = null;
let gameState = 'idle'; // idle, launching, running, error
const debugLog = [];

function log(msg) {
    const entry = `[${new Date().toISOString()}] ${msg}`;
    debugLog.push(entry);
    console.log(entry);
}

// =============================================================================
// Window Creation
// =============================================================================

function createMainWindow() {
    const isDev = process.argv.includes('--dev');

    mainWindow = new BrowserWindow({
        width: 1200,
        height: 800,
        minWidth: 900,
        minHeight: 600,
        frame: false,
        backgroundColor: '#0a0d0c',
        webPreferences: {
            preload: path.join(__dirname, '../preload/preload.js'),
            nodeIntegration: false,
            contextIsolation: true,
            sandbox: false
        }
    });

    mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'));

    if (isDev) {
        mainWindow.webContents.openDevTools();
    }

    mainWindow.on('closed', () => {
        mainWindow = null;
    });

    log('Main window created');
}

// =============================================================================
// Game Launching
// =============================================================================

function detectJavaPath() {
    const havenPath = store.get('havenPath');
    const bundledJava = path.join(havenPath, 'jre', 'bin', 'javaw.exe');

    if (fs.existsSync(bundledJava)) {
        return bundledJava;
    }

    // Fallback: check system Java
    const systemJava = 'javaw';
    return systemJava;
}

function detectPaths() {
    const havenPath = store.get('havenPath');
    const results = {
        havenPath: { path: havenPath, exists: fs.existsSync(havenPath) },
        javaPath: { path: '', exists: false },
        launcherJar: { path: '', exists: false }
    };

    // Java
    const javaPath = detectJavaPath();
    results.javaPath.path = javaPath;
    results.javaPath.exists = fs.existsSync(javaPath);

    // Launcher JAR
    const launcherJar = path.join(havenPath, 'launcher.jar');
    results.launcherJar.path = launcherJar;
    results.launcherJar.exists = fs.existsSync(launcherJar);

    return results;
}

async function launchGame() {
    if (gameProcess) {
        log('Game already running');
        return { success: false, error: 'Game already running' };
    }

    const settings = store.store;
    const javaPath = settings.javaPath || detectJavaPath();
    const havenPath = settings.havenPath;
    const launcherJar = path.join(havenPath, 'launcher.jar');

    if (!fs.existsSync(javaPath)) {
        log('Java not found: ' + javaPath);
        setGameState('error');
        return { success: false, error: 'Java not found at: ' + javaPath };
    }

    if (!fs.existsSync(launcherJar)) {
        log('Launcher JAR not found: ' + launcherJar);
        setGameState('error');
        return { success: false, error: 'launcher.jar not found at: ' + launcherJar };
    }

    setGameState('launching');

    // Haven game client launch format:
    //   java -jar launcher.jar -U https://<server>/hres/ <server>
    //   -U specifies the resource URL for downloading game assets
    //   Final argument is the server hostname to connect to
    const server = settings.server || 'game.havenandhearth.com';

    const args = [
        `-Xms${settings.memory}m`,
        `-Xmx${settings.memory}m`,
        '-jar', launcherJar,
        '-U', `https://${server}/hres/`,
        server
    ];

    log(`Launching: ${javaPath} ${args.join(' ')}`);

    try {
        gameProcess = spawn(javaPath, args, {
            cwd: havenPath,
            detached: true,
            stdio: ['ignore', 'pipe', 'pipe']
        });

        gameProcess.stdout.on('data', (data) => {
            log(`[Game STDOUT] ${data.toString().trim()}`);
        });

        gameProcess.stderr.on('data', (data) => {
            log(`[Game STDERR] ${data.toString().trim()}`);
        });

        gameProcess.on('spawn', () => {
            log('Game process spawned (PID: ' + gameProcess.pid + ')');
            setGameState('running');
        });

        gameProcess.on('exit', (code) => {
            log(`Game exited with code ${code}`);
            gameProcess = null;
            setGameState('idle');
        });

        gameProcess.on('error', (err) => {
            log('Game launch error: ' + err.message);
            gameProcess = null;
            setGameState('error');
        });

        return { success: true, pid: gameProcess.pid };
    } catch (err) {
        log('Launch exception: ' + err.message);
        setGameState('error');
        return { success: false, error: err.message };
    }
}

function killGame() {
    if (gameProcess) {
        try {
            process.kill(gameProcess.pid);
            log('Game process killed');
        } catch (err) {
            log('Kill error: ' + err.message);
        }
        gameProcess = null;
        setGameState('idle');
    }
}

function setGameState(state) {
    gameState = state;
    if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('game-state-change', state);
    }
}

// =============================================================================
// IPC Handlers
// =============================================================================

function setupIPC() {
    // Game control
    ipcMain.handle('launch-game', () => launchGame());
    ipcMain.handle('kill-game', () => { killGame(); return true; });
    ipcMain.handle('is-game-running', () => gameProcess !== null);
    ipcMain.handle('get-game-state', () => gameState);

    // Settings
    ipcMain.handle('get-settings', () => store.store);
    ipcMain.handle('set-settings', (event, settings) => {
        for (const [key, value] of Object.entries(settings)) {
            store.set(key, value);
        }
        return true;
    });

    // File browsing
    ipcMain.handle('browse-directory', async () => {
        const result = await dialog.showOpenDialog(mainWindow, {
            properties: ['openDirectory']
        });
        return result.canceled ? null : result.filePaths[0];
    });

    ipcMain.handle('browse-file', async (event, filters) => {
        const result = await dialog.showOpenDialog(mainWindow, {
            properties: ['openFile'],
            filters: filters || []
        });
        return result.canceled ? null : result.filePaths[0];
    });

    ipcMain.handle('detect-paths', () => detectPaths());

    // Window controls
    ipcMain.on('window-minimize', () => mainWindow?.minimize());
    ipcMain.on('window-maximize', () => {
        if (mainWindow?.isMaximized()) mainWindow.unmaximize();
        else mainWindow?.maximize();
    });
    ipcMain.on('window-close', () => mainWindow?.close());
    ipcMain.handle('open-external', (event, url) => shell.openExternal(url));

    // Tile collection
    ipcMain.handle('set-tile-collection', (event, enabled) => {
        store.set('tileCollection', enabled);
        return true;
    });
    ipcMain.handle('get-tile-collection', () => store.get('tileCollection'));

    // Debug
    ipcMain.handle('get-debug-log', () => debugLog.slice(-200));
}

// =============================================================================
// App Lifecycle
// =============================================================================

app.whenReady().then(() => {
    setupIPC();
    createMainWindow();
    log('Moonflower Client started');
});

app.on('window-all-closed', () => {
    killGame();
    app.quit();
});

app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
        createMainWindow();
    }
});
