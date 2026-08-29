# MoonFlower Clock UI Brief

This completed component brief applies the reusable
`docs/templates/MOONFLOWER_UI_COMPONENT.md` template to the first clock slice.
Detailed mechanics research remains in `docs/CLOCK_CALENDAR_RESEARCH.md`.

## Component

Name: MoonFlower World Clock.

User goal: Replace the small top clock in MoonFlower mode with a unified,
animated clock/calendar and current-area display.

Smallest useful behavior: Animated sky, game time/date, moon phase, season
progress and game-time countdown, terrain, province, realm, and one sourced
context notice.

Classic behavior that must remain available: The original `Cal` widget,
tooltip, server-time text, status text, and hit shape when MoonFlower HUD is off.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Time/date/night/moon/season | `Glob.ast` and `Glob.globtime()` | `LIVE` | Await server astronomy |
| Season remainder | Live season progress and known season length | `CALC` | Omit |
| Terrain | Live player tile resource | `LIVE` | Omit |
| Province/realm | Server `ui/province` payload | `LIVE` | Omit |
| Dawn, Fish Moon, Moonmoth notices | Sourced research catalog | `GUIDE` | No notice claim |

No real-time season conversion is shown in this slice because accelerated and
new worlds make a fixed multiplier unreliable.

## Visual System

- Reuse the portrait HUD's circular wells, curved vines, blossoms, ink, teal,
  aged gold, ivory, and layered dark-metal framing.
- Use the approved inverted seasonal artwork with a flat top mounting rail and
  all botanical ornament hanging downward into the game view.
- The central circular sky aperture cross-fades midnight, dawn, day, sunset,
  and night.
- Accurate analog hands and a compact digital time share the central aperture;
  a gold arc identifies the active painted season quadrant.
- Native sun and moon resources preserve Haven identity inside the MoonFlower
  frame.
- Text and provenance labels supplement every color-coded state.

## Layout

- Anchor: existing top-center hide panel.
- MoonFlower size: 520x182 logical pixels before UI scaling.
- Classic size: original `Cal` size.
- Visual hierarchy: one dominant hanging clock and four-season wheel; compact
  left plaques for season/countdown and date; compact right plaques for
  moon/area and the current context notice.
- Silhouette: transparent around the ornament. Never enclose the component in a
  rectangular panel or arrange its content as a debug-style stack of rows.
- The static frame is project-local generated raster art derived from the
  approved Concept D mockup. Live sky, clock hands, text, and seasonal state are
  rendered separately so no current data is baked into the artwork.
- At 1280x720: one compact crest, clipped long text, no separate duplicate clock
  or status columns in MoonFlower mode.
- At larger resolutions: remain top-center; do not expand simply to fill space.
- Nearby UI: avoid upper-left buffs and upper-right HUD/status elements.
- Unknown and long values: omit unknown area parts; clip within dedicated rows;
  preserve full details in the tooltip.

## Interaction

- Hover: detailed live clock, date, season, moon, area, notice, and provenance.
- Classic mode: delegate hit testing and tooltip behavior to the original `Cal`.
- No server messages, movement, harvesting, fishing, or automated actions.
- Expanded almanac interaction is deferred.

## Motion And Accessibility

- Motion: server-time sun/moon travel, sky color transitions, native sun frames.
- Reduced motion: Options -> Advanced Settings -> MoonFlower HUD can freeze the
  decorative sun frames while live time and sky state continue updating.
- Non-color cues: explicit time, phase, season, `LIVE`/`GUIDE`, and notice text.

## Code Boundaries

- Presentation: `MoonFlowerClockWidget`.
- Immutable state: `WorldClockSnapshot`.
- Adapter: `WorldClockService`.
- Shared theme: existing `MoonFlowerHudTheme` primitives.
- Integration: one widget substitution plus suppression of duplicated legacy
  text only while MoonFlower mode is active.

## Acceptance Criteria

1. MoonFlower mode shows one unified themed clock at top center.
2. Classic mode retains the original clock and surrounding text.
3. Time/date/moon/season derive from live astronomy without external services.
4. Season countdown is explicitly game time and survives season rollover.
5. Terrain/province/realm update from live session context and never remain
   invented when unavailable.
6. GUIDE notices never appear as server-authoritative completion state.
7. Sunrise/day/sunset/night colors remain visually distinct and text-labeled.
8. Reduced decorative motion is user-selectable.
9. Deterministic checks pass; live top-HUD layout remains separately unverified.

## Verification

- Run `scripts/assert-client-stopped.ps1` before a clean/deployment build.
- Run `ant deftgt`.
- Run `java '-Dhaven.uiscale=1' -cp "bin/*" haven.WorldClockChecks`.
- Run existing MoonFlower HUD and branding checks.
- Run `git diff --check` on touched files.
- Live follow-up: inspect classic/MoonFlower switching and top-HUD fit at common
  resolutions and supported scales without treating compilation as visual proof.
