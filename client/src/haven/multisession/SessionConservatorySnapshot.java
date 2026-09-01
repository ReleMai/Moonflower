package haven.multisession;

import java.util.List;

/** Immutable view consumed by the MoonFlower presentation layer. */
public final class SessionConservatorySnapshot {
    private final List<SessionCardSnapshot> sessions;
    private final String selectedSessionId;
    private final boolean singleVisibleWindow;
    private final boolean backgroundWorkersEnabled;
    private final SessionWorkerReadiness workerReadiness;
    private final List<SessionLaunchOptionSnapshot> launchOptions;
    private final SessionLaunchMode launchMode;
    private final String loginAccountLabel;
    private final String status;

    public SessionConservatorySnapshot(List<SessionCardSnapshot> sessions, String selectedSessionId,
                                       boolean singleVisibleWindow, boolean backgroundWorkersEnabled,
                                       SessionWorkerReadiness workerReadiness,
                                       List<SessionLaunchOptionSnapshot> launchOptions,
                                       SessionLaunchMode launchMode, String loginAccountLabel,
                                       String status) {
        this.sessions = List.copyOf(sessions == null ? List.of() : sessions);
        this.selectedSessionId = (selectedSessionId == null) ? "" : selectedSessionId;
        this.singleVisibleWindow = singleVisibleWindow;
        this.backgroundWorkersEnabled = backgroundWorkersEnabled;
        if(workerReadiness == null)
            throw(new IllegalArgumentException("Worker readiness is required."));
        this.workerReadiness = workerReadiness;
        this.launchOptions = List.copyOf(launchOptions == null ? List.of() : launchOptions);
        this.launchMode = (launchMode == null) ? SessionLaunchMode.VIEWER : launchMode;
        this.loginAccountLabel = (loginAccountLabel == null) ? "" : loginAccountLabel.trim();
        this.status = (status == null || status.isBlank()) ? "Session host unavailable" : status.trim();
        long selected = this.sessions.stream().filter(SessionCardSnapshot::selected).count();
        if(selected > 1)
            throw(new IllegalArgumentException("Only one session may own the visible game surface."));
        if(selected != 1)
            throw(new IllegalArgumentException("Exactly one session must own the visible game surface."));
        if(this.sessions.stream().noneMatch(session -> session.sessionId().equals(this.selectedSessionId)))
            throw(new IllegalArgumentException("Selected session is not present in the snapshot."));
        if(!singleVisibleWindow)
            throw(new IllegalArgumentException("MoonFlower multi-account mode requires one visible game window."));
        if(backgroundWorkersEnabled != workerReadiness.ready())
            throw(new IllegalArgumentException("Worker enablement must match its readiness report."));
    }

    public List<SessionCardSnapshot> sessions() {return(sessions);}
    public String selectedSessionId() {return(selectedSessionId);}
    public boolean singleVisibleWindow() {return(singleVisibleWindow);}
    public boolean backgroundWorkersEnabled() {return(backgroundWorkersEnabled);}
    public SessionWorkerReadiness workerReadiness() {return(workerReadiness);}
    public List<SessionLaunchOptionSnapshot> launchOptions() {return(launchOptions);}
    public SessionLaunchMode launchMode() {return(launchMode);}
    public String loginAccountLabel() {return(loginAccountLabel);}
    public String status() {return(status);}
}
