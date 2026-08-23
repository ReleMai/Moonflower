package haven.feasting;

import haven.cookbook.CookbookAttribute;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure deterministic planner for the next food-event trigger. */
public final class FeastingPlanner {
    private static final double EPSILON = 0.000001;

    public FeastingPlan plan(FeastingSnapshot snapshot, CookbookAttribute selected,
                             boolean balancedTarget) {
        return(plan(snapshot, selected, balancedTarget,
                FeastingSourceMode.TABLE_AND_INVENTORY));
    }

    public FeastingPlan plan(FeastingSnapshot snapshot, CookbookAttribute selected,
                             boolean balancedTarget, FeastingSourceMode sourceMode) {
        if(sourceMode == null)
            sourceMode = FeastingSourceMode.TABLE_AND_INVENTORY;
        CookbookAttribute target = chooseTarget(snapshot.baseAttributes(), selected,
                balancedTarget);
        int lowest = extreme(snapshot.baseAttributes(), true);
        int highest = extreme(snapshot.baseAttributes(), false);
        int selectedBase = snapshot.baseAttributes().getOrDefault(target, 0);
        String warning = balanceWarning(target, selectedBase, lowest, highest,
                snapshot.baseAttributes());

        List<FeastingCandidate> remaining = new ArrayList<>();
        Set<Integer> plannedWidgets = new HashSet<>();
        for(FeastingCandidate candidate : snapshot.candidates) {
            if(sourceMode.allows(candidate) && candidate.totalFep > EPSILON &&
                    candidate.fep(target) > EPSILON &&
                    plannedWidgets.add(candidate.widgetId)) {
                for(int unit = 0; unit < Math.max(1, candidate.quantity); unit++)
                    remaining.add(candidate);
            }
        }

        List<FeastingPlan.Entry> entries = new ArrayList<>();
        double projectedTarget = snapshot.currentFep(target);
        double projectedTotal = snapshot.currentTotalFep;
        while(projectedTotal + EPSILON < snapshot.fepCap && !remaining.isEmpty()) {
            double currentTarget = projectedTarget;
            double currentTotal = projectedTotal;
            FeastingCandidate next = remaining.stream().min(candidateOrder(target,
                    currentTarget, currentTotal, snapshot.fepCap)).orElse(null);
            if(next == null)
                break;
            remaining.remove(next);
            projectedTarget += next.fep(target);
            projectedTotal += next.totalFep;
            boolean fills = projectedTotal + EPSILON >= snapshot.fepCap;
            double chance = projectedTotal <= EPSILON ? 0 : projectedTarget / projectedTotal;
            double remainingFep = Math.max(0, snapshot.fepCap - projectedTotal);
            double overfill = Math.max(0, projectedTotal - snapshot.fepCap);
            String reason;
            if(fills) {
                reason = overfill > EPSILON ? String.format(Locale.ROOT,
                        "Reaches trigger; %.2f FEP overfill", overfill) : "Reaches next trigger";
            } else if(entries.isEmpty()) {
                reason = "Best projected target share";
            } else {
                reason = "Preserves target share while filling the bar";
            }
            entries.add(new FeastingPlan.Entry(next, projectedTarget, projectedTotal,
                    chance, remainingFep, overfill, fills, reason));
        }
        return(new FeastingPlan(target, balancedTarget, sourceMode, selectedBase, lowest,
                highest, warning, snapshot.fepCap, snapshot.currentTotalFep,
                snapshot.currentFep(target), entries));
    }

