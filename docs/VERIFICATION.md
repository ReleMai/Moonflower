# Verification Log

Do not record credentials, tokens, cookies, account names, character names, or
other session material here.

## Pre-Port Baseline — 2026-08-21

| Check | Result | Notes |
| --- | --- | --- |
| `mvn test` | PASS | 11 tests |
| `web/npm run build` | PASS | Production bundle generated |
| `web/npm run lint` | FAIL | 6 errors and 5 warnings |
| `client/ant deftgt` | PASS WITH WARNING | No Git metadata existed |
| Current packaged startup | NOT RUN | Historical log was not current proof |
| Current live-game login | NOT RUN | Required backup and user supervision |

## Post-Port Results — 2026-08-21

| Check | Result | Evidence |
| --- | --- | --- |
| MoonFlower source baseline | PASS | Recovered local patch applied to the verified client baseline and built from source |
| `mvn test` | PASS | 13 tests, 0 failures/errors |
| `npm run lint` | PASS | 0 errors/warnings |
| `npm run build` | PASS | Vite 8.2.2 production bundle |
| `npm audit --audit-level=moderate` | PASS | 0 vulnerabilities after lockfile repair |
| `ant deftgt` | PASS | Java client and 48 Panama sources package successfully |
| `haven.Resource find-updates` | PASS | All fetched resources are up to date |
| Python `compileall` and import | PASS | `create_app()` exposes 6 routes |
| Python `pip check` | PASS | No broken requirements |
| `scripts/build-all.ps1` | PASS | Web, Maven/server, client, and media setup complete |
| Client database migration smoke | PASS | Both packaged seed DB SHA-256 hashes match migrated copies |
| Client data backup | PASS | 9,658 files / 164,004,514 bytes matched the source snapshot |
| Server data backup | PASS | 50 files / 5,484,090 bytes matched the source snapshot |
| Loopback startup | PASS | Dashboard 200; server/gateway health `ok`; ports bound to `127.0.0.1` |
| Operator auth/API | PASS | Temporary non-default login and authenticated bots endpoint succeeded |
| Clean shutdown | PASS | Ports 8080 and 8091 released |
| Current live-game login | NOT RUN | Intentionally awaiting user-supervised real-account verification |

## In-Game Cookbook Vertical Slice — 2026-08-21

| Check | Result | Evidence |
| --- | --- | --- |
| `client/ant deftgt` | PASS | Java client, packaged JAR, and Panama sources build successfully |
| `haven.cookbook.CookbookChecks` | PASS | Schema creation/migration, deduplication, world scoping, ingredient search, Q10 ranking, multiple qualities, Tansy modifier classification, captured character-efficiency updates, incomplete-placeholder shadowing, native ingredient resources, animal-specific meat badge resolution, native FEP colors/icons, and matched spice-boost calculation |
| `haven.Resource find-updates` | PASS | All fetched resources remain up to date |
| Live food tooltip capture | PASS | User-provided live screenshot shows food names, ingredient percentages, qualities, hunger, and FEPs in the Cookbook window |
| Initial Cookbook window interaction | PASS | User reported the cookbook working in the visible client and supplied its live UI screenshot |
| Polished cookbook compile | PASS | Native-icon dropdown, Q10/actual-quality details, selected-FEP ordering, recipe opener, and HUD button compile successfully |
| Ingredient planner data preview | PASS | A consistent temporary snapshot of the live cookbook data classifies Tansy as a spice, moves it from Ingredients to Modifiers, resolves native Tansy/fish/herb icons, and finds one matching unspiced Pan-Seared Fish baseline |
| Cookbook schema-v3 migration preview | PASS | A consistent temporary snapshot of the live schema-v1 cookbook migrated additively to v3, retained the Pan-Seared Fish observation, and exposed Tansy as a modifier without changing the live database |
| Cookbook HUD composition | PASS (STATIC) | Generated resource composition shows the new cookbook slot followed by the five unchanged native controls |
| Polished cookbook live interaction | NOT RUN | Requires restart and visible checks for dropdown, text fit, recipe matching, and HUD clicks |
| Ingredient planner live interaction | NOT RUN | Requires restart and visible checks for recipe/ingredient tabs, category filtering, row icons, Tansy modifier text, and spice-boost layout |
| Character-adjusted cookbook values | NOT RUN | Requires restart and a fresh tooltip observation to compare the displayed last-captured FEP/hunger modifiers against the visible food tooltip |
| Cookbook restart persistence | NOT RUN | Requires closing and reopening the visible client |

