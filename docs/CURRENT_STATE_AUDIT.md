# Current-State Audit

Audit date: 2026-08-21

## Status Matrix

| Area | Implemented | Current evidence | Remaining boundary |
| --- | --- | --- | --- |
| Hurricane client | Full visible client plus local bot-control integration | Ant `deftgt` builds on Java 23 | Source is based on Hurricane `v1.59b`; live-game compatibility is not current or proven |
| Bot control | Environment-driven login/character selection, WebSocket runtime, state collection, actions, screenshots, and remote input | Nine local `haven.botcontrol` classes and six patched client seams compile | All seams must be ported across upstream API/toolkit changes and runtime-tested |
| Shared protocol | Commands, events, state snapshots, task status, screenshots, and remote input models | Maven compilation passes | No protocol version/capability handshake exists |
| Spring server | Accounts, bots, tasks, routes, presets, audit, screenshots, clips, wiki lookup, WebSockets, process supervision | 11 tests pass; May 2026 log shows successful port-8080 startup | Current packaged startup and bot launch are not yet reverified; test coverage is narrow |
| React dashboard | Fleet controls, tasks, activity, live feed, screenshots, clips, wiki/detail views | Production build passes | ESLint reports 6 errors and 5 warnings; `App.tsx` is oversized |
| Media gateway | WebRTC stream bridge and an in-memory JPEG rolling replay buffer with MP4 clip export | Implementation exists in `media-gateway/app.py` | Python checks and current health/startup remain to be rerun; research doc is stale |
| Scripts | Build/start/stop helpers for Windows | Commands and paths are present | Ant path is hard-coded; start flow currently rebuilds every layer and needs clean verification |
| Documentation | README, operations guide, media notes, and recovery prompt | Basic paths and commands exist | Client/updater/Java details and media status are stale; provenance was absent |
| Source control | Newly initialized local repository | Pre-update baseline is being captured | No remote publication is authorized; upstream tracking must remain explicit |
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
`v1.69`. They must be ported semantically rather than copied over the newer
files.

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
- Exact live map/preference paths have not been resolved. Real login is blocked
  on that discovery and a backup.

## Immediate Priorities

1. Capture the exact pre-port source in local Git.
2. Port the reconstructed local patch from Hurricane `v1.59b` to the latest
   verified stable Hurricane release.
3. Repair compilation/API conflicts without weakening error handling.
4. Fix React lint, verify Python, and synchronize documentation.
5. Prove packaged loopback startup and clean shutdown.
6. Ask the user to supervise the first live login and control-path smoke test.
