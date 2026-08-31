# MoonFlower Multi-Account Session Supervisor And Mini Live Viewer Prompt

Status: research and phased production design only. No implementation, account
login, process launch, credential migration, or active-task change has been
performed by this document.

Research checked: 2026-08-30.

## Final Architecture Decision: One Visible Game Window

The user explicitly requires all multi-account presentation and interaction to
remain inside one MoonFlower game window. This decision supersedes every older
reference below to multiple visible clients, Windows foreground focus, a
browser dashboard, or taskbar/window swapping.

Keep protocol, credential, resource, and failure isolation through background
session workers, but those workers must be non-windowed and must not create a
second taskbar entry. The primary MoonFlower process owns the only visible
window and embeds the Session Conservatory. Each worker publishes a bounded
render stream and receives input only while it is the single selected target.
"Hot swap" now means atomically changing the selected render/input route inside
that window. The default must fail closed to the current session if a worker,
frame, or input acknowledgement becomes stale.

The first implemented slice is intentionally smaller: it registers the current
session, adds the embedded themed window, enforces the single-visible-surface
invariant, and leaves background launch controls visibly locked until secure
credential delivery and a non-windowed render/input host are implemented.

Repository snapshot used for this design:

- MoonFlower branch: `codex/moonflower-rebrand`
- MoonFlower commit: `c2e7c96bb6aefdae551ddb782cb68b9db858fbeb`
- upstream `dolda2000/hafen-client` HEAD observed during research:
  `bcfc402d0bb218b4f8bc213759a51e4ddd1032d3`

## Mission

Implement a secure, art-led MoonFlower Session Conservatory that supervises
multiple simultaneously logged-in characters on different Haven accounts from
one visible game window. Each additional character remains isolated in a
non-windowed session worker. The host shows correctly freshness-labeled views
and routes the one visible surface and direct input to one selected session.

The word "swap" means changing the selected renderer and input route within the
one MoonFlower window. It does not mean transferring a game session between
accounts, logging two characters from one account at once, or broadcasting one
action to multiple characters.

Build the smallest safe production slice first. Phase 1 is a read-only
embedded host plus launch, selection, and graceful lifecycle controls. Do not expand
Phase 1 into unattended automation, remote Internet access, input multiplexing,
or task orchestration.

## Non-Negotiable Constraints

1. Preserve one OS process, one `UI`, one `RemoteUI`, and one `Session` per live
   character. Do not make `UI.sess`, `BotAgentRuntime.sharedRuntime`, audio,
   renderer state, preferences, resource caches, or map caches multi-tenant.
2. Require separate accounts for simultaneous characters. A character choice
   on an already-active account is not a second simultaneous session.
3. Keep every client visibly launchable and directly controllable without the
   supervisor. The supervisor is orchestration, never the only recovery path.
4. Keep the platform bound to loopback. Any future LAN or remote access is a
   separate threat model and separate user approval.
5. Do not print, persist, transmit, screenshot, audit, or package account
   passwords, auth tokens, cookies, launch tokens, decrypted secrets, or full
   child environments.
6. Phase 1 mini viewers are read-only. No click-through, remote mouse, remote
   keyboard, synchronized commands, macros, or input broadcast.
7. Unknown/stale data must be visibly unknown/stale. A cached screenshot is not
   a live view merely because it is on screen.
8. Preserve classic UI behavior and all native login, character-selection,
   keybind, Escape, and server-message behavior.
9. Do not change `docs/CURRENT_TASKS.md` until the user explicitly promotes this
   design to active implementation work.
10. Before building or packaging the Java client, close all MoonFlower JVMs and
    run `scripts/assert-client-stopped.ps1`. Offline checks do not prove real
    login, simultaneous accounts, live rendering, or atomic input switching.

## Research Findings To Use

### Upstream client architecture

The authoritative upstream client describes `Session.java` as the game
protocol connection, `Widget.java` as the UI base, and its top-level client
window/main loop as the owner of rendering and event dispatch. MoonFlower's
current checkout similarly carries the active session through `UI.sess`, runs
server widgets through `RemoteUI`, and returns a new `RemoteUI` only when the
current runner changes session. Treat this as evidence for process isolation,
not as an invitation to add a fleet container inside `GameUI`.

Compatibility claims must be checked against the exact Seatribe ref at
implementation time. Preserve MoonFlower integration points rather than
copying whole upstream files.

