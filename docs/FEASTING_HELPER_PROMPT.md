# Feasting Helper Implementation Prompt

Implement the first complete, reviewable Feasting Helper slice for the current
MoonFlower Haven & Hearth Java client.

The helper should guide a seated player through foods currently available on
the open table and in the player's main inventory. It must be an extension of
the existing Table window, not a separate window, HUD panel, or remote bot. The
player chooses an attribute, sees a live recommended eating order and the
projected chance that the next food event raises that attribute, receives clear
warnings when the chosen attribute is getting too far ahead of the others, and
may explicitly start a fast auto-eat sequence.

The existing tableware protection remains the safe default. The helper may also
offer an explicit per-run mode that continues eating even when symbel/tableware
could break, because this is a requested feature. That override must be narrow,
obvious, reversible, and never silently change the player's global preference.

## Working Rules

Before editing:

1. Read `AGENTS.md`, `docs/CODING_STANDARDS.md`,
   `docs/FILE_ORGANIZATION.md`, `docs/CURRENT_TASKS.md`, and
   `docs/TECHNICAL_DEBT.md`.
2. Inspect the live worktree and preserve unrelated changes. Do not assume an
   older branch, commit, or prompt describes the current checkout.
3. Inspect the actual table, food, inventory, character-meter, and cookbook
   seams before proposing the implementation. At minimum, review:
   - `client/src/haven/UI.java`, especially `processWindowContent` and
     `initTableUi`
   - `client/src/haven/TableInfo.java`
   - `client/src/haven/GItem.java`, especially the existing tableware-breakage
     guard and local tooltip access
   - `client/src/haven/Inventory.java`, `WItem.java`, and `Window.java`
   - `client/src/haven/BAttrWnd.java`, especially `FoodMeter`, `GlutMeter`, and
     the base attributes
   - `client/src/haven/resutil/FoodInfo.java` and its live efficiency snapshot
   - `client/src/haven/cookbook`, especially `CookbookAttribute`,
     `CookbookFoodParser`, and `CookbookService`
4. Give the user a concise plan tied to the files actually found, then carry
   the approved local implementation through verification. Do not stop at a
   mock-up or recommendations.
5. Preserve the truthful status of the current Fishing Helper live-validation
   task. If this feature becomes the new active task, move the pending fishing
   validation to an explicitly documented pending section rather than implying
   it passed.

## Required User Experience

### Table-integrated sliding frame

- Extend the existing Table window through `TableInfo` or a small adapter owned
  by it. Do not create another `Window`.
- Keep the existing **Prevent Tableware from Breaking** control available.
- Add a compact Feasting Helper handle or header that expands and collapses an
  attached frame with a short, native-looking slide animation.
- Opening a valid feasting Table window should make the helper available. Use
  the live Table window and its feast controls as the client-observable
  authority; do not invent an unreliable seated-player detector.
- Remember collapsed/expanded state only if that matches existing client
  preference conventions. Do not add a database just for panel state.
- Closing or invalidating the Table window must immediately stop automation and
  discard live widget references.
- Keep the layout usable at supported UI scales and for different table
  inventory sizes. Expanding and collapsing must repack the Table window
  without covering its inventory, buttons, labels, or existing tableware
  control.

The expanded frame should contain, at minimum:

- an attribute selector using the native nine food-event attributes and their
  existing icons/colors;
- a concise current FEP-bar summary and target chance;
- a balance summary showing the lowest, highest, and selected base attribute,
  including the selected attribute's lead over the lowest attribute;
- a recommended ordered list with food icon/name, source (`Table` or
  `Inventory`), target FEP, total FEP, hunger cost, projected target chance
  after that bite, and a short reason for its position;
- **Recalculate**, **Start**, and **Stop** controls;
- a safe-mode control that respects tableware protection;
- a clearly worded, unchecked per-run control such as **Fast auto-eat — allow
  symbel breakage** with a warning tooltip;
- a visible status line for loading, ready, eating, stopped, completed, or a
  specific failure reason.

