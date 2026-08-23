package haven.feasting;

import haven.cookbook.CookbookAttribute;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Immutable projection of one currently reachable food widget. */
public final class FeastingCandidate {
    public enum Source {
        TABLE("Table"), INVENTORY("Inventory");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    public final int widgetId;
    public final int quantity;
    public final int infoSequence;
    public final Source source;
    public final String name;
    public final String resourceName;
    public final double quality;
    public final double hungerPermille;
    public final double energyPercent;
    public final double totalFep;
    private final Map<CookbookAttribute, Double> feps;

    public FeastingCandidate(int widgetId, int quantity, int infoSequence, Source source,
                             String name, String resourceName, double quality,
                             double hungerPermille, double energyPercent, double totalFep,
                             Map<CookbookAttribute, Double> feps) {
        this.widgetId = widgetId;
        this.quantity = quantity;
        this.infoSequence = infoSequence;
        this.source = source;
        this.name = name == null ? "Unknown food" : name;
        this.resourceName = resourceName == null ? "" : resourceName;
        this.quality = quality;
        this.hungerPermille = hungerPermille;
        this.energyPercent = energyPercent;
        this.totalFep = Math.max(0, totalFep);
        EnumMap<CookbookAttribute, Double> copy = new EnumMap<>(CookbookAttribute.class);
        if(feps != null)
            copy.putAll(feps);
        this.feps = Collections.unmodifiableMap(copy);
    }

    public double fep(CookbookAttribute attribute) {
        return(feps.getOrDefault(attribute, 0d));
    }

    public Map<CookbookAttribute, Double> feps() {
        return(feps);
    }
}
