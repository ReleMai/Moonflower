# Botanical Wayfinder Phase 2 Validation

Date: 2026-09-01

## Implemented Scope

Phase 2 replaces the text-only foraging selector with an interactive
herbarium and an embedded route-planning map. Every reviewed forageable has a
resolved inventory-icon path with a ground-resource fallback and an explicit
missing icon. Clicking an herb card toggles PICK/SKIP, with opaque versus
translucent artwork and text labels.

The Wayfinder owns its plotted route. Map clicks add numbered points, Undo
removes the last point, Clear Path removes all points, and Route mode requires
at least two points. Points are persisted per world, limited to 64 points and
500 tiles of total path length, and rejected when their map segment is
unreadable or changes during start-up. The controller scans loaded exact Gob
identities inside the eight-tile route corridor, plans conservative local A*
movement, and acknowledges Pick only from target removal plus compatible
inventory evidence.

Native MapView movement, native checkpoint controls, inventory interaction,
Flower Menu behavior, keyboard controls, and global emergency stop remain
outside the Wayfinder-owned route editor.

## Offline Results

| Check | Result | Evidence |
| --- | --- | --- |
| Focused foraging compile | PASS | `javac` for all `client/src/haven/foraging/*.java` against the existing client classpath |
| Deterministic checks | PASS | `ForagingChecks: PASS` at UI scales 1, 1.25, 1.5, and 2 |
| Icon-path coverage contract | PASS | Reviewed catalog entries remain exact forageable identities and derive `gfx/invobjs/...` paths |
| Route geometry and persistence | PASS | Point order, world scoping, clear behavior, corridor distance, and bounded directional route checks |
| Diff whitespace check | PASS | `git diff --check` |
| Client-stop guard | PASS | `scripts/assert-client-stopped.ps1` reported no running `hafen.jar` process |
| Guarded packaged client build | BLOCKED | `ant jar` reached the unrelated dirty `SessionConservatoryService.java:177` compile error; no foraging or multisession changes were altered |

No packaged JAR was rebuilt or refreshed in this pass because the visible
client is still running.

## Supervised Live Matrix

| Behavior | Result | Required evidence |
| --- | --- | --- |
| Herb icons resolve visibly for the full catalog | NOT RUN | Open the Wayfinder and scroll the complete herbarium |
| Individual PICK/SKIP clicks and opacity | NOT RUN | Click icons/cards and verify text plus translucency change |
| Map point plotting, numbering, Undo, and Clear Path | NOT RUN | Add points on the embedded map and verify the route line |
| Route persistence after close/reopen and world change | NOT RUN | Verify the same world restores points and another world does not |
| Safe route traversal and eight-tile collection corridor | NOT RUN | Supervise one short route with visible herb targets |
| Exact Pick and inventory acknowledgement | NOT RUN | Verify target removal, compatible inventory increase, and yield ledger |
| Manual map takeover, Escape, pause, and emergency stop | NOT RUN | Confirm movement halts and state reasons remain visible |
| Map-segment transition and unsafe-path pause | NOT RUN | Test only with a disposable route and safe observation conditions |

Compilation and deterministic checks do not prove resource timing, map
rendering, server movement, protocol behavior, or live Pick acknowledgement.
