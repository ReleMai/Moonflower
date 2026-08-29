package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static java.lang.Math.PI;

/** Classic-compatible top clock with a portrait-HUD-inspired celestial crest. */
public final class MoonFlowerClockWidget extends Widget {
    private static final Coord FACE_SIZE = UI.scale(Coord.of(450, 151));
    private static final Coord FACE_CENTER = UI.scale(Coord.of(225, 73));
    private static final Coord CREST_SIZE = UI.scale(Coord.of(145, 130));
    private static final Coord CREST_OPENING_CENTER = UI.scale(Coord.of(73, 77));
    private static final Color MIDNIGHT = new Color(4, 12, 31, 255);
    private static final Color DAWN = new Color(159, 76, 76, 255);
    private static final Color DAY = new Color(53, 135, 150, 255);
    private static final Color SUNSET = new Color(190, 91, 54, 255);

    private final Cal classic;
    private final WorldClockService service;
    private final Tex crestTex;
    private boolean moonFlower;
    private long nextRefresh;
    private WorldClockSnapshot snapshot = WorldClockSnapshot.unavailable("", "", "");
    private Tex timeTex;
    private Tex dateTex;
    private Tex seasonTex;
    private Tex seasonCountdownTex;
    private Tex moonTex;
    private Tex areaTex;
    private Tex noticeTex;
    private Text details;
    private String detailsKey = "";

    public MoonFlowerClockWidget(GameUI gui) {
        service = new WorldClockService(gui);
        crestTex = buildCrestTexture();
        classic = add(new Cal(), Coord.z);
        moonFlower = MoonFlowerHudTheme.active();
        classic.show(!moonFlower);
        resize(moonFlower ? FACE_SIZE : classic.sz);
    }

    @Override
    public void tick(double dt) {
        applyMode();
        if(moonFlower) {
            long now = System.currentTimeMillis();
            if(now >= nextRefresh) {
                refresh();
                nextRefresh = now + 1000L;
            }
        }
        super.tick(dt);
    }

    @Override
    public void draw(GOut g) {
        applyMode();
        if(moonFlower)
            drawMoonFlower(g);
        super.draw(g);
    }

    @Override
    public boolean checkhit(Coord c) {
        return moonFlower ? c.isect(Coord.z, sz) : classic.checkhit(c);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        return moonFlower ? details : classic.tooltip(c, prev);
    }

    @Override
    public void dispose() {
        disposeText();
        crestTex.dispose();
        if(details != null)
            details.dispose();
        super.dispose();
    }

    private void applyMode() {
        boolean active = MoonFlowerHudTheme.active();
        if(active == moonFlower)
            return;
        moonFlower = active;
        classic.show(!active);
        resize(active ? FACE_SIZE : classic.sz);
        if(parent instanceof GameUI.Hidepanel)
            ((GameUI.Hidepanel)parent).move();
        if(active) {
            nextRefresh = 0;
            refresh();
        }
    }

    private void refresh() {
        snapshot = service.capture();
        disposeText();
        if(!snapshot.available) {
            timeTex = render("--:--", MoonFlowerHudTheme.IVORY, Text.num20boldFnd);
            dateTex = render("AWAITING ASTRONOMY", MoonFlowerHudTheme.IVORY, Text.num12boldFnd);
            seasonTex = seasonCountdownTex = moonTex = areaTex = null;
            noticeTex = render("LIVE  -  " + snapshot.notice, MoonFlowerHudTheme.TEAL_BRIGHT, Text.std);
        } else {
            timeTex = render(snapshot.timeLabel(false), MoonFlowerHudTheme.IVORY, Text.num20boldFnd);
            dateTex = render(String.format("Day %d  ·  Month %d  ·  Year %d",
                    snapshot.calendarDay, snapshot.calendarMonth, snapshot.calendarYear),
                    MoonFlowerHudTheme.IVORY, Text.std);
            seasonTex = render(snapshot.season + "  " + snapshot.seasonDay + "/" + snapshot.seasonLength,
                    MoonFlowerHudTheme.GOLD, Text.num12boldFnd);
            seasonCountdownTex = render(seasonCountdown(snapshot), MoonFlowerHudTheme.IVORY, Text.std);
            moonTex = render(snapshot.moonPhase, MoonFlowerHudTheme.TEAL_BRIGHT, Text.num12boldFnd);
            areaTex = render(snapshot.areaLabel(), MoonFlowerHudTheme.IVORY, Text.std);
            noticeTex = render(snapshot.noticeProvenance + "  -  " + snapshot.notice,
                    snapshot.noticeProvenance.equals("LIVE") ? MoonFlowerHudTheme.TEAL_BRIGHT :
                            MoonFlowerHudTheme.GOLD, Text.std);
        }
        String detailText = detailText();
        if(!detailText.equals(detailsKey)) {
            if(details != null)
                details.dispose();
            details = RichText.render(detailText, UI.scale(360));
            detailsKey = detailText;
        }
    }

