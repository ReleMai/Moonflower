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
| `haven.cookbook.CookbookChecks` | PASS | Temporary compilation plus the offline check suite verifies schema creation/migration, deduplication, world scoping, same-food recipe grouping, selected-stat and FEP/hunger representatives, ingredient search, Q10 ranking, multiple qualities, main-ingredient ordering, persisted tooltip order without recipe duplication, Tansy modifier classification, captured character-efficiency updates, incomplete-placeholder shadowing, native ingredient resources, animal-specific meat badge resolution, native FEP colors/icons, and matched spice-boost calculation |
| `haven.Resource find-updates` | PASS | All fetched resources remain up to date |
| Live food tooltip capture | PASS | User-provided live screenshot shows food names, ingredient percentages, qualities, hunger, and FEPs in the Cookbook window |
| Initial Cookbook window interaction | PASS | User reported the cookbook working in the visible client and supplied its live UI screenshot |
| Polished cookbook compile | PASS | Grouped food rows, known-recipe dropdown, selected-stat/total/FEP-per-hunger/quality/name sorting, wider recipe columns, native-icon dropdown, Q10/actual-quality details, selected-FEP ordering, recipe opener, and HUD button compile successfully |
| Ingredient planner data preview | PASS | A consistent temporary snapshot of the live cookbook data classifies Tansy as a spice, moves it from Ingredients to Modifiers, resolves native Tansy/fish/herb icons, and finds one matching unspiced Pan-Seared Fish baseline |
| Cookbook schema-v4 ingredient order | PASS (OFFLINE) | New databases retain tooltip ingredient positions; existing rows with no stored position use the food/category main-ingredient fallback. Re-observation backfills positions transactionally without duplicating the recipe. |
| Live Salmon capture | PASS (DATA) | A read-only check of the active cookbook database found the newly crafted Pan-Seared Fish with Salmon, Salt, and Stinging Nettle. It was saved after the already-open cookbook view populated, motivating the new visible capture-pending status. |
| Cookbook inventory auto-scan compile | PASS | `GItem` and `CookbookWindow` compile together against the packaged client. The visible Cookbook scans the main inventory immediately and every 1.5 seconds, submitting only new or changed tooltip sequences and retrying data that is not ready. |
| Cookbook recipe/ingredient readability compile | PASS | Temporary compilation and `CookbookChecks` verify strongest-attribute aggregation, ingredient recipe highlights, native food-resource retention, Q10 values, and existing persistence behavior. Recipe variants use the strongest attribute color; unmodified detail lines are controlled by a persistent checkbox; ingredient selection exposes a scrollable icon-and-FEP food ranking. |
| Cookbook HUD composition | PASS (STATIC) | Generated resource composition shows the new cookbook slot followed by the five unchanged native controls |
| Polished cookbook live interaction | NOT RUN | Requires restart and visible checks for grouped rows, known-recipe selection, sort modes, automatic unhovered-item detection, text fit, recipe matching, and HUD clicks |
| Main-ingredient order and capture status live interaction | NOT RUN | Requires restarting the currently running client, then confirming Salmon appears first and the Cookbook briefly reports that a food is being captured. |
| Cookbook inventory auto-scan live interaction | NOT RUN | The clean package now includes auto-scan. Open the Cookbook with an unmoved food in inventory and confirm the entry appears automatically in a visible session. |
| Cookbook strongest-stat and ingredient-food UI live interaction | NOT RUN | The clean package now includes the revised UI; dropdown text fit, checkbox behavior, food icons, colored Q10 values, and ingredient-panel scrolling still require visible review. |
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
| Previous equipped-first tackle workflow | SUPERSEDED | Supervised live attempts showed that repeated equipped-pole interactions and repeated slot drops left items held or unacknowledged; that controller was removed rather than patched again |
| Transactional fishing-rig rework | PASS (COMPILE/OFFLINE) | The replacement separates discovery, content inspection, stationary-pole assembly, and hand placement. It sends one manual-equivalent `itemact` per missing component, waits for pole-content acknowledgement, uses the native Shift+left-click transfer so the server selects the hand, preserves buckets, retains a cursor-held pole in the selector, and fails on ambiguous pole identity |
| Fishing action and preparation separation | PASS (STATIC) | The normal Fishing action only opens, raises, and refreshes the helper. Prepare is a dedicated button; Start remains the explicit preparation-plus-fishing command, and bot-control calls remain explicit automation requests |
| Transactional fishing-rig live interaction | NOT RUN | The clean package includes the transactional rig changes; supervised checks remain for inventory and belt poles, Creel tackle, a missing replacement line, bucket preservation, server-selected hand placement, selector stability during transfers, and an empty cursor |
| Fishing Journal HUD composition | PASS (STATIC) | A dedicated open-journal/fish icon shares the Cookbook-style menu extension, opens the GameUI-owned journal, and leaves the five native controls in their original order |
| Authoritative catch confirmation | NOT RUN | Records remain explicitly `candidate` until supervised live evidence identifies a reliable caught-fish event |
| Regression fixes live interaction | NOT RUN | Requires restart and visible checks that an inventory or belt pole leaves the cursor and enters either allowed hand, initial and post-break replacement tackle right-clicks onto it, attached tackle remains selected, stack wrappers are absent, the helper completes a cast, signed-ID map halos are visible/clickable, fish groups expand to dated catches, quality factors scroll correctly, and the revised HUD icon renders correctly |
| Frozen white-window diagnosis | PASS | The live UI thread terminated with missing `AudioSprite$RepeatSprite$1` and `MiniMap$Scale2D` classes after `client/bin/hafen.jar` was rewritten six minutes after that JVM started; the rebuilt JAR contains both classes, confirming an in-place deployment race rather than missing source |
| Live-client build guard | PASS | Windows preflight script, Ant `clean`/`bin`/`deftgt`, and `scripts/build-all.ps1` refuse to modify the packaged client while a `java -jar ...hafen.jar` process is running |
| MoonFlower identity and paths | PASS | `haven.MoonFlowerChecks` verifies the `MoonFlower` client ID, `v1.0.0` version, AppData directory, and SQLite path |
| Rebrand data copy | PASS | Four mutable database files matched by count and byte total; the copied preferences matched by SHA-256; original files were retained and a complete pre-change backup was created |
| Branding residue scan | PASS | Tracked source, scripts, launch metadata, and documentation contain no former product or maintainer branding references |

