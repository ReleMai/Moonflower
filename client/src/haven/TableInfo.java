package haven;

import haven.feasting.FeastingPanel;

public class TableInfo extends Widget {

    private static final int HEADER_HEIGHT = UI.scale(28);
    private final Window table;
    private final int collapsedWidth;
    private final Button toggleButton;
    private FeastingPanel feastingPanel;
    private boolean expanded = true;

    public static CheckBox preventTablewareFromBreakingCheckBox = new CheckBox("Prevent Tableware from Breaking"){
        {a = Utils.getprefb("preventTablewareFromBreaking", true);}
        public void set(boolean val) {
            OptWnd.preventTablewareFromBreakingCheckBox.set(val);
            a = val;
        }
    };

    public TableInfo(Window table, int width) {
        this.table = table;
        this.collapsedWidth = Math.max(width, UI.scale(390));
        this.sz = new Coord(collapsedWidth, HEADER_HEIGHT);
        add(preventTablewareFromBreakingCheckBox, 10, 0);
        preventTablewareFromBreakingCheckBox.tooltip = OptWnd.preventTablewareFromBreakingCheckBox.tooltip;
        toggleButton = add(new Button(UI.scale(145), "Hide Feasting Helper") {
            @Override
            public void click() {
                setExpanded(!expanded, true);
            }
        }, UI.scale(240), 0);
    }

    @Override
    protected void added() {
        super.added();
        if(feastingPanel == null) {
            feastingPanel = add(new FeastingPanel(ui.gui, table), 0, HEADER_HEIGHT);
            setExpanded(true, false);
        }
    }

    private void setExpanded(boolean value, boolean animate) {
        expanded = value;
        toggleButton.change(value ? "Hide Feasting Helper" : "Show Feasting Helper");
        Coord from = sz;
        Coord to = value ? UI.scale(FeastingPanel.CONTENT_WIDTH,
                FeastingPanel.CONTENT_HEIGHT).add(0, HEADER_HEIGHT) :
                Coord.of(collapsedWidth, HEADER_HEIGHT);
        clearanims(NormAnim.class);
        if(!animate) {
            resize(to);
            table.pack();
            return;
        }
        new NormAnim(0.18) {
            @Override
            public void ntick(double progress) {
                double eased = 1d - Math.pow(1d - progress, 3d);
                resize(from.add(to.sub(from).mul(eased)));
                table.pack();
            }
        };
    }

    @Override
    public boolean keydown(KeyDownEvent event) {
        if(key_esc.match(event) && feastingPanel != null && feastingPanel.active()) {
            feastingPanel.stop("Feasting Helper stopped by Escape.");
            return(true);
        }
        return(super.keydown(event));
    }
}
