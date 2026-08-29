package haven.automated;

import haven.Coord;
import haven.Equipory;
import haven.GItem;
import haven.GameUI;
import haven.Inventory;
import haven.WItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Equips the selected pole and keeps displaced hand items recoverable in the main inventory. */
final class FishingHandManager {
    private static final int LEFT_HAND = 6;
    private static final int RIGHT_HAND = 7;
    private static final long MOVE_TIMEOUT_MS = 2500;

    private final GameUI gui;
    private final FishingItemLocator items;
    private final List<Displaced> displaced = new ArrayList<>();
    private Origin poleOrigin;
    private GItem equippedPole;

    FishingHandManager(GameUI gui, FishingItemLocator items) {
        this.gui = gui;
        this.items = items;
    }

    String equip(String poleName, WItem pole) throws InterruptedException {
        if(pole == null || pole.item == null)
            return("The selected pole disappeared before it could be equipped.");
        if(items.findPoleInHands(poleName, pole.item) != null)
            return(null);
        if(gui.vhand != null)
            return("Clear the cursor before the helper equips the selected pole.");
        Equipory equipory = gui.getequipory();
        if(equipory == null || gui.maininv == null)
            return("The equipment window or main inventory is unavailable.");

        poleOrigin = new Origin(pole);
        equippedPole = pole.item;
        Set<GItem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for(int slot : new int[]{LEFT_HAND, RIGHT_HAND}) {
            WItem hand = equipory.slots[slot];
            if(hand == null || hand.item == null || hand.item == pole.item || !seen.add(hand.item))
                continue;
            Coord room = roomFor(gui.maininv, hand);
            if(room == null)
                return(rollback("The main inventory has no room for the item in hand slot " + slot + "."));
            GItem moved = hand.item;
            moved.wdgmsg("take", hand.sz.div(2));
            if(!await(() -> gui.vhand != null && gui.vhand.item == moved, MOVE_TIMEOUT_MS))
                return(rollback("The item in hand slot " + slot + " could not be picked up."));
            gui.maininv.wdgmsg("drop", room);
            if(!await(() -> gui.vhand == null, MOVE_TIMEOUT_MS))
                return(rollback("The item from hand slot " + slot + " could not be stored safely."));
            displaced.add(new Displaced(moved, slot));
        }

        WItem currentPole = items.findPole(poleName, equippedPole);
        if(currentPole == null)
            return(rollback("The selected pole disappeared after clearing the hand slots."));
        currentPole.item.wdgmsg("take", currentPole.sz.div(2));
        if(!await(() -> gui.vhand != null && poleMatches(poleName), MOVE_TIMEOUT_MS))
            return(rollback("The selected pole was not placed on the cursor."));
        equippedPole = gui.vhand.item;
        equipory.wdgmsg("drop", LEFT_HAND);
        if(!await(() -> gui.vhand == null && items.findPoleInHands(poleName, equippedPole) != null,
                MOVE_TIMEOUT_MS))
            return(rollback("The selected pole could not be equipped in the hand slots."));
        return(null);
    }

    boolean restore() throws InterruptedException {
        boolean restored = true;
        Equipory equipory = gui.getequipory();
        if(equipory == null)
            return(displaced.isEmpty());

        if(gui.vhand != null && gui.vhand.item == equippedPole) {
            Inventory destination = poleOrigin == null || poleOrigin.inventory == null ?
                    gui.maininv : poleOrigin.inventory;
            Coord coordinate = poleOrigin == null ? null : poleOrigin.coordinate;
            Coord room = coordinate == null ? roomFor(destination, gui.vhand) : coordinate;
            if(destination != null && room != null) {
                destination.wdgmsg("drop", room);
                restored &= await(() -> gui.vhand == null, MOVE_TIMEOUT_MS);
            } else {
                restored = false;
            }
        }

        WItem pole = findEquipped(equippedPole);
        if(pole != null && !displaced.isEmpty()) {
            pole.item.wdgmsg("take", pole.sz.div(2));
            if(await(() -> gui.vhand != null, MOVE_TIMEOUT_MS)) {
                Inventory destination = poleOrigin == null || poleOrigin.inventory == null ?
                        gui.maininv : poleOrigin.inventory;
                Coord coordinate = poleOrigin == null ? null : poleOrigin.coordinate;
                Coord room = coordinate == null ? roomFor(destination, gui.vhand) : coordinate;
                if(destination != null && room != null) {
                    destination.wdgmsg("drop", room);
                    restored &= await(() -> gui.vhand == null, MOVE_TIMEOUT_MS);
                } else {
                    restored = false;
                }
            } else {
                restored = false;
            }
        }

        for(Displaced original : new ArrayList<>(displaced)) {
            if(gui.vhand != null) {
                restored = false;
                break;
            }
            WItem stored = findReachable(original.item);
            if(stored == null) {
                restored = false;
                continue;
            }
            stored.item.wdgmsg("take", stored.sz.div(2));
            if(!await(() -> gui.vhand != null && gui.vhand.item == original.item, MOVE_TIMEOUT_MS)) {
                restored = false;
                continue;
            }
            equipory.wdgmsg("drop", original.slot);
            if(!await(() -> gui.vhand == null, MOVE_TIMEOUT_MS)) {
                restored = false;
                break;
            }
            displaced.remove(original);
        }
        if(displaced.isEmpty()) {
            poleOrigin = null;
            equippedPole = null;
        }
        return(restored && displaced.isEmpty());
    }

    private String rollback(String message) throws InterruptedException {
        restore();
        return(message);
    }

    private boolean poleMatches(String poleName) {
        return(gui.vhand != null && gui.vhand.item != null &&
                FishingItemMetadata.name(gui.vhand).equals(poleName));
    }

    private WItem findEquipped(GItem item) {
        Equipory equipory = gui.getequipory();
        if(equipory == null)
            return(null);
        for(int slot : new int[]{LEFT_HAND, RIGHT_HAND}) {
            WItem candidate = equipory.slots[slot];
            if(candidate != null && candidate.item == item)
                return(candidate);
        }
        return(null);
    }

    private WItem findReachable(GItem item) {
        for(WItem candidate : FishingInventory.equipmentItems(gui)) {
            if(candidate != null && candidate.item == item)
                return(candidate);
        }
        return(null);
    }

    private static Coord roomFor(Inventory inventory, WItem item) {
        if(inventory == null || item == null)
            return(null);
        int width = Math.max(1, (item.sz.x + Inventory.sqsz.x - 1) / Inventory.sqsz.x);
        int height = Math.max(1, (item.sz.y + Inventory.sqsz.y - 1) / Inventory.sqsz.y);
        return(inventory.isRoom(width, height));
    }

    private static boolean await(Check condition, long timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout;
        while(System.currentTimeMillis() < deadline) {
            if(condition.ready())
                return(true);
            Thread.sleep(25);
        }
        return(condition.ready());
    }

    private interface Check {
        boolean ready();
    }

    private static final class Origin {
        final Inventory inventory;
        final Coord coordinate;

        Origin(WItem item) {
            inventory = Inventory.fromWidget(item.parent);
            coordinate = inventory == null ? null : item.c.div(Inventory.sqsz);
        }
    }

    private static final class Displaced {
        final GItem item;
        final int slot;

        Displaced(GItem item, int slot) {
            this.item = item;
            this.slot = slot;
        }
    }
}
