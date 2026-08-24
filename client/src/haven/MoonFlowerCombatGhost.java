package haven;

import java.awt.Color;

/** Edit-mode stand-in for combat widgets so their anchors can be moved safely out of combat. */
public class MoonFlowerCombatGhost extends Widget {
    private enum Target {STATUS, DECK}

    private final GameUI gui;
    private Coord statusOffset = MoonFlowerHudSettings.combatStatusOffset();
    private Coord deckOffset = MoonFlowerHudSettings.combatDeckOffset();
    private Target target;
    private Coord dragStart;
    private Coord offsetStart;
    private UI.Grab dragging;

    public MoonFlowerCombatGhost(GameUI gui) {
        this.gui = gui;
    }

    public void parentResized(Coord size) {
        if(size != null)
            resize(size);
    }

    public void resetLayout() {
        MoonFlowerHudSettings.resetCombatLayout();
        statusOffset = MoonFlowerHudSettings.combatStatusOffset();
        deckOffset = MoonFlowerHudSettings.combatDeckOffset();
    }

    private boolean active() {
        return GameUI.showUI && MoonFlowerHudTheme.active() && MoonFlowerHudSettings.editMode() && gui.fs == null;
    }

    private Area statusArea() {
        return MoonFlowerCombatLayout.statusPreviewArea(sz, statusOffset);
    }

    private Area deckArea() {
        return MoonFlowerCombatLayout.actionDeckArea(sz, deckOffset, 10);
    }

    @Override
    public void draw(GOut g) {
        if(!active())
            return;
        drawStatusGhost(g, statusArea());
        drawDeckGhost(g, deckArea());
        super.draw(g);
    }

    private void drawStatusGhost(GOut g, Area area) {
        MoonFlowerHudTheme.drawCombatStatusRail(g, area.ul, area.sz());
        Coord center = area.ul.add(area.sz().div(2));
        FastText.aprintfstroked(g, center.add(0, -UI.scale(27)), 0.5, 0.5, "COMBAT STATUS - DRAG");
        drawOpeningGhost(g, center.add(-UI.scale(122), UI.scale(8)), new Color(65, 188, 89), "21");
        drawOpeningGhost(g, center.add(-UI.scale(78), UI.scale(8)), new Color(210, 64, 70), "0");
        MoonFlowerHudTheme.drawCircularSlot(g, center.add(0, UI.scale(8)), UI.scale(25), true);
        FastText.aprintfstroked(g, center.add(0, UI.scale(8)), 0.5, 0.5, "1.8");
        drawOpeningGhost(g, center.add(UI.scale(78), UI.scale(8)), new Color(210, 64, 70), "34");
        drawOpeningGhost(g, center.add(UI.scale(122), UI.scale(8)), new Color(65, 188, 89), "36");
    }

    private void drawOpeningGhost(GOut g, Coord center, Color color, String value) {
        MoonFlowerHudTheme.drawCircularSlot(g, center, UI.scale(17), false);
        g.chcolor(color);
        g.fellipse(center, UI.scale(11, 11));
        g.chcolor();
        FastText.aprintfstroked(g, center, 0.5, 0.5, "%s", value);
    }

    private void drawDeckGhost(GOut g, Area area) {
        MoonFlowerHudTheme.drawCombatActionDeck(g, area.ul, area.sz());
        FastText.aprintfstroked(g, area.ul.add(area.sz().x / 2, UI.scale(12)), 0.5, 0.5,
                "COMBAT DECK - DRAG");
        int columns = OptWnd.singleRowCombatMovesCheckBox != null && OptWnd.singleRowCombatMovesCheckBox.a ? 10 : 5;
        Coord anchor = MoonFlowerCombatLayout.deckAnchor(sz, deckOffset);
        for(int i = 0; i < 10; i++) {
            Coord slot = anchor.add(-UI.scale(16), -UI.scale(150)).add(MoonFlowerCombatLayout.actionCoord(i));
            Coord size = UI.scale(38, 38);
            MoonFlowerHudTheme.drawCombatActionSlot(g, slot.sub(UI.scale(3), UI.scale(3)), size,
                    i == 0, i == columns);
            FastText.aprintfstroked(g, slot.add(UI.scale(16), UI.scale(16)), 0.5, 0.5,
                    "%s", i < 5 ? Integer.toString(i + 1) : "S" + (i - 4));
        }
    }

    @Override
    public boolean checkhit(Coord c) {
        return active() && (c.isect(statusArea().ul, statusArea().sz()) || c.isect(deckArea().ul, deckArea().sz()));
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if(ev.b != 1 || !active())
            return false;
        Area status = statusArea();
        Area deck = deckArea();
        if(ev.c.isect(status.ul, status.sz())) {
            target = Target.STATUS;
            offsetStart = statusOffset;
        } else if(ev.c.isect(deck.ul, deck.sz())) {
            target = Target.DECK;
            offsetStart = deckOffset;
        } else {
            return false;
        }
        dragStart = ev.c;
        dragging = ui.grabmouse(this);
        return true;
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        if(dragging == null || target == null) {
            super.mousemove(ev);
            return;
        }
        Coord proposed = offsetStart.add(ev.c.sub(dragStart));
        if(target == Target.STATUS) {
            Area base = MoonFlowerCombatLayout.statusPreviewArea(sz, Coord.z);
            statusOffset = MoonFlowerCombatLayout.clampOffset(sz, base, proposed);
        } else {
            Area base = MoonFlowerCombatLayout.actionDeckArea(sz, Coord.z, 10);
            deckOffset = MoonFlowerCombatLayout.clampOffset(sz, base, proposed);
        }
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        if(ev.b != 1 || dragging == null)
            return super.mouseup(ev);
        dragging.remove();
        dragging = null;
        if(target == Target.STATUS)
            MoonFlowerHudSettings.setCombatStatusOffset(statusOffset);
        else if(target == Target.DECK)
            MoonFlowerHudSettings.setCombatDeckOffset(deckOffset);
        target = null;
        return true;
    }

    @Override
    public void destroy() {
        if(dragging != null)
            dragging.remove();
        super.destroy();
    }
}
