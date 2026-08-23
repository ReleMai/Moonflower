package haven.feasting;

import haven.Button;
import haven.Coord;
import haven.GItem;
import haven.Inventory;
import haven.ItemInfo;
import haven.Loading;
import haven.UI;
import haven.WItem;
import haven.Widget;
import haven.Window;
import haven.res.ui.tt.wear.Wear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Shared inspection used by manual feasting and the helper's safe mode. */
public final class TablewareProtection {
    private TablewareProtection() {
    }

    public static Inspection inspect(UI ui) {
        if(ui == null || ui.gui == null)
            return(Inspection.unknown("The Table UI is unavailable."));
        Window table = activeTable(ui);
        return(table == null ? Inspection.unknown("No active feasting Table was found.") :
                inspect(table));
    }

    public static Inspection inspect(Window table) {
        if(table == null || table.parent == null)
            return(Inspection.unknown("The Table UI disappeared."));
        List<String> risks = new ArrayList<>();
        Set<Inventory> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] tablewareInventories = {0};
        try {
            collectTableware(table, visited, risks, tablewareInventories);
        } catch(Loading loading) {
            return(Inspection.unknown("Tableware durability is still loading."));
        } catch(RuntimeException failure) {
            return(Inspection.unknown("Tableware durability could not be read safely."));
        }
        if(tablewareInventories[0] == 0)
            return(Inspection.unknown("Tableware slots are not available yet."));
        return(risks.isEmpty() ? Inspection.safe() : Inspection.atRisk(risks));
    }

    public static Window activeTable(UI ui) {
        if(ui == null || ui.gui == null)
            return(null);
        for(Window window : ui.gui.getAllWindows()) {
            if("Table".equals(window.cap) && hasServerFeastButton(window))
                return(window);
        }
        return(null);
    }

    public static boolean hasServerFeastButton(Window table) {
        if(table == null)
            return(false);
        for(Widget child = table.child; child != null; child = child.next) {
            if(child instanceof Button)
                return(true);
        }
        return(false);
    }

    public static boolean isTablewareInventory(Inventory inventory) {
        return(inventory != null && (inventory.isz.equals(Coord.of(3, 3)) ||
                inventory.isz.equals(Coord.of(1, 2))));
    }

    private static void collectTableware(Widget parent, Set<Inventory> visited,
                                         List<String> risks, int[] tablewareInventories) {
        Inventory inventory = Inventory.fromWidget(parent);
        if(inventory != null && visited.add(inventory) && isTablewareInventory(inventory)) {
            tablewareInventories[0]++;
            for(WItem item : inventory.wmap.values()) {
                GItem gitem = item.item;
                List<ItemInfo> info = gitem.cookbookInfo();
                Wear wear = ItemInfo.find(Wear.class, info);
                if(wear != null && wear.m - wear.d <= 1)
                    risks.add(itemName(gitem, info));
            }
        }
        for(Widget child = parent.child; child != null; child = child.next)
            collectTableware(child, visited, risks, tablewareInventories);
    }

    private static String itemName(GItem item, List<ItemInfo> info) {
        ItemInfo.Name name = ItemInfo.find(ItemInfo.Name.class, info);
        return(name == null ? item.getres().basename() : name.str.text);
    }

    public static final class Inspection {
        public final FeastingSnapshot.TablewareState state;
        public final List<String> atRisk;
        public final String message;

        private Inspection(FeastingSnapshot.TablewareState state, List<String> atRisk,
                           String message) {
            this.state = state;
            this.atRisk = Collections.unmodifiableList(new ArrayList<>(atRisk));
            this.message = message;
        }

        static Inspection safe() {
            return(new Inspection(FeastingSnapshot.TablewareState.SAFE,
                    Collections.emptyList(), "Tableware is safe."));
        }

        static Inspection atRisk(List<String> names) {
            String description = String.join(", ", names);
            return(new Inspection(FeastingSnapshot.TablewareState.AT_RISK, names,
                    "At risk: " + description));
        }

        static Inspection unknown(String message) {
            return(new Inspection(FeastingSnapshot.TablewareState.UNKNOWN,
                    Collections.emptyList(), message));
        }
    }
}
