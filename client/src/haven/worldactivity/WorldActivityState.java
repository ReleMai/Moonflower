package haven.worldactivity;

public enum WorldActivityState {
    AWAITING_INSPECTION("Awaiting Inspect"),
    RUNNING("Running"),
    DUE("Due now");

    private final String label;

    WorldActivityState(String label) {
        this.label = label;
    }

    public String label() {
        return(label);
    }
}
