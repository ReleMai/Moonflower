package haven;

/** Small control attached to an Alchemist's Table window. */
public final class AlchemyTableInfo extends Widget {
    private static final int WIDTH = 220;
    private static final int HEIGHT = 40;
    private final Window table;

    public AlchemyTableInfo(Window table) {
        super(UI.scale(WIDTH, HEIGHT));
        this.table = table;
        Button bookButton = add(new Button(UI.scale(190), "Alchemy Book") {
            @Override
            public void click() {
                GameUI gui = AlchemyTableInfo.this.table.getparent(GameUI.class);
                if(!AlchemyBookAction.open(gui) && gui != null)
                    gui.error("Alchemy Book is not available in the current action menu yet.");
            }
        }, UI.scale(15, 8));
        bookButton.settip("Open the native Alchemy Book for discovered elixir recipes.");
    }

    @Override
    public void draw(GOut g) {
        if(MoonFlowerHudTheme.active())
            MoonFlowerHudTheme.drawPanel(g, Coord.z, sz, 212);
        super.draw(g);
    }
}
