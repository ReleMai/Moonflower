# Current Tasks

## Active Task: GitHub Release Hardening Phase 1

Complete items 1-3 in [RELEASE_HARDENING.md](RELEASE_HARDENING.md): validate
the exact feature commit before `main`, protect `main` with required checks,
make Gitleaks a release dependency, and prevent non-package changes from
republishing the full client.

Acceptance criteria:

1. The feature-branch validation check passes the MoonFlower release policy,
   Gitleaks, clean Ant package build, and deterministic packaged checks.
2. GitHub rejects deletion, force-push/non-fast-forward updates, and production
   commits that do not already have the required validation and Gitleaks checks.
3. Policy, workflow, or documentation-only changes validate without replacing
   `moonflower-latest` or making clients download an unchanged package.
4. The exact validated SHA advances the feature branch and `main`; remote refs,
   ruleset configuration, checks, and release manifest are audited afterward.
5. After this audit passes, item 4 becomes the active task without requiring a
   separate prompt.

## Pending Live Validation: Inventory Action Vine And Localized-Resource Timers

Validate the new attached Inventory action vine in one visible, supervised
session. The offline implementation serially selects native flower-menu actions,
temporarily equips the highest-quality supported one-handed sharp tool reachable
in the main Inventory or equipped Belt, restores the displaced hand item, and
turns a server Inspect refill duration into a session-local `CALC` countdown on
that exact Gob.

Acceptance criteria:

1. The Inventory title rail shows only Close and one engraved, non-overlapping
   Inventory Tools tab integrated into the frame art. It grows a living-vine
   hinge into an eased, clipped drawer containing Sort, Stack, Unstack, slot
   locking, optional Extended View, Butcher all, Crack all, and Stop without
   covering the grid or placing controls outside the frame.
2. Butcher all repeatedly handles the current server's Skin, Clean, and Butcher
   labels in that strict order, then optional Collect/Gather/Take Bones labels,
   one acknowledged item at a time. Crack all selects only Crack/Crack Open.
   Stop cancels safely.
3. Manual animal-processing flower choices and Butcher all select the highest
   quality supported one-handed sharp tool from main Inventory or equipped Belt.
4. Empty and occupied hand cases both restore the exact prior hand/tool state.
   Occupied cursor, loading identity/quality, lost equipment, and timeouts stop
   with a visible reason instead of guessing.
5. Inspecting a depleted localized resource such as Jotun Mussel, Salt Basin,
   Tarpit, or Wellspring preserves the server's duration as a ticking `CALC`
   world label and hover detail. Unrelated or unparsed Inspect text creates no
   timer, and reaching zero says `due now` rather than claiming live availability.
6. The guarded build and `InventoryQolChecks` pass. Exact flower labels,
   equipment acknowledgements, Inspect wording, timer placement, and restoration
   remain unverified until observed in the visible client.

## Active Task: MoonFlower Clock And Calendar First Slice

Validate the new top-center MoonFlower World Clock in one visible, supervised
client session. The offline implementation replaces the small clock only while
MoonFlower HUD mode is active, retains the original `Cal` path in classic mode,
and separates live astronomy/location, derived calendar state, sourced guidance,
and themed presentation.

Acceptance criteria:

1. MoonFlower mode shows one top-center ink, teal, gold, ivory, vine, and blossom
   clock matching the portrait UI; the legacy time/status columns are not
   duplicated beside it.
2. Classic mode immediately restores the original clock, hit shape, tooltip,
   time/season/moon text, province/realm, population, and ping presentation.
3. The animated sky tracks game time and visibly distinguishes dawn, daylight,
   sunset, and night while retaining Haven's native sun and moon resources.
4. Game time/date, moon phase, season day, and game-time countdown roll over
   correctly without claiming a falsely exact real-time conversion.
5. Terrain, province, and realm update after travel and map transitions; unknown
   values are omitted or shown as unavailable rather than remaining stale.