    private void drawMoonFlower(GOut g) {
        Coord left = UI.scale(Coord.of(111, 75));
        Coord right = UI.scale(Coord.of(339, 75));
        drawWing(g, left, true);
        drawWing(g, right, false);
        drawNoticeCradle(g);
        drawCelestialFace(g);

        int wingWidth = UI.scale(142);
        drawCenteredClipped(g, seasonTex, UI.scale(Coord.of(106, 56)), wingWidth, UI.scale(16));
        drawCenteredClipped(g, seasonCountdownTex, UI.scale(Coord.of(106, 74)), wingWidth, UI.scale(13));
        drawCenteredClipped(g, dateTex, UI.scale(Coord.of(106, 90)), wingWidth, UI.scale(13));

        drawCenteredClipped(g, moonTex, UI.scale(Coord.of(344, 57)), wingWidth, UI.scale(16));
        drawCenteredClipped(g, areaTex, UI.scale(Coord.of(344, 78)), wingWidth, UI.scale(14));
        drawCenteredClipped(g, noticeTex, UI.scale(Coord.of(225, 138)), UI.scale(326), UI.scale(13));
    }

    private void drawWing(GOut g, Coord center, boolean left) {
        Coord outer = UI.scale(Coord.of(106, 35));
        fillEllipse(g, center, outer, MoonFlowerHudTheme.INK_DEEP);
        fillEllipse(g, center, outer.sub(UI.scale(2), UI.scale(2)), MoonFlowerHudTheme.GOLD_SOFT);
        fillEllipse(g, center, outer.sub(UI.scale(4), UI.scale(4)), MoonFlowerHudTheme.TEAL);
        fillEllipse(g, center, outer.sub(UI.scale(7), UI.scale(7)), new Color(3, 18, 25, 248));

        int direction = left ? -1 : 1;
        Coord blossom = center.add(direction * UI.scale(88), 0);
        Coord root = center.add(-direction * UI.scale(72), UI.scale(18));
        MoonFlowerHudTheme.drawCurvedVine(g, blossom, root, 1.0);
        MoonFlowerHudTheme.drawBlossom(g, blossom, UI.scale(4));
        drawScroll(g, center.add(direction * UI.scale(102), 0), left);
    }

    private void drawScroll(GOut g, Coord center, boolean left) {
        int radius = UI.scale(11);
        g.chcolor(MoonFlowerHudTheme.INK_DEEP);
        g.fellipse(center, Coord.of(radius, radius));
        g.chcolor(MoonFlowerHudTheme.GOLD_SOFT);
        int segments = 20;
        for(int i = 0; i < segments; i++) {
            double a1 = (PI * 2 * i) / segments;
            double a2 = (PI * 2 * (i + 1)) / segments;
            Coord p1 = center.add((int)Math.round(Math.cos(a1) * radius),
                    (int)Math.round(Math.sin(a1) * radius));
            Coord p2 = center.add((int)Math.round(Math.cos(a2) * radius),
                    (int)Math.round(Math.sin(a2) * radius));
            g.line(p1, p2, Math.max(1, UI.scale(2)));
        }
        Coord curlEnd = center.add(left ? UI.scale(5) : -UI.scale(5), UI.scale(2));
        MoonFlowerHudTheme.drawCurvedVine(g, center.add(left ? -radius : radius, 0), curlEnd, 1.0);
        g.chcolor();
    }

