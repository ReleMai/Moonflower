package haven.multisession;

import java.time.Instant;

/** Presentation-safe session identity. It deliberately contains no credentials. */
public final class SessionCardSnapshot {
    private final String sessionId;
    private final String accountLabel;
    private final String characterName;
    private final String worldName;
    private final SessionSurfaceState state;
    private final boolean selected;
    private final boolean interactive;
    private final Instant observedAt;
    private final String detail;

    public SessionCardSnapshot(String sessionId, String accountLabel, String characterName, String worldName,
                               SessionSurfaceState state, boolean selected, boolean interactive,
                               Instant observedAt, String detail) {
        this.sessionId = safe(sessionId, "current");
        this.accountLabel = safe(accountLabel, "Current account");
        this.characterName = safe(characterName, "Character unavailable");
        this.worldName = safe(worldName, "World unavailable");
        this.state = (state == null) ? SessionSurfaceState.DISCONNECTED : state;
        this.selected = selected;
        this.interactive = interactive;
        this.observedAt = (observedAt == null) ? Instant.EPOCH : observedAt;
        this.detail = safe(detail, this.state.label);
    }

    public String sessionId() {return(sessionId);}
    public String accountLabel() {return(accountLabel);}
    public String characterName() {return(characterName);}
    public String worldName() {return(worldName);}
    public SessionSurfaceState state() {return(state);}
    public boolean selected() {return(selected);}
    public boolean interactive() {return(interactive);}
    public Instant observedAt() {return(observedAt);}
    public String detail() {return(detail);}

    private static String safe(String value, String fallback) {
        if(value == null || value.isBlank())
            return(fallback);
        return(value.trim());
    }
}
