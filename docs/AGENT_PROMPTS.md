# Agent Prompts

Use these prompts when you want structured help from Codex while keeping work inside the project workflow.

## Start A Task

```text
Read AGENTS.md and docs/CURRENT_TASKS.md first. Then inspect the relevant files, identify the smallest safe change, implement it, verify it, and explain the result in beginner-friendly language.
```

## Scope A New Task

```text
Help me turn this idea into one small active task. Update docs/CURRENT_TASKS.md with scope, likely files, acceptance criteria, and verification steps.
```

## Review A Change

```text
Review this change for correctness, regressions, file organization, and technical debt. Prioritize bugs and missing verification over style comments.
```

## Explain The Code

```text
Explain the files changed in plain language. Tell me what each file does, how the execution flows, and what I should understand before editing it myself.
```

## Record A Lesson

```text
Summarize what we learned from this task and add a short entry to docs/LESSONS_LEARNED.md.
```

## Record Debt

```text
If this change takes a shortcut, add a concise entry to docs/TECHNICAL_DEBT.md with the tradeoff and the better long-term replacement.
```
