package haven.cookbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read model used by the in-game cookbook list and details panel. */
public final class CookbookEntry {
    public final long recipeId;
    public final String itemName;
    public final String resourceName;
    public final String ingredients;
    public final String modifiers;
    public final double quality;
    public final double energyPercent;
    public final double hungerPermille;
    public final double targetFep;
    public final double totalFep;
    public final double targetPerHunger;
    public final List<FepValue> feps;
    public final List<Observation> observations;
    public final int seenCount;

    CookbookEntry(long recipeId, String itemName, String resourceName, String ingredients,
                  String modifiers, double quality, double energyPercent, double hungerPermille,
                  double targetFep, double totalFep, long latestObservationId,
                  List<Observation> observations) {
        this.recipeId = recipeId;
        this.itemName = itemName;
        this.resourceName = resourceName;
        this.ingredients = ingredients;
        this.modifiers = modifiers;
        this.quality = quality;
        this.energyPercent = energyPercent;
        this.hungerPermille = hungerPermille;
        this.targetFep = targetFep;
        this.totalFep = totalFep;
        this.targetPerHunger = (hungerPermille > 0) ? targetFep / hungerPermille : targetFep;
        this.observations = immutableCopy(observations);

        List<FepValue> latestFeps = Collections.emptyList();
        int totalSeen = 0;
        for(Observation observation : this.observations) {
            totalSeen += observation.seenCount;
            if(observation.id == latestObservationId)
                latestFeps = observation.feps;
        }
        this.feps = latestFeps;
        this.seenCount = totalSeen;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return(Collections.unmodifiableList(new ArrayList<>(values)));
    }

    public static final class FepValue {
        public final String attribute;
        public final double amount;
        public final double normalizedAmount;

        FepValue(String attribute, double amount, double normalizedAmount) {
            this.attribute = attribute;
            this.amount = amount;
            this.normalizedAmount = normalizedAmount;
        }
    }

    /** One distinct quality/outcome observed for this ingredient recipe. */
    public static final class Observation {
        final long id;
        final long lastSeen;
        public final double quality;
        public final double energyPercent;
        public final double hungerPermille;
        public final double normalizedHungerPermille;
        public final double capturedFepEfficiencyPercent;
        public final double capturedHungerEfficiencyPercent;
        public final int seenCount;
        public final List<FepValue> feps;

        Observation(long id, long lastSeen, double quality, double energyPercent,
                    double hungerPermille, double normalizedHungerPermille,
                    double capturedFepEfficiencyPercent,
                    double capturedHungerEfficiencyPercent, int seenCount, List<FepValue> feps) {
            this.id = id;
            this.lastSeen = lastSeen;
            this.quality = quality;
            this.energyPercent = energyPercent;
            this.hungerPermille = hungerPermille;
            this.normalizedHungerPermille = normalizedHungerPermille;
            this.capturedFepEfficiencyPercent = capturedFepEfficiencyPercent;
            this.capturedHungerEfficiencyPercent = capturedHungerEfficiencyPercent;
            this.seenCount = seenCount;
            this.feps = immutableCopy(feps);
        }
    }
}
