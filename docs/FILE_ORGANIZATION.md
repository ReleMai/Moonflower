# File Organization Guide

## Goal
The project should be easy to navigate. A new person should be able to guess where a feature belongs.

## General Principles
- Group files by responsibility or feature area.
- Keep unrelated systems separate.
- Avoid dumping everything into one folder.
- Avoid one file controlling the entire project.
- Prefer clear names over short names.

## Suggested Structure

```text
project-root/
├── docs/
│   ├── CODING_STANDARDS.md
│   ├── FILE_ORGANIZATION.md
│   ├── ROADMAP.md
│   ├── CURRENT_TASKS.md
│   ├── FEATURE_BACKLOG.md
│   ├── LESSONS_LEARNED.md
│   ├── TECHNICAL_DEBT.md
│   ├── AGENT_PROMPTS.md
│   └── templates/
├── src/
│   ├── core/
│   ├── features/
│   ├── data/
│   ├── ui/
│   ├── services/
│   └── utilities/
├── tests/
├── assets/
├── tools/
├── work/
└── outputs/
```

## Folder Meanings

### `docs/`
Project knowledge, plans, standards, and learning notes.

MoonFlower interface work begins with
`docs/templates/MOONFLOWER_UI_COMPONENT.md`. Keep the completed task brief with
the task's design/research notes when it contains decisions worth preserving;
do not place runtime assets or Java source in `docs/templates/`.

### `src/core/`
Foundational logic used across the project.

### `src/features/`
Specific user-facing features or application systems.

### `src/data/`
Data models, configuration, schemas, and persistent structures.

### `src/ui/`
Interface-related files.

### `src/services/`
Reusable service-style logic such as saving, loading, networking, persistence, or external integrations.

### `src/utilities/`
Small helper functions or shared tools.

### `tests/`
Automated or manual test files.

### `assets/`
Images, sounds, text files, design assets, or other non-code resources.

### `tools/`
Project-specific scripts or development utilities.

## Splitting Files
Split a file when:
- It handles multiple unrelated responsibilities.
- It becomes difficult to explain in one sentence.
- It changes for many different reasons.
- It is too large to review comfortably.
- New features keep getting added to the same file.

## Naming Rules
Names should clearly describe purpose.

Prefer:
- `SaveService`
- `InventoryItem`
- `DialogueState`
- `UserProfileRepository`

Avoid:
- `Manager2`
- `Stuff`
- `HelperBig`
- `NewScript`
- `MainEverything`
