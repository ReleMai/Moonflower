# Haven & Hearth Client Revival Prompt

Use this prompt from the workspace root:

`D:\Codex Project\Haven and Hearth Custom Client`

---

You are taking ownership of restoring, updating, and improving this Haven & Hearth custom-client workspace. Work as a hands-on senior maintainer: inspect the real source, logs, build outputs, upstream repositories, and runtime behavior; do not guess from filenames or old documentation. The goal is a maintainable client and control platform that builds, starts, and works against the current game version without losing the project's custom bot-control features.

## Known starting point

Treat these as verified findings from 2026-08-21, but re-check anything that may have changed:

- This is a monorepo-like workspace containing:
  - `client/`: Hurricane-derived Java client.
  - `shared-protocol/`: shared Java command/event models.
  - `server/`: Spring Boot operator/control server with SQLite persistence.
  - `web/`: React/Vite operator dashboard.
  - `media-gateway/`: Python WebRTC and rolling-replay service.
  - `references/webhaven/`: reference code only.
  - `artifacts/legacy-launcher/`: preserved legacy artifacts only.
- The workspace currently has no `.git` directory. The Ant build consequently writes a failed `git rev-parse` message into `client/build/classes/buildinfo`.
- The local client is based very closely on Hurricane `v1.59b` (`ef9fe0dd8`): 816 checked source/key files matched that tag exactly.
- Local-only control integration is concentrated in these nine files:
  - `client/src/haven/botcontrol/BotAction.java`
  - `client/src/haven/botcontrol/BotActionContext.java`
  - `client/src/haven/botcontrol/BotActionRegistry.java`
  - `client/src/haven/botcontrol/BotAgentRuntime.java`
  - `client/src/haven/botcontrol/BotLaunchConfig.java`
  - `client/src/haven/botcontrol/ClientStateCollector.java`
  - `client/src/haven/botcontrol/IconPackExporter.java`
  - `client/src/haven/botcontrol/RemoteInputExecutor.java`
  - `client/src/haven/botcontrol/ScreenshotCaptureService.java`
- The local snapshot modifies seven Hurricane `v1.59b` files:
  - `client/src/haven/Charlist.java`
  - `client/src/haven/GameUI.java`
  - `client/src/haven/IMeter.java`
  - `client/src/haven/LoginScreen.java`
  - `client/src/haven/Resource.java`
  - `client/src/haven/automated/FishingBot.java`
  - `client/Play_Linux.sh`
- Every one of the six modified Java touchpoints was also changed upstream after `v1.59b`, so merge them deliberately. Do not copy old files wholesale over current upstream.
- At the time of the audit, current Hurricane was `v1.69`, tag/commit `045b1f598a9009b279b778eeb2256c651baf88f8`, released 2026-08-20. It includes a merge of Loftar's latest code. The official Seatribe client repository HEAD was `f4b86b85514ef3d77d95b08be0bea46179ef0b3a`. Re-check both before claiming the client is current.
- Hurricane `v1.59b...v1.69` spans 533 commits and 982 changed files. It includes major client I/O/toolkit work, resource-code changes, Java compatibility changes, UI fixes, and map/client compatibility changes.
- Current verification baseline:
  - `mvn test`: passes 11 tests.
  - `web/npm run build`: passes.
  - `web/npm run lint`: fails with 6 errors and 5 warnings, mainly React effect/state and dependency issues in `App.tsx` and `useOperatorSocket.ts`.
  - `client/ant deftgt`: builds successfully, apart from the missing-Git build-info problem.
  - Old server logs show the packaged server previously started successfully on port 8080, but also show repeated `AsyncRequestTimeoutException` warnings. Current end-to-end startup has not yet been proven.
- Documentation is stale in places. For example, the local client README still describes Java 15-21 and the Primitive Updater, while current Hurricane documents Java 17-25 and the Hurricane Updater. `docs/MEDIA_RESEARCH.md` describes rolling replay as future work even though `media-gateway/app.py` already implements a replay buffer and clip saving.
- `artifacts/legacy-launcher/autohaven-socrates556.jar` is documented as containing compromised embedded login material. Never execute it, extract credentials from it, copy its secrets, or use it as an operational artifact.

## Required working approach

1. Inspect before editing. Read all applicable `AGENTS.md` instructions, inventory the workspace, identify generated/vendor/runtime files, and inspect existing logs and documentation.
2. Establish a recoverable local version-control baseline before source changes. Do not publish or push anything. Preserve the exact pre-update tree and make the upstream relationship explicit.
3. Use primary upstream sources:
   - Hurricane: `https://github.com/Nightdawg/Hurricane.git`
   - Official client: `git://sh.seatribe.se/hafen-client`
   - Official game/client documentation and patch notes where relevant.
4. Reconstruct the update as a three-way port:
   - Base: Hurricane `v1.59b`.
   - Local side: the current client customizations.
   - Upstream side: the latest verified stable Hurricane release, currently `v1.69` at this prompt's creation.
5. Preserve local behavior intentionally. Reapply the nine `botcontrol` files and the seven modified-file changes onto current upstream APIs. Adapt the integration to upstream architectural changes rather than reverting upstream files to old versions.
6. Keep changes small and reviewable. Separate upstream synchronization, custom-integration repairs, platform fixes, and optional improvements into distinct commits or clearly documented stages.
7. Never expose credentials, account secrets, launch tokens, cookies, Steam tickets, or session material in commands, logs, diffs, screenshots, or reports. Keep the server loopback-only by default and replace the default `admin/changeme` credentials before meaningful runtime testing.
8. Preserve user data. Before launching an updated client, discover the exact locations of map caches, preferences, routes, presets, screenshots, clips, databases, and account configuration. Back up migration-sensitive data, especially map data, before allowing a newer client to rewrite it.
9. Do not add anti-cheat bypasses, detection evasion, credential scraping, or other stealth behavior. Review the current game's rules before enabling unattended automation. Begin live validation with a visible client and low-risk manual actions.
10. Preserve LGPL notices and all other applicable licensing/copyright requirements.

