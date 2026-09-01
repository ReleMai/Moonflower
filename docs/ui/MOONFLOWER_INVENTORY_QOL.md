# MoonFlower Inventory QoL Panel

## Component

Name: Inventory action vine

User goal: Open bulk inventory actions from the Inventory window, use the best
reachable sharp tool for animal processing, and retain inspected localized
resource refill countdowns in the world.

Smallest useful behavior: One title-bar blossom expands the Inventory window's
single outer frame to reveal an internal tool bay containing Sort, Stack,
Unstack, slot locking, Extended View when available, Butcher all, Crack all,
and Stop. No secondary window or outer frame is created. Bulk actions probe the selected inventory's native
flower menus one item at a time. Animal processing temporarily equips
the highest-quality supported one-handed sharp tool in the main inventory or
equipped Belt and restores the displaced hand item afterward. Inspect refill
messages become a ticking world label and hover detail for that Gob.

Classic behavior that must remain available: Existing item right-click menus,
all prior Inventory utility actions, equipment messages, Inspect messages, and
classic window behavior remain available. Prior utility actions move into the
panel instead of disappearing or changing their server messages.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| bulk action progress | current Inventory widgets and native flower-menu acknowledgement | `LIVE` | Stop and report unavailable/loading item |
| selected sharp tool quality | live `GItem` quality buff | `LIVE` | Skip tools whose identity or quality is loading |
| localized-resource refill snapshot | server Inspect system message for the clicked Gob | `LIVE` | Do not create a timer |
| ticking refill remainder | elapsed real time subtracted from the Inspect snapshot | `CALC` | Show unavailable after invalid/unparsed text |

## Visual System

The tool bay lives inside the same scalable generated MoonFlower window frame
as the Inventory itself: ink, navy, antique gold, teal vines, and ivory
blossoms. A compact drawn blossom control remains integrated into the title
rail beside Close. A generated vertical moonflower divider separates the grid
and recessed controls without drawing another window border.

## Layout

Anchor or default position: Inside the Inventory window content area,
immediately to the right of the inventory content.

Minimum and preferred size: Fixed 204 by 180 scaled pixels, using two equal
columns for paired actions and full-width rows for Extended View and Stop.

Behavior at 1280x720: Use the native inventory window boundary behavior; there
is no independently positioned panel to drift or clamp.

Behavior at 1920x1080 and larger: Remain attached to the Inventory window.

Supported UI scales: 1.0, 1.25, 1.5, and 2.0.

Nearby UI that must not be covered: Inventory grid, title text, Close control,
adjacent study/container windows, and screen edges.

Long-text and unknown-value behavior: Status wraps to the fixed panel width;
unknown counts use a short fail-closed message.

Movable/resizable state and persistence key, if any: The tool bay is part of the
Inventory and has no independent position or persistence key.

## Interaction

Primary action: Click the single title leaf, then choose an explicitly labeled
utility or bulk-processing action. Click Stop, close the vine, close the
Inventory, occupy the cursor, or start another action to stop safely.

Hover/tooltip behavior: Every title and action control names its effect.
Inspected localized resources expose the ticking `CALC` remainder.

Keyboard behavior: Existing Inventory and Escape behavior is preserved.

Edit-mode behavior: None.

Classic-mode behavior: The feature remains usable without changing classic
window drawing or native item interactions.

Fail-closed conditions: Occupied cursor, missing equipment widget, loading
identity/quality, no reachable sharp tool, lost cursor item, failed equipment
acknowledgement, action timeout, or detached inventory.

## Motion And Accessibility

Motion used: A 190 ms smoothstep reveal expands the existing outer frame and
uncovers the recessed controls from the generated divider.

Reduced-motion behavior: With reduced HUD motion enabled, the panel and hinge
snap directly to their final position.

Non-color state cue: Controls and status text name Idle, Running, Stopping, and
completion states. Slot locking changes its label between `Lock slots` and
`Locking`; color is supplementary.

## Code Boundaries

Presentation file: `client/src/haven/inventoryqol/InventoryControlPanel.java`

Generated divider artwork:
`client/src/haven/hud/moonflower-inventory-divider-v1-alpha.png`. The bay is
drawn within the shared generated MoonFlower window frame rather than carrying
a second frame.

Immutable state/snapshot file: Bulk action definitions in
`InventoryBulkActionController`.

Service or adapter file: `SharpToolAutoManager`, `SharpToolSwapper`, and
`LocalizedResourceTimerInfo`.

Shared theme primitive reused or added: Existing panel, leaf-button, blossom,
and inventory title-button vocabulary.

Persistence location: Localized countdowns are session-only because their
authoritative source is the current server Inspect response.

## Acceptance Criteria

1. Inventory shows only Close and one non-overlapping Inventory Tools control;
   every former title action remains available inside one expanded frame.
2. Butcher all uses native flower-menu options in strict `Skin`, `Clean`,
   `Butcher` priority (then optional bone collection), processing one
   acknowledged item at a time; Stop clears pending selection.
3. Butchering uses the highest-quality supported one-handed sharp tool reachable
   in main Inventory or an equipped Belt and restores the exact displaced item.
4. Manual Skin, Flay, Clean, Butcher, and Collect Bones choices use the same
   guarded swap/restore transaction.
5. An Inspect message containing a refill duration creates a `CALC` countdown
   on that exact Gob; unparsed or unrelated messages create no timer.
6. Classic item menus, Inspect quality capture, title controls, cursor safety,
   and Escape behavior remain intact.
7. Focused deterministic checks and a guarded build pass without claiming live
   server or visual proof.

## Verification

Deterministic checks: Action option matching, sharp-tool classification and
quality ordering, duration parsing/formatting, and title-button layout.

Scale/layout checks: 1.0, 1.25, 1.5, and 2.0 panel bounds, child containment,
two-button title layout, and narrow-inventory regression.

Classic-mode regression check: Existing utility behavior and native flower-menu
selection remain available through the consolidated panel.

Live checks still required: Exact current server flower labels, equipment
acknowledgements for every supported tool, multi-stage carcass processing,
localized-resource Inspect wording, world-label position, and item restoration.

Evidence or screenshots to capture: Inventory vine open/closed, one successful
multi-stage small-animal run, restored hand item, and one inspected depleted
localized resource counting down.