## Feasting Helper Vertical Slice — 2026-08-21

| Check | Result | Evidence |
| --- | --- | --- |
| Table-integrated implementation compile | PASS | The sliding `TableInfo` frame, live snapshot adapter, pure planner, controller, and scoped breakage action context compile in the current Ant build |
| `haven.feasting.FeastingChecks` | PASS | Character-sheet zero-FEP current chance, separate projected chance, inventory-only source filtering/no-fallback behavior, partial-bar projection, exact trigger fill, non-triggering remainder, target-share ordering, hunger/source tie-breaks, widget deduplication, Balanced target selection, imbalance warnings, acknowledgement signals, safe-mode refusal, and scoped override cleanup pass |
| Aggregated local platform gate | PASS | `scripts/build-all.ps1` completed the web lint/build, 13-test Maven package, client package, and media-gateway dependency preparation after the Feasting Helper integration |
| Manual tableware protection boundary | PASS (STATIC) | Manual feast `take` messages use the shared recursive durability inspection; only a helper-dispatched action inside `FeastingActionContext` can bypass the guard |
| Table layout and sliding animation | NOT RUN | Requires a visible Table at supported UI scaling |
| Live food values and plan refresh | NOT RUN | Requires observed Table/main-inventory food, live modifiers, and FEP/attribute changes |
| Acknowledged one-bite auto-eat | NOT RUN | Requires a supervised Table feast to confirm button caption, cursor timing, stack/item mutation, meter timing, and stop behavior |
| Projected chance versus server trigger | NOT RUN | Requires observing a real FEP trigger/reset; offline math is not server authority |
| Confirmed symbel-breakage override | NOT RUN | Requires separate explicit user approval for named disposable live tableware; do not test destructively by default |