6. Dawn, Fish Moon, and Moonmoth notices are visibly labeled `GUIDE`; live data
   is labeled `LIVE`, and no notice claims lore completion or server authority.
7. `Reduce decorative clock motion` freezes decorative sun frames while live
   time, sky state, and text continue updating.
8. The face and tooltip remain readable at supported UI scales and common
   resolutions without covering upper-left buffs or upper-right controls.
9. The guarded build, `WorldClockChecks` at 1/1.25/1.5/2 scales, MoonFlower HUD
   checks, branding checks, and diff checks pass. Live layout remains unverified
   until observed in the visible client.

## Pending Live Validation: Combat Assist And Animal Health-Bar Validation

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

## Pending Live Validation: MoonFlower In-Game HUD

Validate the optional MoonFlower Hearthwheel HUD in one visible,
user-supervised Haven session. The packaged implementation moves the visual
portrait and vitality presentation into a movable bottom-center dock, mirrors
native window controls with a six-socket MoonFlower ornament, and opens all
client-only tools from one expandable, data-driven feature vine. Prefiltered
custom artwork, interactive ring/ribbon vitality styles, a movement-speed
badge, and circular native buffs around the portrait share one teal, ivory, and
gold language. Four effects remain immediately visible; overflow effects animate
out from a `+N` bud. The Equipment portrait button rolls the configured native
hand, pouch, belt, backpack, shoulder, and cape quick slots out on their own vine.
The action bars, quick slots, lower-right controls, map frame, and chat panel
now use that same theme. The six action bars retain scalable horizontal, 2x5,
and vertical layouts, gain edit-mode drag surfaces with persistent per-bar
locks, while Chat provides live text-size, background-opacity, timestamp,
preview, keyword-alert, active-channel-alert, and notification-volume controls.
Generated MoonFlower frame, midnight-panel, and chat-settings artwork now skins
standard windows, inventory, chat, map, and the lower-right menu through an
adaptive nine-slice renderer. A title-bar flower-gear opens Chat Settings
directly, and buffs occupy a separate vine cradle below the portrait rings.
MoonFlower window chrome replaces rather than overlays Haven's native frame;
interior views use borderless texture surfaces so chat, inventory, and the map
do not show doubled edges. Combat now uses a themed status rail, action deck,
and opponent cards while retaining native actions, cooldowns, openings,
initiative, targeting controls, and last-action data.
The generated panel texture fills the complete decorated window so title and
margin regions cannot expose the game world. Outside combat, HUD edit mode
shows independently draggable Combat Status and Combat Deck ghosts; their
saved anchors drive the live combat presentation. The portrait clamp uses its
painted lower bound rather than unused rollout space, allowing the ornament to
sit flush with the bottom edge. The panel surface is clipped inside the floral
frame's transparent corners, Chat delegates opacity to one window-owned
background, the lower-right action menu uses a connected vine dock with themed
slots, and portrait vitals use continuous illuminated arcs with embedded number
badges instead of stacked text over the portrait.

Acceptance criteria:

1. The first in-game startup shows one centered choice between the MoonFlower
   and classic HUDs. Closing the choice keeps the classic HUD, the decision is
   remembered, and either layout remains selectable later in Options.
2. Enabling the MoonFlower HUD hides only the classic portrait, meters, and
   main button strip. Disabling it immediately restores all three without a
   relog or lost action assignments. Percentage-bearing vitality labels render
   without formatter exceptions or UI-thread termination.
3. A never-positioned hub defaults to bottom-center, remains on-screen after
   resize/UI-scale changes, saves a left- or middle-dragged per-character
   position in edit mode, and returns to its responsive default after Reset
   Position. Existing saved positions remain respected after the artwork update.
4. Inventory, Equipment, Character, Kin, Options, and one MoonFlower Features
   button use centers detected from the six painted socket interiors rather
   than hand-entered offsets. The Features button animates a vine containing
   Cookbook, Fishing Journal, Fishing Helper, and Wiki; every
   entry retains its action, tooltip, shortcut, and visible-window state. Adding
   another client feature requires only another vine entry, not new portrait
   geometry or artwork.
