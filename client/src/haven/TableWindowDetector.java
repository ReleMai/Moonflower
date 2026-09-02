package haven;

/**
 * Classifies the server-created windows that use the generic Table caption.
 *
 * <p>The caption is only a coarse protocol signal. The current Alchemist's
 * Table exposes a 4x2 inventory, while feasting tables expose a larger food
 * inventory and the 3x3/1x2 inventories are the tableware sections inside a
 * normal table window. Keeping those rules here gives future table-specific
 * integrations one place to extend instead of scattering size checks through
 * UI.</p>
 */
public final class TableWindowDetector {
    public static final Coord ALCHEMIST_INVENTORY_SIZE = Coord.of(4, 2);

    public enum Kind {
        NOT_TABLE,
        TABLEWARE,
        FEASTING_TABLE,
        ALCHEMIST_TABLE
    }

    private TableWindowDetector() {
    }

    public static Kind classify(Window window, Inventory inventory) {
        return(classify(window == null ? null : window.cap,
                inventory == null ? null : inventory.isz));
    }

    public static Kind classify(String caption, Coord inventorySize) {
        if(!isTableWindow(caption) || inventorySize == null)
            return(Kind.NOT_TABLE);
        if("Alchemist's Table".equals(caption) || isAlchemyInventory(inventorySize))
            return(Kind.ALCHEMIST_TABLE);
        if(isTablewareInventory(inventorySize))
            return(Kind.TABLEWARE);
        return(Kind.FEASTING_TABLE);
    }

    public static boolean isTableWindow(Window window) {
        return(isTableWindow(window == null ? null : window.cap));
    }

    public static boolean isTableWindow(String caption) {
        return("Table".equals(caption) || "Alchemist's Table".equals(caption));
    }

    public static boolean isAlchemyInventory(Coord inventorySize) {
        return(ALCHEMIST_INVENTORY_SIZE.equals(inventorySize));
    }

    public static boolean isTablewareInventory(Coord inventorySize) {
        return(Coord.of(3, 3).equals(inventorySize) ||
                Coord.of(1, 2).equals(inventorySize));
    }
}
