# Current Tasks

## Active Task: Combat Assist And Animal Health-Bar Validation

Validate the new experimental animal health presentation after the visible
clients can be closed and a fresh package can be built. The implementation uses
one validated floating-damage event path for existing accumulated damage and a
per-session animal estimator. The Combat Settings option is off by default.

Acceptance criteria:

1. The clean client package and MoonFlower, Cookbook, Fishing, Feasting, and
   Combat Assist checks pass with no visible client running.
2. Enabling Estimated Animal Health Bars affects only cataloged animal combat
   relations; players, structures, unknown resources, and unrelated passive
   Gobs never receive a bar.
3. A fixed community maximum shows no precise percentage before the first
   current-combat SHP event. After multiple observed hits it shows a labeled
   estimate matching accumulated SHP damage.
4. Approximate, ranged, lower-bound, unknown, stale, and contradicted values
   remain denominator-free and visibly uncertain.
5. Two animals of one species retain independent state. Combat re-entry,
   resource/corpse transition, Gob removal, Clear, logout, and target switching
   do not leak stale damage or textures.
6. At supported UI scales, the bar remains readable above openings, maneuver,
   IP/OIP, cooldowns, damage text, and target markers.
7. Live damage-color/type meaning, missed observations, healing/regeneration,
   and `OD_HEALTH` behavior are recorded as PASS, FAIL, or NOT RUN without
   treating community values as server authority.

## Pending Live Validation: Feasting Helper

Validate the new Table-integrated Feasting Helper in one visible,
user-supervised Haven session. The implementation reads foods from the open
Table and main inventory, shows the selected attribute's current character-sheet
share separately from its projected share, provides lowest/highest-attribute
balance guidance, and can perform one acknowledged Table feast interaction at a
time. An inventory-only toggle excludes Table food from both planning and fast
auto-eating.

Why this task matters:

- Compilation and deterministic planner checks cannot prove the current
  server's Table button caption, feast-cursor timing, stack updates, FEP reset
  semantics, hunger updates, or live window layout.
- The displayed chance is deliberately labeled as a projection until a visible
  session confirms that the selected projected FEP divided by projected total
  FEP matches the current server's next-trigger behavior.
- Breakage-ignoring mode can consume real symbel/tableware durability and must
  not be tested without the user's explicit approval for the named disposable
  item.

Acceptance criteria:

1. Opening a valid Table shows one attached Feasting Helper frame below the
   existing controls. It slides open/closed, repacks the native Table window,
   and does not cover the Table inventory, server controls, or tableware option.
2. The attribute selector uses the nine native food-event attributes. Balanced
   target deterministically selects a currently lowest base attribute, while an
   explicitly selected target remains under player control.
3. Foods currently on the Table and in the main inventory appear once with
   their live source, target FEP, total FEP, hunger, quality/energy details, and
   projected target share. Closed containers and historical Cookbook-only foods
   do not appear. Inventory-only mode removes Table food from both the displayed
   plan and helper-dispatched bites without silently falling back to the Table.
4. The frame shows current FEP progress/cap and the selected attribute's current
   chance directly from the character-sheet meter, separately labels chance after
   each planned bite, and shows current lowest/highest values, the selected
   attribute's lead, and a warning before raising a uniquely highest attribute.
5. Changing Table food, main-inventory food, tooltip data, the FEP meter,
   character attributes, satiations, hunger efficiency, or Table modifiers
   refreshes the plan without stale widget actions.
6. Start activates the Table's normal Feast action, eats one revalidated item,
   waits for an item/FEP/hunger acknowledgement, then recalculates before the
   next bite. No overlapping take messages are sent.
7. Stop, Escape, combat, a held cursor item, Table closure, an unavailable feast
   action, a changed/missing planned item, unreadable safe-mode durability, or
   acknowledgement timeout stops with a visible reason.
8. Safe mode refuses at-risk symbel/tableware. Manual feasting continues to
   obey the existing global protection setting.
9. Fast breakage mode begins unchecked, requires a second explicit confirmation
   for that run, applies only around helper-dispatched bites, and resets after
   every completion, stop, exception, or Table closure.
10. Breakage mode is tested only after the user explicitly approves risking the
    named disposable symbel/tableware. Otherwise record that check as NOT RUN.

## Pending Live Validation: Fishing Helper

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
   an equipped Creel, or a pole in the equipped belt, the normal Fishing action
   opens and refreshes the helper without moving equipment. The separate Prepare
   button assembles and equips the combination without losing held items.