5. Ring and ribbon styles show the same live health, stamina, and energy data
   as the native meters. Soft health remains visually distinct from the darker
   hard-health ceiling. Hovering each ring/ribbon identifies that vital; health
   includes SHP, HHP ceiling, MHP, recoverable damage, wound damage, and total
   missing health without overlapping the Options action.
6. Movement shows the same live player speed used by the native avatar readout.
   Native buff widgets become circular and arc around the portrait, retain
   their clicks and short/long tooltips, animate all overflow from a `+N` bud,
   and return to their classic position, square presentation, and size when the
   MoonFlower HUD is disabled. The configured equipment quick slots similarly
   animate from the Equipment socket and return to their saved classic position.
7. All six action bars preserve slot identities, actions, keybinds, tooltips,
   removal safeguards, dragging, and item/action drops at 100-160% size in
   horizontal, 2x5, and vertical layouts. Action slots, quick slots, the
   lower-right control cluster, compact map, and chat frame use the shared
   MoonFlower panel treatment without changing their hit areas.
8. Chat font and timestamp changes reflow existing messages without losing
   channel selection or scroll position. Opacity, alert volume, notification
   preview duration/count, keyword highlighting, and active-channel alert
   suppression apply immediately, while existing per-channel sound toggles
   remain authoritative.
9. The HUD remains readable and clickable at 1280x720, 1920x1080, 2560x1440,
   ultrawide, and supported UI scales without covering critical combat UI.
10. Rings are visibly thicker than the prior HUD. The Options checkbox can
   show or hide health-current/max, stamina-percent, and energy-percent values
   on their respective ring or ribbon instead of over the portrait, without
   changing hover details.
11. The portrait equipment rollout mirrors the real Equipment window for every
   opening path: it is closed while the full Equipment window is open and rolls
   back out when that window closes. It does not retain an independent state.
12. Buff artwork is circularly masked rather than merely placed in a circular
   frame. Inventory windows use the shared ink, teal, gold, blossom, and vine
   treatment while retaining native item interactions, grid geometry, sorting,
   stacking, extended view, and persistent slot locks. The lock-mode control is
   scaled, visually distinct, and laid out on the title bar from actual button
   widths. Edit mode permits left-dragging each action bar and provides
   persistent per-bar plus lock-all/unlock-all controls.
13. Inventory, Chat, Map, standard menu windows, and the lower-right menu use
   project-local generated raster assets without stretching corner artwork or
   covering native controls. The chat title-bar settings emblem opens the same
   Chat Settings panel available through Options -> Advanced Settings.
14. The four primary buffs remain visibly attached to the portrait by a lower
   vine cradle but do not touch the health, stamina, or energy rings. Expanded
   overflow buffs remain below the portrait and on-screen at supported scales.
15. Standard windows show exactly one outer frame in either classic or
   MoonFlower mode. The combat deck keeps selected and backup actions distinct,
   exposes action name, hotkey, state, and remaining cooldown on hover, and does
   not obscure existing opening, initiative, health, stamina, or last-action
   information.
16. In HUD edit mode and outside combat, separate descriptive ghost panels can
   reposition the status rail and combat deck without sending combat messages.
   Their positions persist, remain clamped on-screen, can be reset in Options,
   and are used by the real combat UI on the next fight. The portrait can be
   dragged to its actual painted bottom edge without hiding its buff cradle.
17. The shared panel texture does not show square pixels outside transparent
   floral frame corners. Chat displays exactly one opacity-controlled surface,
   the lower-right action grid and control rail read as one attached botanical
   dock, and health, stamina, and energy use fluid glowing tracks whose optional
   values sit on their respective tracks without covering the avatar.

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
java '-Dhaven.uiscale=1' -cp "bin/*" haven.MoonFlowerHudChecks
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
