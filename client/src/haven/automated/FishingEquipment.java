package haven.automated;

import haven.GItem;
import haven.GameUI;
import haven.WItem;

import java.util.Comparator;
import java.util.List;

/** Coordinates the verified phases required to prepare and equip one fishing rig. */
final class FishingEquipment {
    private final GameUI gui;
    private final FishingItemLocator items;
    private final FishingPoleInspector inspector;
    private final FishingPoleAssembler assembler;
    private final FishingHandManager hands;
    private Phase phase = Phase.IDLE;

    FishingEquipment(GameUI gui) {
        this.gui = gui;
        items = new FishingItemLocator(gui);
        inspector = new FishingPoleInspector();
        assembler = new FishingPoleAssembler(gui, items, inspector);
        hands = new FishingHandManager(gui, items);
    }

    synchronized Result prepare(String poleName, List<String> linePriority, List<String> hookPriority,
                                List<String> consumablePriority, boolean lure,
                                boolean equipPole) throws InterruptedException {
        if(gui.vhand != null)
            return(fail("Clear the cursor before attaching tackle; the helper will not move the fishing rod."));

        phase = Phase.LOCATE_POLE;
        FishingPoleInspector.Kind consumable = lure ?
                FishingPoleInspector.Kind.LURE : FishingPoleInspector.Kind.BAIT;
        PoleCandidate candidate = choosePole(poleName, linePriority, hookPriority,
                consumablePriority, consumable);
        if(candidate == null)
            return(fail("No reachable " + poleName + " was found in inventory, belt, or hands."));
        if(candidate.loading)
            return(waiting("Waiting for fishing-pole contents."));
        if(!candidate.compatible)
            return(fail("Reachable " + poleName + " poles contain different tackle than the selected preset."));
        WItem pole = candidate.pole;
        FishingPoleInspector.State state = candidate.state;
        if(!state.unknown.isEmpty())
            return(fail("Unknown fishing-pole contents: " + String.join(", ", state.unknown)));

        if(!state.ready(consumable)) {
            phase = Phase.ASSEMBLE;
            FishingPoleAssembler.Outcome assembled = assembler.assemble(poleName, pole,
                    linePriority, hookPriority, consumablePriority, consumable);
            if(!assembled.success())
                return(fail(assembled.message));
            pole = assembled.pole;
            state = assembled.state;
        }

        phase = Phase.VERIFY;
        FishingPoleInspector.State verifiedState = inspector.inspect(pole);
        if(verifiedState.loading || !verifiedState.ready(consumable) ||
                !verifiedState.compatible(linePriority, hookPriority, consumablePriority, consumable))
            return(fail("The fishing pole did not retain the selected verified tackle."));

        phase = Phase.READY;
        boolean equipped = items.findPoleInHands(poleName, pole.item) != null;
        if(equipPole && !equipped) {
            String equipError = hands.equip(poleName, pole);
            if(equipError != null)
                return(fail(equipError));
            pole = items.findPoleInHands(poleName, null);
            equipped = pole != null;
            if(!equipped)
                return(fail("The selected pole was not found in either hand after equipping."));
        }
        return(Result.ready(new Snapshot(FishingItemMetadata.describe(pole), verifiedState.line,
                verifiedState.hook, verifiedState.consumable(consumable),
                lure ? "lure" : "bait", equipped)));
    }

    private PoleCandidate choosePole(String poleName, List<String> lines, List<String> hooks,
                                     List<String> consumables, FishingPoleInspector.Kind consumableKind) {
        List<PoleCandidate> candidates = new java.util.ArrayList<>();
        boolean loading = false;
        for(WItem pole : items.matchingPoles(poleName)) {
            FishingPoleInspector.State state = inspector.inspect(pole);
            if(state.loading) {
                loading = true;
                continue;
            }
            boolean compatible = state.compatible(lines, hooks, consumables, consumableKind);
            if(compatible)
                candidates.add(new PoleCandidate(pole, state, true, false));
        }
        candidates.sort(Comparator
                .comparingInt((PoleCandidate value) -> value.state.ready(consumableKind) ? 0 : 1)
                .thenComparingInt(value -> missing(value.state, consumableKind)));
        if(!candidates.isEmpty())
            return(candidates.get(0));
        if(loading)
            return(new PoleCandidate(null, null, false, true));
        return(items.matchingPoles(poleName).isEmpty() ? null :
                new PoleCandidate(null, null, false, false));
    }

    private static int missing(FishingPoleInspector.State state, FishingPoleInspector.Kind consumable) {
        int missing = state.line == null ? 1 : 0;
        missing += state.hook == null ? 1 : 0;
        missing += state.consumable(consumable) == null ? 1 : 0;
        return(missing);
    }

    synchronized boolean restoreDisplacedHands() throws InterruptedException {
        phase = Phase.IDLE;
        return(hands.restore());
    }

    private Result fail(String message) {
        Phase failedPhase = phase;
        phase = Phase.FAILED;
        return(Result.error(failedPhase.label + ": " + message));
    }

    private Result waiting(String message) {
        phase = Phase.IDLE;
        return(Result.waiting(message));
    }

    static ItemData describe(WItem item) {
        return(FishingItemMetadata.describe(item));
    }

    static ItemData describe(GItem item) {
        return(FishingItemMetadata.describe(item));
    }

    private enum Phase {
        IDLE("Idle"),
        LOCATE_POLE("Pole lookup"),
        ASSEMBLE("Tackle assembly"),
        VERIFY("Final verification"),
        READY("Ready"),
        FAILED("Failed");

        final String label;

        Phase(String label) {
            this.label = label;
        }
    }

    static final class ItemData {
        static final ItemData EMPTY = new ItemData("", "", null);
        final String resourceName;
        final String displayName;
        final Double quality;

        ItemData(String resourceName, String displayName, Double quality) {
            this.resourceName = resourceName == null ? "" : resourceName;
            this.displayName = displayName == null ? "" : displayName;
            this.quality = quality;
        }
    }

    static final class Snapshot {
        final ItemData pole;
        final ItemData line;
        final ItemData hook;
        final ItemData consumable;
        final String consumableKind;
        final boolean equipped;

        Snapshot(ItemData pole, ItemData line, ItemData hook, ItemData consumable,
                 String consumableKind, boolean equipped) {
            this.pole = pole;
            this.line = line;
            this.hook = hook;
            this.consumable = consumable;
            this.consumableKind = consumableKind;
            this.equipped = equipped;
        }
    }

    static final class Result {
        final Snapshot snapshot;
        final String message;
        final boolean waiting;

        private Result(Snapshot snapshot, String message, boolean waiting) {
            this.snapshot = snapshot;
            this.message = message;
            this.waiting = waiting;
        }

        static Result ready(Snapshot snapshot) { return(new Result(snapshot, "", false)); }
        static Result waiting(String message) { return(new Result(null, message, true)); }
        static Result error(String message) { return(new Result(null, message, false)); }
        boolean ready() { return(snapshot != null); }
    }

    private static final class PoleCandidate {
        final WItem pole;
        final FishingPoleInspector.State state;
        final boolean compatible;
        final boolean loading;

        PoleCandidate(WItem pole, FishingPoleInspector.State state, boolean compatible, boolean loading) {
            this.pole = pole;
            this.state = state;
            this.compatible = compatible;
            this.loading = loading;
        }
    }
}
