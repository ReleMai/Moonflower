# MoonFlower Multi-Account Session Supervisor UI

Status: active incremental implementation. The embedded launcher rail and
fail-closed offscreen-worker capability model are implemented; live secondary
login/render/input routing remains locked pending its secure bridges.

Research checked: 2026-08-30.

## Final Single-Window Decision

The user requires exactly one visible MoonFlower game window. This decision
supersedes every earlier reference in this document to focusing, restoring, or
showing a second client window. Additional accounts must run as isolated,
non-windowed background session workers and publish renderer frames into the
one embedded Session Conservatory. Selecting a character changes the one
visible render and input target inside MoonFlower; it never invokes Windows
foreground switching and never creates another taskbar window.

## Component

Name: MoonFlower Session Conservatory

User goal: supervise several characters logged into separate Haven accounts,
see a small live view of each isolated worker, and select any one character as
the full render/input target inside the same game window.

Smallest useful behavior: attach the current session to an embedded in-game
Session Conservatory, show a prominent `+` that opens an embedded login form,
show each known account as `Account name +`, enforce one visible surface, and
keep actual background launches locked until a secure non-windowed
worker/render/input bridge exists.

Classic behavior that must remain available: the one MoonFlower window retains
normal login, character selection, world entry, logout, keybinds, and direct
player input. Closing the Conservatory must not log out the selected session.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Process running/exited | tracked `Process`/`ProcessHandle` | `LIVE` | Show `Not tracked` rather than infer |
| Bot WebSocket connected | `BotSessionRegistry` | `LIVE` | Show `Bridge unavailable` |
| Character and world | current client snapshot | `LIVE` | Show account alias and `Character unavailable` |
| Health, stamina, energy | current `ClientStateCollector` snapshot | `LIVE` | Omit the meter; do not show zero |
| Current rendered view | renderer-backed live frame | `LIVE` | Keep last frame with age veil, then replace with unavailable art |
| Last-frame age | supervisor clock minus frame timestamp | `CALC` | Show `No frame received` |
| Session readiness | process, socket, screen, and frame state | `CALC` | Show the contributing unknown state |
| Account label | local non-secret account metadata | `LEARNED` | Show `Unnamed account` |
| Known-account launch rows | labels parsed from local saved-account metadata | `LEARNED` | Show only fresh-login `+` |
| CPU/GPU/memory estimate | measured local process metrics | `CALC` | Omit rather than guess |

No account password, authentication token, cookie, launch token, full command
environment, private map coordinate, or hidden character data may be displayed,
logged, exported, embedded in art, or included in screenshots.

## Visual System

- Use a deep-ink conservatory cabinet framed with shared gold filigree, teal
  vines, ivory labels, blossom nodes, and the existing MoonFlower panel/frame,
  slot, and circular-control vocabulary.
- Each live character occupies a botanical specimen medallion: portrait or
  preview in the center, character name on an ivory ribbon, a world/account
  alias beneath it, and a vine pulse that indicates fresh telemetry.
- The selected session grows a bright-teal vine from its medallion into the
  large preview aperture. Gold alone never indicates selection; the card also
  gains a raised petal silhouette and `SELECTED` text.
- Preview apertures use painted MoonFlower frames with opaque ink backing.
  World imagery must never bleed through transparent ornament corners or make
  status text unreadable.
- Ruby is reserved for a confirmed crash, authentication failure, severe
  health state, destructive stop confirmation, or an expired security
  handshake. Disconnection uses a broken-vine shape plus text, not ruby alone.
- The center art is visibly dynamic: a moonflower opens toward the selected
  session while small leaf veins brighten when fresh frames arrive. This
  decoration must never imply that stale imagery is live.
- Create project-local raster assets only for artwork that shared drawing
  primitives cannot express. Preserve layered source files or generation
  prompts under `docs/mockups/multi-account/`; ship only reviewed, optimized
  transparent PNG/WebP assets in the owning UI package.
- Do not place generic gray buttons, browser-dashboard cards, neon sci-fi
  telemetry, or flat rectangular overlays on top of the artwork.

### Planned Asset Set

