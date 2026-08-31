# MoonFlower Autonomous Foraging System Production Prompt

Status: production brief with Phase 1 implemented offline on 2026-08-30;
supervised in-world validation remains required before later phases.

Research checked: 2026-08-30.

## Purpose

Use the prompt below to begin a phased, repository-grounded implementation of
a manually launched but otherwise autonomous foraging session. The completed
system should select named forageables, move through a bounded route or area,
avoid observable threats and cliffs, drink safely, use the main inventory and
equipped Wicker Pickers, consolidate compatible stacks, return to configured
storage, unload by rule, and resume when safe.

"Autonomous" does not mean autonomous login, account switching, crime,
teleportation, combat, or credential handling. The first production slice must
remain visible and supervised. Later unattended operation may be considered
only after each client/server interaction has been proven in a live session.

## Research Summary

### Current game mechanics

- A forageable must actually be visible to the current character before the
  client can target it. Community documentation describes visibility as a
  Perception × Exploration check, with rarity and terrain also affecting what
  the player encounters. These values are `GUIDE`, not server telemetry. Never
  claim that the bot can find an herb the server has not exposed as a Gob.
- Herb quality is community-documented as node-based and hardcapped by
  Survival. Treat displayed expectations as `GUIDE`; treat an observed item and
  its tooltip quality as `LIVE` only after the server creates it.
- A Wicker Picker is a 3×2 equipment item containing a restricted 4×5 storage
  grid. It accepts mushrooms, true herbs, fruits, berries, nuts, leaves, and
  flowers. A filled Wicker Picker belongs in its valid equipment slot or a
  non-player container. Do not model it as an unrestricted backpack.
- Equipped sub-inventories are permanently exposed to the client, and current
  game notes say auto-stacking in equipped containers was fixed in April 2026.
  The bot must still acknowledge actual server updates instead of assuming an
  item stacked successfully.
- Drinking restores stamina while consuming energy. Current community guidance
  says a hotbar water container can already auto-drink at low stamina. The bot
  must not fight that native behavior or drink below a configurable energy
  reserve.
- Deep water can drain stamina and cause drowning. Unless a later phase adds a
  separately verified vehicle/swimming policy, all deep-water edges are
  forbidden.
- Storage compatibility is type-specific. A flower stockpile is not a universal
  herb stockpile. Generic containers and user-configured Wicker Pickers are the
  safe default storage endpoints until each specialized destination is proven.

### Existing MoonFlower capabilities to reuse

- `client/src/haven/automated/InteractWithNearestObject.java` already recognizes
  `gfx/terobjs/herbs/` and uses Flower Menu option `Pick`.
- `client/src/haven/automated/pathfinder/Map.java` and `Pathfinder.java` provide
  local obstacle-aware A* movement, but they do not make broken ridge geometry
  a first-class forbidden edge.
- `client/src/haven/resutil/Ridges.java` exposes the authoritative client-side
  ridge test through `Ridges.brokenp(...)`, `RidgeTile`, elevation samples, and
  ridge break thresholds. Build cliff safety on this information.
- `client/src/haven/automated/AUtils.java` has an existing potential-aggro
  resource catalog. It is useful seed data but explicitly incomplete and must
  not be the only threat authority.
- `client/src/haven/automated/OceanScoutBot.java` contains a simple proximity
  danger response. Reuse the concept, not its random movement or hard-coded
  geometry.
- `client/src/haven/automated/FishingBot.java` demonstrates a bounded state
  machine, vital checks, combat shutdown, exact acknowledgement, cursor cleanup,
  timeouts, and visible failure reasons.
- `client/src/haven/automated/FishingInventory.java` demonstrates traversal of
  ordinary inventories, stacks, open contents windows, and equipped containers.
  Generalize the traversal rather than duplicating it.
- `client/src/haven/automated/RefillWaterContainers.java` knows water-container
  resource names, contents, equipped pouches, freshwater tiles, and water
  barrels. Extract reusable read-only classification before changing its live
  interaction logic.
- `client/src/haven/automated/StackAllItems.java` proves the current take →
  itemact pattern, but its name-based grouping and short sleeps are not robust
  enough for autonomous inventory management.
