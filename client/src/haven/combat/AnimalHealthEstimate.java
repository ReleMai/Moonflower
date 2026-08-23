package haven.combat;

/** Honest presentation result; a null fraction means no precise fill is justified. */
public final class AnimalHealthEstimate {
    public enum Status {
        NO_OBSERVATION,
        ESTIMATED,
        APPROXIMATE_MAX,
        RANGE_MAX,
        LOWER_BOUND_MAX,
        UNKNOWN_MAX,
        STALE,
        CONTRADICTED
    }

    private final Status status;
    private final String label;
    private final Double fraction;
    private final long observedDamage;

    public AnimalHealthEstimate(Status status, String label, Double fraction,
                                long observedDamage) {
        this.status = status;
        this.label = label;
        this.fraction = fraction;
        this.observedDamage = observedDamage;
    }

    public Status status() { return(status); }
    public String label() { return(label); }
    public Double fraction() { return(fraction); }
    public long observedDamage() { return(observedDamage); }
}