1. `session-conservatory-frame`: scalable outer cabinet ornament with opaque
   inner panel and transparent exterior corners.
2. `session-preview-aperture`: nine-slice frame for 16:9 and compact previews.
3. `session-medallion`: idle, selected, ready, stale, and disconnected
   silhouettes; colors are applied by theme primitives where possible.
4. `session-vine-divider`: repeatable connector between the session rail and
   preview stage.
5. `session-empty-bloom`: tasteful no-session/no-frame illustration.
6. `session-state-glyphs`: worker, connection, frame, selected, paused,
   crashed, and attention-needed shapes readable at 16-24 logical pixels.

Do not bake names, statuses, controls, or secret-bearing data into images.

## Layout

Anchor or default position: a movable normal window centered on first open. A
compact always-on-top mini-viewer may be enabled explicitly, but never by
default and never above full-screen games without a visible preference.

Minimum and preferred size: minimum 420 x 270 logical pixels; preferred 820 x
560. The compact mini-viewer is approximately 360 x 230 and shows one preview
plus a scrollable medallion rail.

Behavior at 1280x720: use a left vertical rail no wider than 30% and one 16:9
preview. Collapse performance details and the event ledger. Keep Select, Launch,
and the status label visible; put destructive Stop behind a confirmation menu.

Behavior at 1920x1080 and larger: show a two-column session rail or optional
2x2 read-only preview grid. Keep preview aspect ratio and cap line lengths.

Supported UI scales: 1.0, 1.25, 1.5, and 2.0. At 2.0 use internal scrolling;
do not scale the frame beyond the available work area.

Nearby UI that must not be covered: top-center world clock, upper-left buffs,
minimap, chat input, portrait/combat dock, and native login or
character-selection dialogs.

Long-text and unknown-value behavior: ellipsize aliases in the rail with a
tooltip containing only non-secret metadata. Status and failure reasons wrap in
the details area. Unknown values use explicit unavailable text.

Movable/resizable state and persistence key: persist normal-window bounds,
compact-mode bounds, rail orientation, chosen layout, and preview frame-rate
preference under supervisor-local settings. Do not store a running/selected
state, PID, worker handle, or live token across restart.

## Interaction

Primary action: clicking the large `+` opens a login form inside the
Conservatory stage. Clicking `Account name +` requests direct sign-in when that
account has a secure credential reference; otherwise it opens the same embedded
form with only the account label prefilled. No raw legacy password is copied
into the form or launch snapshot.

After login, clicking a fresh preview or its `Select character` blossom
validates that worker's frame and input capabilities, then atomically changes
the one visible render/input route. On failure, retain the prior ready session
and show an in-window reason.

Hover/tooltip behavior: medallions show character, non-secret account alias,
world, session state, last-frame age, and process uptime. Preview controls show
their exact local effect.

Keyboard behavior: configurable in-game shortcuts select session 1-9 within
the visible MoonFlower window. Shortcuts must not be global and
must never broadcast a gameplay key to more than one client. Escape closes a
menu, then exits compact mode, then closes the Conservatory; it does not
log out or stop a client.

Edit-mode behavior: reposition/resize the normal or compact viewer and reorder
session medallions. Reordering changes presentation only.

Classic-mode behavior: use the same state/service model with a simpler native
window skin if MoonFlower theming is disabled. Do not hide or replace any game
client UI.

Fail-closed conditions:

- Never treat the legacy `savedAccounts` plaintext password value as an
  approved direct-sign-in credential. The launcher rail may extract its label
  only until migration to current-user protected storage exists.
- Never launch if the selected account secret cannot be decrypted by the
  current Windows user or if secure one-time credential delivery is
  unavailable.
- Never fall back to plaintext, reversible base64, command-line credentials,
  inherited credential environment variables, or secret-bearing logs.
- Never associate a process using PID alone; require the expected process
  identity plus a one-time, bot-bound registration handshake.
- Never label a cached frame `LIVE` after its freshness threshold. Show exact
  age, then cover it with the stale veil.
- Never route input to a different session than the visibly selected medallion.
- Never forward mouse or keyboard input through the mini-viewer in Phase 1.
- Never broadcast, mirror, repeat, or queue one gameplay action across clients.
- Never auto-restart after an authentication failure, repeated crash, explicit
  user stop, or version/protocol mismatch.
