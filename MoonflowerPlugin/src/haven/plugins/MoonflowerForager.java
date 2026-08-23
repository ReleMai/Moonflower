package haven.plugins;

import haven.*;
import java.awt.Color;
import java.lang.reflect.Field;
import java.util.*;
import javax.sound.sampled.AudioFormat;

/**
 * MoonflowerForager — Automated foraging bot for Haven and Hearth.
 *
 * Features:
 *   - Configurable forageable item toggles (herbs, mushrooms, berries, curios)
 *   - Tabbed UI: Control | Items | Nearby
 *   - Adjustable forage radius (up to 60 tiles)
 *   - Nearby radar with walk-to buttons and compass directions
 *   - Stuck detection with pathfinding workaround
 *   - Pick-up confirmation (gob disappearance check)
 *   - Danger avoidance (bears, boars, wolves, etc.)
 *
 * Targets Haven and Hearth (Java 25).
 */
public class MoonflowerForager extends Plugin {

    // ── Singleton state ────────────────────────────────────────────────
    private static UI ui = null;
    private static volatile boolean isRunning = false;
    private static volatile boolean signalToStop = false;
    private static ForagerWindow configWindow = null;

    // ── Configuration ──────────────────────────────────────────────────
    static volatile int forageRadius = 15;    // tiles (max 60)
    static volatile int dangerRadius = 10;    // tiles
    static volatile boolean avoidDanger = true;
    static volatile boolean alertDanger = true;

    // ── Stuck detection ────────────────────────────────────────────────
    private static final int STUCK_THRESHOLD_MS = 3000;
    private static final double STUCK_MIN_DIST = 3.0;
    private static final int MAX_STUCK_RETRIES = 3;
    private static final int JIGGLE_DIST = 44;

    // ── Blacklist for unreachable gobs ─────────────────────────────────
    private static final HashSet<Long> blacklisted = new HashSet<>();

    // ═══════════════════════════════════════════════════════════════════
    //  Forageable Item
    // ═══════════════════════════════════════════════════════════════════

    static class ForageItem {
        final String name;
        final String pattern;
        final String category;
        final int colorHex;
        volatile boolean enabled;

        ForageItem(String name, String pattern, String category, int color, boolean enabled) {
            this.name = name;
            this.pattern = pattern;
            this.category = category;
            this.colorHex = color;
            this.enabled = enabled;
        }

        Color getColor() {
            return new Color(colorHex);
        }
    }

    // ── Forageables (Haven and Hearth items) ──────────────────────────
    static final ArrayList<ForageItem> FORAGEABLES = new ArrayList<>();
    static final String[] CATEGORIES = {"Mushrooms", "Berries", "Herbs", "Curios", "Greens", "Other"};
    static final LinkedHashMap<String, Color> CATEGORY_COLORS = new LinkedHashMap<>();

