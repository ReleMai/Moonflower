# Current-State Audit

Audit date: 2026-08-21

## Status Matrix

| Area | Implemented | Current evidence | Remaining boundary |
| --- | --- | --- | --- |
| Hurricane client | Full visible client plus local bot-control integration, ported to `v1.69` | Ant package and fetched-resource check pass | Live-game compatibility still requires supervised login |
| Bot control | Environment-driven login/character selection, WebSocket runtime, state collection, actions, screenshots, and remote input | Nine local `haven.botcontrol` classes and their upstream seams compile on `v1.69` | Online telemetry/control needs a real connected bot |
| Shared protocol | Commands, events, state snapshots, task status, screenshots, and remote input models | Maven compilation passes | No protocol version/capability handshake exists |
| Spring server | Accounts, bots, tasks, routes, presets, audit, screenshots, clips, wiki lookup, WebSockets, process supervision | 13 tests and current loopback health/auth smoke pass | Test coverage remains narrower than the feature surface |
| React dashboard | Fleet controls, tasks, activity, live feed, screenshots, clips, wiki/detail views | Production build/lint pass; npm audit reports 0 vulnerabilities | `App.tsx` remains oversized |
| Media gateway | WebRTC bridge and bounded in-memory JPEG replay with MP4 export | Python compile/import/pip checks and loopback health pass | Live video quality and multi-bot load need online verification |
| Scripts | Build/start/stop and client backup helpers for Windows | Full build and start/stop smoke pass; Ant resolution is portable | Scripts are Windows-first |
| Documentation | README, operations, data backup, media, architecture, verification, and provenance | Updated to the recovered architecture and current commands | Keep verification/provenance current on each port |
| Source control | Local Git repository with recoverable pre-port baseline | Tag `pre-revival-v1.59b-local`; port commit `a1bfde0`; upstream remotes recorded | No remote publication is authorized |
| Legacy artifacts | Preserved launcher/runtime evidence | Stored under `artifacts/` and ignored by Git | `autohaven-socrates556.jar` is compromised and must never be operationally reused |

## Reconstructed Client Boundary

The local client matches Hurricane `v1.59b` closely. A physical-file comparison
of Java sources plus key build/launcher files found 816 exact matches. Local
custom work consists of nine new `src/haven/botcontrol/*.java` files and changes
to:

- `src/haven/Charlist.java`
- `src/haven/GameUI.java`
- `src/haven/IMeter.java`
- `src/haven/LoginScreen.java`
- `src/haven/Resource.java`
- `src/haven/automated/FishingBot.java`
- `Play_Linux.sh`

All six modified Java touchpoints changed again between Hurricane `v1.59b` and
`v1.69`. The local patch was therefore applied semantically on the new target;
the toolkit-based remote-input code was adapted to the upstream removal of
`MainFrame`.

## Verified Baseline

| Command | Result |
| --- | --- |
| `mvn test` | PASS: 11 tests, 0 failures/errors |
| `web/npm run build` | PASS |
| `web/npm run lint` | FAIL: 6 errors, 5 warnings |
| `client/ant deftgt` | PASS, with failed Git revision metadata because the workspace previously had no `.git` |

The historical `server-run.log` proves only that the packaged server started in
May 2026. Repeated `AsyncRequestTimeoutException` warnings occurred during that
run. No current client login or live-game protocol assertion is inferred from
these build results.

## Security And Data Findings

- The server binds to `127.0.0.1` by default, which is the required recovery
  posture.
- Default operator credentials are `admin/changeme`; meaningful runtime testing
  must override them.
- Account secrets use a DPAPI-backed protection service on Windows.
- Bot credentials and launch tokens pass through environment variables. Commands
  and logs must not print their values.
- Generated outputs, dependencies, runtime logs, and all `artifacts/` content are
  excluded from the new Git baseline.
- Preferences and hash-addressed map/cache data live under
  `%APPDATA%\Haven and Hearth`; custom SQLite data now lives in its `Hurricane`
  subfolder rather than a rebuildable package directory.
- Client and server data snapshots were copied and verified by file count and
  byte count before runtime smoke testing.

## Remaining Verification Boundary

With the user present, launch one visible client and verify real-account login,
character selection, world entry, resource loading, map continuity, telemetry,
screenshot/live feed, a harmless takeover input, pause/resume, and clean logout.
No build-only result is represented as proof of that online behavior.