- `Inventory`, `InventorySorter`, `InventorySlotLocks`, and
  `GItem.ContentsWindow` provide transfer, sorting, lock, and equipped-container
  seams. Bot actions must preserve locked cells and never move protected gear.
- `CheckpointManager` and `saved_routes.db` already represent route checkpoints.
  Prefer a compatible route/anchor model instead of creating unrelated global
  coordinate assumptions.
- `haven.botcontrol.BotAction`, `BotActionContext`, `BotActionRegistry`,
  `BotAgentRuntime`, and `ClientStateCollector` provide optional operator
  integration. Keep the actual foraging controller inside the visible client
  and expose only explicit start/pause/resume/stop/snapshot actions.
- `MoonFlowerHudTheme`, the portrait HUD assets, and the completed UI component
  rules are mandatory for the new window. Do not add generic gray controls.

### External design precedent

The Nurgling bot client describes reusable actions, user-defined areas, storage
zones, and per-item destination rules. Those are sound architectural ideas, but
no third-party file should be copied wholesale. Reimplement only the concepts
that match MoonFlower's server messages, route identity, inventory rules, and
fail-closed requirements.

### Source links

- [Official Haven Q&A on bots and custom clients](https://www.havenandhearth.com/portal/paq)
- [Official Haven Terms and Conditions](https://www.havenandhearth.com/portal/eula)
- [Ring of Brodgar: Foraging](https://ringofbrodgar.com/wiki/Foraging)
- [Ring of Brodgar: Wicker Picker](https://ringofbrodgar.com/wiki/Wicker_Picker)
- [Ring of Brodgar: Waterflask](https://ringofbrodgar.com/wiki/Waterflask)
- [Ring of Brodgar: Water Terrain](https://ringofbrodgar.com/wiki/Water_Terrain)
- [Ring of Brodgar: Containers](https://ringofbrodgar.com/wiki/Category:Containers)
- [Ring of Brodgar: Stockpile](https://ringofbrodgar.com/wiki/Stockpile)
- [Nurgling client overview](https://www.havenandhearth.com/forum/viewtopic.php?f=49&t=74001)

## Completed MoonFlower UI Design Checklist

### Component

Name: MoonFlower Botanical Wayfinder

User goal: choose exact forageables and safely supervise an autonomous gathering
run without losing control of movement, inventory, equipment, or stored items.

Smallest useful behavior: select visible herb resource types, start a bounded
supervised run, path to one safe target at a time, pick it, acknowledge the
inventory result, and stop with a precise reason.

Classic behavior that must remain available: classic HUD, normal Gob clicks,
Flower Menu, inventory, equipment, map, hotbar auto-drink, and keybinds must be
unchanged when MoonFlower mode or the bot is off.

### Information model

| Value | Source | Provenance | Unknown behavior |
| --- | --- | --- | --- |
| Visible forageable | current `OCache` Gob/resource | `LIVE` | Do not list as nearby |
| Player position/motion | current player Gob | `LIVE` | Stop |
| HP, stamina, energy | current meters | `LIVE` | Stop |
| Combat relation | current `Fightview` | `LIVE` | Stop |
| Water amount/type | live item contents/tooltips | `LIVE` | Do not drink |
| Inventory/basket capacity | live inventories and item extents | `LIVE` | Return or stop |
| Current target/path state | controller state from live inputs | `CALC` | Show unavailable |
| Cliff/danger cells | loaded map/Gobs processed locally | `CALC` | Treat as blocked |
| PER×EXP thresholds | community data | `GUIDE` | Omit |
| Herb terrain/season hints | wiki/community data | `GUIDE` | Omit |
| Local yields/failed approaches | local run database | `LEARNED` | Show no observations |

### Visual system

- Use one dark ink herbarium panel with the existing floral frame.
- Represent selectable herbs as ivory botanical specimen cards seated in shared
  gold slot geometry. Use the live item icon where available.
- Use a teal route vine joining the state medallions: Survey, Travel, Gather,
  Sort, Return, and Store. The active state uses bright teal plus a text label
  and leaf-shape change.
- Use ruby only for danger, failed preflight, destructive clearing, or emergency
  stop. Never use ruby merely for an unselected herb.
- Put Start/Pause/Stop controls into a root-and-blossom artwork cluster using
  shared circular-control primitives. Stop must remain visually and spatially
  stable in every state.
- The center art is a visibly dynamic compass-flower: petals turn toward the
  current target and the vine advances along the current phase. Reduced motion
  freezes decorative sway while the target pointer and text still update.
- No generic desktop checkboxes should sit over themed art. Selection states
  belong inside the herb specimen slots.

### Layout

- Default: centered, movable normal window; preferred size about 720×560 at
  scale 1.0.
- Left column: searchable herbarium and selected-target summary.
- Center: compass-flower, phase vine, current target, distance, and reason.
- Right column: route/area, threat radius, water, inventory reserve, Wicker
  Picker, and storage-rule summaries.
- Bottom: stable Start/Pause/Stop cluster and expandable event ledger.
- At 1280×720, collapse the event ledger and use a two-column compact mode.
- At 1920×1080+, retain a maximum readable width; do not stretch text lines.
- Test UI scales 1.0, 1.25, 1.5, and 2.0. At 2.0, allow internal scrolling and
  clamp the complete frame on-screen.
- Do not cover the portrait dock, combat status/deck, top-center clock,
  minimap controls, chat input, or native Flower Menu.
- Persist window position, selected targets, and named profiles per world. Do
  not persist transient Gob IDs or an active running state across logout.

### Interaction

- Primary action: Start performs preflight and then begins the selected profile.
- Clicking a herb card toggles inclusion; Shift-click selects only that herb;
  Ctrl-click opens rule details without starting the bot.
- Hover shows display name, exact resource name, selected priority, destination,
  and correctly labeled guide/learned information.
- Escape pauses the bot first, then closes the window only when already idle or
  paused. A global emergency-stop key must work even when the window is hidden.
- Manual movement, a manual Gob interaction, or a held cursor item pauses the
  bot and yields to the player. Do not wrest control back automatically.
- Classic mode uses an ordinary native window and controls without hiding any
  original UI. Bot behavior must not depend on MoonFlower art being enabled.

### Fail-closed conditions

Stop or pause before sending another gameplay message when any of these occurs:

- player/map/session/inventory/equipment UI disappears;
- combat begins, the player is knocked out, HP is critical, or a hostile player
  is detected inside the configured boundary;
- stamina or energy is unreadable; water is unavailable/unknown; the selected
  drink action is unacknowledged;
- cursor is occupied unexpectedly or a protected slot/item would be moved;
- target/resource identity changes, target disappears, or `Pick` is absent;
- path touches unknown terrain, a broken ridge, deep water, an unsafe diagonal,
  an unloaded map margin, or a threat exclusion zone;
- movement/pick/transfer acknowledgement times out after one bounded recovery;
- inventory has no reserved recovery cells, Wicker Picker compatibility is
  unknown, storage is full/wrong/unreachable, or unloading would mix against a
  user rule;
- logout, map segment change, hearth travel, entering a building/cave, or server
  reconnect occurs without an explicit profile transition.

### Code boundaries

Presentation: `client/src/haven/foraging/ForagingWindow.java`

Immutable state: `ForagingSnapshot`, `ForagingProfile`, `ForagingTarget`,
`ForagingStorageRule`, `ForagingEvent`

Services/adapters: `ForagingGobScanner`, `ForagingRoutePlanner`,
`ForagingThreatService`, `ForagingInventoryService`, `ForagingWaterService`,
`ForagingStorageService`, `ForagingRepository`

Controller: `ForagingController`

Shared utilities that may be extracted: a read-only reachable-inventory walker,
an acknowledged item transaction helper, and a map safety raster. Do not make a
generic framework before two real callers need it.

Persistence: `ClientData.sqlite("foraging.db")` for profiles, storage anchors,
and local run events; existing MoonFlower preferences only for simple UI state.

Central integration: `GameUI` may own one controller/window reference and
lifecycle hook only. Do not put scanning, pathing, water, storage, or drawing
logic into `GameUI`, `Glob`, `Window`, or `Gob`.

## Copy/Paste Production Prompt

```text
Implement the MoonFlower Autonomous Foraging System in phased, reviewable
slices. Read AGENTS.md, docs/CURRENT_TASKS.md, docs/CODING_STANDARDS.md,
docs/FILE_ORGANIZATION.md, docs/templates/MOONFLOWER_UI_COMPONENT.md, and
docs/prompts/MOONFLOWER_AUTONOMOUS_FORAGING_PRODUCTION_PROMPT.md before editing.

First inspect the live worktree and preserve every unrelated modified or
untracked file. Do not overwrite existing MoonFlower HUD, clock, combat, wiki,
fishing, feasting, inventory-lock, Steam, or artwork work. Record the current
branch, commit, dirty paths, and relevant native/client source refs before making
compatibility claims.

Goal

Build a manually launched, visible-client foraging system that can, after safe
preflight, autonomously follow a user-bounded route or area; select only chosen
forageable resource types; avoid observable creatures, players, cliffs, deep
water, unloaded terrain, and obstacles; drink from approved water containers;
use the main inventory and equipped Wicker Pickers; consolidate only
server-compatible stacks; return to user-configured storage; unload herbs by
explicit rule; and resume until its route, duration, yield, or safety limit is
reached.

Do not implement the whole system in one patch. Complete Phase 1 first and stop
for review unless the user explicitly authorizes the next phase. Every phase
must leave the client buildable and the bot safely stoppable.

Non-negotiable boundaries

1. No autonomous login, credentials, account switching, client relaunch,
   teleportation, criminal acts, trespass enabling, PvP, combat rotation,
   vehicle control, swimming, cave transitions, or remote input.
2. One visible client process per character. Never share a GameUI, Glob, map,
   preferences, or mutable bot state between accounts.
3. No server-message spam and no blind sleeps as acknowledgement. Dispatch one
   interaction, observe a concrete state change, then continue or time out.
4. Manual movement, manual interaction, Escape, emergency stop, combat, logout,
   or unexpected cursor state always wins over automation.
5. Unknown map, resource, inventory, water, destination, threat, or server state
   is unsafe. Pause or stop with a visible reason.
6. Do not infer live behavior from compilation or deterministic checks. Record
   live checks separately as PASS, FAIL, or NOT RUN.
7. Never inspect, store, log, transmit, or package account credentials, cookies,
   session tokens, map caches, routes outside the selected profile, or unrelated
   user data.
8. Keep operator services loopback-only. Do not expose a network service or add
   a scheduler in this task.
9. Preserve InventorySlotLocks. Never move an item touching a locked cell, a
   configured protected item, a bucket, or equipment not explicitly approved by
   the profile.
10. Before any clean build/package, prove the client is stopped with
    scripts/assert-client-stopped.ps1. Do not package or deploy in Phase 1.

Architecture

Create a focused `haven.foraging` package. Use immutable snapshots and explicit
services rather than growing GameUI or a single bot window.

- ForagingController: the only state-machine owner and gameplay dispatcher.
- ForagingProfile: world-scoped configuration; selected resource identities,
  priorities, route/area, threat policy, water thresholds, inventory reserve,
  Wicker Picker policy, storage rules, duration/yield limits.
- ForagingSnapshot: immutable current player, route, target, vitals, inventory,
  threat, map-safety, and status values consumed by the UI.
- ForagingGobScanner: scans only loaded OCache Gobs. Classify normal herbs by
  exact `gfx/terobjs/herbs/` resource identity and keep explicit, reviewed
  exceptions such as Precious Snowflake in a small atlas. Display names are not
  stable identities.
- ForagingRoutePlanner: plans across a safety raster combining passable terrain,
  Gob hitboxes, ridge edges, water policy, unknown-map margins, and dynamic
  threat exclusion zones. It may adapt the existing A* implementation, but must
  not mutate global pathfinder state or start overlapping path threads.
- ForagingThreatService: combines a reviewed resource catalog, live combat
  relations, player Gobs, resource/pose/state observations, configurable
  distance rings, and uncertainty. AUtils.potentialAggroTargets is seed data,
  not complete authority.
- ForagingInventoryService: traverses main inventory, visible/open inventories,
  stack contents, and equipped Wicker Picker contents by exact widget/item
  identity. Reuse or extract FishingInventory traversal semantics.
- ForagingWaterService: reads approved freshwater containers and orchestrates a
  single acknowledged Drink transaction with threshold hysteresis.
- ForagingStorageService: resolves configured world/segment anchors and exact
  destination rules, opens one endpoint, transfers one verified item/stack at a
  time, and stops if compatibility or capacity is ambiguous.
- ForagingRepository: stores profiles and local event/observation history in
  ClientData.sqlite("foraging.db"). Do not write active state that auto-resumes
  after login or a crash.
- ForagingWindow: themed presentation and user control only.

Use a single-threaded logical state machine. UI-thread-safe snapshots may be
published to the widget. Never read or mutate live widget trees concurrently
without following the client's established synchronization/deferred-work
patterns.

Controller states

IDLE -> PREFLIGHT -> SCANNING -> PLANNING -> TRAVELING -> APPROACHING -> PICKING
-> ACKNOWLEDGING -> CONSOLIDATING -> SCANNING

Optional later branches:

- any active state -> PAUSED -> PREFLIGHT
- low stamina -> DRINKING -> prior safe state
- capacity threshold -> RETURN_PLANNING -> RETURNING -> OPENING_STORAGE ->
  STORING -> RESUME_PLANNING
- recoverable movement failure -> RECOVERING -> PLANNING
- terminal condition -> STOPPING -> IDLE
- unsafe/unrecoverable condition -> FAILED

Every transition must state its guard, action, acknowledgement, timeout, retry
budget, cancellation behavior, and visible status reason. No transition may
silently retry forever.

Target discovery and scoring

1. Consider only currently loaded, visible Gobs with a successfully resolved
   resource that exactly matches the profile's selected resource identities.
2. Never fabricate a target from GUIDE terrain/season/PER×EXP data or a saved
   observation. Saved data may influence route guidance only.
3. De-duplicate by Gob identity for the current session. Keep bounded temporary
   blacklists for disappeared, unreachable, unsafe, and repeatedly failed Gobs.
4. Score candidates deterministically by user priority, route progress,
   path-safe distance, threat clearance, storage pressure, and limited
   backtracking. Do not use straight-line distance when a cliff separates the
   player and target.
5. Revalidate the exact Gob/resource immediately before dispatching Pick.
6. Set FlowerMenu selection to `Pick` only for the exact target interaction.
   Verify that the expected menu option or direct result occurs; clear any armed
   automatic selection on pause/stop/failure.
7. A successful gather requires a concrete acknowledgement: target removal or
   state change plus a compatible item delta in a reachable inventory. If these
   disagree, record an uncertain event and stop rather than attributing an
   unrelated new item.

Route and area model

- Phase 1 uses a recorded checkpoint route with a configurable collection
  corridor. Reuse CheckpointManager's per-segment/grid-relative model or create
  a compatible immutable adapter. Do not rely on transient Gob IDs or naked
  process-local world coordinates across sessions.
- A later area mode may use a user-drawn polygon and deterministic boustrophedon
  lanes clipped to loaded safe terrain. Random wandering is not acceptable.
- Store a safe return breadcrumb after each acknowledged segment. Dynamic
  rerouting may use only currently loaded safe terrain and a bounded radius.
- Detect route divergence, stationary movement, oscillation, and repeated
  replans. One local recovery is allowed; then pause with diagnostics.
- Never click beyond loaded/safety-rastered terrain. Long routes must be
  segmented and revalidated as new grids load.

Cliff and terrain safety

This is a hard safety feature, not a visual heuristic.

1. Use `Tiler instanceof Ridges.RidgeTile`, `Ridges.brokenp(map, tile)`,
   `RidgeTile.breakz()`, and loaded elevation samples to construct forbidden
   ridge edges. Do not infer cliffs from minimap color or the optional cliff
   highlight setting.
2. Rasterize edges, not merely whole tile names. Expand each forbidden edge by
   player collision radius plus a configurable safety margin.
3. Prevent diagonal corner cutting across either forbidden orthogonal edge.
4. Treat missing tiles, Loading, nil tiles, map margins, invalid elevation, deep
   water, cave transitions, and unresolved ridge geometry as blocked.
5. Validate both the planned polyline and the exact next server click against a
   fresh safety snapshot. Dynamic map changes invalidate the path.
6. The target may be visible across a cliff. If no loaded safe approach exists,
   mark it temporarily unreachable and continue scanning; never walk into the
   ridge or spam replans.
7. Add deterministic synthetic-map tests for straight, corner, diagonal,
   multi-tile, map-edge, and changing ridge cases.

Threat avoidance

- Default threat policies: avoid known dangerous wildlife; avoid unknown
  creature classifications conservatively; avoid all non-party player Gobs by
  a larger configurable radius; immediately pause on a combat relation.
- Use two rings with hysteresis: a planning exclusion ring and a larger
  emergency ring. A moving threat predicts a short bounded intercept corridor.
- A threat entering the planning ring invalidates the route and target. If a
  safe breadcrumb path exists, retreat along it. If not, stop movement, cancel
  the interaction cursor, alert the user, and remain paused.
- Do not implement fighting, aggro, kiting, hearth escape, logout escape, or
  animal-specific bravery logic in this feature.
- Expose the exact observed reason: resource, Gob ID for current-session debug,
  distance, ring, and whether the classification is LIVE or CALC. Do not persist
  other players' identities or long-term movement history.

Water and vitals

- Profile defaults should be conservative and adjustable: drink below a stamina
  low-water mark, stop drinking above a higher mark, and preserve an energy
  floor well above starvation. Do not hard-code community values as universal.
- If native hotbar auto-drink is active and working, observe it and wait; do not
  dispatch a second Drink. Never change the user's hotbar or preference.
- Select an approved water container by exact item identity and live contents.
  Reject salt water, unknown liquid, unreadable contents, or a container needed
  by a protected equipment rule.
- Pause movement, require an empty cursor, invoke one normal Drink action, and
  acknowledge by a valid pose/meter/content transition. Time out once and stop.
- If stamina is low and no safe drink is possible, return only if the route is
  safe at the available speed; otherwise stop. Never enter deep water.
- Low HP, critical energy, knockout, combat, or unreadable meters is terminal for
  the run. This task does not add auto-eating.

Inventory, stacking, and Wicker Pickers

- Maintain configurable reserved free cells in the main inventory for cursor
  recovery and equipment safety. Capacity calculations must include item
  extents, masks, and locked cells.
- Prefer normal server auto-stacking. Do not assume two items are compatible
  from display name alone or manually merge qualities without server proof.
- If explicit consolidation is needed, resolve exact resource/stack contents,
  move the smaller compatible stack onto another once, and acknowledge the
  actual stack/count/cursor changes. On ambiguity, restore the cursor and stop.
- Discover equipped Wicker Pickers from Equipory/GItem contents metadata and
  traverse their live sub-inventories even when their windows are not detached.
  Add a dedicated classifier similar to FishingAtlas.isCreel.
- Route only known-compatible gathered items into the Wicker Picker. Keep
  rejected items in the main inventory and show why. Never unequip a non-empty
  Wicker Picker into the player inventory.
- Support zero, one, or two equipped Wicker Pickers without overlapping identity
  or window-position assumptions. Never merge their grids.
- Run storage return before every reachable inventory is completely full.
  Preserve water, tools, protected gear, locked slots, and reserve cells.

Storage rules

- The user configures ordered rules by exact gathered resource or category:
  destination anchor, accepted minimum/maximum quality if desired, preferred
  container type, overflow destination, and stop-on-full behavior.
- Record destination identity as world + map segment/grid/offset + expected Gob
  resource + a user label. On every run, resolve nearby and confirm all fields;
  never trust a stale transient Gob ID.
- Phase 3 supports already-designated generic containers and Wicker Pickers.
  Add stockpiles only with an explicit compatibility atlas and live validation.
- Approach through the same safety planner, right-click/open normally, confirm
  the expected inventory belongs to the exact Gob, and transfer one matching
  item/stack at a time.
- A transfer succeeds only when the source delta and destination delta agree.
  If a stack split, auto-stack, quality mix, full destination, closed window, or
  server rejection makes the result ambiguous, stop and retain remaining items.
- Never drop herbs on the ground as overflow. Never choose an arbitrary nearby
  container. Never store into a container not selected by the user.
- After unload, verify water/reserved cells/profile equipment, close only windows
  opened by the bot, and resume at the last safe breadcrumb.

UI requirements

Implement the Botanical Wayfinder checklist in this document. Reuse
MoonFlowerHudTheme's ink, teal, gold, ivory, ruby, panel, vine, blossom, slot,
and circular-control vocabulary. Controls must be integrated into the artwork.
The dynamic compass-flower should point toward the current target and the phase
vine should show Survey, Travel, Gather, Sort, Return, and Store. Reduced-motion
mode freezes decorative motion but not state or target direction.

The herb selector must use exact resource identities, live item/Gob icons where
available, search, select-all-visible, clear, per-herb priority, and per-herb
destination summary. GUIDE data must be labeled GUIDE, local observations
LEARNED, derived path/status CALC, and direct session data LIVE.

Keep Start, Pause/Resume, and Emergency Stop obvious and stable. Show the most
recent reason, current state, target, safe-path distance, threat clearance,
stamina/energy, water reserve, main free cells, each Wicker Picker's capacity,
storage destination, route progress, yield, and bounded event ledger. State is
never communicated by color alone.

Operator integration (Phase 4 only)

- Register explicit `foraging.start`, `foraging.pause`, `foraging.resume`, and
  `foraging.stop` BotActions with validated profile IDs, plus a read-only
  snapshot.
- Keep the server loopback-only and authenticated. Do not add arbitrary resource
  names or raw gameplay messages to the remote API.
- Cancel must call the same idempotent controller stop path as the UI emergency
  stop and must clear armed Flower Menu selections/path threads/cursors safely.
- Telemetry may expose state, reason, selected target display name, route
  progress, vitals percentages, capacity, and aggregate yield. Exclude
  credentials, character secrets, other-player identity, raw map data, and
  precise long-term location history.

Phased delivery

Phase 0 - audit and contracts

- Trace exact current Gob Pick, Flower Menu, movement, path completion, item
  creation, stack, equipped Wicker Picker, container open/transfer, meter, and
  combat signals.
- Write small interaction contracts and deterministic fakes before gameplay
  dispatch. Update this design only where repository evidence requires it.

Phase 1 - supervised single-target gatherer (implement first)

- Themed Botanical Wayfinder shell and exact herb selection.
- Manual route/corridor profile with no storage return.
- Scan loaded visible herbs, safe local path, one target interaction at a time,
  inventory acknowledgement, bounded blacklist, pause/stop/manual takeover.
- Block broken ridges, deep water, unknown terrain, combat, critical vitals,
  occupied cursor, and insufficient reserved inventory.
- No explicit drinking, Wicker Picker routing, stacking, storage, operator API,
  scheduling, or unattended restart yet.

Phase 2 - survival and carrying

- Dynamic threat raster/retreat, acknowledged drinking, reachable-inventory
  service, Wicker Picker routing, safe stack consolidation, and capacity return
  trigger (stop at trigger until Phase 3 exists).

Phase 3 - storage and resume

- Destination setup UI, stable anchors, rule engine, safe return route,
  acknowledged unload, overflow/stop behavior, and breadcrumb resume.

Phase 4 - operator visibility and hardening

- Narrow BotAction integration, telemetry, long-duration supervised soak tests,
  crash/reconnect non-resume behavior, and performance bounds. No scheduler or
  autonomous login without a separate user-approved design.

Phase 1 acceptance criteria

1. The user can select exact currently known herb resources and save a
   world-scoped profile without starting the bot.
2. Start fails preflight without sending gameplay input if player/map/session,
   route, inventory reserve, meters, or resource selections are invalid.
3. Only loaded visible selected `gfx/terobjs/herbs/` Gobs and reviewed explicit
   exceptions can become targets.
4. Candidate choice is deterministic and based on safe-path distance, not merely
   straight-line distance.
5. Every planned path and outgoing movement click rejects unknown terrain,
   broken ridges, unsafe diagonals, deep water, map margins, and Gob obstacles.
6. A visible selected herb across an unresolved cliff is blacklisted temporarily
   without movement spam.
7. The controller sends one Pick interaction and waits for exact target and
   inventory evidence before selecting another target.
8. An unrelated new inventory item is not attributed as the gathered herb.
9. Combat, critical/unreadable vitals, manual movement/interaction, unexpected
   cursor item, logout, segment transition, route divergence, timeout, or no
   reserve pauses/stops with a precise visible reason.
10. Pause and emergency stop are idempotent, cancel movement and armed Flower
    Menu selection, and never leave a bot-owned item on the cursor.
11. Classic UI and manual forage interaction behave exactly as before when the
    bot is idle or MoonFlower HUD is disabled.
12. The themed window remains readable/on-screen at 1280×720 and UI scales 1,
    1.25, 1.5, and 2; reduced motion has a static decorative equivalent.
13. Focused state, scoring, ridge, terrain, cancellation, profile persistence,
    and layout checks pass, followed by a clean client build with the client
    stopped.
14. A supervised live session records Pick option/result semantics, path safety,
    inventory acknowledgement, manual takeover, and visual layout separately.
    Anything not observed is marked NOT RUN.

Later-phase acceptance criteria

1. Threat rings reroute or retreat without approaching a detected danger and
   stop rather than fight if no safe retreat exists.
2. One approved freshwater container is used with threshold hysteresis and a
   concrete acknowledgement; native hotbar auto-drink is never duplicated.
3. Main inventory and each equipped Wicker Picker retain distinct identity,
   capacities, locks, and compatibility; filled pickers are never placed into
   player inventory.
4. Stack consolidation never uses display name alone and always restores or
   stops on an ambiguous cursor/server result.
5. Storage anchors resolve to the exact expected world/segment/resource, and no
   arbitrary container or ground-drop fallback exists.
6. Each unload is acknowledged at both source and destination; full/wrong/closed
   storage leaves items safe and stops with a reason.
7. After verified unload, the controller resumes from a safe breadcrumb without
   skipping route safety checks.
8. Logout, crash, reconnect, or client restart never auto-resumes a run.

Verification

Add focused deterministic checks under `haven.foraging` and keep them runnable
without a real account. Cover state transitions, retry budgets, deterministic
target scoring, exact resource classification, synthetic ridge geometry,
unknown/deep-water rejection, diagonal corner blocking, moving threat zones,
inventory masks/extents/locks, Wicker Picker compatibility and separate
identity, stack acknowledgements, stable storage anchor resolution, transfer
transactions, profile migration, provenance labels, layout, classic regression,
and reduced motion.

Before building:

    .\scripts\assert-client-stopped.ps1

Then use the repository's normal Ant build and focused check class. Run
`git diff --check` on every changed file. Do not run or package
`artifacts/legacy-launcher/autohaven-socrates556.jar`.

For live validation, make a recoverable client-data backup first. Use a visible,
user-supervised character in a low-risk test area with disposable/common herbs,
an empty cursor, ample inventory reserve, no deep water, and no approved danger.
Test one behavior at a time. Preserve logs/screenshots and record PASS, FAIL, or
NOT RUN. Compilation and mocks never count as proof of server acknowledgement,
path safety, Wicker Picker behavior, drinking, storage, or unattended stability.

At handoff, report:

- files changed and each responsibility;
- the exact implemented phase and what remains intentionally absent;
- deterministic commands/results;
- live checks and evidence separately;
- known GUIDE/LEARNED limitations;
- any technical debt in docs/TECHNICAL_DEBT.md;
- the safest next production slice, without beginning it unless authorized.
```

## Design Decision Notes

1. The system begins from a manually defined route/corridor, not unrestricted
   random roaming. This gives the player a reviewable exploration boundary and
   provides safe breadcrumbs for threat and storage returns.
2. Cliff safety is modeled as forbidden edges derived from the same ridge data
   the client uses to render broken terrain. Tile-name blocking alone cannot
   prevent unsafe diagonals or targets visible across a ridge.
3. Wicker Picker support is delayed until the base gather transaction is proven.
   Equipped containers have identity, compatibility, and placement semantics
   that deserve focused tests rather than being treated as extra inventory rows.
4. Storage is rule-based and explicitly anchored. "Nearest container" would be
   convenient but could irreversibly mix valuable items or use another player's
   storage.
5. Combat avoidance means prevention, route invalidation, and retreat—not an
   autonomous fighting or logout system.
6. Runtime persistence never contains a running flag. After logout, reconnect,
   crash, or restart, the user must explicitly start a new run.