- Never kill an untracked or identity-mismatched process. Graceful stop must be
  distinct from force termination and require confirmation.

## Motion And Accessibility

Motion used: a short selection-vine growth, a single bloom when a session first
becomes ready, a restrained frame-arrival leaf pulse, and a crossfade between
fresh previews. No constant ornamental swaying in compact mode.

Reduced-motion behavior: selection and state changes snap immediately; frame
content updates without crossfade; state text and glyphs continue to update.

Non-color state cue: every state has a text label and a distinct glyph or vine
shape. Stale previews use an age label and diagonal petal veil. Selected state
uses a raised frame and `SELECTED` label.

## Code Boundaries

Presentation files: new, focused supervisor components under a dedicated
package/module; do not grow `web/src/App.tsx`, `GameUI`, or `LoginScreen`.

Immutable state/snapshot files: `SessionCardSnapshot`, `SessionFleetSnapshot`,
`PreviewFrameMetadata`, and explicit process/connection/frame state enums.

Service or adapter files: process supervisor, secure credential broker, client
registration/session registry, live-frame coordinator, and selected-session
router remain independent services behind interfaces.

Shared theme primitive reused or added: reuse `MoonFlowerHudTheme` colors,
panels, frames, vines, blossoms, slots, and circular controls. Add a reusable
preview aperture/medallion primitive only after proving existing primitives are
insufficient.

Persistence location: supervisor database/settings outside the repository and
outside distributable client packages. Account secrets use Windows current-user
protection; presentation settings contain no secrets.

## Acceptance Criteria

1. Two different Haven accounts can remain logged in through isolated,
   non-windowed workers while exactly one MoonFlower game window is visible.
2. Each session card displays identity and independently fresh telemetry with
   accurate `LIVE`/`CALC` labeling and an explicit stale/unavailable state.
3. One deliberate click atomically selects exactly one render/input target
   without sending gameplay input or disturbing the other session.
4. Closing/restarting the supervisor leaves independently launched clients
   understandable and recoverable; adoption requires a fresh authenticated
   handshake.
5. No credential or durable auth token appears in process command lines,
   inherited environment, logs, audit records, screenshots, IPC payloads,
   exception text, repository files, or packages.
6. The UI visibly belongs to MoonFlower and remains readable at 1280x720,
   1920x1080, and scales 1.0/1.25/1.5/2.0.
7. Reduced motion, text/glyph state cues, keyboard navigation, and predictable
   selection behavior work without relying on color alone.
8. Focused deterministic, security, worker-lifecycle, protocol, layout, and
   classic-mode checks pass without claiming live server or input-route proof.
9. A supervised two-account test records launch, world entry, preview freshness,
   selection swap, disconnect, graceful stop, supervisor restart, and crash behavior
   separately from offline tests.

## Verification

Deterministic checks: state-reducer transitions; one-time token binding and
expiry; stale-frame thresholds; credential-redaction fixtures; process identity
matching; graceful/forced stop boundaries; protocol-version rejection; bounded
preview queues; no input-capability exposure in Phase 1.

Scale/layout checks: snapshot or geometry checks at 1280x720 and 1920x1080 for
1.0, 1.25, 1.5, and 2.0; long names; ten sessions; missing portraits; stale
frames; disconnected and crashed states.

Classic-mode regression check: normal login, character selection, direct
client input, logout, and client close remain unchanged
with the supervisor disabled or unavailable.

Live checks still required: actual simultaneous account permissions; real
login and world entry; GPU/CPU/memory with 2, 4, and 8 clients; renderer capture
cadence while backgrounded; atomic input routing; DPI/multi-monitor layout;
sleep/resume; network loss; server restart; crash recovery; account token
rotation; frame privacy.

Evidence or screenshots to capture: fresh and stale mini-viewer states;
two-session selection sequence; 1280x720 compact layout; 2.0 scale; reduced motion;
disconnected/crashed art; input-route failure fallback; redacted audit
and process-inspection evidence showing no secrets.