### Current repository capabilities

Reuse and harden these existing seams:

- `AccountList` provides a saved-account experience, but its legacy
  `savedAccounts` preference concatenates account names and secrets. Treat this
  as migration input only, not the production credential store.
- `Bootstrap`, `AuthClient.TokenCred`, `gettoken`, `settoken`, and `rottokens`
  already understand Haven authentication tokens. Prefer a scoped, revocable
  client auth token after an explicit password bootstrap rather than repeatedly
  exposing a durable password.
- `BotProcessSupervisor` already launches one client process per bot profile,
  tracks it, redirects logs, and passes client identity/configuration.
- `BotSessionRegistry` already issues a bot-bound, single-use five-minute
  launch token and tracks bot/operator WebSockets.
- `BotAgentRuntime` already connects a client to the local operator bridge,
  emits fast/snapshot state, handles focus and live-feed commands, and creates
  renderer-backed captures.
- `ClientStateCollector` already supplies character/world, session state,
  position, vitals, inventory, equipment, tasks, and other telemetry. The
  Session Conservatory must request a minimal allowlisted view rather than
  automatically exposing the full snapshot.
- `ScreenshotCaptureService` captures the rendered UI and encodes JPEG. It is a
  useful proof of the render seam, but its 1280-pixel, 0.72-quality screenshot
  defaults are too heavy for a many-session thumbnail rail without measurement.
- `LiveFeedService` already keeps latest frames and bounded subscriber queues,
  exposes a multipart JPEG feed, and can save the current frame.
- `media-gateway` already converts JPEG frames to WebRTC and keeps replay data.
  Do not make replay/recording a dependency of the Phase 1 mini viewer.
- The legacy `BotFleetService.focusClient`/`FOCUS_CLIENT` path is evidence of
  process control only; do not reuse its OS-window focus behavior.
- `web/src/App.tsx` already contains a focused live stage and a grid of online
  bot previews. Extract reusable components/state rather than adding more
  responsibilities to this existing large file.
- `MoonFlowerHudTheme`, `MoonFlowerScreenTheme`, `MoonFlowerHudAssets`, and the
  portrait HUD establish the ink, teal, gold, ivory, ruby, vine, blossom, slot,
  medallion, and circular-control visual language.

### Security gaps that block production status

Resolve these before enabling saved-account launch from the new UI:

1. `AccountList` stores legacy account values in normal client preferences.
   Design an explicit, user-visible migration into the secure store, verify the
   migrated account, and delete the legacy secret only after success. Never
   include secret contents in migration logs or tests.
2. `BotProcessSupervisor` currently places `HAVEN_ACCOUNT_SECRET` in the child
   environment. Replace this with a one-time credential broker over an
   authenticated, local, bot-bound IPC handshake. The child should receive only
   non-secret identity plus an ephemeral bootstrap capability, redeem it once,
   authenticate, zero transient buffers where practical, and never echo it.
3. `WindowsDpapiProtectionService` currently falls back to reversible base64
   outside its working Windows DPAPI path. Remove the production fallback and
   fail closed. If another OS is supported later, require a real OS credential
   store or an explicitly designed encrypted vault.
4. Do not return launch registration tokens to browser UI code unless the
   operator actually needs them. The server should keep broker and registration
   capabilities internal, scoped, short-lived, single-use, and bot-bound.
5. Redact custom launch commands, query strings, exception causes, process info,
   and audit details. Do not assume a field is safe because the current UI does
   not display it.
6. Add a protocol version and capability negotiation before a supervisor asks
   a worker for frames or selection. A mismatched worker stays unavailable but
   unsupported; it is never sent speculative commands.

Windows DPAPI current-user scope is appropriate for a local single-user tool:
by default, protected data can normally be decrypted only by the same user on
the same machine. Document that an administrator-reset Windows password can
make old protected data unrecoverable. Provide export/import only as encrypted,
explicitly user-initiated future work—not Phase 1.

### Platform and game constraints

- Java `ProcessBuilder` is suitable for isolated child configuration and
  working directories, while `Process`/`ProcessHandle` provide PID, liveness,
  descendants, termination, and asynchronous `onExit` handling. Track the
  actual returned `Process`, not a process found later by name.
- Do not use Windows foreground activation for session switching. The only
  visible MoonFlower window remains focused; an in-game selection atomically
  changes its renderer and direct-input route.