## Combat Assist And Animal Health Bars — 2026-08-23

| Check | Result | Evidence |
| --- | --- | --- |
| Current-source research | PASS | Exact official `8c76cb5`, Hurricane `c62d5ae`, and Ender `dd5c08b` refs were inspected and recorded in `docs/COMBAT_RESEARCH.md`; no compared client exposed exact animal current/max HP |
| `client/ant clean deftgt` | PASS WITH 16 WARNINGS | After the visible clients closed, 910 Java sources and 48 Panama sources compiled and the package rebuilt; warnings are existing deprecation/unchecked warnings |
| Official resource dependency integrity | PASS AFTER REPAIR | The clean download initially left a truncated 102,013-byte `hafen-res.jar`; it was re-downloaded from the same official URL, matched the advertised 39,681,258-byte length, passed `jar tf`, and `ant bin` regenerated the package manifest |
| `haven.combat.CombatAssistChecks` | PASS | Integer event parsing, unrelated/encoded-number rejection, duplicate protection, damage-type separation, multiple-Gob isolation, combat baseline reset, Gob/resource lifecycle reset, manual clear, approximate/range/lower-bound/unknown evidence, contradiction, staleness, classifier exclusion, disabled setting, and layout policy passed against `bin/*` |
| Existing MoonFlower/Cookbook/Fishing/Feasting checks | PASS | All four packaged offline suites passed against the integrated `bin/*` classpath |
| `haven.Resource find-updates` | PASS | All fetched resources are up to date |
| Fixed-HP animal multiple-hit estimate | NOT RUN | Requires a visible user-supervised combat session |
| Two same-species animals and target switching | NOT RUN | Requires a visible user-supervised combat session |
| Armor/HHP/healing/missed-event semantics | NOT RUN | Source separates known colors, but live delivery and healing behavior remain unproven |
| `OD_HEALTH` animal behavior | NOT RUN | It remains object-durability evidence unless a supervised trace proves animal vitality semantics |
| Overlay placement, UI scale, toggle, and disposal | NOT RUN | Static layout/cache paths compile; visible rendering and logout cleanup require supervision |

## Private Steam Workshop Readiness — 2026-08-23

