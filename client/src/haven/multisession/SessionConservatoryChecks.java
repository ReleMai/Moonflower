package haven.multisession;

import haven.Coord;
import haven.UI;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Deterministic foundation checks; no login or live-session behavior is claimed. */
public final class SessionConservatoryChecks {
    private SessionConservatoryChecks() {
    }

    public static void main(String[] args) {
        SessionCardSnapshot current = new SessionCardSnapshot(
                "current", "Account alias", "Hearthling", "World", SessionSurfaceState.CURRENT,
                true, true, Instant.parse("2026-08-30T12:00:00Z"), "Direct input"
        );
        SessionWorkerReadiness lockedWorker = SessionWorkerReadiness.evaluate(
                Set.of("jogl"), true, false, false
        );
        SessionConservatorySnapshot view = new SessionConservatorySnapshot(
                List.of(current), "current", true, false, lockedWorker,
                List.of(new SessionLaunchOptionSnapshot("Account two", false)),
                SessionLaunchMode.VIEWER, "", "Foundation"
        );
        require(view.singleVisibleWindow(), "single visible window invariant");
        require(view.sessions().size() == 1 && view.sessions().get(0).selected(),
                "current session owns the selected surface");
        require(!view.backgroundWorkersEnabled(), "background launches remain locked in the first slice");
        require(view.workerReadiness().state() == SessionWorkerReadiness.State.LOCKED,
                "detected offscreen worker stays locked without credential and telemetry bridges");
        require(current.accountLabel().equals("Account alias"), "snapshot carries only the display alias");
        require(view.launchOptions().size() == 1 &&
                        view.launchOptions().get(0).accountLabel().equals("Account two"),
                "known-account rail carries only a display label");
        require(!view.launchOptions().get(0).directSignInReady(),
                "known-account direct sign-in stays locked with the credential bridge");

        List<String> labels = KnownAccountCatalog.labelsFrom(new String[] {
                "Account two(ಠ‿ಠ)never-return-this-value",
                "Account three(ಠ‿ಠ)another-hidden-value",
                "Account two(ಠ‿ಠ)duplicate-hidden-value",
                "malformed-label-only"
        });
        require(labels.equals(List.of("Account two", "Account three", "malformed-label-only")),
                "legacy account catalog extracts ordered unique labels only");
        require(labels.stream().noneMatch(label -> label.contains("hidden") || label.contains("return")),
                "legacy credential values do not enter launch snapshots");

        SessionWorkerReadiness detected = SessionWorkerReadiness.inspect();
        require(detected.nonWindowedClientPresent(), "packaged headless client path is present");
        require(!detected.offscreenRenderers().isEmpty(), "at least one offscreen renderer is discoverable");
        require(detected.state() == SessionWorkerReadiness.State.LOCKED,
                "runtime detection cannot bypass unfinished security gates");

        SessionWorkerProfile profile = new SessionWorkerProfile(
                "second-hearth", "Account two", "Alt Hearthling", "game.havenandhearth.com", 640, 360
        );
        SessionWorkerLaunchSpec launch = SessionWorkerLaunchSpec.offscreen(profile);
        require(launch.mainClass().equals("haven.HeadlessClient"), "worker targets the non-windowed client");
        require(!launch.createsNativeWindow(), "worker launch cannot create a native window");
        require(launch.requiresCredentialBroker(), "worker cannot launch without a credential broker");
        require(launch.arguments().stream().noneMatch(arg -> arg.equals("-u") || arg.equals("-C")),
                "worker arguments contain no username or cookie flags");

        boolean secretRejected = false;
        try {
            SessionWorkerLaunchSpec.verifySafeArguments(List.of("-C", "secret-cookie"));
        } catch(IllegalArgumentException expected) {
            secretRejected = true;
        }
        require(secretRejected, "credential-bearing worker arguments are rejected");

        boolean rejected = false;
        try {
            new SessionConservatorySnapshot(List.of(current), "current", false, false,
                    lockedWorker, List.of(), SessionLaunchMode.VIEWER, "", "Invalid");
        } catch(IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "multi-window presentation is rejected");

        Coord compact = SessionConservatoryLayout.fittedSize(UI.scale(1280, 720));
        Coord large = SessionConservatoryLayout.fittedSize(UI.scale(1920, 1080));
        require(compact.x <= UI.scale(1280) && compact.y <= UI.scale(720), "1280x720 layout fits");
        require(large.x <= UI.scale(1920) && large.y <= UI.scale(1080), "1920x1080 layout fits");
        System.out.println("Session Conservatory checks passed.");
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}
