# Lessons Learned

## Purpose
This file turns mistakes, fixes, and discoveries into reusable knowledge.

After each major task, add a short note.

## Entry Template

```text
Date:
Task:
What worked:
What was confusing:
What broke or almost broke:
What I learned:
Rule to remember next time:
```

## Entries

```text
Date: 2026-09-02
Task: Make testing the default development and local-build branch.
What worked: A project-level AGENTS.md rule, a human-facing project map, and a fail-closed branch check make the intended workflow visible and repeatable.
What was confusing: Generated client/bin output can survive a branch switch and look like source from the wrong branch.
What broke or almost broke: Building on main or detached HEAD would make local artifacts difficult to attribute to active work.
What I learned: Source branch, embedded JAR revision, and launch mode must be checked together.
Rule to remember next time: Start ordinary work on testing, keep main read-only for development, and require a MATCH status before calling a local client run current.
```

```text
Date: 2026-09-02
Task: Make branch, commit, merge, pull-request, and push/pull history descriptive.
What worked: A single naming convention in AGENTS.md, the project map, and the GitHub pull-request template makes purpose, direction, and verification visible in both local and remote history.
What was confusing: A short label such as "update" does not show which branch, client area, or outcome changed.
What broke or almost broke: An unnamed push or pull can hide the exact remote ref and commit range that changed the local client provenance.
What I learned: Timeline clarity comes from recording type, scope, outcome, source/destination, commit range, and verification together.
Rule to remember next time: Use descriptive names and bodies for every branch, commit, merge, pull request, and repository update; verify and report the exact ref after pushes.
```

```text
Date: 2026-09-02
Task: Make the personal branch selector explain remote state and long-running test operations.
What worked: Refreshing remote refs into commit/message/local-relationship records and keeping Git/Ant work in a background worker makes the selected test target and current stage visible.
What was confusing: A captured Ant command can leave the terminal blank while it is still running, and a new detached worktree normally has no ignored client/bin package for a run-only action.
What broke or almost broke: Returning launcher output through the PowerShell pipeline could be mistaken for the numeric process exit code; the selector now writes that output to the terminal explicitly and fails closed when no package exists.
What I learned: A progress bar needs a stage label, elapsed time, recent activity, selected commit, and explicit cleanup/launch plan to be trustworthy.
Rule to remember next time: Keep branch discovery repository-derived, show the exact action plan before execution, report long-running progress, and distinguish build completion from live-game verification.
```

```text
Date: 2026-08-31
Task: Make GitHub-first MoonFlower production updates an automatic project rule.
What worked: Pairing project-level AGENTS.md instructions with an executable privacy and commit-detail gate made the release process both discoverable in new chats and enforceable in GitHub Actions.
What was confusing: Editing tracked source is still required, but directly patching generated client/bin or launcher caches bypasses the versioned, verified release path.
What broke or almost broke: A release feed can exist while an unverified local package or incomplete commit history still gives a misleading impression that production is current.
What I learned: GitHub main, the clean rolling-release workflow, and the signed manifest hash form the production boundary; local generated files are diagnostic artifacts, not delivery targets.
Rule to remember next time: Audit only the intended changes, use detailed commit bodies, pass the release-policy script, push through main, wait for CI, and verify the stable manifest and updater check.
```

```text
Date: 2026-08-29
Task: Establish a unified MoonFlower UI component template while beginning the world clock redesign.
What worked: Turning the portrait HUD's existing palette, primitives, interaction rules, provenance labels, and verification boundaries into one reusable checklist made the visual direction explicit without introducing another framework.
What was confusing: A themed surface can look unified while still duplicating classic HUD information or presenting derived/community values as if they came from the server.
What broke or almost broke: Adding another large drawing path directly to GameUI would have mixed astronomy, location lookup, rendering, and guidance rules into an already central file.
What I learned: Unified UI requires shared information and verification rules as much as shared colors and artwork.
Rule to remember next time: Start substantial MoonFlower UI work from docs/templates/MOONFLOWER_UI_COMPONENT.md, reuse MoonFlowerHudTheme, preserve classic behavior, and separate LIVE, CALC, GUIDE, and LEARNED data.
```

```text
Date: 2026-08-21
Task: Diagnose the recurring frozen white client window.
What worked: Capturing the live Java thread state and comparing the process start time with the packaged JAR timestamp exposed the failure without guessing.
What was confusing: The rebuilt JAR contained every reported missing class, so the error initially looked inconsistent with the current package.
What broke or almost broke: A clean/deploy build rewrote client/bin while the visible JVM was still running; later lazy class loads failed and terminated both a loader thread and the Haven UI thread.
What I learned: A successful rebuild does not make in-place deployment safe for a running Java client, even when most already-loaded features continue working.
Rule to remember next time: Close the visible client before clean or deployment builds, and keep the build guard enabled so live client/bin files are never replaced.
```

```text
Date: 2026-08-21
Task: Recover and modernize the custom client source baseline.
What worked: Reconstructing an exact upstream base from hashes made the local patch small enough to review and apply semantically.
What was confusing: Build output, mutable SQLite data, and historical runtime evidence were mixed into the source tree without provenance.
What broke or almost broke: Upstream removed MainFrame, Ant clean could erase working-directory databases, and the MJPEG endpoint inherited a 30-second async timeout.
What I learned: Preserve a baseline first, treat toolkit seams as semantic ports, and resolve every mutable data path before clean builds or live login.
Rule to remember next time: Record base and target commits, back up runtime data, then port and verify each layer separately before an online smoke test.
```

```text
Date: 2026-06-21
Task: Reconstruct workflow documentation from the provided instruction bundle.
What worked: Converting the loose templates into a real docs structure made the workflow immediately usable.
What was confusing: Several file contents were shifted into the wrong filenames and one placeholder file had no final home.
What broke or almost broke: Copying the temp files directly would have preserved the mismatch and made the workflow harder to trust.
What I learned: It is worth normalizing project-process docs before relying on them as source of truth.
Rule to remember next time: Verify naming and placement before importing external templates into a project.
```

## Learning Prompts
Use these with an AI agent:

```text
Explain the code changed in this task as if I am a beginner.
```

```text
What programming concepts did this task use?
```

```text
What should I understand before modifying this system again?
```

```text
Show me the execution flow from user action to final result.
```
