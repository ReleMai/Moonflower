# Current Tasks

## Active Task: Supervised Live Compatibility

Use the completed Hurricane `v1.69` port in one visible, user-supervised session
to prove live game login and the existing telemetry/control path.

Why this task matters:

- Source/build compatibility is proven, but only the game server can prove the
  current wire protocol and resource behavior.
- The session touches a real account and therefore requires the user present.

Likely evidence locations are the visible client, dashboard, bot process log,
server log, gateway log, and `docs/VERIFICATION.md`. Make compatibility repairs
only when the session exposes a concrete failure.

Acceptance criteria:

1. Client reaches the current login and character screens without resource or
   protocol errors.
2. The selected character enters the world and prior settings/map data remain
   available.
3. The dashboard receives telemetry and a screenshot/live frame.
4. One harmless takeover input and pause/resume round trip succeed visibly.
5. Client and platform shut down cleanly without leaking credentials to logs.

## Scope Boundaries

- Do not add new automation features during compatibility verification.
- Do not replace local integration files blindly with upstream versions.
- Do not execute or inspect credentials from compromised legacy artifacts.
- Do not expose services beyond loopback.
- Do not log, echo, or commit credentials/session material.

## Verification Commands

```powershell
.\scripts\start-platform.ps1 -SkipBuild -NoBrowser
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8091/health
# Launch the configured visible bot with the user present, then follow the
# acceptance checklist above.
.\scripts\stop-platform.ps1
```

## Next Slice After Completion

Repair only concrete issues found by the live session, then add focused
regression coverage for each repair.
