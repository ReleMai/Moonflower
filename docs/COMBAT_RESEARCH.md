# Combat Effectiveness And Animal Health Research

## Scope And Provenance

Research was refreshed on 2026-08-23. The implementation is client-local,
read-only decision support: it does not select moves, acquire targets, pursue,
retreat, move the character, or send combat actions.

| Source | Exact ref inspected | Role |
| --- | --- | --- |
| Local MoonFlower | branch `codex/moonflower-rebrand`, commit `793dcf2cf87e3ed8cba08e01546eb37dc78a56a7` plus the working tree | Implementation target |
| [Official Seatribe client mirror](https://github.com/dolda2000/hafen-client) | `8c76cb5b3205f614dc809bc1d6d307e0f31ee4c8` | Authoritative vanilla client/protocol reference |
| [Hurricane](https://github.com/Nightdawg/Hurricane) | `c62d5aeaa9b26c07efc0515365242e3d8fcf1bee` | Current source-available comparison and MoonFlower ancestry |
| [Ender](https://github.com/EnderWiggin/hafen-client) | `dd5c08b16080b8819e982c725f2b5157d0811864` | Maintained source-available comparison |

The refs were resolved from each repository's advertised default branch on the
research date. Purus was not included in the current matrix because no current,
maintained ref could be resolved; it is historical inspiration, not current
compatibility evidence. No whole files were copied from comparison clients.

## Current Combat Feature Matrix

| Capability | Official | Hurricane | Ender | MoonFlower before this slice |
| --- | --- | --- | --- | --- |
| Server combat relations, openings, IP/OIP, actions, global cooldown, current target | Yes: `Fightview.java`, `Fightsess.java` | Yes | Yes | Yes |
| Overhead foe openings and IP/OIP | No custom overhead panel found | Yes: `GobCombatDataInfo.java` | Yes: `me/ender/gob/GobCombatInfo.java` | Yes: `GobCombatDataInfo.java` |
| Overhead maneuver/stance | Vanilla stance remains in main combat UI | Yes, including Combat Meditation meter | Yes for players | Yes |
| Cleave/defense readiness indicators | No custom per-foe indicators found | Yes, client-timed | No equivalent found in inspected overhead class | Yes |
| Estimated target agility | No | Yes, derived from observed cooldown buckets | No equivalent found | Yes: `Fightview.minAgi/maxAgi` and `Fightsess` |
| Move damage prediction | No | Yes, derived from move data, stats, weapon, and openings | No equivalent found in inspected files | Yes |
| Accumulated SHP/HHP/armor numbers | No custom accumulator | Yes: `GobDamageInfo.java` | Yes: `GobDamageInfo.java` | Yes, but formerly cached globally by Gob ID only |
| Target marker, foe highlight/circle, self range circles, target cycle/nearest engaged foe | Vanilla current-target arrow/cycle base | Expanded | Expanded target marker and alternate combat UI | Expanded and configurable |
| Color-blind opening letters and combat sounds | No | Yes | Ender uses fixed compact colored text | Yes |
| Exact animal current/max HP | No signal found | No signal found | No signal found | No signal found |

MoonFlower already has most of Hurricane's decision-support layer: configurable
top/bottom combat layout, self and opponent openings, maneuvers, IP/OIP, global
and selected move cooldowns, cleave/defense indicators, estimated agility,
damage prediction, accumulated damage by type, target highlighting and circles,
range circles, sounds, and target cycling. Recommending another copy of those
features would add clutter without new information.

Ender's source did provide useful presentation comparisons: its overhead combat
panel uses compact colored text, its alternate combat UI can be repositioned,
its global cooldown prints a remaining time, and it can clear accumulated
damage after combat. Those are client-derived presentation choices, not new
server authority.

## Ranked Improvement Shortlist

| Rank | Improvement | Signal/confidence | Value and cost | Boundary |
| --- | --- | --- | --- | --- |
| 1 | Honest animal health estimate | Observed integer SHP-damage events plus versioned community max HP; estimated | High hunting value; medium lifecycle/data risk; low clutter when combat-only | Implemented in this slice; read-only |
| 2 | One consistent readiness line for global and move cooldowns | Server global cooldown plus client-known move timers; mixed authoritative/derived | High glance value; low implementation and maintenance risk | Read-only |
| 3 | Combat keybind-conflict warnings | Local keybind registry; authoritative for this client | High accessibility and setup value; very low runtime risk | Read-only settings validation |
| 4 | Compact post-fight damage summary | The shared observed-damage tracker; derived and possibly incomplete | Good learning value; low combat clutter; low protocol risk | Read-only summary |
| 5 | Accessibility preset for openings/target/readiness | Existing local colors, letter mode, and target settings | High accessibility value; modest settings complexity | Presentation only |

Latency-aware timing was considered but not ranked: without an authoritative
round-trip/combat acknowledgement seam, adding a latency number near derived
cooldowns risks implying precision the client does not have.

## What The Client Actually Knows About Animal Health

`Fightview.Relation` carries a Gob ID, openings/buffs, IP/OIP, last actions, and
cooldown timing. It does not carry exact remaining or maximum animal HP in the
official or compared current sources.

`GobHealth` is not used as animal vitality. `OCache.OD_HEALTH` supplies one byte
that the client divides by four, and `GobHealth` turns the result into crack and
red-damage presentation. `GobHealthInfo` explicitly exposes the same value as
object durability. A matching name is not proof that the delta means animal
life; no source or supervised live trace established that meaning here.

The viable signal is `gfx/fx/floatimg`. Its current resource-side code identifies
colors `61455`, `64527`, and `36751` as SHP, HHP, and armor respectively. The
new parser accepts only the plain-integer encoding and rejects decimal, time,
float, initiative-colored, and unrelated floating messages. Armor and HHP stay
in their own totals and never reduce the animal-health estimate.

This still cannot prove that every damaging event was visible, or detect all
healing/regeneration. The result therefore remains estimated and expires after
five minutes without a qualifying SHP event. A negative/healing-like SHP event,
damage above a catalog maximum, Gob/resource replacement, removal, or manual
Clear invalidates or resets precision.

## Animal HP Evidence

The checked-in catalog is keyed by Gob resource name in
`client/src/haven/combat/AnimalHealthCatalog.java`. Its version is
`ring-of-brodgar-creatures-oldid-114037-2026-08-23`; every entry points to the
permanent [Ring of Brodgar Creatures revision](https://ringofbrodgar.com/index.php?title=Creatures&oldid=114037).
That page is player-maintained, was last edited 2024-11-30, and is not server
authority. The game world/version represented by every row is not stated, so
the feature is experimental and off by default.

The catalog preserves the table's notation:

- fixed values such as Fox `110`, Boar `450`, and Mammoth `4000` are
  `EXACT` only in the narrow sense that the community table states a fixed
  number; the UI still labels the resulting current-health calculation as
  estimated;
- `~100` is `APPROXIMATE` and never receives a precise fill;
- `1200-1800` is `RANGE` and never receives a precise fill;
- `1000+` is `LOWER_BOUND` and never receives a precise fill;
- `unknown`, `?`, absent rows, and ambiguous variants are `UNKNOWN`;
- fleeing HP is separate metadata and is never substituted for maximum HP.

The runtime performs no scrape or network call. Updating community values is a
reviewable source change.

## Display Contract

| Evidence and observation | Presentation |
| --- | --- |
| Fixed community max, no current-combat SHP event | `Max N (est.)`; no inferred percentage |
| Fixed community max plus fresh current-combat SHP events | Proportional colored fill and `~remaining / max` |
| Approximate max | `Max ~N`, or `Dmg D; max ~N`; no proportional fill |
| Ranged/context max | `Max low-high`, or `Dmg D; max low-high`; no proportional fill |
| Lower bound | `Max N+`, optionally with observed damage; no proportional fill |
| Unknown max | `Max HP unknown` or `Damage observed: D`; no denominator |
| Missed/healing-like/stale evidence | Explicit stale label; no proportional fill |
| Observed damage exceeds fixed max | Contradicted/stale-data label; no proportional fill |

Bars attach only to active `Fightview` animal relations whose exact resource is
in the catalog. Players, structures, unknown resources, and passive unrelated
Gobs receive no overlay. The renderer is positioned above the existing combat
data, caches its label texture, and disposes it with the Gob combat overlay.

## Implementation And Lifecycle

One validated event now feeds both features:

```text
gfx/fx/floatimg overlay
  -> CombatDamageEvent validation
  -> per-Glob CombatDamageTracker
     -> GobDamageInfo accumulated SHP/HHP/armor text
     -> AnimalHealthEstimator
        -> AnimalHealthBarRenderer
```

The tracker separates Gobs, binds each state to Gob ID and resource identity,
deduplicates recent overlay identities, retains lifetime display totals while
resetting the estimate baseline at combat re-entry, and clears state on Gob
removal/resource change/manual Clear. The registry holds `Glob` keys weakly, so
a closed session is not permanently rooted by static combat state.

## Verification Status

On 2026-08-23, after the visible clients closed, `ant clean deftgt` compiled 909
Java sources and 48 Panama sources and rebuilt the package successfully (16
pre-existing compiler warnings). The first resource-dependent check exposed a
truncated generated `hafen-res.jar`; it was replaced from the same official
build URL, validated against the advertised 39,681,258-byte length and as a
readable archive, and the package manifest was regenerated with `ant bin`.

The packaged MoonFlower, Cookbook, Fishing, Feasting, and Combat Assist checks
all passed. `haven.Resource find-updates` also passed. Combat Assist covers the
parser, isolation, duplicate, lifecycle, confidence, contradiction, staleness,
classifier, setting, and layout-policy rules with `haven.uiscale=1`.

All live behavior remains **NOT RUN**. Compilation and deterministic checks do
not prove live message delivery, resource identity, healing, animal variants,
or overlay placement. Those checks must be visible and user-supervised after
backing up client data.

## Recommended Next Slice

Implement a compact post-fight damage summary using the new shared tracker. It
has more learning value than adding another persistent in-combat panel, requires
no new server assumptions, and can clearly label observed SHP/HHP/armor totals
as incomplete when events may have been missed. Readiness consolidation remains
the next-best choice if immediate glanceability is preferred over review.
