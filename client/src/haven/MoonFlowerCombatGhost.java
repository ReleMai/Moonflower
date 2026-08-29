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
        return MoonFlowerCombatLayout.statusPreviewArea(gui, statusOffset, 10);
    }

    private Area deckArea() {
        return MoonFlowerCombatLayout.actionDeckArea(gui, deckOffset, 10);
    }

    @Override
    public void draw(GOut g) {
        if(!active())
            return;
        drawIntegratedPreview(g);
        super.draw(g);
    }

    private void drawIntegratedPreview(GOut g) {
        if(gui.moonFlowerHud == null)
            return;
        gui.moonFlowerHud.drawCombatCrown(g);
        g = gui.moonFlowerHud.combatClip(g);
        Area health = gui.moonFlowerHud.combatHealthArea();
        MoonFlowerHudTheme.drawOpponentHealthPlate(g, health.ul, health.sz(), 0.72,
                "OPPONENT - 72% - PORTRAIT COMBAT PREVIEW");
        String[] resources = {"paginae/atk/cornered", "paginae/atk/offbalance",
                "paginae/atk/dizzy", "paginae/atk/reeling"};
        String[] values = {"34", "36", "18", "21"};
        for(int i = 0; i < resources.length; i++) {
            Coord player = gui.moonFlowerHud.combatOpeningCenter(resources[i], false);
            Coord opponent = gui.moonFlowerHud.combatOpeningCenter(resources[i], true);
            if(player != null)
                FastText.aprintfstroked(g, player, 0.5, 0.5, "%s", values[i]);
            if(opponent != null)
                FastText.aprintfstroked(g, opponent, 0.5, 0.5, "%s",
                        Integer.toString(Math.max(0, Integer.parseInt(values[i]) - 9)));
        }
        for(int i = 0; i < 10; i++)
            FastText.aprintfstroked(g, gui.moonFlowerHud.combatActionCenter(i), 0.5, 0.5,
                    "%s", i < 5 ? Integer.toString(i + 1) : "S" + (i - 4));
        FastText.aprintfstroked(g, gui.moonFlowerHud.combatMoveCenter(false), 0.5, 0.5, "YOU");
        FastText.aprintfstroked(g, gui.moonFlowerHud.combatMoveCenter(true), 0.5, 0.5, "FOE");
        FastText.aprintfstroked(g, gui.moonFlowerHud.combatDefenseCenter(false), 0.5, 0.5, "DEF");
        FastText.aprintfstroked(g, gui.moonFlowerHud.combatDefenseCenter(true), 0.5, 0.5, "DEF");
        FastText.aprintfstroked(g, gui.moonFlowerHud.combatInitiativeCenter(false), 0.5, 0.5, "4");
        FastText.aprintfstroked(g, gui.moonFlowerHud.combatInitiativeCenter(true), 0.5, 0.5, "7");
        FastText.aprintfstroked(g, gui.moonFlowerHud.combatCooldownCenter(), 0.5, 0.5, "1.8");
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
        Coord anchor = MoonFlowerCombatLayout.deckAnchor(gui, deckOffset, 10);
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
        return false;
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        return false;
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        if(dragging == null || target == null) {
            super.mousemove(ev);
            return;
        }
        Coord proposed = offsetStart.add(ev.c.sub(dragStart));
        if(target == Target.STATUS) {
            Area base = MoonFlowerCombatLayout.statusPreviewArea(gui, Coord.z, 10);
            statusOffset = MoonFlowerCombatLayout.clampOffset(sz, base, proposed);
        } else {
            Area base = MoonFlowerCombatLayout.actionDeckArea(gui, Coord.z, 10);
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
