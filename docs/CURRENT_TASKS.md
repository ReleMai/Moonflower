# Current Tasks

## Active Task: Client Revival And Upstream Port

Restore a reproducible source baseline, port the local Hurricane `v1.59b`
customizations to the latest verified stable Hurricane release, and prove the
local platform can build and start without losing the custom control protocol.

Why this task matters:

- The workspace has no Git history and currently emits invalid build metadata.
- The client is several Hurricane releases behind the live game client.
- The custom client/server seam crosses files that changed upstream.
- Compilation currently passes, but lint and current end-to-end runtime proof do not.

Files likely involved:

- `.gitignore`, `AGENTS.md`, `README.md`
- `client/`, especially `client/src/haven/botcontrol/` and its six Java touchpoints
- `shared-protocol/`, `server/`, `web/`, `media-gateway/`, `scripts/`
- `docs/`

Acceptance criteria:

1. The pre-update state is recoverable in local version control and no secrets or
   compromised legacy artifacts are committed.
2. Exact upstream/base/target commits are recorded.
3. Local client customizations are ported onto the latest verified stable base.
4. Maven tests, client build, React build/lint, and Python checks pass.
5. The packaged server and media gateway start and report healthy on loopback.
6. Documentation reflects the implemented architecture and repeatable commands.
7. Real-account login remains an explicit supervised verification boundary.

## Scope Boundaries

- Do not add new automation features during the compatibility port.
- Do not replace local integration files blindly with upstream versions.
- Do not execute or inspect credentials from compromised legacy artifacts.
- Do not expose services beyond loopback.
- Do not allow the updated client to rewrite map or preference data before the
  exact locations are identified and backed up.

## Verification Commands

```powershell
mvn test
Push-Location web; npm run build; npm run lint; Pop-Location
Push-Location client; C:\apache-ant\bin\ant.bat clean deftgt; Pop-Location
python -m compileall media-gateway
.\scripts\start-platform.ps1 -SkipBuild -NoBrowser
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8091/health
.\scripts\stop-platform.ps1
```

## Next Slice After Completion

With the user present, back up discovered live client data and perform one
visible real-account login followed by a supervised telemetry/screenshot/control
smoke test.
