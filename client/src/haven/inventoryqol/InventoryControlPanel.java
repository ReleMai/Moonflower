package haven.inventoryqol;

import haven.Area;
import haven.Coord;
import haven.GOut;
import haven.Inventory;
import haven.MoonFlowerHudSettings;
import haven.MoonFlowerHudTheme;
import haven.Text;
import haven.Tex;
import haven.UI;
import haven.Utils;
import haven.Widget;
import haven.Window;
import haven.automated.InventorySorter;
import haven.automated.StackAllItems;
import haven.automated.UnstackAllItems;

import java.util.function.BooleanSupplier;

/** Attached slide-out home for every inventory utility and bulk action. */
public final class InventoryControlPanel extends Widget {
    private static final int ATTACH_OVERLAP = UI.scale(18);
    private static final int SLIDE_TRAVEL = UI.scale(34);
    private static final int ANIM_MS = 210;
    private final Window target;
    private final Inventory inventory;
    private final InventoryBulkActionController controller;
    private final Layout layout;
    private final ActionLeaf lockSlots;
    private final Tex heading;
    private final Tex organizeHeading;
    private final Tex processingHeading;
    private boolean expanded;
    private double progress;
    private boolean placeRight = true;
    private String lastStatus = "";
    private Tex statusTex;

    public InventoryControlPanel(Window target, Inventory inventory) {
        this(target, inventory, layout(target.hasInventoryExtendedView()));
    }

    private InventoryControlPanel(Window target, Inventory inventory, Layout layout) {
        super(layout.panelSize);
        this.target = target;
        this.inventory = inventory;
        this.layout = layout;
        this.controller = new InventoryBulkActionController(target.ui.gui, inventory);
        this.heading = Text.render("Inventory tools", MoonFlowerHudTheme.IVORY).tex();
        this.organizeHeading = Text.render("ORGANIZE", MoonFlowerHudTheme.GOLD_SOFT).tex();
        this.processingHeading = Text.render("BULK PROCESSING", MoonFlowerHudTheme.GOLD_SOFT).tex();

        ActionLeaf sort = add(action(layout.sort, "Sort all", () -> InventorySorter.sort(inventory)), layout.sort.ul);
        sort.tooltip = "Sort movable items while preserving protected inventory slots.";
        ActionLeaf stack = add(action(layout.stack, "Stack all", this::stackAll), layout.stack.ul);
        stack.tooltip = "Stack all compatible items in this inventory.";
        ActionLeaf unstack = add(action(layout.unstack, "Unstack all", this::unstackAll), layout.unstack.ul);
        unstack.tooltip = "Unstack all item stacks in this inventory.";
        lockSlots = add(action(layout.lock, "Lock slots", this::toggleSlotLock), layout.lock.ul);
        lockSlots.tooltip = "Protect inventory cells from sorting; right-click anywhere to finish.";

        if(layout.extended != null) {
            ActionLeaf extended = add(action(layout.extended, "Extended view", target::toggleInventoryExtendedView),
                    layout.extended.ul);
            extended.tooltip = "Open or close the inventory's extended item list.";
        }

        ActionLeaf butcher = add(action(layout.butcher, "Butcher all",
                () -> controller.start(InventoryBulkActionController.Action.BUTCHER_ALL)), layout.butcher.ul);
        butcher.tooltip = "Process all applicable animal items using the best reachable sharp tool.";
        ActionLeaf crack = add(action(layout.crack, "Crack all",
                () -> controller.start(InventoryBulkActionController.Action.CRACK_ALL)), layout.crack.ul);
        crack.tooltip = "Choose Crack on every applicable item in this inventory.";
        ActionLeaf stop = add(action(layout.stop, "Stop", controller::stop), layout.stop.ul);
        stop.tooltip = "Stop the current bulk action and clear its pending flower-menu selection.";
        refreshLockLabel();
        hide();
    }

