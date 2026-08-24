package haven;

/** Shared combat anchors used by the live session and its edit-mode ghost. */
public final class MoonFlowerCombatLayout {
    private MoonFlowerCombatLayout() {
    }

    public static int statusSliderValue() {
        return OptWnd.combatUITopPanelHeightSlider == null ? 400 : OptWnd.combatUITopPanelHeightSlider.val;
    }

    public static int deckSliderValue() {
        return OptWnd.combatUIBottomPanelHeightSlider == null ? 100 : OptWnd.combatUIBottomPanelHeightSlider.val;
    }

    public static Coord statusCenter(Coord screen, Coord offset) {
        int y = (int)(screen.y - ((screen.y / 500.0) * statusSliderValue()));
        return Coord.of(screen.x / 2, y).add(offset);
    }

    public static Coord deckAnchor(Coord screen, Coord offset) {
        int bottom = (int)(screen.y - ((screen.y / 500.0) * deckSliderValue()));
        return Coord.of(screen.x / 2, bottom).add(offset);
    }

    public static Coord actionCoord(int index) {
        int columns = OptWnd.singleRowCombatMovesCheckBox != null && OptWnd.singleRowCombatMovesCheckBox.a ? 10 : 5;
        return Coord.of((Fightsess.actpitch * (index % columns)) - (((columns - 1) * Fightsess.actpitch) / 2),
                UI.scale(125) + ((index / columns) * Fightsess.actpitch2));
    }

    public static Area actionDeckArea(Coord screen, Coord offset, int actionCount) {
        int available = Math.max(1, actionCount);
        int maximumColumns = OptWnd.singleRowCombatMovesCheckBox != null && OptWnd.singleRowCombatMovesCheckBox.a ? 10 : 5;
        int columns = Math.min(available, maximumColumns);
        int rows = (available + columns - 1) / columns;
        Coord anchor = deckAnchor(screen, offset);
        Coord first = anchor.add(-UI.scale(16), -UI.scale(150)).add(actionCoord(0));
        Coord origin = first.sub(UI.scale(13), UI.scale(25));
        Coord size = Coord.of(((columns - 1) * Fightsess.actpitch) + UI.scale(58),
                ((rows - 1) * Fightsess.actpitch2) + UI.scale(84));
        return Area.sized(origin, size);
    }

    public static Area statusPreviewArea(Coord screen, Coord offset) {
        Coord size = UI.scale(430, 82);
        return Area.sized(statusCenter(screen, offset).sub(size.div(2)), size);
    }

    public static Coord clampOffset(Coord screen, Area baseArea, Coord proposedOffset) {
        Area moved = new Area(baseArea.ul.add(proposedOffset), baseArea.br.add(proposedOffset));
        int dx = 0;
        int dy = 0;
        if(moved.ul.x < 0)
            dx = -moved.ul.x;
        else if(moved.br.x > screen.x)
            dx = screen.x - moved.br.x;
        if(moved.ul.y < 0)
            dy = -moved.ul.y;
        else if(moved.br.y > screen.y)
            dy = screen.y - moved.br.y;
        return proposedOffset.add(dx, dy);
    }
}
