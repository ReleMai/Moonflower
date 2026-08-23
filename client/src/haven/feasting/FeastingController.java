package haven.feasting;

import haven.Button;
import haven.Coord;
import haven.GameUI;
import haven.GItem;
import haven.Widget;
import haven.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded UI-thread state machine for player-started Table feasting. */
public final class FeastingController {
    private static final long ARM_TIMEOUT_MS = 2500;
    private static final long BITE_TIMEOUT_MS = 3500;
    private static final long REFRESH_DELAY_MS = 150;
    private static final String EAT_CURSOR = "gfx/hud/curs/eat";

    private final GameUI gui;
    private final Window table;
    private State state = State.IDLE;
    private boolean active;
    private boolean allowBreakage;
    private long deadline;
    private long refreshTimeout;
    private int bites;
    private int awaitingWidgetId = -1;
    private int awaitingQuantity;
    private int awaitingInfoSequence;
    private String awaitingFingerprint = "";
    private String status = "Ready.";

    public FeastingController(GameUI gui, Window table) {
        this.gui = gui;
        this.table = table;
    }

    public void start(FeastingSnapshot snapshot, FeastingPlan plan, boolean allowBreakage) {
        if(active)
            return;
        if(plan == null || plan.empty()) {
            status = "No reachable food contributes to the selected attribute.";
            return;
        }
        if(snapshot.fepCap <= 0) {
            status = "The character FEP meter is not available yet.";
            return;
        }
        this.allowBreakage = allowBreakage;
        this.active = true;
        this.bites = 0;
        this.state = State.ARMING;
        arm(System.currentTimeMillis());
    }

    public void tick(FeastingSnapshot snapshot, FeastingPlan plan) {
        if(!active)
            return;
        String precondition = preconditionError();
        if(precondition != null) {
            stop(precondition);
            return;
        }
        long now = System.currentTimeMillis();
        switch(state) {
            case ARMING -> tickArming(snapshot, plan, now);
            case WAITING -> tickWaiting(snapshot, now);
            case REFRESHING -> {
                if(now >= deadline && !awaitingFingerprint.equals(snapshot.fingerprint()))
                    prepareNext(snapshot, plan, now);
                else if(now >= refreshTimeout)
                    stop("Feasting Helper stopped: live food and meter state did not refresh after the bite.");
            }
            case IDLE -> {
            }
        }
    }

    private void tickArming(FeastingSnapshot snapshot, FeastingPlan plan, long now) {
        if(gui.ui.checkCursorImage(EAT_CURSOR)) {
            dispatchNext(snapshot, plan, now);
        } else if(now >= deadline) {
            stop("Feasting Helper stopped: the Table feast cursor did not appear.");
        }
    }

    private void tickWaiting(FeastingSnapshot snapshot, long now) {
        Widget live = gui.ui.getwidget(awaitingWidgetId);
        int quantity = live instanceof GItem ? normalizedQuantity((GItem)live) : -1;
        int infoSequence = live instanceof GItem ? ((GItem)live).infoseq : -1;
        boolean changed = observedProgress(live instanceof GItem, awaitingQuantity, quantity,
                awaitingInfoSequence, infoSequence, awaitingFingerprint,
                snapshot.fingerprint());
        if(changed) {
            bites++;
            state = State.REFRESHING;
            deadline = now + REFRESH_DELAY_MS;
            refreshTimeout = now + 1500;
            status = "Bite acknowledged; recalculating from the live FEP meter...";
        } else if(now >= deadline) {
            stop("Feasting Helper stopped: no server acknowledgement was observed after the bite.");
        }
    }

    private void prepareNext(FeastingSnapshot snapshot, FeastingPlan plan, long now) {
        if(plan == null || plan.empty()) {
            complete("Feasting plan completed after " + bites + (bites == 1 ? " bite." : " bites."));
            return;
        }
        if(!tablewareAllowsDispatch(allowBreakage, snapshot.tablewareState)) {
            stop(tablewareStop(snapshot));
            return;
        }
        if(gui.ui.checkCursorImage(EAT_CURSOR)) {
            state = State.ARMING;
            dispatchNext(snapshot, plan, now);
        } else {
            state = State.ARMING;
            arm(now);
        }
    }

