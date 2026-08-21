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
