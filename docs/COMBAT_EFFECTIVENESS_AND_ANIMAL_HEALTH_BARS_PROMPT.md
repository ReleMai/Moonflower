# Combat Effectiveness And Animal Health Bars Prompt

Use this prompt after the current Feasting Helper live-validation task is
complete, or after the user explicitly authorizes replacing that active task.

```text
Research the current MoonFlower combat experience and current source-available
Haven & Hearth clients, identify practical client-side improvements that would
help the player make better combat decisions, and implement one bounded first
slice: honest overhead health bars for animals when the client has enough
evidence to display them.

This is a decision-support and presentation task, not a combat bot. Do not add
automatic move selection, rotations, chained attacks, auto-target acquisition,
automatic pursuit, automatic retreat, autonomous movement/distancing, packet
manipulation, or unattended combat. Existing automation is outside this
feature's scope and must not be expanded. Preserve server authority over damage,
health, movement, targeting, and action execution.

## Working Rules

Before editing:

1. Read `AGENTS.md`, `docs/CURRENT_TASKS.md`, `docs/ARCHITECTURE.md`,
   `docs/CODING_STANDARDS.md`, `docs/FILE_ORGANIZATION.md`,
   `docs/TECHNICAL_DEBT.md`, and `docs/VERIFICATION.md`.
2. Inspect `git status` and preserve all user work. The current checkout has
   in-progress Feasting Helper changes in shared client seams. Do not overwrite,
   reformat, stage, or fold those changes into this task. Do not replace the
   current live-validation task unless the user explicitly approves the switch.
3. Record the exact local branch and commit, exact current official Seatribe
   client ref, and exact refs/dates for every comparison client. Seatribe is
   authoritative for the vanilla client and protocol. Refresh refs immediately
   before compatibility claims; do not rely on old prompt text or release names.
4. Give the user a concise plan tied to the files actually found. Separate:
   research and recommendations, the animal-health-bar implementation, offline
   verification, and supervised live verification.
5. Keep the implementation client-local. Do not change the Spring server,
   dashboard, gateway, botcontrol API, databases, account data, login flow, map
   caches, or protocol.

## Phase 1: Current Combat Feature Audit

Inspect the live MoonFlower code before proposing changes. At minimum review:

- `client/src/haven/Fightview.java`
- `client/src/haven/Fightsess.java`
- `client/src/haven/GobCombatDataInfo.java`
- `client/src/haven/GobDamageInfo.java`
- `client/src/haven/GobHealth.java` and `GobHealthInfo.java`
- `client/src/haven/GobInfo.java`
- the Gob overlay and floating-damage paths in `client/src/haven/Gob.java`
- `client/src/haven/OCache.java` and the current `OD_HEALTH` delta path
- combat settings and keybinds in `client/src/haven/OptWnd.java` and
  `client/src/haven/GameUI.java`
- target highlighting/range sprites under `client/src/haven/sprites/`
- `client/src/haven/automated/CombatDistanceTool.java` and
  `CombatDistancerLite.java`, for awareness only; do not extend their automation

Build a concise feature matrix for MoonFlower, the current official client, the
current Hurricane client, and any other genuinely maintained, source-available
client whose combat code can be inspected. Archived or stale clients may be
listed as historical inspiration but must be labeled as such. Compare source,
not screenshots, hearsay, marketing lists, or old changelogs alone.

For each candidate feature, record:

- what it shows or changes;
- the exact source file/ref that proves it exists;
- whether MoonFlower already has it, partially has it, or lacks it;
- what server/client signal it uses;
- whether its result is authoritative, derived, estimated, or speculative;
- combat value, visual-clutter cost, accessibility value, implementation risk,
  maintenance risk, and licensing/provenance constraints;
- whether it remains read-only decision support or crosses into automation.

Explicitly inventory MoonFlower's existing combat support before recommending
duplicates. The current checkout appears to include opponent and self openings,
maneuvers, IP/OIP, attack and move cooldown displays, estimated agility, combat
hotkeys, damage prediction, accumulated damage, target highlighting/circles,
range circles, target cycling/nearest engaged target, sounds, and configurable
combat layout. Verify every item against the live checkout rather than assuming
this list is still accurate.

Rank no more than five worthwhile improvements. Prefer improvements such as
clearer target/state presentation, better cooldown/readiness legibility,
color-blind-safe openings, keybind-conflict visibility, latency-aware but honest
timing, and compact post-fight feedback. Do not implement these broader ideas in
this slice. End the research report with one recommended next slice and explain
why it is more valuable than the other candidates.

## Phase 2: Animal Health Feasibility And Data Research

First determine what health evidence the client truly receives. Do not treat
similarly named data as equivalent:

- `GobHealth` currently decodes `OD_HEALTH` as a coarse fraction and is used for
  object durability/crack presentation. Do not call it animal vitality unless a
  current source trace or supervised live observation proves that an animal Gob
  receives the delta with that meaning.
- `Fightview.Relation` exposes combat state such as Gob ID, openings, maneuver,
  IP/OIP, last actions, and cooldown timing, but it does not currently expose an
  animal's exact remaining hit points. Confirm this against current upstream.
- `Gob.processDmg` observes `gfx/fx/floatimg` messages and `GobDamageInfo`
  accumulates selected damage-number types per Gob. This is the likely basis for
  a derived animal-health estimate, but validate the message fields and color/type
  meanings before subtracting anything from HP.

Research current animal maximum HP and fleeing thresholds using current sources.
The Ring of Brodgar `Creatures` table is a useful community-maintained starting
point, not server authority. Cross-check individual creature pages, current
forum findings when useful, and source-visible resource names. Record the source
URL, retrieval date, world/game version when known, exact/approximate/range/
unknown status, and contradictions. Never silently convert `~850`, `1000+`, a
range, a mine-level-dependent value, or unknown into an exact maximum. Fleeing HP
is a behavior threshold, not maximum HP.

Create a small checked-in, versioned animal-health catalog keyed by stable Gob
resource names, not localized display names. Each entry must be able to express:

- exact fixed max HP;
- approximate max HP;
- min/max range or context-dependent HP;
- unknown max HP;
- optional fleeing threshold with the same confidence metadata;
- citation/source note and `as of` date.

Do not scrape a wiki or call an external service at runtime. Do not create a
database for this feature. Keep community data replaceable and separate from
rendering and combat-event state.

## Required Animal Health-Bar Behavior

Implement the first slice for visible animal combat relations only. Do not show
bars over players, arbitrary structures, or every passive creature on screen.
Use stable resource identity and a deliberate animal catalog/classifier; do not
assume every non-player Gob is a damageable animal.

Add a clearly named Combat Settings option such as **Estimated animal health
bars**, defaulting to off until supervised validation. Its tooltip must explain
that most values are inferred from observed damage and community max-HP data,
and may be wrong after healing, regeneration, missed off-screen damage, data
changes, variants, or incomplete observation.

For each eligible animal:

1. Prefer a current, explicit server-provided vitality signal only if its meaning
   is proven. Label it `exact` only if it actually carries exact current/max HP;
   a quarter-step durability fraction is not exact numeric vitality.
2. Otherwise, when the catalog has a fixed maximum and the client has a valid
   observed-damage total for this Gob lifecycle, show an estimated bar and text
   such as `~330 / 450`. Visually distinguish estimated data without making the
   overlay noisy.
3. When maximum HP is approximate or a range, either show an honest interval/
   approximate presentation or omit the filled percentage. Never present a
   precise percentage derived from an imprecise maximum.
4. When maximum HP is unknown, show only useful proven information such as
   `Damage observed: 120`, or no bar. Do not invent a denominator.
5. Before the first qualifying damage event, a known catalog maximum may be
   shown only as `Max ~450` or a full estimated bar whose uncertainty is obvious.
   Do not imply that the client has observed the animal at full health.
6. Clamp rendering safely for overkill, but treat observed damage greater than
   the catalog maximum as stale/contradictory data. Downgrade the display to
   unknown or contradicted rather than silently showing a trustworthy `0%`.

The bar should be compact, native-looking, readable at supported UI scales, and
positioned with existing Gob overlays so it does not collide with accumulated
damage, openings, maneuver/IP data, names, or the target marker. Current target
clarity matters more than decorative animation. Dispose textures and overlay
state correctly; do not allocate new text/images every frame.

## Damage State And Lifecycle Correctness

Do not parse the same floating message independently in two features. Refactor
the smallest safe seam so accumulated damage text and estimated health consume
one validated, immutable damage snapshot or event model.

The model must:

- keep separate state for separate Gob IDs;
- bind state to both Gob ID and resource/lifecycle identity so ID reuse or Gob
  replacement cannot inherit an old animal's damage;
- distinguish HP damage from armor damage, HHP/wound numbers, initiative, heals,
  and unrelated floating numbers using proven current semantics;
- count damage from all visible combatants only when those events are actually
  delivered for that Gob;
- handle duplicate overlay delivery without double-counting if duplication is
  possible;
- reset or invalidate on death, corpse/resource transition, Gob removal,
  session teardown, explicit clear, contradictory data, and any lifecycle event
  that makes the estimate unsafe;
- define behavior when combat ends and later restarts against the same Gob;
- account honestly for unobserved damage, healing, and regeneration. If those
  cannot be detected, expire or visibly downgrade the estimate instead of
  preserving false precision indefinitely;
- tolerate `Loading`, missing player/map/UI, and rapid target changes without
  exceptions or stale overlays.

Keep `GobDamageInfo`'s existing user-facing behavior and Clear action working.
If its current static cache keyed only by Gob ID is unsafe, repair that as part
of the shared state seam and cover the behavior with checks.

## Suggested Structure

Adapt names to the live conventions, but keep data, state, and rendering
separate. A reasonable shape is:

```text
client/src/haven/combat/
  AnimalHealthCatalog.java       versioned resource-to-HP evidence
  CombatDamageSnapshot.java      immutable observed damage by type
  CombatDamageTracker.java       per-session Gob lifecycle state
  AnimalHealthEstimate.java      exact/estimated/range/unknown result
  AnimalHealthEstimator.java     pure calculation and confidence rules
  AnimalHealthBarInfo.java       overhead Gob presentation
  CombatAssistChecks.java        deterministic offline checks