- The official Haven Q&A is permissive toward custom clients and describes its
  bot philosophy, but it is not a blanket promise about every future behavior.
  Keep gameplay operations legal at the server-protocol level and preserve a
  user-supervised, auditable boundary.
- Community evidence consistently describes simultaneous characters as
  separate-account sessions. Treat that as current practical guidance, not a
  server protocol guarantee; verify with the user's real accounts in a
  supervised test.

## Target Architecture

```text
MoonFlower game window with embedded Session Conservatory
        |
        | in-process presentation + authenticated loopback worker protocol
        v
Local Supervisor Server
  - account metadata + DPAPI secret vault
  - one-time credential broker
  - session/process registry
  - protocol/capability negotiation
  - preview subscription coordinator
  - audit and redaction boundary
        |
        | one ephemeral launch capability per process
        +--------------------+--------------------+
        v                    v                    v
Non-windowed worker A  Non-windowed worker B  Non-windowed worker N
one UI/RemoteUI/Session one UI/RemoteUI/Session one UI/RemoteUI/Session
        |                    |                    |
        +--- allowlisted state + renderer frames + heartbeat ---+
```

The supervisor owns account-to-profile mapping and worker lifecycle. The one
visible game client owns presentation and selects exactly one renderer/input
route. Each worker owns authentication consumption and one game protocol
session. The legacy web dashboard is not part of this feature surface.

### Required domain model

Introduce small immutable records and enums rather than passing database or
JSON objects through the UI:

- `AccountAlias`: id, display label, username hint if explicitly allowed,
  secret availability, last verified time; never the secret.
- `SessionProfile`: id, account id, preferred character/world, client install
  id, display order, preview preference, restart policy.
- `ClientInstall`: normalized approved root, launcher identity/hash or version,
  working directory, data/profile directory strategy.
- `ManagedSessionId`: stable supervisor UUID unrelated to PID, account name, or
  character name.
- `ManagedProcessIdentity`: session id, expected launch nonce hash, PID, start
  time, executable/launcher identity, registration state.
- `SessionState`: `STOPPED`, `LAUNCHING`, `AUTHENTICATING`, `CHARACTER_SELECT`,
  `ENTERING_WORLD`, `READY`, `STALE`, `DISCONNECTED`, `STOPPING`, `EXITED`,
  `CRASHED`, `AUTH_FAILED`, `VERSION_MISMATCH`, `ATTENTION_REQUIRED`.
- `ConnectionState`, `FrameState`, and `SelectionState` remain separate so the
  UI never flattens contradictory evidence into one green/red lamp.
- `PreviewFrame`: bytes or stream handle plus session id, monotonic sequence,
  captured-at, received-at, dimensions, media type, source, and privacy flags.
- `SessionCardSnapshot`: minimal UI allowlist—display identity, process state,
  connection state, screen, vitals if known, task label if explicitly allowed,
  frame metadata, and actionable failure reason.

### State machine and invariants

All transitions run through one session aggregate/service and are testable.

```text
STOPPED -> LAUNCHING -> AUTHENTICATING -> CHARACTER_SELECT
       -> ENTERING_WORLD -> READY

READY -> STALE -> READY
READY -> DISCONNECTED -> READY | STOPPING | EXITED
any running state -> ATTENTION_REQUIRED | AUTH_FAILED | VERSION_MISMATCH
any tracked state -> STOPPING -> EXITED
unexpected process exit -> CRASHED
```

Invariants:

- one session profile has at most one tracked live process;
- one account may have at most one READY/entering session unless real server
  behavior is explicitly verified and the product rule is changed;
- a process cannot become READY from PID/liveness alone;
- a WebSocket cannot register without the exact unexpired, single-use,
  session-bound launch capability and expected protocol version;
- a frame cannot become fresh if its sequence regresses or timestamps exceed
  configured skew bounds;
- selection applies only to a registered worker selected by the current in-game
  action;
- supervisor restart does not silently relaunch or kill clients;
- auto-restart is off in Phase 1 and never occurs for auth failure, version
  mismatch, explicit stop, or repeated crash.

## Secure Launch And Credential Flow

Implement this flow before UI launch controls are enabled:

1. The operator selects a non-secret `SessionProfile` and clicks Launch.
2. The server validates loopback binding, authenticated operator session,
   approved client install root, account availability, one-session-per-profile,
   and resource policy.
