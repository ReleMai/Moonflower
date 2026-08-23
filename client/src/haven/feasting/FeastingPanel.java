package haven.feasting;

import haven.Button;
import haven.CharWnd;
import haven.CheckBox;
import haven.Coord;
import haven.GOut;
import haven.GameUI;
import haven.Indir;
import haven.Label;
import haven.Loading;
import haven.PUtils;
import haven.Resource;
import haven.RichText;
import haven.SDropBox;
import haven.SListBox;
import haven.SListWidget;
import haven.Text;
import haven.UI;
import haven.Widget;
import haven.Window;
import haven.cookbook.CookbookAttribute;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Native Table-attached presentation for planning and supervised auto-eating. */
public final class FeastingPanel extends Widget {
    public static final int CONTENT_WIDTH = 720;
    public static final int CONTENT_HEIGHT = 338;
    private static final long REFRESH_INTERVAL_MS = 250;
    private static final long CONFIRMATION_WINDOW_MS = 8000;
    private static final Text.Foundry ROW_TEXT =
            new Text.Foundry(Text.sans, 10, Color.WHITE).aa(true);
    private static final Text.Foundry ROW_MUTED =
            new Text.Foundry(Text.sans, 10, new Color(170, 190, 205)).aa(true);
    private final GameUI gui;
    private final Window table;
    private final FeastingPlanner planner = new FeastingPlanner();
    private final FeastingController controller;
    private final AttributeDropBox attributeDropBox;
    private final CheckBox balancedTarget;
    private final CheckBox inventoryOnly;
    private final CheckBox allowBreakage;
    private final Label fepSummary;
    private final Label balanceSummary;
    private final Label status;
    private final Button startButton;
    private final Button stopButton;
    private final PlanList planList;

    private FeastingSnapshot snapshot;
    private FeastingPlan plan;
    private long nextRefresh;
    private long confirmationDeadline;
    private String confirmationFingerprint;
    private String confirmationMessage;
    private boolean displayedActive;
    private String displayedStatus = "";

    public FeastingPanel(GameUI gui, Window table) {
        super(UI.scale(CONTENT_WIDTH, CONTENT_HEIGHT));
        this.gui = gui;
        this.table = table;
        this.controller = new FeastingController(gui, table);

        add(new Label("Target:"), UI.scale(12, 13));
        attributeDropBox = add(new AttributeDropBox(), UI.scale(62, 6));
        balancedTarget = add(new CheckBox("Balanced target") {
            @Override
            public void set(boolean value) {
                if(controller.active())
                    return;
                a = value;
                controller.stop("Ready.");
                clearConfirmation();
                refreshNow();
            }
        }, UI.scale(242, 11));
        fepSummary = add(new Label("Reading live FEP meter..."), UI.scale(390, 13));
        fepSummary.tooltip = RichText.render("Current chance is read from the character sheet: " +
                "current selected FEP / current total FEP. Projected chance includes the " +
                "recommended uneaten foods.", UI.scale(360));

        balanceSummary = add(new Label("Reading base attributes...", ROW_MUTED), UI.scale(12, 43));

        allowBreakage = add(new CheckBox("Fast auto-eat - allow symbel breakage",
                new Color(255, 170, 95)) {
            @Override
            public void set(boolean value) {
                if(controller.active())
                    return;
                a = value;
                clearConfirmation();
            }
        }, UI.scale(12, 68));
        allowBreakage.tooltip = RichText.render("Unchecked is safe mode. When enabled, helper-driven " +
                "bites may break and permanently lose tableware. Manual eating keeps the global " +
                "tableware-protection preference.", UI.scale(350));

        inventoryOnly = add(new CheckBox("Inventory only") {
            @Override
            public void set(boolean value) {
                if(controller.active())
                    return;
                a = value;
                controller.stop("Ready.");
                clearConfirmation();
                refreshNow();
            }
        }, UI.scale(270, 68));
        inventoryOnly.tooltip = RichText.render("When checked, recommendations and fast auto-eating " +
                "exclude food placed on the Table and use only the main inventory. Unchecked uses " +
                "both the Table and main inventory.", UI.scale(350));

        add(new Button(UI.scale(85), "Recalculate") {
            @Override
            public void click() {
                clearConfirmation();
                refreshNow();
            }
        }, UI.scale(390, 64));
        startButton = add(new Button(UI.scale(95), "Start") {
            @Override
            public void click() {
                startRequested();
            }
        }, UI.scale(485, 64));
        stopButton = add(new Button(UI.scale(85), "Stop") {
            @Override
            public void click() {
                controller.stop("Feasting Helper stopped by the player.");
                clearConfirmation();
            }
        }, UI.scale(590, 64));
        stopButton.disable(true);

        status = add(new Label("Reading reachable food...", UI.scale(690), ROW_MUTED),
                UI.scale(12, 96));

        add(new Label("Food"), UI.scale(15, 123));
        add(new Label("Source"), UI.scale(211, 123));
        add(new Label("Target"), UI.scale(276, 123));
        add(new Label("Total"), UI.scale(341, 123));
        add(new Label("Hunger"), UI.scale(406, 123));
        add(new Label("After bite"), UI.scale(476, 123));
        add(new Label("Why this order"), UI.scale(546, 123));
        planList = add(new PlanList(UI.scale(696, 172)), UI.scale(12, 142));

        refreshNow();
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        long now = System.currentTimeMillis();
        if(now >= nextRefresh)
            capture(now);
        if(snapshot != null)
            controller.tick(snapshot, plan);
        if(confirmationDeadline > 0 && now > confirmationDeadline)
            clearConfirmation();
        updateControls();
    }

