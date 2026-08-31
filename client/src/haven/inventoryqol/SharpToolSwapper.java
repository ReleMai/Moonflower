package haven.inventoryqol;

import haven.Coord;
import haven.Equipory;
import haven.GItem;
import haven.GameUI;
import haven.Inventory;
import haven.Loading;
import haven.Resource;
import haven.WItem;
import haven.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Identity-safe one-hand sharp-tool swap used around animal processing. */
final class SharpToolSwapper {
    private static final long STEP_TIMEOUT_MS = 3000;
    private final GameUI gui;

    SharpToolSwapper(GameUI gui) {
        this.gui = gui;
    }

    Session equipBest() throws InterruptedException {
        if(gui.vhand != null)
            return(Session.error(gui, "The cursor is occupied; sharp-tool switching was not started."));
        Equipory equipment = gui.getequipory();
        if(equipment == null)
            return(Session.error(gui, "Equipment is not available yet."));
        Candidate best = bestCandidate(equipment);
        if(best == null)
            return(Session.error(gui, "No supported sharp tool with a loaded quality was found in Inventory or the equipped Belt."));
        int equippedSlot = equippedSlot(equipment, best.item.item);
        if(equippedSlot >= 0)
            return(Session.unchanged(gui, best.displayName, best.quality));
        if(best.inventory == null || best.coordinate == null)
            return(Session.error(gui, "The best sharp tool did not have a safe inventory origin."));

        int handSlot = chooseHandSlot(equipment);
        if(handSlot < 0)
            return(Session.error(gui, "Both hands contain protected shields; the cutting tool was not equipped."));
        GItem displaced = equipment.slots[handSlot] == null ? null : equipment.slots[handSlot].item;
        ItemIdentity tool = new ItemIdentity(best.item.item);
        ItemIdentity prior = displaced == null ? null : new ItemIdentity(displaced);

        best.item.item.wdgmsg("take", center(best.item));
        if(!await(() -> gui.vhand != null && tool.matches(gui.vhand.item), STEP_TIMEOUT_MS))
            return(Session.error(gui, "The server did not place the selected sharp tool on the cursor."));

        equipment.wdgmsg("drop", handSlot);
        if(!await(() -> slotMatches(equipment, handSlot, tool), STEP_TIMEOUT_MS)) {
            returnHeld(best.inventory, best.coordinate);
            return(Session.error(gui, "The server did not acknowledge equipping the selected sharp tool."));
        }

        if(prior != null) {
            if(!await(() -> gui.vhand != null && prior.matches(gui.vhand.item), STEP_TIMEOUT_MS))
                return(Session.error(gui, "The displaced hand item could not be verified on the cursor."));
            best.inventory.wdgmsg("drop", best.coordinate);
            if(!await(() -> gui.vhand == null, STEP_TIMEOUT_MS))
                return(Session.error(gui, "The displaced hand item could not be parked in the sharp tool's original slot."));
        } else if(!await(() -> gui.vhand == null, STEP_TIMEOUT_MS)) {
            return(Session.error(gui, "The cursor did not clear after equipping the sharp tool."));
        }

        return(new Session(gui, equipment, best.inventory, best.coordinate, handSlot,
                tool, prior, best.displayName, best.quality, null, true));
    }

    private Candidate bestCandidate(Equipory equipment) {
        List<Candidate> candidates = new ArrayList<>();
        Set<GItem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        addInventory(candidates, seen, gui.maininv);
        for(int slot : new int[]{6, 7}) {
            WItem equipped = equipment.slots[slot];
            if(equipped != null)
                addCandidate(candidates, seen, equipped, null, null);
        }
        Set<GItem> equipmentItems = Collections.newSetFromMap(new IdentityHashMap<>());
        for(WItem equipped : equipment.slots) {
            if(equipped == null || equipped.item == null || !equipmentItems.add(equipped.item))
                continue;
            if(!isBelt(equipped.item))
                continue;
            addWidgetInventory(candidates, seen, equipped.item.contents);
            if(equipped.item.contentswnd != null)
                addWidgetInventory(candidates, seen, equipped.item.contentswnd.inv);
        }
        Candidate best = null;
        for(Candidate candidate : candidates) {
            if(best == null || candidate.quality > best.quality)
                best = candidate;
        }
        return(best);
    }

    private static void addWidgetInventory(List<Candidate> out, Set<GItem> seen, Widget widget) {
        Inventory inventory = Inventory.fromWidget(widget);
        if(inventory != null)
            addInventory(out, seen, inventory);
    }