| Check | Result | Evidence |
| --- | --- | --- |
| Workshop route and privacy research | PASS | Haven's official custom-client uploader and Steam visibility documentation confirm AppID `3051280` and owner-only `private` visibility; SteamPipe is not used |
| Tracked publishing metadata | PASS | MoonFlower metadata is private, contains no inherited Workshop ID, public repository URL, or feature inventory |
| Java uploader compile | PASS (ISOLATED) | The private-only `SteamWorkshop.java` compiled against the current packaged classpath without replacing the live JAR |
| Direct-upload backstop | PASS | A direct invocation without the explicit confirmation property exited before Steam initialization |
| Public and inherited-ID refusals | PASS | Isolated fixtures with `visibility=public` and Workshop ID `3423755273` both exited before Steam initialization |
| Private staging audit | PASS | The final allowlisted staging contains 758 files and 172,279,328 bytes, includes license notices and the new artwork/description, rejects sensitive paths, preference/account-data files, and literal IP configuration, and is bound to manifest SHA-256 `6721ea299a621b6cbc7e651e00ba1e0274ac3d1ac1c30f6774d9216832887e6b` |
| Manifest checkpoint and cancellation | PASS | The publishing script verified every staged path, size, and SHA-256, displayed the private create checkpoint, and cancelled on a nonmatching typed phrase without contacting the uploader |
| Legacy Ant upload target | PASS | The former direct upload target now fails immediately and directs operators to the audited scripts |
| Executable encryption | NOT IMPLEMENTED (BY DESIGN) | Local Java encryption cannot keep code secret from a recipient, ProGuard is not a security boundary, and LGPL-covered client portions must remain modifiable/relinkable for recipients |
| Clean current-source package and checks | PASS WITH 11 WARNINGS | With the client closed, 897 client sources and 48 Panama sources compiled; MoonFlower, Cookbook, Fishing, Feasting, Combat Assist, and resource checks passed against `bin/*` |
| Client-only JAR boundary | PASS | The final JAR contains zero operator bridge, web-map uploader, remote cookbook uploader, plaintext account-list, or GitHub update-checker classes; web/server/media directories are absent from staging |
| Local account-switching boundary | PASS (STATIC) | The Save on this PC account list is included as client functionality, but its usernames/passwords remain in the current Windows user's Java preferences. The Workshop allowlist does not copy preference storage, and staging rejects preference- or saved-account-named files |
| Personal identifier staging scan | PASS | The final staged package has zero preference/account-data filenames and zero text matches for the local Windows profile path, Steam account ID, or owner profile name; the JAR contains the account-switcher classes but no saved account values |
| Local sensitive-preference cleanup | PASS | Seven sensitive file-backed keys were removed without displaying their values, 294 nonsensitive settings were restored from the existing recovery copy, Java re-read the sanitized XML successfully, and zero sensitive keys remain |
| Steam private item creation and final update | PASS | The retained item `3788836144` was updated in place under AppID `3051280` with the launch repair, security package, artwork, feature description, and restored local account switching. The uploader forced `private`, redacted Steam account diagnostics, and kept the item ID only in ignored local state |
| Unauthenticated visibility check | PASS (INFERENCE) | Steam's public file-details API returned EResult `9` and no title/details after the final update; combined with the successful private-only upload, this is consistent with the item not being publicly readable |
| Steam owner visibility | PASS | The signed-in item page reports `Current visibility: Hidden` and states that only the owner, admins, and marked creators can see it |
| Steam chained-launch path | PASS | The original installed duplicate reproduced `NoSuchResourceException` against Haven's `user.dir`; `ClientInstall` now decodes the official Haven Launcher cache path back to the Workshop package and verifies its resource marker |
| Steam subscription and no-login launch | PASS | The retained item downloaded to Workshop ID `3788836144` and Steam launched its cached `hafen.jar`; `MoonFlower (v1.0.0)` reached the visible login screen with empty username/password fields and no Java exception. No login or world entry was attempted |
| Older duplicate removal | PENDING CONFIRMATION | The older item is ID `3788835767`. Its final Steam deletion remains intentionally paused for action-time confirmation because deletion is irreversible |

## MoonFlower Entry-Screen Redesign — 2026-08-23

