package haven.fishing;

import java.util.ArrayList;
import java.util.List;

/** Explains the quality inputs captured with a catch without claiming hidden server values. */
final class FishingQualityAnalysis {
    private FishingQualityAnalysis() {
    }

    static Result analyze(FishingObservation observation) {
        List<Factor> factors = new ArrayList<>();
        factors.add(new Factor("Pole", observation.poleName, observation.poleQuality));
        factors.add(new Factor("Line", observation.lineName, observation.lineQuality));
        factors.add(new Factor("Hook", observation.hookName, observation.hookQuality));
        factors.add(new Factor(kind(observation.consumableKind), observation.consumableName,
                observation.consumableQuality));

        double total = 0;
        int known = 0;
        Double weakest = null;
        for(Factor factor : factors) {
            if(factor.quality == null)
                continue;
            total += factor.quality;
            known++;
            if(weakest == null || factor.quality < weakest)
                weakest = factor.quality;
        }
        Double average = known == factors.size() ? total / known : null;
        List<String> weakestFactors = new ArrayList<>();
        if(weakest != null) {
            for(Factor factor : factors) {
                if(factor.quality != null && Math.abs(factor.quality - weakest) < 0.0001)
                    weakestFactors.add(factor.label);
            }
        }
        return(new Result(factors, average, weakest, weakestFactors));
    }

    private static String kind(String value) {
        if(value == null || value.isBlank())
            return("Bait/lure");
        return(Character.toUpperCase(value.charAt(0)) + value.substring(1));
    }

    static final class Result {
        final List<Factor> factors;
        final Double tackleAverage;
        final Double weakestQuality;
        final List<String> weakestFactors;

        Result(List<Factor> factors, Double tackleAverage, Double weakestQuality,
               List<String> weakestFactors) {
            this.factors = List.copyOf(factors);
            this.tackleAverage = tackleAverage;
            this.weakestQuality = weakestQuality;
            this.weakestFactors = List.copyOf(weakestFactors);
        }
    }

    static final class Factor {
        final String label;
        final String name;
        final Double quality;

        Factor(String label, String name, Double quality) {
            this.label = label;
            this.name = name == null ? "" : name;
            this.quality = quality;
        }
    }
}
