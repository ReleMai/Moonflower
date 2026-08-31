# MoonFlower Codex UI Polish Checklist

## Component

Name: Codex gallery and responsive archive shelf

User goal: Keep article content on-screen, show the images promised by wiki gallery sections, and make local records easier to filter by item type.

Smallest useful behavior: Resize the three-column layout safely, deduplicate records by canonical guide page, collect safe gallery images, and render a bounded non-blocking thumbnail strip.

Classic behavior that must remain available: The existing classic wiki/search path, native action invocation, navigation keys, and external-source safety checks remain unchanged.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Live action type | current `MenuGrid` resource/action path | LIVE | Fall back to Actions |
| Guide category | Ring of Brodgar page metadata | GUIDE | Use Community Archive |
| Article image URI | sanitized MediaWiki HTML | GUIDE | Omit unsafe or unavailable images |
| Thumbnail image | bounded asynchronous image request | GUIDE | Show loading/error text |

## Visual System

The existing Codex crest, archive rail, ink/teal/gold/ivory palette, shared slot frames, and botanical ornament remain the visual language. The gallery uses the shared slot primitive rather than a new panel style.

## Layout

Anchor or default position: Existing Codex window position and saved size.

Minimum and preferred size: Existing minimum 820x540 logical pixels; preferred 1080x700.

Behavior at 1280x720: Article and gallery shrink before the related shelf; footer controls remain inside the article column.

Behavior at 1920x1080 and larger: Three columns retain readable gutters and the gallery can show up to six thumbnails.

Supported UI scales: 1.0, 1.25, 1.5, and 2.0 deterministic checks.

Nearby UI that must not be covered: Existing HUD, chat, minimap, inventory, and combat portrait remain outside the movable window.

Long-text and unknown-value behavior: Rich text remains scrollable; missing images are omitted or reported as unavailable.

Movable/resizable state and persistence key: Existing `wndsz-wiki` size preference and window dragging.

## Interaction

Primary action: Select a shelf/related entry or search for a record.

Hover/tooltip behavior: Existing row metadata tooltips remain available.

Keyboard behavior: Existing Enter search, Alt+Left/Right navigation, and mouse Back/Forward remain available.

Edit-mode behavior: Not applicable.

Classic-mode behavior: No native action or server message semantics change.

Fail-closed conditions: Unsafe image hosts, oversized images, unavailable live actions, and malformed records are omitted or reported.

## Motion And Accessibility

Motion used: Existing crest/rail reveal and page-turn bloom.

Reduced-motion behavior: Existing Codex reduced-motion option fully reveals static ornaments and disables continuous motes.

Non-color state cue: Labels, selected row frames, provenance text, and loading/error messages accompany color.

## Code Boundaries

Presentation files: `WikiWindow`, `WikiGalleryView`, `WikiOrnamentWidget`.

Immutable state/snapshot files: `WikiArticle`, `WikiReference`, `WikiSearchIndex`.

Service or adapter files: `RingOfBrodgarWikiService`, `WikiGameDataAdapter`.

Shared theme primitive reused or added: Existing `MoonFlowerHudTheme.drawPanel`, `drawSlot`, and generated Codex ornaments.

Persistence location: Existing wiki window/library preferences.

## Acceptance Criteria

1. Duplicate guide aliases collapse to one canonical page entry.
2. Safe article/gallery images render as bounded thumbnails without blocking the UI thread.
3. Type categories expose useful filters for live records.
4. The window remains readable at the supported sizes/scales.
5. Focused WikiChecks pass without claiming live server or visual proof.

## Verification

Deterministic checks: `WikiChecks` including gallery extraction, canonical dedupe, type classification, and reduced-motion behavior.

Scale/layout checks: WikiChecks at 1.0, 1.25, and 1.5; 2.0 remains part of the release matrix.

Classic-mode regression check: Existing action invocation path is unchanged.

Live checks still required: Supervised in-game review of long articles, image loading, gallery thumbnails, and footer clipping.

Evidence or screenshots to capture: A 1280x720 and a 1920x1080 Codex view with a page containing a MediaWiki gallery.
