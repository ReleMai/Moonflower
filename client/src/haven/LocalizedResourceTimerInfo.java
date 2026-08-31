package haven;

import java.awt.Color;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A session-local countdown derived from the server's Inspect response. */
public final class LocalizedResourceTimerInfo extends GobInfo {
    private static final Pattern DURATION = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(?:real\\s+)?(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?)");
    private static final Pattern REFILL = Pattern.compile(
            "(?i)\\b(refill|replenish|regenerate|respawn|available|collect(?:ion)?)\\w*\\b");
    private static final Text.Foundry FONT = new Text.Foundry(Text.sans, 12);

    private final long observedAtMillis;
    private final long dueAtMillis;
    private final String serverMessage;
    private long renderedSecond = Long.MIN_VALUE;

    private LocalizedResourceTimerInfo(Gob owner, long observedAtMillis,
                                       long dueAtMillis, String serverMessage) {
        super(owner);
        this.observedAtMillis = observedAtMillis;
        this.dueAtMillis = dueAtMillis;
        this.serverMessage = serverMessage;
    }

    /** Installs a timer only when the message contains both refill semantics and a duration. */
    public static boolean noteInspection(Gob gob, String message) {
        if(gob == null || message == null || !REFILL.matcher(message).find())
            return(false);
        long duration = parseDurationMillis(message);
        if(duration <= 0)
            return(false);
        long now = System.currentTimeMillis();
        gob.setLocalizedResourceTimer(
                new LocalizedResourceTimerInfo(gob, now, safeAdd(now, duration), message));
        return(true);
    }

    public static long parseDurationMillis(String text) {
        if(text == null || !REFILL.matcher(text).find())
            return(-1);
        Matcher matcher = DURATION.matcher(text);
        double totalSeconds = 0;
        while(matcher.find()) {
            double amount;
            try {
                amount = Double.parseDouble(matcher.group(1));
            } catch(NumberFormatException ignored) {
                return(-1);
            }
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            if(unit.startsWith("day"))
                totalSeconds += amount * 86_400d;
            else if(unit.startsWith("hour") || unit.startsWith("hr"))
                totalSeconds += amount * 3_600d;
            else if(unit.startsWith("minute") || unit.startsWith("min"))
                totalSeconds += amount * 60d;
            else
                totalSeconds += amount;
        }
        if(totalSeconds <= 0 || totalSeconds > (3650d * 86_400d))
            return(-1);
        return(Math.max(1L, Math.round(totalSeconds * 1000d)));
    }

    public static String formatRemaining(long millis) {
        if(millis <= 0)
            return("due now");
        long seconds = (millis + 999L) / 1000L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        if(days > 0)
            return(String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds));
        return(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    public long remainingMillis() {
        return(dueAtMillis - System.currentTimeMillis());
    }

    public String remainingText() {
        return(formatRemaining(remainingMillis()));
    }

    public String richTooltip() {
        return("$col[73,174,178]{CALC refill: }" + remainingText() +
                "\n$col[239,225,185]{Based on this session's server Inspect response.}");
    }

    public String serverMessage() {
        return(serverMessage);
    }

    public long observedAtMillis() {
        return(observedAtMillis);
    }

    @Override
    protected boolean enabled() {
        return(!gob.isHidden);
    }

    @Override
    public void ctick(double dt) {
        long second = Math.max(0L, remainingMillis() / 1000L);
        if(second != renderedSecond) {
            renderedSecond = second;
            clear();
        }
        super.ctick(dt);
    }

    @Override
    protected Tex render() {
        up(5.2f);
        String label = "CALC " + remainingText();
        return(new TexI(FONT.renderstroked(label, MoonFlowerHudTheme.IVORY,
                new Color(1, 7, 11, 235)).img));
    }

    private static long safeAdd(long left, long right) {
        if(Long.MAX_VALUE - left < right)
            return(Long.MAX_VALUE);
        return(left + right);
    }
}
