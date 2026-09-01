package haven.multisession;

import haven.Coord;
import haven.UI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Deterministic foundation checks; no login or live-session behavior is claimed. */
public final class SessionConservatoryChecks {
    private SessionConservatoryChecks() {
    }

    public static void main(String[] args) throws Exception {
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
        require(!view.backgroundWorkersEnabled(), "a manually locked capability cannot launch workers");
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
        require(detected.state() == SessionWorkerReadiness.State.READY,
                "built-in worker protocol and offscreen renderer are ready");

        SessionWorkerProfile profile = new SessionWorkerProfile(
                "second-hearth", "Account two", "Alt Hearthling", "game.havenandhearth.com", 640, 360
        );
        SessionWorkerLaunchSpec launch = SessionWorkerLaunchSpec.offscreen(profile);
        require(launch.mainClass().equals("haven.multisession.SessionWorkerMain"),
                "worker targets the isolated protocol entry point");
        require(launch.usesHeadlessClient(), "worker entry point uses the non-windowed client");
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

        Coord minimum = SessionConservatoryLayout.clampSize(Coord.of(1, 1), Coord.of(2000, 1200));
        require(minimum.x >= SessionConservatoryLayout.MINIMUM.x &&
                        minimum.y >= SessionConservatoryLayout.MINIMUM.y,
                "resizable window has a safe viewport minimum");

        byte[] nonce = new byte[32];
        byte[] cookie = new byte[32];
        Arrays.fill(nonce, (byte)7);
        Arrays.fill(cookie, (byte)9);
        byte[] auth = SessionWorkerProtocol.auth(nonce, "Account two", cookie);
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        SessionWorkerProtocol.write(new DataOutputStream(wire), SessionWorkerProtocol.AUTH, auth);
        SessionWorkerProtocol.Message roundTrip = SessionWorkerProtocol.read(
                new DataInputStream(new ByteArrayInputStream(wire.toByteArray())));
        SessionWorkerProtocol.AuthPayload decoded = SessionWorkerProtocol.decodeAuth(roundTrip.payload);
        require(roundTrip.type == SessionWorkerProtocol.AUTH && decoded.username.equals("Account two") &&
                        Arrays.equals(decoded.nonce, nonce) && Arrays.equals(decoded.cookie, cookie),
                "private authentication protocol round-trips without command-line secrets");
        SessionWorkerProtocol.Input decodedInput = SessionWorkerProtocol.decodeInput(
                SessionWorkerProtocol.input(new SessionWorkerProtocol.InputBuilder(
                        SessionWorkerProtocol.InputType.MOUSE_DOWN, 12, 34, 1, 0, 0, UI.MOD_SHIFT, (char)0)));
        require(decodedInput.type == SessionWorkerProtocol.InputType.MOUSE_DOWN && decodedInput.x == 12 &&
                        decodedInput.y == 34 && decodedInput.button == 1,
                "viewport input protocol preserves pointer targeting");
        System.out.println("Session Conservatory checks passed.");
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}
