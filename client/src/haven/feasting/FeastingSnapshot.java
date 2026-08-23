package haven.feasting;

import haven.cookbook.CookbookAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable live input consumed by the pure feasting planner. */
public final class FeastingSnapshot {
    public enum TablewareState {
        SAFE, AT_RISK, UNKNOWN
    }

    public final double fepCap;
    public final double currentTotalFep;
    public final double hungerLevel;
    public final int pendingFoods;
    public final TablewareState tablewareState;
    public final List<String> atRiskTableware;
    public final List<FeastingCandidate> candidates;
    private final Map<CookbookAttribute, Double> currentFeps;
    private final Map<CookbookAttribute, Integer> baseAttributes;
    private final String fingerprint;

    public FeastingSnapshot(double fepCap, double currentTotalFep, double hungerLevel,
                            int pendingFoods, TablewareState tablewareState,
                            List<String> atRiskTableware,
                            Map<CookbookAttribute, Double> currentFeps,
                            Map<CookbookAttribute, Integer> baseAttributes,
                            List<FeastingCandidate> candidates) {
        this.fepCap = Math.max(0, fepCap);
        this.currentTotalFep = Math.max(0, currentTotalFep);
        this.hungerLevel = hungerLevel;
        this.pendingFoods = Math.max(0, pendingFoods);
        this.tablewareState = tablewareState == null ? TablewareState.UNKNOWN : tablewareState;
        this.atRiskTableware = Collections.unmodifiableList(new ArrayList<>(
                atRiskTableware == null ? Collections.emptyList() : atRiskTableware));
        this.currentFeps = immutableEnumMap(currentFeps);
        this.baseAttributes = immutableEnumMap(baseAttributes);
        this.candidates = Collections.unmodifiableList(new ArrayList<>(
                candidates == null ? Collections.emptyList() : candidates));
        this.fingerprint = buildFingerprint();
    }

    private static <T> Map<CookbookAttribute, T> immutableEnumMap(
            Map<CookbookAttribute, T> source) {
        EnumMap<CookbookAttribute, T> copy = new EnumMap<>(CookbookAttribute.class);
        if(source != null)
            copy.putAll(source);
        return(Collections.unmodifiableMap(copy));
    }

    public double currentFep(CookbookAttribute attribute) {
        return(currentFeps.getOrDefault(attribute, 0d));
    }

    public Map<CookbookAttribute, Double> currentFeps() {
        return(currentFeps);
    }

    public Map<CookbookAttribute, Integer> baseAttributes() {
        return(baseAttributes);
    }

    public String fingerprint() {
        return(fingerprint);
    }

    private String buildFingerprint() {
        StringBuilder value = new StringBuilder();
        value.append(String.format(Locale.ROOT, "%.4f|%.4f|%.4f|", fepCap,
                currentTotalFep, hungerLevel));
        for(CookbookAttribute attribute : CookbookAttribute.values()) {
            if(attribute.allRecipes())
                continue;
            value.append(attribute.name()).append(':')
                    .append(String.format(Locale.ROOT, "%.4f", currentFep(attribute)))
                    .append(':').append(baseAttributes.getOrDefault(attribute, 0)).append('|');
        }
        for(FeastingCandidate candidate : candidates) {
            value.append(candidate.widgetId).append(':').append(candidate.quantity).append(':')
                    .append(candidate.infoSequence).append(':')
                    .append(String.format(Locale.ROOT, "%.4f:%.4f", candidate.totalFep,
                            candidate.hungerPermille));
            for(CookbookAttribute attribute : CookbookAttribute.values()) {
                if(!attribute.allRecipes())
                    value.append(':').append(String.format(Locale.ROOT, "%.4f",
                            candidate.fep(attribute)));
            }
            value.append('|');
        }
        return(value.toString());
    }
}
