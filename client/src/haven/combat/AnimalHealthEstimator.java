package haven.combat;

import static haven.combat.AnimalHealthCatalog.EvidenceKind;

/** Pure rules for turning community max-HP evidence and observed damage into UI. */
public final class AnimalHealthEstimator {
    public static final long MAX_FRESH_AGE_MILLIS = 5 * 60 * 1000;

    private AnimalHealthEstimator() {
    }

    public static AnimalHealthEstimate estimate(AnimalHealthCatalog.Entry animal,
                                                 CombatDamageSnapshot damage) {
        long observed = damage.combatSoftHp();
        AnimalHealthCatalog.HpEvidence max = animal.maxHp();
        boolean hasObservation = damage.hasCombatSoftHpObservation();

        if(hasObservation && (!damage.estimateReliable() ||
                damage.lastSoftEventAgeMillis() > MAX_FRESH_AGE_MILLIS)) {
            return(new AnimalHealthEstimate(AnimalHealthEstimate.Status.STALE,
                    "Dmg " + observed + "; estimate stale", null, observed));
        }

        if(max.kind() == EvidenceKind.EXACT) {
            int maximum = max.maximum();
            if(hasObservation && observed > maximum) {
                return(new AnimalHealthEstimate(AnimalHealthEstimate.Status.CONTRADICTED,
                        "Dmg " + observed + "; max data stale", null, observed));
            }
            if(!hasObservation) {
                return(new AnimalHealthEstimate(AnimalHealthEstimate.Status.NO_OBSERVATION,
                        "Max " + maximum + " (est.)", null, 0));
            }
            long remaining = Math.max(0, maximum - observed);
            return(new AnimalHealthEstimate(AnimalHealthEstimate.Status.ESTIMATED,
                    "~" + remaining + " / " + maximum,
                    Math.max(0.0, Math.min(1.0, remaining / (double)maximum)), observed));
        }
        if(max.kind() == EvidenceKind.APPROXIMATE) {
            return(new AnimalHealthEstimate(AnimalHealthEstimate.Status.APPROXIMATE_MAX,
                    hasObservation ? "Dmg " + observed + "; max ~" + max.maximum() :
                            "Max ~" + max.maximum(), null, observed));
        }
        if(max.kind() == EvidenceKind.RANGE) {
            String range = max.minimum() + "-" + max.maximum();
            return(new AnimalHealthEstimate(AnimalHealthEstimate.Status.RANGE_MAX,
                    hasObservation ? "Dmg " + observed + "; max " + range :
                            "Max " + range, null, observed));
        }
        if(max.kind() == EvidenceKind.LOWER_BOUND) {
            return(new AnimalHealthEstimate(AnimalHealthEstimate.Status.LOWER_BOUND_MAX,
                    hasObservation ? "Dmg " + observed + "; max " + max.minimum() + "+" :
                            "Max " + max.minimum() + "+", null, observed));
        }
        return(new AnimalHealthEstimate(AnimalHealthEstimate.Status.UNKNOWN_MAX,
                hasObservation ? "Damage observed: " + observed : "Max HP unknown", null, observed));
    }
}
