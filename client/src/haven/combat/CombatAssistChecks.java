package haven.combat;

import haven.Coord;
import haven.UI;

/** Focused offline checks for combat-number tracking and animal-health presentation rules. */
public final class CombatAssistChecks {
    private static final String FOX = "gfx/kritter/fox/fox";
    private static final String BEAVER = "gfx/kritter/beaver/beaver";
    private static final long NOW = 10_000;

    private CombatAssistChecks() {
    }

    public static void main(String[] args) {
        checkParser();
        checkTrackerLifecycle();
        checkCatalogAndEstimates();
        checkLayoutPolicy();
        System.out.println("Combat assist checks passed.");
    }

    private static void checkParser() {
        CombatDamageEvent soft = CombatDamageEvent.fromFloatImage(
                12, 0, CombatDamageEvent.SOFT_HP_COLOR).orElseThrow();
        check(soft.type() == CombatDamageEvent.Type.SOFT_HP && soft.amount() == 12,
                "plain soft-HP number should parse");
        check(CombatDamageEvent.fromFloatImage(12, 2, CombatDamageEvent.SOFT_HP_COLOR).isEmpty(),
                "non-integer float-image encodings must be rejected");
        check(CombatDamageEvent.fromFloatImage(12, 0, 12345).isEmpty(),
                "unrelated float-image colors must be rejected");
    }

    private static void checkTrackerLifecycle() {
        CombatDamageTracker tracker = new CombatDamageTracker();
        tracker.beginCombat(1, FOX, NOW);
        check(tracker.record(1, FOX, 101, event(CombatDamageEvent.Type.SOFT_HP, 20), NOW + 1),
                "first damage event should be accepted");
        check(!tracker.record(1, FOX, 101, event(CombatDamageEvent.Type.SOFT_HP, 20), NOW + 1),
                "duplicate overlay event should be ignored");
        tracker.record(1, FOX, 102, event(CombatDamageEvent.Type.ARMOR, 15), NOW + 2);
        tracker.record(1, FOX, 103, event(CombatDamageEvent.Type.HARD_HP, 3), NOW + 3);
        tracker.record(2, "gfx/kritter/boar/boar", 201,
                event(CombatDamageEvent.Type.SOFT_HP, 40), NOW + 4);

        CombatDamageSnapshot first = tracker.snapshot(1, FOX, NOW + 5);
        check(first.totalSoftHp() == 20 && first.combatSoftHp() == 20,
                "soft-HP combat damage should accumulate once");
        check(first.totalArmor() == 15 && first.totalHardHp() == 3,
                "armor and hard-HP totals should remain separate");
        check(first.hasCombatSoftHpObservation(), "current combat should record a soft-HP observation");
        check(tracker.snapshot(2, "gfx/kritter/boar/boar", NOW + 5).totalSoftHp() == 40,
                "different gob IDs must retain independent state");

        tracker.endCombat(1);
        tracker.beginCombat(1, FOX, NOW + 10);
        CombatDamageSnapshot restarted = tracker.snapshot(1, FOX, NOW + 10);
        check(restarted.totalSoftHp() == 20 && restarted.combatSoftHp() == 0,
                "a new combat should preserve display totals but reset its estimate baseline");
        check(!restarted.hasCombatSoftHpObservation(),
                "a previous combat must not count as a current-combat observation");
        tracker.record(1, FOX, 104, event(CombatDamageEvent.Type.SOFT_HP, 5), NOW + 11);
        check(tracker.snapshot(1, FOX, NOW + 12).combatSoftHp() == 5,
                "new combat damage should be measured from the new baseline");

        tracker.record(1, FOX, 105, event(CombatDamageEvent.Type.SOFT_HP, -2), NOW + 13);
        check(!tracker.snapshot(1, FOX, NOW + 14).estimateReliable(),
                "negative soft-HP events should invalidate health estimation");
        check(tracker.snapshot(1, BEAVER, NOW + 15) == CombatDamageSnapshot.EMPTY,
                "resource identity changes must reset reused gob IDs");

        tracker.beginCombat(3, FOX, NOW);
        tracker.record(3, FOX, 301, event(CombatDamageEvent.Type.SOFT_HP, 9), NOW + 1);
        tracker.removeGob(3);
        check(tracker.snapshot(3, FOX, NOW + 2) == CombatDamageSnapshot.EMPTY,
                "gob removal must clear lifecycle state");
        tracker.clear();
        check(tracker.snapshot(2, "gfx/kritter/boar/boar", NOW) == CombatDamageSnapshot.EMPTY,
                "manual clear must clear every tracked gob");
    }

    private static void checkCatalogAndEstimates() {
        AnimalHealthCatalog.Entry fox = requireAnimal(FOX);
        check(fox.maxHp().kind() == AnimalHealthCatalog.EvidenceKind.EXACT,
                "fixed community value should retain its evidence kind");
        check(requireAnimal(BEAVER).maxHp().kind() == AnimalHealthCatalog.EvidenceKind.APPROXIMATE,
                "approximate value should not become exact");
        check(requireAnimal("gfx/kritter/ants/ants").maxHp().maximum() == 50,
                "live ant resource alias should resolve to the catalog row");
        check(requireAnimal("gfx/kritter/caveangler/caveangler").maxHp().kind() ==
                        AnimalHealthCatalog.EvidenceKind.RANGE,
                "range value should retain both bounds");
        check(requireAnimal("gfx/kritter/troll/troll").maxHp().kind() ==
                        AnimalHealthCatalog.EvidenceKind.LOWER_BOUND,
                "lower bound should not become a fixed maximum");
        check(AnimalHealthCatalog.find("gfx/borka/body") == null,
                "players must not be classified as animals");

        AnimalHealthEstimate noObservation = AnimalHealthEstimator.estimate(fox, snapshot(0, false, true, 0));
        check(noObservation.status() == AnimalHealthEstimate.Status.NO_OBSERVATION &&
                        noObservation.fraction() == null,
                "fixed max without observed damage should not imply current health");
        AnimalHealthEstimate estimated = AnimalHealthEstimator.estimate(fox, snapshot(20, true, true, 1));
        check(estimated.status() == AnimalHealthEstimate.Status.ESTIMATED &&
                        close(estimated.fraction(), 90.0 / 110.0),
                "observed current-combat damage should produce an estimated fixed-max fraction");
        AnimalHealthEstimate approximate = AnimalHealthEstimator.estimate(
                requireAnimal(BEAVER), snapshot(20, true, true, 1));
        check(approximate.status() == AnimalHealthEstimate.Status.APPROXIMATE_MAX &&
                        approximate.fraction() == null,
                "approximate max must remain denominator-free");
        AnimalHealthEstimate ranged = AnimalHealthEstimator.estimate(
                requireAnimal("gfx/kritter/caveangler/caveangler"), snapshot(20, true, true, 1));
        check(ranged.status() == AnimalHealthEstimate.Status.RANGE_MAX && ranged.fraction() == null,
                "context-dependent max must remain a range");
        AnimalHealthEstimate lowerBound = AnimalHealthEstimator.estimate(
                requireAnimal("gfx/kritter/troll/troll"), snapshot(20, true, true, 1));
        check(lowerBound.status() == AnimalHealthEstimate.Status.LOWER_BOUND_MAX &&
                        lowerBound.fraction() == null,
                "lower-bound max must not create a denominator");
        AnimalHealthEstimate unknown = AnimalHealthEstimator.estimate(
                requireAnimal("gfx/kritter/nidbane/nidbane"), snapshot(20, true, true, 1));
        check(unknown.status() == AnimalHealthEstimate.Status.UNKNOWN_MAX &&
                        unknown.fraction() == null,
                "unknown max must show observed damage without a denominator");
        AnimalHealthEstimate contradicted = AnimalHealthEstimator.estimate(fox, snapshot(120, true, true, 1));
        check(contradicted.status() == AnimalHealthEstimate.Status.CONTRADICTED &&
                        contradicted.fraction() == null,
                "damage above a fixed max should invalidate the bar");
        AnimalHealthEstimate stale = AnimalHealthEstimator.estimate(fox,
                snapshot(20, true, true, AnimalHealthEstimator.MAX_FRESH_AGE_MILLIS + 1));
        check(stale.status() == AnimalHealthEstimate.Status.STALE && stale.fraction() == null,
                "old observations should become visibly stale");
    }

    private static void checkLayoutPolicy() {
        Coord anchor = new Coord(200, 300);
        AnimalHealthBarRenderer.Layout layout = AnimalHealthBarRenderer.layout(anchor);
        check(layout.barTop().y == anchor.y - UI.scale(AnimalHealthBarRenderer.BASE_Y_OFFSET),
                "health bar should remain above existing combat data");
        check(!AnimalHealthBarRenderer.shouldDisplay(false, requireAnimal(FOX)),
                "disabled setting must suppress the feature");
        check(!AnimalHealthBarRenderer.shouldDisplay(true, null),
                "unknown resources must not receive a bar");
    }

    private static CombatDamageEvent event(CombatDamageEvent.Type type, int amount) {
        return(new CombatDamageEvent(type, amount));
    }

    private static CombatDamageSnapshot snapshot(long damage, boolean currentObservation,
                                                   boolean reliable, long age) {
        return(new CombatDamageSnapshot(damage, 0, 0, damage, currentObservation,
                currentObservation, false, false, true, reliable, age));
    }

    private static AnimalHealthCatalog.Entry requireAnimal(String resource) {
        AnimalHealthCatalog.Entry animal = AnimalHealthCatalog.find(resource);
        if(animal == null)
            throw(new AssertionError("missing animal catalog entry: " + resource));
        return(animal);
    }

    private static boolean close(Double actual, double expected) {
        return(actual != null && Math.abs(actual - expected) < 0.0001);
    }

    private static void check(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}
