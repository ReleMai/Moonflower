# Verification Log

Use this file for reproducible recovery evidence. Do not record credentials,
tokens, cookies, account names, character names, or other session material.

## Pre-Port Baseline — 2026-08-21

| Check | Result | Notes |
| --- | --- | --- |
| `mvn test` | PASS | 11 tests |
| `web/npm run build` | PASS | Vite production bundle generated |
| `web/npm run lint` | FAIL | 6 errors and 5 warnings |
| `client/ant deftgt` | PASS WITH WARNING | Git revision lookup failed because no repository existed |
| Current packaged startup | NOT RUN | Historical May log is not current proof |
| Current live-game login | NOT RUN | Requires data backup and user supervision |

## Post-Port Results

Pending.
