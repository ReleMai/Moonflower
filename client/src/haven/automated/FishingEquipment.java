package haven.automated;

import haven.Coord;
import haven.Equipory;
import haven.GItem;
import haven.GameUI;
import haven.Inventory;
import haven.ItemInfo;
import haven.Loading;
import haven.Resource;
import haven.WItem;
import haven.Widget;
import haven.automated.helpers.FishingAtlas;
import haven.res.ui.stackinv.ItemStack;
import haven.res.ui.tt.q.qbuff.QBuff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Performs verified fishing-equipment moves while keeping displaced hand items recoverable. */
final class FishingEquipment {
    private static final long ACTION_TIMEOUT_MS = 3500;
    private static final long ATTACH_RETRY_MS = 450;
    private static final long EQUIP_RETRY_MS = 450;
    private static final int LEFT_HAND_SLOT = 6;
    private static final int RIGHT_HAND_SLOT = 7;
    private static final int HAND_SLOT_MASK = (1 << LEFT_HAND_SLOT) | (1 << RIGHT_HAND_SLOT);

    private final GameUI gui;
    private final List<SavedHandItem> displaced = new ArrayList<>();

    FishingEquipment(GameUI gui) {
        this.gui = gui;
    }

    synchronized Result prepare(String poleName, List<String> linePriority, List<String> hookPriority,
                                List<String> consumablePriority, boolean lure) throws InterruptedException {
        if(!recoverFishingCursor())
            return(Result.error("Clear the cursor or make inventory room before preparing the fishing pole."));
        WItem pole = findPoleAnywhere(poleName, null);
        if(pole == null)
            return(Result.error("No reachable " + poleName + " was found in inventory, belt, or hands."));

        WItem equipped = findPoleInHands(poleName);
        if(equipped == null) {
            equipped = ensurePoleEquipped(poleName, pole);
            if(equipped == null)
                return(Result.error("The " + poleName +
                        " could not be placed in an available hand slot. Clear a hand and try again."));
        }
        pole = equipped;

        FishingAtlas.Part consumablePart = lure ? FishingAtlas.Part.LURE : FishingAtlas.Part.BAIT;
        for(int attempt = 0; attempt < 6; attempt++) {
            PoleState state = inspect(pole);
            if(state.loading)
                return(Result.waiting("Waiting for fishing-pole contents."));
            if(!state.unknown.isEmpty())
                return(Result.error("Unknown fishing-pole contents: " + String.join(", ", state.unknown)));
            if(state.line == null) {
                if(!attach(pole, FishingAtlas.Part.LINE, linePriority))
                    return(Result.error("No selected fishline from inventory or an equipped Creel could be attached."));
            } else if(state.hook == null) {
                if(!attach(pole, FishingAtlas.Part.HOOK, hookPriority))
                    return(Result.error("No selected hook from inventory or an equipped Creel could be attached."));
            } else if(state.consumable(consumablePart) == null) {
                if(!attach(pole, consumablePart, consumablePriority))
                    return(Result.error("No selected " + (lure ? "lure" : "bait") +
                            " from inventory or an equipped Creel could be attached."));
            } else {
                return(Result.ready(new Snapshot(describe(pole), state.line, state.hook,
                        state.consumable(consumablePart), lure ? "lure" : "bait")));
            }
            pole = findPoleInHands(poleName);
            if(pole == null)
                return(Result.error("Equipped fishing pole disappeared while tackle was being attached."));
        }
        return(Result.error("Fishing pole did not reach a ready state."));
    }

    private boolean recoverFishingCursor() throws InterruptedException {
        if(gui.vhand == null)
            return(true);
        FishingAtlas.Part held = FishingAtlas.classify(safeName(gui.vhand), safeResource(gui.vhand));
        if(held != FishingAtlas.Part.POLE && held != FishingAtlas.Part.LINE &&
                held != FishingAtlas.Part.HOOK && held != FishingAtlas.Part.BAIT &&
                held != FishingAtlas.Part.LURE)
            return(false);
        Coord room = roomFor(gui.maininv, gui.vhand);
        if(room == null)
            return(false);
        gui.maininv.wdgmsg("drop", room);
        return(waitFor(() -> gui.vhand == null));
    }

