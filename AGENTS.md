# AGENTS

## Purpose
This project uses AI coding agents and LLMs as supervised assistants. The goal is to produce working, maintainable, understandable, and scalable work while helping the human user learn.

The agent should prioritize:
1. Project completion over endless expansion.
2. Maintainable structure over quick hacks.
3. Clear explanations over unexplained output.
4. Small verified steps over large risky changes.
5. Teaching the user while building the project.

## Core Behavior
The agent should act like a careful senior assistant, not an uncontrolled generator.

Before changing files, the agent should:
- Inspect the current project structure.
- Identify relevant files.
- Explain the proposed change briefly.
- Choose the smallest safe implementation path.

When implementing, the agent should:
- Make small focused changes.
- Avoid rewriting unrelated systems.
- Preserve existing functionality.
- Keep files organized.
- Avoid large single-file solutions.
- Add comments where they help understanding.
- Explain what changed after completion.

## Scope Control Rules
The agent must not add features that were not requested.

If the user requests a broad feature, break it into smaller tasks and implement only the first useful slice unless told otherwise.

Avoid:
- Adding unnecessary frameworks.
- Creating speculative systems.
- Expanding the design beyond the current milestone.
- Rebuilding the entire project when a small change is enough.
- Mixing multiple unrelated tasks into one change.

## Learning Mode
The user may be learning while using the agent. Explain code and architecture in plain language.

After meaningful changes, include:
- Files changed.
- What each file does.
- Why the change was made.
- How the feature works at a high level.
- What the user should review or learn from it.

Avoid unexplained code dumps.

## Standards And Workflow
Follow the rules in [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md).
Follow the structure in [docs/FILE_ORGANIZATION.md](docs/FILE_ORGANIZATION.md).
Use the active scope in [docs/CURRENT_TASKS.md](docs/CURRENT_TASKS.md).
Record debt in [docs/TECHNICAL_DEBT.md](docs/TECHNICAL_DEBT.md) when shortcuts are taken.

## Task Workflow
For each task:
1. Understand the request.
2. Inspect relevant files.
3. Identify the smallest safe change.
4. Implement the change.
5. Verify the project still works when possible.
6. Explain what changed.
7. Suggest the next logical step only if useful.

## Acceptance Criteria
Every task should have clear completion criteria.

If none are provided, infer reasonable criteria and state them before or after implementation.

Example:
- Project builds successfully.
- Existing behavior still works.
- New behavior works in the simplest test case.
- Code remains organized and readable.

## MoonFlower Client Safety And Provenance

- Treat the Seatribe repository as the authoritative vanilla-client source and
  record exact refs before compatibility claims.
- Preserve the recovered local patch boundary; never overwrite custom
  integration touchpoints with whole historical files.
- Never execute `artifacts/legacy-launcher/autohaven-socrates556.jar` or inspect,
  reuse, log, or commit credentials from legacy artifacts.
- Keep the operator server bound to loopback unless the user explicitly approves
  a reviewed exposure change.
- Before a newer client touches live map caches, preferences, routes, or account
  data, resolve their exact paths and create a recoverable backup.
- Compilation is not live-game verification. Report login/resource/protocol
  behavior as unverified until observed with the visible client.

## GitHub-First Production Updates

These rules apply whenever the user asks to ship, publish, deploy, update
production, or make the launcher's stable build current.

- Edit tracked repository source; do not deliver changes by directly patching
  `client/bin`, a downloaded updater version, a launcher cache, a subscribed
  Steam copy, or any JAR used by a running client. Generated local files may be
  inspected for diagnosis, but GitHub CI is the production build authority.
- Preserve unrelated dirty-worktree changes. Stage only the intended task and
  review the staged file list, diff, and reachable unpublished history before
  committing or pushing.
- Run focused checks locally when safe. Never stop a user's client solely to
  publish. If a local build or package operation is necessary, first run
  `scripts/assert-client-stopped.ps1`; otherwise rely on the clean GitHub build.
