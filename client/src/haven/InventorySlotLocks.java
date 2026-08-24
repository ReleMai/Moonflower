package haven;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Persistent per-slot protection used by client-side inventory sorting. */
final class InventorySlotLocks {
    private static final String MAIN_INVENTORY_KEY = "inventory-slot-locks/main";
    private static final Color LOCK_FILL = new Color(54, 43, 20, 125);
    private static final Color LOCK_BORDER = new Color(242, 194, 74, 230);
    private static final Resource LOCK_CURSOR = createLockCursor();

    private final Inventory inventory;
    private final Set<Coord> slots = new LinkedHashSet<>();
    private String preferenceKey;

    InventorySlotLocks(Inventory inventory) {
        this.inventory = inventory;
    }

    void attached() {
        String resolved = preferenceKey(inventory);
        if(Utils.eq(resolved, preferenceKey))
            return;
        preferenceKey = resolved;
        slots.clear();
        if(preferenceKey != null)
            slots.addAll(decode(Utils.getpref(preferenceKey, "")));
        removeInvalidSlots();
    }

    synchronized boolean toggle(Coord slot) {
        if(!valid(slot))
            return false;
        if(!slots.remove(slot))
            slots.add(new Coord(slot));
        save();
        return true;
    }

    synchronized boolean isLocked(Coord slot) {
        return slots.contains(slot);
    }

    synchronized Set<Coord> snapshot() {
        return new LinkedHashSet<>(slots);
    }

    synchronized void inventoryResized() {
        if(removeInvalidSlots())
            save();
    }

    void draw(GOut g) {
        for(Coord slot : snapshot()) {
            if(MoonFlowerHudTheme.active()) {
                MoonFlowerHudTheme.drawInventoryLockOverlay(g, slot.mul(Inventory.sqsz), Inventory.sqsz);
                continue;
            }
            Coord position = slot.mul(Inventory.sqsz).add(UI.scale(2), UI.scale(2));
            Coord size = Inventory.sqsz.sub(UI.scale(4), UI.scale(4));
            g.chcolor(LOCK_FILL);
            g.frect(position, size);
            g.chcolor(LOCK_BORDER);
            g.rect(position, size);
            g.chcolor();
        }
    }

    static boolean itemTouchesLockedSlot(Coord position, Coord size, Collection<Coord> locked) {
        for(Coord slot : locked) {
            if((slot.x >= position.x) && (slot.y >= position.y) &&
                    (slot.x < position.x + size.x) && (slot.y < position.y + size.y))
                return true;
        }
        return false;
    }

    static BufferedImage[] buttonImages() {
        int size = UI.scale(20);
        return new BufferedImage[] {
                lockImage(size, new Color(231, 192, 82), new Color(3, 12, 18, 245), false),
                lockImage(size, new Color(255, 229, 128), new Color(24, 95, 105, 250), true),
                lockImage(size, new Color(255, 216, 104), new Color(3, 12, 18, 250), false)
        };
    }

    static Resource cursor() {
        return LOCK_CURSOR;
    }

    static String encode(Collection<Coord> slots) {
        List<Coord> ordered = new ArrayList<>(slots);
        ordered.sort(Comparator.comparingInt((Coord slot) -> slot.y).thenComparingInt(slot -> slot.x));
        StringBuilder encoded = new StringBuilder();
        for(Coord slot : ordered) {
            if(encoded.length() > 0)
                encoded.append(';');
            encoded.append(slot.x).append(',').append(slot.y);
        }
        return encoded.toString();
    }

