package haven.plugins;

import haven.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;

/**
 * MoonflowerTileSync — Live map tile uploader to HavenCartographer.
 *
 * Watches the Haven tile output folder for new tile_X_Y.png files
 * and uploads them via multipart POST to the Cartographer server.
 *
 * Features:
 *   - Automatic tile upload on creation
 *   - Player position reporting
 *   - Bulk upload of existing tiles
 *   - Server folder selection
 *   - Map management (create, cycle, refresh)
 *   - Layer/mine detection
 *
 * Targets Haven and Hearth (Java 25).
 */
public class MoonflowerTileSync extends Plugin {

    private static UI ui = null;
    private static volatile boolean isWatching = false;
    private static volatile boolean signalToStop = false;
    private static TileSyncWindow syncWindow = null;

    private static String cartographerUrl = "http://127.0.0.1:3300";
    private static volatile int uploadedCount = 0;
    private static volatile int errorCount = 0;
    static volatile int selectedMapId = -1;
    private static volatile String selectedMapName = "Default";

    private static ArrayList<int[]> sharedMapList = new ArrayList<>();
    private static ArrayList<String> sharedMapNames = new ArrayList<>();
    private static volatile int sharedMapIndex = 0;

    private static volatile String customServer = null;
    private static ArrayList<String> availableServers = new ArrayList<>();
    private static volatile int serverIndex = -1;

    // Layer / Mine tracking
    private static volatile int currentLayer = 0;
    private static volatile String currentMineId = null;
    private static volatile int lastSpX = Integer.MIN_VALUE;
    private static volatile int lastSpY = Integer.MIN_VALUE;
    private static volatile int surfaceMapId = -1;

    private static final String[] MINE_GOB_PATTERNS = {
        "gfx/terobjs/minesupport", "gfx/terobjs/ladder",
        "gfx/terobjs/column", "gfx/kritter/bat"
    };

    private static final String[] SURFACE_GOB_PATTERNS = {
        "trees/", "bushes/", "herbs/", "gfx/terobjs/plants",
        "gfx/terobjs/claim", "gfx/terobjs/stockpile"
    };

    private static final Pattern TILE_PATTERN = Pattern.compile("tile_(-?\\d+)_(-?\\d+)\\.png");
    private static final int GRID_CELL_SIZE = 1100;

    static volatile int sessionSpX = Integer.MIN_VALUE;
    static volatile int sessionSpY = Integer.MIN_VALUE;

    // MapFile bulk export
    private static volatile boolean isExporting = false;
    private static volatile int exportTotal = 0;
    private static volatile int exportDone = 0;
    private static volatile int exportErrors = 0;