## Work phases

### Phase 1: Current-state audit

- Produce a concise status matrix for the client, shared protocol, server, web dashboard, media gateway, scripts, documentation, tests, runtime artifacts, and reference code.
- Distinguish clearly among implemented, compiled, tested, previously observed, currently verified, incomplete, stale, and unknown.
- Trace the actual start flow from `scripts/start-platform.ps1` through the server, web assets, media gateway, bot process supervisor, client launcher, login, WebSocket registration, and dashboard state.
- Identify duplicated, oversized, or generated content that should not be source-controlled. Note that `web/node_modules`, Python `.venv`, Java targets/builds, client binaries, and legacy release bundles are already present locally.
- Document findings before performing the upstream port.

### Phase 2: Upstream synchronization

- Fetch the latest Hurricane release/tag and the current official Seatribe client source. Record exact URLs, refs, commit hashes, and timestamps.
- Confirm whether the latest Hurricane release has incorporated the official client HEAD. If not, explain the gap before choosing a target.
- Compare `v1.59b` to the chosen target and identify protocol, resource, launcher, Java-version, map-format, rendering, input, audio, login, and widget changes relevant to the local integration.
- Port local customizations using the reconstructed `v1.59b` base. Pay special attention to:
  - login/autologin in `LoginScreen.java`;
  - character selection in `Charlist.java`;
  - runtime attach/detach in `GameUI.java`;
  - health telemetry in `IMeter.java`;
  - local resource resolution in `Resource.java`;
  - the local `FishingBot.java` changes;
  - toolkit/input changes affecting `RemoteInputExecutor` and screenshots;
  - state-collector assumptions about widgets, inventories, meters, quests, positions, and equipment.
- Run the client's resource update checker and resolve fetched-resource source/version changes. Do not silence resource or protocol failures with generic fallbacks.
- Update launcher scripts, Java compatibility notes, updater references, version metadata, and build-info generation.

### Phase 3: Platform repair and improvement

- Fix the React lint failures properly; do not merely disable the rules globally.
- Investigate the repeated async timeout warnings and determine whether they are expected long-poll/media behavior or a real lifecycle bug.
- Reconcile documentation with the implemented rolling-replay/media behavior.
- Audit dependency versions and security advisories across Maven, npm, and Python. Upgrade only where useful and compatible, documenting material migrations.
- Review default credentials, token handling, DPAPI secret protection, WebSocket authentication, path validation, process launching, screenshot/clip storage, and error reporting.
- Split oversized or mixed-responsibility files only where it directly improves the work being done; avoid an unrelated rewrite.
- Add focused tests around the fragile custom seams, especially bot registration, command/event compatibility, login configuration parsing, state collection, task lifecycle, reconnection, and input/screenshot routing.

### Phase 4: Build and end-to-end startup

Use exact commands appropriate to the repaired workspace and record results. At minimum verify:

- Clean Java/Maven build and all tests.
- React production build and lint with zero errors.
- Python syntax/import/dependency setup and media-gateway health.
- Clean Ant client build from source, including current resources and meaningful build metadata.
- `scripts/start-platform.ps1 -NoBrowser` starts the packaged server and media gateway.
- `http://127.0.0.1:8080/api/health` and `http://127.0.0.1:8091/health` report healthy.
- The dashboard loads, authenticates using non-default test credentials, and receives operator WebSocket updates.
- A visible client can be launched from the built `client/bin` with no secret printed to logs.
- With user confirmation before the first real login: verify official authentication, character selection, world entry, resource loading, map-cache safety, bot WebSocket registration, heartbeat/state telemetry, screenshots/live feed, begin/end takeover, remote input, pause/resume/abort, and safe logout.
- Exercise one low-risk task end to end while visibly supervised. Do not claim unattended automation works based only on compilation or mock tests.
- Stop all processes cleanly and confirm no orphaned Java, Node, or Python processes remain.

## Deliverables

- Updated working source that preserves the custom control integration while matching the latest verified compatible game/client version.
- A current-state audit and an upstream/base provenance record.
- Updated setup, build, start, stop, troubleshooting, backup, and upgrade documentation.
- A short architecture map showing the client/server/protocol/web/media boundaries.
- A prioritized list of remaining defects, risks, technical debt, and optional improvements.
- A final verification report listing every command run, pass/fail result, runtime evidence, exact upstream commits used, and anything not tested.

## Definition of done

Do not call the project complete merely because it compiles. It is complete for this recovery slice only when the upstream provenance is reproducible, local customizations are preserved, required builds and tests pass, lint passes, the platform starts cleanly, health checks succeed, and—with explicit user confirmation—the updated visible client completes a real login and demonstrates the core control/telemetry path. If live authentication cannot be performed, report that as the remaining boundary rather than guessing.

Start by presenting the audit and proposed update sequence. Then continue through the safe, authorized implementation work without repeatedly asking for routine decisions. Pause only for the first real-account login, destructive/migration-sensitive data changes, exposure beyond loopback, or another choice that materially changes scope.
