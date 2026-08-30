# MoonFlower Fishing System Relogic

Design brief for mockup selection. This is not an implementation record and
does not claim live client or server behavior.

Selected direction: **C. Tideglass Navigator**, chosen by the user on
2026-08-29. Production work combines the fish carousel, fish-specific ranked
location rail, Tideglass locator, four-stage tackle thread, observed-rig drawer,
and numbered preset crests from that direction. The classic journal remains
available whenever MoonFlower mode is disabled.

Selecting a numbered preset crest opens a read-only `PRESET OVERVIEW` in the
main journal. It displays the saved pole, line, hook, and lure/bait configuration
and recalculates possible catches from exact matching local observations, sorted
by each fish's highest learned percentage. Merely previewing a preset never moves
an item; inventory changes remain behind the explicit Select and Apply & prepare
actions.

Production artwork:

- `client/src/haven/hud/moonflower-fishing-tideglass-mark-v1-alpha.png`
- `client/src/haven/hud/moonflower-fishing-tackle-thread-v1-alpha.png`
- `client/src/haven/hud/moonflower-fishing-locator-ring-v1-alpha.png`

The artwork was generated as transparent raster source and is downsampled with
the client's Lanczos UI-art filter. Motion is state-driven Java rendering rather
than baked animation, so the existing MoonFlower reduced-motion preference can
replace every transition with an immediate static state.

## Component

Name: MoonFlower Fishing System

User goal: Choose a fish, see its best recorded location first, navigate to any
recorded catch location, assemble evidence-backed fishing poles, and save pole
presets for later supervised swapping.

Smallest useful behavior: Replace the Water tab with a fish-first browser. A
selected fish shows ranked recorded locations with an explicit map action. A
separate pole workshop previews available line, hook, bait, and lure choices and
shows only locally observed catch evidence for the selected combination.

Classic behavior that must remain available: Native Fishing action, existing
helper flow, map centering, journal storage, bookmarks, and classic UI behavior
when MoonFlower mode is disabled.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Fish icon and name | recorded fish resource/name | `LEARNED` | Text-only fallback icon |
| Ranked catch locations | local fishing observations grouped by fish | `LEARNED` | Show no recorded locations |
| Best offered chance | saved server choice row for that fish and rig | `LEARNED` | Show `chance unavailable` |
| Map coordinate | recorded segment/grid and tile | `LEARNED` | Disable map action with reason |
| Pole component availability | reachable client inventories | `LIVE` | Empty slot marked unavailable |
| Combination catch list | saved choice rows matching the exact rig | `LEARNED` | Show no observations for this rig |
| Preset composition | local preset storage | `LEARNED` | Missing components called out |
| Current equipped rig | inspected current equipment | `LIVE` | Show unavailable; do not infer |

`Available catches` means observed in saved choice/catch information. It must
never imply that unobserved fish are impossible or that the server guarantees a
catch.

## Visual System

- Reuse `MoonFlowerHudTheme` ink/deep-ink panels, teal active states, gold and
  soft-gold frames/connectors, ivory text, vines, blossoms, slots, and circular
  controls.
- Ruby is reserved for missing/invalid preset components or destructive preset
  deletion.
- Each direction includes original project-local fishing ornament concepts, but
  the selected artwork must be prepared as transparent layered assets before
  implementation.

## Layout

Anchor or default position: movable centered window, preserving the current
saved window position behavior.

Minimum and preferred size: minimum approximately 760x430 logical pixels;
preferred approximately 940x540.

Behavior at 1280x720: single main window; compact fish strip; details collapse
behind selection without covering the portrait hub, chat, or compact map.

Behavior at 1920x1080 and larger: fish rail, ranked locations, detail/map action,
and preset rail remain visible together.

Supported UI scales: existing supported Haven UI scales through `UI.scale`.

Nearby UI that must not be covered: portrait HUD, compact map, belt, chat, and
open inventory windows.

Long-text and unknown-value behavior: ellipsis plus tooltip for item names;
unknown evidence uses explicit unavailable copy and never a fabricated zero.

Movable/resizable state and persistence key: reuse the fishing-system window
position; add a dedicated size/view key only if the chosen concept needs it.

## Interaction

Primary action: select a fish icon, then select a ranked location or pole
component.