    private static Comparator<FeastingCandidate> candidateOrder(CookbookAttribute target,
                                                                 double currentTarget,
                                                                 double currentTotal,
                                                                 double cap) {
        return((left, right) -> {
            Projection a = project(left, target, currentTarget, currentTotal, cap);
            Projection b = project(right, target, currentTarget, currentTotal, cap);
            int compare = Double.compare(b.chance, a.chance);
            if(compare != 0)
                return(compare);
            if(a.fills && b.fills) {
                compare = Double.compare(a.overfill, b.overfill);
                if(compare != 0)
                    return(compare);
            }
            compare = Double.compare(hungerPerTarget(left, target),
                    hungerPerTarget(right, target));
            if(compare != 0)
                return(compare);
            compare = Double.compare(right.fep(target), left.fep(target));
            if(compare != 0)
                return(compare);
            compare = Integer.compare(left.source.ordinal(), right.source.ordinal());
            if(compare != 0)
                return(compare);
            compare = left.resourceName.compareToIgnoreCase(right.resourceName);
            if(compare != 0)
                return(compare);
            compare = Double.compare(right.quality, left.quality);
            if(compare != 0)
                return(compare);
            return(Integer.compare(left.widgetId, right.widgetId));
        });
    }

    private static Projection project(FeastingCandidate candidate, CookbookAttribute target,
                                      double currentTarget, double currentTotal, double cap) {
        double total = currentTotal + candidate.totalFep;
        double chance = total <= EPSILON ? 0 :
                (currentTarget + candidate.fep(target)) / total;
        return(new Projection(chance, total + EPSILON >= cap, Math.max(0, total - cap)));
    }

    private static double hungerPerTarget(FeastingCandidate candidate,
                                         CookbookAttribute target) {
        double targetFep = candidate.fep(target);
        if(targetFep <= EPSILON)
            return(Double.POSITIVE_INFINITY);
        return(Math.max(0, candidate.hungerPermille) / targetFep);
    }

    static CookbookAttribute chooseTarget(Map<CookbookAttribute, Integer> bases,
                                          CookbookAttribute selected, boolean balanced) {
        CookbookAttribute fallback = (selected == null || selected.allRecipes()) ?
                CookbookAttribute.STRENGTH : selected;
        if(!balanced || bases.isEmpty())
            return(fallback);
        CookbookAttribute result = null;
        int lowest = Integer.MAX_VALUE;
        for(CookbookAttribute attribute : CookbookAttribute.values()) {
            if(attribute.allRecipes() || !bases.containsKey(attribute))
                continue;
            int value = bases.get(attribute);
            if(value < lowest) {
                lowest = value;
                result = attribute;
            }
        }
        return(result == null ? fallback : result);
    }

    private static int extreme(Map<CookbookAttribute, Integer> bases, boolean minimum) {
        if(bases.isEmpty())
            return(0);
        int result = minimum ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        for(Map.Entry<CookbookAttribute, Integer> entry : bases.entrySet()) {
            if(entry.getKey().allRecipes())
                continue;
            result = minimum ? Math.min(result, entry.getValue()) :
                    Math.max(result, entry.getValue());
        }
        if(result == Integer.MAX_VALUE || result == Integer.MIN_VALUE)
            return(0);
        return(result);
    }

    private static String balanceWarning(CookbookAttribute target, int selectedBase,
                                         int lowest, int highest,
                                         Map<CookbookAttribute, Integer> bases) {
        if(bases.isEmpty())
            return("Base attributes are not available yet.");
        long highestCount = bases.entrySet().stream()
                .filter(entry -> !entry.getKey().allRecipes() && entry.getValue() == highest)
                .count();
        if(selectedBase == highest && highest > lowest && highestCount == 1)
            return(String.format(Locale.ROOT,
                    "%s is already the highest attribute, %d above the lowest.",
                    target.label, selectedBase - lowest));
        if(selectedBase > lowest)
            return(String.format(Locale.ROOT, "%s is %d above the lowest attribute.",
                    target.label, selectedBase - lowest));
        return("");
    }

    private static final class Projection {
        final double chance;
        final boolean fills;
        final double overfill;

        Projection(double chance, boolean fills, double overfill) {
            this.chance = chance;
            this.fills = fills;
            this.overfill = overfill;
        }
    }
}