Do not turn the frame into a dense debug dashboard. The first view should make
the next recommended food and the target chance obvious; secondary numerical
details may use tooltips or compact rows.

## Live Food Sources And Authority

- Consider only food widgets that are currently reachable in:
  1. the food inventory or inventories belonging to the open Table window; and
  2. `GameUI.maininv`.
- Do not search closed cupboards, belts, nested containers, remote inventories,
  the Cookbook database, or the world for additional food in this slice.
- Distinguish the Table's food inventory from its small tableware/symbel
  inventories using the actual live widget structure. Keep the existing
  dimensions and behavior in mind, but do not scatter magic size checks across
  the new feature; isolate any unavoidable classification rule.
- A candidate is a live `GItem`/`WItem` whose local tooltip contains
  `FoodInfo`. Use local-only tooltip data so ranking does not trigger optional
  network food integrations.
- Treat the live item's tooltip and the current character/Table state as
  authoritative. The Cookbook may provide reusable attribute presentation and
  parsing concepts, but historical Cookbook records must never substitute for
  a live food that is absent, changed, unreadable, or stale.
- Handle `Loading` as an ordinary pending state. Do not freeze the UI, rank
  partially read values as certain, or eat an item before its food data is
  available.
- Give each plan entry enough stable identity to re-resolve the intended live
  widget before acting. Never keep a stale `WItem` reference across inventory
  mutations and assume it is still valid.

## Calculation And Recommendation Model

Put calculation in small, UI-independent classes so it can be checked without
a live game session. Do not embed the planner in `TableInfo`, `GItem`, or one
large widget file.

For every candidate, snapshot:

- source and live item identity;
- name, resource, icon, quality, and stack/quantity information when exposed;
- raw food-event values;
- current adjusted food-event values after the live character, satiation,
  account, wound, and Table modifiers already represented by the client;
- target FEP, non-target FEP, total FEP, hunger cost, and energy gain;
- any uncertainty that prevents a reliable recommendation.

Reuse or minimally extract the calculation behind `FoodInfo.currentEfficiency`
instead of duplicating its Table-label parsing and character modifiers. This is
also the appropriate narrowly scoped opportunity to address the existing debt
about a shared live food-efficiency calculator, if extraction is required for
correctness. Do not refactor unrelated tooltip rendering.

Read the current FEP pool and cap from `BAttrWnd.FoodMeter`. Simulate the plan
one bite at a time from the current live pool:

- The displayed **projected target chance** is the selected attribute's share
  of the projected FEP pool at the next food-event trigger.
- Show the formula or explanatory tooltip: selected projected FEP divided by
  projected total FEP.
- If the currently observable game protocol uses different semantics, document
  and test the observed rule instead of forcing this assumption.
- If the proposed foods do not yet reach the cap, label the value as the
  projected share and state how much FEP remains; do not present it as an
  immediate level-up probability.
- If a bite can cross a trigger or the server resets/updates the pool, simulate
  only behavior justified by live meter events. Recalculate from the server's
  acknowledged meter state after every automated bite.
- Label projections as estimates until a visible supervised session confirms
  the live server behavior. Never show false precision when required state is
  unavailable.

Use a deterministic recommendation policy, documented in code and tests:

1. Prefer sequences that maximize the selected attribute's chance at the next
   food-event trigger.
2. Prefer reaching the trigger with less unrelated FEP dilution and less
   avoidable overfill.
3. Among materially equivalent choices, prefer lower hunger cost and then a
   stable tie-breaker such as source, resource name, quality, and widget ID.
4. Never recommend the same physical item or stack unit twice.
5. Recompute when relevant table contents, main inventory contents, tooltip
   data, FEP meter/cap, hunger efficiency, satiations, Table bonus, or base
   attributes change.

The exact score should be explainable. Do not hide an arbitrary weighted model
behind a single number.

## Attribute Balancing

Balancing is advisory by default and must remain understandable:

- Show all nine current base attribute values, or a compact summary with full
  values in a tooltip.
- Clearly identify the current lowest and highest attributes and how far the
  selected attribute is above or below the lowest.
