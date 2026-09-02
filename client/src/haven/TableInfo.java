package haven;

import haven.feasting.FeastingPanel;

public class TableInfo extends Widget {

    private static final int HEADER_HEIGHT = UI.scale(28);
    private static final String LOCKED_PREFERENCE = "feastingHelperLocked";
    private static final String EXPANDED_PREFERENCE = "feastingHelperExpanded";
    private final Window table;
    private final int collapsedWidth;
    private final Button toggleButton;
    private final CheckBox lockButton;
    private FeastingPanel feastingPanel;
    private boolean expanded;
    private boolean locked;

    public static CheckBox preventTablewareFromBreakingCheckBox = new CheckBox("Prevent Tableware from Breaking"){
        {a = Utils.getprefb("preventTablewareFromBreaking", true);}
        public void set(boolean val) {
            OptWnd.preventTablewareFromBreakingCheckBox.set(val);
            a = val;
        }
    };

    public TableInfo(Window table, int width) {
        this.table = table;
        this.locked = Utils.getprefb(LOCKED_PREFERENCE, false);
        this.expanded = locked && Utils.getprefb(EXPANDED_PREFERENCE, true);
        if(!locked)
            this.expanded = true;
        this.collapsedWidth = Math.max(width, UI.scale(500));
        this.sz = new Coord(collapsedWidth, HEADER_HEIGHT);
        add(preventTablewareFromBreakingCheckBox, 10, 0);
        preventTablewareFromBreakingCheckBox.tooltip = OptWnd.preventTablewareFromBreakingCheckBox.tooltip;
        toggleButton = add(new Button(UI.scale(145), "Hide Feasting Helper") {
            @Override
            public void click() {
                setExpanded(!expanded, true);
            }
        }, UI.scale(240), 0);
        lockButton = add(new CheckBox("Lock helper") {
            {
                a = TableInfo.this.locked;
            }

            @Override
            public void set(boolean value) {
                setLocked(value);
            }
        }, UI.scale(395), 0);
        lockButton.settip("Remember whether the Feasting Helper is open or closed for newly opened Tables.");
    }

    @Override
    protected void added() {
        super.added();
        if(feastingPanel == null) {
            feastingPanel = add(new FeastingPanel(ui.gui, table), 0, HEADER_HEIGHT);
            setExpanded(expanded, false);
        }
    }

    private void setExpanded(boolean value, boolean animate) {
        expanded = value;
        if(locked)
            Utils.setprefb(EXPANDED_PREFERENCE, value);
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

    private void setLocked(boolean value) {
        locked = value;
        lockButton.a = value;
        Utils.setprefb(LOCKED_PREFERENCE, value);
        if(value)
            Utils.setprefb(EXPANDED_PREFERENCE, expanded);
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
