# MoonFlower UI Component Template

Use this brief before creating or substantially redesigning any MoonFlower
window, HUD element, panel, toolbar, menu, or overlay.

## Component

Name:

User goal:

Smallest useful behavior:

Classic behavior that must remain available:

## Information Model

List every displayed value and its provenance:

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Example: season | live `Glob.ast` | `LIVE` | Show unavailable |

Allowed provenance labels:

- `LIVE`: directly received from the current server or session.
- `CALC`: deterministically derived from live values.
- `GUIDE`: community-maintained or otherwise non-authoritative guidance.
- `LEARNED`: local observations collected by MoonFlower.

Never present estimates, wiki guidance, or learned correlations as server fact.

## Visual System

Use the shared vocabulary in `MoonFlowerHudTheme` and the portrait HUD:

- ink/deep-ink surfaces for the background;
- teal and bright teal for active state, vitality, and living vines;
- gold and soft gold for frames, ornaments, selected guidance, and connectors;
- ivory for primary readable text and petals;
- ruby only for danger, destructive action, severe state, or flower centers;
- the shared panel/frame, vine, blossom, slot, and circular-control primitives;
- generated project-local artwork only when drawing primitives cannot express
  the component cleanly.

Do not introduce a second palette, unrelated frame language, default gray
desktop controls, or a new one-off texture style.

## Layout

Anchor or default position:

Minimum and preferred size:

Behavior at 1280x720:

Behavior at 1920x1080 and larger:

Supported UI scales:

Nearby UI that must not be covered:

Long-text and unknown-value behavior:

Movable/resizable state and persistence key, if any:

## Interaction

Primary action:

Hover/tooltip behavior:

Keyboard behavior:

Edit-mode behavior:

Classic-mode behavior:

Fail-closed conditions:

## Motion And Accessibility

- Use motion to explain state or spatial relationships, not as constant noise.
- Provide a reduced-motion or static equivalent for continuous decoration.
- Never communicate state with color alone; add text, shape, icon, or pattern.
- Keep text readable over world imagery and at every supported UI scale.
- Preserve native hit areas, tooltips, keybinds, focus, and Escape behavior.

Motion used:

Reduced-motion behavior:

Non-color state cue:

## Code Boundaries

Presentation file:

Immutable state/snapshot file:

Service or adapter file:

Shared theme primitive reused or added:

Persistence location:

Avoid adding unrelated responsibilities to `GameUI`, `Glob`, `Window`, or a
feature's main widget. Prefer a small snapshot/service/presentation boundary.

## Acceptance Criteria

1. The smallest useful behavior works without expanding into adjacent features.
2. Classic UI behavior remains available when MoonFlower mode is disabled.
3. The component visibly belongs to the portrait HUD's visual system.
4. Unknown and non-authoritative values are omitted or labeled accurately.
5. Native interaction and accessibility behavior is preserved.
6. The component remains on-screen and readable at supported sizes and scales.
7. Reduced-motion behavior is available when continuous animation is used.
8. Focused checks pass without claiming live server or visual proof.
9. A visible, supervised client check is recorded separately when required.

## Verification

Deterministic checks:

Scale/layout checks:

Classic-mode regression check:

Live checks still required:

Evidence or screenshots to capture:
