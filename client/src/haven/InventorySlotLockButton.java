package haven;

import java.awt.image.BufferedImage;

/** Window control that owns the modal cursor used to toggle protected inventory slots. */
final class InventorySlotLockButton extends ICheckBox implements Widget.CursorQuery.Handler {
    private static final BufferedImage[] IMAGES = InventorySlotLocks.buttonImages();

    private final Inventory inventory;
    private UI.Grab grab;

    InventorySlotLockButton(Inventory inventory) {
        super(new TexI(IMAGES[0]), new TexI(IMAGES[1]), new TexI(IMAGES[2]), new TexI(IMAGES[1]));
        this.inventory = inventory;
        settip("Lock inventory slots (click again or right-click to finish)");
    }

    public boolean state() {
        return grab != null;
    }

    public void click() {
        if(grab == null)
            grab = ui.grabmouse(this);
        else
            ungrab();
    }

    public boolean mousedown(MouseDownEvent event) {
        if(!event.grabbed)
            return super.mousedown(event);
        if(event.b == 3)
            return ungrab();
        if(event.b != 1)
            return true;
        if(checkhit(event.c))
            return ungrab();

        Coord global = event.c.add(rootpos());
        Coord local = global.sub(inventory.rootpos());
        if(inventory.visible() && local.isect(Coord.z, inventory.sz))
            inventory.toggleSlotLock(local.div(Inventory.sqsz));
        return true;
    }

    public boolean getcurs(CursorQuery event) {
        return event.grabbed ? event.set(InventorySlotLocks.cursor()) : false;
    }

    public void dispose() {
        ungrab();
        super.dispose();
    }

    private boolean ungrab() {
        if(grab != null) {
            grab.remove();
            grab = null;
        }
        return true;
    }
}