    private ActionLeaf action(Area area, String label, Runnable action) {
        return(new ActionLeaf(area.sz(), label, action));
    }

    private void stackAll() {
        new Thread(new StackAllItems(target.ui.gui, inventory), "inventory-stack-all").start();
    }

    private void unstackAll() {
        new Thread(new UnstackAllItems(target.ui.gui, inventory), "inventory-unstack-all").start();
    }

    private void toggleSlotLock() {
        target.toggleInventorySlotLock();
        refreshLockLabel();
    }

    private void refreshLockLabel() {
        lockSlots.setLabel(target.inventorySlotLockActive() ? "Locking" : "Lock slots");
    }

    public void toggle() {
        expanded = !expanded;
        if(expanded)
            show();
        else
            controller.stop();
    }

    public boolean expanded() {
        return(expanded);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if(target == null || target.parent == null || !target.visible()) {
            expanded = false;
            progress = 0;
            controller.stop();
            hide();
            return;
        }
        refreshLockLabel();
        double step = MoonFlowerHudSettings.hudReducedMotion() ? 1.0 :
                Math.max(0.01, dt * 1000.0 / ANIM_MS);
        progress = expanded ? Math.min(1.0, progress + step) : Math.max(0.0, progress - step);
        if(progress <= 0 && !expanded) {
            hide();
            return;
        }
        show();
        followTarget();
    }

    private void followTarget() {
        Coord host = parent == null ? Coord.z : parent.sz;
        placeRight = target.c.x + target.sz.x - ATTACH_OVERLAP + sz.x <= host.x;
        move(dockPosition(target.c, target.sz, sz, host, placeRight, slideProgress()));
    }

    static Coord dockPosition(Coord targetPosition, Coord targetSize, Coord panelSize, Coord hostSize,
                              boolean placeRight, double motion) {
        int openX = placeRight ? targetPosition.x + targetSize.x - ATTACH_OVERLAP :
                targetPosition.x - panelSize.x + ATTACH_OVERLAP;
        int closedX = openX + (placeRight ? -SLIDE_TRAVEL : SLIDE_TRAVEL);
        int x = (int)Math.round(closedX + ((openX - closedX) * Utils.clip(motion, 0.0, 1.0)));
        int y = targetPosition.y + ((targetSize.y - panelSize.y) / 2);
        return(Coord.of(Utils.clip(x, 0, Math.max(0, hostSize.x - panelSize.x)),
                Utils.clip(y, 0, Math.max(0, hostSize.y - panelSize.y))));
    }

    private double visualProgress() {
        if(MoonFlowerHudSettings.hudReducedMotion())
            return(expanded ? 1.0 : 0.0);
        return(Utils.smoothstep(progress));
    }

    /* Gives the drawer a gentle lead into its engraved hinge without changing the clipped hit area. */
    private double slideProgress() {
        if(MoonFlowerHudSettings.hudReducedMotion())
            return(expanded ? 1.0 : 0.0);
        double eased = Utils.smoothstep(progress);
        return(Utils.clip(eased + (Math.sin(progress * Math.PI) * 0.055), 0.0, 1.0));
    }

    @Override
    public void draw(GOut g) {
        double reveal = visualProgress();
        if(reveal <= 0)
            return;
        int visibleWidth = Math.max(1, (int)Math.round(sz.x * reveal));
        Coord clipOrigin = placeRight ? Coord.z : Coord.of(sz.x - visibleWidth, 0);
        GOut clipped = g.reclip(clipOrigin, Coord.of(visibleWidth, sz.y));
        GOut full = clipped.reclip(clipOrigin.inv(), sz);
        MoonFlowerHudTheme.drawInventoryToolsDock(full, sz, placeRight, reveal);
        full.chcolor(MoonFlowerHudTheme.GOLD_SOFT);
        full.line(Coord.of(UI.scale(17), UI.scale(29)), Coord.of(sz.x - UI.scale(17), UI.scale(29)), UI.scale(1));
        full.line(Coord.of(UI.scale(17), layout.processingRuleY),
                Coord.of(sz.x - UI.scale(17), layout.processingRuleY), UI.scale(1));
        full.chcolor();
        full.aimage(heading, Coord.of(sz.x / 2, UI.scale(16)), 0.5, 0.5);
        full.image(organizeHeading, layout.organizeHeading);
        full.image(processingHeading, layout.processingHeading);
        drawStatus(full);
        super.draw(full);
    }

