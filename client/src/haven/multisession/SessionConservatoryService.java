package haven.multisession;

import haven.AuthClient;
import haven.Bootstrap;
import haven.Connection;
import haven.GameUI;
import haven.NamedSocketAddress;
import haven.Session;
import haven.Utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the embedded multi-login workers while the GameUI remains the only
 * visible Haven client window. Snapshots contain display state only; secrets
 * exist briefly during authentication and are never stored in this service.
 */
public final class SessionConservatoryService {
    private static final int MAX_WORKERS = 4;
    private final GameUI gui;
    private final SessionWorkerReadiness workerReadiness;
    private final Map<String, SessionWorkerProcess> workers = new LinkedHashMap<>();
    private final Set<String> launchingAccounts = new HashSet<>();
    private SessionLaunchMode launchMode = SessionLaunchMode.VIEWER;
    private String loginAccountLabel = "";
    private String selectedSessionId = "current";
    private String actionStatus = "";
    private boolean closed;

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
        List<SessionCardSnapshot> sessions = new ArrayList<>();
        sessions.add(new SessionCardSnapshot(
                "current", account, gui.chrid, gui.genus, state,
                selectedSessionId.equals("current"), true, Instant.now(), detail));
        for(SessionWorkerProcess worker : workers.values())
            sessions.add(worker.snapshot(worker.workerId().equals(selectedSessionId)));

        if(sessions.stream().noneMatch(SessionCardSnapshot::selected)) {
            selectedSessionId = "current";
            SessionCardSnapshot current = sessions.get(0);
            sessions.set(0, new SessionCardSnapshot(
                    current.sessionId(), current.accountLabel(), current.characterName(), current.worldName(),
                    current.state(), true, current.interactive(), current.observedAt(), current.detail()));
        }

        List<SessionLaunchOptionSnapshot> launchOptions = new ArrayList<>();
        for(String label : KnownAccountCatalog.labels())
            launchOptions.add(new SessionLaunchOptionSnapshot(label, workerReadiness.ready()));
        String status = actionStatus.isEmpty() ? defaultStatus(connected) : actionStatus;
        return(new SessionConservatorySnapshot(
                sessions,
                selectedSessionId,
                true,
                workerReadiness.ready(),
                workerReadiness,
                launchOptions,
                launchMode,
                loginAccountLabel,
                status
        ));
    }

    private String defaultStatus(boolean connected) {
        if(!workers.isEmpty())
            return(workers.size() + " embedded session(s); only the selected session receives input.");
        return(connected ?
                "Single-window host attached to the current Haven session." :
                "Waiting for the current session. No background client window was opened.");
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
                "Enter the account secret. It will be exchanged for a one-shot worker cookie." :
                "The secure worker bridge is unavailable.";
    }

    public synchronized void cancelLogin() {
        launchMode = SessionLaunchMode.VIEWER;
        loginAccountLabel = "";
        actionStatus = "Account login closed; the current session was not changed.";
    }

    public void submitLogin(String accountLabel, String secret) {
        String label = (accountLabel == null) ? "" : accountLabel.trim();
        if(label.isEmpty() || secret == null || secret.isEmpty()) {
            synchronized(this) {
                actionStatus = "Account name and password or login token are required.";
            }
            return;
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        synchronized(this) {
            if(closed) {
                Arrays.fill(secretBytes, (byte)0);
                return;
            }
            if(!workerReadiness.ready()) {
                Arrays.fill(secretBytes, (byte)0);
                actionStatus = "The secure worker bridge is unavailable; no login was attempted.";
                return;
            }
            String accountKey = accountKey(label);
            if(workers.size() + launchingAccounts.size() >= MAX_WORKERS) {
                Arrays.fill(secretBytes, (byte)0);
                actionStatus = "The embedded worker limit has been reached.";
                return;
            }
            if(launchingAccounts.contains(accountKey)) {
                Arrays.fill(secretBytes, (byte)0);
                actionStatus = "That account is already being opened.";
                return;
            }
            for(SessionWorkerProcess worker : workers.values()) {
                if(worker.accountLabel().equalsIgnoreCase(label) && worker.state() != SessionWorkerProcess.State.STOPPED) {
                    Arrays.fill(secretBytes, (byte)0);
                    actionStatus = "That account already has an embedded session.";
                    return;
                }
            }
            launchingAccounts.add(accountKey);
            launchMode = SessionLaunchMode.VIEWER;
            loginAccountLabel = "";
            actionStatus = "Authenticating the account in memory; the current session remains active.";
        }
        Thread launcher = new Thread(() -> launchWorker(label, secretBytes),
                "MoonFlower worker launcher");
        launcher.setDaemon(true);
        launcher.start();
    }

    private void launchWorker(String label, byte[] secret) {
        SessionWorkerAuthTicket ticket = null;
        SessionWorkerProcess worker = null;
        try {
            ticket = authenticate(label, secret);
            NamedSocketAddress authServer = authServer();
            SessionWorkerProfile profile = new SessionWorkerProfile(
                    "worker-" + UUID.randomUUID(), label, "", gameServer().toString(0), 640, 360);
            SessionWorkerLaunchSpec launch = SessionWorkerLaunchSpec.offscreen(
                    profile, authServer, Connection.encrypt.get());
            final SessionWorkerProcess[] holder = new SessionWorkerProcess[1];
            worker = new SessionWorkerProcess(profile, launch, () -> workerChanged(holder[0]));
            holder[0] = worker;
            synchronized(this) {
                if(closed)
                    return;
                workers.put(profile.workerId(), worker);
                selectedSessionId = profile.workerId();
                actionStatus = "Starting the embedded viewport for " + label + ".";
            }
            worker.start(ticket);
            synchronized(this) {
                actionStatus = "Embedded viewport ready for " + label + ". Only this selected session receives input.";
            }
        } catch(AuthClient.Credentials.AuthException e) {
            synchronized(this) {
                actionStatus = "Login failed; the current session was not changed.";
            }
        } catch(Exception e) {
            synchronized(this) {
                actionStatus = "The embedded session could not be started; the current session was not changed.";
            }
        } finally {
            if(ticket != null)
                ticket.clear();
            Arrays.fill(secret, (byte)0);
            synchronized(this) {
                launchingAccounts.remove(accountKey(label));
            }
        }
    }

    private static String accountKey(String label) {
        return(label.toLowerCase(Locale.ROOT));
    }

    private SessionWorkerAuthTicket authenticate(String label, byte[] secret) throws IOException {
        String text = new String(secret, StandardCharsets.UTF_8);
        AuthClient.Credentials credentials;
        if(isToken(text))
            credentials = new AuthClient.TokenCred(label, Utils.hex.dec(text));
        else
            credentials = new AuthClient.NativeCred(label, secret);
        try(AuthClient auth = new AuthClient(authServer())) {
            Session.User user = credentials.tryauth(auth);
            return(new SessionWorkerAuthTicket(user.readname(), auth.getcookie()));
        } finally {
            credentials.discard();
        }
    }

    private static boolean isToken(String value) {
        if(value.length() != 64)
            return(false);
        for(int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if(!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ||
                    (c >= 'A' && c <= 'F')))
                return(false);
        }
        return(true);
    }

    private NamedSocketAddress authServer() {
        NamedSocketAddress configured = Bootstrap.authserv.get();
        return(configured == null ? new NamedSocketAddress("localhost", AuthClient.DEFPORT) : configured);
    }

    private NamedSocketAddress gameServer() {
        NamedSocketAddress configured = Bootstrap.gameserv.get();
        if(configured != null)
            return(configured);
        Session session = (gui.ui == null) ? null : gui.ui.sess;
        if(session != null && session.conn instanceof Connection) {
            SocketAddress server = ((Connection)session.conn).server;
            if(server instanceof InetSocketAddress) {
                InetSocketAddress inet = (InetSocketAddress)server;
                return(new NamedSocketAddress(inet.getHostString(), inet.getPort()));
            }
        }
        NamedSocketAddress auth = authServer();
        return(new NamedSocketAddress(auth.host, Bootstrap.gameport.get()));
    }

    private void workerChanged(SessionWorkerProcess worker) {
        if(worker == null)
            return;
        synchronized(this) {
            if(worker.state() == SessionWorkerProcess.State.READY)
                actionStatus = "Embedded viewport ready for " + worker.accountLabel() + ".";
            else if(worker.state() == SessionWorkerProcess.State.FAILED)
                actionStatus = "Embedded session for " + worker.accountLabel() + " stopped unexpectedly.";
        }
    }

    public synchronized void selectSession(String sessionId) {
        if(sessionId == null)
            return;
        if(sessionId.equals("current") || workers.containsKey(sessionId)) {
            selectedSessionId = sessionId;
            launchMode = SessionLaunchMode.VIEWER;
            actionStatus = sessionId.equals("current") ?
                    "Current session selected; the game keeps its normal input target." :
                    "Embedded session selected; input is routed only to its viewport.";
        }
    }

    public void stopSelectedWorker() {
        SessionWorkerProcess worker;
        synchronized(this) {
            if(selectedSessionId.equals("current"))
                return;
            worker = workers.remove(selectedSessionId);
            selectedSessionId = "current";
            actionStatus = "Embedded session closed; the current session remains active.";
        }
        if(worker != null)
            worker.stop();
    }

    public synchronized boolean selectedWorker() {
        return(!selectedSessionId.equals("current") && workers.containsKey(selectedSessionId));
    }

    public synchronized BufferedImage selectedFrame() {
        SessionWorkerProcess worker = workers.get(selectedSessionId);
        return(worker == null ? null : worker.latestFrame());
    }

    public synchronized haven.Coord selectedPreviewSize() {
        SessionWorkerProcess worker = workers.get(selectedSessionId);
        return(worker == null ? null : worker.previewSize());
    }

    public synchronized boolean routeMouseDown(int x, int y, int button, int modifiers) {
        SessionWorkerProcess worker = workers.get(selectedSessionId);
        if(worker == null)
            return(false);
        worker.sendMouseDown(x, y, button, modifiers);
        return(true);
    }

    public synchronized boolean routeMouseUp(int x, int y, int button, int modifiers) {
        SessionWorkerProcess worker = workers.get(selectedSessionId);
        if(worker == null)
            return(false);
        worker.sendMouseUp(x, y, button, modifiers);
        return(true);
    }

    public synchronized boolean routeMouseMove(int x, int y, int modifiers) {
        SessionWorkerProcess worker = workers.get(selectedSessionId);
        if(worker == null)
            return(false);
        worker.sendMouseMove(x, y, modifiers);
        return(true);
    }

    public synchronized boolean routeMouseWheel(int x, int y, int amount, int modifiers) {
        SessionWorkerProcess worker = workers.get(selectedSessionId);
        if(worker == null)
            return(false);
        worker.sendMouseWheel(x, y, amount, modifiers);
        return(true);
    }

    public synchronized boolean routeKey(boolean down, int keyCode, int modifiers, char keyChar) {
        SessionWorkerProcess worker = workers.get(selectedSessionId);
        if(worker == null)
            return(false);
        worker.sendKey(down, keyCode, modifiers, keyChar);
        return(true);
    }

    public void close() {
        List<SessionWorkerProcess> closing;
        synchronized(this) {
            if(closed)
                return;
            closed = true;
            closing = new ArrayList<>(workers.values());
            workers.clear();
            selectedSessionId = "current";
        }
        for(SessionWorkerProcess worker : closing)
            worker.stop();
    }

    /** Compatibility hook for older callers of the foundation slice. */
    public synchronized void noteLoginBlocked(String accountLabel) {
        String label = (accountLabel == null || accountLabel.isBlank()) ? "this account" : accountLabel.trim();
        actionStatus = "Enter the secret for " + label + " to open it inside this window.";
    }
}