    private void dispatchNext(FeastingSnapshot snapshot, FeastingPlan plan, long now) {
        if(plan == null || plan.empty()) {
            complete("Feasting plan completed after " + bites + (bites == 1 ? " bite." : " bites."));
            return;
        }
        if(!tablewareAllowsDispatch(allowBreakage, snapshot.tablewareState)) {
            stop(tablewareStop(snapshot));
            return;
        }
        FeastingCandidate candidate = plan.entries.get(0).candidate;
        Widget widget = gui.ui.getwidget(candidate.widgetId);
        if(!(widget instanceof GItem item) || !reachable(item, candidate.source)) {
            stop("Feasting Helper stopped: the next planned food moved or disappeared.");
            return;
        }
        if(item.infoseq != candidate.infoSequence ||
                normalizedQuantity(item) != candidate.quantity) {
            stop("Feasting Helper stopped: the next food changed before it could be eaten.");
            return;
        }

        awaitingWidgetId = candidate.widgetId;
        awaitingQuantity = normalizedQuantity(item);
        awaitingInfoSequence = item.infoseq;
        awaitingFingerprint = snapshot.fingerprint();
        try {
            if(allowBreakage) {
                try(FeastingActionContext.Scope ignored =
                            FeastingActionContext.allowTablewareBreakage()) {
                    item.wdgmsg("take", Coord.z);
                }
            } else {
                item.wdgmsg("take", Coord.z);
            }
        } catch(RuntimeException failure) {
            stop("Feasting Helper stopped: the bite could not be dispatched safely.");
            return;
        }
        state = State.WAITING;
        deadline = now + BITE_TIMEOUT_MS;
        status = String.format(Locale.ROOT, "Eating %s from %s; waiting for acknowledgement...",
                candidate.name, candidate.source.label);
    }

    private void arm(long now) {
        Button feast = findFeastButton();
        if(feast == null) {
            stop("Feasting Helper stopped: the Table Feast button is unavailable.");
            return;
        }
        feast.click();
        deadline = now + ARM_TIMEOUT_MS;
        status = "Selecting the Table Feast action...";
    }

    private Button findFeastButton() {
        List<Button> buttons = new ArrayList<>();
        for(Widget child = table.child; child != null; child = child.next) {
            if(child instanceof Button button) {
                buttons.add(button);
                if(button.text != null && "Feast".equalsIgnoreCase(button.text.text))
                    return(button);
            }
        }
        return(buttons.size() == 1 ? buttons.get(0) : null);
    }

    private boolean reachable(GItem item, FeastingCandidate.Source source) {
        return(source == FeastingCandidate.Source.TABLE ? item.hasparent(table) :
                gui.maininv != null && item.hasparent(gui.maininv));
    }

    private String preconditionError() {
        if(table.parent == null || !table.visible())
            return("Feasting Helper stopped because the Table window closed.");
        if(gui.ui == null || gui.maininv == null || gui.chrwdg == null ||
                gui.chrwdg.battr == null)
            return("Feasting Helper stopped because character or inventory state disappeared.");
        if(gui.vhand != null)
            return("Feasting Helper stopped because an item is being held by the cursor.");
        if(gui.fv != null && !gui.fv.lsrel.isEmpty())
            return("Feasting Helper stopped because combat started.");
        return(null);
    }

    private static String tablewareStop(FeastingSnapshot snapshot) {
        if(snapshot.tablewareState == FeastingSnapshot.TablewareState.UNKNOWN)
            return("Feasting Helper stopped because tableware durability is unreadable.");
        return("Feasting Helper stopped before breaking: " +
                String.join(", ", snapshot.atRiskTableware) + ".");
    }

    public void stop(String message) {
        active = false;
        allowBreakage = false;
        state = State.IDLE;
        awaitingWidgetId = -1;
        status = message == null ? "Stopped." : message;
    }

    private void complete(String message) {
        stop(message);
    }

    public boolean active() {
        return(active);
    }

    public String status() {
        return(status);
    }

    public int bites() {
        return(bites);
    }

    private static int normalizedQuantity(GItem item) {
        return(item.num > 0 ? item.num : 1);
    }

    static boolean observedProgress(boolean itemExists, int previousQuantity, int currentQuantity,
                                    int previousInfoSequence, int currentInfoSequence,
                                    String previousFingerprint, String currentFingerprint) {
        return(!itemExists || previousQuantity != currentQuantity ||
                previousInfoSequence != currentInfoSequence ||
                !previousFingerprint.equals(currentFingerprint));
    }

    static boolean tablewareAllowsDispatch(boolean allowBreakage,
                                           FeastingSnapshot.TablewareState state) {
        return(allowBreakage || state == FeastingSnapshot.TablewareState.SAFE);
    }

    private enum State {
        IDLE, ARMING, WAITING, REFRESHING
    }
}
