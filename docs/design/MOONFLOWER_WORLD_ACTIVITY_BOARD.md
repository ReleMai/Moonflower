# MoonFlower World Activity Board

## Component

Name: World Activity Board

User goal: Keep one quiet, session-local place to see pyres and localized
resources that have been observed, with quality and a server-derived countdown.

Smallest useful behavior: Detect loaded pyres and localized-resource Gobs,
associate an interaction or Inspect response with the Gob, retain quality and
duration observations, and show a due state without sounds or automatic actions.

Classic behavior that must remain available: The board is an optional window;
normal object interaction, Inspect behavior, existing Gob labels, and the
classic HUD remain unchanged when MoonFlower mode is disabled.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Resource/activity label | Loaded current-session Gob resource | `LIVE` | Use a safe resource-name fallback |
| Pyre/localized-resource presence | Current object cache | `LIVE` | Do not list an unrecognized resource |
| Quality | Current-session Inspect/gather notice | `LIVE` | Show `QL unavailable` |
| Duration | Current-session server notice containing activity context | `LIVE` | Show `Awaiting Inspect timer` |
| Remaining time | Observed due time minus local clock | `CALC` | Show `DUE NOW` after expiry |
| Fuel/lit state | Explicit recognized server wording | `LIVE` | Keep `Fuel unknown`; never infer it from art |
| Out-of-view status | Local object-cache presence | `LEARNED` | Keep timed rows as `last seen` until session end |

`DRYING_RACK`, `HERBALIST_TABLE`, `KILN`, `OVEN`, `SMELTER`, `GARDEN_POT`,
`FIELD`, and `CURIOSITY` are represented as future activity types. Their
workstation-specific item, fuel, and lit-state adapters are not enabled in this
first slice, so no timer is fabricated for them.

## Visual System

The first window reuses `MoonFlowerHudTheme` panels, slots, vines, blossoms, and
the existing window decoration. It intentionally has no new art assets. Due
rows have both a `DUE NOW` label and a blossom marker; state is not conveyed by
color alone.

## Layout

Anchor/default position: the native Window preference `wndc-World Activity
Board`, default `(220, 140)`.

Minimum/preferred size: 520x360 logical pixels before UI scaling. Rows are
bounded to the available content area and overflow is summarized.

At 1280x720 the board fits beside the default lower-right menu and remains
movable. At 1920x1080 and larger it keeps its preferred size rather than
covering the whole map. `UI.scale` is used for all dimensions.

The board does not cover the portrait HUD by default, and its saved location is
independent from the existing cookbook, fishing, wiki, and conservatory windows.
Long labels are clipped with an ellipsis. Unknown quality, timer, fuel, and
visibility states are written explicitly.

## Interaction

Primary action: Open/close from the MoonFlower feature tray or `Ctrl+M+T`.

The board is observational only. It does not open a flower menu, send an
Inspect/gather request, light a fire, add fuel, or move items. Existing native
object clicks and Inspect cursor behavior continue through the same `GameUI`
and `MapView` paths.

## Motion And Accessibility

Motion used: None in this first slice; countdown text updates once per second.

Reduced-motion behavior: The static board is already the reduced-motion form.

Non-color state cue: Every row includes `DUE NOW`, `CALC`, or `awaiting Inspect`,
and due rows include a blossom marker.

## Code Boundaries

Presentation file: `client/src/haven/worldactivity/WorldActivityBoardWindow.java`

Immutable state/snapshot file: `client/src/haven/worldactivity/WorldActivityEntry.java`

Service/adapter files: `WorldActivityBoardService.java`,
`WorldActivityDetector.java`, and `WorldActivityTimingParser.java`

Shared theme primitive reused: `MoonFlowerHudTheme`.

Persistence location: None. Observations intentionally last for the current
session only; persistent world timers need a separate identity and staleness
policy before being added.

## Acceptance Criteria

1. Loaded pyres appear as awaiting inspection and retain timed observations.
2. Loaded localized resources appear and retain quality/timer observations.
3. A server duration becomes a ticking `CALC` value and then `DUE NOW`.
4. Quality-only and duration-only notices can be associated with one Gob.
5. Explicit fuel wording is retained; unknown fuel remains unknown.
6. No timer is created from an unrelated number or resource name alone.
7. Classic interaction paths remain unchanged.

## Verification

Deterministic checks: `haven.worldactivity.WorldActivityChecks` will cover
duration context, quality parsing, fuel fail-closed behavior, classification,
formatting, and state transitions.

Scale/layout checks: Verify the bounded row layout at 1280x720, 1920x1080, and
the supported UI scales after compilation.

Classic-mode regression check: Open and close the optional board with
MoonFlower disabled and confirm native object/Inspect paths remain unchanged.

Live checks still required: Visible supervised client verification of pyre
Inspect wording, localized-resource quality/refill wording, object association,
out-of-view retention, and actual due timing. Compilation cannot prove these.

Evidence or screenshots to capture: Board with an awaiting pyre, a localized
resource with `LIVE QL` plus `CALC`, and a due row; record server wording
separately from deterministic checks.