Hover/tooltip behavior: fish icons show name and observation count; components
show item name/quality/source container; evidence badges explain provenance.

Keyboard behavior: preserve Escape-to-close and normal Haven focus behavior;
arrow keys move within the active list when practical; Enter activates the
selected map action.

Edit-mode behavior: no special HUD edit-mode interaction.

Classic-mode behavior: current widgets and hit areas remain unchanged when the
MoonFlower theme is disabled.

Fail-closed conditions: a missing map ID disables navigation; missing inventory
components prevent swapping; no observed rig match shows an honest empty state;
ambiguous cursor/pole identity prevents preset application.

## Motion And Accessibility

Motion used:

- 180 ms fish-selection glide with a blossom/teal selection ring.
- 220 ms ranked-location reorder using position interpolation rather than a
  disappearing list.
- 240 ms pole-component socket transition along a gold line path.
- 180 ms catch-card reveal after a rig selection changes.
- 220 ms preset drawer expand/collapse and a brief confirmation pulse after a
  verified save or swap.
- Ambient lure float, water shimmer, and vine sway only while the window is
  focused; no full-window constant motion.

Reduced-motion behavior: selection state changes instantly; ambient loops are
disabled; confirmation uses a static ivory blossom and text.

Non-color state cue: selected tabs use an inset frame and blossom marker;
unavailable data uses explicit text; missing components use a broken-line slot
pattern as well as ruby.

## Mockup Directions

### A. Angler's Field Journal

Fish-first three-column book layout: selectable fish icon rail, ranked location
cards with the best record enlarged, and a map/evidence detail page. Pole
workshop opens as a second page with a large illustrated rod and saved preset
bookmarks.

### B. Moonlit Tackle Atelier

The pole builder is the visual center. Fish icons form a top ribbon; components
orbit the rod in circular selectors; observed catches appear as cards beneath.
Best Spots is a slide-in chart with the top location emphasized.

### C. Tideglass Navigator

Map-and-data layout: fish carousel at the top, ranked location rail beside a
mini-map preview, and a bottom tackle timeline showing pole to line to hook to
lure/bait. Presets are compact numbered crests for fast supervised swapping.

## Code Boundaries

Presentation files: `FishingSystemWindow`, focused fish/location widgets, and a
new pole-workshop widget after a concept is selected.

Immutable state/snapshot file: new fish-location and rig-preview snapshots built
from repository observations and inspected inventory state.

Service or adapter files: `FishingJournalService`/`FishingRepository` for
learned evidence; existing fishing inventory and pole inspection adapters for
live component availability; a small local preset repository.

Shared theme primitive reused or added: reuse window/panel/slot/vine/blossom
primitives; add fishing-specific ornament rendering or transparent artwork only
after visual selection.

Persistence location: MoonFlower client preferences for view/preset metadata;
the existing local fishing database remains the observation source. No map
marker persistence or remote upload.

## Acceptance Criteria

1. The Water tab is removed without losing the water label in catch details.
2. Selecting a fish shows its best recorded location first and all remaining
   recorded locations in descending evidence-backed order.
3. Every fish catch/location row has a direct map action when coordinates exist.
4. Pole, line, hook, and lure/bait selectors show reachable items and never
   imply unknown catch combinations are impossible.
5. Exact-rig observed catch information updates with every component selection.
6. Named presets can be saved, inspected, and applied only through the existing
   fail-closed supervised pole preparation flow.
7. Motion explains selection, sorting, assembly, and confirmation; reduced
   motion preserves every state and action.
8. Classic mode, native Fishing, helper behavior, map markers, and journal data
   remain intact.

## Verification

Deterministic checks: fish-specific spot ordering, missing chance/map handling,
exact-rig filtering, preset serialization, missing-component refusal, and
reduced-motion state transitions.

Scale/layout checks: 1280x720 and 1920x1080 at supported UI scales with long item
and fish names.

Classic-mode regression check: existing fishing checks plus unchanged classic
widget hit areas and tab behavior.

Live checks still required: component discovery across real inventories,
supervised preset assembly/swapping, map centering, animation presentation, and
native/helper catch capture.

Evidence or screenshots to capture: every main view, no-data/unknown states,
missing-preset-component state, reduced-motion state, map navigation, and a
supervised verified preset swap.