- Warn when the selected attribute is already the unique highest or when the
  planned gain would widen the largest gap.
- Provide a **Balanced target** option that recommends one of the currently
  lowest attributes and uses deterministic tie-breaking when several are tied.
- When the player explicitly chooses a different target, continue to calculate
  it; do not silently replace the user's choice. Show the imbalance warning in
  the plan and again before starting automation if the target is already the
  highest.
- Do not invent permanent caps or an unexplained ideal ratio. If a configurable
  maximum lead is added, give it plain units, an explicit default, and a way to
  disable it; otherwise keep balance as transparent guidance rather than a
  hidden hard stop.

## Auto-Eat Behavior

Automation must be visible, local, bounded, and initiated by the player:

- **Start** freezes a displayed plan revision, revalidates its first item, and
  performs the same Table feast interaction a player would use. Inspect the
  actual widget/message path before implementing it; do not guess or spam raw
  server messages.
- Eat one item at a time. After each bite, wait for authoritative observable
  progress such as the intended item/stack changing and the character food
  meter updating. Use bounded retries/timeouts, then recalculate from fresh
  state before the next bite.
- "Fast" means proceed immediately after acknowledgement. It does not mean send
  multiple blind clicks or take messages before the server catches up.
- Stop immediately on **Stop**, Escape, Table closure, loss of the feast action,
  combat, an unreadable or missing planned item, stale plan identity, tooltip
  loading timeout, server rejection, or failure to observe progress.
- Never walk, acquire food, move food between containers, craft, login, select a
  character, reconnect, or continue unattended.
- Display the exact stop reason and leave the client/cursor in a recoverable
  state.

### Symbel/tableware protection

Safe mode is the default:

- Respect the existing **Prevent Tableware from Breaking** preference and the
  one-durability `Wear` check.
- If any relevant tableware durability is loading or unreadable, fail closed.
- Stop with the existing clear warning before the next bite could break
  tableware.

The requested breakage-ignoring mode must behave as follows:

- It is unchecked at the start of every Table/helper run, even if another UI
  preference is remembered.
- Its label and tooltip must say that symbel/tableware can break and be lost.
- Starting while it is enabled requires one explicit confirmation for that
  run, showing which currently observed tableware is at risk when possible.
- It bypasses the breakage guard only for bites dispatched by this helper and
  only while that confirmed run is active.
- Do not toggle, overwrite, or persistently disable
  `preventTablewareFromBreaking` in `OptWnd` or `TableInfo`.
- Manual feasting and all other helpers must continue to obey the player's
  global protection setting.
- Implement the narrow override with an explicit helper action/run context,
  not a broad mutable global flag that can leak after errors or window closure.
- Always clear the override in success, stop, timeout, exception, widget
  destruction, and session teardown paths.

## Suggested Structure

Adapt names to existing conventions, but keep responsibilities separate. A
reasonable first structure is:

```text
client/src/haven/feasting/
  FeastingSnapshot.java       live immutable planning input
  FeastingCandidate.java      one reachable live food projection
  FeastingPlan.java           ordered result and explanations
  FeastingPlanner.java        pure deterministic calculation
  FeastingController.java     bounded UI-thread interaction state machine
  FeastingPanel.java          sliding Table-window presentation
  FeastingChecks.java         offline deterministic checks
```

Keep `TableInfo` as the small integration/lifecycle adapter. Extract a shared
food-efficiency snapshot only if necessary for the planner; do not make the
Cookbook database or window own feasting behavior.

All live widget reads and messages must respect the UI thread. Database or
network work is not needed for this slice. Avoid sleeps on the UI thread; use
the client's tick/deferred mechanisms and explicit state transitions.

## Verification

Add deterministic offline checks for at least:

- candidate classification from live-style tooltip fixtures;
- adjusted target/total FEP and hunger values;
- a partially filled FEP bar and a sequence that reaches the cap;
- projected target share/chance math and the "does not fill yet" case;
- ordering, stable tie-breaking, duplicate item/stack-unit prevention, and
  table-versus-main-inventory sources;
- target mode and tied-lowest Balanced target mode;
- warnings when the selected target is already highest;
- loading, unreadable, removed, or replaced items;
- recalculation after plan-affecting state changes;
- acknowledgement timeout and stop-state cleanup;
- safe mode refusing an at-risk symbel item;
- confirmed fast mode bypassing only helper-driven breakage checks;
- manual eating still respecting the global protection setting;
- Table closure and Escape cancelling an active run.

With the visible client closed, run the repository's current Java gate. At
minimum:

```powershell
Push-Location client
ant clean deftgt
java -cp "bin/hafen.jar;bin/sqlite-jdbc-3.42.0.0.jar" haven.MoonFlowerChecks
java -cp "bin/hafen.jar;bin/sqlite-jdbc-3.42.0.0.jar" haven.cookbook.CookbookChecks
java -cp "bin/hafen.jar;bin/sqlite-jdbc-3.42.0.0.jar" haven.fishing.FishingChecks
java -cp "bin/hafen.jar;bin/sqlite-jdbc-3.42.0.0.jar" haven.feasting.FeastingChecks
java -cp bin/hafen.jar haven.Resource find-updates
Pop-Location
```

If package names or the current verification gate differ, use the live
repository's canonical commands and document the exact commands and exits.
Compilation and offline checks do not prove live Table behavior.

Before a visible live test, stop the client and run:

```powershell
.\scripts\backup-client-data.ps1
```

Then perform a user-supervised session that verifies:

1. Opening a valid Table exposes the attached helper and its slide/collapse
   behavior without damaging the native Table layout.
2. Foods on the Table and in the main inventory appear once, with correct live
   FEP, hunger, source, and selected-attribute projections.
3. Changing food, attribute, FEP progress, or Table modifiers refreshes the
   recommendation.
4. Safe auto-eat performs one acknowledged bite at a time and stops before
   at-risk tableware.
5. Stop, Escape, and Table closure cancel immediately.
6. Normal manual feasting still observes the existing global protection.
7. Breakage-ignoring mode is tested only after the user explicitly approves
   risking the named disposable symbel/tableware in that session. Do not break
   live equipment merely to satisfy an automated test.

Record visible tests as **PASS**, **FAIL**, or **NOT RUN** in
`docs/VERIFICATION.md`. Report live chance semantics or interaction timing as
unverified until actually observed.

## Scope Boundaries

- No separate helper window, HUD button, dashboard, overlay service, or remote
  operator control.
- No food acquisition, crafting, container search, inventory transfer,
  character movement, table selection, login, reconnect, or unattended play.
- No database or account/session credential changes.
- No rewrite of the Cookbook, inventory, Table, tooltip, or character-sheet
  systems.
- No claim that a build proves server behavior.
- Preserve Cookbook and Fishing Helper behavior and their local data.
- Do not inspect, log, or store credentials or session material.

## Completion Criteria

The slice is complete when:

1. The Feasting Helper is an expandable sliding frame owned by the live Table
   window, not an independent UI.
2. It ranks only currently reachable Table and main-inventory foods for the
   selected or Balanced target using live adjusted food and character data.
3. Its projected target chance/share and eating order are transparent,
   deterministic, and covered by offline checks.
4. It gives clear attribute-balance guidance without silently overriding an
   explicit player choice.
5. Player-started auto-eat uses acknowledged, bounded, one-item interactions
   and stops safely on stale or invalid state.
6. Tableware protection remains the default; the breakage-ignoring mode is a
   conspicuous, confirmed, per-run helper-only override with cleanup on every
   exit path.
7. Existing MoonFlower, Cookbook, and Fishing checks still pass.
8. Documentation accurately distinguishes static/build verification from live
   supervised results, and any shortcut is recorded in
   `docs/TECHNICAL_DEBT.md`.

After implementation, explain in plain language which files changed, how live
food ranking and chance projection work, how the controller proves each bite,
how the scoped symbel override avoids weakening manual feasting, what was
verified, and what still requires the supervised game session.
