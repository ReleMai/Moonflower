package haven;

import java.awt.Color;
import java.util.Collection;

import static java.lang.Math.PI;

/** Classic-compatible world clock presented inside the inverted seasonal ornament. */
public final class MoonFlowerClockWidget extends Widget {
    private static final Coord FACE_SIZE = UI.scale(Coord.of(520, 182));
    private static final Coord CLOCK_CENTER = UI.scale(Coord.of(260, 84));
    private static final int CLOCK_RADIUS = UI.scale(31);
    private static final Color MIDNIGHT = new Color(4, 12, 31, 255);
    private static final Color DAWN = new Color(159, 76, 76, 255);
    private static final Color DAY = new Color(53, 135, 150, 255);
    private static final Color SUNSET = new Color(190, 91, 54, 255);

    private final Cal classic;
    private final WorldClockService service;
    private final Tex frameTex;
    private boolean moonFlower;
    private long nextRefresh;
    private WorldClockSnapshot snapshot = WorldClockSnapshot.unavailable("", "", "");
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
        frameTex = new TexI(PUtils.convolvedown(MoonFlowerHudAssets.clockOrnament,
                FACE_SIZE, new PUtils.Lanczos(3)));
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
        frameTex.dispose();
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
            dateTex = render("Awaiting astronomy", MoonFlowerHudTheme.IVORY, Text.std);
            seasonTex = seasonCountdownTex = moonTex = areaTex = null;
            noticeTex = render("Waiting for sky data", MoonFlowerHudTheme.TEAL_BRIGHT, Text.std);
        } else {
            dateTex = render(String.format("Day %d  ·  M%d  ·  Y%d",
                    snapshot.calendarDay, snapshot.calendarMonth, snapshot.calendarYear),
                    MoonFlowerHudTheme.IVORY, Text.std);
            seasonTex = render(snapshot.season + "  " + snapshot.seasonDay + "/" + snapshot.seasonLength,
                    MoonFlowerHudTheme.GOLD, Text.num12boldFnd);
            seasonCountdownTex = render(compactSeasonCountdown(snapshot), MoonFlowerHudTheme.IVORY, Text.std);
            moonTex = render(snapshot.moonPhase, MoonFlowerHudTheme.TEAL_BRIGHT, Text.num12boldFnd);
            areaTex = render(compactArea(snapshot), MoonFlowerHudTheme.IVORY, Text.std);
            noticeTex = render(compactNotice(snapshot, weatherLabel()),
                    "LIVE".equals(snapshot.noticeProvenance) ? MoonFlowerHudTheme.TEAL_BRIGHT :
                            MoonFlowerHudTheme.GOLD, Text.std);
        }
        String detailText = detailText();
        if(!detailText.equals(detailsKey)) {
            if(details != null)
                details.dispose();
            details = RichText.render(detailText, UI.scale(380));
            detailsKey = detailText;
        }
    }

    private void drawMoonFlower(GOut g) {
        g.image(frameTex, Coord.z);
        drawClockFace(g);

        int plaqueWidth = UI.scale(132);
        drawCenteredClipped(g, seasonTex, UI.scale(Coord.of(92, 22)), plaqueWidth, UI.scale(13));
        drawCenteredClipped(g, seasonCountdownTex, UI.scale(Coord.of(92, 35)), plaqueWidth, UI.scale(11));
        drawCenteredClipped(g, moonTex, UI.scale(Coord.of(428, 22)), plaqueWidth, UI.scale(13));
        drawCenteredClipped(g, areaTex, UI.scale(Coord.of(428, 35)), plaqueWidth, UI.scale(11));
        drawCenteredClipped(g, dateTex, UI.scale(Coord.of(92, 70)), plaqueWidth, UI.scale(11));
        drawCenteredClipped(g, noticeTex, UI.scale(Coord.of(428, 70)), plaqueWidth, UI.scale(11));
    }

    private void drawClockFace(GOut g) {
        drawSkyDisc(g, CLOCK_CENTER, CLOCK_RADIUS);
        if(snapshot.available)
            drawClockHands(g);
    }

    private void drawClockTicks(GOut g) {
        g.chcolor(MoonFlowerHudTheme.GOLD_SOFT);
        int inner = CLOCK_RADIUS - UI.scale(5);
        int outer = CLOCK_RADIUS - UI.scale(1);
        for(int hour = 0; hour < 12; hour++) {
            double angle = (-PI / 2) + ((PI * 2 * hour) / 12.0);
            Coord from = CLOCK_CENTER.add((int)Math.round(Math.cos(angle) * inner),
                    (int)Math.round(Math.sin(angle) * inner));
            Coord to = CLOCK_CENTER.add((int)Math.round(Math.cos(angle) * outer),
                    (int)Math.round(Math.sin(angle) * outer));
            g.line(from, to, Math.max(1, UI.scale(hour % 3 == 0 ? 2 : 1)));
        }
        g.chcolor();
    }

    private void drawClockHands(GOut g) {
        double motion = MoonFlowerHudSettings.clockReducedMotion() ? 0.0 :
                ((System.currentTimeMillis() % 1000L) / 1000.0);
        double hourAngle = hourHandAngle(snapshot.hour, snapshot.minute) +
                ((motion / 60.0) * (PI * 2 / 12.0));
        double minuteAngle = minuteHandAngle(snapshot.minute, snapshot.second) +
                (motion * (PI * 2 / 60.0));
        Coord hourEnd = handEnd(CLOCK_CENTER, CLOCK_RADIUS - UI.scale(14), hourAngle);
        Coord minuteEnd = handEnd(CLOCK_CENTER, CLOCK_RADIUS - UI.scale(8), minuteAngle);
        Coord hourTail = handEnd(CLOCK_CENTER, UI.scale(7), hourAngle + PI);
        Coord minuteTail = handEnd(CLOCK_CENTER, UI.scale(5), minuteAngle + PI);

        g.chcolor(new Color(2, 7, 10, 220));
        g.line(CLOCK_CENTER, hourEnd, Math.max(2, UI.scale(5)));
        g.line(CLOCK_CENTER, minuteEnd, Math.max(2, UI.scale(4)));
        g.line(CLOCK_CENTER, hourTail, Math.max(1, UI.scale(3)));
        g.line(CLOCK_CENTER, minuteTail, Math.max(1, UI.scale(2)));
        g.chcolor(MoonFlowerHudTheme.GOLD);
        g.line(CLOCK_CENTER, hourEnd, Math.max(1, UI.scale(3)));
        g.line(CLOCK_CENTER, hourTail, Math.max(1, UI.scale(1)));
        g.chcolor(MoonFlowerHudTheme.IVORY);
        g.line(CLOCK_CENTER, minuteEnd, Math.max(1, UI.scale(2)));
        g.line(CLOCK_CENTER, minuteTail, Math.max(1, UI.scale(1)));
        fillEllipse(g, CLOCK_CENTER, UI.scale(Coord.of(3, 3)), MoonFlowerHudTheme.GOLD);
        fillEllipse(g, CLOCK_CENTER, UI.scale(Coord.of(1, 1)), MoonFlowerHudTheme.IVORY);
        g.chcolor();
    }

    private void drawSkyDisc(GOut g, Coord center, int radius) {
        double dayFraction = snapshot.available ? snapshot.dayFraction : 0.0;
        Color sky = skyColor(dayFraction);
        // Tint only the inner face; the generated seasonal artwork remains visible.
        fillEllipse(g, center, UI.scale(Coord.of(radius, radius)), new Color(2, 8, 15, 150));
        for(int y = -radius; y <= radius; y++) {
            int halfWidth = (int)Math.floor(Math.sqrt(Math.max(0, (radius * radius) - (y * y))));
            double position = (y + radius) / (double)Math.max(1, radius * 2);
            Color tinted = shade(sky, 1.03 - (position * 0.28));
            g.chcolor(new Color(tinted.getRed(), tinted.getGreen(), tinted.getBlue(), 62));
            g.frect(center.add(-halfWidth, y), Coord.of((halfWidth * 2) + 1, 1));
        }

        if(snapshot.available) {
            int orbit = UI.scale(22);
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

        int horizonY = center.y + UI.scale(9);
        int dy = horizonY - center.y;
        g.chcolor(new Color(2, 22, 25, 72));
        for(int y = horizonY; y <= center.y + radius; y++) {
            int offset = y - center.y;
            int halfWidth = (int)Math.floor(Math.sqrt(Math.max(0, (radius * radius) - (offset * offset))));
            g.frect(Coord.of(center.x - halfWidth, y), Coord.of((halfWidth * 2) + 1, 1));
        }
        g.chcolor();
    }

    static double hourHandAngle(int hour, int minute) {
        return (-PI / 2) + (PI * 2 * ((Math.floorMod(hour, 12) + (minute / 60.0)) / 12.0));
    }

    static double minuteHandAngle(int minute, int second) {
        return (-PI / 2) + (PI * 2 * ((Math.floorMod(minute, 60) + (second / 60.0)) / 60.0));
    }

    static double seasonStartAngle(int seasonIndex) {
        return PI + (Math.floorMod(seasonIndex, 4) * (PI / 2));
    }

    private static Coord handEnd(Coord center, int length, double angle) {
        return center.add((int)Math.round(Math.cos(angle) * length),
                (int)Math.round(Math.sin(angle) * length));
    }

    private static String compactSeasonCountdown(WorldClockSnapshot snapshot) {
        long days = snapshot.seasonRemainingSeconds / (24 * 60 * 60);
        long hours = (snapshot.seasonRemainingSeconds % (24 * 60 * 60)) / (60 * 60);
        return String.format("%dd %02dh to %s", days, hours, snapshot.nextSeason);
    }

    private static String compactArea(WorldClockSnapshot snapshot) {
        String primary = !snapshot.province.isEmpty() ? snapshot.province : snapshot.terrain;
        String secondary = (!snapshot.realm.isEmpty() && !"-".equals(snapshot.realm)) ? snapshot.realm : "";
        if(primary.isEmpty())
            return secondary.isEmpty() ? "Area unavailable" : secondary;
        return secondary.isEmpty() ? primary : primary + "  ·  " + secondary;
    }

    private static String compactNotice(WorldClockSnapshot snapshot, String weather) {
        String prefix = (snapshot.night ? "Night" : "Daylight") + "  ·  ";
        if(snapshot.notice.contains("Dawn window")) {
            String summary = snapshot.notice.replace("Dawn window - ", "Dawn · ");
            int detail = summary.indexOf(" - ");
            return (detail < 0 ? summary : summary.substring(0, detail)) + "  ·  " + weather;
        }
        if(snapshot.notice.contains("Fish Moon"))
            return prefix + "Fish Moon  ·  " + weather;
        if(snapshot.notice.contains("Moonmoths"))
            return prefix + "Moonmoths  ·  " + weather;
        return prefix + weather;
    }

    /** Weather effects are owned by the live session; keep the plaque honest and compact. */
    private String weatherLabel() {
        Glob glob = ui == null || ui.sess == null ? null : ui.sess.glob;
        if(glob == null)
            return "Weather unknown";
        try {
            Collection<Glob.Weather> effects = glob.weather();
            if(effects.isEmpty())
                return "Clear skies";
            StringBuilder label = new StringBuilder();
            for(Glob.Weather effect : effects) {
                String name = effect.getClass().getSimpleName().toLowerCase();
                String pretty = name.contains("rain") ? "Rain" :
                        name.contains("snow") ? "Snow" :
                        name.contains("cloud") ? "Clouds" :
                        name.contains("wet") ? "Wet ground" : "Atmosphere";
                if(label.indexOf(pretty) < 0) {
                    if(label.length() > 0)
                        label.append(" · ");
                    label.append(pretty);
                }
            }
            return label.toString();
        } catch(RuntimeException ignored) {
            return "Weather loading";
        }
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

    private static void drawCenteredClipped(GOut g, Tex texture, Coord center, int width, int height) {
        if(texture == null || width <= 0 || height <= 0)
            return;
        Coord origin = Coord.of(center.x - (width / 2), center.y - (height / 2));
        Coord textureOrigin = Coord.of((width - texture.sz().x) / 2, (height - texture.sz().y) / 2);
        g.reclip(origin, Coord.of(width, height)).image(texture, textureOrigin);
    }

    private static void fillEllipse(GOut g, Coord center, Coord radius, Color color) {
        g.chcolor(color);
        g.fellipse(center, radius);
        g.chcolor();
    }

    private String detailText() {
        if(!snapshot.available)
            return "$col[239,225,185]{MoonFlower World Clock}\nAwaiting live server astronomy.";
        return String.format("$col[239,225,185]{MoonFlower World Clock}\n" +
                        "%s game time · %s\n%s\n%s (game-time countdown)\n" +
                        "Moon: %s\nArea: %s\nWeather: %s\n\n" +
                        "$col[207,164,72]{%s}: %s\n" +
                        "$col[150,180,180]{LIVE = session data; GUIDE = sourced community guidance.}",
                snapshot.timeLabel(false), snapshot.night ? "Night" : "Daylight",
                snapshot.dateLabel(), snapshot.seasonLabel(), snapshot.moonPhase,
                snapshot.areaLabel(), weatherLabel(), snapshot.noticeProvenance, snapshot.notice);
    }

    private void disposeText() {
        Tex[] textures = {dateTex, seasonTex, seasonCountdownTex, moonTex, areaTex, noticeTex};
        for(Tex texture : textures) {
            if(texture != null)
                texture.dispose();
        }
        dateTex = seasonTex = seasonCountdownTex = moonTex = areaTex = noticeTex = null;
    }
}