3. The server creates two separate ephemeral values:
   a registration capability and a credential-redemption capability. Store only
   hashes plus scope, session id, expiry, and consumed state.
4. Start the child with non-secret profile/session ids and the registration
   bootstrap only. Do not put account password/token in command arguments,
   environment variables, working-directory files, or inherited streams.
5. The child connects to loopback, verifies expected server origin/protocol,
   proves possession of the registration capability, and negotiates
   capabilities.
6. Over the authenticated bot-bound channel, the child requests the credential
   once. The server decrypts only at this moment, sends it only to that bound
   client, marks the redemption consumed, and expires both capabilities.
7. Prefer issuance/use of a Haven auth token supported by existing
   `Bootstrap`/`AuthClient` behavior. If a password is required for bootstrap,
   do not retain another plaintext copy.
8. The client authenticates and reports only coarse states and sanitized error
   codes (`AUTH_REJECTED`, `TOKEN_EXPIRED`), never server error text containing
   secret material.
9. On logout, failure, timeout, or process exit, revoke outstanding capabilities
   and clear session-specific frame/state caches.

Add tests proving expiry, wrong-bot rejection, replay rejection, double redeem,
wrong protocol, server restart, client crash during redemption, log redaction,
and no secret in process command/environment inspection.

## Preview Pipeline

### Phase 1 transport

Use the existing renderer capture and `LiveFeedService`, but add a dedicated
thumbnail profile rather than invoking full screenshots repeatedly:

- default thumbnail target: 480x270 or lower after measurement;
- default cadence: 1 fps for grid cards, up to 2-4 fps only for the selected
  preview; never promise a frame rate before profiling;
- encode off the UI thread after renderer readback;
- one outstanding capture and one newest-frame queue per client; drop obsolete
  frames instead of building latency;
- monotonic sequence numbers and captured/received timestamps;
- adaptive backoff when minimized, occluded, CPU/GPU constrained, disconnected,
  or no subscribers exist;
- unsubscribe stops capture work after a short bounded idle timeout;
- cap frame dimensions, encoded bytes, subscriber count, and aggregate memory;
- never persist frames unless the operator explicitly invokes Save;
- conceal or suppress previews at login/password entry and other defined
  sensitive screens.

The large selected view may use the existing WebRTC gateway if measured latency
or bandwidth justifies it. The small grid should not require replay buffers or
video encoding. Keep MJPEG/WebRTC transport adapters behind one preview-source
interface.

### Freshness semantics

- `LIVE`: last frame is within the measured freshness threshold and the client
  heartbeat is current.
- `STALE 3.2s`: retain the last frame behind a visible veil and exact age.
- `NO SIGNAL`: heartbeat or process identity is absent beyond the hard timeout;
  replace world imagery with themed unavailable art.
- Never reset freshness merely because the same cached frame was re-delivered.
- Show capture time and transport state separately from process/socket state.

### Privacy and visibility

Define a minimal default preview crop/full-frame policy. The preview may contain
chat, map labels, coordinates, kin names, or other private imagery. Provide a
per-profile privacy mask later only after the base feed is stable. Until then,
bind all feeds to authenticated loopback access, set no-store cache headers,
avoid browser/service-worker caching, and do not include previews in normal
audit records.

## Embedded Hot Swap

Implement `SelectedSessionRouter` behind a small interface and test fake.

1. The in-game card click creates one selection request for a registered,
   ready session id.
2. Freeze new input routing while the target frame stream and input capability
   are validated.
3. Atomically change the visible surface and input destination; never allow a
   frame from one session to be paired with input to another.
4. Require a fresh selected-session acknowledgement before enabling gameplay
   input. Return `SELECTED`, `STALE`, `WORKER_MISSING`, `VERSION_MISMATCH`, or
   `INPUT_NOT_READY`.
5. On failure, retain or restore the previous ready session and show the reason
   inside the one game window.
6. Never create, restore, focus, or flash another OS window.

## UI And Art Direction

Follow the completed checklist in
`docs/design/MOONFLOWER_MULTI_ACCOUNT_SESSION_SUPERVISOR_UI.md`.

Build a MoonFlower Session Conservatory, not a generic admin dashboard:

- deep-ink botanical cabinet and opaque preview apertures;
- gold filigree, teal living vines, ivory ribbons, restrained ruby danger;
- character specimen medallions on a reorderable session rail;
- selected vine connecting the rail to the main preview;
- a visibly dynamic moonflower that opens toward the selected session and
  pulses only when genuinely fresh frames arrive;
- text plus distinct shapes for every process/connection/frame state;
- stable Select action and separately confirmed graceful Stop action;
- compact single-preview mode and an optional read-only 2x2 grid on large
  screens;
- no generic browser cards, gray desktop controls, floating debug text, or
  unframed video rectangles.

Use existing theme primitives first. If custom raster art is needed, create an
art-direction sheet and at least three concept variants before choosing one.
Keep generated prompts/sources in `docs/mockups/multi-account/`, review alpha
edges at native scale, then place only optimized runtime assets beside the
owning component. Artwork must contain no text or account data.

### Required visible states

Design and test: no sessions, stopped, launching, authenticating, character
select, entering world, ready/fresh, selected, input ready, stale frame,
bridge disconnected, worker exited, crashed, auth failed, version mismatch,
selection rejected, graceful stop pending, and attention required.

### Controls in Phase 1

- Add/import account metadata and explicitly migrate a legacy saved account.
- Create/edit/reorder a session profile.
- Launch one stopped profile.
- Select one ready worker as the visible render/input target.
- Start/stop preview subscription if automatic subscriber counting is not yet
  reliable.
- Gracefully stop with confirmation; force termination only after timeout and a
  second explicit confirmation.
- Open sanitized diagnostics showing state transitions, version, timestamps,
  exit code, and redacted log tail.

Exclude pause/resume automation, takeover, arbitrary task dispatch, and remote
input from the Session Conservatory Phase 1 surface even if older operator APIs
already expose them.

## Implementation Boundaries

Do not add another god file. Suggested boundaries may be adapted to repository
conventions after inspection:

### Server

- `SessionSupervisorService`: aggregate/state machine only.
- `SecureCredentialBroker`: DPAPI vault access and one-time redemption.
- `ManagedProcessRegistry`: actual returned `Process`, start time, identity,
  `onExit`, descendants, graceful/forced termination boundary.
- `ClientRegistrationService`: launch capability, protocol/capability handshake.
- `PreviewCoordinator`: subscriber counts, cadence policy, newest-frame queues,
  freshness.
- `SelectedSessionRouter`: atomic frame/input selection contract.
- Existing `BotProcessSupervisor`, `BotSessionRegistry`, `LiveFeedService`, and
  `BotFleetService` should be decomposed or adapted in small patches, not
  replaced wholesale.

### Client

- Keep `BotAgentRuntime` as transport glue; extract protocol version,
  credential redemption, preview producer, and selected-input routing
  into focused collaborators.
- Keep `ScreenshotCaptureService` for explicit saved screenshots; create a
  separate bounded thumbnail producer or configurable encoder for live cards.
- Add a minimal `SessionTelemetryAdapter` that allowlists only Conservatory
  fields instead of serializing every `ClientStateCollector` detail.
- Do not add fleet state to `GameUI`, `Glob`, `Window`, `LoginScreen`, or
  `AccountList`.

### Embedded game-window UI

- Keep the in-game session rail, preview stage, status glyphs, lifecycle
  controls, and diagnostics in a dedicated `haven.multisession` package.
- Use immutable snapshots for orthogonal worker/connection/frame/selection
  state; do not scatter status strings through `GameUI`.
- Reuse `MoonFlowerHudTheme` directly and keep `GameUI` integration limited to
  construction, visibility, persistence, and the feature-vine action.
- The legacy browser dashboard must not open or become a dependency of the
  embedded Session Conservatory.

### Protocol

- Add versioned request/event envelopes to `shared-protocol` and make the Java
  client consume the same definitions or a generated/verified compatible
  schema.
- Cap message/frame sizes and reject unknown privileged commands.
- Define request ids, session ids, sequence numbers, timestamps, result codes,
  capability flags, and idempotency for lifecycle operations.
- Never make string enum fallbacks perform an action.

## Phased Delivery Plan

### Phase 0 — Security and measurement gate

Keep the embedded first-slice UI read-only while the security gate is built.

- Audit all current secret storage, process environment/command construction,
  query parameters, WebSocket authentication, logging, audit, screenshots,
  database files, and packages.
