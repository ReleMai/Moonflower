package haven.automated;

import haven.Equipory;
import haven.GItem;
import haven.GameUI;
import haven.WItem;
import haven.automated.helpers.FishingAtlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Resolves current widgets after every server move instead of retaining stale WItem wrappers. */
final class FishingItemLocator {
    private static final int LEFT_HAND_SLOT = 6;
    private static final int RIGHT_HAND_SLOT = 7;
    private final GameUI gui;

    FishingItemLocator(GameUI gui) {
        this.gui = gui;
    }

    WItem findPole(String poleName, GItem preferred) {
        List<WItem> matches = matchingPoles(poleName);
        if(preferred != null) {
            for(WItem item : matches) {
                if(item.item == preferred)
                    return(item);
            }
            // A server refresh may replace the client widget. Continue only when the
            // requested pole name still resolves unambiguously.
            return(matches.size() == 1 ? matches.get(0) : null);
        }
        WItem hand = findPoleInHands(poleName, null);
        if(hand != null)
            return(hand);
        return(matches.isEmpty() ? null : matches.get(0));
    }

    private List<WItem> matchingPoles(String poleName) {
        List<WItem> matches = new ArrayList<>();
        Set<GItem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Equipory equipory = gui.getequipory();
        if(equipory != null) {
            for(int slot : new int[]{LEFT_HAND_SLOT, RIGHT_HAND_SLOT}) {
                WItem item = equipory.slots[slot];
                if(isPole(item, poleName) && seen.add(item.item))
                    matches.add(item);
            }
        }
        for(WItem item : FishingInventory.equipmentItems(gui)) {
            if(isPole(item, poleName) && !FishingInventory.insideFishingPole(item) &&
                    seen.add(item.item))
                matches.add(item);
        }
        return(matches);
    }

    WItem findPoleInHands(String poleName, GItem preferred) {
        Equipory equipory = gui.getequipory();
        if(equipory == null)
            return(null);
        WItem fallback = null;
        for(int slot : new int[]{LEFT_HAND_SLOT, RIGHT_HAND_SLOT}) {
            WItem item = equipory.slots[slot];
            if(!isPole(item, poleName))
                continue;
            if(preferred != null) {
                if(item.item == preferred)
                    return(item);
            } else if(fallback == null) {
                fallback = item;
            }
        }
        return(fallback);
    }

    WItem candidate(FishingPoleInspector.Kind kind, List<String> priority) {
        List<String> ordered = priority == null ? List.of() : priority;
        if(ordered.isEmpty())
            return(null);
        List<WItem> matches = new ArrayList<>();
        for(WItem item : FishingInventory.equipmentItems(gui)) {
            if(item == null || item.item == null || FishingInventory.insideFishingPole(item))
                continue;
            String name = FishingItemMetadata.name(item);
            if(classify(item) == kind && selected(ordered, name))
                matches.add(item);
        }
        matches.sort(Comparator
                .comparingInt((WItem item) -> preferenceIndex(ordered, FishingItemMetadata.name(item)))
                .thenComparing(FishingItemMetadata::quality,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FishingItemMetadata::resource));
        return(matches.isEmpty() ? null : matches.get(0));
    }

    private static boolean isPole(WItem item, String poleName) {
        return(item != null && item.item != null && poleName != null &&
                poleName.equals(FishingItemMetadata.name(item)) &&
                FishingAtlas.classify(FishingItemMetadata.name(item),
                        FishingItemMetadata.resource(item)) == FishingAtlas.Part.POLE);
    }

    private static FishingPoleInspector.Kind classify(WItem item) {
        switch(FishingAtlas.classify(FishingItemMetadata.name(item), FishingItemMetadata.resource(item))) {
        case LINE: return(FishingPoleInspector.Kind.LINE);
        case HOOK: return(FishingPoleInspector.Kind.HOOK);
        case BAIT: return(FishingPoleInspector.Kind.BAIT);
        case LURE: return(FishingPoleInspector.Kind.LURE);
        default: return(null);
        }
    }

    private static int preferenceIndex(List<String> priority, String name) {
        for(int index = 0; index < priority.size(); index++) {
            if(FishingAtlas.sameDisplayName(priority.get(index), name))
                return(index);
        }
        return(Integer.MAX_VALUE);
    }

    private static boolean selected(List<String> priority, String name) {
        for(String choice : priority) {
            if(FishingAtlas.sameDisplayName(choice, name))
                return(true);
        }
        return(false);
    }
}
