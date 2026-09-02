package haven;

import java.awt.Color;

import haven.worldactivity.WorldActivityDetector;
import haven.worldactivity.WorldActivityTimingParser;
import haven.worldactivity.WorldActivityType;

/** A session-local countdown derived from the server's Inspect response. */
public final class LocalizedResourceTimerInfo extends GobInfo {
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
        if(gob == null || message == null
                || WorldActivityDetector.classify(gob) != WorldActivityType.LOCALIZED_RESOURCE)
            return(false);
        long duration = WorldActivityTimingParser.parseLocalizedResourceDurationMillis(message);
        if(duration <= 0)
            return(false);
        long now = System.currentTimeMillis();
        gob.setLocalizedResourceTimer(
                new LocalizedResourceTimerInfo(gob, now, safeAdd(now, duration), message));
        return(true);
    }

    public static long parseDurationMillis(String text) {
        return(WorldActivityTimingParser.parseLocalizedResourceDurationMillis(text));
    }

    public static String formatRemaining(long millis) {
        return(WorldActivityTimingParser.formatRemaining(millis));
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

    public long dueAtMillis() {
        return(dueAtMillis);
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
