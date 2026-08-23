package haven.feasting;

import haven.cookbook.CookbookAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Explainable ordered result produced by {@link FeastingPlanner}. */
public final class FeastingPlan {
    public final CookbookAttribute target;
    public final boolean balancedTarget;
    public final FeastingSourceMode sourceMode;
    public final int selectedBase;
    public final int lowestBase;
    public final int highestBase;
    public final String balanceWarning;
    public final double cap;
    public final double startingTotal;
    public final double startingTarget;
    public final double currentChance;
    public final List<Entry> entries;

    FeastingPlan(CookbookAttribute target, boolean balancedTarget,
                 FeastingSourceMode sourceMode, int selectedBase,
                 int lowestBase, int highestBase, String balanceWarning, double cap,
                 double startingTotal, double startingTarget, List<Entry> entries) {
        this.target = target;
        this.balancedTarget = balancedTarget;
        this.sourceMode = sourceMode;
        this.selectedBase = selectedBase;
        this.lowestBase = lowestBase;
        this.highestBase = highestBase;
        this.balanceWarning = balanceWarning;
        this.cap = cap;
        this.startingTotal = startingTotal;
        this.startingTarget = startingTarget;
        this.currentChance = startingTotal <= 0 ? 0 : startingTarget / startingTotal;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public boolean empty() {
        return(entries.isEmpty());
    }

    public boolean reachesTrigger() {
        return(!entries.isEmpty() && entries.get(entries.size() - 1).fillsBar);
    }

    public static final class Entry {
        public final FeastingCandidate candidate;
        public final double projectedTargetFep;
        public final double projectedTotalFep;
        public final double projectedChance;
        public final double remainingFep;
        public final double overfill;
        public final boolean fillsBar;
        public final String reason;

        Entry(FeastingCandidate candidate, double projectedTargetFep,
              double projectedTotalFep, double projectedChance, double remainingFep,
              double overfill, boolean fillsBar, String reason) {
            this.candidate = candidate;
            this.projectedTargetFep = projectedTargetFep;
            this.projectedTotalFep = projectedTotalFep;
            this.projectedChance = projectedChance;
            this.remainingFep = remainingFep;
            this.overfill = overfill;
            this.fillsBar = fillsBar;
            this.reason = reason;
        }
    }
}