    @Override
    public boolean checkhit(Coord c) {
        int visibleWidth = Math.max(0, (int)Math.round(sz.x * visualProgress()));
        Coord origin = placeRight ? Coord.z : Coord.of(sz.x - visibleWidth, 0);
        return(visibleWidth > 0 && c.isect(origin, Coord.of(visibleWidth, sz.y)));
    }

    private void drawStatus(GOut g) {
        String status = controller.status();
        if(!status.equals(lastStatus)) {
            if(statusTex != null)
                statusTex.dispose();
            lastStatus = status;
            String shown = status.length() <= 30 ? status : status.substring(0, 27) + "...";
            statusTex = Text.render(shown, controller.running() ? MoonFlowerHudTheme.TEAL_BRIGHT :
                    MoonFlowerHudTheme.IVORY).tex();
        }
        if(statusTex != null)
            g.aimage(statusTex, Coord.of(sz.x / 2, layout.statusY), 0.5, 1.0);
    }

    @Override
    public void destroy() {
        controller.close();
        heading.dispose();
        organizeHeading.dispose();
        processingHeading.dispose();
        if(statusTex != null)
            statusTex.dispose();
        super.destroy();
    }

    static Layout layout(boolean extended) {
        int width = UI.scale(220);
        int margin = UI.scale(17);
        int gap = UI.scale(5);
        int rowGap = UI.scale(4);
        int rowHeight = UI.scale(19);
        int columnWidth = (width - (margin * 2) - gap) / 2;
        int y = UI.scale(43);
        Area sort = Area.sized(Coord.of(margin, y), Coord.of(columnWidth, rowHeight));
        Area stack = Area.sized(Coord.of(margin + columnWidth + gap, y), Coord.of(columnWidth, rowHeight));
        y += rowHeight + rowGap;
        Area unstack = Area.sized(Coord.of(margin, y), Coord.of(columnWidth, rowHeight));
        Area lock = Area.sized(Coord.of(margin + columnWidth + gap, y), Coord.of(columnWidth, rowHeight));
        y += rowHeight + rowGap;
        Area extendedArea = null;
        if(extended) {
            extendedArea = Area.sized(Coord.of(margin, y), Coord.of(width - margin * 2, rowHeight));
        }
        /* Keep the wing stable when Extended view is unavailable on a container. */
        y += rowHeight + rowGap;
        int ruleY = y + UI.scale(3);
        Coord processingHeading = Coord.of(margin, ruleY + UI.scale(5));
        y = ruleY + UI.scale(19);
        Area butcher = Area.sized(Coord.of(margin, y), Coord.of(columnWidth, rowHeight));
        Area crack = Area.sized(Coord.of(margin + columnWidth + gap, y), Coord.of(columnWidth, rowHeight));
        y += rowHeight + rowGap;
        Area stop = Area.sized(Coord.of(margin, y), Coord.of(width - margin * 2, rowHeight));
        int statusY = y + rowHeight + UI.scale(15);
        Coord panelSize = UI.scale(220, 198);
        return(new Layout(panelSize, Coord.of(margin, UI.scale(31)), processingHeading, ruleY, statusY,
                sort, stack, unstack, lock, extendedArea, butcher, crack, stop));
    }

    static final class Layout {
        final Coord panelSize;
        final Coord organizeHeading;
        final Coord processingHeading;
        final int processingRuleY;
        final int statusY;
        final Area sort;
        final Area stack;
        final Area unstack;
        final Area lock;
        final Area extended;
        final Area butcher;
        final Area crack;
        final Area stop;

