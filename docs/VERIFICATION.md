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
| Hurricane source port | PASS | `v1.59b` local patch reconstructed and applied to `v1.69` (`045b1f598a...`) |
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

## Repeatable Commands

```powershell
mvn test
Push-Location web; npm run lint; npm run build; npm audit --audit-level=moderate; Pop-Location
Push-Location client; ant deftgt; java -cp bin/hafen.jar haven.Resource find-updates; Pop-Location
Push-Location media-gateway; .\.venv\Scripts\python.exe -m compileall -q app.py; .\.venv\Scripts\python.exe -m pip check; Pop-Location
.\scripts\build-all.ps1
.\scripts\start-platform.ps1 -SkipBuild -NoBrowser
.\scripts\stop-platform.ps1
```