    private void capture(long now) {
        String previous = snapshot == null ? null : snapshot.fingerprint();
        snapshot = FeastingLiveSnapshot.capture(gui, table);
        plan = planner.plan(snapshot, attributeDropBox.sel, balancedTarget.a,
                sourceMode());
        planList.reset();
        updateSummaries();
        nextRefresh = now + REFRESH_INTERVAL_MS;
        if(confirmationFingerprint != null && !Objects.equals(confirmationFingerprint,
                snapshot.fingerprint()) && previous != null)
            clearConfirmation();
    }

    private void refreshNow() {
        nextRefresh = 0;
    }

    private void updateSummaries() {
        if(snapshot == null || plan == null)
            return;
        FeastingPlan.Entry projection = plan.entries.isEmpty() ? null :
                plan.entries.get(plan.entries.size() - 1);
        String projected = projection == null ? "no projection" :
                String.format(Locale.ROOT, "projected %.1f%%%s", projection.projectedChance * 100d,
                        projection.fillsBar ? " at trigger" : " after plan");
        fepSummary.settext(String.format(Locale.ROOT,
                "FEP %.2f/%.2f | %s current %.1f%% | %s",
                snapshot.currentTotalFep, snapshot.fepCap, plan.target.label,
                plan.currentChance * 100d, projected));
        int lead = plan.selectedBase - plan.lowestBase;
        String balance = String.format(Locale.ROOT,
                "%s %d | lowest %d | highest %d | selected lead %s%d",
                plan.target.label, plan.selectedBase, plan.lowestBase, plan.highestBase,
                lead >= 0 ? "+" : "", lead);
        if(!plan.balanceWarning.isBlank())
            balance += " | " + plan.balanceWarning;
        balanceSummary.settext(balance);
        balanceSummary.setcolor(plan.balanceWarning.isBlank() ?
                new Color(170, 190, 205) : new Color(255, 170, 95));
    }

    private void startRequested() {
        if(snapshot == null || plan == null) {
            refreshNow();
            return;
        }
        if(controller.active())
            return;
        if(plan.empty()) {
            displayedStatus = "";
            controller.stop("No reachable food contributes to " + plan.target.label + ".");
            return;
        }
        boolean imbalanceConfirmation = plan.balanceWarning.contains("already the highest");
        boolean confirmationRequired = allowBreakage.a || imbalanceConfirmation;
        long now = System.currentTimeMillis();
        if(confirmationRequired && (confirmationDeadline <= now ||
                !Objects.equals(confirmationFingerprint, snapshot.fingerprint()))) {
            confirmationDeadline = now + CONFIRMATION_WINDOW_MS;
            confirmationFingerprint = snapshot.fingerprint();
            confirmationMessage = confirmationText(imbalanceConfirmation);
            startButton.change("Confirm start");
            return;
        }
        clearConfirmation();
        controller.start(snapshot, plan, allowBreakage.a);
    }

    private String confirmationText(boolean imbalanceConfirmation) {
        StringBuilder text = new StringBuilder("Click Confirm start within 8 seconds");
        if(allowBreakage.a) {
            text.append(" to allow helper-driven symbel breakage");
            if(!snapshot.atRiskTableware.isEmpty())
                text.append(" (at risk: ").append(String.join(", ", snapshot.atRiskTableware))
                        .append(')');
            else if(snapshot.tablewareState == FeastingSnapshot.TablewareState.UNKNOWN)
                text.append(" (durability is currently unreadable)");
        }
        if(imbalanceConfirmation)
            text.append(allowBreakage.a ? " and" : " to").append(" raise an already-highest attribute");
        text.append('.');
        return(text.toString());
    }

    private void clearConfirmation() {
        confirmationDeadline = 0;
        confirmationFingerprint = null;
        confirmationMessage = null;
        if(startButton != null)
            startButton.change("Start");
    }

    private void updateControls() {
        boolean active = controller.active();
        if(displayedActive != active) {
            startButton.disable(active);
            stopButton.disable(!active);
            if(displayedActive && !active) {
                allowBreakage.a = false;
                clearConfirmation();
            }
            displayedActive = active;
        }
        String value;
        if(confirmationMessage != null) {
            value = confirmationMessage;
        } else if(active || !"Ready.".equals(controller.status())) {
            value = controller.status();
        } else if(snapshot == null) {
            value = "Reading reachable food...";
        } else if(snapshot.fepCap <= 0) {
            value = "Character FEP data is unavailable; open or refresh the character state.";
        } else if(allowedCandidateCount() == 0) {
            value = snapshot.pendingFoods > 0 ? "Food tooltips are still loading..." :
                    (sourceMode() == FeastingSourceMode.INVENTORY_ONLY ?
                            "No reachable food was found in the main inventory." :
                            "No reachable food was found on this Table or in the main inventory.");
        } else {
            int candidateCount = allowedCandidateCount();
            value = candidateCount +
                    (candidateCount == 1 ? " reachable food" : " reachable foods") +
                    (sourceMode() == FeastingSourceMode.INVENTORY_ONLY ? " | inventory only" : "") +
                    (snapshot.pendingFoods > 0 ? " | " + snapshot.pendingFoods + " loading" : "") +
                    " | " + tablewareStatus();
        }
        if(!Objects.equals(displayedStatus, value)) {
            displayedStatus = value;
            status.settext(value);
        }
    }

    private FeastingSourceMode sourceMode() {
        return(inventoryOnly.a ? FeastingSourceMode.INVENTORY_ONLY :
                FeastingSourceMode.TABLE_AND_INVENTORY);
    }

    private int allowedCandidateCount() {
        int count = 0;
        for(FeastingCandidate candidate : snapshot.candidates) {
            if(sourceMode().allows(candidate))
                count++;
        }
        return(count);
    }

    private String tablewareStatus() {
        return(switch(snapshot.tablewareState) {
            case SAFE -> "tableware safe";
            case AT_RISK -> "at risk: " + String.join(", ", snapshot.atRiskTableware);
            case UNKNOWN -> "tableware durability unreadable";
        });
    }

    public boolean active() {
        return(controller.active());
    }

    public void stop(String reason) {
        controller.stop(reason);
        clearConfirmation();
    }

    @Override
    public void destroy() {
        controller.stop("Feasting Helper stopped because the Table window closed.");
        super.destroy();
    }

    @Override
    public void draw(GOut g) {
        Coord topLeft = Window.wbox.btloff();
        Coord bottomRight = sz.sub(Window.wbox.cisz()).add(topLeft);
        for(int y = topLeft.y; y < bottomRight.y; y += Window.bg.sz().y) {
            for(int x = topLeft.x; x < bottomRight.x; x += Window.bg.sz().x)
                g.image(Window.bg, Coord.of(x, y), topLeft, bottomRight);
        }
        Window.wbox.draw(g, Coord.z, sz);
        super.draw(g);
    }

    private final class AttributeDropBox extends SDropBox<CookbookAttribute, Widget> {
        private AttributeDropBox() {
            super(UI.scale(165), UI.scale(245), UI.scale(25));
            super.change(CookbookAttribute.STRENGTH);
        }

