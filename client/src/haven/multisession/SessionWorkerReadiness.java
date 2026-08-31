package haven.multisession;

import haven.iosys.tk.Acephal;

import java.util.Set;
import java.util.TreeSet;

/** Immutable, non-sensitive capability report for the worker implementation. */
public final class SessionWorkerReadiness {
    public enum State {
        READY("READY"),
        LOCKED("LOCKED"),
        UNAVAILABLE("UNAVAILABLE");

        public final String label;

        State(String label) {
            this.label = label;
        }
    }

    private final State state;
    private final Set<String> offscreenRenderers;
    private final boolean nonWindowedClientPresent;
    private final boolean credentialBrokerReady;
    private final boolean telemetryBridgeReady;
    private final String detail;

    private SessionWorkerReadiness(State state, Set<String> offscreenRenderers,
                                   boolean nonWindowedClientPresent, boolean credentialBrokerReady,
                                   boolean telemetryBridgeReady, String detail) {
        this.state = state;
        this.offscreenRenderers = Set.copyOf(offscreenRenderers);
        this.nonWindowedClientPresent = nonWindowedClientPresent;
        this.credentialBrokerReady = credentialBrokerReady;
        this.telemetryBridgeReady = telemetryBridgeReady;
        this.detail = detail;
    }

    public static SessionWorkerReadiness inspect() {
        Set<String> renderers = new TreeSet<>();
        boolean client = false;
        try {
            renderers.addAll(Acephal.types().keySet());
        } catch(RuntimeException ignored) {
        }
        try {
            Class.forName(SessionWorkerLaunchSpec.MAIN_CLASS, false,
                    SessionWorkerReadiness.class.getClassLoader());
            client = true;
        } catch(ClassNotFoundException ignored) {
        }
        return(evaluate(renderers, client, false, false));
    }

    static SessionWorkerReadiness evaluate(Set<String> renderers, boolean client,
                                           boolean credentialBroker, boolean telemetryBridge) {
        Set<String> safeRenderers = new TreeSet<>(renderers == null ? Set.of() : renderers);
        if(!client || safeRenderers.isEmpty()) {
            return(new SessionWorkerReadiness(State.UNAVAILABLE, safeRenderers, client,
                    credentialBroker, telemetryBridge,
                    "Offscreen client support is unavailable; background launch is blocked."));
        }
        if(!credentialBroker || !telemetryBridge) {
            return(new SessionWorkerReadiness(State.LOCKED, safeRenderers, true,
                    credentialBroker, telemetryBridge,
                    "Offscreen path found. Waiting for one-shot credentials and preview telemetry."));
        }
        return(new SessionWorkerReadiness(State.READY, safeRenderers, true, true, true,
                "Non-windowed worker launch prerequisites are ready."));
    }

    public State state() {return(state);}
    public Set<String> offscreenRenderers() {return(offscreenRenderers);}
    public boolean nonWindowedClientPresent() {return(nonWindowedClientPresent);}
    public boolean credentialBrokerReady() {return(credentialBrokerReady);}
    public boolean telemetryBridgeReady() {return(telemetryBridgeReady);}
    public boolean ready() {return(state == State.READY);}
    public String detail() {return(detail);}
}
