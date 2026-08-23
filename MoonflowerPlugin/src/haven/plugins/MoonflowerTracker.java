package haven.plugins;

import haven.*;
import java.awt.Color;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * MoonflowerTracker — Bot/account tracking and remote command plugin.
 *
 * Reports player position, status, and nearby interactable objects to
 * the HavenCartographer server. Receives commands from the web UI
 * and executes them (walk-to, interact, forage, etc.).
 *
 * Features:
 *   - High-fidelity position reporting with interpolation + validation
 *   - Nearby object scanning with distance/direction
 *   - Command queue (walk, interact, use-flower-menu, stop)
 *   - Status heartbeat (idle, moving, busy, foraging, offline)
 *   - Performance-optimized: throttled reports, batched sends
 *
 * Targets Haven and Hearth (Java 25).
 */
public class MoonflowerTracker extends Plugin {

    // ── Singleton state ────────────────────────────────────────────────
    private static UI ui = null;
    private static volatile boolean isTracking = false;
    private static volatile boolean signalToStop = false;
    private static TrackerWindow trackerWindow = null;

    // ── Identity ───────────────────────────────────────────────────────
    private static volatile String botId = null;
    private static volatile String charName = "Unknown";
    private static volatile String botType = "player";

    // ── Position state ─────────────────────────────────────────────────
    private static volatile double lastWorldX = Double.NaN;
    private static volatile double lastWorldY = Double.NaN;
    private static volatile double lastReportedX = Double.NaN;
    private static volatile double lastReportedY = Double.NaN;
    private static volatile int lastTileX = Integer.MIN_VALUE;
    private static volatile int lastTileY = Integer.MIN_VALUE;
    private static volatile boolean isMoving = false;
    private static volatile String currentStatus = "idle";

    // ── Performance tuning ─────────────────────────────────────────────
    private static final int POSITION_INTERVAL_MS = 1000;       // 1s while moving
    private static final int IDLE_INTERVAL_MS = 5000;           // 5s when idle
    private static final int NEARBY_SCAN_INTERVAL_MS = 3000;    // 3s for nearby scan
    private static final int COMMAND_POLL_INTERVAL_MS = 2000;   // 2s for command polling
    private static final double MIN_MOVE_THRESHOLD = 2.0;       // Min distance to report
    private static final int GRID_CELL_SIZE = 1100;
    private static final double NEARBY_RANGE = 50.0 * 11.0;    // 50 tiles in world units
    private static final int MAX_NEARBY_OBJECTS = 50;

    // ── Command queue ──────────────────────────────────────────────────
    private static final ConcurrentLinkedQueue<Map<String, Object>> commandQueue = new ConcurrentLinkedQueue<>();
    private static volatile boolean executingCommand = false;
    private static volatile String currentCommandId = null;
    private static volatile long lastCommandFetch = 0;

    // ── HTTP connection pool ───────────────────────────────────────────
    private static final String cartographerUrl = "http://127.0.0.1:3300";
    private static final ExecutorService httpPool = Executors.newFixedThreadPool(2, r -> {
        var t = new Thread(r, "MF-Tracker-HTTP");
        t.setDaemon(true);
        return t;
    });

    // ── Stuck / validation ─────────────────────────────────────────────
    private static volatile long lastPositionTime = 0;
    private static volatile int consecutiveErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 10;

