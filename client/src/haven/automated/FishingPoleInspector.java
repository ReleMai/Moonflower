package haven.automated;

import haven.GItem;
import haven.Inventory;
import haven.ItemInfo;
import haven.Loading;
import haven.WItem;
import haven.Widget;
import haven.automated.helpers.FishingAtlas;
import haven.res.ui.stackinv.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/** Converts every supported fishing-pole contents representation into one verified state. */
final class FishingPoleInspector {
    private static final long VERIFY_TIMEOUT_MS = 4000;

    State inspect(WItem pole) {
        State state = new State();
        if(pole == null || pole.item == null) {
            state.loading = true;
            return(state);
        }
        try {
            for(ItemInfo info : pole.item.info()) {
                if(info instanceof ItemInfo.Contents)
                    addContents(state, (ItemInfo.Contents)info);
            }
            addWidgetContents(state, pole.item.contents,
                    Collections.newSetFromMap(new IdentityHashMap<>()));
        } catch(Loading loading) {
            state.loading = true;
        }
        return(state);
    }

    State awaitReady(WItem pole, Kind consumable) throws InterruptedException {
        long deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS;
        while(System.currentTimeMillis() < deadline) {
            State state = inspect(pole);
            if(!state.loading && state.ready(consumable))
                return(state);
            Thread.sleep(50);
        }
        State state = inspect(pole);
        return(!state.loading && state.ready(consumable) ? state : null);
    }

    /** A pole tooltip can describe line, hook, and bait/lure in one contents block. */
    static void addContents(State state, ItemInfo.Contents contents) {
        boolean foundName = addContentInfo(state, contents == null ? null : contents.sub);
        if(!foundName && contents != null)
            state.add(FishingItemMetadata.describe(contents));
    }

    private static boolean addContentInfo(State state, Iterable<ItemInfo> infos) {
        if(infos == null)
            return(false);
        boolean foundName = false;
        for(ItemInfo info : infos) {
            if(info instanceof ItemInfo.Name) {
                state.add(new FishingEquipment.ItemData("", ((ItemInfo.Name)info).str.text, null));
                foundName = true;
            } else if(info instanceof ItemInfo.Contents) {
                foundName |= addContentInfo(state, ((ItemInfo.Contents)info).sub);
            }
        }
        return(foundName);
    }

    private void addWidgetContents(State state, Widget widget, Set<Widget> seen) {
        if(widget == null || !seen.add(widget))
            return;
        Inventory inventory = Inventory.fromWidget(widget);
        if(inventory != null) {
            for(WItem item : inventory.getAllItems())
                state.add(FishingItemMetadata.describe(item));
        } else if(widget instanceof ItemStack) {
            for(WItem item : ((ItemStack)widget).wmap.values())
                state.add(FishingItemMetadata.describe(item));
        } else if(widget instanceof WItem) {
            state.add(FishingItemMetadata.describe((WItem)widget));
        } else if(widget instanceof GItem) {
            state.add(FishingItemMetadata.describe((GItem)widget));
        }
        for(Widget child : widget.children())
            addWidgetContents(state, child, seen);
    }

    enum Kind { LINE, HOOK, BAIT, LURE }

    static final class State {
        FishingEquipment.ItemData line;
        FishingEquipment.ItemData hook;
        FishingEquipment.ItemData bait;
        FishingEquipment.ItemData lure;
        boolean loading;
        final Set<String> unknown = new HashSet<>();

        void add(FishingEquipment.ItemData item) {
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

        FishingEquipment.ItemData consumable(Kind kind) {
            return(kind == Kind.LURE ? lure : bait);
        }

        boolean has(Kind kind) {
            switch(kind) {
            case LINE: return(line != null);
            case HOOK: return(hook != null);
            case BAIT: return(bait != null);
            case LURE: return(lure != null);
            default: return(false);
            }
        }

        boolean ready(Kind consumable) {
            return(line != null && hook != null && consumable(consumable) != null && unknown.isEmpty());
        }

        boolean compatible(java.util.List<String> lines, java.util.List<String> hooks,
                           java.util.List<String> consumables, Kind consumableKind) {
            if(!unknown.isEmpty() || !selected(line, lines) || !selected(hook, hooks) ||
                    !selected(consumable(consumableKind), consumables))
                return(false);
            return(consumableKind == Kind.LURE ? bait == null : lure == null);
        }

        private static boolean selected(FishingEquipment.ItemData item, java.util.List<String> allowed) {
            if(item == null)
                return(true);
            if(allowed == null || allowed.isEmpty())
                return(false);
            for(String value : allowed) {
                if(FishingAtlas.sameDisplayName(value, item.displayName))
                    return(true);
            }
            return(false);
        }

        String summary() {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if(line != null) parts.add("line=" + line.displayName);
            if(hook != null) parts.add("hook=" + hook.displayName);
            if(bait != null) parts.add("bait=" + bait.displayName);
            if(lure != null) parts.add("lure=" + lure.displayName);
            if(!unknown.isEmpty()) parts.add("unknown=" + String.join(", ", unknown));
            return(parts.isEmpty() ? "no recognized contents" : String.join("; ", parts));
        }
    }
}
