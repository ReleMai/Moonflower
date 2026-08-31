# MoonFlower Portrait Combat Transform

## Component

Name: Portrait Combat Transform

User goal: Entering combat should transform the existing portrait HUD into a
compact combat HUD without hiding the portrait, adding a second panel, or
blocking more of the world view.

Smallest useful behavior: Crossfade the portrait ornament into a combat-state
ornament inside the same footprint, move the six existing utility controls to
mirrored side rails, and place live combat actions, openings, maneuvers,
initiative, last moves, cooldown, and opponent health into the transformed
ornament. Reverse the transition when combat ends.

Classic behavior that must remain available: With MoonFlower HUD styling
disabled, the native combat UI, hit areas, keybinds, tooltips, focus, Escape
behavior, and server messages remain unchanged.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Player portrait and vitals | live session meters and avatar | `LIVE` | Preserve existing unavailable behavior |
| Combat actions and cooldowns | `Fightsess.actions` and action timers | `LIVE` | Empty wells remain visibly empty |
| Player/opponent openings | live combat buffs | `LIVE` | Omit absent openings |
| Player/opponent maneuver | live combat relation/session | `LIVE` | Show an empty shield well |
| Initiative points | live `Fightview.Relation` | `LIVE` | Show unavailable, never infer |
| Last player/opponent move | live fight session resources | `LIVE` | Show an empty move well |
| Opponent health | exact live value when exposed; existing observed estimate otherwise | `LIVE` or `LEARNED` | Label health not exposed; never invent a percentage |
| Utility-button pressed state | current client window state | `LIVE` | Default to unpressed |

## Visual System

Reuse the portrait HUD's deep ink, teal, gold, ivory, ruby, vine, blossom,
slot, circular-control, stitched-leather, and engraved-metal vocabulary. The
combat-state artwork is a project-local transparent raster sibling of the
normal portrait ornament. Ruby remains limited to danger and the red opening;
combat state is also identified by shield/weapon shapes and labels.
Player openings use a teal identity rim and a `PLAYER` badge; opponent openings
use a ruby identity rim and an `ENEMY` badge, so ownership is not communicated
by position or color alone.

## Layout

Anchor or default position: Existing saved portrait-hub position, centered at
the bottom by default.

Minimum and preferred size: Same logical `520 x 288` ornament footprint as the
normal portrait HUD; no combat-only height is added. Combat wells use the
available painted sockets more fully so action icons, opening values, and
cooldown timers remain readable without enlarging the footprint.

Behavior at 1280x720: Keep every painted and interactive element inside the
portrait widget bounds. The side rails fold inward rather than growing upward.

Behavior at 1920x1080 and larger: Preserve the same proportional footprint and
existing portrait-scale setting.

Supported UI scales: Existing Haven UI scale plus all portrait scale settings.

Nearby UI that must not be covered: Chat, belt, quick equipment, minimap,
inventory windows, world center above the portrait, and the portrait itself.

Long-text and unknown-value behavior: Use short `YOU`, `FOE`, `IP`, and health
labels; clip or omit unavailable values rather than widening the HUD.

Movable/resizable state and persistence key, if any: Reuse the existing
character-specific portrait position and portrait scale preferences.

## Interaction

Primary action: Existing combat action clicks/keys and existing portrait
utility-button clicks.

Hover/tooltip behavior: Preserve native action tooltips and current utility
button tooltips.

Keyboard behavior: Preserve native combat keybinds and window keybinds.

Edit-mode behavior: Show the final transformed combat state statically without
starting a fight or sending server messages.

Classic-mode behavior: Use the existing native combat presentation.

Fail-closed conditions: If the transformed hub or its geometry is unavailable,
fall back to the existing native combat layout; never hide combat actions.

## Motion And Accessibility

Motion used: A 420 ms reversible crossfade with a short inward side-rail glide
and delayed combat-content reveal. The portrait, vitals, quick equipment, and
hub anchor do not move.

Reduced-motion behavior: When reduced motion is enabled, switch states in one
frame while retaining all geometry and non-color cues.

Non-color state cue: Combat-state shield finial, paired weapon flourishes,
`YOU`/`FOE` labels, and changed circular-well layout.

## Code Boundaries

Presentation file: `MoonFlowerPortraitHub.java` plus `Fightsess.java` only for
rendering live combat values into hub-owned coordinates.

Immutable state/snapshot file: Existing combat state remains owned by
`Fightsess`/`Fightview`; this slice adds no duplicate state store.

Service or adapter file: `MoonFlowerCombatLayout.java` and
`MoonFlowerHudAssets.java` provide geometry only.

Shared theme primitive reused or added: Existing `MoonFlowerHudTheme` colors,
round icon drawing, opponent-health plate, and cooldown rendering.

Persistence location: Existing MoonFlower HUD preferences; no new persisted
combat state.

## Acceptance Criteria

1. Combat uses one transformed portrait HUD, not a separately painted crown.
2. The portrait, vital rings, buffs, movement controls, and quick equipment are
   not covered or displaced by combat presentation.
3. Inventory, equipment, character sheet, kin, options, and feature controls
   remain available on mirrored side rails during combat.
4. Combat state does not extend above the normal portrait ornament footprint.
5. Entry and exit animations are reversible and have a static equivalent.
6. Classic combat rendering remains unchanged when MoonFlower mode is off.
7. Focused HUD and combat checks pass at 1280x720 and larger reference sizes.
8. Live appearance and server behavior remain explicitly unverified until a
   supervised client combat check.
9. Action labels stay inside their painted wells, while buffs and movement
   controls interpolate to the combat artwork's measured socket centers.

## Verification

Deterministic checks: `haven.MoonFlowerHudChecks` and
`haven.combat.CombatAssistChecks`.

Scale/layout checks: Assert transformed ornament bounds, protected portrait
circle, action wells, side rails, and quick-equipment area at 1280x720,
1920x1080, and supported portrait scales.

Classic-mode regression check: Existing classic branch remains selected when
`MoonFlowerHudTheme.active()` is false.

Live checks still required: Enter and leave combat, click every utility control,
exercise action tooltips/keybinds, inspect opponent-health unknown state, and
confirm no world-view obstruction at the user's resolution.

Evidence or screenshots to capture: Normal state, mid-transition, combat state,
equipment window during combat, and post-combat restored state.
