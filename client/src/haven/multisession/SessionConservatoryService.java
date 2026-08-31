package haven.multisession;

import haven.GameUI;
import haven.Connection;
import haven.Session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only first slice of the in-game session host.
 *
 * The service exposes the current session without retaining passwords, cookies,
 * authentication tokens, or mutable UI objects in its immutable snapshots.
 */
public final class SessionConservatoryService {
    private final GameUI gui;
    private final SessionWorkerReadiness workerReadiness;
    private SessionLaunchMode launchMode = SessionLaunchMode.VIEWER;
    private String loginAccountLabel = "";
    private String actionStatus = "";

    public SessionConservatoryService(GameUI gui) {
        if(gui == null)
            throw(new IllegalArgumentException("GameUI is required."));
        this.gui = gui;
        this.workerReadiness = SessionWorkerReadiness.inspect();
    }

    public synchronized SessionConservatorySnapshot snapshot() {
        Session session = (gui.ui == null) ? null : gui.ui.sess;
        boolean connected = session != null && (!(session.conn instanceof Connection) ||
                ((Connection)session.conn).alive());
        String account = connected ? session.user.readname() : "Current account";
        SessionSurfaceState state = connected ? SessionSurfaceState.CURRENT : SessionSurfaceState.DISCONNECTED;
        String detail = connected ?
                "Direct input remains attached to this single MoonFlower surface." :
                "The current Haven session is unavailable.";
        SessionCardSnapshot current = new SessionCardSnapshot(
                "current",
                account,
                gui.chrid,
                gui.genus,
                state,
                true,
                true,
                Instant.now(),
                detail
        );
        List<SessionLaunchOptionSnapshot> launchOptions = new ArrayList<>();
        for(String label : KnownAccountCatalog.labels())
            launchOptions.add(new SessionLaunchOptionSnapshot(label, workerReadiness.ready()));
        String status = actionStatus.isEmpty() ? (connected ?
                "Single-window host attached to the current Haven session." :
                "Waiting for the current session. No background client window was opened.") : actionStatus;
        return(new SessionConservatorySnapshot(
                List.of(current),
                current.sessionId(),
                true,
                workerReadiness.ready(),
                workerReadiness,
                launchOptions,
                launchMode,
                loginAccountLabel,
                status
        ));
    }

    public synchronized void requestFreshLogin() {
        launchMode = SessionLaunchMode.LOGIN;
        loginAccountLabel = "";
        actionStatus = "Fresh account login opened inside the current MoonFlower window.";
    }

    public synchronized void requestKnownAccount(String accountLabel) {
        loginAccountLabel = (accountLabel == null) ? "" : accountLabel.trim();
        launchMode = SessionLaunchMode.LOGIN;
        actionStatus = workerReadiness.ready() ?
                "Preparing direct sign-in for " + loginAccountLabel + "." :
                "Confirm the account login. Direct sign-in waits for the secure worker bridge.";
    }

    public synchronized void cancelLogin() {
        launchMode = SessionLaunchMode.VIEWER;
        loginAccountLabel = "";
        actionStatus = "Account login closed; the current session was not changed.";
    }

    public synchronized void noteLoginBlocked(String accountLabel) {
        String label = (accountLabel == null || accountLabel.isBlank()) ? "this account" : accountLabel.trim();
        actionStatus = "Cannot open " + label + " yet: the one-shot credential bridge is locked.";
    }
}
