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

/** Recessed tool bay that expands inside the inventory window's single outer frame. */
public final class InventoryControlPanel extends Widget {
    private static final int ANIM_MS = 190;
    private final Window target;
    private final Inventory inventory;
    private final InventoryBulkActionController controller;
    private final Layout layout;
    private final ActionLeaf lockSlots;
    private final Tex organizeHeading;
    private final Tex processingHeading;
    private boolean expanded;
    private double progress;
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
        this.controller = new InventoryBulkActionController(target.ui == null ? null : target.ui.gui, inventory);
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
        if(expanded) {
            show();
            syncHost(0.0);
        } else {
            controller.stop();
        }
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
            syncHost(0.0);
            hide();
            return;
        }
        show();
        syncHost(visualProgress());
    }

    private Coord baseContentSize() {
        Coord max = Coord.z;
        for(Widget child : target.children()) {
            if(child == target.deco || child == this || !child.visible)
                continue;
            Coord br = child.c.add(child.sz);
            max = Coord.of(Math.max(max.x, br.x), Math.max(max.y, br.y));
        }
        return(max);
    }

    void syncHost(double reveal) {
        Coord base = baseContentSize();
        int width = revealedWidth(layout.panelSize.x, reveal);
        int openHeight = Math.max(base.y, layout.panelSize.y);
        int height = base.y + (int)Math.round((openHeight - base.y) * Utils.clip(reveal, 0.0, 1.0));
        int panelHeight = Math.min(layout.panelSize.y, height);
        move(Coord.of(base.x, Math.max(0, (height - panelHeight) / 2)));
        resize(Coord.of(width, panelHeight));
        target.resize(Coord.of(base.x + width, height));
    }

    static int revealedWidth(int fullWidth, double reveal) {
        return(Math.max(0, Math.min(fullWidth,
                (int)Math.round(fullWidth * Utils.clip(reveal, 0.0, 1.0)))));
    }

    private double visualProgress() {
        if(MoonFlowerHudSettings.hudReducedMotion())
            return(expanded ? 1.0 : 0.0);
        return(Utils.smoothstep(progress));
    }

    @Override
    public void draw(GOut g) {
        double reveal = visualProgress();
        if(reveal <= 0)
            return;
        MoonFlowerHudTheme.drawInventoryToolBay(g, layout.panelSize, reveal);
        g.chcolor(MoonFlowerHudTheme.GOLD_SOFT);
        g.line(Coord.of(layout.contentLeft, layout.organizeRuleY),
                Coord.of(layout.contentRight, layout.organizeRuleY), UI.scale(1));
        g.line(Coord.of(layout.contentLeft, layout.processingRuleY),
                Coord.of(layout.contentRight, layout.processingRuleY), UI.scale(1));
        g.chcolor();
        g.image(organizeHeading, layout.organizeHeading);
        g.image(processingHeading, layout.processingHeading);
        drawStatus(g);
        super.draw(g);
    }

    @Override
    public boolean checkhit(Coord c) {
        return(c.isect(Coord.z, sz));
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
            g.aimage(statusTex, Coord.of(layout.contentCenterX, layout.statusY), 0.5, 1.0);
    }

    @Override
    public void destroy() {
        controller.close();
        organizeHeading.dispose();
        processingHeading.dispose();
        if(statusTex != null)
            statusTex.dispose();
        super.destroy();
    }

    static Layout layout(boolean extended) {
        int width = UI.scale(204);
        int contentLeft = UI.scale(25);
        int contentRight = width - UI.scale(10);
        int gap = UI.scale(5);
        int rowGap = UI.scale(4);
        int rowHeight = UI.scale(19);
        int columnWidth = (contentRight - contentLeft - gap) / 2;
        int organizeRuleY = UI.scale(23);
        int y = UI.scale(28);
        Area sort = Area.sized(Coord.of(contentLeft, y), Coord.of(columnWidth, rowHeight));
        Area stack = Area.sized(Coord.of(contentLeft + columnWidth + gap, y), Coord.of(columnWidth, rowHeight));
        y += rowHeight + rowGap;
        Area unstack = Area.sized(Coord.of(contentLeft, y), Coord.of(columnWidth, rowHeight));
        Area lock = Area.sized(Coord.of(contentLeft + columnWidth + gap, y), Coord.of(columnWidth, rowHeight));
        y += rowHeight + rowGap;
        Area extendedArea = null;
        if(extended) {
            extendedArea = Area.sized(Coord.of(contentLeft, y), Coord.of(contentRight - contentLeft, rowHeight));
        }
        /* Keep the bay stable when Extended view is unavailable on a container. */
        y += rowHeight + rowGap;
        int ruleY = y + UI.scale(2);
        Coord processingHeading = Coord.of(contentLeft, ruleY + UI.scale(5));
        y = ruleY + UI.scale(18);
        Area butcher = Area.sized(Coord.of(contentLeft, y), Coord.of(columnWidth, rowHeight));
        Area crack = Area.sized(Coord.of(contentLeft + columnWidth + gap, y), Coord.of(columnWidth, rowHeight));
        y += rowHeight + rowGap;
        Area stop = Area.sized(Coord.of(contentLeft, y), Coord.of(contentRight - contentLeft, rowHeight));
        int statusY = y + rowHeight + UI.scale(15);
        Coord panelSize = UI.scale(204, 180);
        return(new Layout(panelSize, contentLeft, contentRight, (contentLeft + contentRight) / 2,
                organizeRuleY, Coord.of(contentLeft, UI.scale(8)), processingHeading, ruleY, statusY,
                sort, stack, unstack, lock, extendedArea, butcher, crack, stop));
    }

    static final class Layout {
        final Coord panelSize;
        final int contentLeft;
        final int contentRight;
        final int contentCenterX;
        final int organizeRuleY;
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

        Layout(Coord panelSize, int contentLeft, int contentRight, int contentCenterX, int organizeRuleY,
               Coord organizeHeading, Coord processingHeading, int processingRuleY, int statusY,
               Area sort, Area stack, Area unstack, Area lock, Area extended, Area butcher, Area crack, Area stop) {
            this.panelSize = panelSize;
            this.contentLeft = contentLeft;
            this.contentRight = contentRight;
            this.contentCenterX = contentCenterX;
            this.organizeRuleY = organizeRuleY;
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
            super(UI.scale(20, 18));
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