    private static void addInventory(List<Candidate> out, Set<GItem> seen, Inventory inventory) {
        if(inventory == null)
            return;
        for(WItem item : inventory.getAllItems())
            addCandidate(out, seen, item, inventory, item.c.div(Inventory.sqsz));
    }

    private static void addCandidate(List<Candidate> out, Set<GItem> seen, WItem item,
                                     Inventory inventory, Coord coordinate) {
        if(item == null || item.item == null || !seen.add(item.item))
            return;
        String name = safeName(item.item);
        String resource = safeResource(item.item);
        if(!isSupportedSharpTool(name, resource))
            return;
        try {
            if(item.item.getQBuff() != null)
                out.add(new Candidate(item, inventory, coordinate, name, item.item.getQBuff().q));
        } catch(Loading ignored) {
        }
    }

    static boolean isSupportedSharpTool(String displayName, String resourceName) {
        String name = normalize(displayName);
        String base = normalize(basename(resourceName));
        String joined = name + " " + base;
        if(joined.contains("knife") || joined.contains("cleaver") || joined.contains("dagger"))
            return(true);
        if(joined.contains("stone axe") || joined.contains("stoneaxe") ||
                joined.contains("metal axe") || joined.contains("metalaxe") ||
                joined.contains("woodsman axe") || joined.contains("woodsmans axe") ||
                joined.contains("woodsmanaxe") || joined.contains("tinker throwing axe") ||
                joined.contains("tinkers throwing axe") || joined.contains("tinkersaxe"))
            return(true);
        return(joined.contains("bronze sword") || joined.contains("bronzesword") ||
                joined.contains("hirdsman sword") || joined.contains("hirdsmans sword") ||
                joined.contains("hirdsword"));
    }

    private static boolean isBelt(GItem item) {
        String text = normalize(safeName(item) + " " + safeResource(item) + " " +
                (item.contentsnm == null ? "" : item.contentsnm) + " " + item.contentsid);
        return(text.contains("belt") || text.contains("toolbelt"));
    }

    private static int equippedSlot(Equipory equipment, GItem item) {
        for(int slot : new int[]{6, 7})
            if(equipment.slots[slot] != null && equipment.slots[slot].item == item)
                return(slot);
        return(-1);
    }

    /** Select the cutting hand first, never evicting a shield to make room. */
    private static int chooseHandSlot(Equipory equipment) {
        WItem first = equipment.slots[6];
        WItem second = equipment.slots[7];
        return(chooseHandSlot(first != null, first != null && isShield(first.item),
                        first != null && isSupportedSharpTool(safeName(first.item), safeResource(first.item)),
                second != null, second != null && isShield(second.item),
                        second != null && isSupportedSharpTool(safeName(second.item), safeResource(second.item))));
    }

    static int chooseHandSlot(boolean occupied6, boolean shield6, boolean sharp6,
                              boolean occupied7, boolean shield7, boolean sharp7) {
        if(occupied6 && sharp6)
            return(6);
        if(occupied7 && sharp7)
            return(7);
        if(!occupied6)
            return(6);
        if(!occupied7)
            return(7);
        if(!shield6)
            return(6);
        if(!shield7)
            return(7);
        return(-1);
    }

    private static boolean isShield(GItem item) {
        String joined = normalize(safeName(item) + " " + safeResource(item));
        return(joined.contains("shield") || joined.contains("buckler") || joined.contains("targe"));
    }

    private void returnHeld(Inventory inventory, Coord coordinate) throws InterruptedException {
        if(gui.vhand == null || inventory == null || coordinate == null)
            return;
        inventory.wdgmsg("drop", coordinate);
        await(() -> gui.vhand == null, STEP_TIMEOUT_MS);
    }

    private static boolean slotMatches(Equipory equipment, int slot, ItemIdentity item) {
        return(equipment.slots[slot] != null && item.matches(equipment.slots[slot].item));
    }

    private static Coord center(WItem item) {
        return(Coord.of(Math.max(1, item.sz.x) / 2, Math.max(1, item.sz.y) / 2));
    }

    private static String safeName(GItem item) {
        try {
            String name = item.getname();
            return(name == null ? "" : name);
        } catch(RuntimeException ignored) {
            return("");
        }
    }

    private static String safeResource(GItem item) {
        try {
            Resource resource = item.getres();
            return(resource == null ? "" : resource.name);
        } catch(RuntimeException ignored) {
            return("");
        }
    }