        @Override
        protected List<CookbookAttribute> items() {
            return(FeastingLiveSnapshot.foodAttributes());
        }

        @Override
        protected Widget makeitem(CookbookAttribute attribute, int index, Coord size) {
            return(new AttributeItem(size, attribute));
        }

        @Override
        public void change(CookbookAttribute attribute) {
            if(controller.active())
                return;
            super.change(attribute);
            controller.stop("Ready.");
            if(balancedTarget != null && balancedTarget.a)
                balancedTarget.set(false);
            clearConfirmation();
            refreshNow();
        }
    }

    private static final class AttributeItem extends SListWidget.IconText {
        private final CookbookAttribute attribute;

        private AttributeItem(Coord size, CookbookAttribute attribute) {
            super(size);
            this.attribute = attribute;
        }

        @Override
        protected BufferedImage img() {
            return(attribute.icon());
        }

        @Override
        protected String text() {
            return(attribute.label);
        }

        @Override
        protected int margin() {
            return(UI.scale(3));
        }

        @Override
        protected Text.Forge foundry() {
            return(attribute.font());
        }

        @Override
        protected PUtils.Convolution filter() {
            return(CharWnd.iconfilter);
        }
    }

    private final class PlanList extends SListBox<FeastingPlan.Entry, Widget> {
        private PlanList(Coord size) {
            super(size, UI.scale(32), UI.scale(2));
        }

        @Override
        protected List<? extends FeastingPlan.Entry> items() {
            return(plan == null ? Collections.emptyList() : plan.entries);
        }

        @Override
        protected Widget makeitem(FeastingPlan.Entry entry, int index, Coord size) {
            FeastingCandidate food = entry.candidate;
            Widget row = new SListWidget.ItemWidget<FeastingPlan.Entry>(this, size, entry);
            row.add(new FoodIconText(UI.scale(190, 30), food), UI.scale(3, 0));
            row.add(SListWidget.TextItem.of(UI.scale(58, 30), ROW_MUTED,
                    () -> food.source.label), UI.scale(198, 0));
            row.add(SListWidget.TextItem.of(UI.scale(58, 30), plan.target.font(),
                    () -> String.format(Locale.ROOT, "%.2f", food.fep(plan.target))),
                    UI.scale(263, 0));
            row.add(SListWidget.TextItem.of(UI.scale(58, 30), ROW_TEXT,
                    () -> String.format(Locale.ROOT, "%.2f", food.totalFep)), UI.scale(328, 0));
            row.add(SListWidget.TextItem.of(UI.scale(63, 30), ROW_TEXT,
                    () -> String.format(Locale.ROOT, "%.1f‰", food.hungerPermille)),
                    UI.scale(393, 0));
            row.add(SListWidget.TextItem.of(UI.scale(63, 30), plan.target.font(),
                    () -> String.format(Locale.ROOT, "%.1f%%", entry.projectedChance * 100d)),
                    UI.scale(463, 0));
            row.add(SListWidget.TextItem.of(UI.scale(150, 30), ROW_MUTED,
                    () -> entry.reason), UI.scale(533, 0));
            row.tooltip = String.format(Locale.ROOT,
                    "%s Q%.1f | Energy %.1f%% | projected %.2f/%.2f FEP | %s",
                    food.name, food.quality, food.energyPercent, entry.projectedTargetFep,
                    entry.projectedTotalFep, entry.reason);
            return(row);
        }
    }

    private static final class FoodIconText extends SListWidget.IconText {
        private final Indir<Resource> resource;
        private final FeastingCandidate food;

        private FoodIconText(Coord size, FeastingCandidate food) {
            super(size);
            this.food = food;
            this.resource = food.resourceName.isBlank() ? null :
                    Resource.remote().load(food.resourceName);
        }

        @Override
        protected BufferedImage img() {
            if(resource == null)
                return(null);
            try {
                return(resource.get().flayer(Resource.imgc).img);
            } catch(Loading loading) {
                return(null);
            }
        }

        @Override
        protected String text() {
            return(food.name + (food.quantity > 1 ? " x" + food.quantity : ""));
        }

        @Override
        protected Text.Forge foundry() {
            return(ROW_TEXT);
        }

        @Override
        protected PUtils.Convolution filter() {
            return(CharWnd.iconfilter);
        }
    }
}
