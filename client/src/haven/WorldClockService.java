package haven;

import haven.MCache;

/** Resolves live astronomy and current terrain without touching map persistence. */
public final class WorldClockService {
    private final GameUI gui;
    private Astronomy anchoredAstronomy;
    private long anchoredAtGameSecond;
    private long anchoredSeasonRemaining;

    public WorldClockService(GameUI gui) {
        this.gui = gui;
    }

    public WorldClockSnapshot capture() {
        Glob glob = gui.ui == null || gui.ui.sess == null ? null : gui.ui.sess.glob;
        Astronomy astronomy = glob == null ? null : glob.ast;
        long gameSeconds = glob == null ? 0L : (long)Math.floor(glob.globtime());
        WorldClockSnapshot snapshot = WorldClockSnapshot.create(astronomy, gameSeconds, terrain(),
                UI.provinceName, UI.realmName);
        if(!snapshot.available)
            return snapshot;
        if(astronomy != anchoredAstronomy || gameSeconds < anchoredAtGameSecond) {
            anchoredAstronomy = astronomy;
            anchoredAtGameSecond = gameSeconds;
            anchoredSeasonRemaining = snapshot.seasonRemainingSeconds;
        }
        long elapsedGameSeconds = Math.max(0L, gameSeconds - anchoredAtGameSecond);
        return snapshot.withSeasonRemaining(anchoredSeasonRemaining - elapsedGameSeconds);
    }

    private String terrain() {
        if(gui.map == null || gui.ui == null || gui.ui.sess == null)
            return "";
        Gob player = gui.map.player();
        if(player == null)
            return "";
        try {
            MCache map = gui.ui.sess.glob.map;
            int tile = map.gettile(player.rc.floor(MCache.tilesz));
            Resource resource = map.tilesetr(tile);
            if(resource == null)
                return "";
            String name = Utils.prettyResName(resource.name);
            if(name.equals("Water"))
                return "Shallow Water";
            if(name.equals("Deep"))
                return "Deep Water";
            if(name.equals("Owater"))
                return "Shallow Ocean";
            if(name.equals("Odeep"))
                return "Deep Ocean";
            if(name.equals("Odeeper"))
                return "Very Deep Ocean";
            return name;
        } catch(Loading ignored) {
            return "";
        }
    }
}
