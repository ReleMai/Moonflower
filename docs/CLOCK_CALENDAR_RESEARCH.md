# MoonFlower Clock And Calendar Research

## Purpose

This brief defines a source-grounded direction for redesigning the small clock at
the top of the client. It is research and planning only. It does not promote the
feature into `CURRENT_TASKS.md` or authorize implementation.

The desired result is a compact MoonFlower world-information hub that:

- animates sunrise, daylight, sunset, night, and the visible moon phase;
- tells the Haven date and time;
- shows the current season and time until the next season;
- identifies the current terrain, province, and realm when known;
- surfaces useful time-, moon-, season-, weather-, and area-dependent activity;
- distinguishes live server data, client calculations, community references,
  and the player's own observations.

## Research Snapshot

- Researched: 2026-08-29.
- Local branch: `codex/moonflower-rebrand` at `47fa12462c9ff0fe14d4be5f12cb5b7fc109fb7f`.
- Authoritative vanilla-client reference checked: Seatribe `master` at
  `51842d0c70ac4ed9faef80ca49f0f2a208f24055`.
- Community mechanics references are not server contracts. They should be
  labeled and easy to update or disable.

## What The Client Already Knows

### Server-backed astronomy

`Glob.blob()` receives an `astro` update containing:

- fraction of the current day;
- fraction of the current moon cycle;
- fraction of the current year;
- authoritative night/day state;
- moon light color;
- season index and progress;
- calendar year, month, and day values.

The existing vanilla `Cal` already draws animated sun frames, one of eight moon
frames, a circular sun/moon path, separate day/night skies, and one landscape
for each season. MoonFlower's `Astronomy` extension also calculates game hour,
minute, season day, and remaining game days/hours/minutes.

This means the redesign should preserve the server astronomy payload and replace
only its presentation and derived calculations. It does not need an external
clock service.

### Existing MoonFlower clock text

`Glob.servertimecalc()` currently renders:

- absolute game day and `HH:MM:SS`;
- season name, season day, and remaining game/estimated real days;
- eight-phase moon name;
- a Dewy Lady's Mantle notice from 04:45 through 07:15 game time.

The existing real-time season estimate uses a world-ID allowlist and otherwise
assumes a 3.29 game-time multiplier. That can be wrong for a new or accelerated
world. A redesigned clock should always show the game-time remainder and label
real-time output as an estimate unless the multiplier is observed or otherwise
authoritative.

### Current-area information

The checkout already has local sources for:

- exact terrain/biome under the player, including friendly water names;
- province and realm names received through the server UI protocol;
- current map segment/grid and player coordinate context;
- players online and ping.

Province and realm are currently stored only as rendered textures. A future
implementation should retain the original strings in a small location model
rather than trying to read text back from a texture.

### Weather limitation

The client receives and renders weather resources, but `Glob.Weather` does not
provide a stable human-readable weather name. Weather should initially be
omitted or shown as `Weather unavailable`. A later capability probe may map
verified resource identities to labels, with unknown resources left unknown.

### Fishing observations

MoonFlower's fishing system already captures:

- game day and second of day;
- day/night, moon phase, and season;
- water tile and map location;
- pole, line, hook, bait/lure, and qualities;
- Survival, Will, displayed choices, catch, and quality.

That is a stronger basis for personal fishing guidance than a hard-coded global
fish table. The clock can eventually say what this player has actually observed
at the current spot and conditions without claiming to know the server formula.

## Verified And Reported World Mechanics

### Time and calendar

The community-maintained calendar documents:

- 24 Haven hours per game day;
- a normal-world game day of about 7 hours 18 minutes real time at 3.29x;
- a 180-game-day year;
- Spring 30 days, Summer 105, Autumn 30, and Winter 15;
- six 30-day months;
- eight moon phases, each about 3.75 game days.

The season lengths agree with the current MoonFlower calculations. They remain
derived client knowledge, not fields guaranteed by the protocol.

### Time-sensitive opportunities and hazards

Good candidates for contextual clock notices include:

| Condition | Useful information | Confidence |
| --- | --- | --- |
| 04:45-07:15 | Dewy Lady's Mantle harvest window | Current community reference and existing client rule |
| 04:45-07:15 | `Dawn Breaking` and `Dawn on the Mountain` experience opportunities | Current community reference |
| Night | `Driving out the Darkness` experience opportunity | Current community reference |
| Full moon | `Fish Moon` experience opportunity | Current community reference |
| Night | Moonmoths can be seen | Current community reference |
| Night in swamp/forest | Midge swarms are more prevalent; rain suppresses their spawn | Community reference backed by cited patch notes |
| Holiday content, 18:00-06:00 | Elven Lights and Yule Stars may be available | Seasonal community reference; hide outside relevant content |

These should be reminders, not automation. The clock must not claim an event is
uncompleted unless the client has an authoritative per-character lore state.

### Seasons

The official season announcement states that herbs, most small animals, and
some large animals have seasonality. It also describes winter crop fallow,
trellis changes, tree fruit loss, and water freezing. The current community
tables contain much more detailed creature and forageable observations, but
explicitly warn that absence is hard to prove and later patches can make the
tables stale.