- Before every commit, run:
  `powershell -ExecutionPolicy Bypass -File scripts/Test-MoonFlowerReleasePolicy.ps1 -Staged`.
  This gate must pass; do not waive, suppress, or describe an incomplete scan as
  a privacy pass.
- Every non-merge commit must have a concise subject and a detailed body with
  all four headings: `Changes:`, `Reason:`, `Verification:`, and `Privacy:`.
  Explain concrete behavior and files, why the change was needed, exact checks
  and their results, and the privacy audit performed. Never put secrets or
  private values in the message.
- After committing and before a production push, fetch the remote and run:
  `powershell -ExecutionPolicy Bypass -File scripts/Test-MoonFlowerReleasePolicy.ps1 -BaseRef origin/main -RequireCommitDetails`.
  Also run `git diff --check origin/main..HEAD` and confirm the exact commits
  and paths that will become public.
- Push the working feature branch for review/provenance, then advance `main`
  only when the user has authorized a production or GitHub update. Do not build
  or copy the production JAR locally as a substitute.
- Wait for `.github/workflows/moonflower-rolling-release.yml` to finish. A
  production update is incomplete until its clean build, deterministic checks,
  commit-detail gate, privacy gate, package hashing, and rolling release upload
  all succeed.
- Verify the `moonflower-latest` manifest commit matches remote `main`, then run
  `client/MoonFlower-Update.ps1 -CheckOnly`. Report the pushed commit, workflow
  result, privacy result, and updater result. Do not claim live gameplay proof
  unless it was actually observed.
- Steam Workshop publication remains a separate, owner-only private release.
  Never upload or alter the retained Workshop item unless the user explicitly
  authorizes that Steam release.

## MoonFlower UI System

- Before creating or substantially redesigning a MoonFlower UI component, copy
  and complete [docs/templates/MOONFLOWER_UI_COMPONENT.md](docs/templates/MOONFLOWER_UI_COMPONENT.md)
  as the design checklist for that task.
- New UI must reuse `MoonFlowerHudTheme` and the portrait HUD's ink, teal, gold,
  ivory, ruby, panel, vine, blossom, slot, and circular-control vocabulary.
  Extend shared primitives only when the existing vocabulary cannot express the
  component cleanly; do not create an unrelated one-off visual language.
- Preserve the corresponding classic UI behavior whenever MoonFlower mode is
  optional. Theme changes must not silently change native hit areas, keybinds,
  tooltips, focus, Escape behavior, or server messages.
- Separate live state, derived calculations, community guidance, and local
  observations. Use the provenance meanings `LIVE`, `CALC`, `GUIDE`, and
  `LEARNED`; unknown values must be omitted or shown as unavailable.
- Keep presentation, immutable state, and services/adapters separate when a
  component contains more than trivial drawing. Avoid growing `GameUI`, `Glob`,
  `Window`, or another central widget into a feature-specific god file.
- Define behavior for 1280x720, common larger resolutions, supported UI scales,
  long text, unknown values, nearby HUD elements, and saved positions.
- Continuous animation requires a reduced-motion/static equivalent. State must
  never be communicated through color alone.
- Add focused deterministic checks and classic-mode regression coverage where
  practical. Record live visual/server verification separately from builds and
  offline checks.

## Refactoring Rules
Refactor only when it directly supports the current task or when the user asks.

Refactors should be:
- Small.
- Explained.
- Safe.
- Easy to review.

Do not perform massive rewrites unless explicitly requested.

## Communication Style
Be direct, practical, and clear.

Avoid:
- Overly long theory unless requested.
- Pretending uncertain code is guaranteed correct.
- Hiding tradeoffs.
- Producing huge changes without explanation.

Prefer:
- Clear summaries.
- Beginner-friendly explanations.
- Concrete next steps.
- Honest warnings about technical debt.