```

Use fewer files if the live architecture supports a simpler focused design, but
do not put the catalog, message parser, lifecycle state, estimation rules,
rendering, settings UI, and tests into one large class. Keep `Gob.java`,
`Fightview.java`, `Fightsess.java`, and `OptWnd.java` as small integration seams
where possible.

## Verification

Add deterministic offline checks for at least:

- exact fixed max HP minus validated observed HP damage;
- approximate, ranged, context-dependent, contradicted, and unknown catalog data;
- armor/HHP/initiative/unrelated numbers not reducing animal HP unless current
  protocol evidence proves otherwise;
- multiple hits, duplicate-event protection, overkill, and integer boundaries;
- two animals of one species retaining separate state;
- Gob removal, death/corpse transition, combat exit/re-entry, resource change,
  ID reuse, explicit Clear, and session teardown;
- healing/regeneration or missed-observation downgrade behavior;
- `Loading`, missing resources, and rapid current-target changes;
- players and non-animal Gobs never receiving animal bars;
- option disabled, current target, and other animal combat foes;
- layout offsets at supported UI scales and coexistence with combat data/damage;
- texture/cache invalidation and disposal after repeated updates.

With the visible client closed, run the repository's current Java gate. At
minimum:

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

If package names or the canonical gate have changed, use the live repository's
commands and report the exact commands and exit codes. Compilation and offline
math do not prove live damage-event semantics, animal identity, regeneration,
or overlay placement.

Before a visible live test, stop all Haven clients and run:

```powershell
.\scripts\backup-client-data.ps1
```

Then perform a visible, user-supervised session using an animal the user agrees
is safe to fight. Do not initiate combat, risk equipment, or control the
character without the user's direct action. Observe and record:

1. Whether that animal ever receives `OD_HEALTH`, and what its values correlate
   with; do not log raw session/authentication material.
2. The floating-message fields/colors for HP damage, armor damage, wounds,
   initiative, healing, misses, and other visible events that can be safely
   observed.
3. One known fixed-HP animal taking multiple hits, with the estimated bar and
   accumulated damage agreeing after every observed event.
4. A second animal of the same species proving state isolation.
5. Target switching and multiple combat relations proving stable placement and
   no overlap with openings, IP/OIP, maneuver, cooldown, and damage text.
6. Combat end, animal fleeing/knockout/death, Gob removal, and Clear behavior.
7. UI scaling, option toggling, and no lingering textures/overlays after logout.

Record each live item as **PASS**, **FAIL**, or **NOT RUN** in
`docs/VERIFICATION.md`. Keep bars labeled estimated unless live evidence proves
a stronger status. If a safe live test cannot establish message semantics, ship
only the research/report and offline estimator scaffolding, or keep the display
behind an experimental default-off option; do not guess.

## Documentation Deliverables

Add a focused combat research document containing:

- the source/ref/date feature matrix;
- MoonFlower's existing combat feature inventory;
- the ranked improvement shortlist and one recommended next slice;
- the animal HP catalog sources and confidence rules;
- a plain-language explanation of why `GobHealth` durability is not assumed to
  be animal vitality;
- the exact/estimated/range/unknown display contract;
- offline and supervised verification results.

Update `docs/CURRENT_TASKS.md` only if this task was explicitly made active.
Preserve pending Feasting and Fishing live-validation truth. Record shortcuts or
known stale data in `docs/TECHNICAL_DEBT.md`; do not bury them in code comments.

## Completion Criteria

The slice is complete when:

1. Current MoonFlower, official-client, Hurricane, and other maintained-client
   combat features are compared using recorded source refs and dates.
2. Existing MoonFlower features are not duplicated, and no combat automation is
   added or expanded.
3. Animal max-HP evidence is versioned, resource-keyed, cited, and preserves
   exact/approximate/range/unknown distinctions.
4. One validated damage-event path feeds both accumulated damage and health
   estimation without double parsing or cross-Gob leakage.
5. Eligible animal combat relations receive compact overhead information that
   is exact only when proven, estimated when derived, and denominator-free when
   max HP is unknown.
6. Players, structures, passive unrelated Gobs, stale lifecycles, and unknown
   variants never receive fabricated health percentages.
7. Settings, UI scale, overlay coexistence, lifecycle cleanup, and texture
   disposal are covered by deterministic checks.
8. Existing MoonFlower, Cookbook, Fishing, and Feasting checks still pass.
9. Documentation distinguishes source research, offline verification, and live
   supervised evidence, with untested behavior clearly marked.

After implementation, explain in beginner-friendly language: files changed,
what each file owns, how a damage message becomes an estimate, why some animals
show an approximate bar while others show only observed damage, how stale state
is prevented, what was verified, what remains uncertain, and the one recommended
next combat-effectiveness slice.
```

## Research Starting Points

- [Official Seatribe client mirror](https://github.com/dolda2000/hafen-client)
- [Hurricane client](https://github.com/Nightdawg/Hurricane)
- [MoonFlower repository](https://github.com/ReleMai/Moonflower)
- [Ring of Brodgar: Creatures](https://ringofbrodgar.com/wiki/creatures)
- [Haven & Hearth client-source policy and architecture](https://legacy.havenandhearth.com/portal/doc-src)

The creature wiki and forum reports are community-maintained and can drift.
Treat them as evidence with confidence metadata, not as live server authority.
Compare only source-visible client behavior, respect each source's license, and
reimplement ideas cleanly rather than copying files wholesale.
