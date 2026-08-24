package haven.automated;

import haven.Equipory;
import haven.GItem;
import haven.GameUI;
import haven.Inventory;
import haven.Resource;
import haven.WItem;
import haven.Widget;
import haven.automated.helpers.FishingAtlas;
import haven.res.ui.stackinv.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Finds fishing-accessible items, including contents of equipped Creels even when hidden. */
final class FishingInventory {
    private FishingInventory() {
    }

    static List<WItem> equipmentItems(GameUI gui) {
        Collector collector = new Collector();
        if(gui == null)
            return(collector.items);
        if(gui.maininv != null)
            collector.addInventory(gui.maininv, true);
        addEquippedFishingContainers(gui, collector, true);
        addOpenContentsWindows(gui, collector, true);
        return(collector.items);
    }

    static List<WItem> catchItems(GameUI gui) {
        Collector collector = new Collector();
        if(gui == null)
            return(collector.items);
        if(gui.maininv != null)
            collector.addCatchInventory(gui.maininv);
        addEquippedFishingContainers(gui, collector, false);
        addOpenContentsWindows(gui, collector, false);
        return(collector.items);
    }

    static EnumMap<FishingAtlas.Part, List<String>> fishingChoices(GameUI gui) {
        EnumMap<FishingAtlas.Part, Set<String>> names = new EnumMap<>(FishingAtlas.Part.class);
        for(FishingAtlas.Part part : new FishingAtlas.Part[]{FishingAtlas.Part.POLE, FishingAtlas.Part.LINE,
                FishingAtlas.Part.HOOK, FishingAtlas.Part.BAIT, FishingAtlas.Part.LURE})
            names.put(part, new LinkedHashSet<>());
        for(WItem item : equipmentItems(gui)) {
            String name = item == null || item.item == null ? "" :
                    FishingAtlas.displayName(safeName(item.item));
            FishingAtlas.Part part = FishingAtlas.classify(name,
                    item == null || item.item == null ? "" : safeResource(item.item));
            if(names.containsKey(part) && !name.isBlank())
                names.get(part).add(name);
        }
        // Keep a pole or tackle choice stable during the short cursor-held phase.
        if(gui != null && gui.vhand != null && gui.vhand.item != null) {
            String name = FishingAtlas.displayName(safeName(gui.vhand.item));
            FishingAtlas.Part part = FishingAtlas.classify(name, safeResource(gui.vhand.item));
            if(names.containsKey(part) && !name.isBlank())
                names.get(part).add(name);
        }
        EnumMap<FishingAtlas.Part, List<String>> choices = new EnumMap<>(FishingAtlas.Part.class);
        for(java.util.Map.Entry<FishingAtlas.Part, Set<String>> entry : names.entrySet()) {
            List<String> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            choices.put(entry.getKey(), sorted);
        }
        return(choices);
    }

    static boolean insideFishingPole(WItem item) {
        for(Widget parent = item == null ? null : item.parent; parent != null; parent = parent.parent) {
            if(parent instanceof GItem) {
                GItem owner = (GItem)parent;
                if(FishingAtlas.classify(safeName(owner), safeResource(owner)) == FishingAtlas.Part.POLE)
                    return(true);
            }
        }
        return(false);
    }

    private static void addEquippedFishingContainers(GameUI gui, Collector collector, boolean includeBelt) {
        Equipory equipory = gui.getequipory();
        if(equipory == null)
            return;
        Set<GItem> seenEquipment = Collections.newSetFromMap(new IdentityHashMap<>());
        for(WItem equipped : equipory.slots) {
            if(equipped == null || equipped.item == null || !seenEquipment.add(equipped.item))
                continue;
            if(includeBelt && FishingAtlas.classify(safeName(equipped.item),
                    safeResource(equipped.item)) == FishingAtlas.Part.POLE)
                collector.addItem(equipped, false);
            boolean creel = FishingAtlas.isCreel(safeName(equipped.item), safeResource(equipped.item)) ||
                    FishingAtlas.isCreel(equipped.item.contentsnm, safeResource(equipped.item));
            if(includeBelt && FishingAtlas.classify(safeName(equipped.item),
                    safeResource(equipped.item)) == FishingAtlas.Part.POLE)
                collector.addWidget(equipped.item.contents, true);
            if(creel || includeBelt && isBelt(equipped.item))
                collector.addWidget(equipped.item.contents, true);
        }
    }

