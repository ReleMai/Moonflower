package haven;

import java.awt.Color;

/** Deterministic checks for clock calculations that do not require a live session. */
public final class WorldClockChecks {
    private WorldClockChecks() {
    }

    public static void main(String[] args) {
        Astronomy spring = astronomy(5.0 / 24.0, 0.50, false, 0, 0.50);
        WorldClockSnapshot dawn = WorldClockSnapshot.create(spring, (5 * 60 * 60),
                "Swamp", "Westvale", "Moon Realm");
        require("05:00".equals(dawn.timeLabel(false)), "game time formatting");
        require(dawn.seasonDay == 16 && dawn.seasonRemainingSeconds == 15L * 24 * 60 * 60,
                "season day and remaining game time");
        require(dawn.withSeasonRemaining(dawn.seasonRemainingSeconds - 90).seasonLabel().contains("14d 23h 58m"),
                "season countdown advances in game time");
        require("Summer".equals(dawn.nextSeason), "next season rollover");
        require(dawn.notice.startsWith("Dawn window"), "dawn notice outranks full moon");
        require("Swamp - Westvale - Moon Realm".equals(dawn.areaLabel()), "area composition");

        Astronomy night = astronomy(22.0 / 24.0, 0.10, true, 1, 0.25);
        WorldClockSnapshot moonmoth = WorldClockSnapshot.create(night, 22 * 60 * 60,
                "Oakwild", "", "-");
        require(moonmoth.notice.contains("Moonmoths"), "night notice");
        require("Oakwild".equals(moonmoth.areaLabel()), "unknown area values omitted");

        Astronomy fullMoon = astronomy(12.0 / 24.0, 0.50, false, 2, 0.10);
        WorldClockSnapshot fishMoon = WorldClockSnapshot.create(fullMoon, 12 * 60 * 60,
                "Deep Water", "Eastmere", "-");
        require(fishMoon.moonIndex == 4 && fishMoon.notice.contains("Fish Moon"),
                "full-moon fishing notice");

        WorldClockSnapshot unavailable = WorldClockSnapshot.unavailable("", "", "");
        require(!unavailable.available && "Area unavailable".equals(unavailable.areaLabel()),
                "unavailable state remains explicit");

        Color dawnSky = MoonFlowerClockWidget.skyColor(0.25);
        Color noonSky = MoonFlowerClockWidget.skyColor(0.50);
        Color nightSky = MoonFlowerClockWidget.skyColor(0.95);
        require(!dawnSky.equals(noonSky) && !noonSky.equals(nightSky),
                "sunrise, daylight, and night palettes remain distinct");
        System.out.println("World clock checks passed.");
    }

    private static Astronomy astronomy(double day, double moon, boolean night, int season,
                                       double seasonProgress) {
        return new Astronomy(day, moon, 0.25, night, Color.WHITE, season, seasonProgress,
                0.5, 3.0, 2.0, 10.0);
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
