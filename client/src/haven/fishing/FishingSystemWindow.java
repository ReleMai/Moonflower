package haven.fishing;

import haven.Coord;
import haven.GOut;
import haven.Tabs;
import haven.UI;
import haven.Window;
import haven.automated.FishingBot;

/** One home for autonomous fishing controls and the local fishing journal. */
public final class FishingSystemWindow extends Window {
    private static final Coord SYSTEM_SIZE = UI.scale(820, 610);
    private static final Coord TAB_SIZE = SYSTEM_SIZE.sub(UI.scale(20, 50));
    private final FishingBot helper;
    private final FishingJournalWindow journal;
    private final Tabs tabs;
    private final Tabs.Tab helperTab;
    private final Tabs.Tab journalTab;
    private FishingTabButton helperButton;
    private FishingTabButton journalButton;

    public FishingSystemWindow(FishingBot helper, FishingJournalWindow journal) {
        super(SYSTEM_SIZE, "Fishing System");
        this.helper = helper;
        this.journal = journal;
        journal.bindHelper(helper);
        tabs = new Tabs(UI.scale(10, 40), TAB_SIZE, this) {
            @Override
            public void changed(Tab from, Tab to) {
                updateTabButtons();
                if(to == helperTab)
                    helper.opened();
                else if(to == journalTab) {
                    journal.setTideglassVisible(haven.MoonFlowerHudTheme.active());
                    journal.refresh();
                }
            }
        };
        helperTab = tabs.add();
        journalTab = tabs.add();
        helperButton = add(new FishingTabButton(UI.scale(150), "Helper", helperTab), UI.scale(10, 4));
        journalButton = add(new FishingTabButton(UI.scale(150), "Fish Guide", journalTab), UI.scale(165, 4));
        Coord helperPosition = Coord.of(Math.max(0, (TAB_SIZE.x - helper.sz.x) / 2),
                Math.max(0, (TAB_SIZE.y - helper.sz.y) / 2));
        helperTab.add(helper, helperPosition);
        journalTab.add(journal, Coord.z);
        updateTabButtons();
        reqclose(this::hide);
    }

    private void updateTabButtons() {
        if(helperButton != null)
            helperButton.updateState();
        if(journalButton != null)
            journalButton.updateState();
    }


    public void showHelper() {
        tabs.showtab(helperTab);
        show();
        raise();
    }

    public void showJournal() {
        tabs.showtab(journalTab);
        show();
        raise();
    }

    public void showSpot(FishingMapMarker marker) {
        tabs.showtab(journalTab);
        journal.showSpot(marker);
        journal.setTideglassVisible(haven.MoonFlowerHudTheme.active());
        show();
        raise();
    }

    public boolean showingHelper() {
        return(visible() && tabs.curtab == helperTab);
    }

    public boolean showingJournal() {
        return(visible() && tabs.curtab == journalTab);
    }

    public FishingJournalWindow journal() {
        return(journal);
    }

    private final class FishingTabButton extends Tabs.TabButton {
        private final String label;

        FishingTabButton(int width, String label, Tabs.Tab tab) {
            tabs.super(width, label, tab);
            this.label = label;
        }

        void updateState() {
            String shown = label;
            change(tabs.curtab == tab ? "*  " + shown.toUpperCase() : shown);
        }

        @Override
        public void draw(GOut g) {
            super.draw(g);
            if(tabs.curtab == tab) {
                g.chcolor(242, 193, 78, 235);
                g.frect(Coord.of(UI.scale(5), sz.y - UI.scale(3)),
                        Coord.of(sz.x - UI.scale(10), UI.scale(3)));
                g.chcolor();
            }
        }
    }
}
