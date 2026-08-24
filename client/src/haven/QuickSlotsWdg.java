package haven;

import static haven.Equipory.etts;
import static haven.Inventory.sqsz;

public class QuickSlotsWdg extends Widget implements DTarget {
    private static final Tex sbg = Resource.loadtex("gfx/hud/quickslots");
    public Coord leftHandSlotCoord, rightHandSlotCoord, leftPouchSlotCoord, rightPouchSlotCoord, beltSlotCoord, backpackSlotCoord, capeSlotCoord, shouldersSlotCoord = null;
    public static final int slotSpacing = UI.scale(2);

    public static final Tex slotSquareBg = Inventory.invsq;
    public static final Tex leftHandBg = Resource.loadtex("gfx/hud/equip/ep6");
    public static final Tex rightHandBg = Resource.loadtex("gfx/hud/equip/ep7");
    public static final Tex leftPouchBg = Resource.loadtex("gfx/hud/equip/ep19");
    public static final Tex rightPouchBg = Resource.loadtex("gfx/hud/equip/ep20");
    public static final Tex beltBg = Resource.loadtex("gfx/hud/equip/ep5");
    public static final Tex backpackBg = Resource.loadtex("gfx/hud/equip/ep11");
    public static final Tex capeBg = Resource.loadtex("gfx/hud/equip/ep14");
    public static final Tex shouldersBg = Resource.loadtex("gfx/hud/equip/ep22");
    public int shownSlots = 0;

    private UI.Grab dragging;
    private Coord dc;
    private boolean portraitIntegrated = false;
    private double rolloutReveal = 1.0;

    public QuickSlotsWdg() {
        super(UI.scale(new Coord(0, 0)));
        reloadSlots();
    }

    public void setPortraitIntegrated(boolean integrated) {
        portraitIntegrated = integrated;
        rolloutReveal = integrated ? 0.0 : 1.0;
        reloadSlots();
    }

    public void setPortraitRollout(double reveal) {
        reveal = Utils.clip(reveal, 0.0, 1.0);
        if(portraitIntegrated && Math.abs(rolloutReveal - reveal) < 0.0001)
            return;
        portraitIntegrated = true;
        rolloutReveal = reveal;
        reloadSlots();
    }

