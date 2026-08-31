# MoonFlower Codex

## Player Experience

MoonFlower's former raw Ring of Brodgar reader is now a native field archive.
Open **MoonFlower Codex** from the feature vine or use the configurable
`ring-of-brodgar-wiki` key binding (default `Ctrl+Alt/Meta+W`).

The Codex provides:

- instant ranked search over records already known to the client;
- explicit community archive search for records not yet indexed locally;
- categories derived from current live action resources and retrieved records;
- stable record identities independent of display text;
- linked records parsed from main-namespace community article links;
- Back, Forward, Home, mouse navigation, and `Alt+Left`/`Alt+Right`;
- per-character recently viewed records, search history, and persistent saved
  records through existing preferences, without changing Haven saves;
- bounded community lead images plus current action icons;
- a responsive three-column field-journal layout with MoonFlower ink, teal,
  gold, ivory, panel, vine, and blossom vocabulary;
- a painted open-journal crest and botanical archive rail with transparent,
  project-local artwork;
- bounded center-reveal, page-bloom, and traveling-mote decoration with a
  dedicated reduced-motion setting;
- compact provenance labels and link tooltips; and
- `Ctrl+Alt/Meta+right-click` on an inventory item to open its record without
  replacing the native item flower menu.

## Live Actions And Crafting

`WikiGameDataAdapter` indexes the current character's server-provided
`MenuGrid.Pagina` resources. These records are labeled `LIVE`. Their title,
description, icon, parent/child relationships, resource identifier, and action
kind are read from the loaded resource rather than copied into wiki content.

For a leaf action, **Open Action** delegates to `MenuGrid.use`. A real craft
action is labeled **Open Crafting** and follows the same path used by the native
action grid, allowing Haven to create its normal `Makewindow`. Ingredients,
skills, tools, availability, and messages therefore remain server/native facts.
The Codex does not simulate crafting, count inventory against a duplicated
recipe, or send invented production jobs.

Haven does not ship a complete local authoritative catalog of every item,
creature, terrain, biome, recipe, technology, or world-generation rule. Those
systems cannot be truthfully generated as local definition pages in this
client. Current menu actions are the dynamically generated local records;
everything sourced from Ring of Brodgar is visibly labeled `GUIDE`.

## Community Archive Boundary

Community search uses MediaWiki's main-namespace search API. A selected record
uses `action=parse` and requests rendered text, revision, categories, and linked
main-namespace pages. The client maps a bounded non-executing subset into native
headings, paragraphs, lists, infobox facts, categories, a lead image, and stable
related-record references.

Network behavior remains deliberately respectful and fail-closed:

- explicit submit rather than network search-as-you-type;
- one uncached community query per minute;
- bounded search/article/image caches;
- descriptive user agent, timeouts, and response/image limits;
- `https://ringofbrodgar.com` source validation;
- `/images/`-only community image validation; and
- visible GFDL/community attribution and revision context.

There is no wiki login, editing, cookie use, scripting, background crawling,
linked-page prefetching, bulk mirroring, or unrestricted HTML. Community content
may be incomplete or outdated and never overrides live client/server state.

## Architecture

- `WikiReference` owns stable `guide:` and `action:` identities plus provenance.
- `WikiSearchIndex` owns incremental token indexing and deterministic ranking.
- `WikiNavigationState` owns current, Back, and Forward stacks.
- `WikiLibrary` owns bounded per-character recent/search/saved preferences.
- `WikiGameDataAdapter` converts the live action menu into records and delegates
  real actions back to `MenuGrid`.
- `RingOfBrodgarWikiService` owns safe requests, parsing, caching, and limits.
- `WikiWindow` owns thematic presentation and interaction.
- `WikiUiAssets` and `WikiOrnamentWidget` own optimized painted assets and
  reduced-motion-aware decorative animation.
- `WikiImageView` owns bounded remote-image and live-icon presentation.
- `WikiChecks` owns deterministic offline checks.
- `GameUI` owns lifecycle, window geometry, feature-vine, and keybinding entry.

The design and resolution decisions are recorded in
`docs/ui/MOONFLOWER_CODEX.md`.

## Failure Behavior

Unknown/deleted records, unloaded live resources, unsafe links, missing images,
network failures, and malformed optional resources leave the Codex open with a
recoverable message. One malformed resource cannot prevent other live actions
from being indexed. Development checks validate stable references and retrieved
article links; runtime logs and live observation remain the evidence for server
resource behavior.

## Verification

Close the visible client before normal compilation or packaging.

```powershell
Push-Location client
ant hafen-client
java '-Dhaven.uiscale=1' -cp "build/classes;lib/*;lib/ext/*/*" haven.wiki.WikiChecks
java -cp "build/classes;lib/*;lib/ext/*/*" haven.MoonFlowerChecks
Pop-Location
git diff --check
```

The focused suite covers query normalization, safe URLs, community parsing,
caches, exact/partial/alias/category/no-result search, Back/Forward state,
article categories and links, stable reference persistence shape, and live
action categorization.

Compilation and deterministic checks do not prove visible layout, live network
behavior, real item context interaction, image rendering, or server crafting.
A supervised client session should separately confirm:

1. 1280x720 minimum-size and 1080p/1440p preferred layouts at supported scales;
2. local type-ahead, one submitted community query, and cached repeat behavior;
3. several related-record hops followed by Back and Forward;
4. Recent and Saved persistence after closing/reopening the Codex;
5. one inventory item opened with the Codex modifier gesture; and
6. one `LIVE` craft record opening the normal server-provided crafting window.
