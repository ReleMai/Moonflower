package haven.foraging;

import haven.Coord;
import haven.Coord2d;
import haven.FastText;
import haven.GOut;
import haven.Loading;
import haven.MapWnd;
import haven.MCache;
import haven.MiniMap;
import haven.MoonFlowerHudTheme;
import haven.Tex;
import haven.UI;
import haven.Widget;
import haven.Window;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.List;

/** Themed route planner and supervised controller for visible-client foraging. */
public final class ForagingWindow extends Window {
    private static final Coord PREFERRED = UI.scale(820, 600);
    private final ForagingController controller;
    private final WayfinderCanvas canvas;

    public ForagingWindow(ForagingController controller) {
        super(PREFERRED, "Botanical Wayfinder");
        this.controller = controller;
        canvas = add(new WayfinderCanvas(PREFERRED), Coord.z);
        reqclose(() -> {
            if(controller.active())
                controller.pause("Paused: Botanical Wayfinder was closed.", true);
            hide();
        });
    }

    public void fitTo(Coord available) {
        Coord fitted = fittedSize(available);
        resize(fitted);
        canvas.resize(fitted);
        canvas.layoutControls();
    }

    static Coord fittedSize(Coord available) {
        Coord margin = UI.scale(42, 86);
        int width = Math.max(320, available.x - margin.x);
        int height = Math.max(280, available.y - margin.y);
        return(Coord.of(Math.min(PREFERRED.x, width), Math.min(PREFERRED.y, height)));
    }

    @Override
    public boolean keydown(KeyDownEvent event) {
        if(event.awt.getKeyCode() == KeyEvent.VK_ESCAPE && controller.active()) {
            controller.pause("Paused: Escape requested manual takeover.", true);
            return(true);
        }
        return(super.keydown(event));
    }

    private final class WayfinderCanvas extends Widget {
        private final ForagingHerbIconCache icons = new ForagingHerbIconCache();
        private RouteMap routeMap;
        private int scrollRow;
        private Coord mapOrigin = Coord.z;
        private Coord mapSize = Coord.z;

        WayfinderCanvas(Coord size) {
            super(size);
        }

        void layoutControls() {
            int split = split();
            Coord right = Coord.of(split + UI.scale(4), UI.scale(56));
            Coord rightSize = Coord.of(sz.x - right.x - UI.scale(14), sz.y - UI.scale(72));
            mapOrigin = right.add(UI.scale(8), UI.scale(34));
            int mapHeight = Math.max(UI.scale(100), Math.min(UI.scale(230), rightSize.y / 2));
            mapSize = Coord.of(Math.max(UI.scale(150), rightSize.x - UI.scale(16)), mapHeight);
            if(routeMap != null) {
                routeMap.c = mapOrigin;
                routeMap.resize(mapSize);
            }
        }

        @Override
        public void tick(double dt) {
            super.tick(dt);
            ensureRouteMap();
            if(routeMap != null) {
                routeMap.c = mapOrigin;
                routeMap.resize(mapSize);
            }
        }

        private void ensureRouteMap() {
            MapWnd mapFile = controller.gameUI().mapfile;
            if(mapFile == null) {
                if(routeMap != null) {
                    routeMap.reqdestroy();
                    routeMap = null;
                }
                return;
            }
            if(routeMap == null || routeMap.file != mapFile.file) {
                if(routeMap != null)
                    routeMap.reqdestroy();
                routeMap = add(new RouteMap(mapSize, mapFile.file), mapOrigin);
            }
        }

        private int split() {
            return(Math.max(UI.scale(330), (int)(sz.x * 0.42)));
        }

        @Override
        public void draw(GOut g) {
            ForagingSnapshot view = controller.snapshot();
            MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 235);
            MoonFlowerHudTheme.drawWindowFrame(g, Coord.z, sz);
            int split = split();
            Coord left = UI.scale(14, 56);
            Coord leftSize = Coord.of(split - UI.scale(22), Math.max(UI.scale(120), sz.y - UI.scale(118)));
            Coord right = Coord.of(split + UI.scale(4), UI.scale(56));
            Coord rightSize = Coord.of(Math.max(UI.scale(180), sz.x - right.x - UI.scale(14)),
                    Math.max(UI.scale(120), sz.y - UI.scale(72)));
            MoonFlowerHudTheme.drawPanel(g, left, leftSize, 205);
            MoonFlowerHudTheme.drawPanel(g, right, rightSize, 205);
            drawHeader(g, view);
            drawCatalog(g, view, left, leftSize);
            drawRoutePanel(g, view, right, rightSize);
            drawFooter(g, view);
            super.draw(g);
        }