- Implement fail-closed DPAPI storage and one-time credential redemption.
- Add protocol/capability negotiation and redaction tests.
- Measure one current client at idle and active states: heap, working set, CPU,
  GPU, renderer capture cost, JPEG size/encode time, frame latency, and behavior
  while minimized.
- Decide data/profile directory isolation. Back up the exact live paths before
  any experiment. Shared preferences/map caches must be proven safe or given
  explicit per-profile roots; do not guess.

Exit: two manually launched processes can securely register with sanitized
state and no secrets visible in process inspection or logs.

### Phase 1 — Read-only Session Conservatory

- Implement session profile/state model and process adoption/launch tracking.
- Implement minimal session telemetry, low-rate preview subscriptions,
  freshness, and compact/focused layouts.
- Implement user-initiated in-window selection with stale/input-not-ready fallback.
- Provide launch, graceful stop, and sanitized diagnostics only.
- Produce/review custom MoonFlower art and deterministic UI states.

Exit: two different accounts remain logged in, both previews are independently
fresh, and repeated in-window switching selects exactly the intended
client without gameplay input forwarding.

### Phase 2 — Reliability and scale

- Validate 2, 4, and 8 client resource budgets; set an evidence-based soft cap
  and warning, not an arbitrary promise.
- Add adaptive preview cadence, selected-view priority, subscriber-driven
  capture, backpressure metrics, sleep/resume handling, and supervisor restart
  adoption.
- Add optional per-profile data-root isolation if Phase 0 proves shared paths
  unsafe.
- Add crash loop suppression and explicit recovery guidance. Auto-restart
  remains opt-in and cannot cover auth/version failures.

### Phase 3 — Quality-of-life expansion

Only after Phase 1-2 live evidence:

- named layouts/workspaces and monitor-aware placement;
- privacy masks for chat/map regions;
- attention rules derived from allowlisted live state;
- optional large WebRTC selected view and explicit replay controls;
- local session journal and performance dashboard;
- encrypted backup/restore of non-secret profiles, with separately designed
  credential recovery.

### Phase 4 — Explicit supervised controls

Treat as a new project and security review. Possible features include
single-session manual takeover, per-session pause, or task controls already
present in the operator platform. Never introduce input broadcast, hidden
automation, or multi-character action mirroring as a natural extension of the
viewer.

## Performance Budget And Backpressure

Establish budgets from measurement and encode them in tests/configuration:

- maximum managed sessions and separately maximum active previews;
- selected versus background preview frame rates;
- maximum capture dimension and encoded frame bytes;
- maximum aggregate live-frame memory and subscribers;
- maximum UI-thread readback duration and encoder queue depth;
- heartbeat and stale/no-signal thresholds;
- process launch concurrency and timeout;
- graceful-stop timeout before force-stop becomes available;
- log tail size and diagnostic retention.

Use newest-frame-wins semantics. A slow viewer should skip old frames, never
cause the client to render faster, and never build an unbounded queue. Prefer a
lower frame rate with accurate freshness over a delayed "smooth" view.

## Failure And Recovery Matrix

Define UI state, automatic action, user action, and audit event for at least:

- wrong password/token, expired credential, locked credential store;
- duplicate account/profile launch;
- launcher missing or outside approved root;
- registration expiry/replay/wrong bot;
- protocol or capability mismatch;
- game server unreachable, disconnect, reconnect, logout;
- renderer capture timeout, encoder error, stale/no frames;
- supervisor/server/media gateway restart;
- child exits normally, crashes, hangs, spawns descendants, or refuses stop;
- selection rejected, input route missing, worker backgrounded, monitor removed, DPI change;
- machine sleep/resume and clock change;
- database locked/corrupt and DPAPI data unrecoverable;
- operator session expiry and browser refresh;
- resource budget exceeded.

Failures must preserve direct control of already running game windows. Never
hide an authentication or crash loop behind decorative "reconnecting" art.

## Verification Plan

### Automated security checks

- Scan source, built artifacts, database fixtures, logs, audit JSON, command
  arrays, process environment fixtures, WebSocket messages, REST responses, and
  screenshots for canary secrets.
- Prove production refusal when DPAPI is unavailable; no base64/plain fallback.
- Prove one-time capabilities are random, hashed at rest, bot/session-bound,
  short-lived, single-use, revoked on exit, and rejected after restart if
  policy requires it.
- Prove all privileged endpoints and preview streams require authenticated
  loopback operator access.
