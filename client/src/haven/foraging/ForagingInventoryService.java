package haven.foraging;

import haven.Coord;
import haven.GItem;
import haven.GameUI;
import haven.Inventory;
import haven.ItemInfo;
import haven.Loading;
import haven.Resource;
import haven.WItem;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Phase 1 main-inventory capacity and exact-resource acknowledgement adapter. */
public final class ForagingInventoryService {
    private final GameUI gui;

    public ForagingInventoryService(GameUI gui) {
        this.gui = gui;
    }

    public int freeCells() {
        Inventory inventory = gui.maininv;
        if(inventory == null)
            return(-1);
        Set<Coord> occupied = new HashSet<>();
        synchronized(inventory.wmap) {
            for(Map.Entry<GItem, WItem> entry : inventory.wmap.entrySet()) {
                WItem widget = entry.getValue();
                Coord position = widget.c.div(Inventory.sqsz);
                Coord extent = widget.sz.add(Inventory.sqsz).sub(1, 1).div(Inventory.sqsz);
                occupy(occupied, position, extent);
            }
        }
        return(countFree(inventory.isz, inventory.sqmask, inventory.lockedSlots(), occupied));
    }

    static int countFree(Coord size, boolean[] mask, Collection<Coord> locked,
                         Collection<Coord> occupied) {
        int free = 0;
        int index = 0;
        for(int y = 0; y < size.y; y++) {
            for(int x = 0; x < size.x; x++, index++) {
                Coord cell = Coord.of(x, y);
                boolean masked = mask != null && index < mask.length && mask[index];
                if(!masked && !locked.contains(cell) && !occupied.contains(cell))
                    free++;
            }
        }
        return(free);
    }

    private static void occupy(Set<Coord> cells, Coord position, Coord extent) {
        for(int y = 0; y < Math.max(1, extent.y); y++)
            for(int x = 0; x < Math.max(1, extent.x); x++)
                cells.add(position.add(x, y));
    }

    public int compatibleResourceAmount(String groundResourceName) {
        Inventory inventory = gui.maininv;
        if(inventory == null)
            return(-1);
        String basename = basename(groundResourceName);
        int total = 0;
        synchronized(inventory.wmap) {
            for(GItem item : inventory.wmap.keySet()) {
                try {
                    Resource resource = item.getres();
                    if(resource == null || !basename.equals(resource.basename()))
                        continue;
                    total += amount(item.info());
                } catch(Loading ignored) {
                    return(-1);
                }
            }
        }
        return(total);
    }

    static String basename(String resourceName) {
        if(resourceName == null)
            return("");
        int separator = resourceName.lastIndexOf('/');
        return(separator < 0 ? resourceName : resourceName.substring(separator + 1));
    }

    private static int amount(List<ItemInfo> info) {
        for(ItemInfo itemInfo : info) {
            if(itemInfo instanceof GItem.Amount)
                return(Math.max(1, ((GItem.Amount)itemInfo).itemnum()));
        }
        return(1);
    }
}