    @Override
    public void draw(GOut g) {
        if (!GameUI.showUI)
            return;
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            if(portraitIntegrated && shownSlots > 1) {
                Coord previous = null;
                for(Coord slot : slotCoords()) {
                    Coord center = slot.add(slotSquareBg.sz().div(2));
                    if(previous != null)
                        MoonFlowerHudTheme.drawCurvedVine(g, previous, center, rolloutReveal);
                    previous = center;
                }
            }
            if (shownSlots > 0) {
                for (Coord slotLocation : slotCoords()) {
                    if(portraitIntegrated)
                        MoonFlowerHudTheme.drawCircularSlot(g, slotLocation.add(slotSquareBg.sz().div(2)),
                                (Math.min(slotSquareBg.sz().x, slotSquareBg.sz().y) / 2) - UI.scale(1), false);
                    else if(MoonFlowerHudTheme.active())
                        MoonFlowerHudTheme.drawSlot(g, slotLocation, slotSquareBg.sz(), false, false);
                    else
                        g.image(slotSquareBg, slotLocation);
                }
            }

            if (leftHandSlotCoord != null){
                drawEquipmentHint(g, leftHandBg, leftHandSlotCoord);
                if (e.slots[6] != null)
                    e.slots[6].draw(g.reclipl(leftHandSlotCoord, slotSquareBg.sz()));
            }
            if (rightHandSlotCoord != null){
                drawEquipmentHint(g, rightHandBg, rightHandSlotCoord);
                if (e.slots[7] != null)
                    e.slots[7].draw(g.reclipl(rightHandSlotCoord, slotSquareBg.sz()));
            }

            if (leftPouchSlotCoord != null) {
                drawEquipmentHint(g, leftPouchBg, leftPouchSlotCoord);
                if (e.slots[19] != null)
                    e.slots[19].draw(g.reclipl(leftPouchSlotCoord, slotSquareBg.sz()));
            }

            if (rightPouchSlotCoord != null) {
                drawEquipmentHint(g, rightPouchBg, rightPouchSlotCoord);
                if (e.slots[20] != null)
                    e.slots[20].draw(g.reclipl(rightPouchSlotCoord, slotSquareBg.sz()));
            }

            if (beltSlotCoord != null) {
                drawEquipmentHint(g, beltBg, beltSlotCoord);
                if (e.slots[5] != null)
                    e.slots[5].draw(g.reclipl(beltSlotCoord, slotSquareBg.sz()));
            }

            if (backpackSlotCoord != null) {
                drawEquipmentHint(g, backpackBg, backpackSlotCoord);
                if (e.slots[11] != null)
                    e.slots[11].draw(g.reclipl(backpackSlotCoord, slotSquareBg.sz()));
            }

            if (shouldersSlotCoord != null) {
                drawEquipmentHint(g, shouldersBg, shouldersSlotCoord);
                if (e.slots[22] != null)
                    e.slots[22].draw(g.reclipl(shouldersSlotCoord, slotSquareBg.sz()));
            }

            if (capeSlotCoord != null) {
                drawEquipmentHint(g, capeBg, capeSlotCoord);
                if (e.slots[14] != null)
                    e.slots[14].draw(g.reclipl(capeSlotCoord, slotSquareBg.sz()));
            }

        }
    }


    @Override
    public boolean drop(Coord cc, Coord ul) {
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            if (leftHandSlotCoord != null && cc.x > leftHandSlotCoord.x && cc.x <= leftHandSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 6);
            if (rightHandSlotCoord != null && cc.x > rightHandSlotCoord.x && cc.x <= rightHandSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 7);
            if (leftPouchSlotCoord != null && cc.x > leftPouchSlotCoord.x && cc.x <= leftPouchSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 19);
            if (rightPouchSlotCoord != null && cc.x > rightPouchSlotCoord.x && cc.x <= rightPouchSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 20);
            if (beltSlotCoord != null && cc.x > beltSlotCoord.x && cc.x <= beltSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 5);
            if (backpackSlotCoord != null && cc.x > backpackSlotCoord.x && cc.x <= backpackSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 11);
            if (shouldersSlotCoord != null && cc.x > shouldersSlotCoord.x && cc.x <= shouldersSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 22);
            if (capeSlotCoord != null && cc.x > capeSlotCoord.x && cc.x <= capeSlotCoord.x + slotSquareBg.sz().x) e.wdgmsg("drop", 14);
            return true;
        }
        return false;
    }

    @Override
    public boolean iteminteract(Coord cc, Coord ul) {
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            WItem w = null;
            if (leftHandSlotCoord != null && cc.x > leftHandSlotCoord.x && cc.x <= leftHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[6];
            if (rightHandSlotCoord != null && cc.x > rightHandSlotCoord.x && cc.x <= rightHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[7];
            if (leftPouchSlotCoord != null && cc.x > leftPouchSlotCoord.x && cc.x <= leftPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[19];
            if (rightPouchSlotCoord != null && cc.x > rightPouchSlotCoord.x && cc.x <= rightPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[20];
            if (beltSlotCoord != null && cc.x > beltSlotCoord.x && cc.x <= beltSlotCoord.x + slotSquareBg.sz().x) w = e.slots[5];
            if (backpackSlotCoord != null && cc.x > backpackSlotCoord.x && cc.x <= backpackSlotCoord.x + slotSquareBg.sz().x) w = e.slots[11];
            if (shouldersSlotCoord != null && cc.x > shouldersSlotCoord.x && cc.x <= shouldersSlotCoord.x + slotSquareBg.sz().x) w = e.slots[22];
            if (capeSlotCoord != null && cc.x > capeSlotCoord.x && cc.x <= capeSlotCoord.x + slotSquareBg.sz().x) w = e.slots[14];
            if (w != null) {
                return w.iteminteract(cc, ul);
            }
        }
        return false;
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        Equipory e = ui.gui.getequipory();
        if(e != null) {
            WItem w = null;
            if (leftHandSlotCoord != null && c.x > leftHandSlotCoord.x && c.x <= leftHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[6];
            if (rightHandSlotCoord != null && c.x > rightHandSlotCoord.x && c.x <= rightHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[7];
            if (leftPouchSlotCoord != null && c.x > leftPouchSlotCoord.x && c.x <= leftPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[19];
            if (rightPouchSlotCoord != null && c.x > rightPouchSlotCoord.x && c.x <= rightPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[20];
            if (beltSlotCoord != null && c.x > beltSlotCoord.x && c.x <= beltSlotCoord.x + slotSquareBg.sz().x) w = e.slots[5];
            if (backpackSlotCoord != null && c.x > backpackSlotCoord.x && c.x <= backpackSlotCoord.x + slotSquareBg.sz().x) w = e.slots[11];
            if (shouldersSlotCoord != null && c.x > shouldersSlotCoord.x && c.x <= shouldersSlotCoord.x + slotSquareBg.sz().x) w = e.slots[22];
            if (capeSlotCoord != null && c.x > capeSlotCoord.x && c.x <= capeSlotCoord.x + slotSquareBg.sz().x) w = e.slots[14];
            if(w != null) {
                return w.tooltip(c, (prev == this) ? w : prev);
            } else {
                return null;
            }
        }
        return super.tooltip(c, prev);
    }

    @Override
    public boolean mousehover(MouseHoverEvent ev, boolean on) {
        Coord c = ev.c;
        Equipory e = ui.gui.getequipory();
        if(e != null) {
            WItem w = null;
            Coord hoverCoord = null;
            if (leftHandSlotCoord != null && c.x > leftHandSlotCoord.x && c.x <= leftHandSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(leftHandSlotCoord.x + slotSquareBg.sz().x, leftHandSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[6];
            }
            if (rightHandSlotCoord != null && c.x > rightHandSlotCoord.x && c.x <= rightHandSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(rightHandSlotCoord.x + slotSquareBg.sz().x, rightHandSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[7];
            }
            if (leftPouchSlotCoord != null && c.x > leftPouchSlotCoord.x && c.x <= leftPouchSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(leftPouchSlotCoord.x + slotSquareBg.sz().x, leftPouchSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[19];
            }
            if (rightPouchSlotCoord != null && c.x > rightPouchSlotCoord.x && c.x <= rightPouchSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(rightPouchSlotCoord.x + slotSquareBg.sz().x, rightPouchSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[20];
            }
            if (beltSlotCoord != null && c.x > beltSlotCoord.x && c.x <= beltSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(beltSlotCoord.x + slotSquareBg.sz().x, beltSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[5];
            }
            if (backpackSlotCoord != null && c.x > backpackSlotCoord.x && c.x <= backpackSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(backpackSlotCoord.x + slotSquareBg.sz().x, backpackSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[11];
            }
            if (shouldersSlotCoord != null && c.x > shouldersSlotCoord.x && c.x <= shouldersSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(shouldersSlotCoord.x + slotSquareBg.sz().x, shouldersSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[22];
            }
            if (capeSlotCoord != null && c.x > capeSlotCoord.x && c.x <= capeSlotCoord.x + slotSquareBg.sz().x){
                hoverCoord = new Coord(capeSlotCoord.x + slotSquareBg.sz().x, capeSlotCoord.y + slotSquareBg.sz().y);
                w = e.slots[14];
            }
            if(w != null) {
                if(on && (w.item.contents != null && (!OptWnd.showHoverInventoriesWhenHoldingShiftCheckBox.a || ui.modshift))) {
                    Widget hover = new Widget();
                    QuickSlotsWdg.this.add(hover, hoverCoord);
                    w.item.hovering(hover);
                    return(true);
                }
                return w.mousehover(ev, on);
            }
        }
        return(false);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if (!GameUI.showUI)
            return false;
        if (ui.modmeta || ui.modctrl)
            return true;
        if (ev.b == 2 && !portraitIntegrated) {
            if((dragging != null)) { // ND: I need to do this extra check and remove it in case you do another click before the mouseup. Idk why it has to be done like this, but it solves the issue.
                dragging.remove();
                dragging = null;
            }
            dragging = ui.grabmouse(this);
            dc = ev.c;
            return true;
        }
        Equipory e = ui.gui.getequipory();
        if (e != null) {
            WItem w = null;
            if (leftHandSlotCoord != null && ev.c.x > leftHandSlotCoord.x && ev.c.x <= leftHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[6];
            if (rightHandSlotCoord != null && ev.c.x > rightHandSlotCoord.x && ev.c.x <= rightHandSlotCoord.x + slotSquareBg.sz().x) w = e.slots[7];
            if (leftPouchSlotCoord != null && ev.c.x > leftPouchSlotCoord.x && ev.c.x <= leftPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[19];
            if (rightPouchSlotCoord != null && ev.c.x > rightPouchSlotCoord.x && ev.c.x <= rightPouchSlotCoord.x + slotSquareBg.sz().x) w = e.slots[20];
            if (beltSlotCoord != null && ev.c.x > beltSlotCoord.x && ev.c.x <= beltSlotCoord.x + slotSquareBg.sz().x) w = e.slots[5];
            if (backpackSlotCoord != null && ev.c.x > backpackSlotCoord.x && ev.c.x <= backpackSlotCoord.x + slotSquareBg.sz().x) w = e.slots[11];
            if (shouldersSlotCoord != null && ev.c.x > shouldersSlotCoord.x && ev.c.x <= shouldersSlotCoord.x + slotSquareBg.sz().x) w = e.slots[22];
            if (capeSlotCoord != null && ev.c.x > capeSlotCoord.x && ev.c.x <= capeSlotCoord.x + slotSquareBg.sz().x) w = e.slots[14];
            if (w != null) {
                w.mousedown(new MouseDownEvent(new Coord(w.sz.x / 2, w.sz.y / 2), ev.b));
                return true;
            } else if (ev.b == 1 && !portraitIntegrated) {
                if((dragging != null)) { // ND: I need to do this extra check and remove it in case you do another click before the mouseup. Idk why it has to be done like this, but it solves the issue.
                    dragging.remove();
                    dragging = null;
                }
                dragging = ui.grabmouse(this);
                dc = ev.c;
                return true;
            }
        }
        return (super.mousedown(ev));
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        checkIfOutsideOfUI(); // ND: Prevent the widget from being dragged outside the current window size
        if((dragging != null)) {
            dragging.remove();
            dragging = null;
            Utils.setprefc("wndc-quickslots", this.c);
            return true;
        }
        return super.mouseup(ev);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        if (dragging != null) {
            this.c = this.c.add(ev.c.x, ev.c.y).sub(dc);
            return;
        }
        super.mousemove(ev);
    }

    public void checkIfOutsideOfUI() {
        if (this.c.x < 0)
            this.c.x = 0;
        if (this.c.y < 0)
            this.c.y = 0;
        if (this.c.x > (ui.gui.sz.x - this.sz.x))
            this.c.x = ui.gui.sz.x - this.sz.x;
        if (this.c.y > (ui.gui.sz.y - this.sz.y))
            this.c.y = ui.gui.sz.y - this.sz.y;
    }

    public void reloadSlots(){
        shownSlots = 0;
        leftHandSlotCoord = setSlotCoord(OptWnd.leftHandQuickSlotCheckBox.a);
        rightHandSlotCoord = setSlotCoord(OptWnd.rightHandQuickSlotCheckBox.a);
        leftPouchSlotCoord = setSlotCoord(OptWnd.leftPouchQuickSlotCheckBox.a);
        rightPouchSlotCoord = setSlotCoord(OptWnd.rightPouchQuickSlotCheckBox.a);
        beltSlotCoord = setSlotCoord(OptWnd.beltQuickSlotCheckBox.a);
        backpackSlotCoord = setSlotCoord(OptWnd.backpackQuickSlotCheckBox.a);
        shouldersSlotCoord = setSlotCoord(OptWnd.shouldersQuickSlotCheckBox.a);
        capeSlotCoord = setSlotCoord(OptWnd.capeQuickSlotCheckBox.a);
        if (shownSlots > 0) {
            int pitch = (int)Math.round((slotSquareBg.sz().x + slotSpacing) *
                    (portraitIntegrated ? rolloutReveal : 1.0));
            this.sz = new Coord(slotSquareBg.sz().x + (pitch * (shownSlots - 1)), slotSquareBg.sz().y);
        } else {
            this.sz = new Coord(0, 0);
        }

    }

    private Coord setSlotCoord(boolean isEnabled) {
        if (isEnabled) {
            int pitch = (int)Math.round((slotSquareBg.sz().x + slotSpacing) *
                    (portraitIntegrated ? rolloutReveal : 1.0));
            Coord coord = new Coord(pitch * shownSlots, 0);
            shownSlots++;
            return coord;
        } else {
            return null;
        }
    }

    private void drawEquipmentHint(GOut g, Tex hint, Coord origin) {
        if(!portraitIntegrated) {
            g.image(hint, origin);
            return;
        }
        int inset = UI.scale(6);
        g.image(hint, origin.add(inset, inset), slotSquareBg.sz().sub(inset * 2, inset * 2));
    }

    private Coord[] slotCoords() {
        Coord[] all = {leftHandSlotCoord, rightHandSlotCoord, leftPouchSlotCoord, rightPouchSlotCoord,
                beltSlotCoord, backpackSlotCoord, shouldersSlotCoord, capeSlotCoord};
        Coord[] visible = new Coord[shownSlots];
        int index = 0;
        for(Coord coord : all) {
            if(coord != null)
                visible[index++] = coord;
        }
        return visible;
    }

}