    private void drawNoticeCradle(GOut g) {
        Coord center = UI.scale(Coord.of(225, 138));
        Coord outer = UI.scale(Coord.of(171, 13));
        fillEllipse(g, center, outer, MoonFlowerHudTheme.INK_DEEP);
        fillEllipse(g, center, outer.sub(UI.scale(2), UI.scale(2)), MoonFlowerHudTheme.GOLD_SOFT);
        fillEllipse(g, center, outer.sub(UI.scale(4), UI.scale(4)), new Color(3, 18, 25, 248));
        MoonFlowerHudTheme.drawCurvedVine(g, center.sub(UI.scale(154), 0), center.add(UI.scale(154), 0), 1.0);
    }

    private void drawCelestialFace(GOut g) {
        drawSkyDisc(g, FACE_CENTER, UI.scale(43));
        g.image(crestTex, FACE_CENTER.sub(CREST_OPENING_CENTER));
        drawCenteredClipped(g, timeTex, FACE_CENTER.add(0, UI.scale(16)), UI.scale(82), UI.scale(23));
    }

    private void drawSkyDisc(GOut g, Coord center, int radius) {
        double dayFraction = snapshot.available ? snapshot.dayFraction : 0.0;
        Color sky = skyColor(dayFraction);
        for(int y = -radius; y <= radius; y++) {
            int halfWidth = (int)Math.floor(Math.sqrt(Math.max(0, (radius * radius) - (y * y))));
            double position = (y + radius) / (double)Math.max(1, radius * 2);
            g.chcolor(shade(sky, 1.03 - (position * 0.28)));
            g.frect(center.add(-halfWidth, y), Coord.of((halfWidth * 2) + 1, 1));
        }

        if(snapshot.available) {
            int orbit = UI.scale(27);
            long now = System.currentTimeMillis();
            int sunFrame = MoonFlowerHudSettings.clockReducedMotion() ? 0 :
                    (int)((now / Cal.sun.d) % Cal.sun.f.length);
            Resource.Image sun = Cal.sun.f[sunFrame][0];
            Resource.Image moon = Cal.moon.f[snapshot.moonIndex][0];
            Coord sunAt = Coord.sc((dayFraction + 0.75) * 2 * PI, orbit).add(center).sub(sun.ssz.div(2));
            Coord moonAt = Coord.sc((dayFraction + 0.25) * 2 * PI, orbit).add(center).sub(moon.ssz.div(2));
            g.image(sun, sunAt);
            Astronomy astronomy = ui == null || ui.sess == null ? null : ui.sess.glob.ast;
            if(astronomy != null)
                g.chcolor(astronomy.mc);
            g.image(moon, moonAt);
            g.chcolor();
        }

        int horizonY = center.y + UI.scale(10);
        int halfHorizon = (int)Math.floor(Math.sqrt(Math.max(0,
                (radius * radius) - ((horizonY - center.y) * (horizonY - center.y)))));
        g.chcolor(new Color(2, 22, 25, 235));
        for(int y = horizonY; y <= center.y + radius; y++) {
            int dy = y - center.y;
            int halfWidth = (int)Math.floor(Math.sqrt(Math.max(0, (radius * radius) - (dy * dy))));
            g.frect(Coord.of(center.x - halfWidth, y), Coord.of((halfWidth * 2) + 1, 1));
        }
        g.chcolor(MoonFlowerHudTheme.GOLD_SOFT);
        g.line(Coord.of(center.x - halfHorizon, horizonY), Coord.of(center.x + halfHorizon, horizonY),
                Math.max(1, UI.scale(1)));
        g.chcolor();
        MoonFlowerHudTheme.drawCurvedVine(g, Coord.of(center.x - halfHorizon + UI.scale(3), horizonY + UI.scale(5)),
                Coord.of(center.x + halfHorizon - UI.scale(3), horizonY + UI.scale(5)), 1.0);
    }

    private static void fillEllipse(GOut g, Coord center, Coord radius, Color color) {
        g.chcolor(color);
        g.fellipse(center, radius);
        g.chcolor();
    }

    private static String seasonCountdown(WorldClockSnapshot snapshot) {
        long days = snapshot.seasonRemainingSeconds / (24 * 60 * 60);
        long hours = (snapshot.seasonRemainingSeconds % (24 * 60 * 60)) / (60 * 60);
        long minutes = (snapshot.seasonRemainingSeconds % (60 * 60)) / 60;
        return String.format("%dd %02dh %02dm to %s", days, hours, minutes, snapshot.nextSeason);
    }