        Layout(Coord panelSize, Coord organizeHeading, Coord processingHeading, int processingRuleY, int statusY,
               Area sort, Area stack, Area unstack, Area lock, Area extended, Area butcher, Area crack, Area stop) {
            this.panelSize = panelSize;
            this.organizeHeading = organizeHeading;
            this.processingHeading = processingHeading;
            this.processingRuleY = processingRuleY;
            this.statusY = statusY;
            this.sort = sort;
            this.stack = stack;
            this.unstack = unstack;
            this.lock = lock;
            this.extended = extended;
            this.butcher = butcher;
            this.crack = crack;
            this.stop = stop;
        }
    }

    /** Scalable engraved tab painted directly in the inventory title rail. */
    public static final class TitleButton extends Widget {
        private final Runnable action;
        private final BooleanSupplier active;
        private UI.Grab grab;
        private boolean hover;
        private boolean down;

        public TitleButton(Runnable action, BooleanSupplier active) {
            super(UI.scale(76, 14));
            this.action = action;
            this.active = active;
            settip("Inventory tools");
        }

        @Override
        public void draw(GOut g) {
            MoonFlowerHudTheme.drawInventoryToolTab(g, Coord.z, sz, active.getAsBoolean(), hover, down);
            super.draw(g);
        }

        @Override
        public boolean mousedown(MouseDownEvent event) {
            if(event.b != 1 || ui == null || !event.c.isect(Coord.z, sz))
                return(false);
            down = true;
            grab = ui.grabmouse(this);
            return(true);
        }

        @Override
        public boolean mouseup(MouseUpEvent event) {
            if(event.b != 1 || grab == null)
                return(false);
            boolean activate = down && event.c.isect(Coord.z, sz);
            down = false;
            grab.remove();
            grab = null;
            if(activate)
                action.run();
            return(true);
        }

        @Override
        public void mousemove(MouseMoveEvent event) {
            hover = event.c.isect(Coord.z, sz);
            super.mousemove(event);
        }

        @Override
        public void destroy() {
            if(grab != null) {
                grab.remove();
                grab = null;
            }
            super.destroy();
        }
    }

    private static final class ActionLeaf extends Widget {
        private String label;
        private final Runnable action;
        private UI.Grab grab;
        private boolean hover;
        private boolean down;
        private Tex text;

        ActionLeaf(Coord size, String label, Runnable action) {
            super(size);
            this.action = action;
            setLabel(label);
        }

        void setLabel(String label) {
            if(label.equals(this.label))
                return;
            if(text != null)
                text.dispose();
            this.label = label;
            this.text = Text.render(label, MoonFlowerHudTheme.IVORY).tex();
        }

        @Override
        public void draw(GOut g) {
            MoonFlowerHudTheme.drawLeafButton(g, Coord.z, sz, down, hover);
            g.aimage(text, Coord.of(sz.x / 2, sz.y / 2), 0.5, 0.5);
            super.draw(g);
        }

        @Override
        public boolean mousedown(MouseDownEvent event) {
            if(event.b != 1 || ui == null || !event.c.isect(Coord.z, sz))
                return(false);
            down = true;
            grab = ui.grabmouse(this);
            return(true);
        }

        @Override
        public boolean mouseup(MouseUpEvent event) {
            if(event.b != 1 || grab == null)
                return(false);
            boolean activate = down && event.c.isect(Coord.z, sz);
            down = false;
            grab.remove();
            grab = null;
            if(activate)
                action.run();
            return(true);
        }

        @Override
        public void mousemove(MouseMoveEvent event) {
            hover = event.c.isect(Coord.z, sz);
            super.mousemove(event);
        }

        @Override
        public void destroy() {
            if(grab != null) {
                grab.remove();
                grab = null;
            }
            text.dispose();
            super.destroy();
        }
    }
}
