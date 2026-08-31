package haven.wiki;

import haven.Coord;
import haven.GOut;
import haven.MoonFlowerHudSettings;
import haven.MoonFlowerHudTheme;
import haven.Tex;
import haven.UI;
import haven.Widget;

import java.awt.Color;

/** Static-alpha artwork with bounded code-driven reveal and page-turn accents. */
final class WikiOrnamentWidget extends Widget {
    enum Kind {
        CREST,
        ARCHIVE_RAIL
    }

    private final Kind kind;
    private final Tex art;
    private double reveal;
    private double bloom;
    private double mote;

    WikiOrnamentWidget(Kind kind) {
        super(kind == Kind.CREST ? WikiUiAssets.crest.sz() : WikiUiAssets.archiveRail.sz());
        this.kind = kind;
        this.art = kind == Kind.CREST ? WikiUiAssets.crest : WikiUiAssets.archiveRail;
        this.reveal = MoonFlowerHudSettings.codexReducedMotion() ? 1.0 : 0.0;
    }

    void awaken() {
        if(MoonFlowerHudSettings.codexReducedMotion()) {
            reveal = 1.0;
            bloom = 0.0;
        } else {
            reveal = 0.0;
            bloom = 1.0;
        }
    }

    void pageTurn() {
        if(!MoonFlowerHudSettings.codexReducedMotion())
            bloom = 1.0;
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        boolean reduced = MoonFlowerHudSettings.codexReducedMotion();
        reveal = revealAfter(reveal, dt, reduced);
        bloom = reduced ? 0.0 : Math.max(0.0, bloom - (dt * 1.65));
        if(!reduced)
            mote = (mote + (dt * 0.09)) % 1.0;
    }

    @Override
    public void draw(GOut g) {
        int shown = kind == Kind.CREST ? (int)Math.round(sz.x * reveal) :
                (int)Math.round(sz.y * reveal);
        if(kind == Kind.CREST) {
            int left = (sz.x - shown) / 2;
            g.image(art, Coord.z, Coord.of(left, 0), Coord.of(left + shown, sz.y));
            drawBloom(g, sz.div(2), Math.min(sz.x, sz.y) / 3);
        } else {
            int top = sz.y - shown;
            g.image(art, Coord.z, Coord.of(0, top), sz);
            if(!MoonFlowerHudSettings.codexReducedMotion() && reveal >= 1.0) {
                int y = UI.scale(8) + (int)Math.round((sz.y - UI.scale(16)) * mote);
                MoonFlowerHudTheme.drawBlossom(g, Coord.of(sz.x / 2, y), UI.scale(3));
            }
        }
        super.draw(g);
    }

    private void drawBloom(GOut g, Coord center, int baseRadius) {
        if(bloom <= 0.0)
            return;
        int alpha = (int)Math.round(150 * bloom);
        int radius = baseRadius + (int)Math.round(UI.scale(16) * (1.0 - bloom));
        g.chcolor(new Color(MoonFlowerHudTheme.GOLD.getRed(), MoonFlowerHudTheme.GOLD.getGreen(),
                MoonFlowerHudTheme.GOLD.getBlue(), alpha));
        int segments = 32;
        for(int index = 0; index < segments; index++) {
            double a = (Math.PI * 2.0 * index) / segments;
            double b = (Math.PI * 2.0 * (index + 1)) / segments;
            Coord from = center.add((int)Math.round(Math.cos(a) * radius),
                    (int)Math.round(Math.sin(a) * radius));
            Coord to = center.add((int)Math.round(Math.cos(b) * radius),
                    (int)Math.round(Math.sin(b) * radius));
            g.line(from, to, Math.max(1, UI.scale(1)));
        }
        g.chcolor();
    }

    static double revealAfter(double current, double dt, boolean reduced) {
        if(reduced)
            return(1.0);
        return(Math.max(0.0, Math.min(1.0, current + (Math.max(0.0, dt) * 1.8))));
    }
}
