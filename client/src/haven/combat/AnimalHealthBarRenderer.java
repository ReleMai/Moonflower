package haven.combat;

import haven.Coord;
import haven.GOut;
import haven.Tex;
import haven.Text;
import haven.UI;

import java.awt.Color;

/** Compact cached overhead rendering used by the existing combat-data overlay. */
public final class AnimalHealthBarRenderer {
    public static final int BASE_Y_OFFSET = 112;
    private static final Color FRAME = new Color(0, 0, 0, 220);
    private static final Color BACKGROUND = new Color(45, 45, 45, 210);
    private static final Color UNKNOWN = new Color(120, 120, 120, 180);

    private Tex labelTex;
    private String labelText;

    public void draw(GOut g, Coord anchor, AnimalHealthEstimate estimate) {
        Layout layout = layout(anchor);
        g.chcolor(FRAME);
        g.frect(layout.barTop(), layout.barSize());
        g.chcolor(BACKGROUND);
        g.frect(layout.barTop().add(UI.scale(1), UI.scale(1)),
                layout.barSize().sub(UI.scale(2), UI.scale(2)));

        if(estimate.fraction() != null) {
            int innerWidth = Math.max(0, layout.barSize().x - UI.scale(2));
            int fillWidth = (int)Math.round(innerWidth * estimate.fraction());
            if(fillWidth > 0) {
                g.chcolor(fillColor(estimate.fraction()));
                g.frect(layout.barTop().add(UI.scale(1), UI.scale(1)),
                        new Coord(fillWidth, layout.barSize().y - UI.scale(2)));
            }
        } else {
            g.chcolor(UNKNOWN);
            g.frect(layout.barTop().add(UI.scale(1), UI.scale(1)),
                    new Coord(layout.barSize().x - UI.scale(2), UI.scale(2)));
        }
        g.chcolor(Color.WHITE);

        Tex label = label(estimate.label());
        g.aimage(label, layout.labelCenter(), 0.5, 1.0);
    }

    public static Layout layout(Coord anchor) {
        Coord size = UI.scale(new Coord(94, 8));
        Coord top = new Coord(anchor.x - (size.x / 2), anchor.y - UI.scale(BASE_Y_OFFSET));
        return(new Layout(top, size, new Coord(anchor.x, top.y - UI.scale(2))));
    }

    public static boolean shouldDisplay(boolean enabled, AnimalHealthCatalog.Entry entry) {
        return(enabled && entry != null);
    }

    public void dispose() {
        if(labelTex != null) {
            labelTex.dispose();
            labelTex = null;
        }
        labelText = null;
    }

    private Tex label(String text) {
        if(labelTex == null || !text.equals(labelText)) {
            if(labelTex != null)
                labelTex.dispose();
            labelTex = Text.renderstroked(text, Color.WHITE, Color.BLACK).tex();
            labelText = text;
        }
        return(labelTex);
    }

    private static Color fillColor(double fraction) {
        if(fraction <= 0.25)
            return(new Color(190, 45, 35, 230));
        if(fraction <= 0.55)
            return(new Color(210, 150, 20, 230));
        return(new Color(35, 165, 55, 230));
    }

    public static final class Layout {
        private final Coord barTop;
        private final Coord barSize;
        private final Coord labelCenter;

        public Layout(Coord barTop, Coord barSize, Coord labelCenter) {
            this.barTop = barTop;
            this.barSize = barSize;
            this.labelCenter = labelCenter;
        }

        public Coord barTop() { return(barTop); }
        public Coord barSize() { return(barSize); }
        public Coord labelCenter() { return(labelCenter); }
    }
}
