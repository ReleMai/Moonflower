package haven.multisession;

import haven.NamedSocketAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fail-closed description of a worker process. It targets the protocol entry
 * point which hosts the repository's offscreen HeadlessClient and intentionally
 * contains no authentication data.
 */
public final class SessionWorkerLaunchSpec {
    public static final String MAIN_CLASS = "haven.multisession.SessionWorkerMain";
    public static final String HEADLESS_CLIENT_CLASS = "haven.HeadlessClient";
    private static final Set<String> FORBIDDEN_ARGUMENTS = Set.of(
            "-u", "-c", "--user", "--username", "--password", "--cookie", "--token"
    );
    private final SessionWorkerProfile profile;
    private final List<String> arguments;

    private SessionWorkerLaunchSpec(SessionWorkerProfile profile, List<String> arguments) {
        this.profile = profile;
        this.arguments = List.copyOf(arguments);
        verifySafeArguments(this.arguments);
    }

    public static SessionWorkerLaunchSpec offscreen(SessionWorkerProfile profile) {
        return(offscreen(profile, null, false));
    }

    public static SessionWorkerLaunchSpec offscreen(SessionWorkerProfile profile,
                                                    NamedSocketAddress authServer,
                                                    boolean encrypted) {
        if(profile == null)
            throw(new IllegalArgumentException("Worker profile is required."));
        String preferenceScope = "moonflower-session-" + safeId(profile.workerId());
        List<String> args = new ArrayList<>(List.of(
                "--size", profile.previewWidth() + "x" + profile.previewHeight(),
                "--prefs", preferenceScope,
                "--game-server", profile.server(),
                "--encrypt", Boolean.toString(encrypted)
        ));
        if(authServer != null) {
            args.add("--auth-server");
            args.add(authServer.toString(0));
        }
        return(new SessionWorkerLaunchSpec(profile, args));
    }

    public SessionWorkerProfile profile() {return(profile);}
    public String mainClass() {return(MAIN_CLASS);}
    public List<String> arguments() {return(arguments);}
    public boolean createsNativeWindow() {return(false);}
    public boolean requiresCredentialBroker() {return(true);}
    public boolean usesHeadlessClient() {return(true);}

    static void verifySafeArguments(List<String> arguments) {
        for(String argument : arguments) {
            String normalized = (argument == null) ? "" : argument.toLowerCase(Locale.ROOT);
            if(FORBIDDEN_ARGUMENTS.contains(normalized) || normalized.startsWith("haven.authck=") ||
                    normalized.startsWith("haven.inittoken=") || normalized.contains("account_secret"))
                throw(new IllegalArgumentException("Authentication data cannot be placed in worker arguments."));
        }
    }

    private static String safeId(String value) {
        String safe = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        while(safe.contains("--"))
            safe = safe.replace("--", "-");
        return(safe);
    }
}