    /** Include already-open stack and container views without opening or moving any user UI. */
    private static void addOpenContentsWindows(GameUI gui, Collector collector, boolean recurse) {
        for(Widget widget = gui.lchild; widget != null; widget = widget.prev) {
            if(widget instanceof GItem.ContentsWindow)
                collector.addWidget(((GItem.ContentsWindow)widget).inv, recurse);
        }
    }

    private static boolean isBelt(GItem item) {
        String name = safeName(item).toLowerCase(java.util.Locale.ROOT);
        String resource = safeResource(item).toLowerCase(java.util.Locale.ROOT);
        String contents = item.contentsnm == null ? "" : item.contentsnm.toLowerCase(java.util.Locale.ROOT);
        String id = String.valueOf(item.contentsid).toLowerCase(java.util.Locale.ROOT);
        return(name.contains("belt") || resource.contains("belt") || contents.contains("belt") ||
                id.contains("toolbelt"));
    }

    private static String safeName(GItem item) {
        try {
            String name = item.getname();
            return(name == null ? "" : name);
        } catch(RuntimeException e) {
            return("");
        }
    }

    private static String safeResource(GItem item) {
        try {
            Resource resource = item.getres();
            return(resource == null ? "" : resource.name);
        } catch(RuntimeException e) {
            return("");
        }
    }

    private static final class Collector {
        final List<WItem> items = new ArrayList<>();
        final Set<GItem> seenItems = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Widget> seenWidgets = Collections.newSetFromMap(new IdentityHashMap<>());

        void addInventory(Inventory inventory, boolean recurse) {
            if(inventory == null || !seenWidgets.add(inventory))
                return;
            for(WItem item : inventory.getAllItems())
                addItem(item, recurse);
        }

        void addCatchInventory(Inventory inventory) {
            if(inventory == null || !seenWidgets.add(inventory))
                return;
            for(WItem item : inventory.getAllItems()) {
                if(item != null && item.item != null && item.item.contents instanceof ItemStack)
                    addWidget(item.item.contents, false);
                else
                    addItem(item, false);
            }
        }

        void addItem(WItem item, boolean recurse) {
            if(item == null || item.item == null || !seenItems.add(item.item))
                return;
            if(item.item.contents instanceof ItemStack) {
                /*
                 * A configured fishing pole exposes its tackle as an ItemStack. Keep the
                 * parent pole selectable while also walking that stack; ordinary stacks
                 * are containers only and must not become fishing-equipment candidates.
                 */
                if(FishingAtlas.classify(safeName(item.item), safeResource(item.item)) ==
                        FishingAtlas.Part.POLE)
                    items.add(item);
                if(recurse)
                    addWidget(item.item.contents, true);
                if(recurse && item.item.contentswnd != null)
                    addWidget(item.item.contentswnd.inv, true);
                return;
            }
            items.add(item);
            if(recurse) {
                addWidget(item.item.contents, true);
                if(item.item.contentswnd != null)
                    addWidget(item.item.contentswnd.inv, true);
            }
        }

        void addWidget(Widget widget, boolean recurse) {
            if(widget == null || !seenWidgets.add(widget))
                return;
            Inventory inventory = Inventory.fromWidget(widget);
            if(inventory != null) {
                for(WItem item : inventory.getAllItems())
                    addItem(item, recurse);
            } else if(widget instanceof ItemStack) {
                for(WItem item : ((ItemStack)widget).wmap.values())
                    addItem(item, recurse);
            } else if(widget instanceof WItem) {
                addItem((WItem)widget, recurse);
            }
            for(Widget child : widget.children())
                addWidget(child, recurse);
        }
    }
}