| Check | Result | Evidence |
| --- | --- | --- |
| Clean client package | PASS WITH 11 WARNINGS | With both packaged-client JVMs stopped, 899 Java sources compiled and `ant clean bin` completed. The warnings are the existing deprecation/unchecked warnings. |
| Login-screen visual check | PASS | The locally packaged `Play.bat` reached the visible MoonFlower login screen with the new moonflower-and-hearth artwork, title, saved-account panel, login panel, empty credential fields, and no Java exception. No login was attempted. |
| Local account switching | PRESERVED | The redesigned screen still exposes the saved-account list and Save on this PC option. The visible clean-package check contained no saved names, and preference storage remains outside the package allowlist. |
| Character-selection redesign | PASS (COMPILE/STATIC) | The selector now installs the dedicated MoonFlower background, hides only a legacy full-screen image, renders selectable character cards and a separate avatar panel, and retains Play, keyboard selection, Log out, and music-volume behavior. |
| Character-selection live check | NOT RUN | Reaching this screen requires a real account login; it remains a supervised visual check rather than an automated credentialed action. |
| Original entry music | PASS (OFFLINE) | The reproducible generator produced a 32-second Moonlit Hearth login loop and a 28-second Homecoming selector loop at 44.1 kHz stereo. Both have non-zero RMS/peak data and are loaded only from packaged local files. |
| Hurricane entry-art exclusion | PASS | The clean `bin` package excludes the thirteen inherited `*Screen.png` files, their matching `*Theme.res` files, and the old character-selection themes while preserving unrelated in-game ambient themes. |
| Existing offline suites | PASS | MoonFlower, Cookbook, Fishing, Feasting, Combat Assist, and resource-update checks all pass against the clean `bin/*` package. |
| Private staging refresh and Steam update | PASS | The audited private stage contains 733 files and 153,744,558 bytes with manifest SHA-256 `29b5ebbfef58bdcdbba2902cd617de388ea7c82b608b53a689405765ab5107cb`. Steam completed `PreparingConfig`, `PreparingContent`, `UploadingContent`, `UploadingPreviewFile`, and `CommittingChanges` for the retained private item `3788836144` with change note `MoonFlower entry screen redesign`. |
| Steam stale-install recovery | PASS | Steam had committed content manifest `4995976587164804471`, but the local subscription still contained the preceding release while reporting that no update was needed. The guarded refresh command forced a 19,816,352-byte download, after which the installed JAR matched the staged SHA-256 `3c3f73e432f4e9ba1c1e57b856aec3be0d3131f79877e8e3c372c3e26faa5cd7`; both new PNGs and WAVs also matched byte-for-byte, and the inherited rogue login artwork was absent. |
| Haven Launcher cache recovery | PASS | The single cache entry for retained item `3788836144` was moved to a reversible `.stale-pre-redesign-20260823` backup. The official Haven launcher recreated it from the refreshed Workshop package, and its 9,170,854-byte JAR matched the staged release hash. No preference or account-data path was changed. |
| Steam-installed redesign visual check | PASS | The official Haven launcher opened `MoonFlower (v1.0.0)` from retained Workshop item `3788836144` and visibly rendered the new moonlit MoonFlower login screen, saved-account panel, login panel, and music control. Credential fields and the saved-account list were empty; no login or world entry was attempted. |
| Grok layout integration | PASS (LOGIN VISUAL / CHARACTER STATIC) | The proposed named layout constants, wider account rows, empty-account message, contained credential/Steam controls, narrower character cards, and expanded character-preview panel were integrated without replacing license headers or changing `savedAccounts` storage. A clean 899-source build completed with the same 11 existing warnings, `MoonFlowerChecks` passed, and the rebuilt login screen showed both panels without overlap. The optional replacement background images were not present in the supplied files, and the character screen still requires a supervised real-account visual check. |
| Private Grok-layout Steam update | PASS | The audited update stage contains 734 files and 154,350,908 bytes with manifest SHA-256 `b223797fb6c8880f5aaff317e0b3f63cf7e923acff23f3a44e81651a9f3daf86`. Steam committed content manifest `7879464736846329717` to retained private item `3788836144` and logged the upload result as `OK`. The subscribed package was refreshed, its JAR matched the staged SHA-256 `7a7757edb2e63585b7963946d106f894344f5f9b4e001684dbb5a01585757add`, the stale Haven Launcher cache was moved to a reversible backup, and the official no-login Workshop launch recreated a matching cache entry. No credentials or real-account login were used. |

## Repeatable Commands

```powershell
mvn test
Push-Location web; npm run lint; npm run build; npm audit --audit-level=moderate; Pop-Location
Push-Location client; ant clean deftgt; java -cp "bin/*" haven.MoonFlowerChecks; java '-Dhaven.uiscale=1' -cp "bin/*" haven.cookbook.CookbookChecks; java '-Dhaven.uiscale=1' -cp "bin/*" haven.fishing.FishingChecks; java '-Dhaven.uiscale=1' -cp "bin/*" haven.feasting.FeastingChecks; java '-Dhaven.uiscale=1' -cp "bin/*" haven.combat.CombatAssistChecks; java -cp "bin/*" haven.Resource find-updates; Pop-Location
Push-Location media-gateway; .\.venv\Scripts\python.exe -m compileall -q app.py; .\.venv\Scripts\python.exe -m pip check; Pop-Location
.\scripts\build-all.ps1
.\scripts\start-platform.ps1 -SkipBuild -NoBrowser
.\scripts\stop-platform.ps1
```
