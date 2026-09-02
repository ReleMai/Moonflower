package haven.worldactivity;

/** Immutable board row; timing is recalculated from the observed due time. */
public final class WorldActivityEntry {
    private final long gobId;
    private final WorldActivityType type;
    private final String resourceName;
    private final String label;
    private final Double quality;
    private final long observedAtMillis;
    private final long dueAtMillis;
    private final WorldActivityFuelState fuelState;
    private final boolean visible;

    public WorldActivityEntry(long gobId, WorldActivityType type, String resourceName,
                              String label, Double quality, long observedAtMillis,
                              long dueAtMillis, WorldActivityFuelState fuelState,
                              boolean visible) {
        this.gobId = gobId;
        this.type = type;
        this.resourceName = resourceName;
        this.label = label;
        this.quality = quality;
        this.observedAtMillis = observedAtMillis;
        this.dueAtMillis = dueAtMillis;
        this.fuelState = fuelState == null ? WorldActivityFuelState.UNKNOWN : fuelState;
        this.visible = visible;
    }

    public long gobId() {
        return(gobId);
    }

    public WorldActivityType type() {
        return(type);
    }

    public String resourceName() {
        return(resourceName);
    }

    public String label() {
        return(label == null || label.isEmpty() ? type.label() : label);
    }

    public Double quality() {
        return(quality);
    }

    public long observedAtMillis() {
        return(observedAtMillis);
    }

    public long dueAtMillis() {
        return(dueAtMillis);
    }

    public WorldActivityFuelState fuelState() {
        return(fuelState);
    }

    public boolean visible() {
        return(visible);
    }

    public boolean hasTimer() {
        return(dueAtMillis >= 0L);
    }

    public long remainingMillis(long nowMillis) {
        return(hasTimer() ? dueAtMillis - nowMillis : -1L);
    }

    public String remainingText(long nowMillis) {
        return(hasTimer() ? WorldActivityTimingParser.formatRemaining(remainingMillis(nowMillis)) : "unavailable");
    }

    public WorldActivityState state(long nowMillis) {
        if(!hasTimer())
            return(WorldActivityState.AWAITING_INSPECTION);
        return(dueAtMillis <= nowMillis ? WorldActivityState.DUE : WorldActivityState.RUNNING);
    }
}
