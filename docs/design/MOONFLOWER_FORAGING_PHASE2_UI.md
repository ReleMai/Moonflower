# Botanical Wayfinder - Phase 2 UI Brief

## Component

Name: Botanical Wayfinder route planner

User goal: choose the exact forageables to collect, plot a bounded path on a
live map, and supervise collection as the character walks through that path.

Smallest useful behavior: every catalog entry has an icon; clicking its icon
toggles PICK/SKIP presentation; the map accepts numbered route points; and a
manually started run follows the route, searches its collection corridor, and
acknowledges one exact herb pick at a time.

Classic behavior that must remain available: native map movement, checkpoint
routes, inventory interaction, Flower Menu picking, keybinds, and Escape when
the Botanical Wayfinder is idle or paused.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Herb icon | current resource image, with local ground-resource fallback | `LIVE` | Show the missing-resource icon |
| Herb selection | world-scoped foraging profile | `LEARNED` | Treat missing selection as SKIP |
| Visible forageable | loaded `OCache` Gob/resource | `LIVE` | Do not fabricate a target |
| Route points | map click converted to current map-segment coordinates | `CALC` / `LEARNED` | Reject points from another or unreadable segment |
| Route corridor | configured route and corridor width | `CALC` | Do not collect outside the corridor |
| Player and target position | current player/Gob coordinates | `LIVE` | Pause |
| Path safety | loaded terrain, ridges, collision Gobs, and local A* | `CALC` | Treat unknown data as blocked |
| Pick result and yield | target removal plus compatible inventory increase | `LEARNED` | Pause on ambiguous acknowledgement |
| State and reason | controller state machine | `CALC` | Show the precise reason |

## Visual System

The window keeps the MoonFlower ink, teal, gold, ivory, ruby, panel, vine,
blossom, slot, and circular-control vocabulary. Herb cards use an ivory
specimen slot: the selected PICK icon is opaque and the SKIP icon is visibly
translucent, with text labels so state is not communicated by color alone.

The right side is a framed map panel. A teal route line joins numbered gold
points, the player is marked with an ivory blossom, and the current target uses
a gold marker. Ruby is reserved for Stop and failure/danger text.

## Layout

Anchor/default position: centered on first display and persisted as
`wndc-foragingWayfinder`.

Preferred size: 820x600 logical pixels; the frame fits the available client
area and keeps a scrollable herbarium at smaller sizes.

At 1280x720 the herbarium and map remain side by side; the event ledger
collapses to the latest short events and map/status content gets priority.
At 1920x1080 and larger the same maximum readable width is retained.
Supported UI scales are 1.0, 1.25, 1.5, and 2.0.

The frame must not cover the portrait dock, combat status/deck, clock,
minimap controls, chat input, or native Flower Menu.

## Interaction

Primary action: click an herb icon/card to toggle PICK/SKIP, click the map to
append a route point, then press START. Shift-click or right-click the map
removes the last route point. Clear Path removes all points.

Hover is limited to readable labels and state text. Escape pauses an active
run before any window close; the global emergency-stop key remains effective
when the window is hidden. Manual map movement or a manual Gob interaction
still yields control to the player.

The plotted route is the default Route mode. The existing eight compass
directions remain available as a bounded fallback when no plotted route is
selected.

## Motion And Accessibility

Motion used: restrained active blossom pulse and a moving target marker.

Reduced-motion behavior: static blossom, route, and markers while live state
and text continue updating.

Non-color state cue: PICK/SKIP text, numbered points, phase labels, and exact
status reasons accompany opacity and accent changes.

## Code Boundaries

Presentation file: `client/src/haven/foraging/ForagingWindow.java`

Immutable state/snapshot files: `ForagingProfile`, `ForagingSnapshot`,
`ForagingTarget`

Service/adapters: `ForagingHerbIconCache`, `ForagingGobScanner`,
`ForagingRoutePlanner`, `ForagingMapSafety`, `ForagingInventoryService`, and
`ForagingRepository`

Controller: `ForagingController`

Shared theme primitive reused: `MoonFlowerHudTheme` panel, leaf, slot, line,
and blossom primitives; no new palette or artwork is introduced.

Persistence location: `ClientData.sqlite("foraging.db")`; route points and
selection are saved per world, while Gob IDs and active state are never saved.

## Acceptance Criteria

1. Every catalog item draws its own resolved icon or the explicit missing icon.
2. Clicking a herb icon/card changes both its selection text and icon opacity.
3. The embedded map accepts, numbers, draws, clears, and persists route points.
4. Route mode requires two points and collects only selected exact loaded Gobs
   within the configured corridor.
5. Movement remains bounded, path-safe, single-dispatch, and stoppable.
6. Classic UI behavior remains available and no external Checkpoint Manager
   state is mutated by the Wayfinder.
7. Deterministic checks pass; live server, resource timing, map appearance,
   movement, and Pick acknowledgement remain supervised checks.

## Verification

Deterministic checks: icon-path derivation, route persistence and geometry,
selection opacity contract, path planning, and existing safety checks.

Scale/layout checks: `ForagingChecks` at UI scales 1, 1.25, 1.5, and 2.

Classic-mode regression check: keep native MapView, Flower Menu, inventory,
checkpoint, and keyboard paths unchanged when the Wayfinder is hidden/idle.

Live checks still required: visible icon loading, map point placement,
route-following, herb visibility, exact Pick option, inventory acknowledgement,
manual takeover, and segment-transition pause.

Evidence or screenshots to capture: 1280x720 route-planning view, larger-scale
herbarium scroll, selected versus skipped icon opacity, and one supervised
single-herb acknowledgement.