    synchronized boolean restoreDisplacedHands() throws InterruptedException {
        if(displaced.isEmpty())
            return(true);
        Equipory equipory = gui.getequipory();
        if(equipory == null || gui.maininv == null)
            return(false);

        for(int slot : new int[]{LEFT_HAND_SLOT, RIGHT_HAND_SLOT}) {
            WItem item = equipory.slots[slot];
            if(item != null && FishingAtlas.classify(safeName(item), safeResource(item)) == FishingAtlas.Part.POLE) {
                Coord room = roomFor(gui.maininv, item);
                if(room == null)
                    return(false);
                GItem moving = item.item;
                moving.wdgmsg("transfer", Coord.z);
                if(!waitFor(() -> equipory.slots[slot] == null || equipory.slots[slot].item != moving))
                    return(false);
            }
        }

        boolean restored = true;
        for(SavedHandItem saved : new ArrayList<>(displaced)) {
            if(equipory.slots[saved.slot] != null) {
                restored = false;
                continue;
            }
            WItem candidate = findSavedItem(saved);
            int allowedHands = allowedHandSlots(candidate);
            int restoreSlot = (allowedHands & (1 << saved.slot)) != 0 ? saved.slot :
                    selectEmptyHand(equipory, allowedHands);
            if(candidate == null || restoreSlot < 0 || !equip(candidate, restoreSlot, allowedHands)) {
                restored = false;
                continue;
            }
            displaced.remove(saved);
        }
        return(restored);
    }

    private WItem ensurePoleEquipped(String poleName, WItem candidate) throws InterruptedException {
        Equipory equipory = gui.getequipory();
        if(equipory == null || gui.maininv == null || poleName == null || poleName.isBlank())
            return(null);
        WItem equipped = findPoleInHands(poleName);
        if(equipped != null)
            return(equipped);

        if(candidate == null || candidate.item == null || !poleName.equals(safeName(candidate)))
            candidate = findPoleAnywhere(poleName, null);
        if(candidate == null)
            return(null);

        int allowedHands = allowedHandSlots(candidate);
        int targetSlot = selectEmptyHand(equipory, allowedHands);
        if(targetSlot < 0) {
            for(int slot : new int[]{LEFT_HAND_SLOT, RIGHT_HAND_SLOT}) {
                if((allowedHands & (1 << slot)) == 0)
                    continue;
                WItem hand = equipory.slots[slot];
                if(hand != null && !isBucket(hand)) {
                    targetSlot = slot;
                    break;
                }
            }
            if(targetSlot < 0 || !displace(equipory.slots[targetSlot], targetSlot))
                return(null);
        }

        if(!equip(candidate, targetSlot, allowedHands))
            return(null);
        return(findPoleInHands(poleName));
    }

    private static int allowedHandSlots(WItem item) {
        try {
            Equipory.SlotInfo slots = item == null || item.item == null ? null :
                    ItemInfo.find(Equipory.SlotInfo.class, item.item.info());
            int hands = slots == null ? 0 : slots.slots() & HAND_SLOT_MASK;
            return(hands == 0 ? HAND_SLOT_MASK : hands);
        } catch(Loading loading) {
            return(HAND_SLOT_MASK);
        }
    }

    private static int selectEmptyHand(Equipory equipory, int allowedHands) {
        for(int slot : new int[]{LEFT_HAND_SLOT, RIGHT_HAND_SLOT}) {
            if((allowedHands & (1 << slot)) != 0 && equipory.slots[slot] == null)
                return(slot);
        }
        return(-1);
    }

    private static boolean isBucket(WItem item) {
        String name = safeName(item).toLowerCase(java.util.Locale.ROOT);
        String resource = safeResource(item).toLowerCase(java.util.Locale.ROOT);
        return(name.contains("bucket") || resource.contains("bucket"));
    }

    private boolean displace(WItem item, int slot) throws InterruptedException {
        Coord room = roomFor(gui.maininv, item);
        if(room == null)
            return(false);
        ItemData data = describe(item);
        GItem moving = item.item;
        moving.wdgmsg("transfer", Coord.z);
        if(!waitFor(() -> {
            Equipory equipory = gui.getequipory();
            return(equipory == null || equipory.slots[slot] == null || equipory.slots[slot].item != moving);
        }))
            return(false);
        displaced.add(new SavedHandItem(slot, data));
        return(true);
    }

