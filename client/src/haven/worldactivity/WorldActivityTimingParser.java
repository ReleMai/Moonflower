package haven.worldactivity;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses only the small set of timing and quality phrases that are tied to a
 * recognized activity type. This keeps arbitrary system messages from
 * becoming timers merely because they contain a number.
 */
public final class WorldActivityTimingParser {
    private static final long SECOND_MILLIS = 1_000L;
    private static final long MAX_DURATION_MILLIS = 3650L * 86_400_000L;
    private static final Pattern DURATION = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(?:real\\s+|rl\\s+)?"
                    + "(weeks?|w|days?|d|hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b");
    private static final Pattern QUALITY = Pattern.compile(
            "(?i)\\b(?:quality|ql)\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)\\b");
    private static final Pattern LOCALIZED_CONTEXT = Pattern.compile(
            "(?i)\\b(refill(?:ed|s|ing)?|replenish(?:ed|es|ing)?|"
                    + "regenerat(?:e|ed|es|ing)|respawn(?:ed|s|ing)?|"
                    + "availab(?:le|ility)|collect(?:ed|ion|ing)?|"
                    + "gather(?:ed|ing)?|harvest(?:ed|s|ing)?|"
                    + "deplet(?:ed|ion|es|ing)?|resource|node)\\b");
    private static final Pattern PYRE_CONTEXT = Pattern.compile(
            "(?i)\\b(pyre|burn(?:ed|ing|s)?|smolder(?:ed|ing|s)?|"
                    + "smoulder(?:ed|ing|s)?|ash(?:es)?|fire|flame(?:s|d|ing)?)\\b");

    private WorldActivityTimingParser() {
    }

    public static ParsedTiming parse(String text, WorldActivityType type) {
        return(new ParsedTiming(parseDurationMillis(text, type), parseFuelState(text)));
    }

    public static long parseDurationMillis(String text, WorldActivityType type) {
        if(text == null || type == null || !hasContext(text, type))
            return(-1L);
        Matcher matcher = DURATION.matcher(text);
        double totalSeconds = 0.0d;
        boolean found = false;
        while(matcher.find()) {
            found = true;
            double amount;
            try {
                amount = Double.parseDouble(matcher.group(1));
            } catch(NumberFormatException ignored) {
                return(-1L);
            }
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            double multiplier;
            if(unit.startsWith("week") || unit.equals("w"))
                multiplier = 604_800d;
            else if(unit.startsWith("day") || unit.equals("d"))
                multiplier = 86_400d;
            else if(unit.startsWith("hour") || unit.startsWith("hr") || unit.equals("h"))
                multiplier = 3_600d;
            else if(unit.startsWith("minute") || unit.startsWith("min") || unit.equals("m"))
                multiplier = 60d;
            else
                multiplier = 1d;
            totalSeconds += amount * multiplier;
            if(!Double.isFinite(totalSeconds) || totalSeconds > (MAX_DURATION_MILLIS / 1000d))
                return(-1L);
        }
        if(!found || totalSeconds <= 0.0d)
            return(-1L);
        return(Math.max(1L, Math.round(totalSeconds * SECOND_MILLIS)));
    }

    /** The existing localized-resource adapter uses this legacy-shaped entry point. */
    public static long parseLocalizedResourceDurationMillis(String text) {
        return(parseDurationMillis(text, WorldActivityType.LOCALIZED_RESOURCE));
    }

    public static Double parseQuality(String text) {
        if(text == null)
            return(null);
        Matcher matcher = QUALITY.matcher(text);
        if(!matcher.find())
            return(null);
        try {
            double quality = Double.parseDouble(matcher.group(1));
            if(!Double.isFinite(quality) || quality < 0.0d || quality > 1_000.0d)
                return(null);
            return(quality);
        } catch(NumberFormatException ignored) {
            return(null);
        }
    }

    public static WorldActivityFuelState parseFuelState(String text) {
        if(text == null)
            return(WorldActivityFuelState.UNKNOWN);
        String lower = text.toLowerCase(Locale.ROOT);
        if(lower.matches(".*\\b(no|without|out of)\\s+(any\\s+)?fuel\\b.*")
                || lower.matches(".*\\bfuel\\s+(is\\s+)?(empty|depleted)\\b.*"))
            return(WorldActivityFuelState.NO_FUEL);
        if(lower.matches(".*\\b(unlit|not\\s+lit|extinguish(?:ed|ing)?|fire\\s+is\\s+out|no\\s+fire)\\b.*"))
            return(WorldActivityFuelState.UNLIT);
        if(lower.matches(".*\\b(lit|burning|on\\s+fire|flame(?:s|d|ing)?)\\b.*"))
            return(WorldActivityFuelState.LIT);
        return(WorldActivityFuelState.UNKNOWN);
    }

    public static String formatRemaining(long millis) {
        if(millis <= 0L)
            return("due now");
        long seconds = (millis + 999L) / SECOND_MILLIS;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        if(days > 0L)
            return(String.format(Locale.ROOT, "%dd %02d:%02d:%02d", days, hours, minutes, seconds));
        return(String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds));
    }

    private static boolean hasContext(String text, WorldActivityType type) {
        if(type == WorldActivityType.PYRE)
            return(PYRE_CONTEXT.matcher(text).find());
        if(type == WorldActivityType.LOCALIZED_RESOURCE)
            return(LOCALIZED_CONTEXT.matcher(text).find());
        return(false);
    }

    public static final class ParsedTiming {
        private final long durationMillis;
        private final WorldActivityFuelState fuelState;

        private ParsedTiming(long durationMillis, WorldActivityFuelState fuelState) {
            this.durationMillis = durationMillis;
            this.fuelState = fuelState;
        }

        public long durationMillis() {
            return(durationMillis);
        }

        public boolean hasDuration() {
            return(durationMillis > 0L);
        }

        public WorldActivityFuelState fuelState() {
            return(fuelState);
        }
    }
}