    static {
        CATEGORY_COLORS.put("Mushrooms", new Color(0xEECC88));
        CATEGORY_COLORS.put("Berries", new Color(0xAAFF00));
        CATEGORY_COLORS.put("Herbs", new Color(0x33E677));
        CATEGORY_COLORS.put("Curios", new Color(0xFFFF55));
        CATEGORY_COLORS.put("Greens", new Color(0x66CC66));
        CATEGORY_COLORS.put("Other", new Color(0xFFAAFF));

        // ── Mushrooms ──
        FORAGEABLES.add(new ForageItem("Chantrelle", "gfx/terobjs/herbs/chantrelle", "Mushrooms", 0xFFCC00, true));
        FORAGEABLES.add(new ForageItem("Morel", "gfx/terobjs/herbs/morel", "Mushrooms", 0xFFFFB3, true));
        FORAGEABLES.add(new ForageItem("Chives", "gfx/terobjs/herbs/chives", "Mushrooms", 0x66CC66, true));
        FORAGEABLES.add(new ForageItem("Liberty Cap", "gfx/terobjs/herbs/libertycap", "Mushrooms", 0xCC9966, true));
        FORAGEABLES.add(new ForageItem("Parasol Mushroom", "gfx/terobjs/herbs/parasol", "Mushrooms", 0xCCBB88, true));

        // ── Berries ──
        FORAGEABLES.add(new ForageItem("Blueberry", "gfx/terobjs/herbs/blueberry", "Berries", 0x6666CC, true));
        FORAGEABLES.add(new ForageItem("Lingonberry", "gfx/terobjs/herbs/lingon", "Berries", 0xCC3333, true));
        FORAGEABLES.add(new ForageItem("Gooseberry", "gfx/terobjs/herbs/gooseberry", "Berries", 0x99CC66, true));
        FORAGEABLES.add(new ForageItem("Blackberry", "gfx/terobjs/herbs/blackberry", "Berries", 0x332244, true));
        FORAGEABLES.add(new ForageItem("Raspberry", "gfx/terobjs/herbs/raspberry", "Berries", 0xCC3366, true));

        // ── Herbs ──
        FORAGEABLES.add(new ForageItem("Clover", "gfx/terobjs/herbs/clover", "Herbs", 0x33CC33, true));
        FORAGEABLES.add(new ForageItem("Dandelion", "gfx/terobjs/herbs/dandelion", "Herbs", 0xFFFF00, true));
        FORAGEABLES.add(new ForageItem("Yarrow", "gfx/terobjs/herbs/yarrow", "Herbs", 0xFFFFCC, true));
        FORAGEABLES.add(new ForageItem("Wild Windsown Weed", "gfx/terobjs/herbs/wwweed", "Herbs", 0x669966, false));
        FORAGEABLES.add(new ForageItem("Camomile", "gfx/terobjs/herbs/camomile", "Herbs", 0xFFFFEE, true));

        // ── Curios ──
        FORAGEABLES.add(new ForageItem("Quartz", "gfx/terobjs/herbs/quartz", "Curios", 0xEEEEEE, false));
        FORAGEABLES.add(new ForageItem("Arrow Stone", "gfx/terobjs/herbs/arrowstone", "Curios", 0xAAAAAA, false));
        FORAGEABLES.add(new ForageItem("Peculiar Bone", "gfx/terobjs/herbs/bone", "Curios", 0xDDDDCC, false));
        FORAGEABLES.add(new ForageItem("Four-Leaf Clover", "gfx/terobjs/herbs/4leafclover", "Curios", 0x00FF44, true));

        // ── Greens ──
        FORAGEABLES.add(new ForageItem("Stinging Nettle", "gfx/terobjs/herbs/nettle", "Greens", 0x339933, false));
        FORAGEABLES.add(new ForageItem("Cattail", "gfx/terobjs/herbs/cattail", "Greens", 0x886633, false));
        FORAGEABLES.add(new ForageItem("Coltsfoot", "gfx/terobjs/herbs/coltsfoot", "Greens", 0xFFCC66, true));

        // ── Other ──
        FORAGEABLES.add(new ForageItem("Ground Items", "gfx/terobjs/items/", "Other", 0xFFAAFF, false));
    }

    // ── Dangerous creatures (Haven and Hearth) ──
    private static final String[] DANGER_PATTERNS = {
        "gfx/kritter/bear", "gfx/kritter/boar", "gfx/kritter/wolf",
        "gfx/kritter/lynx", "gfx/kritter/badger", "gfx/kritter/adder",
        "gfx/kritter/bat", "gfx/kritter/troll", "gfx/kritter/wolverine",
        "gfx/kritter/moose", "gfx/kritter/walrus", "gfx/kritter/mammoth"
    };

