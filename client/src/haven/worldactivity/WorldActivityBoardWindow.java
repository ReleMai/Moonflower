package haven.worldactivity;

import haven.FastText;
import haven.Coord;
import haven.GOut;
import haven.MoonFlowerHudTheme;
import haven.UI;
import haven.Window;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

/** First-pass presentation shell; the board is intentionally art-light. */
public final class WorldActivityBoardWindow extends Window {
    private static final int DEFAULT_WIDTH = 520;
    private static final int DEFAULT_HEIGHT = 360;
    private final WorldActivityBoardService service;
    private double refreshIn;

    public WorldActivityBoardWindow(WorldActivityBoardService service) {
        super(Coord.of(UI.scale(DEFAULT_WIDTH), UI.scale(DEFAULT_HEIGHT)), "World Activity Board");
        this.service = service;
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        refreshIn -= dt;
        if(refreshIn <= 0.0d) {
            service.refresh();
            refreshIn = 1.0d;
        }
    }

    @Override
    public void cdraw(GOut g) {
        List<WorldActivityEntry> entries = service.snapshot();
        long now = System.currentTimeMillis();
        int pad = UI.scale(8);
        int titleHeight = UI.scale(28);
        int rowHeight = UI.scale(48);
        int rowGap = UI.scale(4);
        int rowWidth = Math.max(UI.scale(120), g.sz().x - (pad * 2));

        MoonFlowerHudTheme.drawPanel(g, Coord.z, g.sz(), 226);
        drawText(g, Coord.of(pad, UI.scale(15)), MoonFlowerHudTheme.IVORY,
                "World Activity  |  " + service.trackedCount() + " tracked  |  "
                        + service.dueCount() + " due now");
        drawText(g, Coord.of(pad, UI.scale(29)), MoonFlowerHudTheme.GOLD_SOFT,
                "Server observations become quiet CALC countdowns");

        int availableRows = Math.max(0, (g.sz().y - titleHeight - pad - UI.scale(2))
                / (rowHeight + rowGap));
        if(entries.isEmpty()) {
            drawText(g, Coord.of(pad, titleHeight + UI.scale(18)), MoonFlowerHudTheme.GOLD_SOFT,
                    "No pyres or localized resources observed in this session.");
            return;
        }
        int shown = Math.min(availableRows, entries.size());
        for(int i = 0; i < shown; i++) {
            WorldActivityEntry entry = entries.get(i);
            WorldActivityState state = entry.state(now);
            int y = titleHeight + pad + (i * (rowHeight + rowGap));
            Coord origin = Coord.of(pad, y);
            Coord size = Coord.of(rowWidth, rowHeight);
            MoonFlowerHudTheme.drawSlot(g, origin, size, true, state == WorldActivityState.RUNNING);
            if(state == WorldActivityState.DUE)
                MoonFlowerHudTheme.drawBlossom(g, origin.add(UI.scale(12), size.y / 2), UI.scale(4));
            else
                MoonFlowerHudTheme.drawCurvedVine(g, origin.add(UI.scale(8), size.y - UI.scale(7)),
                        origin.add(UI.scale(29), size.y - UI.scale(7)), 1.0);

            int textX = origin.x + UI.scale(24);
            int maxTextWidth = size.x - UI.scale(34);
            String primary = fit(entry.label() + "  |  " + entry.type().label(), maxTextWidth);
            drawText(g, Coord.of(textX, origin.y + UI.scale(16)), MoonFlowerHudTheme.IVORY, primary);
            String detail = detail(entry, now);
            Color detailColor = state == WorldActivityState.DUE ? MoonFlowerHudTheme.RUBY
                    : state == WorldActivityState.RUNNING ? MoonFlowerHudTheme.TEAL_BRIGHT
                    : MoonFlowerHudTheme.GOLD_SOFT;
            drawText(g, Coord.of(textX, origin.y + UI.scale(33)), detailColor,
                    fit(detail, maxTextWidth));
        }
        if(shown < entries.size()) {
            drawText(g, Coord.of(pad, g.sz().y - UI.scale(5)), MoonFlowerHudTheme.GOLD_SOFT,
                    "+ " + (entries.size() - shown) + " more session entries");
        }
    }

    private static String detail(WorldActivityEntry entry, long now) {
        String quality = entry.quality() == null ? "QL unavailable" : "QL " + formatQuality(entry.quality());
        String status;
        if(entry.hasTimer())
            status = entry.state(now) == WorldActivityState.DUE
                    ? "DUE NOW  •  CALC" : "CALC " + entry.remainingText(now);
        else
            status = "LIVE  •  awaiting Inspect timer";
        if(entry.type() == WorldActivityType.PYRE) {
            status += "  |  LIVE " + entry.fuelState().label();
        }
        if(!entry.visible())
            status += "  |  last seen";
        return("LIVE " + quality + "  |  " + status);
    }

    private static String formatQuality(Double quality) {
        if(quality == null)
            return("unavailable");
        if(Math.rint(quality) == quality)
            return(String.format(Locale.ROOT, "%.0f", quality));
        return(String.format(Locale.ROOT, "%.2f", quality));
    }

    private static String fit(String text, int width) {
        text = safeText(text);
        if(text == null || text.isEmpty() || FastText.textw(text) <= width)
            return(text == null ? "" : text);
        String suffix = "...";
        int end = text.length();
        while(end > 0 && FastText.textw(text.substring(0, end) + suffix) > width)
            end--;
        return(end <= 0 ? suffix : text.substring(0, end) + suffix);
    }

    private static void drawText(GOut g, Coord c, Color color, String text) {
        g.chcolor(color);
        FastText.aprintfstroked(g, c, 0.0, 0.5, "%s", safeText(text));
        g.chcolor();
    }

    private static String safeText(String text) {
        if(text == null)
            return("");
        StringBuilder safe = new StringBuilder(text.length());
        for(int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            safe.append((c >= 32 && c <= 255) ? c : '?');
        }
        return(safe.toString());
    }

}