    static Set<Coord> decode(String encoded) {
        Set<Coord> decoded = new LinkedHashSet<>();
        if(encoded == null || encoded.trim().isEmpty())
            return decoded;
        for(String entry : encoded.split(";")) {
            String[] parts = entry.split(",", -1);
            if(parts.length != 2)
                continue;
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                if(x >= 0 && y >= 0)
                    decoded.add(Coord.of(x, y));
            } catch(NumberFormatException ignored) {
            }
        }
        return decoded;
    }

    private static String preferenceKey(Inventory inventory) {
        Window window = inventory.getparent(Window.class);
        if(window == null)
            return null;
        if("Inventory".equals(window.cap))
            return MAIN_INVENTORY_KEY;
        if(window instanceof GItem.ContentsWindow)
            return ((GItem.ContentsWindow)window).inventorySlotLockPreferenceKey();
        return null;
    }

    private synchronized boolean removeInvalidSlots() {
        return slots.removeIf(slot -> !valid(slot));
    }

    private boolean valid(Coord slot) {
        return slot != null && slot.x >= 0 && slot.y >= 0 &&
                slot.x < inventory.isz.x && slot.y < inventory.isz.y;
    }

    private void save() {
        if(preferenceKey != null)
            Utils.setpref(preferenceKey, encode(slots));
    }

    private static Resource createLockCursor() {
        try {
            BufferedImage image = lockCursorImage();
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);

            Resource.Virtual resource = new Resource.Virtual(Resource.local(),
                    "customclient/curs/inventory-slot-lock", 1);
            MessageBuf imageData = new MessageBuf();
            imageData.adduint8(0).addint8((byte)0).addint16((short)0).adduint8(2)
                    .addint16((short)-1).addint16((short)0).addint16((short)0)
                    .addbytes(png.toByteArray());
            resource.add(resource.new Image(new MessageBuf(imageData.fin())));

            MessageBuf negativeData = new MessageBuf();
            addShortCoord(negativeData, Coord.of(1, 1));
            addShortCoord(negativeData, Coord.z);
            addShortCoord(negativeData, image == null ? Coord.of(24, 24) : Utils.imgsz(image));
            negativeData.addint32(0).adduint8(0);
            resource.add(resource.new Neg(new MessageBuf(negativeData.fin())));
            return resource;
        } catch(IOException | RuntimeException error) {
            return Resource.local().loadwait("gfx/hud/curs/wrench");
        }
    }

    private static void addShortCoord(MessageBuf message, Coord coordinate) {
        message.addint16((short)coordinate.x).addint16((short)coordinate.y);
    }

    private static BufferedImage lockCursorImage() {
        BufferedImage image = TexI.mkbuf(Coord.of(24, 24));
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(255, 255, 255, 235));
        graphics.fillPolygon(new int[] {0, 2, 7, 4}, new int[] {0, 12, 8, 7}, 4);
        graphics.setColor(new Color(20, 20, 20, 240));
        graphics.drawPolygon(new int[] {0, 2, 7, 4}, new int[] {0, 12, 8, 7}, 4);
        drawLock(graphics, 8, 7, 14, new Color(244, 198, 72), new Color(50, 39, 14, 245));
        graphics.dispose();
        return image;
    }

    private static BufferedImage lockImage(int size, Color metal, Color outline, boolean active) {
        BufferedImage image = TexI.mkbuf(Coord.of(size, size));
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(active ? new Color(30, 112, 119, 245) : new Color(2, 12, 18, 238));
        graphics.fillOval(0, 0, size - 1, size - 1);
        graphics.setStroke(new BasicStroke(Math.max(1, UI.scale(1))));
        graphics.setColor(active ? new Color(255, 229, 128, 255) : new Color(142, 106, 46, 245));
        graphics.drawOval(1, 1, size - 3, size - 3);
        int lockSize = Math.max(9, size - UI.scale(8));
        drawLock(graphics, (size - lockSize) / 2, UI.scale(3), lockSize, metal, outline);
        graphics.dispose();
        return image;
    }

    private static void drawLock(Graphics2D graphics, int x, int y, int size,
                                 Color metal, Color outline) {
        int shackleWidth = Math.max(2, size / 5);
        graphics.setStroke(new BasicStroke(shackleWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(outline);
        graphics.drawArc(x + size / 5, y, size * 3 / 5, size * 3 / 5, 0, 180);
        graphics.setColor(metal);
        graphics.setStroke(new BasicStroke(Math.max(1, shackleWidth - 1),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawArc(x + size / 5, y, size * 3 / 5, size * 3 / 5, 0, 180);

        int bodyY = y + size / 3;
        graphics.setColor(outline);
        graphics.fillRoundRect(x, bodyY, size, size * 2 / 3, 3, 3);
        graphics.setColor(metal);
        graphics.fillRoundRect(x + 1, bodyY + 1, Math.max(1, size - 2),
                Math.max(1, size * 2 / 3 - 2), 2, 2);
        graphics.setColor(outline);
        graphics.fillOval(x + size / 2 - 1, bodyY + size / 4, 3, 4);
    }
}
