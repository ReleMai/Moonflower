package haven.worldactivity;

/**
 * Fuel state is deliberately explicit. UNKNOWN is different from UNLIT and
 * NO_FUEL so a future workstation adapter cannot silently claim a safe timer.
 */
public enum WorldActivityFuelState {
    NOT_REQUIRED("No fuel required"),
    UNKNOWN("Fuel unknown"),
    LIT("Lit"),
    UNLIT("Unlit"),
    NO_FUEL("No fuel");

    private final String label;

    WorldActivityFuelState(String label) {
        this.label = label;
    }

    public String label() {
        return(label);
    }
}