- Prove diagnostics redact query strings, launch commands, secrets, tokens,
  cookies, headers, and environment values.

### Automated lifecycle/protocol checks

- State-machine transition table and invalid transitions.
- Duplicate launch races and concurrent stop/selection requests.
- `Process.onExit` updates once with correct classification.
- Graceful stop precedes force termination and never targets identity mismatch.
- Protocol mismatch, unknown command, duplicate request, and out-of-order frame.
- Supervisor restart/adoption with no silent launch or kill.

### Automated preview/UI checks

- Newest-frame backpressure, dimensions/byte caps, subscriber cleanup, stale and
  no-signal transitions, privacy-screen suppression.
- Layout snapshots/geometry at 1280x720 and 1920x1080, scales 1.0/1.25/1.5/2.0,
  long names, 1/2/4/8/10 sessions, missing art, every failure state.
- Reduced motion, keyboard focus order, screen-readable labels, non-color cues,
  and classic-mode regression.

### Supervised live matrix

Record PASS, FAIL, or NOT RUN separately for:

1. backup of resolved preferences, maps, routes, and account-related data;
2. two permitted accounts launched and logged into distinct characters;
3. character/world identity and vitals match each visible client;
4. preview freshness/latency while selected and backgrounded;
5. 30 repeated A/B selections, including input-not-ready fallback;
6. disconnect/reconnect and logout/login without cross-account state leakage;
7. graceful stop, stop timeout, crash, supervisor restart, and machine sleep;
8. multi-monitor/DPI/work-area changes;
9. resource measurements at 2, then 4, then 8 clients only if prior step is
   healthy;
10. process explorer/log/package audit showing no canary secret.

Compilation, unit tests, mock windows, and sample JPEGs do not prove any item in
this live matrix.

## Definition Of Done For Phase 1

Phase 1 is complete only when:

- the credential security gate is complete with no insecure fallback;
- two separate-account client processes remain simultaneously usable;
- each preview and status belongs to the correct authenticated session;
- a click selects exactly one visible render/input route or clearly reports why
  the route stayed on the previous session;
- mini-viewers never forward gameplay input;
- previews degrade honestly from fresh to stale to unavailable;
- supervisor/browser/server failure does not strand or unexpectedly kill a
  running game client;
- custom MoonFlower art is integrated, readable, scalable, reduced-motion
  compatible, and not a generic overlay;
- focused automated gates pass and the supervised live matrix is recorded;
- documentation explains architecture, operations, credential recovery,
  resource limits, and remaining unverified behavior.

## Explicit Non-Goals

- several Haven sessions inside one JVM or one `GameUI`;
- simultaneous characters on one account unless the server explicitly supports
  and the user separately requests it;
- remote Internet/LAN exposure;
- browser-controlled gameplay in Phase 1;
- input broadcasting, multibox mirroring, or one-to-many commands;
- hidden or headless clients;
- automatic credential scraping/import without confirmation;
- plaintext/base64/env/command-line secret delivery;
- automatic crash relaunch by default;
- claiming live correctness from builds or offline tests;
- modifying the Steam package, launcher cache, Workshop item, or current active
  task as part of this design prompt.

## Research Sources

- [Official upstream Haven client repository and source overview](https://github.com/dolda2000/hafen-client)
- [Official Haven Q&A on bots and custom clients](https://www.havenandhearth.com/portal/paq)
- [Oracle Java 21 ProcessBuilder guide](https://docs.oracle.com/en/java/javase/21/core/attributes-that-processbuilder-manages.html)
- [Oracle Java 21 ProcessHandle guide](https://docs.oracle.com/en/java/javase/21/core/methods-process-handle-class.html)
- [Oracle asynchronous process-exit handling](https://docs.oracle.com/en/java/javase/21/core/managing-processes-asynchronously-onexit-method.html)
- [Microsoft foreground-window permission behavior](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-allowsetforegroundwindow)
- [Microsoft DPAPI/CryptProtectData example and scope notes](https://learn.microsoft.com/en-us/windows/win32/seccrypto/example-c-program-using-cryptprotectdata)
- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [Haven forum discussion of simultaneous characters on separate accounts](https://www.havenandhearth.com/forum/viewtopic.php?f=42&t=53656)

Forum/community statements are context, not authoritative protocol or policy.
Recheck external sources and the exact upstream ref when implementation begins.
