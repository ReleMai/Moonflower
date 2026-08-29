# Roadmap

## Purpose
This file prevents the project from becoming overwhelming.

The roadmap should describe the project in small milestones. Each milestone should create a working improvement, not a giant unfinished system.

## Roadmap Rules
- Keep milestones small.
- Each milestone should be testable.
- Do not work on future milestones until the current one is stable.
- Ideas belong in `FEATURE_BACKLOG.md`, not directly in the roadmap.

## Current Focus

Milestone 1 is complete. The Cookbook slice is offline-verified and has initial
live capture evidence. The active controlled-expansion slice is the first
MoonFlower World Clock implementation: unified portrait-HUD theming, live
astronomy and area context, a game-time season countdown, and sourced contextual
notices. Fishing-helper and combat refinements remain pending supervised live
validation.

## Milestones

### Milestone 1: Recoverable Current Client

Status: complete on 2026-08-21; online game compatibility is Milestone 2.

Goal: Preserve the custom control platform while updating the client to the
latest verified compatible official-client source.

Acceptance criteria:
- Pre-update source is recoverable locally.
- Upstream provenance and the custom patch boundary are documented.
- Clean builds and static checks pass.
- Packaged local services report healthy.

### Milestone 2: Supervised Live Compatibility

Status: active; requires the user present for real-account login.

Goal: Prove a visible client can safely log into the current game and exercise
the existing control/telemetry path.

Acceptance criteria:
- Map/preferences data is backed up before migration.
- Login, character selection, world entry, resources, and telemetry work.
- Screenshot, takeover, remote input, pause/resume/abort, and safe logout work.

### Milestone 3: Reliability And Recovery

Goal: Harden reconnection, process supervision, persistence, backup, and error
reporting based on observed live failures.

Acceptance criteria:
- Data is stored clearly.
- Data can be loaded or reused.
- Failure cases are documented.

### Milestone 4: Maintainability

Goal: Reduce oversized UI/service seams and expand focused integration tests
without changing behavior.

Acceptance criteria:
- Interactions are understandable.
- Logic is separated from presentation where possible.

### Milestone 5: Controlled Expansion

Goal: Consider new operator features only after compatibility and reliability
are stable.

Acceptance criteria:
- New systems fit the architecture.
- No major god files are created.
- Technical debt is recorded.