    private boolean equip(WItem candidate, int slot, int allowedHands) throws InterruptedException {
        Widget source = candidate.parent;
        Coord sourceCoordinate = candidate.c.div(Inventory.sqsz);
        ItemData expected = describe(candidate);
        GItem moving = candidate.item;
        moving.wdgmsg("take", Coord.z);
        if(!waitFor(() -> gui.vhand != null && gui.vhand.item == moving))
            return(false);
        Equipory equipory = gui.getequipory();
        if(equipory == null) {
            returnCursor(source, sourceCoordinate);
            return(false);
        }
        boolean equipped = placeHeldItemInHand(moving, expected, slot, allowedHands);
        if(!equipped)
            returnCursor(source, sourceCoordinate);
        return(equipped);
    }

    /** Performs the same equipment-window drop as placing the held rod on a hand slot. */
    private boolean placeHeldItemInHand(GItem moving, ItemData expected, int preferredSlot,
                                        int allowedHands) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ACTION_TIMEOUT_MS;
        long nextDrop = 0;
        int targetSlot = preferredSlot;
        while(System.currentTimeMillis() < deadline) {
            Equipory equipory = gui.getequipory();
            if(equipory == null)
                return(false);
            if(findEquipped(expected, moving) != null)
                return(true);
            if(gui.vhand != null && gui.vhand.item == moving) {
                if((allowedHands & (1 << targetSlot)) == 0 || equipory.slots[targetSlot] != null) {
                    int alternate = selectEmptyHand(equipory, allowedHands);
                    if(alternate >= 0)
                        targetSlot = alternate;
                }
                long now = System.currentTimeMillis();
                if(equipory.slots[targetSlot] == null && now >= nextDrop) {
                    equipory.wdgmsg("drop", targetSlot);
                    nextDrop = now + EQUIP_RETRY_MS;
                }
            }
            Thread.sleep(50);
        }
        return(findEquipped(expected, moving) != null);
    }

    private WItem findEquipped(ItemData expected, GItem original) {
        Equipory equipory = gui.getequipory();
        if(equipory == null)
            return(null);
        Set<GItem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for(int slot : new int[]{LEFT_HAND_SLOT, RIGHT_HAND_SLOT}) {
            WItem item = equipory.slots[slot];
            if(item == null || item.item == null || !seen.add(item.item))
                continue;
            if(item.item == original)
                return(item);
            ItemData actual = describe(item);
            if(!expected.resourceName.isEmpty() && expected.resourceName.equals(actual.resourceName) ||
                    expected.resourceName.isEmpty() && expected.displayName.equals(actual.displayName))
                return(item);
        }
        return(null);
    }

    private boolean attach(WItem pole, FishingAtlas.Part part, List<String> priority)
            throws InterruptedException {
        WItem candidate = candidates(part, priority).stream().findFirst().orElse(null);
        if(candidate == null)
            return(false);
        Widget source = candidate.parent;
        Coord sourceCoordinate = candidate.c.div(Inventory.sqsz);
        GItem moving = candidate.item;
        moving.wdgmsg("take", Coord.z);
        if(!waitFor(() -> gui.vhand != null && gui.vhand.item == moving))
            return(false);
        String poleName = safeName(pole);
        boolean attached = attachHeldItemToPole(poleName, part, moving);
        if(!attached)
            returnCursor(source, sourceCoordinate);
        return(attached);
    }

    /**
     * Mirrors the previous helper's proven interaction: take one tackle item, then send a
     * zero-modifier item interaction directly to the freshly resolved equipped pole.
     */
    private boolean attachHeldItemToPole(String poleName, FishingAtlas.Part part, GItem moving)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + ACTION_TIMEOUT_MS;
        long nextRightClick = 0;
        while(System.currentTimeMillis() < deadline) {
            WItem currentPole = findPoleInHands(poleName);
            if(currentPole != null) {
                PoleState state = inspect(currentPole);
                if(!state.loading && state.has(part))
                    return(true);
                long now = System.currentTimeMillis();
                if(gui.vhand != null && gui.vhand.item == moving && now >= nextRightClick) {
                    currentPole.item.wdgmsg("itemact", 0);
                    nextRightClick = now + ATTACH_RETRY_MS;
                }
            }
            Thread.sleep(50);
        }
        WItem currentPole = findPoleInHands(poleName);
        if(currentPole == null)
            return(false);
        PoleState state = inspect(currentPole);
        return(!state.loading && state.has(part));
    }

    private void returnCursor(Widget source, Coord sourceCoordinate) throws InterruptedException {
        if(gui.vhand == null)
            return;
        if(source instanceof ItemStack) {
            source.wdgmsg("drop");
            if(waitFor(() -> gui.vhand == null))
                return;
        }
        Inventory sourceInventory = Inventory.fromWidget(source);
        Inventory target = sourceInventory == null ? gui.maininv : sourceInventory;
        if(target == null)
            return;
        Coord coordinate = sourceCoordinate;
        if(coordinate == null || sourceInventory == null)
            coordinate = roomFor(target, gui.vhand);
        if(coordinate == null)
            return;
        target.wdgmsg("drop", coordinate);
        if(!waitFor(() -> gui.vhand == null)) {
            Coord fallback = roomFor(target, gui.vhand);
            if(fallback != null && !fallback.equals(coordinate)) {
                target.wdgmsg("drop", fallback);
                waitFor(() -> gui.vhand == null);
            }
        }
    }

    private List<WItem> candidates(FishingAtlas.Part part, List<String> priority) {
        List<WItem> candidates = new ArrayList<>();
        List<String> ordered = priority == null ? List.of() : priority;
        if(ordered.isEmpty())
            return(candidates);
        for(WItem item : FishingInventory.equipmentItems(gui)) {
            String name = safeName(item);
            if(item != null && item.item != null && FishingAtlas.classify(name, safeResource(item)) == part &&
                    !FishingInventory.insideFishingPole(item) &&
                    (ordered.isEmpty() || ordered.contains(name)))
                candidates.add(item);
        }
        candidates.sort(Comparator
                .comparingInt((WItem item) -> preferenceIndex(ordered, safeName(item)))
                .thenComparing((WItem item) -> quality(item), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FishingEquipment::safeResource));
        return(candidates);
    }

    private WItem findSavedItem(SavedHandItem saved) {
        return(FishingInventory.equipmentItems(gui).stream()
                .filter(item -> saved.data.resourceName.equals(safeResource(item)))
                .min(Comparator.comparingDouble(item -> qualityDistance(quality(item), saved.data.quality)))
                .orElse(null));
    }

    private WItem findPoleInHands(String poleName) {
        Equipory equipory = gui.getequipory();
        if(equipory == null)
            return(null);
        for(int slot : new int[]{LEFT_HAND_SLOT, RIGHT_HAND_SLOT}) {
            WItem item = equipory.slots[slot];
            if(item != null && poleName.equals(safeName(item)))
                return(item);
        }
        return(null);
    }

    private WItem findPoleAnywhere(String poleName, GItem preferred) {
        WItem equipped = findPoleInHands(poleName);
        if(equipped != null && (preferred == null || equipped.item == preferred))
            return(equipped);
        WItem fallback = equipped;
        for(WItem item : FishingInventory.equipmentItems(gui)) {
            if(item == null || item.item == null || FishingInventory.insideFishingPole(item) ||
                    !poleName.equals(safeName(item)) ||
                    FishingAtlas.classify(safeName(item), safeResource(item)) != FishingAtlas.Part.POLE)
                continue;
            if(item.item == preferred)
                return(item);
            if(fallback == null)
                fallback = item;
        }
        return(fallback);
    }

    private static PoleState inspect(WItem pole) {
        PoleState state = new PoleState();
        if(pole == null || pole.item == null) {
            state.loading = true;
            return(state);
        }
        try {
            for(ItemInfo info : pole.item.info()) {
                if(!(info instanceof ItemInfo.Contents))
                    continue;
                ItemData item = describe((ItemInfo.Contents)info);
                if(item.displayName.isEmpty())
                    continue;
                state.add(item);
            }
            addPoleContents(state, pole.item.contents,
                    Collections.newSetFromMap(new IdentityHashMap<>()));
        } catch(Loading e) {
            state.loading = true;
        }
        return(state);
    }

    private static void addPoleContents(PoleState state, Widget widget, Set<Widget> seen) {
        if(widget == null || !seen.add(widget))
            return;
        Inventory inventory = Inventory.fromWidget(widget);
        if(inventory != null) {
            for(WItem item : inventory.getAllItems())
                state.add(describe(item));
        } else if(widget instanceof ItemStack) {
            for(WItem item : ((ItemStack)widget).wmap.values())
                state.add(describe(item));
        } else if(widget instanceof WItem) {
            state.add(describe((WItem)widget));
        } else if(widget instanceof GItem) {
            state.add(describe((GItem)widget));
        }
        for(Widget child : widget.children())
            addPoleContents(state, child, seen);
    }

    private static ItemData describe(ItemInfo.Contents contents) {
        String name = "";
        Double quality = null;
        for(ItemInfo info : contents.sub) {
            if(info instanceof ItemInfo.Name)
                name = ((ItemInfo.Name)info).str.text;
            if(info instanceof QBuff)
                quality = ((QBuff)info).q;
        }
        // Sub-info owners resolve to the pole itself, not the contained tackle;
        // leave the resource empty rather than persist a confidently wrong value.
        return(new ItemData("", name, quality));
    }

    static ItemData describe(WItem item) {
        return(item == null ? ItemData.EMPTY : describe(item.item));
    }

    static ItemData describe(GItem item) {
        if(item == null)
            return(ItemData.EMPTY);
        String resource = "";
        String name = "";
        try {
            Resource res = item.getres();
            resource = res == null ? "" : res.name;
        } catch(Loading ignored) {
        }
        try {
            name = item.getname();
            if("it's null".equals(name) || "exception".equals(name))
                name = "";
        } catch(RuntimeException ignored) {
        }
        return(new ItemData(resource, name, quality(item)));
    }

    private static String safeName(WItem item) {
        return(describe(item).displayName);
    }

    private static String safeResource(WItem item) {
        return(describe(item).resourceName);
    }

    private static Double quality(WItem item) {
        return(item == null ? null : quality(item.item));
    }

    private static Double quality(GItem item) {
        if(item == null)
            return(null);
        try {
            QBuff quality = item.getQBuff();
            return(quality == null ? null : quality.q);
        } catch(RuntimeException ignored) {
            return(null);
        }
    }

    private static Coord roomFor(Inventory inventory, WItem item) {
        if(inventory == null || item == null)
            return(null);
        int width = Math.max(1, (item.sz.x + Inventory.sqsz.x - 1) / Inventory.sqsz.x);
        int height = Math.max(1, (item.sz.y + Inventory.sqsz.y - 1) / Inventory.sqsz.y);
        return(inventory.isRoom(width, height));
    }

    private static int preferenceIndex(List<String> priority, String name) {
        int index = priority.indexOf(name);
        return(index < 0 ? Integer.MAX_VALUE : index);
    }

    private static double qualityDistance(Double first, Double second) {
        if(first == null || second == null)
            return(first == second ? 0 : Double.MAX_VALUE / 2);
        return(Math.abs(first - second));
    }

    private static boolean waitFor(Check check) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ACTION_TIMEOUT_MS;
        while(System.currentTimeMillis() < deadline) {
            if(check.ready())
                return(true);
            Thread.sleep(50);
        }
        return(check.ready());
    }

    private interface Check {
        boolean ready();
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

    private static final class PoleState {
        ItemData line;
        ItemData hook;
        ItemData bait;
        ItemData lure;
        boolean loading;
        final Set<String> unknown = new HashSet<>();

        void add(ItemData item) {
            if(item == null || item.displayName.isEmpty())
                return;
            switch(FishingAtlas.classify(item.displayName, item.resourceName)) {
            case LINE: line = item; break;
            case HOOK: hook = item; break;
            case BAIT: bait = item; break;
            case LURE: lure = item; break;
            default: unknown.add(item.displayName);
            }
        }

        ItemData consumable(FishingAtlas.Part part) {
            return(part == FishingAtlas.Part.LURE ? lure : bait);
        }

        boolean has(FishingAtlas.Part part) {
            switch(part) {
            case LINE: return(line != null);
            case HOOK: return(hook != null);
            case BAIT: return(bait != null);
            case LURE: return(lure != null);
            default: return(false);
            }
        }
    }

    private static final class SavedHandItem {
        final int slot;
        final ItemData data;

        SavedHandItem(int slot, ItemData data) {
            this.slot = slot;
            this.data = data;
        }
    }
}
