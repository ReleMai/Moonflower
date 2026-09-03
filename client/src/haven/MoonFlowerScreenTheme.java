package haven;

import java.awt.Color;

/** Shared visual language and layout for MoonFlower's entry screens. */
public final class MoonFlowerScreenTheme {
    private static final String[] LOGIN_BACKGROUNDS = {
            "screens/moonflower-login-v2-01-moonrise-valley.png",
            "screens/moonflower-login-v2-02-lantern-fen.png",
            "screens/moonflower-login-v2-03-starlit-grove.png"
    };
    private static final String[] CHARACTER_BACKGROUNDS = {
            "screens/moonflower-character-v2-01-lakeside-homestead.png",
            "screens/moonflower-character-v2-02-dawn-meadow.png",
            "screens/moonflower-character-v2-03-moonlit-harbor.png"
    };
    public static final String LOGIN_BACKGROUND = asset(LOGIN_BACKGROUNDS[0]);
    public static final String CHARACTER_BACKGROUND = asset(CHARACTER_BACKGROUNDS[0]);
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
        Label label = new Label(text, MoonFlowerFont.foundry(size, ACCENT));
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

    static String nextLoginBackground() {
        return(nextBackground("moonflower.login.background.index", LOGIN_BACKGROUNDS));
    }

    static String nextCharacterBackground() {
        return(nextBackground("moonflower.character.background.index", CHARACTER_BACKGROUNDS));
    }

    static String[] loginBackgrounds() {
        return(LOGIN_BACKGROUNDS.clone());
    }

    static String[] characterBackgrounds() {
        return(CHARACTER_BACKGROUNDS.clone());
    }

    private static String nextBackground(String preference, String[] relativePaths) {
        int index = Utils.getprefi(preference, 0);
        if(index < 0 || index >= relativePaths.length)
            index = 0;
        Utils.setprefi(preference, (index + 1) % relativePaths.length);
        return(asset(relativePaths[index]));
    }

    private static String asset(String relativePath) {
        return(Client.gameDir + "res/customclient/" + relativePath);
    }
}
