package haven;

/** Immutable, presentation-neutral view of the live Haven clock and location. */
public final class WorldClockSnapshot {
    private static final int DAY_SECONDS = 24 * 60 * 60;
    private static final int DAWN_START = (4 * 60 * 60) + (45 * 60);
    private static final int DAWN_END = (7 * 60 * 60) + (15 * 60);

    public final boolean available;
    public final long gameSeconds;
    public final int gameDay;
    public final int secondOfDay;
    public final int hour;
    public final int minute;
    public final int second;
    public final double dayFraction;
    public final boolean night;
    public final int calendarDay;
    public final int calendarMonth;
    public final int calendarYear;
    public final int moonIndex;
    public final String moonPhase;
    public final int seasonIndex;
    public final String season;
    public final int seasonDay;
    public final int seasonLength;
    public final long seasonRemainingSeconds;
    public final String nextSeason;
    public final String terrain;
    public final String province;
    public final String realm;
    public final String notice;
    public final String noticeProvenance;

    private WorldClockSnapshot(boolean available, long gameSeconds, int gameDay, int secondOfDay,
                               int hour, int minute, int second, double dayFraction, boolean night,
                               int calendarDay, int calendarMonth, int calendarYear,
                               int moonIndex, String moonPhase, int seasonIndex, String season,
                               int seasonDay, int seasonLength, long seasonRemainingSeconds,
                               String nextSeason, String terrain, String province, String realm,
                               String notice, String noticeProvenance) {
        this.available = available;
        this.gameSeconds = gameSeconds;
        this.gameDay = gameDay;
        this.secondOfDay = secondOfDay;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.dayFraction = dayFraction;
        this.night = night;
        this.calendarDay = calendarDay;
        this.calendarMonth = calendarMonth;
        this.calendarYear = calendarYear;
        this.moonIndex = moonIndex;
        this.moonPhase = clean(moonPhase);
        this.seasonIndex = seasonIndex;
        this.season = clean(season);
        this.seasonDay = seasonDay;
        this.seasonLength = seasonLength;
        this.seasonRemainingSeconds = seasonRemainingSeconds;
        this.nextSeason = clean(nextSeason);
        this.terrain = clean(terrain);
        this.province = clean(province);
        this.realm = clean(realm);
        this.notice = clean(notice);
        this.noticeProvenance = clean(noticeProvenance);
    }

    public static WorldClockSnapshot unavailable(String terrain, String province, String realm) {
        return new WorldClockSnapshot(false, 0, 0, 0, 0, 0, 0, 0, false,
                0, 0, 0, 0, "", -1, "", 0, 0, 0, "",
                terrain, province, realm, "Awaiting world astronomy", "LIVE");
    }

    public static WorldClockSnapshot create(Astronomy astronomy, long gameSeconds,
                                            String terrain, String province, String realm) {
        if(astronomy == null)
            return unavailable(terrain, province, realm);

        int secondOfDay = (int)Math.floorMod(gameSeconds, DAY_SECONDS);
        int hour = secondOfDay / (60 * 60);
        int minute = (secondOfDay % (60 * 60)) / 60;
        int second = secondOfDay % 60;
        int moonIndex = Math.floorMod((int)Math.round(astronomy.mp * Astronomy.phase.length),
                Astronomy.phase.length);

        Astronomy.Season[] seasons = Astronomy.Season.values();
        int seasonIndex = Math.floorMod(astronomy.is, seasons.length);
        Astronomy.Season current = seasons[seasonIndex];
        Astronomy.Season next = seasons[(seasonIndex + 1) % seasons.length];
        int seasonDay = Utils.clip((int)Math.floor(current.length * astronomy.sp) + 1, 1, current.length);
        long remaining = Math.max(0L,
                Math.round(current.length * (double)DAY_SECONDS * (1.0 - astronomy.sp)));

        Notice notice = notice(secondOfDay, astronomy.night, moonIndex);
        return new WorldClockSnapshot(true, gameSeconds,
                (int)Math.floorDiv(gameSeconds, DAY_SECONDS), secondOfDay,
                hour, minute, second, secondOfDay / (double)DAY_SECONDS, astronomy.night,
                (int)Math.floor(astronomy.md) + 1,
                (int)Math.floor(astronomy.ym) + 1,
                (int)Math.floor(astronomy.years) + 1,
                moonIndex, Astronomy.phase[moonIndex], seasonIndex, current.toString(),
                seasonDay, current.length, remaining, next.toString(), terrain, province, realm,
                notice.text, notice.provenance);
    }

    public String timeLabel(boolean seconds) {
        return seconds ? String.format("%02d:%02d:%02d", hour, minute, second) :
                String.format("%02d:%02d", hour, minute);
    }

    public String dateLabel() {
        return String.format("Day %d - Month %d - Year %d", calendarDay, calendarMonth, calendarYear);
    }

    public String seasonLabel() {
        long days = seasonRemainingSeconds / DAY_SECONDS;
        long hours = (seasonRemainingSeconds % DAY_SECONDS) / (60 * 60);
        long minutes = (seasonRemainingSeconds % (60 * 60)) / 60;
        return String.format("%s %d/%d - %dd %02dh %02dm to %s",
                season, seasonDay, seasonLength, days, hours, minutes, nextSeason);
    }

    public WorldClockSnapshot withSeasonRemaining(long remainingSeconds) {
        return new WorldClockSnapshot(available, gameSeconds, gameDay, secondOfDay,
                hour, minute, second, dayFraction, night, calendarDay, calendarMonth, calendarYear,
                moonIndex, moonPhase, seasonIndex, season, seasonDay, seasonLength,
                Math.max(0L, remainingSeconds), nextSeason, terrain, province, realm,
                notice, noticeProvenance);
    }

    public String areaLabel() {
        StringBuilder area = new StringBuilder();
        appendArea(area, terrain);
        appendArea(area, province);
        if(!realm.equals("-"))
            appendArea(area, realm);
        return area.length() == 0 ? "Area unavailable" : area.toString();
    }

    private static void appendArea(StringBuilder area, String value) {
        String clean = clean(value);
        if(clean.isEmpty())
            return;
        if(area.length() > 0)
            area.append(" - ");
        area.append(clean);
    }

    private static Notice notice(int secondOfDay, boolean night, int moonIndex) {
        if(secondOfDay >= DAWN_START && secondOfDay <= DAWN_END) {
            int remainingMinutes = Math.max(0, (DAWN_END - secondOfDay) / 60);
            return new Notice(String.format("Dawn window - %dm left - Dewy Mantle & experiences",
                    remainingMinutes), "GUIDE");
        }
        if(moonIndex == 4)
            return new Notice("Full Moon - Fish Moon opportunity", "GUIDE");
        if(night)
            return new Notice("Night - Moonmoths may be visible", "GUIDE");
        return new Notice("Live Haven time and area", "LIVE");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Notice {
        final String text;
        final String provenance;

        Notice(String text, String provenance) {
            this.text = text;
            this.provenance = provenance;
        }
    }
}
