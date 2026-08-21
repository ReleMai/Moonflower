# Coding Standards

## Goal
Code should be readable, maintainable, testable, and understandable by a beginner reviewing the project later.

## General Rules
- Prefer clarity over cleverness.
- Use descriptive names for files, classes, methods, and variables.
- Keep each file focused on one main responsibility.
- Avoid large files that mix unrelated systems.
- Avoid duplicate logic.
- Avoid hidden side effects.
- Do not introduce unnecessary dependencies.
- Do not rewrite working systems unless the task requires it.

## File Size Guidance
These are guidelines, not hard laws:

- 0-150 lines: ideal for many files.
- 150-300 lines: acceptable if focused.
- 300-500 lines: review for possible splitting.
- 500+ lines: likely too large unless justified.

## Comments
Use comments to explain why something exists, not obvious behavior.

Good comments explain:
- Important decisions.
- Complex logic.
- Non-obvious tradeoffs.
- Temporary limitations.
- Future refactor notes.

Avoid comments like:

```text
// Add one to count
```

Prefer comments like:

```text
// This counter is reset per session so saved data does not preserve temporary state.
```

## Documentation
Public or important methods should include short summaries when useful.

Each major system should have a plain-language explanation in the relevant project documentation.

## Error Handling
- Handle predictable errors clearly.
- Avoid silent failures.
- Prefer useful error messages.
- Do not hide exceptions without explaining why.

## Testing And Verification
When possible, every change should include a simple way to verify it.

Examples:
- Build succeeds.
- A command runs successfully.
- A screen opens without errors.
- A save/load cycle works.
- A unit test passes.

## Technical Debt Rules
Technical debt is allowed only when documented.

When taking a shortcut, write down:
- What shortcut was taken.
- Why it was acceptable now.
- What should replace it later.
- Where the issue lives.

Record debt in `docs/TECHNICAL_DEBT.md`.
