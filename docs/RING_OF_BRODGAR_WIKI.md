# Ring of Brodgar Wiki

## Implemented Slice

MoonFlower now has a native, read-only Ring of Brodgar search window. Open the
collapsible feature tray and select **Ring of Brodgar Wiki**, or use the
configurable `ring-of-brodgar-wiki` key binding (default `Ctrl+Alt/Meta+W`).

The reader provides:

- explicit Enter/Search submission rather than search-as-you-type traffic;
- MediaWiki main-namespace search results with exact-title matches promoted,
  cleaned excerpts, word counts, edit timestamps, page IDs, and safely encoded
  article links;
- an explicit **Read in game** action that converts the selected page's rendered
  HTML into native headings, paragraphs, lists, and compact infobox facts;
- one lead image per opened article, downloaded only from Ring of Brodgar's
  `/images/` path, decoded with byte and pixel limits, proportionally sized,
  and cached in memory;
- a separate **Open web** action for the canonical source page;
- safe `https://ringofbrodgar.com` link validation;
- bounded in-memory caches for repeated searches, opened pages, and images;
- a one-request-per-minute gate matching the site's published crawl delay;
- a descriptive client user agent, timeouts, and a 1 MiB response limit;
- visible community-source, GFDL, and freshness context;
- a clearer, higher-contrast two-pane layout with a resizable `1000 x 650`
  default window and `760 x 500` minimum; and
- saved window position and size.

No wiki login, cookies, editing, scripts, background crawling, linked-page
prefetching, or bulk download behavior is present. Images are displayed from
the source site at reading time; they are not bundled into MoonFlower.

## Current Boundary

Ring of Brodgar does not expose MediaWiki's plain-text `extracts` property, so
the native reader fetches the selected page through MediaWiki's `action=parse`
API. It uses a non-executing HTML parser and maps a deliberately small subset of
content into Haven's native widgets. Navigation tables, edit controls, scripts,
styles, arbitrary embedded media, and unrestricted HTML are discarded.

The visible source link, revision ID, GFDL notice, and **Open web** action retain
provenance. Community-maintained facts may still be incomplete or outdated.
Before increasing automated request frequency or adding bulk/offline mirroring,
obtain permission from Ring of Brodgar's administrator.

## Important Files

- `client/src/haven/wiki/RingOfBrodgarWikiService.java` owns requests, caching,
  parsing, URL construction, and rate limiting.
- `client/src/haven/wiki/WikiWindow.java` owns native layout and interaction.
- `client/src/haven/wiki/WikiImageView.java` owns bounded proportional image
  presentation and texture cleanup.
- `client/src/haven/wiki/WikiArticle.java` is the sanitized article model.
- `client/src/haven/wiki/WikiChecks.java` contains deterministic offline checks.
- `client/src/haven/GameUI.java` owns the feature-tray and key-binding entry.

## Verification

Close the visible client before building or packaging.

```powershell
Push-Location client
ant hafen-client
java '-Dhaven.uiscale=1' -cp "build/classes;lib/*;lib/ext/*/*" haven.wiki.WikiChecks
java -cp "build/classes;lib/*;lib/ext/*/*" haven.MoonFlowerChecks
Pop-Location
```

Live validation should confirm window resizing at supported UI scales, tray
pressed state, Enter and button submission, cached repeated searches, the
cooldown message for a different query, **Read in game** page loading, lead-image
display, scrolling, and external opening of one selected article. Compilation
and offline checks do not prove those visible behaviors.
