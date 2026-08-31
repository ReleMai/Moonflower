package haven.foraging;

import haven.Coord;
import haven.FastText;
import haven.GOut;
import haven.MoonFlowerHudSettings;
import haven.MoonFlowerHudTheme;
import haven.UI;
import haven.Widget;
import haven.Window;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.List;

/** Themed, presentation-only control surface for the Phase 1 controller. */
public final class ForagingWindow extends Window {
    private static final Coord PREFERRED = UI.scale(720, 520);
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
        return(Coord.of(Math.min(PREFERRED.x, Math.max(320, available.x - margin.x)),
                Math.min(PREFERRED.y, Math.max(280, available.y - margin.y))));
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
        private final LeafControl start;
        private final LeafControl pause;
        private final LeafControl stop;
        private final LeafControl selectAll;
        private final LeafControl clearAll;
        private int scrollRow;

        WayfinderCanvas(Coord size) {
            super(size);
            start = add(new LeafControl(UI.scale(108, 32), "START / RESUME",
                    controller::startOrResume, false), Coord.z);
            pause = add(new LeafControl(UI.scale(88, 32), "PAUSE",
                    () -> controller.pause("Paused by user.", true), false), Coord.z);
            stop = add(new LeafControl(UI.scale(88, 32), "STOP",
                    () -> controller.emergencyStop("Emergency stop requested by user."), true), Coord.z);
            selectAll = add(new LeafControl(UI.scale(72, 24), "SELECT ALL",
                    () -> controller.setAllSelected(true), false), Coord.z);
            clearAll = add(new LeafControl(UI.scale(62, 24), "CLEAR",
                    () -> controller.setAllSelected(false), false), Coord.z);
            layoutControls();
        }

        void layoutControls() {
            int y = sz.y - UI.scale(45);
            start.c = Coord.of(UI.scale(18), y);
            pause.c = Coord.of(UI.scale(134), y);
            stop.c = Coord.of(UI.scale(230), y);
            int split = Math.max(UI.scale(310), (int)(sz.x * 0.52));
            selectAll.c = Coord.of(Math.max(UI.scale(24), split - UI.scale(154)), UI.scale(62));
            clearAll.c = Coord.of(Math.max(UI.scale(100), split - UI.scale(78)), UI.scale(62));
        }

        @Override
        public void draw(GOut g) {
            ForagingSnapshot view = controller.snapshot();
            MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 235);
            MoonFlowerHudTheme.drawWindowFrame(g, Coord.z, sz);
            int split = Math.max(UI.scale(310), (int)(sz.x * 0.52));
            Coord left = UI.scale(14, 56);
            Coord leftSize = Coord.of(split - UI.scale(22), sz.y - UI.scale(118));
            Coord right = Coord.of(split + UI.scale(4), UI.scale(56));
            Coord rightSize = Coord.of(sz.x - right.x - UI.scale(14), sz.y - UI.scale(72));
            MoonFlowerHudTheme.drawPanel(g, left, leftSize, 205);
            MoonFlowerHudTheme.drawPanel(g, right, rightSize, 205);
            drawHeader(g, view);
            drawCatalog(g, view, left, leftSize);
            drawCompassAndStatus(g, view, right, rightSize);
            super.draw(g);
        }

        private void drawHeader(GOut g, ForagingSnapshot view) {
            MoonFlowerHudTheme.drawCurvedVine(g, UI.scale(22, 34), Coord.of(sz.x - UI.scale(22), UI.scale(34)), 1.0);
            MoonFlowerHudTheme.drawBlossom(g, Coord.of(sz.x / 2, UI.scale(34)), UI.scale(7));
            FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(15)), 0.5, 0.5,
                    "BOTANICAL WAYFINDER  ·  PHASE 1");
            FastText.aprintfstroked(g, Coord.of(sz.x / 2, UI.scale(46)), 0.5, 0.5,
                    "%s  ·  %s", view.state.name(), shortText(view.reason, 78));
        }

        private void drawCatalog(GOut g, ForagingSnapshot view, Coord origin, Coord size) {
            FastText.aprintfstroked(g, origin.add(UI.scale(10), UI.scale(18)), 0, 0.5,
                    "ALL FORAGEABLES  ·  exact pickup selection");
            List<ForagingGobScanner.HerbResource> herbs = view.catalog;
            int cardHeight = UI.scale(38);
            int top = origin.y + UI.scale(58);
            int availableRows = Math.max(1, (size.y - UI.scale(68)) / cardHeight);
            int maxScroll = Math.max(0, herbs.size() - availableRows);
            scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));
            if(herbs.isEmpty()) {
                FastText.aprintfstroked(g, origin.add(size.x / 2, size.y / 2), 0.5, 0.5,
                        "No forageable Gobs are loaded");
                return;
            }
            for(int row = 0; row < availableRows && scrollRow + row < herbs.size(); row++) {
                ForagingGobScanner.HerbResource herb = herbs.get(scrollRow + row);
                Coord card = Coord.of(origin.x + UI.scale(8), top + row * cardHeight);
                Coord cardSize = Coord.of(size.x - UI.scale(16), cardHeight - UI.scale(4));
                boolean selected = view.selectedResources.contains(herb.resourceName);
                MoonFlowerHudTheme.drawLeafButton(g, card, cardSize, selected, false);
                FastText.aprintfstroked(g, card.add(UI.scale(14), UI.scale(12)), 0, 0.5,
                        "%s  %s  ·  %s", selected ? "[PICK]" : "[SKIP]",
                        shortText(herb.displayName, 23), herb.live ? "LIVE" : "GUIDE");
                FastText.aprintfstroked(g, card.add(UI.scale(14), UI.scale(26)), 0, 0.5,
                        "%s  ·  %s", herb.category, shortText(herb.resourceName, 38));
            }
        }

        private void drawCompassAndStatus(GOut g, ForagingSnapshot view, Coord origin, Coord size) {
            boolean compact = size.y < UI.scale(340);
            Coord center = Coord.of(origin.x + size.x / 2,
                    origin.y + (compact ? UI.scale(54) : UI.scale(82)));
            int radius = Math.min(compact ? UI.scale(34) : UI.scale(50),
                    Math.max(UI.scale(24), size.x / 5));
            MoonFlowerHudTheme.drawCircularSlot(g, center, radius, view.active());
            int pulse = MoonFlowerHudSettings.hudReducedMotion() ? 0 :
                    (view.active() ? (int)Math.round(Math.sin(System.currentTimeMillis() / 280.0) * UI.scale(2)) : 0);
            MoonFlowerHudTheme.drawBlossom(g, center, Math.max(UI.scale(8), UI.scale(11) + pulse));
            drawDirectionPicker(g, view, center, radius);
            if(view.target != null) {
                Coord needle = center.add((int)Math.round(Math.cos(view.targetBearing) * (radius - UI.scale(8))),
                        (int)Math.round(Math.sin(view.targetBearing) * (radius - UI.scale(8))));
                g.chcolor(MoonFlowerHudTheme.GOLD);
                g.line(center, needle, Math.max(2, UI.scale(2)));
                g.chcolor();
            }
            int textY = center.y + radius + UI.scale(18);
            FastText.aprintfstroked(g, Coord.of(center.x, textY), 0.5, 0.5, "%s",
                    view.target == null ? "No current target" : shortText(view.target.displayName, 28));
            FastText.aprintfstroked(g, Coord.of(center.x, textY + UI.scale(18)), 0.5, 0.5,
                    "CALC  distance: %s", view.targetDistance < 0 ? "unavailable" : String.format("%.1f", view.targetDistance));
            int infoY = textY + (compact ? UI.scale(32) : UI.scale(48));
            FastText.aprintfstroked(g, origin.add(UI.scale(12), infoY - origin.y), 0, 0.5,
                    "LEARNED  travel: %s  ·  point %d / %d", view.direction.label,
                    Math.min(view.routeIndex + 1, view.routeSize), view.routeSize);
            FastText.aprintfstroked(g, origin.add(UI.scale(12), infoY - origin.y + UI.scale(19)), 0, 0.5,
                    "CALC  free cells: %s  reserve: %d", view.freeCells < 0 ? "?" : Integer.toString(view.freeCells), view.reserveCells);
            FastText.aprintfstroked(g, origin.add(UI.scale(12), infoY - origin.y + UI.scale(38)), 0, 0.5,
                    "LEARNED  acknowledged yield: %d", view.yield);
            if(compact)
                return;
            FastText.aprintfstroked(g, origin.add(UI.scale(12), infoY - origin.y + UI.scale(66)), 0, 0.5, "EVENT LEDGER");
            int eventY = infoY + UI.scale(84);
            int shown = Math.max(1, (origin.y + size.y - eventY - UI.scale(8)) / UI.scale(17));
            for(int index = 0; index < Math.min(shown, view.events.size()); index++)
                FastText.aprintfstroked(g, Coord.of(origin.x + UI.scale(12), eventY + index * UI.scale(17)),
                        0, 0.5, "%s", shortText(view.events.get(index), 46));
        }

        private void drawDirectionPicker(GOut g, ForagingSnapshot view, Coord center, int radius) {
            int orbit = radius + UI.scale(18);
            for(ForagingDirection direction : ForagingDirection.values()) {
                if(direction.usesCheckpointRoute())
                    continue;
                Coord directionCenter = directionCenter(center, orbit, direction);
                MoonFlowerHudTheme.drawCircularSlot(g, directionCenter, UI.scale(10),
                        view.direction == direction);
                FastText.aprintfstroked(g, directionCenter, 0.5, 0.5, "%s", direction.label);
            }
            FastText.aprintfstroked(g, center.add(0, UI.scale(20)), 0.5, 0.5,
                    "%s", view.direction.usesCheckpointRoute() ? "ROUTE" : view.direction.label);
            FastText.aprintfstroked(g, center.add(0, UI.scale(34)), 0.5, 0.5,
                    "CENTER: ROUTE");
        }

        @Override
        public boolean mousedown(MouseDownEvent event) {
            ForagingSnapshot view = controller.snapshot();
            int split = Math.max(UI.scale(310), (int)(sz.x * 0.52));
            Coord origin = UI.scale(14, 56);
            Coord size = Coord.of(split - UI.scale(22), sz.y - UI.scale(118));
            int cardHeight = UI.scale(38);
            int top = origin.y + UI.scale(58);
            if(event.b == 1 && event.c.x >= origin.x + UI.scale(8) && event.c.x < origin.x + size.x - UI.scale(8) &&
                    event.c.y >= top && event.c.y < origin.y + size.y) {
                int index = scrollRow + ((event.c.y - top) / cardHeight);
                if(index >= 0 && index < view.catalog.size()) {
                    controller.toggleSelection(view.catalog.get(index).resourceName);
                    return(true);
                }
            }
            Coord right = Coord.of(split + UI.scale(4), UI.scale(56));
            Coord rightSize = Coord.of(sz.x - right.x - UI.scale(14), sz.y - UI.scale(72));
            boolean compact = rightSize.y < UI.scale(340);
            Coord center = Coord.of(right.x + rightSize.x / 2,
                    right.y + (compact ? UI.scale(54) : UI.scale(82)));
            int radius = Math.min(compact ? UI.scale(34) : UI.scale(50),
                    Math.max(UI.scale(24), rightSize.x / 5));
            int pickRadius = UI.scale(14);
            if(event.b == 1) {
                if(event.c.dist(center) <= pickRadius) {
                    controller.selectDirection(ForagingDirection.ROUTE);
                    return(true);
                }
                int orbit = radius + UI.scale(18);
                for(ForagingDirection direction : ForagingDirection.values()) {
                    if(!direction.usesCheckpointRoute() &&
                            event.c.dist(directionCenter(center, orbit, direction)) <= pickRadius) {
                        controller.selectDirection(direction);
                        return(true);
                    }
                }
            }
            return(super.mousedown(event));
        }

        @Override
        public boolean mousewheel(MouseWheelEvent event) {
            scrollRow += event.a > 0 ? 1 : -1;
            return(true);
        }
    }

    private static Coord directionCenter(Coord center, int orbit, ForagingDirection direction) {
        Coord vector = direction.tileVector;
        double length = Math.sqrt((vector.x * vector.x) + (vector.y * vector.y));
        return(center.add((int)Math.round((vector.x / length) * orbit),
                (int)Math.round((vector.y / length) * orbit)));
    }

    private static final class LeafControl extends Widget {
        private final String label;
        private final Runnable action;
        private final boolean danger;
        private boolean hover;

        LeafControl(Coord size, String label, Runnable action, boolean danger) {
            super(size);
            this.label = label;
            this.action = action;
            this.danger = danger;
        }

        @Override
        public void draw(GOut g) {
            MoonFlowerHudTheme.drawLeafButton(g, Coord.z, sz, hover, hover);
            if(danger) {
                g.chcolor(new Color(MoonFlowerHudTheme.RUBY.getRed(), MoonFlowerHudTheme.RUBY.getGreen(),
                        MoonFlowerHudTheme.RUBY.getBlue(), 180));
                g.rect(UI.scale(3, 3), sz.sub(UI.scale(6, 6)));
                g.chcolor();
            }
            FastText.aprintfstroked(g, sz.div(2), 0.5, 0.5, "%s", label);
        }

        @Override
        public boolean mousedown(MouseDownEvent event) {
            if(event.b == 1) {
                action.run();
                return(true);
            }
            return(false);
        }

        @Override
        public void mousemove(MouseMoveEvent event) {
            hover = event.c.isect(Coord.z, sz);
        }

    }

    private static String shortText(String value, int maximum) {
        if(value == null)
            return("unavailable");
        return(value.length() <= maximum ? value : value.substring(0, Math.max(1, maximum - 3)) + "...");
    }
}
