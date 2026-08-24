package haven;

/** One-time, in-client choice between the classic and MoonFlower HUDs. */
public final class MoonFlowerHudChoiceWindow extends Window {
    private final GameUI gui;
    private boolean decided;

    public MoonFlowerHudChoiceWindow(GameUI gui) {
        super(Coord.z, "Choose Your In-Game UI", true);
        this.gui = gui;

        Widget previous = add(new Label("MoonFlower now includes a custom in-game HUD."), UI.scale(18, 8));
        previous = add(new Label("The new layout moves your portrait and vitals to a lower-right hub"),
                previous.pos("bl").adds(0, 6).xs(18));
        previous = add(new Label("with surrounding buttons, scalable action bars, and new chat controls."),
                previous.pos("bl").adds(0, 3).xs(18));
        previous = add(new Label("Which interface would you like to use?"),
                previous.pos("bl").adds(0, 12).xs(18));

        Button useMoonFlower = new Button(UI.scale(190), "Use New MoonFlower UI", () -> choose(true));
        Button keepClassic = new Button(UI.scale(160), "Keep Classic UI", () -> choose(false));
        add(useMoonFlower, previous.pos("bl").adds(0, 14).xs(18));
        add(keepClassic, useMoonFlower.pos("ur").adds(12, 0));
        pack();
        reqclose(() -> choose(false));
    }

    private void choose(boolean useMoonFlower) {
        if(decided)
            return;
        decided = true;
        gui.completeMoonFlowerHudChoice(useMoonFlower);
    }

    @Override
    public void drag(Coord offset) {
        // Keep this one-time choice centered and hard to lose behind other windows.
    }
}
