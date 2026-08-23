package haven.feasting;

import haven.cookbook.CookbookAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Focused offline checks for planning, balancing, acknowledgement, and safety. */
public final class FeastingChecks {
    private FeastingChecks() {
    }

    public static void main(String[] args) {
        FeastingPlanner planner = new FeastingPlanner();
        Map<CookbookAttribute, Integer> bases = bases(10, 10, 10, 10, 10, 10, 10, 10, 10);
        List<FeastingCandidate> foods = List.of(
                food(101, FeastingCandidate.Source.TABLE, "Pure strength meal", 4, 4, 1),
                food(102, FeastingCandidate.Source.INVENTORY, "Small strength snack", 2, 2, 0.5),
                mixedFood(103, "Mixed stew", 3, 3, 6, 0.2));
        FeastingSnapshot partial = snapshot(10, 4, 2, bases, foods);
        FeastingPlan strength = planner.plan(partial, CookbookAttribute.STRENGTH, false);
        check(strength.entries.size() == 2, "planner should stop at the next trigger");
        check(strength.entries.get(0).candidate.widgetId == 101,
                "highest projected target share should be first");
        check(strength.entries.get(1).candidate.widgetId == 102,
                "pure target food should reach the trigger before a mixed food");
        check(strength.reachesTrigger(), "planned sequence should reach the FEP cap");
        check(close(strength.entries.get(1).projectedChance, 0.8),
                "projected target chance is incorrect");
        check(close(strength.entries.get(1).overfill, 0),
                "exact cap fill should not report overfill");

        FeastingPlan zeroCurrentTarget = planner.plan(snapshot(10, 5, 0, bases,
                        List.of(food(105, FeastingCandidate.Source.TABLE,
                                "Future strength", 2, 2, 1))),
                CookbookAttribute.STRENGTH, false);
        check(close(zeroCurrentTarget.currentChance, 0),
                "zero character-sheet target FEP must report zero current chance");
        check(zeroCurrentTarget.entries.get(0).projectedChance > 0,
                "projected chance should remain distinct from current chance");

        FeastingPlan doesNotFill = planner.plan(snapshot(12, 2, 1, bases,
                List.of(food(110, FeastingCandidate.Source.TABLE, "Small bite", 2, 2, 1))),
                CookbookAttribute.STRENGTH, false);
        check(!doesNotFill.reachesTrigger() &&
                        close(doesNotFill.entries.get(0).remainingFep, 8),
                "non-triggering plan should expose remaining FEP");

        List<FeastingCandidate> tied = List.of(
                food(201, FeastingCandidate.Source.INVENTORY, "Inventory twin", 3, 3, 1),
                food(202, FeastingCandidate.Source.TABLE, "Table twin", 3, 3, 1));
        FeastingPlan sourceTie = planner.plan(snapshot(5, 0, 0, bases, tied),
                CookbookAttribute.STRENGTH, false);
        check(sourceTie.entries.get(0).candidate.widgetId == 202,
                "Table source should win an otherwise exact tie");
        FeastingPlan inventoryOnly = planner.plan(snapshot(5, 0, 0, bases, tied),
                CookbookAttribute.STRENGTH, false, FeastingSourceMode.INVENTORY_ONLY);
        check(inventoryOnly.entries.size() == 1 &&
                        inventoryOnly.entries.get(0).candidate.widgetId == 201,
                "inventory-only mode must exclude Table food from its plan");
        FeastingPlan noInventoryFood = planner.plan(snapshot(5, 0, 0, bases,
                        List.of(food(203, FeastingCandidate.Source.TABLE,
                                "Table only", 3, 3, 1))),
                CookbookAttribute.STRENGTH, false, FeastingSourceMode.INVENTORY_ONLY);
        check(noInventoryFood.empty(),
                "inventory-only mode must not fall back to Table food");

        List<FeastingCandidate> hungerTie = List.of(
                food(301, FeastingCandidate.Source.TABLE, "Hungry", 3, 3, 8),
                food(302, FeastingCandidate.Source.TABLE, "Efficient", 3, 3, 2));
        FeastingPlan efficient = planner.plan(snapshot(5, 0, 0, bases, hungerTie),
                CookbookAttribute.STRENGTH, false);
        check(efficient.entries.get(0).candidate.widgetId == 302,
                "lower hunger should break an equal FEP tie");

        List<FeastingCandidate> duplicateWidget = List.of(
                food(401, FeastingCandidate.Source.TABLE, "Same widget", 2, 2, 1),
                food(401, FeastingCandidate.Source.TABLE, "Same widget duplicate", 2, 2, 1));
        FeastingPlan deduplicated = planner.plan(snapshot(10, 0, 0, bases, duplicateWidget),
                CookbookAttribute.STRENGTH, false);
        check(deduplicated.entries.size() == 1,
                "one physical food widget must not be planned twice");

        FeastingCandidate stack = food(402, FeastingCandidate.Source.TABLE,
                "Two-bite stack", 2, 2, 1, 2);
        FeastingPlan stacked = planner.plan(snapshot(10, 0, 0, bases, List.of(stack)),
                CookbookAttribute.STRENGTH, false);
        check(stacked.entries.size() == 2,
                "distinct units exposed by one stack should each be planned once");

        Map<CookbookAttribute, Integer> imbalanced = bases(20, 5, 8, 5, 9, 7, 6, 10, 11);
        FeastingPlan balanced = planner.plan(snapshot(10, 0, 0, imbalanced, foods),
                CookbookAttribute.STRENGTH, true);
        check(balanced.target == CookbookAttribute.AGILITY,
                "Balanced target should choose the first tied-lowest native attribute");
        FeastingPlan highTarget = planner.plan(snapshot(10, 0, 0, imbalanced, foods),
                CookbookAttribute.STRENGTH, false);
        check(highTarget.balanceWarning.contains("already the highest") &&
                        highTarget.selectedBase - highTarget.lowestBase == 15,
                "highest-attribute balance warning is incorrect");

        check(FeastingController.observedProgress(false, 1, -1, 4, -1, "a", "a"),
                "removed item should acknowledge a bite");
        check(FeastingController.observedProgress(true, 4, 3, 1, 1, "a", "a"),
                "stack decrement should acknowledge a bite");
        check(FeastingController.observedProgress(true, 1, 1, 1, 1, "a", "b"),
                "FEP or hunger snapshot change should acknowledge a bite");
        check(!FeastingController.observedProgress(true, 1, 1, 1, 1, "a", "a"),
                "unchanged state must not acknowledge a bite");

        check(FeastingController.tablewareAllowsDispatch(false,
                        FeastingSnapshot.TablewareState.SAFE) &&
                        !FeastingController.tablewareAllowsDispatch(false,
                                FeastingSnapshot.TablewareState.AT_RISK) &&
                        !FeastingController.tablewareAllowsDispatch(false,
                                FeastingSnapshot.TablewareState.UNKNOWN),
                "safe mode must fail closed for risky or unreadable tableware");
        check(FeastingController.tablewareAllowsDispatch(true,
                        FeastingSnapshot.TablewareState.AT_RISK),
                "confirmed breakage mode should permit a helper dispatch");

        check(!FeastingActionContext.allowsTablewareBreakage(),
                "breakage override leaked before its scope");
        try(FeastingActionContext.Scope ignored = FeastingActionContext.allowTablewareBreakage()) {
            check(FeastingActionContext.allowsTablewareBreakage(),
                    "breakage override is unavailable inside its scope");
            try(FeastingActionContext.Scope nested = FeastingActionContext.allowTablewareBreakage()) {
                check(FeastingActionContext.allowsTablewareBreakage(),
                        "nested breakage scope is unavailable");
            }
            check(FeastingActionContext.allowsTablewareBreakage(),
                    "nested scope cleared its parent early");
        }
        check(!FeastingActionContext.allowsTablewareBreakage(),
                "breakage override leaked after its scope");

        System.out.println("Feasting checks passed.");
    }

