# MoonFlower Codex Component Brief

## Component

Name: MoonFlower Codex

User goal: Research encountered Haven concepts without leaving the client, retain
reading context, and open a known native crafting/action page from its record.

Smallest useful behavior: A searchable, interconnected field archive with local
history/bookmarks, category browsing, safe community records, and live action
records generated from the current character's server-provided action menu.

Classic behavior that must remain available: The classic action menu, crafting
window, item interactions, shortcuts, and server messages are not replaced.

## Information Model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Article text, categories, links, revision | Ring of Brodgar page | `GUIDE` | Show unavailable or omit |
| Article image | Ring of Brodgar page asset | `GUIDE` | Reflow without image |
| Known actions and their descriptions/icons | Live `MenuGrid.Pagina` resources | `LIVE` | Omit unloaded actions |
| Craft availability and requirements | Native server-created crafting window | `LIVE` | Open native action; never guess |
| Bookmarks, recent records, search history | MoonFlower preferences | `LEARNED` | Start empty |
| Search ranking | Deterministic local index | `CALC` | Show no matches |

The client does not contain authoritative catalogs for every item, creature,
terrain type, recipe, skill, or technology. Community records are therefore
explicitly guidance, while only current server-provided menu actions are labeled
live. No gameplay value is copied into a second definition.

## Visual System

The window reuses `MoonFlowerHudTheme` ink and deep-ink surfaces, teal selection,
gold dividers and navigation, ivory copy, and restrained vine/blossom ornament.
Two project-local transparent paintings add a field-journal crest and botanical
archive rail without replacing the scalable shared frame. Ruby is reserved for
failures and flower centers. Lists and reading surfaces use one frame each and
keep controls compact.

## Layout

Anchor or default position: saved `wndc-wiki`, otherwise near upper-left center.

Minimum and preferred size: 820x540 and 1080x700 logical pixels.

Behavior at 1280x720: Sidebar narrows, article image height is bounded, and
related records retain a scrollable compact list.

Behavior at 1920x1080 and larger: The reading column expands; sidebar and image
remain bounded so text does not become excessively wide.

Supported UI scales: Existing client-supported scales through `UI.scale`.

Nearby UI that must not be covered: The resizable window remains movable and is
clamped by the existing `GameUI.fitwdg` opening path.

Long-text and unknown-value behavior: Native scrolling, bounded labels, omitted
missing fields, and a no-image article layout.

Movable/resizable state and persistence key: `wndc-wiki`, `wndsz-wiki`.

## Interaction

Primary action: Search locally while typing; submit to search the community
archive when local knowledge does not contain the needed record.

Hover/tooltip behavior: Linked records and controls provide compact provenance or
action tooltips.

Keyboard behavior: Configurable Codex key binding, Enter to submit a community
search, Escape/window close behavior inherited from native windows, and mouse
Back/Forward buttons for navigation.

Edit-mode behavior: No special edit mode; the normal window remains draggable.

Classic-mode behavior: The Codex remains functional with classic HUD chrome and
does not alter classic crafting or item interactions.

Fail-closed conditions: Unknown links show a recoverable message; unsafe URLs are
refused; missing/unloaded actions cannot be invoked; failed article/image loads do
not close the window.

## Motion And Accessibility

Motion used: The crest reveals from its center when opened, record changes emit
one bounded gold bloom, and a small blossom mote travels down the archive rail.

Reduced-motion behavior: `Reduce Codex ornament motion` keeps both paintings
fully revealed and freezes page blooms and the traveling mote.

Non-color state cue: Selected rows, `GUIDE`/`LIVE` labels, bookmark star text,
and explicit error/status text accompany color.

## Code Boundaries

Presentation file: `client/src/haven/wiki/WikiWindow.java`

Immutable state/snapshot files: `WikiReference`, `WikiArticle`, search result
models.

Service or adapter files: `RingOfBrodgarWikiService`, `WikiLibrary`,
`WikiSearchIndex`, `WikiGameDataAdapter`.

Shared theme primitive reused or added: Existing `MoonFlowerHudTheme` panel,
slot, vine, blossom, ink, teal, gold, and ivory primitives plus the versioned
`moonflower-codex-crest-v1-alpha.png` and
`moonflower-codex-archive-rail-v1-alpha.png` paintings.

Persistence location: Existing Java preferences through `Utils`; no save-format
change.

## Acceptance Criteria

1. Search, category browsing, record loading, related links, Back, Forward, Home,
   recent records, and persistent bookmarks operate through stable references.
2. Retrieved community data is labeled `GUIDE`; current menu actions are `LIVE`.
3. Known menu actions are indexed dynamically and open the existing native action
   or crafting flow rather than simulating it.
4. The interface uses MoonFlower theme primitives and remains usable at the
   minimum size and supported scales.
5. Unknown records, missing images, broken links, and unloaded resources fail
   without crashing.
6. Focused deterministic search, navigation, persistence-shape, link, and action
   validation checks pass.
7. Live layout, network, image, item-context, and server crafting behavior remain
   separately identified until supervised in the visible client.

## Verification

Deterministic checks: `haven.wiki.WikiChecks`, `haven.MoonFlowerChecks`, compile,
and whitespace checks.

Scale/layout checks: Construct/layout checks at 1.0, 1.25, 1.5, and 2.0 where
supported by the focused harness; visible confirmation remains required.

Classic-mode regression check: Existing action menu and `Makewindow` are not
modified; any context shortcut must consume only its unique modifier gesture.

Live checks still required: Visible resize/scroll/tooltip review, real community
requests, image loading, navigating several linked records, and opening one known
craft action through the server-provided menu.

Evidence or screenshots to capture: 1280x720 minimum-size Codex, 1080p preferred
layout, a linked article chain with Back/Forward, bookmark persistence after
reopen, and a native crafting window opened from a `LIVE` record.
