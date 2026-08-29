package haven.fishing;

import haven.Coord;
import haven.GOut;
import haven.Tabs;
import haven.UI;
import haven.Window;
import haven.automated.FishingBot;

/** One home for autonomous fishing controls and the local fishing journal. */
public final class FishingSystemWindow extends Window {
    private static final Coord HELPER_SIZE = UI.scale(610, 350);
    private static final Coord JOURNAL_SIZE = UI.scale(820, 610);
    private static final long RESIZE_DURATION_MS = 260;
    private final FishingBot helper;
    private final FishingJournalWindow journal;
    private final Tabs tabs;
    private final Tabs.Tab helperTab;
    private final Tabs.Tab journalTab;
    private FishingTabButton helperButton;
    private FishingTabButton journalButton;
    private Coord resizeFrom = JOURNAL_SIZE;
    private Coord resizeTarget = HELPER_SIZE;
    private long resizeStartedAt;

    public FishingSystemWindow(FishingBot helper, FishingJournalWindow journal) {
        super(JOURNAL_SIZE, "Fishing System");
        this.helper = helper;
        this.journal = journal;
        tabs = new Tabs(UI.scale(10, 40), JOURNAL_SIZE.sub(UI.scale(20, 50)), this) {
            @Override
            public void changed(Tab from, Tab to) {
                animateTo(to == helperTab ? HELPER_SIZE : JOURNAL_SIZE);
                updateTabButtons();
            }
        };
        helperTab = tabs.add();
        journalTab = tabs.add();
        helperButton = add(new FishingTabButton(UI.scale(150), "Helper", helperTab), UI.scale(10, 4));
        journalButton = add(new FishingTabButton(UI.scale(150), "Journal", journalTab), UI.scale(165, 4));
        helperTab.add(helper, Coord.z);
        journalTab.add(journal, Coord.z);
        resize(HELPER_SIZE);
        tabs.resize(HELPER_SIZE.sub(UI.scale(20, 50)));
        updateTabButtons();
        reqclose(this::hide);
    }

    private void animateTo(Coord target) {
        if(target == null || target.equals(resizeTarget) && sz.equals(target))
            return;
        resizeFrom = sz;
        resizeTarget = target;
        resizeStartedAt = System.currentTimeMillis();
    }

    private void updateTabButtons() {
        if(helperButton != null)
            helperButton.updateState();
        if(journalButton != null)
            journalButton.updateState();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if(!sz.equals(resizeTarget)) {
            double progress = Math.min(1.0,
                    (System.currentTimeMillis() - resizeStartedAt) / (double)RESIZE_DURATION_MS);
            double eased = progress * progress * (3.0 - 2.0 * progress);
            Coord next = Coord.of(
                    (int)Math.round(resizeFrom.x + (resizeTarget.x - resizeFrom.x) * eased),
                    (int)Math.round(resizeFrom.y + (resizeTarget.y - resizeFrom.y) * eased));
            resize(next);
            tabs.resize(next.sub(UI.scale(20, 50)));
            if(parent != null) {
                Coord limit = parent.sz.sub(sz).max(Coord.z);
                c = Coord.of(Math.max(0, Math.min(c.x, limit.x)),
                        Math.max(0, Math.min(c.y, limit.y)));
            }
        }
    }

    public void showHelper() {
        tabs.showtab(helperTab);
        show();
        raise();
        helper.opened();
    }

    public void showJournal() {
        tabs.showtab(journalTab);
        show();
        raise();
        journal.refresh();
    }

    public void showSpot(FishingMapMarker marker) {
        tabs.showtab(journalTab);
        journal.showSpot(marker);
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
            change(tabs.curtab == tab ? "◆  " + label.toUpperCase() : label);
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