    private static FeastingSnapshot snapshot(double cap, double currentTotal,
                                              double currentStrength,
                                              Map<CookbookAttribute, Integer> bases,
                                              List<FeastingCandidate> foods) {
        EnumMap<CookbookAttribute, Double> current = new EnumMap<>(CookbookAttribute.class);
        if(currentStrength > 0)
            current.put(CookbookAttribute.STRENGTH, currentStrength);
        return(new FeastingSnapshot(cap, currentTotal, 0.25, 0,
                FeastingSnapshot.TablewareState.SAFE, Collections.emptyList(), current,
                bases, foods));
    }

    private static FeastingCandidate food(int id, FeastingCandidate.Source source, String name,
                                           double target, double total, double hunger) {
        return(food(id, source, name, target, total, hunger, 1));
    }

    private static FeastingCandidate food(int id, FeastingCandidate.Source source, String name,
                                           double target, double total, double hunger,
                                           int quantity) {
        EnumMap<CookbookAttribute, Double> feps = new EnumMap<>(CookbookAttribute.class);
        feps.put(CookbookAttribute.STRENGTH, target);
        return(new FeastingCandidate(id, quantity, 1, source, name, "gfx/invobjs/testfood",
                10, hunger, 20, total, feps));
    }

    private static FeastingCandidate mixedFood(int id, String name, double strength,
                                                double agility, double total, double hunger) {
        EnumMap<CookbookAttribute, Double> feps = new EnumMap<>(CookbookAttribute.class);
        feps.put(CookbookAttribute.STRENGTH, strength);
        feps.put(CookbookAttribute.AGILITY, agility);
        return(new FeastingCandidate(id, 1, 1, FeastingCandidate.Source.TABLE, name,
                "gfx/invobjs/testfood", 10, hunger, 20, total, feps));
    }

    private static Map<CookbookAttribute, Integer> bases(int strength, int agility,
                                                         int intelligence, int constitution,
                                                         int perception, int charisma,
                                                         int dexterity, int will, int psyche) {
        EnumMap<CookbookAttribute, Integer> result = new EnumMap<>(CookbookAttribute.class);
        int[] values = {strength, agility, intelligence, constitution, perception, charisma,
                dexterity, will, psyche};
        List<CookbookAttribute> attributes = new ArrayList<>(FeastingLiveSnapshot.foodAttributes());
        for(int index = 0; index < attributes.size(); index++)
            result.put(attributes.get(index), values[index]);
        return(result);
    }

    private static boolean close(double actual, double expected) {
        return(Math.abs(actual - expected) < 0.0001);
    }

    private static void check(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}