    private static Tex buildCrestTexture() {
        BufferedImage source = MoonFlowerHudAssets.dockOrnament;
        Coord opening = MoonFlowerHudAssets.portraitCenter;
        int diameter = MoonFlowerHudAssets.portraitOpeningDiameter;
        int cropWidth = Math.min(source.getWidth(), (int)Math.round(diameter * 1.644));
        int cropHeight = Math.min(source.getHeight(), (int)Math.round(diameter * 1.472));
        int cropX = Utils.clip(opening.x - (cropWidth / 2), 0, source.getWidth() - cropWidth);
        int cropY = Utils.clip(opening.y - (int)Math.round(diameter * 0.869),
                0, source.getHeight() - cropHeight);
        BufferedImage crown = source.getSubimage(cropX, cropY, cropWidth, cropHeight);
        return new TexI(PUtils.convolvedown(crown, CREST_SIZE, new PUtils.Lanczos(3)));
    }

    static Color skyColor(double fraction) {
        fraction = fraction - Math.floor(fraction);
        if(fraction < 0.18)
            return MIDNIGHT;
        if(fraction < 0.25)
            return blend(MIDNIGHT, DAWN, (fraction - 0.18) / 0.07);
        if(fraction < 0.32)
            return blend(DAWN, DAY, (fraction - 0.25) / 0.07);
        if(fraction < 0.68)
            return DAY;
        if(fraction < 0.75)
            return blend(DAY, SUNSET, (fraction - 0.68) / 0.07);
        if(fraction < 0.82)
            return blend(SUNSET, MIDNIGHT, (fraction - 0.75) / 0.07);
        return MIDNIGHT;
    }

    private static Color blend(Color from, Color to, double amount) {
        amount = Utils.clip(amount, 0.0, 1.0);
        return new Color(
                (int)Math.round(from.getRed() + ((to.getRed() - from.getRed()) * amount)),
                (int)Math.round(from.getGreen() + ((to.getGreen() - from.getGreen()) * amount)),
                (int)Math.round(from.getBlue() + ((to.getBlue() - from.getBlue()) * amount)), 255);
    }

    private static Color shade(Color color, double amount) {
        return new Color((int)Math.round(color.getRed() * amount),
                (int)Math.round(color.getGreen() * amount),
                (int)Math.round(color.getBlue() * amount), color.getAlpha());
    }

    private static Tex render(String text, Color color, Text.Foundry foundry) {
        return Text.renderstroked(text, color, Color.BLACK, foundry).tex();
    }

    private static void drawClipped(GOut g, Tex texture, Coord origin, int width, int height) {
        if(texture == null || width <= 0 || height <= 0)
            return;
        g.reclip(origin, Coord.of(width, height)).image(texture, Coord.z);
    }

    private static void drawCenteredClipped(GOut g, Tex texture, Coord center, int width, int height) {
        if(texture == null || width <= 0 || height <= 0)
            return;
        Coord origin = Coord.of(center.x - (width / 2), center.y - (height / 2));
        Coord textureOrigin = Coord.of((width - texture.sz().x) / 2, 0);
        g.reclip(origin, Coord.of(width, height)).image(texture, textureOrigin);
    }

    private String detailText() {
        if(!snapshot.available)
            return "$col[239,225,185]{MoonFlower World Clock}\nAwaiting live server astronomy.";
        return String.format("$col[239,225,185]{MoonFlower World Clock}\n" +
                        "%s (game time), absolute day %d\n%s\n%s\nMoon: %s\nArea: %s\n\n" +
                        "$col[207,164,72]{%s}: %s\n" +
                        "$col[150,180,180]{LIVE = server/session data; GUIDE = community-maintained guidance.}",
                snapshot.timeLabel(false), snapshot.gameDay, snapshot.dateLabel(), snapshot.seasonLabel(),
                snapshot.moonPhase, snapshot.areaLabel(), snapshot.noticeProvenance, snapshot.notice);
    }

    private void disposeText() {
        Tex[] textures = {timeTex, dateTex, seasonTex, seasonCountdownTex, moonTex, areaTex, noticeTex};
        for(Tex texture : textures) {
            if(texture != null)
                texture.dispose();
        }
        timeTex = dateTex = seasonTex = seasonCountdownTex = moonTex = areaTex = noticeTex = null;
    }
}
