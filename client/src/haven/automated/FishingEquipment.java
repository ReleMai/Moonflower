package haven.automated;

import haven.GItem;
import haven.GameUI;
import haven.WItem;

import java.util.List;

/** Coordinates the verified phases required to prepare and equip one fishing rig. */
final class FishingEquipment {
    private final GameUI gui;
    private final FishingItemLocator items;
    private final FishingPoleInspector inspector;
    private final FishingPoleAssembler assembler;
    private Phase phase = Phase.IDLE;

    FishingEquipment(GameUI gui) {
        this.gui = gui;
        items = new FishingItemLocator(gui);
        inspector = new FishingPoleInspector();
        assembler = new FishingPoleAssembler(gui, items, inspector);
    }

    synchronized Result prepare(String poleName, List<String> linePriority, List<String> hookPriority,
                                List<String> consumablePriority, boolean lure) throws InterruptedException {
        if(gui.vhand != null)
            return(fail("Clear the cursor before attaching tackle; the helper will not move the fishing rod."));

        phase = Phase.LOCATE_POLE;
        WItem pole = items.findPole(poleName, null);
        if(pole == null)
            return(fail("No reachable " + poleName + " was found in inventory, belt, or hands."));

        FishingPoleInspector.State state = inspector.inspect(pole);
        if(state.loading)
            return(waiting("Waiting for fishing-pole contents."));
        if(!state.unknown.isEmpty())
            return(fail("Unknown fishing-pole contents: " + String.join(", ", state.unknown)));

        FishingPoleInspector.Kind consumable = lure ?
                FishingPoleInspector.Kind.LURE : FishingPoleInspector.Kind.BAIT;
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
        if(verifiedState.loading || !verifiedState.ready(consumable))
            return(fail("The fishing pole did not retain its verified tackle."));

        phase = Phase.READY;
        boolean equipped = items.findPoleInHands(poleName, pole.item) != null;
        return(Result.ready(new Snapshot(FishingItemMetadata.describe(pole), verifiedState.line,
                verifiedState.hook, verifiedState.consumable(consumable),
                lure ? "lure" : "bait", equipped)));
    }

    synchronized boolean restoreDisplacedHands() throws InterruptedException {
        phase = Phase.IDLE;
        return(true);
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
}