    // ═══════════════════════════════════════════════════════════════════
    //  Plugin Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void load(UI ui) {
        var glob = ui.sess.glob;
        glob.paginae.add(glob.paginafor(Resource.load("paginae/add/moonflower")));
        XTendedPaginae.registerPlugin("moonflower", this);
    }

    @Override
    public void execute(UI ui) {
        MoonflowerForager.ui = ui;
        if (configWindow != null && configWindow.visible) {
            configWindow.destroy();
            configWindow = null;
        } else {
            configWindow = new ForagerWindow(ui.gui);
        }
    }

    // ── Direct start/stop ─────────────────────────────────────────────

    static void startDirect(UI theUI) {
        if (isRunning) return;
        MoonflowerForager.ui = theUI;
        blacklisted.clear();
        isRunning = true;
        signalToStop = false;
        msg("Forager started (direct)", Color.GREEN);
        new Thread(() -> forageLoop(), "Moonflower-Forager").start();
    }

    static void stopDirect() {
        signalToStop = true;
        msg("Forager stopping...", Color.ORANGE);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Forager Window — Tabbed UI
    // ═══════════════════════════════════════════════════════════════════

    static class ForagerWindow extends Window {
        private String activeTab = "control";
        private final ArrayList<Widget> tabWidgets = new ArrayList<>();
        private Button btnControl, btnItems, btnNearby;
        private Label lblStatus;
        private Button btnStart;

        private ArrayList<NearbyEntry> lastScan = new ArrayList<>();

        ForagerWindow(Widget parent) {
            super(new Coord(180, 80), new Coord(340, 300), parent, "Moonflower Forager");
            this.justclose = true;

            int y = 0;
            btnControl = new Button(new Coord(0, y), 90, this, "* Control") {
                public void click() { switchTab("control"); }
            };
            btnItems = new Button(new Coord(95, y), 90, this, "  Items") {
                public void click() { switchTab("items"); }
            };
            btnNearby = new Button(new Coord(190, y), 90, this, "  Nearby") {
                public void click() { switchTab("nearby"); }
            };

            buildControlTab();
            this.pack();
        }

        void switchTab(String tab) {
            clearTab();
            activeTab = tab;
            btnControl.change(tab.equals("control") ? "* Control" : "  Control");
            btnItems.change(tab.equals("items") ? "* Items" : "  Items");
            btnNearby.change(tab.equals("nearby") ? "* Nearby" : "  Nearby");

            switch (tab) {
                case "control" -> buildControlTab();
                case "items" -> buildItemsTab();
                case "nearby" -> buildNearbyTab();
            }
            this.pack();
        }

        void clearTab() {
            for (var w : tabWidgets) {
                try { w.destroy(); } catch (Exception e) { }
            }
            tabWidgets.clear();
        }

        @SuppressWarnings("unchecked")
        <T extends Widget> T tw(T w) {
            tabWidgets.add(w);
            return w;
        }

        // ── Control Tab ──────────────────────────────────────────────

        void buildControlTab() {
            int y = 28;

            lblStatus = tw(new Label(new Coord(0, y), this, isRunning ? "RUNNING" : "Idle"));
            if (isRunning) lblStatus.setcolor(Color.GREEN);
            y += 22;

            btnStart = tw(new Button(new Coord(0, y), 150, this, isRunning ? "Stop Foraging" : "Start Foraging") {
                public void click() {
                    if (!isRunning) startForaging();
                    else stopForaging();
                }
            });
            tw(new Button(new Coord(160, y), 120, this, "Clear Skips") {
                public void click() {
                    blacklisted.clear();
                    msg("Blacklist cleared!", Color.CYAN);
                }
            });
            y += 32;

            tw(new Label(new Coord(0, y), this, "--- Settings ---"));
            y += 18;

            tw(new Label(new Coord(0, y), this, "Forage Radius:"));
            final var lblRad = tw(new Label(new Coord(220, y), this, forageRadius + "t"));
            y += 16;
            tw(new HSlider(new Coord(0, y), 300, this, 1, 60, forageRadius) {
                public void changed() {
                    forageRadius = this.val;
                    lblRad.settext(this.val + "t");
                }
            });
            y += 22;

            tw(new Label(new Coord(0, y), this, "Danger Radius:"));
            final var lblDng = tw(new Label(new Coord(220, y), this, dangerRadius + "t"));
            y += 16;
            tw(new HSlider(new Coord(0, y), 300, this, 1, 40, dangerRadius) {
                public void changed() {
                    dangerRadius = this.val;
                    lblDng.settext(this.val + "t");
                }
            });
            y += 22;

            var cbDanger = tw(new CheckBox(new Coord(0, y), this, "Avoid dangerous animals") {
                public void changed(boolean val) { avoidDanger = val; }
            });
            cbDanger.a = avoidDanger;
            y += 20;

            var cbAlert = tw(new CheckBox(new Coord(0, y), this, "Alert on danger") {
                public void changed(boolean val) { alertDanger = val; }
            });
            cbAlert.a = alertDanger;
            y += 24;

            int enabledCount = (int) FORAGEABLES.stream().filter(fi -> fi.enabled).count();
            tw(new Label(new Coord(0, y), this,
                "Enabled: " + enabledCount + "/" + FORAGEABLES.size() + "  Skipped: " + blacklisted.size()));
        }

        // ── Items Tab ────────────────────────────────────────────────

        void buildItemsTab() {
            int y = 28;

            tw(new Button(new Coord(0, y), 90, this, "All On") {
                public void click() {
                    FORAGEABLES.forEach(fi -> fi.enabled = true);
                    switchTab("items");
                }
            });
            tw(new Button(new Coord(95, y), 90, this, "All Off") {
                public void click() {
                    FORAGEABLES.forEach(fi -> fi.enabled = false);
                    switchTab("items");
                }
            });
            int ec = (int) FORAGEABLES.stream().filter(fi -> fi.enabled).count();
            tw(new Label(new Coord(195, y + 4), this, ec + "/" + FORAGEABLES.size()));
            y += 28;

            String currentCat = "";
            int col = 0;

            for (var item : FORAGEABLES) {
                if (!item.category.equals(currentCat)) {
                    if (col == 1) { y += 17; col = 0; }
                    y += 4;
                    var catLbl = tw(new Label(new Coord(0, y), this, "-- " + item.category + " --"));
                    var catColor = CATEGORY_COLORS.get(item.category);
                    if (catColor != null) catLbl.setcolor(catColor);
                    y += 17;
                    currentCat = item.category;
                    col = 0;
                }

                int xOff = col == 0 ? 14 : 170;
                var dot = tw(new Label(new Coord(xOff - 12, y), this, "*"));
                dot.setcolor(item.getColor());

                var cb = tw(new CheckBox(new Coord(xOff, y), this, item.name) {
                    public void changed(boolean val) { item.enabled = val; }
                });
                cb.a = item.enabled;

                col++;
                if (col >= 2) { col = 0; y += 17; }
            }
            if (col == 1) y += 17;
        }

        // ── Nearby Tab ───────────────────────────────────────────────

        void buildNearbyTab() {
            int y = 28;

            var header = tw(new Label(new Coord(0, y), this,
                "-- Nearby Forageables (radius " + forageRadius + "t) --"));
            header.setcolor(Color.CYAN);
            y += 18;

            lastScan = scanNearby();
            tw(new Label(new Coord(0, y), this, "Found " + lastScan.size() + " items"));
            y += 20;

            if (lastScan.isEmpty()) {
                var empty = tw(new Label(new Coord(0, y), this, "Nothing nearby. Try exploring!"));
                empty.setcolor(Color.GRAY);
                y += 18;
            } else {
                int shown = Math.min(lastScan.size(), 12);
                for (int i = 0; i < shown; i++) {
                    final var ne = lastScan.get(i);
                    boolean skip = blacklisted.contains(ne.gob.id);
                    String txt = ne.shortName + " " + String.format("%.1f", ne.dist) + "t " + ne.dir;
                    if (skip) txt = "[SKIP] " + txt;

                    var dot = tw(new Label(new Coord(0, y + 2), this, "*"));
                    dot.setcolor(skip ? Color.DARK_GRAY : ne.color);

                    tw(new Button(new Coord(12, y), 270, this, txt) {
                        public void click() {
                            if (!isRunning) forageSpecific(ne.gob);
                        }
                    });
                    y += 24;
                }
                if (lastScan.size() > 12) {
                    tw(new Label(new Coord(0, y), this,
                        "... +" + (lastScan.size() - 12) + " more"));
                    y += 18;
                }
            }

            y += 6;
            tw(new Button(new Coord(0, y), 90, this, "Refresh") {
                public void click() { switchTab("nearby"); }
            });
            tw(new Button(new Coord(100, y), 100, this, "Pick Nearest") {
                public void click() {
                    if (!isRunning && !lastScan.isEmpty()) {
                        for (var ne : lastScan) {
                            if (!blacklisted.contains(ne.gob.id)) {
                                forageSpecific(ne.gob);
                                return;
                            }
                        }
                        msg("All nearby items blacklisted!", Color.YELLOW);
                    }
                }
            });
            tw(new Button(new Coord(210, y), 80, this, "Clr Skips") {
                public void click() { blacklisted.clear(); switchTab("nearby"); }
            });
        }

        void startForaging() {
            if (isRunning) return;
            blacklisted.clear();
            isRunning = true;
            signalToStop = false;
            if (btnStart != null) btnStart.change("Stop Foraging");
            if (lblStatus != null) { lblStatus.settext("RUNNING"); lblStatus.setcolor(Color.GREEN); }
            msg("Forager started!", Color.GREEN);
            new Thread(() -> forageLoop(), "Moonflower-Forager").start();
        }

        void stopForaging() {
            signalToStop = true;
            if (btnStart != null) btnStart.change("Start Foraging");
            if (lblStatus != null) { lblStatus.settext("Stopping..."); lblStatus.setcolor(Color.ORANGE); }
            msg("Forager stopping...", Color.ORANGE);
        }

        void updateStatus(String text, Color color) {
            try {
                if (lblStatus != null) { lblStatus.settext(text); lblStatus.setcolor(color); }
            } catch (Exception e) { }
        }

        void onForageStopped() {
            try {
                if (btnStart != null) btnStart.change("Start Foraging");
                if (lblStatus != null) { lblStatus.settext("Idle"); lblStatus.setcolor(Color.WHITE); }
            } catch (Exception e) { }
        }

        public void destroy() {
            if (isRunning) signalToStop = true;
            configWindow = null;
            super.destroy();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Nearby Scanner
    // ═══════════════════════════════════════════════════════════════════

    static class NearbyEntry {
        final Gob gob;
        final String fullName;
        final String shortName;
        final double dist;
        final String dir;
        final Color color;

        NearbyEntry(Gob g, String fn, String sn, double d, String dr, Color c) {
            this.gob = g; this.fullName = fn; this.shortName = sn;
            this.dist = d; this.dir = dr; this.color = c;
        }
    }

    static ArrayList<NearbyEntry> scanNearby() {
        var results = new ArrayList<NearbyEntry>();
        var gobs = getGobs();
        if (gobs == null) return results;
        var pp = getPlayerCoord();
        if (pp == null) return results;

        double maxDist = forageRadius * 11.0;

        for (var gob : gobs) {
            String name = getGobResName(gob);
            if (name.isEmpty()) continue;

            boolean isForageable = name.contains("herbs/") ||
                                   name.contains("terobjs/items/");
            if (!isForageable) continue;

            double dist = gob.rc.dist(pp);
            if (dist > maxDist) continue;

            double tileDist = dist / 11.0;
            String dir = getDirection(pp, gob.rc);
            String shortName = shortResName(name);

            Color color = Color.WHITE;
            for (var fi : FORAGEABLES) {
                if (name.contains(fi.pattern)) {
                    color = fi.getColor();
                    shortName = fi.name;
                    break;
                }
            }

            results.add(new NearbyEntry(gob, name, shortName, tileDist, dir, color));
        }

        results.sort(Comparator.comparingDouble(a -> a.dist));
        return results;
    }

    static String getDirection(Coord from, Coord to) {
        int dx = to.x - from.x;
        int dy = to.y - from.y;
        if (Math.abs(dx) < 5 && Math.abs(dy) < 5) return "Here";
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle >= -22.5 && angle < 22.5) return "E";
        if (angle >= 22.5 && angle < 67.5) return "SE";
        if (angle >= 67.5 && angle < 112.5) return "S";
        if (angle >= 112.5 && angle < 157.5) return "SW";
        if (angle >= 157.5 || angle < -157.5) return "W";
        if (angle >= -157.5 && angle < -112.5) return "NW";
        if (angle >= -112.5 && angle < -67.5) return "N";
        if (angle >= -67.5 && angle < -22.5) return "NE";
        return "?";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Forage Specific Gob
    // ═══════════════════════════════════════════════════════════════════

    static void forageSpecific(final Gob gob) {
        if (isRunning || gob == null) return;
        isRunning = true;
        signalToStop = false;
        if (configWindow != null) configWindow.updateStatus("Picking...", Color.CYAN);

        new Thread(() -> {
            try {
                String name = resolveItemName(gob);
                msg("Walking to " + name + "...", Color.CYAN);

                boolean picked = tryForageGob(gob, name);
                if (picked) msg("Picked " + name + "!", Color.GREEN);
                else msg("Could not pick " + name, Color.YELLOW);

                if (configWindow != null && configWindow.activeTab.equals("nearby")) {
                    configWindow.switchTab("nearby");
                }
            } catch (Exception e) {
                msg("Error: " + e.getMessage(), Color.RED);
            } finally {
                isRunning = false;
                if (configWindow != null) configWindow.onForageStopped();
            }
        }, "Moonflower-ForageOne").start();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Core: Forage a single gob with stuck detection
    // ═══════════════════════════════════════════════════════════════════

    private static boolean tryForageGob(Gob target, String name) {
        long gobId = target.id;
        int stuckRetries = 0;

        while (stuckRetries <= MAX_STUCK_RETRIES && !signalToStop) {
            var startPos = getPlayerCoord();
            if (startPos == null) return false;

            rightClickGob(target);
            sleep(30);

            boolean moved = waitMovementStart(1500);

            if (!moved) {
                sleep(50);
                if (handleFlowerMenu()) {
                    if (waitForProgress(6000)) {
                        return confirmPicked(gobId);
                    }
                }
                stuckRetries++;
                if (stuckRetries <= MAX_STUCK_RETRIES) {
                    if (configWindow != null)
                        configWindow.updateStatus("Stuck! Rerouting " + stuckRetries + "/" + MAX_STUCK_RETRIES, Color.ORANGE);
                    jiggleAround(startPos);
                    continue;
                }
                break;
            }

            long moveStart = System.currentTimeMillis();
            boolean arrivedOrStopped = false;

            while (!signalToStop) {
                sleep(50);
                var curPos = getPlayerCoord();
                if (curPos == null) break;

                try {
                    var p = ui.gui.map.player();
                    if (p != null && p.getattr(Moving.class) == null) {
                        arrivedOrStopped = true;
                        break;
                    }
                } catch (Exception e) { break; }

                long elapsed = System.currentTimeMillis() - moveStart;
                if (elapsed > STUCK_THRESHOLD_MS) {
                    double moved2 = curPos.dist(startPos);
                    if (moved2 < STUCK_MIN_DIST) {
                        arrivedOrStopped = false;
                        break;
                    }
                }
                if (elapsed > 15000) break;
            }

            if (!arrivedOrStopped && !signalToStop) {
                stuckRetries++;
                if (stuckRetries <= MAX_STUCK_RETRIES) {
                    if (configWindow != null)
                        configWindow.updateStatus("Stuck moving! Retry " + stuckRetries, Color.ORANGE);
                    jiggleAround(getPlayerCoord());
                    continue;
                }
                break;
            }

            sleep(30);
            if (handleFlowerMenu()) {
                if (waitForProgress(6000)) {
                    return confirmPicked(gobId);
                }
            }

            stuckRetries++;
            if (stuckRetries <= MAX_STUCK_RETRIES) {
                sleep(100);
                continue;
            }
            break;
        }

        blacklisted.add(gobId);
        return false;
    }

    // ── Stuck workaround ─────────────────────────────────────────────

    private static void jiggleAround(Coord fromPos) {
        if (fromPos == null) return;
        var rng = new Random();
        int dx = (rng.nextInt(JIGGLE_DIST * 2) - JIGGLE_DIST);
        int dy = (rng.nextInt(JIGGLE_DIST * 2) - JIGGLE_DIST);
        if (Math.abs(dx) < 11) dx = 22 * (dx >= 0 ? 1 : -1);
        if (Math.abs(dy) < 11) dy = 22 * (dy >= 0 ? 1 : -1);
        var dest = new Coord(fromPos.x + dx, fromPos.y + dy);
        try {
            ui.wdgmsg((Widget) ui.gui.map, "click", new Object[]{dest, dest, 1, 0});
        } catch (Exception e) { }
        sleep(200);
        if (waitMovementStart(800)) {
            waitMovementStop(3000);
        }
        sleep(100);
    }

    // ── Confirm item picked ──────────────────────────────────────────

    private static boolean confirmPicked(long gobId) {
        sleep(100);
        var gobs = getGobs();
        if (gobs == null) return true;
        for (var g : gobs) {
            if (g.id == gobId) return false;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Core Foraging Loop
    // ═══════════════════════════════════════════════════════════════════

    private static void forageLoop() {
        try {
            int picked = 0;
            int skipped = 0;

            while (!signalToStop) {
                if (isInventoryFull()) {
                    msg("Inventory full! Stopping. Picked " + picked, Color.YELLOW);
                    break;
                }

                if (avoidDanger && isDangerNearby()) {
                    if (alertDanger) msg("DANGER nearby — fleeing!", Color.RED);
                    if (configWindow != null) configWindow.updateStatus("DANGER — fleeing!", Color.RED);
                    fleeFromDanger();
                    sleep(2000);
                    continue;
                }

                var target = findNearest();
                if (target == null) {
                    if (configWindow != null) configWindow.updateStatus("No items found", Color.GRAY);
                    msg("No enabled forageables nearby. Picked " + picked + ", skipped " + skipped, Color.GRAY);
                    break;
                }

                String itemName = resolveItemName(target);
                double dist = distToPlayer(target);
                if (configWindow != null)
                    configWindow.updateStatus("-> " + itemName + " (" + String.format("%.1f", dist) + "t)", Color.CYAN);

                boolean success = tryForageGob(target, itemName);

                if (success) {
                    picked++;
                    if (configWindow != null)
                        configWindow.updateStatus("Picked " + picked + " (" + itemName + ")", Color.GREEN);
                } else {
                    skipped++;
                    if (configWindow != null)
                        configWindow.updateStatus("Skipped " + itemName + " (" + skipped + " total)", Color.YELLOW);
                }

                sleep(30);
            }

            msg("Forager done. Picked " + picked + ", skipped " + skipped + ".", Color.GREEN);
        } catch (Exception e) {
            msg("Error: " + e.getMessage(), Color.RED);
        } finally {
            isRunning = false;
            signalToStop = false;
            if (configWindow != null) configWindow.onForageStopped();
            msg("Forager stopped.", Color.YELLOW);
            playNotifySound();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Game Object Scanning
    // ═══════════════════════════════════════════════════════════════════

    private static Gob findNearest() {
        var gobs = getGobs();
        if (gobs == null) return null;
        var pp = getPlayerCoord();
        if (pp == null) return null;

        double maxDist = forageRadius * 11.0;
        Gob nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (var gob : gobs) {
            if (signalToStop) return null;
            if (blacklisted.contains(gob.id)) continue;

            String name = getGobResName(gob);
            if (name.isEmpty()) continue;

            boolean match = FORAGEABLES.stream()
                .anyMatch(fi -> fi.enabled && name.contains(fi.pattern));
            if (!match) continue;

            double dist = gob.rc.dist(pp);
            if (dist < maxDist && dist < nearestDist) {
                nearest = gob;
                nearestDist = dist;
            }
        }
        return nearest;
    }

    private static boolean isDangerNearby() {
        var gobs = getGobs();
        if (gobs == null) return false;
        var pp = getPlayerCoord();
        if (pp == null) return false;
        double dangerDist = dangerRadius * 11.0;

        for (var gob : gobs) {
            String name = getGobResName(gob);
            if (name.isEmpty()) continue;
            for (String danger : DANGER_PATTERNS) {
                if (name.contains(danger) && gob.rc.dist(pp) < dangerDist) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void fleeFromDanger() {
        var pp = getPlayerCoord();
        if (pp == null) return;
        var gobs = getGobs();
        if (gobs == null) return;

        Gob nearestDanger = null;
        double nearestDist = Double.MAX_VALUE;
        for (var gob : gobs) {
            String gname = getGobResName(gob);
            if (gname.isEmpty()) continue;
            for (String dp : DANGER_PATTERNS) {
                if (gname.contains(dp)) {
                    double d = gob.rc.dist(pp);
                    if (d < nearestDist) {
                        nearestDist = d;
                        nearestDanger = gob;
                    }
                    break;
                }
            }
        }

        if (nearestDanger == null) return;

        int dx = pp.x - nearestDanger.rc.x;
        int dy = pp.y - nearestDanger.rc.y;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) { dx = 11; len = 11; }

        var dest = new Coord(pp.x + (int)(dx / len * 110), pp.y + (int)(dy / len * 110));
        try {
            ui.wdgmsg((Widget) ui.gui.map, "click", new Object[]{dest, dest, 1, 0});
        } catch (Exception e) { }
        sleep(2000);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Game Interaction
    // ═══════════════════════════════════════════════════════════════════

    private static void rightClickGob(Gob gob) {
        try {
            ui.wdgmsg(
                (Widget) ui.gui.map, "click",
                new Object[]{gob.sc, gob.rc, 3, 0, 0, (int) gob.id, gob.rc, 0, -1}
            );
        } catch (Exception e) {
            msg("Click error: " + e.getMessage(), Color.RED);
        }
    }

    private static boolean handleFlowerMenu() {
        for (int i = 0; i < 30 && !signalToStop; i++) {
            var fm = findFlowerMenu();
            if (fm != null) {
                try {
                    Field optsField = FlowerMenu.class.getDeclaredField("opts");
                    optsField.setAccessible(true);
                    var opts = (FlowerMenu.Petal[]) optsField.get(fm);
                    if (opts != null && opts.length > 0) {
                        FlowerMenu.Petal pick = null;
                        for (var p : opts) {
                            if (p.name != null) {
                                String n = p.name.toLowerCase();
                                if (n.equals("pick") || n.equals("pick up") ||
                                    n.equals("forage") || n.equals("harvest")) {
                                    pick = p;
                                    break;
                                }
                            }
                        }
                        fm.choose(pick != null ? pick : opts[0]);
                        return true;
                    }
                } catch (Exception e) {
                    try { fm.choose(null); } catch (Exception ignored) { }
                }
                return false;
            }
            sleep(8);
        }
        return false;
    }

    private static FlowerMenu findFlowerMenu() {
        try {
            for (Widget w = ui.root.child; w != null; w = w.next) {
                if (w instanceof FlowerMenu) return (FlowerMenu) w;
            }
        } catch (Exception e) { }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Inventory / Movement / Progress
    // ═══════════════════════════════════════════════════════════════════

    private static boolean isInventoryFull() {
        try {
            return ui.gui.maininv.getSameName("", true).size() >= 1023;
        } catch (Exception e) { return false; }
    }

    private static boolean waitMovementStart(int timeout) {
        for (int w = 0; w < timeout && !signalToStop; w += 15) {
            try {
                var p = ui.gui.map.player();
                if (p != null && p.getattr(Moving.class) != null) return true;
            } catch (Exception e) { }
            sleep(15);
        }
        return false;
    }

    private static boolean waitMovementStop(int timeout) {
        for (int w = 0; w < timeout && !signalToStop; w += 15) {
            try {
                var p = ui.gui.map.player();
                if (p != null && p.getattr(Moving.class) == null) return true;
            } catch (Exception e) { }
            sleep(15);
        }
        return false;
    }

    private static boolean waitForProgress(int timeout) {
        for (int w = 0; w < 1200 && !signalToStop; w += 15) {
            try { if (ui.gui.prog != -1) break; } catch (Exception e) { return false; }
            sleep(15);
        }
        for (int w = 0; w < timeout && !signalToStop; w += 15) {
            try { if (ui.gui.prog == -1) return true; } catch (Exception e) { return false; }
            sleep(15);
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Utilities
    // ═══════════════════════════════════════════════════════════════════

    private static String resolveItemName(Gob gob) {
        String name = getGobResName(gob);
        for (var fi : FORAGEABLES) {
            if (name.contains(fi.pattern)) return fi.name;
        }
        return shortResName(name);
    }

    private static Collection<Gob> getGobs() {
        try { return ui.sess.glob.oc.getGobs(); }
        catch (Exception e) {
            sleep(15);
            try { return ui.sess.glob.oc.getGobs(); } catch (Exception e2) { return null; }
        }
    }

    private static Coord getPlayerCoord() {
        try { return ui.gui.map.player().rc; } catch (Exception e) { return null; }
    }

    private static String getGobResName(Gob gob) {
        try {
            var rd = gob.getattr(ResDrawable.class);
            if (rd != null && rd.res != null) return rd.res.get().name;
        } catch (Loading l) { } catch (Exception e) { }
        try {
            var cmp = gob.getattr(Composite.class);
            if (cmp != null && cmp.base != null) return cmp.base.get().name;
        } catch (Loading l) { } catch (Exception e) { }
        return "";
    }

    private static double distToPlayer(Gob gob) {
        var pp = getPlayerCoord();
        return pp == null ? Double.MAX_VALUE : gob.rc.dist(pp) / 11.0;
    }

    private static String shortResName(String name) {
        return name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
    }

    static void msg(String text, Color color) {
        try { if (ui != null) ui.message(text, GameUI.MsgType.INFO); }
        catch (Exception e) { System.out.println("[Moonflower] " + text); }
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