2. An already equipped pole is reused immediately. Otherwise an empty hand is
   preferred, buckets are never displaced, and any other displaced hand item is
   moved only when inventory space is available and restored when the helper stops.
3. A water tile within three tiles and 33 coordinate units is selected; otherwise
   the helper stops with a clear status instead of walking or casting blindly.
4. Bait mode casts once and waits for the result; lure mode selects the displayed
   fish choice with the highest final percentage.
5. New fish appearing in the main inventory or an equipped Creel during a
   fishing attempt are saved to
   `%APPDATA%\Haven and Hearth\MoonFlower\fishing.db`; unrelated new items are
   rejected by the fish classifier.
6. Each saved observation includes world, segment/grid/offset, cast and player
   coordinates, water, fish, pole/line/hook/bait-or-lure names/resources/qualities,
   real and game time, day/night, moon phase, season, Survival, and Will.
7. The Fishing Journal HUD button opens the read-only journal without requiring
   the helper window; the map shows grouped fish-icon spots derived from the
   journal, clicking one opens the recent catches for that tile, and the map
   sidebar can hide the fishing overlays without deleting journal data. The
   journal groups catches by fish resource, then shows the selected fish's
   catches by date and time. Full catch and quality-factor details remain opt-in.
8. Stop, low energy, critical vitals, combat, missing inventory room, unreadable
   equipment, or a lost cursor all fail closed with a visible reason.
9. Pole, line, hook, bait, and lure controls list only currently reachable items;
   highlighted entries are included and can be clicked to exclude them. Stack
   containers are never offered as tackle choices, while their actual contents
   remain selectable.
10. Signed negative map segment and grid IDs are treated as valid Haven IDs;
    only the explicit `-1` observation sentinel is considered unknown.
11. When line, hook, bait, or lure is missing, the incomplete pole remains in a
    stable inventory or equipped-belt container. Each selected component is put
    on the cursor and right-clicked onto that exact pole identity once, matching
    the supervised manual interaction; the next phase starts only after the
    pole contents acknowledge the new component.
12. Only a fully assembled and re-inspected pole is moved to a hand. Placement
    sends the same transfer as Shift+left-click so the server chooses the valid
    hand slot, preserves buckets, and succeeds only after the exact pole appears
    in either hand without entering a stuck cursor state.
13. Preparation is a fail-closed transaction: normalize the cursor, resolve one
    pole identity, station and assemble it when incomplete, equip it when ready,
    and verify that line, hook, and bait/lure survived the equipment move. A pole
    remains in the selector while briefly cursor-held, and no repeated drop or
    item-interaction messages are sent while state is ambiguous.

## Scope Boundaries

- Keep animal health bars read-only, combat-relation-only, experimental, and
  disabled by default until supervised live evidence is recorded.
- Do not treat `OD_HEALTH` object durability as animal vitality without a
  current source trace or supervised observation proving that meaning.
- Never turn approximate, ranged, lower-bound, unknown, stale, or contradicted
  animal data into an exact percentage.
- Keep the helper inside the visible Java client; do not add login, travel,
  inventory dumping, or remote unattended-account behavior.
- Keep the Feasting Helper attached to the Table window. It may inspect only the
  open Table and main inventory and must never acquire, move, craft, or search
  for food.
- Treat Feasting chance values as projections until the live FEP trigger/reset
  behavior is observed. Do not infer server authority from offline math.
- Never exercise symbel-breakage mode against live tableware without the user's
  explicit approval for that supervised run.
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
java -cp "bin/*" haven.MoonFlowerChecks
java '-Dhaven.uiscale=1' -cp "bin/*" haven.cookbook.CookbookChecks
java '-Dhaven.uiscale=1' -cp "bin/*" haven.fishing.FishingChecks
java '-Dhaven.uiscale=1' -cp "bin/*" haven.feasting.FeastingChecks
java '-Dhaven.uiscale=1' -cp "bin/*" haven.combat.CombatAssistChecks
java -cp "bin/*" haven.Resource find-updates
Pop-Location
```

Before the live session, stop the client and run:

```powershell
.\scripts\backup-client-data.ps1
```

## Next Slice After Completion

First complete the clean packaged regression gate and supervised animal-health
checks above. If the event and overlay evidence holds, keep the feature labeled
estimated and implement a compact post-fight observed-damage summary as the
next combat slice. Feasting and Fishing Helper live validation remain separate
pending sessions with their existing safety boundaries.
