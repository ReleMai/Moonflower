package haven;

import java.awt.Color;

/** Shared visual language and layout for MoonFlower's entry screens. */
public final class MoonFlowerScreenTheme {
    public static final String LOGIN_BACKGROUND = Client.gameDir + "res/customclient/moonflower-login-v1.png";
    public static final String CHARACTER_BACKGROUND = Client.gameDir + "res/customclient/moonflower-character-select-v1.png";
    public static final String LOGIN_MUSIC = "res/customclient/music/moonflower-login.wav";
    public static final String CHARACTER_MUSIC = "res/customclient/music/moonflower-homecoming.wav";

    public static final Color PANEL = new Color(5, 15, 28, 218);
    public static final Color PANEL_SOFT = new Color(8, 23, 39, 190);
    public static final Color BORDER = new Color(158, 188, 207, 190);
    public static final Color ACCENT = new Color(231, 241, 245, 235);
    public static final Color MUTED = new Color(174, 194, 205);

    public static final Coord CHAR_PARENT = UI.scale(1067, 600);

    public static final Coord LOGIN_LEFT_POS = UI.scale(40, 118);
    public static final Coord LOGIN_LEFT_SZ = UI.scale(320, 400);
    public static final Coord LOGIN_RIGHT_POS = UI.scale(640, 118);
    public static final Coord LOGIN_RIGHT_SZ = UI.scale(390, 400);

    public static final Coord CHAR_LIST_POS = UI.scale(48, 108);
    public static final Coord CHAR_PREVIEW_POS = UI.scale(430, 108);
    public static final Coord CHAR_PREVIEW_SZ = UI.scale(590, 405);
    public static final Coord CHAR_AVA_POS = UI.scale(470, 150);

    private MoonFlowerScreenTheme() {
    }

    public static Label title(String text, int size) {
        Label label = new Label(text, new Text.Foundry(Text.serif, size).aa(true));
        label.setcolor(ACCENT);
        return(label);
    }

    public static Label subtitle(String text) {
        Label label = new Label(text, new Text.Foundry(Text.sans, 14).aa(true));
        label.setcolor(MUTED);
        return(label);
    }

    public static class Panel extends Widget {
        private final boolean soft;

        public Panel(Coord sz) {
            this(sz, false);
        }

        public Panel(Coord sz, boolean soft) {
            super(sz);
            this.soft = soft;
        }

        public void draw(GOut g) {
            g.chcolor(soft ? PANEL_SOFT : PANEL);
            g.frect(Coord.z, sz);
            g.chcolor(BORDER);
            g.rect(Coord.z, sz);
            g.chcolor(ACCENT);
            g.frect(Coord.z, new Coord(sz.x, UI.scale(2)));
            g.chcolor();
            super.draw(g);
        }
    }
}
