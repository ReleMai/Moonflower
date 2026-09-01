# MoonFlower Unified Inventory

## Component

Name: Unified Inventory Tool Bay

User goal: Keep inventory utilities visually and spatially part of the inventory instead of presenting them as a second attached window.

Smallest useful behavior: The title control expands one outer inventory frame and reveals a recessed tool bay containing the existing organization and bulk-processing actions.

Classic behavior that must remain available: Inventory contents, native window dragging/closing, title-button hit areas, sorting, stacking, slot locking, extended view, and stoppable bulk actions remain unchanged.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Bulk-action status | Local `InventoryBulkActionController` state | `LIVE` | Show `Idle` when no action is running |
| Slot-lock state | Current inventory window lock controller | `LIVE` | Show unlocked label if unavailable |

No server estimates, community guidance, or learned observations are displayed.

## Visual System

The component uses `MoonFlowerHudTheme` ink surfaces, teal recesses, gold dividers, ivory labels, leaf buttons, vines, blossoms, and the existing generated MoonFlower window frame. A new project-local transparent divider ornament joins the inventory grid and tool bay without creating another outer frame.

## Layout

Anchor or default position: Inside the inventory window content area, immediately after the inventory content on the right.

Minimum and preferred size: 196 logical pixels wide; height follows the larger of the inventory content and the compact action layout.

Behavior at 1280x720: The window grows right when space permits and shifts left only enough to keep the expanded outer frame on-screen. Collapsing restores the inventory grid's screen position.

Behavior at 1920x1080 and larger: Same compact dimensions; no extra whitespace or detached centering.

Supported UI scales: 1.0, 1.25, 1.5, and 2.0.

Nearby UI that must not be covered: The expanded window is clamped to its parent bounds without moving vertically.

Long-text and unknown-value behavior: Status text truncates with an ellipsis; fixed action labels remain centered.

Movable/resizable state and persistence key, if any: The existing inventory window position remains authoritative; the tool bay has no separate persisted position.

## Interaction

Primary action: Click the integrated title emblem to expand or collapse the tool bay.

Hover/tooltip behavior: The title emblem and every action preserve descriptive tooltips and hover/pressed cues.

Keyboard behavior: No new keybinds or focus capture.

Edit-mode behavior: Not applicable.

Classic-mode behavior: The same integrated geometry and actions remain usable; classic window chrome stays classic when MoonFlower mode is disabled.

Fail-closed conditions: If the inventory or host window is unavailable, the bay closes, stops its controller, and performs no action.

## Motion And Accessibility

Motion used: A 190 ms eased width reveal expands the existing outer window frame and uncovers the internal bay from the inventory seam.

Reduced-motion behavior: The bay and frame snap directly between collapsed and expanded sizes.

Non-color state cue: The toggle emblem changes engraved shape and the bay exposes explicit text labels; status is always written as text.

## Code Boundaries

Presentation file: `client/src/haven/inventoryqol/InventoryControlPanel.java`

Immutable state/snapshot file: Existing immutable `InventoryControlPanel.Layout` geometry.

Service or adapter file: Existing `InventoryBulkActionController`; no behavior rewrite.

Shared theme primitive reused or added: `MoonFlowerHudTheme.drawInventoryToolBay` and `drawInventoryToolToggle`.

Persistence location: Existing inventory window position preference only.

`Window` receives only the minimal child-layout hook needed to expand one frame; bulk-action behavior remains outside it.

## Acceptance Criteria

1. Inventory and tools share exactly one outer frame and one title bar.
2. No tool panel widget is added as a sibling of the inventory window.
3. Opening and closing preserve the inventory grid's position and exact base content size.
4. All existing action buttons remain clickable and stoppable.
5. The title control remains inside the title rail at supported scales.
6. Reduced motion snaps without an intermediate animation.
7. Focused deterministic checks pass without claiming live visual proof.

## Verification

Deterministic checks: `InventoryQolChecks` verifies child integration, bounds, scale-safe geometry, and existing action/tool-order behavior.

Scale/layout checks: Run focused checks at 1.0, 1.25, 1.5, and 2.0 UI scales.

Classic-mode regression check: Confirm the integrated bay does not force MoonFlower frame drawing when the theme is disabled.

Live checks still required: Open the inventory in-world, expand/collapse at screen edges, and exercise Butcher all and Crack all on applicable items.

Evidence or screenshots to capture: One collapsed and one expanded inventory at 1.0 scale, plus an expanded edge-of-screen case.
