# MoonFlower Codex Search And UI Cleanup

## Component

Name: Codex field search and reading layout

User goal: Find a useful item, creature, recipe, or action quickly without
having to understand which archive panel or search mode is active.

Smallest useful behavior: Make the current-session search forgiving and
ranked, make community search an explicit and clearly named action, and keep
the article readable when the window is narrow.

Classic behavior that must remain available: Existing native action invocation,
community article loading, bookmarks, recent records, search history, browser
navigation, external-source safety checks, and classic Haven mode remain
unchanged.

## Research decisions

- MediaWiki's `prefixsearch` and `opensearch` APIs are appropriate models for
  title suggestions, but the client does not use them while typing because
  Ring of Brodgar requests are deliberately rate-limited. The in-memory live
  action index provides the same low-latency affordance without network noise.
- MediaWiki full-text search supports internal query rewriting for spelling
  correction. The community query enables that server-side option while still
  using an explicit Submit/Enter action.
- The WAI-ARIA combobox pattern uses a collapsed suggestion list, Down/Up to
  move through suggestions, Enter to accept, and Escape to dismiss. The native
  Haven widgets do not expose ARIA, so the equivalent visible contract here is
  a single search field, local results updated as typed, Enter for community
  search, and result rows that retain selection and tooltip feedback.
- Progressive disclosure research favors a focused primary surface and clear
  labels for secondary actions. The Codex therefore keeps reading content
  primary, makes the community boundary explicit, and suppresses the related
  shelf when the window cannot give it a readable width.

## Information model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Current-session result | loaded `MenuGrid` action | LIVE | Omit until resource/action is ready |
| Local result ranking | deterministic title/category/summary match | CALC | Do not invent a result |
| Community result | Ring of Brodgar MediaWiki search | GUIDE | Show an actionable no-result message |
| Saved/recent query | local player preference | LEARNED | Show an empty-state explanation |

## Visual system

The existing Codex crest, archive rail, ink/teal/gold/ivory palette, selected
rows, provenance labels, and shared panel treatment remain the visual language.
No new icon or palette system is introduced.

## Layout

Anchor or default size: Existing saved Codex position and preferred 1080x700
size.

Minimum size: Existing 820x540 logical pixels.

Behavior at 1280x720: Search scope is visible in the button/status copy; the
sidebar and article remain readable; the related-record shelf is hidden if it
would reduce the article below a useful width.

Behavior at 1920x1080 and larger: The related-record shelf may appear beside
the article when records are available.

Supported UI scales: Existing 1.0, 1.25, 1.5, and 2.0 checks.

Long text: Rows truncate visually but retain the full record description in a
tooltip; article text remains scrollable.

Saved positions and size: Existing `wndc-wiki` and `wndsz-wiki` preferences.

## Interaction

Primary action: Type to search session-known records, select a result, or press
Enter / choose `Community Search` to search Ring of Brodgar.

Keyboard behavior: Enter submits the community search; Alt+Left/Right and
mouse Back/Forward preserve browser navigation; the existing Wiki shortcut
continues to focus the field.

Fail-closed conditions: Short queries, unsafe links, unavailable live actions,
rate limits, malformed records, and unavailable images produce a clear status
or empty state instead of a guessed result.

## Motion and accessibility

Motion used: Existing crest and archive-rail reveal/page-turn animation only.

Reduced-motion behavior: Existing static fully-revealed ornament state.

Non-color state cue: Result headings, provenance text, selected-row frames,
tooltips, and explicit loading/error copy accompany color.

## Code boundaries

Presentation: `client/src/haven/wiki/WikiWindow.java`.

Search state and scoring: `client/src/haven/wiki/WikiSearchIndex.java`.

Community adapter: `client/src/haven/wiki/RingOfBrodgarWikiService.java`.

Focused checks: `client/src/haven/wiki/WikiChecks.java`.

## Acceptance criteria

1. A typo or punctuation variation in a known local record still produces the
   intended result when the term is long enough to support safe fuzzy matching.
2. Exact title, title prefix, category, summary, and alias matches remain
   sensibly ordered.
3. The UI names local/session search and community search distinctly.
4. Narrow windows do not force a third column that makes the article unreadable.
5. Community search retains explicit Enter/button submission and rate-limit
   messaging.
6. Focused checks pass without claiming live visual or server proof.

## Verification

Deterministic checks: `WikiChecks` for normalization, exact/prefix/alias/fuzzy
ranking, community query parameters, cache/rate-limit behavior, and scale-safe
layout constants.

Live checks still required: Visible review at 1280x720 and 1920x1080, supported
UI scales, long result lists, community search, and article readability.