## Fishing Helper Vertical Slice — 2026-08-21

| Check | Result | Evidence |
| --- | --- | --- |
| `client/ant clean deftgt` | PARTIAL | The historical run rebuilt `client/bin` while the visible client held files open; this was later confirmed unsafe and is now blocked by the live-client build guard |
| `haven.fishing.FishingChecks` | PASS | Schema, repeat observations, world filtering, null qualities, reordered choice parsing, current bait/fish classification, resource fallback, grouped map projection, signed-negative grid IDs, map coordinates, transient-marker isolation, tackle averaging, and weakest-component analysis passed |
| `haven.cookbook.CookbookChecks` | PASS | Existing Cookbook persistence and ranking checks still pass after the shared client rebuild |
| `haven.Resource find-updates` | PASS | All fetched resources are up to date |
| Safe equipment workflow | PASS (STATIC) | Deterministic inventory selection, observed cursor/equipment transitions, reserved inventory space, failure rollback, and displaced-hand restoration are implemented |
| Nearby water selection | PASS (STATIC) | Search is bounded to three tiles and 33 coordinate units and prefers tiles with more adjacent water |
| Fishing choice handling | PASS (OFFLINE) | Semantic row parsing and checked percentage-order fallback are covered without relying on a fixed label index |
| Observation persistence | PASS (OFFLINE) | Append-only SQLite repository preserves repeated catches and nullable metadata in a client-local database |
| Equipped Creel and pole routing | PASS (STATIC) | Fishing preparation and catch baselining traverse hidden Inventory or ItemStack contents of equipped Creels; once a pole moves into the hands, its attached contents remain in the selector model and equipment inspection recursively reads nested pole contents |
| Live base helper cycle | PASS (USER OBSERVED) | User reported that the normal action opens the helper, the pole gears successfully, and fish catches are tracked |
| Non-destructive hand selection | PASS (STATIC) | An already equipped pole is reused; an empty hand is preferred; buckets are excluded from displacement; at most one other hand item is moved |
| Live inventory selectors | PASS (STATIC) | Pole, line, hook, bait, and lure choices are derived from main inventory, equipped Creels, hands, and the equipped belt; highlighted choices can be excluded |
| Stack-safe tackle and catch scanning | PASS (STATIC) | ItemStack wrapper rows are excluded from tackle selectors and catch observations while their concrete contents remain discoverable, preventing duplicate `item`/`item, stack of` choices and false stack-wrapper catches |
| Fishing map spots | PASS (STATIC/OFFLINE) | Journal rows are grouped by mapped fishing tile into display-only fish-resource markers; projection coordinates and non-persistence are checked, fish spots receive a visible cyan halo and stable click target, unresolved grids retry, and projection errors are surfaced |
| Fishing marker visibility | PASS (STATIC) | The expanded map sidebar persists a Show fishing markers option; disabling it filters fishing overlays from both the large map and corner minimap and removes their custom click target without deleting journal observations |
| Fishing Journal interaction | PASS (STATIC/OFFLINE) | The journal defaults to fish-resource groups with icons; selecting a group exposes catches by date/time, full details appear only after a dated catch is selected, and map spot clicks filter both levels to that tile |
| Signed map-ID regression | PASS (OFFLINE) | Live catches used valid negative grid IDs, but the marker builder rejected every value below zero; it now rejects only the explicit `-1` sentinel and a focused projection check covers a live-shaped negative grid and segment ID |
| Grouped catch and quality view | PASS (STATIC/OFFLINE) | Recent observations are grouped by fish resource, selecting a group exposes dated/timed catches, and selecting a catch explains the hidden spot input, recorded tackle average, weakest component, per-part qualities, and same-fish quality range at that exact tile without presenting hidden server values as known |
| Replacement tackle right-click | PASS (STATIC) | Attachment now mirrors the recovered v1.59b helper's server message: it takes one selected part, re-resolves the equipped hand-pole, and sends `itemact` directly to that pole with modifier flags fixed at zero; bounded retries stop after the cursor is consumed or the new pole contents appear, and a rejected Creel-stack part is returned to its source stack before inventory fallback |
| Pole hand-slot placement | PASS (STATIC) | Pole placement reads the item's allowed hand-slot mask, sends the equipment widget's native `drop` message for the chosen hand slot, retries while the same rod remains held, and recognizes a server-refreshed equipped item by resource instead of requiring the original widget identity |
| Equipped-first preparation order | PASS (STATIC) | The helper equips and acknowledges the selected pole before moving tackle, then inspects that stable hand-pole and attaches only missing selected line, hook, and bait/lure parts from inventory or equipped Creels |
| Fishing Journal HUD composition | PASS (STATIC) | A dedicated open-journal/fish icon shares the Cookbook-style menu extension, opens the GameUI-owned journal, and leaves the five native controls in their original order |
| Authoritative catch confirmation | NOT RUN | Records remain explicitly `candidate` until supervised live evidence identifies a reliable caught-fish event |
| Regression fixes live interaction | NOT RUN | Requires restart and visible checks that an inventory or belt pole leaves the cursor and enters either allowed hand, initial and post-break replacement tackle right-clicks onto it, attached tackle remains selected, stack wrappers are absent, the helper completes a cast, signed-ID map halos are visible/clickable, fish groups expand to dated catches, quality factors scroll correctly, and the revised HUD icon renders correctly |
| Frozen white-window diagnosis | PASS | The live UI thread terminated with missing `AudioSprite$RepeatSprite$1` and `MiniMap$Scale2D` classes after `client/bin/hafen.jar` was rewritten six minutes after that JVM started; the rebuilt JAR contains both classes, confirming an in-place deployment race rather than missing source |
| Live-client build guard | PASS | Windows preflight script, Ant `clean`/`bin`/`deftgt`, and `scripts/build-all.ps1` refuse to modify the packaged client while a `java -jar ...hafen.jar` process is running |
| MoonFlower identity and paths | PASS | `haven.MoonFlowerChecks` verifies the `MoonFlower` client ID, `v1.0.0` version, AppData directory, and SQLite path |
| Rebrand data copy | PASS | Four mutable database files matched by count and byte total; the copied preferences matched by SHA-256; original files were retained and a complete pre-change backup was created |
| Branding residue scan | PASS | Tracked source, scripts, launch metadata, and documentation contain no former product or maintainer branding references |

## Repeatable Commands

```powershell
mvn test
Push-Location web; npm run lint; npm run build; npm audit --audit-level=moderate; Pop-Location
Push-Location client; ant clean deftgt; java -cp "bin/hafen.jar;bin/sqlite-jdbc-3.42.0.0.jar" haven.MoonFlowerChecks; java -cp "bin/hafen.jar;bin/sqlite-jdbc-3.42.0.0.jar" haven.cookbook.CookbookChecks; java -cp "bin/hafen.jar;bin/sqlite-jdbc-3.42.0.0.jar" haven.fishing.FishingChecks; java -cp bin/hafen.jar haven.Resource find-updates; Pop-Location
Push-Location media-gateway; .\.venv\Scripts\python.exe -m compileall -q app.py; .\.venv\Scripts\python.exe -m pip check; Pop-Location
.\scripts\build-all.ps1
.\scripts\start-platform.ps1 -SkipBuild -NoBrowser
.\scripts\stop-platform.ps1
```