Recommended use:

- show broad, well-supported season effects in a season details panel;
- allow a link into the native wiki reader for the detailed tables;
- do not hard-code every creature and forageable into the always-visible clock;
- if a small local catalog is added later, give it a source date/version and
  label entries `community reported`.

### Fish, time, and moon

The current casting-rod reference says the offered fish and catch chances depend
on fishing gear, moon phase, and the local fish node. Community discussion also
reports day/night or time effects, but the exact formula is not public and some
older fish charts are explicitly Legacy data.

The clock should therefore avoid predictions such as `Pike now` from a static
wiki chart. Safer features are:

- current moon phase and time block;
- `Full moon fishing: Fish Moon experience is available`;
- recent fish observed by this player under matching conditions;
- a link to the Fishing Journal filtered to current spot/time/moon;
- a confidence badge and sample count for learned patterns;
- `No local observations yet` when evidence is insufficient.

### Animal moon-phase claim

This research did not find a reliable current source showing ordinary animals
spawning only during particular moon phases. There is good evidence for:

- Moonmoths appearing at night;
- midge behavior varying with night and rain;
- creature seasonality;
- moon phase affecting fishing and a full-moon fishing experience.

Until live or authoritative evidence says otherwise, the redesigned clock must
not advertise moon-specific animal spawns as fact.

## Recommended Information Design

### Compact face: always visible

Keep the top HUD readable rather than turning it into a permanent dashboard.
The compact face should contain:

1. An animated sky window:
   - continuous sun and moon travel driven by server day fraction;
   - cross-faded dawn, day, sunset, and night colors;
   - current moon phase and moon-light tint;
   - season-specific foreground art;
   - reduced-motion mode that keeps the same information without continuous
     decorative animation.
2. Large game time, with seconds optional.
3. Haven date in unambiguous words, for example `Day 12, Month 3, Year 4`.
4. Season row, for example `Autumn 8/30 - 22d 04h to Winter`.
5. Area row, for example `Swamp - Westvale - Realm Name`, omitting unknowns.
6. One rotating or priority-selected `Now` notice, such as:
   - `Dawn window - 31m left`;
   - `Full Moon - Fish Moon opportunity`;
   - `Moonmoths visible`;
   - `Swamp at night - midge risk`;
   - `Fishing: 3 matching local observations`.

Use text or icons for state as well as color. Sunrise and sunset cannot be
communicated only by orange/blue hues.

### Expanded almanac: click or hover

The existing tooltip should remain available, but clicking the clock should
open a compact details surface with four sections:

- **Sky:** exact time/date, day phase, moon phase, time to next phase, season,
  and game/estimated-real countdown.
- **Area:** terrain/biome, province, realm, map identity, and weather only when
  a verified label exists.
- **Opportunities:** current and upcoming time/moon/season notices, ordered by
  soonest closing window and filtered by the current terrain where possible.
- **Fishing:** matching personal observations and a button/link to the filtered
  Fishing Journal.

An optional secondary footer can retain players online and ping. Those are
useful server facts but lower priority than the user's current world context.

### Additional feature candidates

Features worth considering after the compact face is proven:

- a `next transitions` strip for dawn, dusk, moon phase, and season rollover;
- configurable alerts a few real minutes before a selected window opens or
  closes, with quiet mode and no repeated spam;
- an area-filtered almanac showing community-reported forageables and creatures
  for this biome and season, with a direct native-wiki link;
- a winter-preparation notice summarizing crop fallow, water freezing, tree
  fruit, and other broad season effects before the rollover;
- learned `fish now / fish later` comparisons using only the player's local
  observations and showing sample sizes;
- an ambient-light cue for players planning travel without a light source;
- opt-in real-world local time beside Haven time, never replacing game time;
- a session history of season/moon transitions and notices actually observed;
- a small server-health footer using the already available population and ping;
- future weather-sensitive notices only after weather resource labels are
  verified;
- server-announced world events, such as a falling-star notice, only when the
  client receives a trustworthy event message rather than predicts one.

The compact face should not show all of these at once. They belong in the
expanded almanac, settings, or the single priority-selected `Now` notice.

### Provenance language

Each non-obvious notice should carry one small provenance category:

- `LIVE` - directly received from the current server/session;
- `CALC` - deterministically derived from live values;
- `GUIDE` - community-maintained reference that may change;
- `LEARNED` - the player's local MoonFlower observations.

The visual treatment can be subtle, but the expanded details must explain it.

## Suggested Activity Priorities

The clock should choose at most one compact notice using this order:

1. Short-lived hazard relevant to the current terrain and conditions.
2. Opportunity closing soon.
3. Newly opened time or moon opportunity.
4. Imminent season change.
5. Matching local fishing evidence.
6. General season or area fact.

Notices should be dismissible for the session and individually configurable.
Do not add automatic movement, harvesting, fishing, or world interaction.

## Calculation And Accuracy Rules

