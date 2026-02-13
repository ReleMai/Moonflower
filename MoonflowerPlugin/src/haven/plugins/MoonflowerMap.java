package haven.plugins;

import haven.*;
import java.awt.Color;
import java.awt.Desktop;
import java.net.URI;

/**
 * MoonflowerMap — Opens the HavenCartographer web map viewer in a browser.
 *
 * Features:
 *   - Open full map viewer in default browser
 *   - Navigate to player's current position
 *   - Server status check
 *
 * Targets Haven and Hearth (Java 25).
 */
public class MoonflowerMap extends Plugin {

    private static UI ui = null;
    private static MapInfoWindow infoWindow = null;
    private static String mapUrl = "http://127.0.0.1:3300/map";

    @Override
    public void load(UI ui) {
        var glob = ui.sess.glob;
        glob.paginae.add(glob.paginafor(Resource.load("paginae/add/moonflowermap")));
        XTendedPaginae.registerPlugin("moonflowermap", this);
    }

    @Override
    public void execute(UI ui) {
        MoonflowerMap.ui = ui;
        if (infoWindow != null && infoWindow.visible) {
            infoWindow.destroy();
            infoWindow = null;
        } else {
            infoWindow = new MapInfoWindow(ui.gui);
        }
    }

    static class MapInfoWindow extends Window {
        MapInfoWindow(Widget parent) {
            super(new Coord(300, 150), new Coord(260, 200), parent, "Moonflower Map");
            this.justclose = true;
            int y = 0;

            new Label(new Coord(0, y), this, "HavenCartographer Map Viewer");
            y += 20;

            new Label(new Coord(0, y), this, "Server: " + mapUrl);
            y += 24;

            new Button(new Coord(0, y), 200, this, "Open Map in Browser") {
                public void click() { openMapInBrowser(); }
            };
            y += 30;

            // Player coords
            String coords = "Unknown";
            try {
                if (ui != null && ui.gui != null && ui.gui.map != null) {
                    var player = ui.gui.map.player();
                    if (player != null) coords = "X: " + player.rc.x + "  Y: " + player.rc.y;
                }
            } catch (Exception e) { }
            new Label(new Coord(0, y), this, "Position: " + coords);
            y += 20;

            // Game server
            String server = "Unknown";
            try { if (ui != null && ui.sess != null) server = Config.defserv; } catch (Exception e) { }
            new Label(new Coord(0, y), this, "Game Server: " + server);
            y += 24;

            new Button(new Coord(0, y), 200, this, "Open Map at My Position") {
                public void click() { openMapAtPlayer(); }
            };
            y += 30;

            new Button(new Coord(0, y), 200, this, "Check Cartographer Status") {
                public void click() { checkServerStatus(); }
            };
            y += 30;

            new Button(new Coord(0, y), 200, this, "Open Hub Dashboard") {
                public void click() { openHub(); }
            };

            this.pack();
        }

        public void destroy() {
            infoWindow = null;
            super.destroy();
        }
    }

    private static void openMapInBrowser() {
        try {
            Desktop.getDesktop().browse(new URI(mapUrl));
            msg("Opening map in browser...", Color.GREEN);
        } catch (Exception e) {
            msg("Could not open browser: " + e.getMessage(), Color.RED);
            try {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", mapUrl});
            } catch (Exception e2) {
                msg("Failed to open map: " + e2.getMessage(), Color.RED);
            }
        }
    }

    private static void openMapAtPlayer() {
        try {
            var player = ui.gui.map.player();
            if (player != null) {
                int tx = player.rc.x / 11;
                int ty = player.rc.y / 11;
                String url = mapUrl + "?x=" + tx + "&y=" + ty + "&zoom=3";
                Desktop.getDesktop().browse(new URI(url));
                msg("Opening map at (" + tx + ", " + ty + ")...", Color.GREEN);
            } else {
                msg("Cannot determine player position.", Color.YELLOW);
                openMapInBrowser();
            }
        } catch (Exception e) {
            msg("Error: " + e.getMessage(), Color.RED);
            openMapInBrowser();
        }
    }

    private static void openHub() {
        try {
            String hubUrl = mapUrl.replace("/map", "/");
            Desktop.getDesktop().browse(new URI(hubUrl));
            msg("Opening hub dashboard...", Color.GREEN);
        } catch (Exception e) {
            msg("Could not open hub: " + e.getMessage(), Color.RED);
        }
    }

    private static void checkServerStatus() {
        new Thread(() -> {
            try {
                var url = new java.net.URL("http://127.0.0.1:3300/api/health");
                var conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    msg("Cartographer is ONLINE (" + mapUrl + ")", Color.GREEN);
                } else {
                    msg("Cartographer responded with code " + code, Color.YELLOW);
                }
                conn.disconnect();
            } catch (Exception e) {
                msg("Cartographer is OFFLINE or unreachable: " + e.getMessage(), Color.RED);
            }
        }, "Moonflower-MapCheck").start();
    }

    private static void msg(String text, Color color) {
        try { if (ui != null) ui.message(text, GameUI.MsgType.INFO); }
        catch (Exception e) { System.out.println("[MoonflowerMap] " + text); }
    }
}
