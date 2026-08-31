package haven.multisession;

/** Independent lifecycle state for a session shown by the embedded host. */
public enum SessionSurfaceState {
    CURRENT("LIVE", "Current game surface"),
    STARTING("CALC", "Starting in background"),
    READY("LIVE", "Ready to switch"),
    STALE("CALC", "Preview is stale"),
    DISCONNECTED("LIVE", "Disconnected"),
    LOCKED("CALC", "Secure worker host not enabled");

    public final String provenance;
    public final String label;

    SessionSurfaceState(String provenance, String label) {
        this.provenance = provenance;
        this.label = label;
    }
}
