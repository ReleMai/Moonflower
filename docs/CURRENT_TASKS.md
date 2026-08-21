# Current Tasks

## Active Task: Fishing Helper Live Validation

Validate the new fishing helper in one visible, user-supervised Haven session.
The implementation prepares a selected pole deterministically, finds nearby
water, casts, records candidate catches locally, exposes a read-only journal,
and projects local observations as clickable fish-icon map spots.

Why this task matters:

- Compilation and offline persistence checks cannot prove the current server's
  equipment messages, fishing pose, choice-window layout, or inventory timing.
- Candidate catch capture deliberately favors safety over certainty until a
  stable, authoritative catch event is found in the live client protocol.

Acceptance criteria:

1. Starting with a selected pole, line, hook, and bait or lure in inventory,
   an equipped Creel, or a pole in the equipped belt, using the normal Fishing action opens the helper,
   equips and assembles the combination, and does not lose held items.
2. An already equipped pole is reused immediately. Otherwise an empty hand is
   preferred, buckets are never displaced, and any other displaced hand item is
   moved only when inventory space is available and restored when the helper stops.
3. A water tile within three tiles and 33 coordinate units is selected; otherwise
   the helper stops with a clear status instead of walking or casting blindly.
4. Bait mode casts once and waits for the result; lure mode selects the displayed
   fish choice with the highest final percentage.
5. New fish appearing in the main inventory or an equipped Creel during a
   fishing attempt are saved to
   `%APPDATA%\Haven and Hearth\Hurricane\fishing.db`; unrelated new items are
   rejected by the fish classifier.
6. Each saved observation includes world, segment/grid/offset, cast and player
   coordinates, water, fish, pole/line/hook/bait-or-lure names/resources/qualities,
   real and game time, day/night, moon phase, season, Survival, and Will.
7. The Fishing Journal HUD button opens the read-only journal without requiring
   the helper window; the map shows grouped fish-icon spots derived from the
   journal, and clicking one opens the recent catches for that tile. The journal
   groups catches by fish resource, then shows the selected fish's catches by
   date and time. Full catch and quality-factor details remain opt-in.
8. Stop, low energy, critical vitals, combat, missing inventory room, unreadable
   equipment, or a lost cursor all fail closed with a visible reason.
9. Pole, line, hook, bait, and lure controls list only currently reachable items;
   highlighted entries are included and can be clicked to exclude them. Stack
   containers are never offered as tackle choices, while their actual contents
   remain selectable.
10. Signed negative map segment and grid IDs are treated as valid Haven IDs;
    only the explicit `-1` observation sentinel is considered unknown.
11. When line, hook, bait, or lure is lost, replacement tackle is taken and
    right-clicked onto the current equipped-pole widget. The helper re-resolves
    and retries that interaction during bounded server/widget refresh races.
12. A selected pole is placed through the equipment window's actual hand-slot
    drop path. Allowed slots come from the item's slot metadata; placement is
    retried while the rod remains held and accepts a refreshed equipped widget.
13. Preparation is tackle-first: inspect the pole wherever it rests, attach any
    missing selected line, hook, and bait/lure by taking that part and
    right-clicking the stationary pole, then equip the fully prepared pole.

## Scope Boundaries

- Keep the helper inside the visible Java client; do not add login, travel,
  inventory dumping, or remote unattended-account behavior.
- Fishing spots remain local, derived, display-only overlays. Do not save them
  into the normal map-marker index or upload them to mapping services.
- Treat saved catches as `candidate` observations until live evidence exposes an
  authoritative caught-fish event. Do not promote confidence from timing alone.
- Preserve the existing Cookbook changes and its local `cookbook.db` data.
- Do not inspect, log, or store account credentials or session material.

## Verification Commands

The visible client must be closed before running these commands. The Ant build
now stops with a clear error if `hafen.jar` is running, because replacing the
packaged JAR underneath a live JVM can terminate its UI thread.

```powershell
Push-Location client
ant clean deftgt
java -cp "bin/hafen.jar;bin/sqlite-jdbc-3.42.0.0.jar" haven.cookbook.CookbookChecks
java -cp "bin/hafen.jar;bin/sqlite-jdbc-3.42.0.0.jar" haven.fishing.FishingChecks
java -cp bin/hafen.jar haven.Resource find-updates
Pop-Location
```

Before the live session, stop the client and run:

```powershell
.\scripts\backup-client-data.ps1
```

## Next Slice After Completion

If the live session reveals a stable protocol or widget event for a successful
catch, add a focused adapter and promote only those records to authoritative.
Do not infer catch provenance solely from timing.