    private static String basename(String resource) {
        if(resource == null)
            return("");
        int slash = resource.lastIndexOf('/');
        return(slash < 0 ? resource : resource.substring(slash + 1));
    }

    private static String normalize(String text) {
        return(text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'').replace("'", "").replace('-', ' ').trim());
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

    private static final class Candidate {
        final WItem item;
        final Inventory inventory;
        final Coord coordinate;
        final String displayName;
        final double quality;

        Candidate(WItem item, Inventory inventory, Coord coordinate,
                  String displayName, double quality) {
            this.item = item;
            this.inventory = inventory;
            this.coordinate = coordinate == null ? null : new Coord(coordinate);
            this.displayName = displayName;
            this.quality = quality;
        }
    }

    private static final class ItemIdentity {
        final GItem original;
        final String name;
        final String resource;

        ItemIdentity(GItem item) {
            original = item;
            name = safeName(item);
            resource = safeResource(item);
        }

        boolean matches(GItem item) {
            return(item != null && (item == original ||
                    (!resource.isEmpty() && resource.equals(safeResource(item)) &&
                            normalize(name).equals(normalize(safeName(item))))));
        }
    }

    static final class Session implements AutoCloseable {
        private final GameUI gui;
        private final Equipory equipment;
        private final Inventory origin;
        private final Coord coordinate;
        private final int handSlot;
        private final ItemIdentity tool;
        private final ItemIdentity prior;
        final String toolName;
        final double quality;
        final String error;
        private final boolean changed;
        private boolean closed;

        private Session(GameUI gui, Equipory equipment, Inventory origin, Coord coordinate,
                        int handSlot, ItemIdentity tool, ItemIdentity prior, String toolName,
                        double quality, String error, boolean changed) {
            this.gui = gui;
            this.equipment = equipment;
            this.origin = origin;
            this.coordinate = coordinate;
            this.handSlot = handSlot;
            this.tool = tool;
            this.prior = prior;
            this.toolName = toolName;
            this.quality = quality;
            this.error = error;
            this.changed = changed;
        }

        static Session error(GameUI gui, String error) {
            return(new Session(gui, null, null, null, -1, null, null,
                    "", 0, error, false));
        }

        static Session unchanged(GameUI gui, String name, double quality) {
            return(new Session(gui, null, null, null, -1, null, null,
                    name, quality, null, false));
        }

        boolean success() { return(error == null); }

        @Override
        public void close() {
            if(closed || !changed) {
                closed = true;
                return;
            }
            closed = true;
            try {
                if(gui.vhand != null)
                    throw new IllegalStateException("cursor is occupied");
                if(prior == null) {
                    if(!slotMatches(equipment, handSlot, tool))
                        throw new IllegalStateException("equipped sharp tool changed");
                    equipment.slots[handSlot].item.wdgmsg("take", Coord.z);
                    if(!await(() -> gui.vhand != null && tool.matches(gui.vhand.item), STEP_TIMEOUT_MS))
                        throw new IllegalStateException("sharp tool was not taken for restoration");
                    origin.wdgmsg("drop", coordinate);
                    if(!await(() -> gui.vhand == null, STEP_TIMEOUT_MS))
                        throw new IllegalStateException("sharp tool did not return to its origin");
                    return;
                }
                WItem parked = null;
                for(WItem item : origin.getAllItems()) {
                    if(prior.matches(item.item)) {
                        parked = item;
                        break;
                    }
                }
                if(parked == null)
                    throw new IllegalStateException("displaced hand item is no longer in the tool origin");
                parked.item.wdgmsg("take", center(parked));
                if(!await(() -> gui.vhand != null && prior.matches(gui.vhand.item), STEP_TIMEOUT_MS))
                    throw new IllegalStateException("displaced hand item was not taken");
                equipment.wdgmsg("drop", handSlot);
                if(!await(() -> slotMatches(equipment, handSlot, prior) &&
                        gui.vhand != null && tool.matches(gui.vhand.item), STEP_TIMEOUT_MS))
                    throw new IllegalStateException("original hand item was not restored");
                origin.wdgmsg("drop", coordinate);
                if(!await(() -> gui.vhand == null, STEP_TIMEOUT_MS))
                    throw new IllegalStateException("sharp tool did not return to its origin");
            } catch(InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                gui.error("Sharp-tool restoration was interrupted; check the cursor and hand slots.");
            } catch(RuntimeException failure) {
                gui.error("Could not restore the previous hand item: " + failure.getMessage() + ".");
            }
        }
    }
}
