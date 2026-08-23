package haven.automated;

import haven.Coord;
import haven.GItem;
import haven.GameUI;
import haven.Inventory;
import haven.WItem;
import haven.Widget;
import haven.res.ui.stackinv.ItemStack;

import java.util.List;

/** Performs Haven's native take-tackle/right-click-rod gesture without moving the rod. */
final class FishingPoleAssembler {
    private static final long TAKE_TIMEOUT_MS = 3000;
    private static final long ATTACH_TIMEOUT_MS = 5000;
    private final GameUI gui;
    private final FishingItemLocator items;
    private final FishingPoleInspector inspector;

    FishingPoleAssembler(GameUI gui, FishingItemLocator items, FishingPoleInspector inspector) {
        this.gui = gui;
        this.items = items;
        this.inspector = inspector;
    }

    Outcome assemble(String poleName, WItem pole, List<String> lines, List<String> hooks,
                     List<String> consumables, FishingPoleInspector.Kind consumable)
            throws InterruptedException {
        GItem poleItem = pole.item;
        FishingPoleInspector.State state = inspector.inspect(pole);
        for(FishingPoleInspector.Kind kind : new FishingPoleInspector.Kind[]{
                FishingPoleInspector.Kind.LINE, FishingPoleInspector.Kind.HOOK, consumable}) {
            if(state.has(kind))
                continue;
            List<String> priority = kind == FishingPoleInspector.Kind.LINE ? lines :
                    kind == FishingPoleInspector.Kind.HOOK ? hooks : consumables;
            WItem component = items.candidate(kind, priority);
            if(component == null)
                return(Outcome.error("No selected " + label(kind) +
                        " is reachable in inventory or an equipped Creel."));
            String error = attachOne(poleName, poleItem, component, kind);
            if(error != null)
                return(Outcome.error(error));
            pole = items.findPole(poleName, poleItem);
            if(pole == null)
                return(Outcome.error("The stationary fishing pole disappeared during assembly."));
            state = inspector.inspect(pole);
            if(state.loading || !state.has(kind))
                return(Outcome.error("The server did not confirm the attached " + label(kind) + "."));
        }
        return(state.ready(consumable) ? Outcome.ready(pole, state) :
                Outcome.error("The fishing pole did not reach a complete verified state."));
    }

    private String attachOne(String poleName, GItem poleItem, WItem component,
                             FishingPoleInspector.Kind kind) throws InterruptedException {
        if(gui.vhand != null)
            return("The cursor changed before the " + label(kind) + " could be taken.");
        CursorOrigin origin = new CursorOrigin(component);
        GItem moving = component.item;
        // Match an ordinary left-click on the centre of the tackle item.
        moving.wdgmsg("take", new Coord(component.sz.x / 2, component.sz.y / 2));
        if(!await(() -> gui.vhand != null && gui.vhand.item == moving, TAKE_TIMEOUT_MS))
            return("The selected " + label(kind) + " was not placed on the cursor.");

        WItem target = items.findPole(poleName, poleItem);
        if(target == null) {
            origin.returnHeld(gui);
            return("The selected fishing pole disappeared before the " + label(kind) + " interaction could be sent.");
        }

        /*
         * WItem.iteminteract emits this target-only message. The Haven server
         * takes the source item from vhand, so there is no cursor-free protocol
         * to use here; importantly, the pole itself is never picked up or moved.
         */
        target.item.wdgmsg("itemact", 0);
        boolean attached = await(() -> {
            WItem current = items.findPole(poleName, poleItem);
            FishingPoleInspector.State state = inspector.inspect(current);
            return(current != null && !state.loading && state.has(kind));
        }, ATTACH_TIMEOUT_MS);
        if(!attached) {
            origin.returnHeld(gui);
            return("Right-clicking the " + label(kind) + " onto the stationary pole was not acknowledged.");
        }
        await(() -> gui.vhand == null || gui.vhand.item != moving, 1000);
        return(null);
    }

    private static String label(FishingPoleInspector.Kind kind) {
        switch(kind) {
        case LINE: return("fishline");
        case HOOK: return("hook");
        case BAIT: return("bait");
        case LURE: return("lure");
        default: return("tackle");
        }
    }

    private static boolean await(Check check, long timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout;
        while(System.currentTimeMillis() < deadline) {
            if(check.ready())
                return(true);
            Thread.sleep(50);
        }
        return(check.ready());
    }

    private interface Check { boolean ready(); }

    private static final class CursorOrigin {
        final Widget parent;
        final Coord coordinate;

        CursorOrigin(WItem item) {
            parent = item.parent;
            coordinate = Inventory.fromWidget(parent) == null ? null : item.c.div(Inventory.sqsz);
        }

        void returnHeld(GameUI gui) throws InterruptedException {
            if(gui.vhand == null)
                return;
            if(parent instanceof ItemStack) {
                parent.wdgmsg("drop");
                if(await(() -> gui.vhand == null, 1500))
                    return;
            }
            Inventory source = Inventory.fromWidget(parent);
            Inventory target = source == null ? gui.maininv : source;
            Coord destination = coordinate == null ? roomFor(target, gui.vhand) : coordinate;
            if(target != null && destination != null) {
                target.wdgmsg("drop", destination);
                await(() -> gui.vhand == null, 1500);
            }
        }
    }

    private static Coord roomFor(Inventory inventory, WItem item) {
        if(inventory == null || item == null)
            return(null);
        int width = Math.max(1, (item.sz.x + Inventory.sqsz.x - 1) / Inventory.sqsz.x);
        int height = Math.max(1, (item.sz.y + Inventory.sqsz.y - 1) / Inventory.sqsz.y);
        return(inventory.isRoom(width, height));
    }

    static final class Outcome {
        final WItem pole;
        final FishingPoleInspector.State state;
        final String message;

        private Outcome(WItem pole, FishingPoleInspector.State state, String message) {
            this.pole = pole;
            this.state = state;
            this.message = message;
        }

        static Outcome ready(WItem pole, FishingPoleInspector.State state) {
            return(new Outcome(pole, state, ""));
        }

        static Outcome error(String message) {
            return(new Outcome(null, null, message));
        }

        boolean success() { return(pole != null); }
    }
}
