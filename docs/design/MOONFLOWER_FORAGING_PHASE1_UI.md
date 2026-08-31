# Botanical Wayfinder - Phase 1 UI Brief

## Component

Name: Botanical Wayfinder

User goal: Select any cataloged forageable, choose a compass direction or
checkpoint route, and supervise a safely stoppable gather run.

Smallest useful behavior: A world-scoped all-forageables selector, live
preflight/status, route progress, an interactive direction flower, event
history, and Start/Pause/Stop.

Classic behavior that must remain available: Native map movement, checkpoint
routes, inventory interaction, Flower Menu picking, keybinds, and Escape.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Complete herb catalog | reviewed local atlas | `GUIDE` | Keep entries selectable but never fabricate targets |
| Observed herb badge/name | current loaded OCache resources | `LIVE` | Retain GUIDE identity and label |
| Selected resource identities | local world-scoped profile | `LEARNED` | Empty selection blocks Start |
| Travel direction | local world-scoped profile | `LEARNED` | Default to North |
| State and reason | controller state machine | `CALC` | Show unavailable and remain idle |
| Route point/progress | copied checkpoint route | `LIVE` / `CALC` | Missing route blocks Start |
| Inventory free cells | main inventory widgets and locks | `CALC` | Unreadable blocks Start |
| Target distance/bearing | live player and target coordinates | `CALC` | Hide direction and distance |
| Session yield/events | acknowledged controller transactions | `LEARNED` | Start at zero |

## Visual System

The window reuses `MoonFlowerHudTheme` ink panels, gold frames, teal active
vines, ivory text, ruby danger cues, blossoms, and leaf controls. No new bitmap
art or unrelated palette is introduced.

## Layout

Default position: centered and then persisted as `wndc-foragingWayfinder`.

Preferred size: 720x520 logical pixels; compact minimum: 620x440.

At 1280x720 the window is fitted on-screen. Larger screens retain the compact
information density. All dimensions use `UI.scale`; the herb catalog scrolls
rather than expanding the window. Long reasons are clipped to a dedicated
status band and preserved in the event ledger.

## Interaction

Primary action: click any herb card to toggle PICK/SKIP, choose Route or one of
eight compass directions, then Start. Select All and Clear provide bulk edits.

Keyboard: Ctrl+Alt+G toggles the window; Ctrl+Shift+F12 is emergency stop;
Escape pauses an active run before allowing the window to close.

Fail-closed conditions: unreadable or critical vitals, combat, logout, cursor
item, missing route or inventory, reserve exhaustion, unsafe/unknown path,
manual map input, target mismatch, or acknowledgement timeout.

## Motion And Accessibility

Motion used: a restrained blossom pulse during active work and directional
petal/needle rotation toward the current target.

Reduced-motion behavior: static blossom and target direction.

Non-color state cue: every state has a text name and precise reason; button
labels and phase text do not depend on color.

## Code Boundaries

Presentation: `client/src/haven/foraging/ForagingWindow.java`

Immutable state: `ForagingProfile`, `ForagingSnapshot`, `ForagingTarget`

Services: scanner, route planner/map safety, inventory, repository

Controller: `ForagingController`

Persistence: `ClientData.sqlite("foraging.db")`; active runs are never saved or
auto-resumed.

## Acceptance And Verification

Phase 1 follows the acceptance criteria in the production prompt. Deterministic
checks cover exact classification, deterministic safe-path planning, diagonal
blocking, unknown/deep-water rejection contracts, inventory masks/locks, and
profile persistence. Build and checks are offline evidence only. Supervised
visual, Pick acknowledgement, manual takeover, and cliff behavior remain
separate live checks.
