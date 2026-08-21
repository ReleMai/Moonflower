# Fishing Helper And Catch Journal Prompt

Use this prompt after the current in-game Cookbook live-validation task is
complete, or after the user explicitly authorizes replacing that active task.

```text
Implement the next client feature as one bounded, client-local vertical slice:
upgrade the existing Hurricane Fishing Bot into a fishing helper that can equip
and prepare a pole from the player's available inventory, start fishing at a
nearby valid water location, and persist trustworthy catch observations so the
player can rediscover productive fishing spots.

Start by reading AGENTS.md, docs/CURRENT_TASKS.md, docs/ARCHITECTURE.md,
docs/CODING_STANDARDS.md, docs/FILE_ORGANIZATION.md, docs/DATA_BACKUP.md,
docs/UPSTREAM_PROVENANCE.md, and docs/VERIFICATION.md. Inspect git status and
preserve all uncommitted user work. The Cookbook work currently modifies shared
client seams, including GItem and GameUI, so do not overwrite or casually fold
those edits into this feature. Do not replace docs/CURRENT_TASKS.md until its
current live-validation task is complete or the user explicitly approves the
task switch.

Before editing, inspect and briefly explain the actual relevant code, especially:

- client/src/haven/automated/FishingBot.java
- client/src/haven/automated/helpers/FishingAtlas.java
- client/src/haven/automated/AUtils.java
- client/src/haven/automated/EquipFromBelt.java
- client/src/haven/GameUI.java
- client/src/haven/GItem.java and ItemInfo.Contents
- client/src/haven/Glob.java and Astronomy.java
- client/src/haven/MapFile.java, MapWnd.java, MCache.java, and WaterTile.java
- client/src/haven/botcontrol/BotActionRegistry.java
- client/src/haven/ClientData.java
- the local SQLite patterns under client/src/haven/cookbook/

Record and use the exact current upstream refs before making compatibility
claims. Hurricane is the custom-client upstream and Seatribe is authoritative
for the vanilla protocol/client. Do not copy an old FishingBot file wholesale:
the local bot and its botcontrol integration are custom touchpoints. At the time
this prompt was written, the recorded refs were Hurricane v1.69 at
045b1f598a9009b279b778eeb2256c651baf88f8 and Seatribe
f4b86b85514ef3d77d95b08be0bea46179ef0b3a; refresh them if they may have moved.

Research-grounded mechanics to design around:

- Fishing uses a pole or rod, a dedicated fishline, a hook, and bait or a lure.
- Bushcraft Fishingpoles use expendable bait and can automatically catch,
  re-bait, and recast. Primitive Casting-Rods use lures and present fish choices
  with bite/landing percentages.
- Fish are localized resources. Results vary with location, game time, tackle
  types, and tackle qualities. Moon phase is also widely reported as relevant
  and is already exposed by Glob.ast, so record it even if the current wiki text
  is incomplete or contradictory.
- Survival affects caught-fish quality, and Survival plus Will affects the lure
  fishing choice list. Record both when they are available; never invent them.
- Lines and hooks can be lost or break, bait is consumed, and lures can be lost.
- A fish node is not necessarily a discrete Gob. Do not equate any water tile
  with a proven fish node or claim that jumping-fish visuals are detectable
  until the code/resource signal is verified. Persist the actual cast point and
  observed results; infer spot clusters only as a clearly labeled derived view.

Required first-slice behavior:

1. Extend or refactor the existing FishingBot; do not create a second competing
   bot. Keep the existing visible window and fishing.start/fishing.stop command
   contract working. Replace reflection-based/private-button coupling with a
   small public start/stop API if that can be done without expanding server or
   protocol scope.

2. When Start is pressed, select an allowed pole from the player's reachable
   inventories using stable resource names where possible, not only localized
   display text. If the pole is not already equipped, safely equip it. Preserve
   displaced hand items by moving them to confirmed free inventory locations,
   and restore them on a normal stop when possible. Abort before moving anything
   if there is no safe destination. Never drop or destroy equipment as a fallback.

3. Inspect the pole's actual contained items rather than assuming that the count
   of ItemInfo.Contents entries proves a valid order. Attach a compatible
   fishline, hook, and bait or lure from the allowed selections. Make selection
   order deterministic and visible (for example, chosen user priority and then
   quality), not Collections.shuffle(). Verify each server-confirmed state change
   before continuing. Handle Loading without treating it as missing equipment.

4. Find a nearby castable water coordinate within a small, documented radius.
   Prefer a verified current-session fish-node signal if one exists; otherwise
   call the behavior "nearby fishable water," not node detection. Use WaterTile
   or another authoritative tile/resource check, reject shore-adjacent or
   otherwise invalid points when the current game rules require it, and do not
   add autonomous travel, boats, long-range pathfinding, or node searching in
   this slice. Save the actual cast coordinate, not just the player's position.

5. Start fishing through the current resource-backed Fishing action. For lure
   fishing, inspect the current "This is Bait" choice widget structurally and
   capture the displayed candidate fish plus gear, lure, and final percentages.
   Use an explicit target policy: select a user-requested fish if present;
   otherwise use the visible highest final chance or the game's documented
   default. Do not parse rows by fragile child indexes without checks, and do not
   silently assume a translated window caption.

6. Detect a caught fish using a causal, testable signal tied to the active cast.
   An arbitrary inventory arrival is not enough. Correlate the fishing state,
   pole/cast session, server UI/pose transition, and new item resource. If exact
   provenance cannot be proven in the first slice, record the row as
   confidence="candidate" and show that limitation; never label it a confirmed
   catch merely because a fish item appeared near the expected time.

7. Persist observations locally in a new fishing.db resolved through ClientData,
   not in the packaged client directory and not in the external Spring server.
   Serialize database access off the UI thread. Use prepared statements,
   migrations, and idempotent schema creation. Do not store account credentials,
   session tokens, or private authentication material.

For every confirmed or candidate catch observation, store all available fields:

- world/map-cache scope; map segment ID; grid ID; grid-local offset or tile;
  exact cast coordinate; player coordinate only as optional context
- real observation timestamp and in-game day/time; game-time seconds; day/night;
  moon phase; season when available
- fish resource name, display name, quality, and any reliable size/weight data
- pole, fishline, hook, and bait/lure resource names and display names
- quality of every tackle component, with null meaning unavailable
- whether the consumable was bait or lure, and whether any component was
  consumed, lost, or broken during the attempt
- lure-window candidate rows and their gear/lure/final percentages when present
- Survival and Will values when reliably available
- water tile/resource type; attempt outcome (caught, no catch, interrupted,
  invalid target, inventory full, missing tackle, lost component, or unknown)
- provenance confidence and a schema version

Do not deduplicate away repeated catches: repeated observations are the useful
evidence. A separate derived query may group nearby cast points by fish, tackle,
moon phase, and time bucket and report sample count and success rate. Do not
create permanent MapFile markers in the first slice. Show saved spots in a small
read-only journal/list or transient map overlay backed by fishing.db. If a later
slice writes map markers or changes live map caches, first resolve exact paths,
stop the client as required, and run scripts/backup-client-data.ps1.

Safety and stop behavior:

- Keep this a visible, explicitly started client helper. Do not add auto-login,
  credential handling, unattended relaunch, or hidden background startup.
- Provide an immediate Stop that cancels pending waits/actions, ends the cast,
  and leaves no item on the cursor. Do not use fixed sleeps as the primary state
  machine; use bounded waits for observable state transitions.
- Stop cleanly on combat/threat, logout, missing player/map/UI, low HP/energy,
  full or fragmented inventory, missing/lost tackle with no replacement,
  invalid water target, or repeated timeout. Do not automatically hearth without
  making that behavior a separate explicit option; the default is stop and
  report the exact reason.
- Avoid action spam. Only send the next server action after the prior state is
  confirmed or a bounded retry policy permits it.
- Keep all UI mutations on the appropriate UI thread and make worker shutdown
  deterministic. Closing the window and fishing.stop must be idempotent and
  null-safe even if the player or map disappears.

Keep the code organized. FishingBot should coordinate UI and state, not also own
SQL, coordinate conversion, inventory algorithms, catch parsing, and analytics.
Prefer small fishing-focused model/service/repository classes and pure checks.
Reuse proven local utilities only where their behavior matches these safety
requirements; do not inherit unsafe belt-only assumptions or five-millisecond
timing guesses from EquipFromBelt.

Acceptance criteria:

1. Existing user work is preserved and the exact upstream refs are recorded.
2. With the selected pole and tackle in reachable inventory, Start safely equips
   the pole, attaches the required parts in order, and casts at a verified nearby
   water point without dropping displaced hand items.
3. Missing space, missing tackle, Loading, invalid water, logout, and Stop all
   end in a clear, recoverable state with no cursor-held item and no action loop.
4. Bushcraft bait replacement and Primitive Casting-Rod lure selection both have
   defined, observable behavior; component loss triggers a bounded re-equip or a
   clear stop.
5. A caught fish produces exactly one confirmed observation, or an honestly
   labeled candidate if causal proof is unavailable. Unrelated inventory changes
   do not produce confirmed catches.
6. The saved observation includes the actual location, fish, all tackle types,
   tackle qualities, in-game time, moon phase, fish quality, water type, outcome,
   and available lure percentages/stats. Restarting the client preserves it.
7. The player can review recent catches/spots in a small local view; repeated
   observations remain available for later success-rate analysis.
8. fishing.start, fishing.stop, window close, and repeated Stop are safe and do
   not leave a running thread or stale GameUI reference.
9. Offline checks and the client build pass. Live login, casting, catch
   correlation, and map-coordinate behavior are reported as unverified until
   observed in a visible, user-supervised session.

Verification should include focused offline checks for:

- schema creation/migration and restart persistence
- repeated observation retention and derived grouping
- world/segment/grid/local coordinate conversion
- game-time, day/night, moon-phase, and season capture with missing Astronomy
- deterministic tackle choice and resource-name matching
- incomplete/translated/reordered lure-window rows
- catch correlation versus unrelated inventory additions
- every stop reason, timeout, close, and idempotent cleanup path

Then run at minimum:

Push-Location client
ant deftgt
java -cp "bin/hafen.jar;bin/sqlite-jdbc-3.42.0.0.jar" <new fishing checks class>
java -cp bin/hafen.jar haven.Resource find-updates
Pop-Location

Before the first supervised live test, stop all Haven clients and run:

.\scripts\backup-client-data.ps1

In the visible session, verify both pole types, safe hand-item restoration, a
nearby valid cast, one caught fish, a non-fishing inventory arrival, component
replacement, Stop during a wait, and persistence after restart. Compilation is
not proof of live fishing, resource, protocol, or coordinate correctness.

After implementation, explain in beginner-friendly language: files changed,
each file's responsibility, the helper state flow, how catch confidence works,
where fishing.db lives, what was verified offline, what remains live-unverified,
and the one next logical slice. Record any shortcuts in TECHNICAL_DEBT.md. Keep
only one concrete next slice in CURRENT_TASKS.md.
```

## Research References

- [Ring of Brodgar: Fishing](https://ringofbrodgar.com/wiki/Fishing)
- [Haven forum: casting-rod fishing and node behavior](https://www.havenandhearth.com/forum/viewtopic.php?f=42&t=65074)
- [Hurricane v1.69 FishingBot source](https://github.com/Nightdawg/Hurricane/blob/v1.69/src/haven/automated/FishingBot.java)
- [Hurricane project documentation](https://nightdawg.github.io/HurricaneDocs/)

The wiki and forum mechanics are community-maintained and can drift. Treat them
as design inputs, then verify current resource/UI behavior in the visible client.
