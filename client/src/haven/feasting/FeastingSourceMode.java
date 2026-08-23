package haven.feasting;

/** Controls which reachable food sources may be recommended and auto-eaten. */
public enum FeastingSourceMode {
    TABLE_AND_INVENTORY,
    INVENTORY_ONLY;

    public boolean allows(FeastingCandidate candidate) {
        return(this == TABLE_AND_INVENTORY ||
                candidate.source == FeastingCandidate.Source.INVENTORY);
    }
}
