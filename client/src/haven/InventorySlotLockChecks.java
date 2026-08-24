package haven;

import java.util.Arrays;
import java.util.Set;

/** Offline checks for slot-lock persistence, item pinning, and cursor assets. */
public final class InventorySlotLockChecks {
    private InventorySlotLockChecks() {
    }

    public static void main(String[] args) {
        Set<Coord> decoded = InventorySlotLocks.decode("3,2;bad;1,0;-1,4;3,2");
        require(decoded.size() == 2 && decoded.contains(Coord.of(1, 0)) && decoded.contains(Coord.of(3, 2)),
                "slot-lock preference decoding");
        require(InventorySlotLocks.encode(decoded).equals("1,0;3,2"),
                "stable slot-lock preference encoding");

        require(InventorySlotLocks.itemTouchesLockedSlot(Coord.of(2, 1), Coord.of(2, 2),
                        Arrays.asList(Coord.of(3, 2))),
                "multi-slot item should be pinned when any covered slot is locked");
        require(!InventorySlotLocks.itemTouchesLockedSlot(Coord.of(2, 1), Coord.of(2, 2),
                        Arrays.asList(Coord.of(4, 2))),
                "adjacent lock should not pin an item");

        Resource cursor = InventorySlotLocks.cursor();
        require(cursor.name.equals("customclient/curs/inventory-slot-lock"),
                "generated padlock cursor resource");
        require(cursor.flayer(Resource.imgc).img.getWidth() == 24 &&
                        cursor.flayer(Resource.negc).cc.equals(Coord.of(1, 1)),
                "padlock cursor image and hotspot");

        java.awt.image.BufferedImage[] buttons = InventorySlotLocks.buttonImages();
        require(buttons.length == 3,
                "inventory lock button states");
        require(buttons[0].getWidth() == UI.scale(20) && buttons[0].getHeight() == UI.scale(20),
                "scaled inventory lock button dimensions");
        require(((buttons[0].getRGB(buttons[0].getWidth() / 2, buttons[0].getHeight() / 2) >>> 24) & 0xff) > 200,
                "visible inventory lock button center");
        System.out.println("Inventory slot-lock checks passed.");
    }

    private static void require(boolean condition, String description) {
        if(!condition)
            throw new AssertionError("Unexpected " + description);
    }
}