    // ═══════════════════════════════════════════════════════════════════
    //  Plugin Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void load(UI ui) {
        var glob = ui.sess.glob;
        try {
            glob.paginae.add(glob.paginafor(Resource.load("paginae/add/moonflowertracker")));
        } catch (Exception e) {
            // Fallback: use TileSync icon if tracker icon not found
            try {
                glob.paginae.add(glob.paginafor(Resource.load("paginae/add/moonflowertilesync")));
            } catch (Exception e2) {
                System.err.println("[MoonflowerTracker] No plugin icon found, skipping paginae registration");
            }
        }
        XTendedPaginae.registerPlugin("moonflowertracker", this);
    }

    @Override
    public void execute(UI ui) {
        MoonflowerTracker.ui = ui;
        if (trackerWindow != null && trackerWindow.visible) {
            trackerWindow.destroy();
            trackerWindow = null;
        } else {
            trackerWindow = new TrackerWindow(ui.gui);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  External API (for other plugins to start/stop tracking)
    // ═══════════════════════════════════════════════════════════════════

    public static void startDirect(UI theUI) {
        if (isTracking) return;
        MoonflowerTracker.ui = theUI;
        startTracking();
    }

    public static void stopDirect() {
        signalToStop = true;
    }

    public static boolean isActive() { return isTracking; }
    public static String getBotId() { return botId; }
    public static String getStatus() { return currentStatus; }

    // ═══════════════════════════════════════════════════════════════════
    //  Tracking Core
    // ═══════════════════════════════════════════════════════════════════

    private static void startTracking() {
        if (isTracking) return;
        isTracking = true;
        signalToStop = false;
        consecutiveErrors = 0;
        currentStatus = "idle";

        // Generate persistent bot ID from character info
        botId = generateBotId();
        charName = resolveCharName();

        msg("Tracker started [" + charName + "] id=" + botId, Color.GREEN);

        // Position report thread
        new Thread(MoonflowerTracker::positionLoop, "MF-Tracker-Pos").start();
        // Nearby objects scanner
        new Thread(MoonflowerTracker::nearbyScanLoop, "MF-Tracker-Nearby").start();
        // Command polling & execution
        new Thread(MoonflowerTracker::commandLoop, "MF-Tracker-Cmd").start();
    }

    private static void stopTracking() {
        signalToStop = true;
        msg("Tracker stopping...", Color.ORANGE);
    }

    // ── Bot ID Generation ──────────────────────────────────────────────

    private static String generateBotId() {
        try {
            // Use character name + session hash for persistence
            String name = resolveCharName();
            String server = getActiveServer();
            return "bot-" + Math.abs((name + server).hashCode()) % 100000;
        } catch (Exception e) {
            return "bot-" + Long.toHexString(System.currentTimeMillis());
        }
    }

    private static String resolveCharName() {
        try {
            if (ui != null && ui.gui != null) {
                String charNm = ui.gui.getparent(GameUI.class).chrid;
                if (charNm != null && !charNm.isEmpty()) return charNm;
            }
        } catch (Exception e) { }
        try {
            if (ui != null && ui.gui != null && ui.gui.buddies != null) {
                return "Player";
            }
        } catch (Exception e) { }
        return "Unknown-" + Long.toHexString(System.currentTimeMillis() % 0xFFFF);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Position Reporting Loop
    // ═══════════════════════════════════════════════════════════════════

    private static void positionLoop() {
        try {
            while (!signalToStop && isTracking) {
                try {
                    if (consecutiveErrors > MAX_CONSECUTIVE_ERRORS) {
                        msg("Too many errors, pausing position reports for 30s", Color.YELLOW);
                        sleep(30000);
                        consecutiveErrors = 0;
                    }

                    var posData = readPlayerPosition();
                    if (posData == null) {
                        sleep(IDLE_INTERVAL_MS);
                        continue;
                    }

                    double wx = posData[0];
                    double wy = posData[1];
                    boolean moving = posData[2] > 0;

                    // Validate position — reject teleport-like jumps unless layer changed
                    if (!Double.isNaN(lastWorldX)) {
                        double dist = Math.sqrt(Math.pow(wx - lastWorldX, 2) + Math.pow(wy - lastWorldY, 2));
                        if (dist > 5000 && dist < 100000) {
                            // Likely a coordinate system shift, not a real move — skip this tick
                            msg("Position jump detected (" + (int) dist + "), skipping", Color.YELLOW);
                            lastWorldX = wx;
                            lastWorldY = wy;
                            sleep(2000);
                            continue;
                        }
                    }

                    // Calculate tile coords with origin offset
                    int sessionSpX = MoonflowerTileSync.sessionSpX;
                    int sessionSpY = MoonflowerTileSync.sessionSpY;
                    int gridX = Math.floorDiv((int) wx, GRID_CELL_SIZE);
                    int gridY = Math.floorDiv((int) wy, GRID_CELL_SIZE);
                    int tileX = (sessionSpX != Integer.MIN_VALUE) ? gridX - sessionSpX : gridX;
                    int tileY = (sessionSpY != Integer.MIN_VALUE) ? gridY - sessionSpY : gridY;
                    double fracX = (double) Math.floorMod((int) wx, GRID_CELL_SIZE) / GRID_CELL_SIZE;
                    double fracY = (double) Math.floorMod((int) wy, GRID_CELL_SIZE) / GRID_CELL_SIZE;

                    // Only report if moved enough or status changed or enough time passed
                    boolean movedEnough = Double.isNaN(lastReportedX) ||
                        Math.sqrt(Math.pow(wx - lastReportedX, 2) + Math.pow(wy - lastReportedY, 2)) > MIN_MOVE_THRESHOLD;
                    boolean statusChanged = moving != isMoving;
                    long timeSinceLastReport = System.currentTimeMillis() - lastPositionTime;
                    boolean timeElapsed = timeSinceLastReport > (moving ? POSITION_INTERVAL_MS * 3 : IDLE_INTERVAL_MS);

                    if (movedEnough || statusChanged || timeElapsed) {
                        isMoving = moving;
                        lastWorldX = wx;
                        lastWorldY = wy;

                        // Determine status
                        String status = resolveStatus(moving);

                        String server = getActiveServer();
                        int layer = MoonflowerTileSync.getCurrentLayer();
                        String mineId = MoonflowerTileSync.getCurrentMineId();
                        int mapId = MoonflowerTileSync.getSelectedMapId();

                        String json = buildPositionJson(wx, wy, tileX, tileY, fracX, fracY,
                            moving, status, layer, mineId, mapId, server);

                        sendPositionAsync(json);

                        lastReportedX = wx;
                        lastReportedY = wy;
                        lastTileX = tileX;
                        lastTileY = tileY;
                        lastPositionTime = System.currentTimeMillis();
                    }
                } catch (Exception e) {
                    consecutiveErrors++;
                }

                sleep(isMoving ? POSITION_INTERVAL_MS : IDLE_INTERVAL_MS);
            }
        } catch (Exception e) {
            msg("Position loop error: " + e.getMessage(), Color.RED);
        } finally {
            // Send offline status
            sendOfflineStatus();
            isTracking = false;
            signalToStop = false;
            currentStatus = "offline";
            if (trackerWindow != null) trackerWindow.onTrackingStopped();
            msg("Tracker stopped", Color.YELLOW);
        }
    }

    private static double[] readPlayerPosition() {
        try {
            if (ui == null || ui.gui == null || ui.gui.map == null) return null;
            var player = ui.gui.map.player();
            if (player == null || player.rc == null) return null;

            boolean moving = false;
            try { moving = player.getattr(Moving.class) != null; } catch (Exception e) { }

            return new double[]{player.rc.x, player.rc.y, moving ? 1.0 : 0.0};
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveStatus(boolean moving) {
        if (executingCommand) return "busy";
        try {
            if (ui != null && ui.gui != null && ui.gui.prog != -1) return "gathering";
        } catch (Exception e) { }
        try {
            if (MoonflowerForager.isActive()) return "foraging";
        } catch (Exception e) { }
        if (moving) return "moving";
        return "idle";
    }

    private static String buildPositionJson(double wx, double wy,
            int tileX, int tileY, double fracX, double fracY,
            boolean moving, String status, int layer, String mineId,
            int mapId, String server) {
        var sb = new StringBuilder(512);
        sb.append("{\"botId\":\"").append(botId)
          .append("\",\"name\":\"").append(escapeJson(charName))
          .append("\",\"type\":\"").append(botType)
          .append("\",\"x\":").append((int) wx)
          .append(",\"y\":").append((int) wy)
          .append(",\"tileX\":").append(tileX)
          .append(",\"tileY\":").append(tileY)
          .append(",\"fracX\":").append(String.format("%.4f", fracX))
          .append(",\"fracY\":").append(String.format("%.4f", fracY))
          .append(",\"moving\":").append(moving)
          .append(",\"status\":\"").append(status)
          .append("\",\"layer\":").append(layer);
        if (mineId != null) sb.append(",\"mineId\":\"").append(mineId).append("\"");
        if (mapId >= 0) sb.append(",\"mapId\":").append(mapId);
        sb.append(",\"server\":\"").append(server)
          .append("\",\"timestamp\":").append(System.currentTimeMillis())
          .append("}");
        return sb.toString();
    }

    private static void sendPositionAsync(String json) {
        httpPool.submit(() -> {
            HttpURLConnection conn = null;
            try {
                var url = new URL(cartographerUrl + "/api/bots/position");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("Content-Type", "application/json");
                try (var os = conn.getOutputStream()) {
                    os.write(json.getBytes("UTF-8"));
                }
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    consecutiveErrors = 0;
                } else {
                    consecutiveErrors++;
                }
            } catch (Exception e) {
                consecutiveErrors++;
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private static void sendOfflineStatus() {
        try {
            String server = getActiveServer();
            String json = "{\"botId\":\"" + botId + "\",\"name\":\"" + escapeJson(charName)
                + "\",\"status\":\"offline\",\"server\":\"" + server
                + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
            var url = new URL(cartographerUrl + "/api/bots/position");
            var conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Content-Type", "application/json");
            try (var os = conn.getOutputStream()) { os.write(json.getBytes("UTF-8")); }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) { }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Nearby Object Scanner
    // ═══════════════════════════════════════════════════════════════════

    private static void nearbyScanLoop() {
        try {
            while (!signalToStop && isTracking) {
                try {
                    var nearby = scanNearbyObjects();
                    if (nearby != null && !nearby.isEmpty()) {
                        sendNearbyAsync(nearby);
                    }
                } catch (Exception e) { }
                sleep(NEARBY_SCAN_INTERVAL_MS);
            }
        } catch (Exception e) { }
    }

    private static List<Map<String, Object>> scanNearbyObjects() {
        try {
            if (ui == null || ui.gui == null || ui.gui.map == null) return null;
            var player = ui.gui.map.player();
            if (player == null) return null;
            var pp = player.rc;

            Collection<Gob> gobs;
            try { gobs = ui.sess.glob.oc.getGobs(); } catch (Exception e) { return null; }
            if (gobs == null) return null;

            var result = new ArrayList<Map<String, Object>>();
            for (var gob : gobs) {
                if (result.size() >= MAX_NEARBY_OBJECTS) break;
                try {
                    String name = getGobResName(gob);
                    if (name.isEmpty()) continue;
                    if (gob.rc == null) continue;
                    double dist = gob.rc.dist(pp);
                    if (dist > NEARBY_RANGE || dist < 1.0) continue;

                    // Skip player's own gob
                    if (gob.id == player.id) continue;

                    var entry = new HashMap<String, Object>();
                    entry.put("gobId", gob.id);
                    entry.put("name", name);
                    entry.put("shortName", extractShortName(name));
                    entry.put("x", gob.rc.x);
                    entry.put("y", gob.rc.y);
                    entry.put("dist", (int) dist);
                    entry.put("interactable", isInteractable(name));
                    result.add(entry);
                } catch (Exception e) { }
            }

            // Sort by distance
            result.sort((a, b) -> Integer.compare((int) a.get("dist"), (int) b.get("dist")));
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isInteractable(String name) {
        // Common interactable patterns in Haven
        return name.contains("gfx/terobjs/") || name.contains("gfx/borka/")
            || name.contains("gfx/kritter/") || name.contains("herbs/")
            || name.contains("trees/") || name.contains("bushes/")
            || name.contains("stockpile") || name.contains("cupboard")
            || name.contains("chest") || name.contains("barrel");
    }

    private static String extractShortName(String fullName) {
        int lastSlash = fullName.lastIndexOf('/');
        return lastSlash >= 0 ? fullName.substring(lastSlash + 1) : fullName;
    }

    private static void sendNearbyAsync(List<Map<String, Object>> nearby) {
        httpPool.submit(() -> {
            HttpURLConnection conn = null;
            try {
                // Build JSON array manually for performance
                var sb = new StringBuilder(2048);
                sb.append("{\"botId\":\"").append(botId)
                  .append("\",\"server\":\"").append(getActiveServer())
                  .append("\",\"objects\":[");
                boolean first = true;
                for (var obj : nearby) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{\"gobId\":").append(obj.get("gobId"))
                      .append(",\"name\":\"").append(escapeJson((String) obj.get("name")))
                      .append("\",\"shortName\":\"").append(escapeJson((String) obj.get("shortName")))
                      .append("\",\"x\":").append(obj.get("x"))
                      .append(",\"y\":").append(obj.get("y"))
                      .append(",\"dist\":").append(obj.get("dist"))
                      .append(",\"interactable\":").append(obj.get("interactable"))
                      .append("}");
                }
                sb.append("],\"timestamp\":").append(System.currentTimeMillis()).append("}");

                var url = new URL(cartographerUrl + "/api/bots/nearby");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("Content-Type", "application/json");
                try (var os = conn.getOutputStream()) { os.write(sb.toString().getBytes("UTF-8")); }
                conn.getResponseCode();
            } catch (Exception e) { }
            finally { if (conn != null) conn.disconnect(); }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Command Polling & Execution
    // ═══════════════════════════════════════════════════════════════════

    private static void commandLoop() {
        try {
            while (!signalToStop && isTracking) {
                try {
                    // Poll for new commands from server
                    long now = System.currentTimeMillis();
                    if (now - lastCommandFetch > COMMAND_POLL_INTERVAL_MS) {
                        fetchPendingCommands();
                        lastCommandFetch = now;
                    }

                    // Execute queued commands
                    var cmd = commandQueue.poll();
                    if (cmd != null) {
                        executeCommand(cmd);
                    }
                } catch (Exception e) { }
                sleep(500);
            }
        } catch (Exception e) { }
    }

    private static void fetchPendingCommands() {
        HttpURLConnection conn = null;
        try {
            String server = getActiveServer();
            var url = new URL(cartographerUrl + "/api/bots/commands?botId=" + URLEncoder.encode(botId, "UTF-8")
                + "&server=" + URLEncoder.encode(server, "UTF-8") + "&status=pending");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            if (code == 200) {
                String resp;
                try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    var sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    resp = sb.toString();
                }
                parseAndQueueCommands(resp);
            }
        } catch (Exception e) { }
        finally { if (conn != null) conn.disconnect(); }
    }

    private static void parseAndQueueCommands(String json) {
        try {
            // Minimal JSON array parsing
            if (!json.contains("[")) return;
            String inner = json.substring(json.indexOf("[") + 1, json.lastIndexOf("]")).trim();
            if (inner.isEmpty()) return;

            // Split by },{ pattern (simple objects)
            String[] objects = inner.split("}\\s*,\\s*\\{");
            for (String obj : objects) {
                obj = obj.replace("{", "").replace("}", "").trim();
                if (obj.isEmpty()) continue;

                var cmd = new HashMap<String, Object>();
                String id = extractStringField(obj, "id");
                String type = extractStringField(obj, "command");
                String targetX = extractStringField(obj, "targetX");
                String targetY = extractStringField(obj, "targetY");
                String gobId = extractStringField(obj, "gobId");
                String menuOption = extractStringField(obj, "menuOption");

                if (id != null) cmd.put("id", id);
                if (type != null) cmd.put("command", type);
                if (targetX != null) cmd.put("targetX", Double.parseDouble(targetX));
                if (targetY != null) cmd.put("targetY", Double.parseDouble(targetY));
                if (gobId != null) cmd.put("gobId", Long.parseLong(gobId));
                if (menuOption != null) cmd.put("menuOption", menuOption);

                if (type != null) commandQueue.add(cmd);
            }
        } catch (Exception e) {
            msg("Command parse error: " + e.getMessage(), Color.YELLOW);
        }
    }

    private static void executeCommand(Map<String, Object> cmd) {
        String type = (String) cmd.get("command");
        String cmdId = cmd.get("id") != null ? cmd.get("id").toString() : null;

        if (type == null) return;

        executingCommand = true;
        currentCommandId = cmdId;
        currentStatus = "busy";

        try {
            // Acknowledge command start
            if (cmdId != null) reportCommandStatus(cmdId, "executing", null);

            switch (type) {
                case "walk" -> executeWalk(cmd);
                case "interact" -> executeInteract(cmd);
                case "stop" -> executeStop();
                case "forage-start" -> executeForageStart();
                case "forage-stop" -> executeForageStop();
                default -> {
                    msg("Unknown command: " + type, Color.YELLOW);
                    if (cmdId != null) reportCommandStatus(cmdId, "failed", "Unknown command: " + type);
                }
            }

            if (cmdId != null && !"stop".equals(type)) {
                reportCommandStatus(cmdId, "completed", null);
            }
        } catch (Exception e) {
            msg("Command error: " + e.getMessage(), Color.RED);
            if (cmdId != null) reportCommandStatus(cmdId, "failed", e.getMessage());
        } finally {
            executingCommand = false;
            currentCommandId = null;
            currentStatus = isMoving ? "moving" : "idle";
        }
    }

    // ── Walk To ────────────────────────────────────────────────────────

    private static void executeWalk(Map<String, Object> cmd) throws Exception {
        double tx = (double) cmd.getOrDefault("targetX", 0.0);
        double ty = (double) cmd.getOrDefault("targetY", 0.0);

        if (ui == null || ui.gui == null || ui.gui.map == null) {
            throw new Exception("UI not ready");
        }

        msg("Walking to (" + (int) tx + ", " + (int) ty + ")", Color.CYAN);

        // Send left-click at target position (Coord for wdgmsg)
        Coord dest = new Coord((int) tx, (int) ty);
        Coord2d destWorld = new Coord2d(tx, ty);
        ui.wdgmsg((Widget) ui.gui.map, "click",
            new Object[]{dest, dest, 1, 0});

        // Wait for movement to start, then stop
        long start = System.currentTimeMillis();
        boolean moved = false;
        while (System.currentTimeMillis() - start < 15000 && !signalToStop) {
            try {
                var player = ui.gui.map.player();
                if (player != null) {
                    boolean m = player.getattr(Moving.class) != null;
                    if (m) moved = true;
                    if (moved && !m) break; // Stopped moving = arrived
                    // Check if close enough (Coord2d for distance)
                    double dist = player.rc.dist(destWorld);
                    if (dist < 5.0) break;
                }
            } catch (Exception e) { }
            sleep(200);
        }
    }

    // ── Interact with Gob ──────────────────────────────────────────────

    private static void executeInteract(Map<String, Object> cmd) throws Exception {
        long gobId = cmd.containsKey("gobId") ? (long) cmd.get("gobId") : -1;
        String menuOption = (String) cmd.get("menuOption");

        if (gobId < 0) throw new Exception("No gobId specified");
        if (ui == null || ui.gui == null || ui.gui.map == null) throw new Exception("UI not ready");

        // Find the gob
        Gob target = null;
        try {
            Collection<Gob> gobs = ui.sess.glob.oc.getGobs();
            for (var g : gobs) {
                if (g.id == gobId) { target = g; break; }
            }
        } catch (Exception e) { throw new Exception("Cannot enumerate gobs"); }

        if (target == null) throw new Exception("Gob " + gobId + " not found (may have despawned)");

        String name = getGobResName(target);
        msg("Interacting with " + extractShortName(name) + " (id=" + gobId + ")", Color.CYAN);

        // Right-click the gob
        ui.wdgmsg((Widget) ui.gui.map, "click",
            new Object[]{target.sc, target.rc, 3, 0, 0, (int) target.id, target.rc, 0, -1});

        // Wait for flower menu or action
        sleep(800);

        if (menuOption != null && !menuOption.isEmpty()) {
            handleFlowerMenu(menuOption);
        }

        // Wait for action to complete (progress bar)
        waitForActionComplete(10000);
    }

    private static void handleFlowerMenu(String desiredOption) {
        try {
            // Find FlowerMenu widget
            FlowerMenu fm = null;
            for (Widget w = ui.gui.child; w != null; w = w.next) {
                if (w instanceof FlowerMenu) { fm = (FlowerMenu) w; break; }
            }
            if (fm == null) {
                sleep(500);
                for (Widget w = ui.gui.child; w != null; w = w.next) {
                    if (w instanceof FlowerMenu) { fm = (FlowerMenu) w; break; }
                }
            }
            if (fm == null) return;

            // Use reflection to access opts
            Field optsField = FlowerMenu.class.getDeclaredField("opts");
            optsField.setAccessible(true);
            FlowerMenu.Petal[] opts = (FlowerMenu.Petal[]) optsField.get(fm);
            if (opts == null) return;

            for (var petal : opts) {
                if (petal.name.toLowerCase().contains(desiredOption.toLowerCase())) {
                    fm.choose(petal);
                    msg("Selected: " + petal.name, Color.CYAN);
                    return;
                }
            }
            // If no match found, pick the first option
            if (opts.length > 0) {
                fm.choose(opts[0]);
                msg("Selected first option: " + opts[0].name, Color.CYAN);
            }
        } catch (Exception e) {
            msg("FlowerMenu error: " + e.getMessage(), Color.YELLOW);
        }
    }

    private static void waitForActionComplete(int timeoutMs) {
        long start = System.currentTimeMillis();
        boolean wasInProgress = false;
        while (System.currentTimeMillis() - start < timeoutMs && !signalToStop) {
            try {
                boolean inProgress = ui.gui.prog != -1;
                if (inProgress) wasInProgress = true;
                if (wasInProgress && !inProgress) return; // Action finished
            } catch (Exception e) { }
            sleep(200);
        }
    }

    // ── Stop ───────────────────────────────────────────────────────────

    private static void executeStop() {
        msg("Stop command received", Color.ORANGE);
        commandQueue.clear();
        // Click near player to cancel movement
        try {
            if (ui != null && ui.gui != null && ui.gui.map != null) {
                var player = ui.gui.map.player();
                if (player != null) {
                    Coord pos = player.rc;
                    ui.wdgmsg((Widget) ui.gui.map, "click",
                        new Object[]{pos, pos, 1, 0});
                }
            }
        } catch (Exception e) { }
    }

    // ── Forage Start/Stop ──────────────────────────────────────────────

    private static void executeForageStart() {
        try {
            if (!MoonflowerForager.isActive()) {
                MoonflowerForager.startDirect(ui);
                msg("Forager started via remote command", Color.GREEN);
            }
        } catch (Exception e) {
            msg("Failed to start forager: " + e.getMessage(), Color.RED);
        }
    }

    private static void executeForageStop() {
        try {
            if (MoonflowerForager.isActive()) {
                MoonflowerForager.stopDirect();
                msg("Forager stopped via remote command", Color.ORANGE);
            }
        } catch (Exception e) {
            msg("Failed to stop forager: " + e.getMessage(), Color.RED);
        }
    }

    // ── Command Status Report ──────────────────────────────────────────

    private static void reportCommandStatus(String cmdId, String status, String error) {
        httpPool.submit(() -> {
            HttpURLConnection conn = null;
            try {
                var sb = new StringBuilder();
                sb.append("{\"id\":\"").append(cmdId)
                  .append("\",\"status\":\"").append(status).append("\"");
                if (error != null) sb.append(",\"error\":\"").append(escapeJson(error)).append("\"");
                sb.append(",\"botId\":\"").append(botId)
                  .append("\",\"timestamp\":").append(System.currentTimeMillis()).append("}");

                var url = new URL(cartographerUrl + "/api/bots/commands/status");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("Content-Type", "application/json");
                try (var os = conn.getOutputStream()) { os.write(sb.toString().getBytes("UTF-8")); }
                conn.getResponseCode();
            } catch (Exception e) { }
            finally { if (conn != null) conn.disconnect(); }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Tracker Window
    // ═══════════════════════════════════════════════════════════════════

    static class TrackerWindow extends Window {
        private Label lblStatus, lblBotId, lblPosition, lblCharName, lblCommandStatus;
        private Button btnTrack;

        TrackerWindow(Widget parent) {
            super(new Coord(300, 200), new Coord(260, 200), parent, "Moonflower Tracker");
            this.justclose = true;
            int y = 0;

            // Character name
            new Label(new Coord(0, y), this, "Character:");
            lblCharName = new Label(new Coord(70, y), this, resolveCharName());
            lblCharName.setcolor(new Color(100, 200, 255));
            y += 18;

            // Bot ID
            new Label(new Coord(0, y), this, "Bot ID:");
            lblBotId = new Label(new Coord(50, y), this, botId != null ? botId : "—");
            lblBotId.setcolor(new Color(200, 200, 200));
            y += 18;

            // Status
            new Label(new Coord(0, y), this, "Status:");
            lblStatus = new Label(new Coord(50, y), this, isTracking ? currentStatus : "Stopped");
            lblStatus.setcolor(isTracking ? Color.GREEN : Color.WHITE);
            y += 18;

            // Position
            new Label(new Coord(0, y), this, "Position:");
            lblPosition = new Label(new Coord(60, y), this, "—");
            y += 18;

            // Command status
            new Label(new Coord(0, y), this, "Command:");
            lblCommandStatus = new Label(new Coord(60, y), this, executingCommand ? "Executing" : "None");
            y += 24;

            btnTrack = new Button(new Coord(0, y), 200, this,
                isTracking ? "Stop Tracking" : "Start Tracking") {
                public void click() {
                    if (!isTracking) {
                        startTracking();
                        change("Stop Tracking");
                        lblStatus.settext("Starting..."); lblStatus.setcolor(Color.GREEN);
                    } else {
                        stopTracking();
                        change("Start Tracking");
                        lblStatus.settext("Stopping..."); lblStatus.setcolor(Color.ORANGE);
                    }
                }
            };
            y += 30;

            new Label(new Coord(0, y), this, "Web: " + cartographerUrl + "/map");
            this.pack();

            // Status updater thread
            new Thread(() -> {
                while (trackerWindow != null) {
                    try {
                        lblStatus.settext(isTracking ? currentStatus : "Stopped");
                        lblStatus.setcolor(isTracking ? Color.GREEN : Color.GRAY);
                        lblBotId.settext(botId != null ? botId : "—");
                        if (!Double.isNaN(lastWorldX)) {
                            lblPosition.settext((int) lastWorldX + ", " + (int) lastWorldY
                                + " [" + lastTileX + "," + lastTileY + "]");
                        }
                        lblCommandStatus.settext(executingCommand ?
                            "Executing: " + currentCommandId : "None");
                    } catch (Exception e) { }
                    sleep(1000);
                }
            }, "MF-TrackerUI").start();
        }

        void onTrackingStopped() {
            try {
                btnTrack.change("Start Tracking");
                lblStatus.settext("Stopped"); lblStatus.setcolor(Color.GRAY);
            } catch (Exception e) { }
        }

        public void destroy() {
            trackerWindow = null;
            super.destroy();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utilities
    // ═══════════════════════════════════════════════════════════════════

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

    static String getActiveServer() {
        try {
            String server = MoonflowerTileSync.getActiveServer();
            if (server != null && !server.isEmpty()) return server;
        } catch (Exception e) { }
        try { return Config.defserv; } catch (Exception e) { return "game.havenandhearth.com"; }
    }

    private static String extractStringField(String obj, String field) {
        // Handles both "field":"value" and "field":numericValue
        int fieldIdx = obj.indexOf("\"" + field + "\"");
        if (fieldIdx < 0) return null;
        int colonIdx = obj.indexOf(":", fieldIdx);
        if (colonIdx < 0) return null;
        String rest = obj.substring(colonIdx + 1).trim();
        if (rest.startsWith("\"")) {
            int end = rest.indexOf("\"", 1);
            return end > 0 ? rest.substring(1, end) : null;
        } else {
            // Numeric value — return as string
            var sb = new StringBuilder();
            for (char c : rest.toCharArray()) {
                if (c == ',' || c == '}' || c == ']') break;
                sb.append(c);
            }
            return sb.toString().trim();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void msg(String text, Color color) {
        try { if (ui != null) ui.message(text, GameUI.MsgType.INFO); }
        catch (Exception e) { System.out.println("[MoonflowerTracker] " + text); }
    }

    private static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