    // ═══════════════════════════════════════════════════════════════════
    //  Plugin Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void load(UI ui) {
        var glob = ui.sess.glob;
        glob.paginae.add(glob.paginafor(Resource.load("paginae/add/moonflowertilesync")));
        XTendedPaginae.registerPlugin("moonflowertilesync", this);
    }

    @Override
    public void execute(UI ui) {
        MoonflowerTileSync.ui = ui;
        if (syncWindow != null && syncWindow.visible) {
            syncWindow.destroy();
            syncWindow = null;
        } else {
            syncWindow = new TileSyncWindow(ui.gui);
        }
    }

    static void startDirect(UI theUI) {
        if (isWatching) return;
        MoonflowerTileSync.ui = theUI;
        String folder = getMapFolder();
        if (folder == null) {
            msg("Cannot find map folder! Explore first.", Color.RED);
            return;
        }
        isWatching = true;
        signalToStop = false;
        uploadedCount = 0;
        errorCount = 0;
        sessionSpX = Integer.MIN_VALUE;
        sessionSpY = Integer.MIN_VALUE;
        lastSpX = Integer.MIN_VALUE;
        lastSpY = Integer.MIN_VALUE;
        currentLayer = 0;
        currentMineId = null;
        msg("Tile sync started (direct)", Color.GREEN);

        new Thread(() -> watchFolder(folder), "Moonflower-TileSync").start();
        new Thread(() -> positionReportLoop(), "Moonflower-TileSyncPos").start();
    }

    static void stopDirect() {
        signalToStop = true;
        msg("Tile sync stopping...", Color.ORANGE);
    }

    // ── Player Position Reporting ────────────────────────────────────

    private static void positionReportLoop() {
        Coord lastPos = null;
        String clientId = "client-" + Long.toHexString(System.currentTimeMillis());
        try {
            while (!signalToStop && isWatching) {
                Coord pos = null;
                boolean isMoving = false;
                try {
                    if (ui != null && ui.gui != null && ui.gui.map != null) {
                        var p = ui.gui.map.player();
                        if (p != null) {
                            pos = p.rc;
                            isMoving = p.getattr(Moving.class) != null;
                        }
                    }
                } catch (Exception e) { }

                if (sessionSpX == Integer.MIN_VALUE) tryReadSessionOrigin();
                checkLayerChange();

                if (pos != null && sessionSpX != Integer.MIN_VALUE
                        && (lastPos == null || pos.x != lastPos.x || pos.y != lastPos.y)) {
                    String server = getActiveServer();
                    int gridX = Math.floorDiv(pos.x, GRID_CELL_SIZE);
                    int gridY = Math.floorDiv(pos.y, GRID_CELL_SIZE);
                    int tileX = gridX - sessionSpX;
                    int tileY = gridY - sessionSpY;
                    double fracX = (double) Math.floorMod(pos.x, GRID_CELL_SIZE) / GRID_CELL_SIZE;
                    double fracY = (double) Math.floorMod(pos.y, GRID_CELL_SIZE) / GRID_CELL_SIZE;

                    String json = """
                        {"botId":"%s","type":"mapper","x":%d,"y":%d,"tileX":%d,"tileY":%d,\
                        "fracX":%.4f,"fracY":%.4f,"moving":%b,"layer":%d%s,\
                        "server":"%s","timestamp":%d}"""
                        .formatted(clientId, pos.x, pos.y, tileX, tileY, fracX, fracY,
                            isMoving, currentLayer,
                            currentMineId != null ? ",\"mineId\":\"" + currentMineId + "\"" : "",
                            server, System.currentTimeMillis());
                    postPosition(json);
                    lastPos = pos;
                }
                sleep(2000);
            }
            postPosition("{\"botId\":\"" + clientId + "\",\"status\":\"offline\"}");
        } catch (Exception e) { }
    }

    private static void tryReadSessionOrigin() {
        try {
            if (ui == null || ui.gui == null) return;
            var mmap = findWidgetByClass(ui.gui, "haven.LocalMiniMap");
            if (mmap == null) return;
            Field spField = mmap.getClass().getDeclaredField("sp");
            spField.setAccessible(true);
            var spObj = spField.get(mmap);
            if (spObj instanceof Coord sp) {
                sessionSpX = sp.x;
                sessionSpY = sp.y;
            }
        } catch (Exception e) { }
    }

    static void calibrateFromTile(int tileX, int tileY) {
        if (sessionSpX != Integer.MIN_VALUE) return;
        try {
            if (ui != null && ui.gui != null && ui.gui.map != null) {
                var player = ui.gui.map.player();
                if (player != null) {
                    sessionSpX = Math.floorDiv(player.rc.x, GRID_CELL_SIZE) - tileX;
                    sessionSpY = Math.floorDiv(player.rc.y, GRID_CELL_SIZE) - tileY;
                }
            }
        } catch (Exception e) { }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Layer / Mine Detection
    // ═══════════════════════════════════════════════════════════════════

    private static void checkLayerChange() {
        int spx = sessionSpX;
        int spy = sessionSpY;
        if (spx == Integer.MIN_VALUE) return;
        if (lastSpX == Integer.MIN_VALUE) {
            lastSpX = spx; lastSpY = spy; return;
        }
        if (spx == lastSpX && spy == lastSpY) return;

        int oldSpX = lastSpX, oldSpY = lastSpY;
        lastSpX = spx; lastSpY = spy;
        msg("Session origin changed: (" + oldSpX + "," + oldSpY + ") -> (" + spx + "," + spy + ")", Color.CYAN);
        sleep(1500);

        boolean inMine = detectMineEnvironment();
        if (inMine) {
            if (currentLayer == 0) surfaceMapId = selectedMapId;
            currentLayer = 1;
            currentMineId = "sp_" + spx + "_" + spy;
            msg("Underground detected! Mine: " + currentMineId, Color.ORANGE);
            registerMineWithServer();
        } else {
            if (currentLayer > 0) {
                msg("Surface detected — returning from mine.", Color.GREEN);
                if (surfaceMapId >= 0) {
                    selectedMapId = surfaceMapId;
                    selectedMapName = "(Surface Default)";
                }
            }
            currentLayer = 0;
            currentMineId = null;
        }
    }

    private static boolean detectMineEnvironment() {
        try {
            if (ui == null || ui.gui == null || ui.gui.map == null) return false;
            var player = ui.gui.map.player();
            if (player == null) return false;
            var pp = player.rc;
            Collection<Gob> gobs;
            try { gobs = ui.sess.glob.oc.getGobs(); } catch (Exception e) { return false; }
            if (gobs == null) return false;

            boolean foundMine = false, foundSurface = false;
            double checkDist = 30.0 * 11.0;
            for (var gob : gobs) {
                String name = getGobResName(gob);
                if (name.isEmpty() || gob.rc.dist(pp) > checkDist) continue;
                for (String pat : MINE_GOB_PATTERNS) if (name.contains(pat)) { foundMine = true; break; }
                for (String pat : SURFACE_GOB_PATTERNS) if (name.contains(pat)) { foundSurface = true; break; }
            }
            return foundMine && !foundSurface;
        } catch (Exception e) { return false; }
    }

    private static void registerMineWithServer() {
        HttpURLConnection conn = null;
        try {
            String server = getActiveServer();
            String body = "{\"server\":\"" + server + "\",\"mine_id\":\"" + currentMineId + "\",\"layer\":1}";
            var url = new URL(cartographerUrl + "/api/mines/enter");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/json");
            try (var os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }
            int code = conn.getResponseCode();
            if (code == 200 || code == 201) {
                String resp;
                try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    var sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    resp = sb.toString();
                }
                int mapId = extractIntField(resp, "map_id");
                String mapName = extractStringField(resp, "name");
                if (mapId > 0) {
                    selectedMapId = mapId;
                    selectedMapName = mapName != null ? mapName : ("Mine " + currentMineId);
                    msg("Mine map: " + selectedMapName + " (id=" + mapId + ")", Color.GREEN);
                    if (syncWindow != null && syncWindow.lblMap != null) syncWindow.lblMap.settext(selectedMapName);
                }
            } else {
                msg("Failed to register mine: HTTP " + code, Color.YELLOW);
            }
        } catch (Exception e) {
            msg("Mine register error: " + e.getMessage(), Color.YELLOW);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String getGobResName(Gob gob) {
        try {
            var rd = gob.getattr(ResDrawable.class);
            if (rd != null && rd.res != null) return rd.res.get().name;
        } catch (Exception e) { }
        try {
            var cmp = gob.getattr(Composite.class);
            if (cmp != null && cmp.base != null) return cmp.base.get().name;
        } catch (Exception e) { }
        return "";
    }

    private static Widget findWidgetByClass(Widget root, String className) {
        for (Widget w = root.child; w != null; w = w.next) {
            if (w.getClass().getName().equals(className)) return w;
            var found = findWidgetByClass(w, className);
            if (found != null) return found;
        }
        return null;
    }

    private static void postPosition(final String json) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                var url = new URL(cartographerUrl + "/api/bot/position");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("Content-Type", "application/json");
                try (var os = conn.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                conn.getResponseCode();
            } catch (Exception e) { }
            finally { if (conn != null) conn.disconnect(); }
        }, "MF-PosReport").start();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Sync Window
    // ═══════════════════════════════════════════════════════════════════

    static class TileSyncWindow extends Window {
        private Button btnSync;
        private Label lblStatus, lblUploaded, lblErrors, lblFolder, lblMap, lblServer, lblExport;

        TileSyncWindow(Widget parent) {
            super(new Coord(300, 200), new Coord(280, 340), parent, "Moonflower Tile Sync");
            this.justclose = true;
            scanAvailableServers();
            int y = 0;

            lblStatus = new Label(new Coord(0, y), this, isWatching ? "SYNCING" : "Idle");
            y += 22;

            new Label(new Coord(0, y), this, "Server:");
            String serverDisplay = (customServer != null) ? customServer : "Auto (" + getActiveServer() + ")";
            lblServer = new Label(new Coord(50, y), this, serverDisplay);
            lblServer.setcolor(new Color(255, 200, 80));
            y += 18;

            new Button(new Coord(0, y), 80, this, "< Prev") {
                public void click() { cycleServers(-1); }
            };
            new Button(new Coord(85, y), 80, this, "Next >") {
                public void click() { cycleServers(1); }
            };
            new Button(new Coord(170, y), 65, this, "Scan") {
                public void click() {
                    scanAvailableServers();
                    updateServerLabel();
                    msg("Scanned: " + availableServers.size() + " server folder(s)", Color.CYAN);
                }
            };
            y += 26;

            String mapFolder = getMapFolder();
            lblFolder = new Label(new Coord(0, y), this,
                "Folder: " + (mapFolder != null ? ".../" + new File(mapFolder).getName() : "N/A"));
            y += 20;

            new Label(new Coord(0, y), this, "Map:");
            lblMap = new Label(new Coord(32, y), this, selectedMapName);
            lblMap.setcolor(new Color(100, 200, 255));
            y += 18;

            new Button(new Coord(0, y), 90, this, "< Prev Map") {
                public void click() { cycleMaps(-1); }
            };
            new Button(new Coord(100, y), 90, this, "Next Map >") {
                public void click() { cycleMaps(1); }
            };
            y += 26;

            new Button(new Coord(0, y), 90, this, "Refresh") {
                public void click() { new Thread(() -> refreshMaps(), "MF-FetchMaps").start(); }
            };
            new Button(new Coord(100, y), 90, this, "New Map") {
                public void click() { new Thread(() -> createMapDirect(), "MF-CreateMap").start(); }
            };
            y += 28;

            lblUploaded = new Label(new Coord(0, y), this, "Uploaded: " + uploadedCount);
            y += 16;
            lblErrors = new Label(new Coord(0, y), this, "Errors: " + errorCount);
            y += 24;

            btnSync = new Button(new Coord(0, y), 200, this, isWatching ? "Stop Sync" : "Start Live Sync") {
                public void click() { if (!isWatching) startSync(); else stopSync(); }
            };
            y += 30;

            new Button(new Coord(0, y), 200, this, "Upload All Existing Tiles") {
                public void click() { uploadExistingTiles(); }
            };
            y += 30;

            new Button(new Coord(0, y), 200, this, "Export Explored Map") {
                public void click() {
                    new Thread(() -> bulkExportFromMapFile(), "MF-MapExport-Init").start();
                }
            };
            y += 22;

            lblExport = new Label(new Coord(0, y), this, isExporting ? "Exporting..." : "");
            y += 18;

            new Label(new Coord(0, y), this, "Cartographer: " + cartographerUrl);
            this.pack();

            new Thread(() -> refreshMaps(), "MF-FetchMaps").start();
        }

        private void startSync() {
            if (isWatching) return;
            String folder = getMapFolder();
            if (folder == null) { msg("Cannot find map folder! Explore first.", Color.RED); return; }
            isWatching = true; signalToStop = false; uploadedCount = 0; errorCount = 0;
            sessionSpX = Integer.MIN_VALUE; sessionSpY = Integer.MIN_VALUE;
            btnSync.change("Stop Sync");
            lblStatus.settext("SYNCING"); lblStatus.setcolor(Color.GREEN);
            msg("Tile sync started! Exploring will upload tiles.", Color.GREEN);
            new Thread(() -> watchFolder(folder), "Moonflower-TileSync").start();
        }

        private void stopSync() {
            signalToStop = true;
            btnSync.change("Start Live Sync");
            lblStatus.settext("Stopping..."); lblStatus.setcolor(Color.ORANGE);
            msg("Tile sync stopping...", Color.ORANGE);
        }

        void updateCounts() {
            try {
                lblUploaded.settext("Uploaded: " + uploadedCount);
                lblErrors.settext("Errors: " + errorCount);
                if (lblExport != null) {
                    if (isExporting)
                        lblExport.settext("Export: " + exportDone + "/" + exportTotal);
                    else if (exportDone > 0)
                        lblExport.settext("Export done: " + exportDone + " tiles");
                    else
                        lblExport.settext("");
                }
            } catch (Exception e) { }
        }

        void onSyncStopped() {
            try {
                btnSync.change("Start Live Sync");
                lblStatus.settext("Idle"); lblStatus.setcolor(Color.WHITE);
            } catch (Exception e) { }
        }

        void updateServerLabel() {
            try {
                if (lblServer == null) return;
                String display = (customServer != null) ? customServer : "Auto (" + getActiveServer() + ")";
                lblServer.settext(display);
            } catch (Exception e) { }
        }

        void updateFolderLabel() {
            try {
                if (lblFolder == null) return;
                String mf = getMapFolder();
                lblFolder.settext("Folder: " + (mf != null ? ".../" + new File(mf).getName() : "N/A"));
            } catch (Exception e) { }
        }

        public void destroy() {
            if (isWatching) signalToStop = true;
            syncWindow = null;
            super.destroy();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Folder Watching
    // ═══════════════════════════════════════════════════════════════════

    private static void watchFolder(String folderPath) {
        try {
            var dir = Paths.get(folderPath);
            var watcher = FileSystems.getDefault().newWatchService();
            dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);

            // Also watch subdirectories
            File[] subDirs = dir.toFile().listFiles(File::isDirectory);
            if (subDirs != null) {
                for (var sub : subDirs) {
                    sub.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
                }
            }
            msg("Watching for new tiles in: " + folderPath, Color.CYAN);

            while (!signalToStop) {
                WatchKey key;
                try { key = watcher.poll(500, TimeUnit.MILLISECONDS); }
                catch (InterruptedException e) { break; }
                if (key == null) continue;

                var watchedDir = (Path) key.watchable();
                for (var event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                    var fileName = (Path) event.context();
                    String name = fileName.toString();
                    var newFile = watchedDir.resolve(fileName).toFile();

                    if (newFile.isDirectory()) {
                        try {
                            newFile.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
                            msg("Watching new session folder: " + name, Color.CYAN);
                        } catch (Exception e) { }
                        continue;
                    }

                    var m = TILE_PATTERN.matcher(name);
                    if (m.matches()) {
                        int x = Integer.parseInt(m.group(1));
                        int y = Integer.parseInt(m.group(2));
                        calibrateFromTile(x, y);
                        sleep(200);
                        if (newFile.exists() && newFile.length() > 0) {
                            String session = watchedDir.getFileName().toString();
                            uploadTile(newFile, x, y, session);
                        }
                    }
                }
                if (!key.reset()) break;
            }
            watcher.close();
        } catch (Exception e) {
            msg("Watch error: " + e.getMessage(), Color.RED);
        } finally {
            isWatching = false; signalToStop = false;
            if (syncWindow != null) syncWindow.onSyncStopped();
            msg("Tile sync stopped. Uploaded: " + uploadedCount + ", Errors: " + errorCount, Color.YELLOW);
            playNotifySound();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Tile Upload
    // ═══════════════════════════════════════════════════════════════════

    private static void uploadTile(File tileFile, int x, int y, String session) {
        try {
            String server = getActiveServer();
            byte[] imageData;
            try (var fis = new FileInputStream(tileFile)) {
                imageData = fis.readAllBytes();
            }
            if (imageData.length == 0) return;

            String boundary = "----MoonflowerBoundary" + System.currentTimeMillis();
            var url = new URL(cartographerUrl + "/api/tiles/live");
            var conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (var os = conn.getOutputStream();
                 var writer = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true)) {
                writeField(writer, boundary, "server", server);
                writeField(writer, boundary, "session", session);
                writeField(writer, boundary, "x", String.valueOf(x));
                writeField(writer, boundary, "y", String.valueOf(y));
                if (selectedMapId >= 0) writeField(writer, boundary, "map_id", String.valueOf(selectedMapId));
                writeField(writer, boundary, "layer", String.valueOf(currentLayer));
                if (currentMineId != null) writeField(writer, boundary, "mine_id", currentMineId);

                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"tile\"; filename=\"")
                      .append(tileFile.getName()).append("\"\r\n");
                writer.append("Content-Type: image/png\r\n\r\n");
                writer.flush();
                os.write(imageData);
                os.flush();
                writer.append("\r\n").append("--").append(boundary).append("--\r\n");
                writer.flush();
            }

            int code = conn.getResponseCode();
            conn.disconnect();
            if (code == 200 || code == 201) {
                uploadedCount++;
            } else {
                errorCount++;
            }
            if (syncWindow != null) syncWindow.updateCounts();
        } catch (Exception e) {
            errorCount++;
            if (syncWindow != null) syncWindow.updateCounts();
        }
    }

    private static void writeField(PrintWriter writer, String boundary, String name, String value) {
        writer.append("--").append(boundary).append("\r\n")
              .append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
              .append(value).append("\r\n");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Upload Existing Tiles
    // ═══════════════════════════════════════════════════════════════════

    private static void uploadExistingTiles() {
        String folder = getMapFolder();
        if (folder == null) { msg("No map folder found!", Color.RED); return; }

        var mapDir = new File(folder);
        File[] sessions = mapDir.listFiles(File::isDirectory);
        int totalTiles = 0;
        if (sessions != null) {
            for (var sessionDir : sessions) {
                File[] tiles = sessionDir.listFiles();
                if (tiles != null) {
                    for (var tile : tiles) {
                        if (TILE_PATTERN.matcher(tile.getName()).matches()) totalTiles++;
                    }
                }
            }
        }
        if (totalTiles == 0) { msg("No existing tiles found to upload.", Color.YELLOW); return; }
        msg("Found " + totalTiles + " tiles. Uploading...", Color.CYAN);

        new Thread(() -> {
            int count = 0;
            var md = new File(folder);
            File[] sess = md.listFiles(File::isDirectory);
            if (sess == null) return;
            for (var sessionDir : sess) {
                File[] tiles = sessionDir.listFiles();
                if (tiles == null) continue;
                String session = sessionDir.getName();
                for (var tile : tiles) {
                    if (signalToStop) return;
                    var m = TILE_PATTERN.matcher(tile.getName());
                    if (m.matches()) {
                        uploadTile(tile, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), session);
                        count++;
                        if (count % 50 == 0) msg("Uploaded " + count + " tiles...", Color.CYAN);
                        sleep(10);
                    }
                }
            }
            msg("Finished uploading " + count + " existing tiles.", Color.GREEN);
        }, "Moonflower-BulkUpload").start();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  MapFile Bulk Export — renders all explored grids from persistent map
    // ═══════════════════════════════════════════════════════════════════

    private static void bulkExportFromMapFile() {
        if (isExporting) { msg("Export already in progress!", Color.YELLOW); return; }
        if (ui == null || ui.gui == null) { msg("No active game session", Color.RED); return; }

        // Access MapFile through MapWnd
        MapFile mapFile;
        try {
            if (ui.gui.mapfile == null) { msg("Map not loaded yet. Open the map first!", Color.RED); return; }
            mapFile = ui.gui.mapfile.file;
        } catch (Exception e) {
            msg("Cannot access MapFile: " + e.getMessage(), Color.RED);
            return;
        }
        if (mapFile == null) { msg("MapFile not available. Explore the world first!", Color.RED); return; }

        // Collect all grid entries from all segments under read lock
        ArrayList<long[]> allGrids = new ArrayList<>();
        try {
            Collection<Long> segIds;
            mapFile.lock.readLock().lock();
            try {
                segIds = new ArrayList<>(mapFile.knownsegs);
            } finally {
                mapFile.lock.readLock().unlock();
            }

            msg("Scanning " + segIds.size() + " map segment(s)...", Color.CYAN);

            for (var segId : segIds) {
                MapFile.Segment seg = mapFile.segments.get(segId);
                if (seg == null) continue;
                Map<Coord, Long> gridEntries = seg.gridMap();
                for (var entry : gridEntries.entrySet()) {
                    Coord sc = entry.getKey();
                    long gridId = entry.getValue();
                    allGrids.add(new long[]{sc.x, sc.y, gridId, segId});
                }
            }
        } catch (Exception e) {
            msg("Error reading map data: " + e.getMessage(), Color.RED);
            return;
        }

        if (allGrids.isEmpty()) {
            msg("No explored grids found in MapFile.", Color.YELLOW);
            return;
        }

        exportTotal = allGrids.size();
        exportDone = 0;
        exportErrors = 0;
        isExporting = true;

        msg("Exporting " + exportTotal + " grids from MapFile...", Color.GREEN);

        new Thread(() -> {
            String server = getActiveServer();
            MapFile mf;
            try {
                mf = ui.gui.mapfile.file;
            } catch (Exception e) {
                msg("Lost MapFile reference", Color.RED);
                isExporting = false;
                return;
            }

            for (long[] entry : allGrids) {
                if (signalToStop) break;

                int tileX = (int) entry[0];
                int tileY = (int) entry[1];
                long gridId = entry[2];
                long segId = entry[3];

                try {
                    MapFile.Grid grid = MapFile.Grid.load(mf, gridId);
                    if (grid == null) { exportErrors++; continue; }

                    // Render with proper texture offset for seamless tiling
                    BufferedImage img = grid.render(new Coord(tileX * 100, tileY * 100));
                    if (img == null) { exportErrors++; continue; }

                    var baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "png", baos);
                    byte[] pngData = baos.toByteArray();

                    if (pngData.length == 0) { exportErrors++; continue; }

                    String session = "mapfile-" + Long.toHexString(segId);
                    uploadTileBytes(pngData, tileX, tileY, session, server,
                        "tile_" + tileX + "_" + tileY + ".png");
                    exportDone++;

                    if (exportDone % 25 == 0) {
                        msg("Exported " + exportDone + "/" + exportTotal +
                            " grids (" + exportErrors + " errors)", Color.CYAN);
                    }
                    if (syncWindow != null) syncWindow.updateCounts();

                    Thread.sleep(15); // Throttle to avoid overwhelming server
                } catch (Loading l) {
                    // Tileset resource not loaded yet — skip this grid
                    exportErrors++;
                } catch (InterruptedException ie) {
                    break;
                } catch (Exception e) {
                    exportErrors++;
                }
            }

            isExporting = false;
            uploadedCount += exportDone;
            msg("MapFile export complete: " + exportDone + "/" + exportTotal +
                " tiles exported, " + exportErrors + " errors", Color.GREEN);
            if (syncWindow != null) syncWindow.updateCounts();
            playNotifySound();
        }, "Moonflower-MapFileExport").start();
    }

    /**
     * Upload raw PNG bytes as a tile to the Cartographer server.
     */
    private static void uploadTileBytes(byte[] pngData, int x, int y,
            String session, String server, String filename) {
        HttpURLConnection conn = null;
        try {
            String boundary = "----MoonflowerBoundary" + System.currentTimeMillis();
            var url = new URL(cartographerUrl + "/api/tiles/live");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (var os = conn.getOutputStream();
                 var writer = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true)) {
                writeField(writer, boundary, "server", server);
                writeField(writer, boundary, "session", session);
                writeField(writer, boundary, "x", String.valueOf(x));
                writeField(writer, boundary, "y", String.valueOf(y));
                if (selectedMapId >= 0) writeField(writer, boundary, "map_id", String.valueOf(selectedMapId));
                writeField(writer, boundary, "layer", String.valueOf(currentLayer));
                if (currentMineId != null) writeField(writer, boundary, "mine_id", currentMineId);

                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"tile\"; filename=\"")
                      .append(filename).append("\"\r\n");
                writer.append("Content-Type: image/png\r\n\r\n");
                writer.flush();
                os.write(pngData);
                os.flush();
                writer.append("\r\n").append("--").append(boundary).append("--\r\n");
                writer.flush();
            }

            int code = conn.getResponseCode();
            if (code != 200 && code != 201) {
                errorCount++;
            }
        } catch (Exception e) {
            errorCount++;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Server Folder / Map Management
    // ═══════════════════════════════════════════════════════════════════

    private static void scanAvailableServers() {
        availableServers.clear();
        try {
            String mapRoot = Config.userhome + "/map/";
            var mapDir = new File(mapRoot);
            if (mapDir.exists() && mapDir.isDirectory()) {
                File[] dirs = mapDir.listFiles(File::isDirectory);
                if (dirs != null) {
                    for (var d : dirs) availableServers.add(d.getName());
                }
            }
        } catch (Exception e) { }
        msg("Found " + availableServers.size() + " server folder(s)", Color.CYAN);
    }

    static String getActiveServer() {
        if (customServer != null && !customServer.isEmpty()) return customServer;
        try { return Config.defserv; } catch (Exception e) { return "game.havenandhearth.com"; }
    }

    private static void cycleServers(int direction) {
        if (availableServers.isEmpty()) scanAvailableServers();
        int total = availableServers.size() + 1;
        int current = serverIndex + 1;
        current = (current + direction + total) % total;
        serverIndex = current - 1;
        if (serverIndex < 0) {
            customServer = null;
            msg("Server: Auto (" + getActiveServer() + ")", Color.CYAN);
        } else {
            customServer = availableServers.get(serverIndex);
            msg("Server: " + customServer, Color.CYAN);
        }
        if (syncWindow != null) { syncWindow.updateServerLabel(); syncWindow.updateFolderLabel(); }
    }

    private static String getMapFolder() {
        try {
            if (customServer != null && !customServer.isEmpty()) {
                String folder = Config.userhome + "/map/" + customServer + "/";
                if (new File(folder).exists()) return folder;
            }
            String folder = Config.userhome + "/map/" + Config.defserv + "/";
            if (new File(folder).exists()) return folder;
            folder = Config.userhome + "/map/" + Config.server + "/";
            if (new File(folder).exists()) return folder;
            if (availableServers.isEmpty()) scanAvailableServers();
            if (availableServers.size() == 1) {
                folder = Config.userhome + "/map/" + availableServers.get(0) + "/";
                if (new File(folder).exists()) return folder;
            }
        } catch (Exception e) { }
        return null;
    }

    private static void cycleMaps(int direction) {
        if (sharedMapList.isEmpty()) { refreshMaps(); return; }
        sharedMapIndex = (sharedMapIndex + direction + sharedMapList.size()) % sharedMapList.size();
        applySharedMapSelection();
    }

    private static void refreshMaps() {
        HttpURLConnection conn = null;
        try {
            String server = getActiveServer();
            var url = new URL(cartographerUrl + "/api/maps?server=" + URLEncoder.encode(server, "UTF-8"));
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            if (code == 200) {
                String resp;
                try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    var sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    resp = sb.toString();
                }
                parseSharedMapList(resp);
                msg("Maps refreshed (" + sharedMapList.size() + " found)", Color.CYAN);
            } else {
                msg("Failed to load maps: HTTP " + code, Color.YELLOW);
            }
        } catch (Exception e) {
            msg("Failed to load maps: " + e.getMessage(), Color.YELLOW);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void createMapDirect() {
        createMapWithName("Map " + (sharedMapList.size() + 1));
    }

    private static void createMapWithName(String name) {
        if (name == null || name.trim().isEmpty()) { msg("Map name cannot be empty", Color.YELLOW); return; }
        String trimmed = name.trim();
        HttpURLConnection conn = null;
        try {
            String server = getActiveServer();
            String body = "{\"server\":\"" + server + "\",\"name\":\"" + trimmed.replace("\"", "\\\"") + "\"}";
            var url = new URL(cartographerUrl + "/api/maps");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST"); conn.setDoOutput(true);
            conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/json");
            try (var os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
            }
            int code = conn.getResponseCode();
            if (code == 201 || code == 200) {
                msg("Created map: " + trimmed, Color.GREEN);
                refreshMaps();
            } else {
                msg("Failed to create map: HTTP " + code, Color.YELLOW);
            }
        } catch (Exception e) {
            msg("Failed to create map: " + e.getMessage(), Color.YELLOW);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void parseSharedMapList(String json) {
        sharedMapList.clear();
        sharedMapNames.clear();
        int defaultIdx = 0;
        try {
            json = json.trim();
            if (json.startsWith("[")) json = json.substring(1);
            if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
            String[] objects = json.split("}\\s*,\\s*\\{");
            for (int i = 0; i < objects.length; i++) {
                String obj = objects[i].replace("{", "").replace("}", "");
                int id = extractIntField(obj, "id");
                String n = extractStringField(obj, "name");
                int isDef = extractIntField(obj, "is_default");
                if (id >= 0 && n != null) {
                    sharedMapList.add(new int[]{id, isDef});
                    sharedMapNames.add(n);
                    if (isDef == 1) defaultIdx = sharedMapList.size() - 1;
                }
            }
        } catch (Exception e) {
            msg("Map parse error: " + e.getMessage(), Color.YELLOW);
        }
        if (!sharedMapList.isEmpty()) {
            boolean found = false;
            if (selectedMapId >= 0) {
                for (int i = 0; i < sharedMapList.size(); i++) {
                    if (sharedMapList.get(i)[0] == selectedMapId) { sharedMapIndex = i; found = true; break; }
                }
            }
            if (!found) sharedMapIndex = defaultIdx;
            applySharedMapSelection();
        } else {
            selectedMapId = -1;
            selectedMapName = "No maps";
        }
    }

    private static void applySharedMapSelection() {
        if (sharedMapIndex >= 0 && sharedMapIndex < sharedMapList.size()) {
            int[] entry = sharedMapList.get(sharedMapIndex);
            selectedMapId = entry[0];
            String name = sharedMapNames.get(sharedMapIndex);
            selectedMapName = name;
            String suffix = entry[1] == 1 ? " (default)" : "";
            msg("Map: " + name + suffix + " (id=" + selectedMapId + ")", Color.CYAN);
            if (syncWindow != null && syncWindow.lblMap != null) syncWindow.lblMap.settext(name + suffix);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utilities
    // ═══════════════════════════════════════════════════════════════════

    private static int extractIntField(String obj, String field) {
        var p = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?\\d+)");
        var m = p.matcher(obj);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static String extractStringField(String obj, String field) {
        var p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*?)\"");
        var m = p.matcher(obj);
        return m.find() ? m.group(1) : null;
    }

    static int getCurrentLayer() { return currentLayer; }
    static String getCurrentMineId() { return currentMineId; }
    static int getSelectedMapId() { return selectedMapId; }
    static String getSelectedMapName() { return selectedMapName; }
    static String getCartographerUrl() { return cartographerUrl; }

    private static void msg(String text, Color color) {
        try { if (ui != null) ui.message(text, GameUI.MsgType.INFO); }
        catch (Exception e) { System.out.println("[MoonflowerTileSync] " + text); }
    }

    private static void playNotifySound() {
        try {
            var fmt = Audio.fmt;
            int sampleRate = (int) fmt.getSampleRate();
            int samples = sampleRate / 5;
            int channels = fmt.getChannels();
            int bytesPerSample = fmt.getSampleSizeInBits() / 8;
            byte[] buf = new byte[samples * channels * bytesPerSample];
            for (int i = 0; i < samples; i++) {
                double t = (double) i / sampleRate;
                double envelope = 1.0 - ((double) i / samples);
                double sample = Math.sin(2.0 * Math.PI * 880.0 * t) * envelope * 0.35;
                short val = (short) (sample * Short.MAX_VALUE);
                for (int ch = 0; ch < channels; ch++) {
                    int idx = (i * channels + ch) * bytesPerSample;
                    buf[idx] = (byte) (val & 0xFF);
                    if (bytesPerSample > 1) buf[idx + 1] = (byte) ((val >> 8) & 0xFF);
                }
            }
            Audio.play(buf);
        } catch (Exception e) { }
    }

    private static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
