package haven.fishing;

/** One validated row from the lure-fishing choice window. */
public final class FishingChoice {
    public final String fishName;
    public final Integer gearPercent;
    public final Integer lurePercent;
    public final Integer finalPercent;

    public FishingChoice(String fishName, Integer gearPercent, Integer lurePercent, Integer finalPercent) {
        this.fishName = fishName == null ? "" : fishName.trim();
        this.gearPercent = gearPercent;
        this.lurePercent = lurePercent;
        this.finalPercent = finalPercent;
    }
}