- Use the server `night` flag as the authoritative day/night state.
- Use server day fraction for the sun/moon position and game time.
- Use server calendar and season progress rather than a real-world start-date
  reconstruction.
- Show season remainder in game time even when real-time speed is unknown.
- Learn the real/game speed from observed monotonic deltas if practical; label
  the converted countdown `estimated real time` and handle accelerated worlds.
- Do not call the visual transition an exact sunrise/sunset boundary unless the
  server exposes or observation validates that boundary.
- Recalculate textual snapshots no more than once per second. The draw loop may
  interpolate animation without rebuilding text or allocating textures each
  frame.
- Unknown data is omitted or displayed as unavailable, never guessed.

## Smallest Safe Implementation Slice

When the feature is explicitly made active, the first useful slice should be:

1. Add a focused immutable world-clock snapshot built from `Glob` astronomy and
   player terrain context.
2. Replace the current small face only in MoonFlower HUD mode; keep classic
   `Cal` available and preserve its tooltip/hit behavior.
3. Draw the animated sky, game time/date, moon phase, season progress/countdown,
   terrain, province, and realm.
4. Add only three contextual notices initially:
   - Dewy Lady's Mantle/dawn experience window;
   - full-moon Fish Moon opportunity;
   - Moonmoth-at-night reminder.
5. Add reduced motion, supported-scale layout checks, and deterministic clock
   snapshot tests.

Weather names, complete season catalogs, lore-completion tracking, and learned
fishing forecasts should follow only after the core display is live-validated.

## Likely Future Code Boundaries

Avoid adding more responsibilities to `Glob`, `GameUI`, or `Cal`. A maintainable
implementation would likely separate:

- `WorldClockSnapshot`: immutable live and derived values;
- `WorldClockService`: builds snapshots and measures game/real speed;
- `LocationContext`: terrain, province, realm, and optional weather label;
- `TimeActivityCatalog`: small sourced rules with confidence and applicability;
- `MoonFlowerClockWidget`: compact drawing and interaction;
- `MoonFlowerAlmanacWindow`: expanded details and fishing-journal link;
- focused checks for phase boundaries, season rollover, unknown values,
  accelerated worlds, priority notices, and UI scaling.

Names are proposals, not implementation commitments.

## Acceptance Direction

A later implementation should be considered complete only when:

- it preserves the classic clock path when MoonFlower HUD is disabled;
- the sun/moon animation tracks live server time without discontinuities;
- date, moon, season, and countdown roll over correctly;
- accelerated or unknown-speed worlds do not show falsely exact real time;
- terrain, province, and realm update after travel and map transitions;
- unknown location/weather values do not leave stale text;
- notices identify their provenance and never present community claims as live
  server truth;
- animation and text remain readable at supported scales and resolutions;
- reduced-motion mode works;
- clock updates do not introduce per-frame texture churn or HUD refresh work;
- live in-world behavior is recorded separately from compilation and checks.

## Sources

### Project and authoritative client source

- `client/src/haven/Glob.java` - server astronomy, game time, light, sky, and
  weather payload handling; existing clock strings.
- `client/src/haven/Astronomy.java` - MoonFlower derived calendar values.
- `client/src/haven/Cal.java` - current compact animated calendar.
- `client/src/haven/GameUI.java` - clock/status placement, province/realm,
  population/ping, and world speed assumptions.
- `client/src/haven/MiniMap.java` - current biome/terrain lookup and naming.
- `client/src/haven/automated/FishingEnvironment.java` - fishing time, moon,
  season, water, and location capture.
- Seatribe Hafen client `master` at
  `51842d0c70ac4ed9faef80ca49f0f2a208f24055`, checked 2026-08-29.

### External references

- [Official Season's Greetings announcement](https://www.havenandhearth.com/forum/viewtopic.php?t=66280)
- [Ring of Brodgar: Seasons](https://ringofbrodgar.com/wiki/Seasons)
- [Ring of Brodgar: day/night and game clock](https://ringofbrodgar.com/wiki/Glossary#Day_and_Night_Cycle)
- [Ring of Brodgar: Experience Events](https://ringofbrodgar.com/wiki/Experience_Event)
- [Ring of Brodgar: Dewy Lady's Mantle](https://ringofbrodgar.com/wiki/Dewy_Lady%27s_Mantle)
- [Ring of Brodgar: Moonmoth](https://ringofbrodgar.com/wiki/Moonmoth)
- [Ring of Brodgar: Midge Swarm](https://ringofbrodgar.com/wiki/Midge_Swarm)
- [Ring of Brodgar: Primitive Casting-Rod](https://ringofbrodgar.com/wiki/Primitive_Casting-Rod)
- [Ring of Brodgar: creature seasonality table](https://ringofbrodgar.com/wiki/Tables/Seasons/Creatures)
- [Ring of Brodgar: forageable seasonality table](https://ringofbrodgar.com/wiki/Tables/Seasons/Forageables)
- [Community discussion of current fishing complexity](https://www.havenandhearth.com/forum/viewtopic.php?t=75291)
