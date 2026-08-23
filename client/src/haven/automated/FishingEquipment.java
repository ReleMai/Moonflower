package haven.automated;

import haven.GItem;
import haven.GameUI;
import haven.WItem;

import java.util.List;

/** Coordinates the verified phases required to prepare and equip one fishing rig. */
final class FishingEquipment {
    private final FishingItemLocator items;
    private final FishingHandManager hands;
    private final FishingPoleInspector inspector;
    private final FishingPoleAssembler assembler;
    private Phase phase = Phase.IDLE;

    FishingEquipment(GameUI gui) {
        items = new FishingItemLocator(gui);
        inspector = new FishingPoleInspector();
        hands = new FishingHandManager(gui, items);
        assembler = new FishingPoleAssembler(gui, items, inspector);
    }

    synchronized Result prepare(String poleName, List<String> linePriority, List<String> hookPriority,
                                List<String> consumablePriority, boolean lure) throws InterruptedException {
        phase = Phase.NORMALIZE_CURSOR;
        if(!hands.stowFishingCursor())
            return(fail("Clear the cursor or make inventory room before preparing the fishing pole."));

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
            phase = Phase.STATION_POLE;
            pole = hands.movePoleToMainInventory(poleName, pole);
            if(pole == null)
                return(fail("The incomplete " + poleName +
                        " could not be placed in inventory for assembly."));

            phase = Phase.ASSEMBLE;
            FishingPoleAssembler.Outcome assembled = assembler.assemble(poleName, pole,
                    linePriority, hookPriority, consumablePriority, consumable);
            if(!assembled.success())
                return(fail(assembled.message));
            pole = assembled.pole;
            state = assembled.state;
        }

        phase = Phase.EQUIP;
        WItem equipped = hands.equipPole(poleName, pole);
        if(equipped == null)
            return(fail("The prepared " + poleName +
                    " could not be placed in an allowed empty hand slot."));

        phase = Phase.VERIFY;
        FishingPoleInspector.State equippedState = inspector.awaitReady(equipped, consumable);
        if(equippedState == null)
            return(fail("The equipped pole did not retain its verified tackle."));

        phase = Phase.READY;
        return(Result.ready(new Snapshot(FishingItemMetadata.describe(equipped), equippedState.line,
                equippedState.hook, equippedState.consumable(consumable),
                lure ? "lure" : "bait")));
    }

    synchronized boolean restoreDisplacedHands() throws InterruptedException {
        phase = Phase.RESTORE;
        boolean restored = hands.restoreDisplacedHands();
        phase = Phase.IDLE;
        return(restored);
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
        NORMALIZE_CURSOR("Cursor cleanup"),
        LOCATE_POLE("Pole lookup"),
        STATION_POLE("Pole stationing"),
        ASSEMBLE("Tackle assembly"),
        EQUIP("Pole equip"),
        VERIFY("Final verification"),
        READY("Ready"),
        RESTORE("Hand restoration"),
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

        Snapshot(ItemData pole, ItemData line, ItemData hook, ItemData consumable,
                 String consumableKind) {
            this.pole = pole;
            this.line = line;
            this.hook = hook;
            this.consumable = consumable;
            this.consumableKind = consumableKind;
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