        private void drawHeader(GOut g, ForagingSnapshot view) {
            MoonFlowerHudTheme.drawCurvedVine(g, UI.scale(22, 34),
                    Coord.of(sz.x - UI.scale(22), UI.scale(34)), 1.0);
            MoonFlowerHudTheme.drawBlossom(g, Coord.of(sz.x / 2, UI.scale(34)), UI.scale(7));
            FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(15)), 0.5, 0.5,
                    "BOTANICAL WAYFINDER  ·  ROUTE FORAGING");
            FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(46)), 0.5, 0.5,
                    "%s  ·  %s", view.state.name(), shortText(view.reason, 88));
        }

        private void drawCatalog(GOut g, ForagingSnapshot view, Coord origin, Coord size) {
            FastText.aprintfstroked(g, origin.add(UI.scale(10), UI.scale(18)), 0, 0.5,
                    "HERBARIUM  ·  click an icon to PICK / SKIP");
            MoonFlowerHudTheme.drawLeafButton(g, origin.add(size.x - UI.scale(176), UI.scale(7)),
                    Coord.of(UI.scale(74), UI.scale(22)), false, false);
            FastText.aprintfstroked(g, origin.add(size.x - UI.scale(139), UI.scale(18)), 0.5, 0.5,
                    "SELECT ALL");
            MoonFlowerHudTheme.drawLeafButton(g, origin.add(size.x - UI.scale(94), UI.scale(7)),
                    Coord.of(UI.scale(70), UI.scale(22)), false, false);
            FastText.aprintfstroked(g, origin.add(size.x - UI.scale(59), UI.scale(18)), 0.5, 0.5,
                    "CLEAR");
            List<ForagingGobScanner.HerbResource> herbs = view.catalog;
            int cardHeight = Math.max(UI.scale(34), Math.min(UI.scale(48), size.y / 8));
            int top = origin.y + UI.scale(32);
            int availableRows = Math.max(1, (size.y - UI.scale(42)) / cardHeight);
            int maxScroll = Math.max(0, herbs.size() - availableRows);
            scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));
            if(herbs.isEmpty()) {
                FastText.aprintfstroked(g, origin.add(size.x / 2, size.y / 2), 0.5, 0.5,
                        "Waiting for the loaded herb catalog...");
                return;
            }
            for(int row = 0; row < availableRows && scrollRow + row < herbs.size(); row++) {
                ForagingGobScanner.HerbResource herb = herbs.get(scrollRow + row);
                Coord card = Coord.of(origin.x + UI.scale(8), top + row * cardHeight);
                Coord cardSize = Coord.of(size.x - UI.scale(16), cardHeight - UI.scale(3));
                boolean selected = view.selectedResources.contains(herb.resourceName);
                MoonFlowerHudTheme.drawLeafButton(g, card, cardSize, selected, false);
                int iconSide = Math.max(UI.scale(22), Math.min(UI.scale(36), cardSize.y - UI.scale(6)));
                Coord iconOrigin = card.add(UI.scale(5), (cardSize.y - iconSide) / 2);
                Tex texture = icons.texture(herb);
                g.chcolor(255, 255, 255, selected ? 255 : 86);
                g.image(texture, iconOrigin, Coord.of(iconSide, iconSide));
                g.chcolor();
                FastText.aprintfstroked(g, card.add(iconSide + UI.scale(12), UI.scale(15)), 0, 0.5,
                        "%s  %s  ·  %s", selected ? "[PICK]" : "[SKIP]",
                        shortText(herb.displayName, 24), herb.live ? "LIVE" : "GUIDE");
                FastText.aprintfstroked(g, card.add(iconSide + UI.scale(12), UI.scale(30)), 0, 0.5,
                        "%s  ·  %s", herb.category, shortText(herb.resourceName, 36));
            }
            if(maxScroll > 0)
                FastText.aprintfstroked(g, Coord.of(origin.x + size.x - UI.scale(20), top - UI.scale(9)),
                        0.5, 0.5, "%d/%d", scrollRow + 1, maxScroll + 1);
        }

        private void drawRoutePanel(GOut g, ForagingSnapshot view, Coord origin, Coord size) {
            FastText.aprintfstroked(g, origin.add(UI.scale(10), UI.scale(18)), 0, 0.5,
                    "FIELD MAP  ·  click to plot the collection path");
            if(routeMap == null) {
                FastText.aprintfstroked(g, Coord.of(origin.x + size.x / 2, mapOrigin.y + mapSize.y / 2),
                        0.5, 0.5, "Map data is not loaded yet");
            }
            int y = mapOrigin.y + mapSize.y + UI.scale(7);
            drawAction(g, Coord.of(origin.x + UI.scale(8), y), Coord.of(UI.scale(84), UI.scale(24)),
                    "UNDO", false);
            drawAction(g, Coord.of(origin.x + UI.scale(98), y), Coord.of(UI.scale(84), UI.scale(24)),
                    "CLEAR PATH", false);
            drawAction(g, Coord.of(origin.x + UI.scale(198), y), Coord.of(UI.scale(76), UI.scale(24)),
                    "ROUTE", view.direction.usesCheckpointRoute());
            FastText.aprintfstroked(g, origin.add(UI.scale(10), y - origin.y + UI.scale(43)), 0, 0.5,
                    "CALC  path points: %d  ·  corridor: 8 tiles", view.route.size());
            FastText.aprintfstroked(g, origin.add(UI.scale(10), y - origin.y + UI.scale(61)), 0, 0.5,
                    "SHIFT / RIGHT CLICK  removes the last point");
            drawModes(g, origin, size, y + UI.scale(72), view);
            drawStatus(g, origin, size, y + UI.scale(105), view);
        }

        private void drawModes(GOut g, Coord origin, Coord size, int y, ForagingSnapshot view) {
            int x = origin.x + UI.scale(8);
            int rowY = y;
            for(ForagingDirection mode : ForagingDirection.values()) {
                int width = mode.usesCheckpointRoute() ? UI.scale(62) : UI.scale(34);
                if(x + width > origin.x + size.x - UI.scale(8)) {
                    x = origin.x + UI.scale(8);
                    rowY += UI.scale(23);
                }
                drawAction(g, Coord.of(x, rowY), Coord.of(width, UI.scale(20)), mode.label,
                        view.direction == mode);
                x += width + UI.scale(4);
            }
        }

        private void drawStatus(GOut g, Coord origin, Coord size, int y, ForagingSnapshot view) {
            int line = UI.scale(17);
            String target = view.target == null ? "none" : shortText(view.target.displayName, 26);
            String distance = view.targetDistance < 0 ? "unavailable" : String.format("%.1f", view.targetDistance);
            FastText.aprintfstroked(g, origin.add(UI.scale(10), y - origin.y), 0, 0.5,
                    "TARGET  %s  ·  CALC distance %s", target, distance);
            FastText.aprintfstroked(g, origin.add(UI.scale(10), y - origin.y + line), 0, 0.5,
                    "PROGRESS  %d / %d  ·  LEARNED yield %d", view.routeIndex,
                    view.routeSize, view.yield);
            FastText.aprintfstroked(g, origin.add(UI.scale(10), y - origin.y + line * 2), 0, 0.5,
                    "CALC free cells %s  ·  reserve %d", view.freeCells < 0 ? "?" : view.freeCells,
                    view.reserveCells);
            if(size.y > UI.scale(430)) {
                FastText.aprintfstroked(g, origin.add(UI.scale(10), y - origin.y + line * 4), 0, 0.5,
                        "EVENT LEDGER");
                for(int index = 0; index < Math.min(3, view.events.size()); index++)
                    FastText.aprintfstroked(g, origin.add(UI.scale(10), y - origin.y + line * (5 + index)),
                            0, 0.5, "%s", shortText(view.events.get(index), 52));
            }
        }

        private void drawFooter(GOut g, ForagingSnapshot view) {
            int y = sz.y - UI.scale(42);
            drawAction(g, Coord.of(UI.scale(18), y), Coord.of(UI.scale(122), UI.scale(30)),
                    "START / RESUME", false);
            drawAction(g, Coord.of(UI.scale(150), y), Coord.of(UI.scale(82), UI.scale(30)),
                    "PAUSE", false);
            MoonFlowerHudTheme.drawLeafButton(g, Coord.of(UI.scale(242), y),
                    Coord.of(UI.scale(82), UI.scale(30)), false, false);
            g.chcolor(new Color(MoonFlowerHudTheme.RUBY.getRed(), MoonFlowerHudTheme.RUBY.getGreen(),
                    MoonFlowerHudTheme.RUBY.getBlue(), 180));
            g.rect(Coord.of(UI.scale(245), y + UI.scale(3)), Coord.of(UI.scale(76), UI.scale(24)));
            g.chcolor();
            FastText.aprintfstroked(g, Coord.of(UI.scale(283), y + UI.scale(15)), 0.5, 0.5, "STOP");
            FastText.aprintfstroked(g, Coord.of(sz.x - UI.scale(15), y + UI.scale(15)), 1, 0.5,
                    "%s", shortText(view.reason, 54));
        }

        private void drawAction(GOut g, Coord origin, Coord size, String label, boolean active) {
            MoonFlowerHudTheme.drawLeafButton(g, origin, size, active, false);
            FastText.aprintfstroked(g, origin.add(size.div(2)), 0.5, 0.5, "%s", label);
        }

        @Override
        public boolean mousedown(MouseDownEvent event) {
            ForagingSnapshot view = controller.snapshot();
            int split = split();
            Coord left = UI.scale(14, 56);
            Coord leftSize = Coord.of(split - UI.scale(22), Math.max(UI.scale(120), sz.y - UI.scale(118)));
            List<ForagingGobScanner.HerbResource> herbs = view.catalog;
            int cardHeight = Math.max(UI.scale(34), Math.min(UI.scale(48), leftSize.y / 8));
            int top = left.y + UI.scale(32);
            int availableRows = Math.max(1, (leftSize.y - UI.scale(42)) / cardHeight);
            int maxScroll = Math.max(0, herbs.size() - availableRows);
            scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));
            if(event.b == 1 && event.c.x >= left.x + UI.scale(8) &&
                    event.c.x < left.x + leftSize.x - UI.scale(8) &&
                    event.c.y >= top && event.c.y < top + availableRows * cardHeight) {
                int index = scrollRow + ((event.c.y - top) / cardHeight);
                if(index >= 0 && index < herbs.size()) {
                    controller.toggleSelection(herbs.get(index).resourceName);
                    return(true);
                }
            }
            if(event.b == 1 && hit(event.c, Coord.of(left.x + leftSize.x - UI.scale(176), left.y + UI.scale(7)),
                    Coord.of(UI.scale(74), UI.scale(22)))) {
                controller.setAllSelected(true);
                return(true);
            }
            if(event.b == 1 && hit(event.c, Coord.of(left.x + leftSize.x - UI.scale(94), left.y + UI.scale(7)),
                    Coord.of(UI.scale(70), UI.scale(22)))) {
                controller.setAllSelected(false);
                return(true);
            }
            Coord right = Coord.of(split + UI.scale(4), UI.scale(56));
            Coord rightSize = Coord.of(Math.max(UI.scale(180), sz.x - right.x - UI.scale(14)),
                    Math.max(UI.scale(120), sz.y - UI.scale(72)));
            int y = mapOrigin.y + mapSize.y + UI.scale(7);
            if(event.b == 1 && hit(event.c, Coord.of(right.x + UI.scale(8), y),
                    Coord.of(UI.scale(84), UI.scale(24)))) {
                controller.removeLastRoutePoint();
                return(true);
            }
            if(event.b == 1 && hit(event.c, Coord.of(right.x + UI.scale(98), y),
                    Coord.of(UI.scale(84), UI.scale(24)))) {
                controller.clearRoute();
                return(true);
            }
            if(event.b == 1 && hit(event.c, Coord.of(right.x + UI.scale(198), y),
                    Coord.of(UI.scale(76), UI.scale(24)))) {
                controller.selectDirection(ForagingDirection.ROUTE);
                return(true);
            }
            int modeY = y + UI.scale(72);
            int modeX = right.x + UI.scale(8);
            for(ForagingDirection mode : ForagingDirection.values()) {
                int width = mode.usesCheckpointRoute() ? UI.scale(62) : UI.scale(34);
                if(modeX + width > right.x + rightSize.x - UI.scale(8)) {
                    modeX = right.x + UI.scale(8);
                    modeY += UI.scale(23);
                }
                if(event.b == 1 && hit(event.c, Coord.of(modeX, modeY), Coord.of(width, UI.scale(20)))) {
                    controller.selectDirection(mode);
                    return(true);
                }
                modeX += width + UI.scale(4);
            }
            int footerY = sz.y - UI.scale(42);
            if(event.b == 1 && hit(event.c, Coord.of(UI.scale(18), footerY), Coord.of(UI.scale(122), UI.scale(30)))) {
                controller.startOrResume();
                return(true);
            }
            if(event.b == 1 && hit(event.c, Coord.of(UI.scale(150), footerY), Coord.of(UI.scale(82), UI.scale(30)))) {
                controller.pause("Paused by user.", true);
                return(true);
            }
            if(event.b == 1 && hit(event.c, Coord.of(UI.scale(242), footerY), Coord.of(UI.scale(82), UI.scale(30)))) {
                controller.emergencyStop("Emergency stop requested by user.");
                return(true);
            }
            return(super.mousedown(event));
        }

        @Override
        public boolean mousewheel(MouseWheelEvent event) {
            Coord left = UI.scale(14, 56);
            int split = split();
            Coord leftSize = Coord.of(split - UI.scale(22), Math.max(UI.scale(120), sz.y - UI.scale(118)));
            if(event.c.x >= left.x && event.c.x < left.x + leftSize.x &&
                    event.c.y >= left.y && event.c.y < left.y + leftSize.y) {
                scrollRow += event.a > 0 ? 1 : -1;
                return(true);
            }
            return(super.mousewheel(event));
        }

        private boolean hit(Coord point, Coord origin, Coord size) {
            return(point.x >= origin.x && point.x < origin.x + size.x &&
                    point.y >= origin.y && point.y < origin.y + size.y);
        }
    }

    private final class RouteMap extends MiniMap {
        private boolean followsPlayer;

        RouteMap(Coord size, haven.MapFile file) {
            super(size, file);
        }

        @Override
        public void tick(double dt) {
            if(!followsPlayer && ui != null && ui.sess != null) {
                follow(new MiniMap.MapLocator(controller.gameUI().map));
                followsPlayer = true;
            }
            super.tick(dt);
        }

        @Override
        public void draw(GOut g) {
            super.draw(g);
            if(dloc == null)
                return;
            ForagingSnapshot view = controller.snapshot();
            Coord previous = null;
            g.chcolor(new Color(MoonFlowerHudTheme.TEAL.getRed(), MoonFlowerHudTheme.TEAL.getGreen(),
                    MoonFlowerHudTheme.TEAL.getBlue(), 210));
            try {
                for(int index = 0; index < view.route.size(); index++) {
                    Coord point = p2c(view.route.get(index));
                    if(previous != null)
                        g.line(previous, point, Math.max(1, UI.scale(2)));
                    previous = point;
                    g.fcircle(point.x, point.y, UI.scale(4), 12);
                    FastText.aprintfstroked(g, point.add(UI.scale(7), -UI.scale(5)), 0, 0.5,
                            "%d", index + 1);
                }
                if(controller.gameUI().map.player() != null) {
                    Coord player = p2c(controller.gameUI().map.player().rc);
                    g.chcolor(MoonFlowerHudTheme.IVORY);
                    g.fcircle(player.x, player.y, UI.scale(5), 12);
                    g.chcolor();
                }
                if(view.target != null) {
                    Coord target = p2c(view.target.coordinate);
                    g.chcolor(MoonFlowerHudTheme.GOLD);
                    g.circle(target.x, target.y, UI.scale(7), 12, Math.max(1, UI.scale(2)));
                    g.chcolor();
                }
            } catch(Loading ignored) {
            } catch(RuntimeException ignored) {
            } finally {
                g.chcolor();
            }
        }

        @Override
        public boolean mousedown(MouseDownEvent event) {
            if((event.b == 1 || event.b == 3) && xlate(event.c) != null) {
                if(event.b == 3 || ui.modshift)
                    controller.removeLastRoutePoint();
                else
                    controller.addRoutePoint(worldCoordinate(xlate(event.c)));
                return(true);
            }
            return(super.mousedown(event));
        }

        private Coord2d worldCoordinate(MiniMap.Location location) {
            if(controller.gameUI().map.player() == null || curloc == null || location == null || location.seg != curloc.seg)
                return(null);
            Coord tile = controller.gameUI().map.player().rc.floor(MCache.tilesz).add(location.tc.sub(curloc.tc));
            return(ForagingMapSafety.tileCenter(tile));
        }
    }

    private static String shortText(String value, int maximum) {
        if(value == null)
            return("unavailable");
        return(value.length() <= maximum ? value : value.substring(0, Math.max(1, maximum - 3)) + "...");
    }
}
