# Botanical Wayfinder Phase 1 Validation

Date: 2026-08-30

## Implemented Scope

Phase 1 implements a manually launched, visible-client, route-or-direction-bounded herb
gatherer. It includes exact loaded-resource selection and world-scoped
persistence; conservative safe-path planning; unknown terrain, water, broken
ridge, diagonal, and Gob-hitbox rejection; main-inventory reserve accounting
that respects masks and locked cells; single-dispatch movement; exact Gob
revalidation; Flower Menu `Pick`; compatible-basename inventory evidence; and
manual/Escape/emergency takeover.

The selector now exposes the complete reviewed GUIDE catalog even when entries
are not nearby, marks currently observed resources `LIVE`, and supports
PICK/SKIP plus Select All/Clear. The compass selects Route or N/NE/E/SE/S/SW/W/NW.
Directional modes generate a normalized, bounded 50-tile corridor using short
safe-planning checkpoints so unloaded distant terrain is never clicked blindly.

Drinking, dynamic mob/player threat rings, Wicker Pickers, consolidation,
storage returns, operator APIs, remote input, relaunch, and automatic resume are
intentionally absent.

## Offline Results

The all-catalog and direction-picker enhancement was rebuilt after the
stopped-client guard confirmed that no MoonFlower process was using the client
JAR. The packaged JAR contains the new Wayfinder controller, catalog, direction,
and window classes.

| Check | Result | Evidence |
| --- | --- | --- |
| Client stopped guard for current enhancement | PASS | `scripts/assert-client-stopped.ps1` reported no running `hafen.jar` process |
| Current enhancement compile and launchable JAR | PASS | `ant clean jar`, followed by the minimal `Utils.getprefsa` visibility fix, `ant jar`, and `ant bin` |
| Current enhancement focused checks | PASS | `ForagingChecks: PASS` at UI scales 1, 1.25, 1.5, and 2 |
| Packaged class inspection | PASS | `client/bin/hafen.jar` contains `ForagingController`, `ForagingDirection`, `ForagingHerbAtlas`, and `ForagingWindow` |
| Original Phase 1 clean compile and JAR | PASS | `ant clean jar` (963 sources, 11 existing warnings) |
| Original Phase 1 deterministic checks | PASS | `ForagingChecks: PASS` at UI scales 1, 1.25, 1.5, and 2 |
| Existing MoonFlower HUD regression check | PASS at scale 1 | `MoonFlowerHudChecks`; its unrelated proportional-inset assertion still fails at scale 1.25 |
| Diff whitespace check | PASS | `git diff --check` on scoped files |

These results do not prove server, protocol, visual, or live path behavior.

## Supervised Live Matrix

| Behavior | Result | Required evidence |
| --- | --- | --- |
| MoonFlower and classic-mode layout at supported scales | NOT RUN | Screenshots at 1280x720 and a common larger resolution |
| Exact herb cards and world-scoped restore | NOT RUN | Select, close/reopen, relog same world, verify selection |
| All-catalog scrolling and PICK/SKIP bulk controls | NOT RUN | Scroll full list, Select All, Clear, and individual toggle |
| Eight-way direction and Route picker | NOT RUN | Select every petal and center Route mode; confirm persisted selection |
| Directional corridor movement | NOT RUN | Confirm chosen heading uses short safe segments and stops at bound |
| Preflight sends no input when invalid | NOT RUN | Missing route, cursor item, low stamina, full reserve cases |
| One safe route movement segment | NOT RUN | Visible route plus client/server behavior |
| Broken ridge and deep-water refusal | NOT RUN | Low-risk test geometry and visible pause reason |
| `Pick` option semantics | NOT RUN | One disposable/common herb and Flower Menu behavior |
| Target removal plus compatible inventory acknowledgement | NOT RUN | Before/after inventory and event ledger |
| Unrelated inventory item rejection | NOT RUN | Controlled unrelated item arrival during acknowledgement |
| Manual map click and Escape takeover | NOT RUN | Movement stops/pauses and manual input wins |
| Ctrl+Shift+F12 idempotent emergency stop | NOT RUN | Repeated stop with no armed menu or continued movement |

Do not proceed to threat, water, basket, stack, or storage work until the basic
Pick transaction and takeover behavior are observed in a supervised session.
