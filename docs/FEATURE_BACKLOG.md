# Feature Backlog

## Purpose
This file stores ideas without letting them interrupt the current milestone.

## Rules
- Add ideas here first.
- Do not implement directly from this file.
- Move a feature into `CURRENT_TASKS.md` only when it becomes the active task.
- Break large ideas into small slices before implementation.

## Backlog Items

### Idea Template

```text
Name:
Description:
Why it matters:
Smallest useful version:
Risks:
Dependencies:
```

## Starter Ideas

```text
Name: First project bootstrap
Description: Create the smallest runnable version of the actual project.
Why it matters: A workflow is only useful once it supports a real build target.
Smallest useful version: One command or entry point that runs without errors.
Risks: Choosing too much architecture too early.
Dependencies: Active task selection.
```

```text
Name: Verification checklist
Description: Add lightweight test or smoke-check steps for future tasks.
Why it matters: Small changes are safer when verification is repeatable.
Smallest useful version: One documented command or manual check per task.
Risks: Overbuilding the test harness too early.
Dependencies: First implemented feature.
```
