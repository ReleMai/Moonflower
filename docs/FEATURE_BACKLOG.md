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

## MoonFlower Client Ideas

### Beehive Pyre Marker And Completion Alert

```text
Name: Beehive pyre marker and completion alert
Description: When the client observes the player build or finish placing a
Beehive Pyre, record its world location, add a distinct map marker, and begin a
local completion timer. Notify the player when the expected smoking cycle has
finished. This is background client behavior and does not need a dedicated
button or control panel.
Why it matters: Beehive Pyres are easy to forget, especially when several are
spread across an area. Automatic markers and alerts remove manual timekeeping
without automating gameplay.
Smallest useful version: Detect the relevant placed-object resource, create one
deduplicated local marker, start a session timer using a configurable or
verified duration, and show an existing-style notification when it expires.
Risks: The exact resource identity and authoritative completion duration must be
verified in the live client. Reconnects, map-grid changes, object removal,
rebuilding at the same location, clock changes, and duplicate placement events
could create stale markers or alerts. The feature must never claim completion
from elapsed time if the server exposes a state that contradicts it.
Dependencies: Confirmed Beehive Pyre resource/state evidence; existing map
marker persistence; a small reusable local timer/notification service.
Acceptance notes: No new always-visible UI and no gameplay commands. Timers
survive a normal client restart if their marker still exists, can expire while
the player is elsewhere, and cleanly discard confirmed-destroyed pyres.
```

### Multi-Account Session Supervisor And Mini-Viewer

```text
Name: Multi-account session supervisor and movable mini-viewer
Description: Let one MoonFlower operator window securely launch and supervise
multiple isolated game sessions. A small movable viewer shows another session's
rendered view and basic status, and lets the player bring that session forward.
Reuse the existing saved-account experience, process supervisor, and local live
feed where possible instead of placing multiple protocol sessions inside one
Java UI/session.
Why it matters: Players with several permitted accounts could monitor them from
one organized workspace, and the same session model could later support
explicitly designed automation tools.
Smallest useful version: Launch a second isolated MoonFlower process from a
saved account entry, display its read-only low-frame-rate preview and
online/disconnected status in one draggable mini-viewer, then focus its full
window on request. Do not add remote input or unattended automation in the first
slice.
Risks: This is a major security, memory, GPU, lifecycle, and account-isolation
feature despite its compact UI. Credentials must never be copied into logs,
screenshots, IPC messages, project files, or distributable packages. One JVM
hosting multiple game sessions would risk global-state, cache, audio, rendering,
and preference collisions. Any later bot control must remain explicit,
auditable, pausable, and compliant with the game's rules.
Dependencies: Existing encrypted/local credential handling, saved-account UI,
one-process-per-session launcher, authenticated loopback telemetry/live feed,
process health supervision, and measured multi-client resource use.
Acceptance notes: Each account has an isolated process and data boundary; closing
one session cannot terminate or corrupt the others; previews cannot expose login
screens or credential fields; no credentials are added to the client package.
```

### Botanical Clock And Location Information Hub

Status: First implementation slice selected on 2026-08-29. See
`docs/CLOCK_CALENDAR_RESEARCH.md` and `docs/CLOCK_CALENDAR_UI_BRIEF.md`; expanded
almanac, weather labels, and learned fishing forecasts remain future slices.

```text
Name: Botanical clock and location information hub
Description: Replace the small top clock presentation with a MoonFlower-themed
information hub matching the portrait UI. Its primary face shows game time,
time-of-day phase, date/season where authoritative data exists, current weather,
and a few concise environmental facts. A button flips the same surface to a
location face showing region, province/realm, current biome or terrain, and a
players-online value when a trustworthy source is available.
Why it matters: It turns scattered world context into a readable, cohesive HUD
element while extending the established MoonFlower visual language.
Smallest useful version: A movable or safely anchored clock face with game time,
day phase, current terrain/biome, and a flip button. Preserve the existing clock
behavior and defer fields for which the client has no authoritative data.
Risks: "Region," "province," weather, date, and online-player count may not all
exist in the protocol or may mean different things. Do not invent values, scrape
private data, or label inferred information as authoritative. The expanded panel
must not cover buffs, minimap controls, or other top-HUD elements at common UI
scales.
Dependencies: Existing clock widget and MoonFlower portrait/theme primitives;
verified client sources for astronomical time, weather, terrain, realm/province,
and population; responsive HUD placement checks.
Acceptance notes: Unknown values display as unavailable or are omitted. The
front/back state persists locally, the panel remains legible at supported UI
scales, and the original time tooltip/interaction remains accessible.
```

## High-Impact Future Ideas

### Unified World Activity And Alert Engine

```text
Name: Unified world activity and alert engine
Description: Create one small background service for world-object timers and
state-change alerts: crops ready, curios complete, ovens/kilns finished,
livestock events, drying racks, localized danger, and the Beehive Pyre timer.
Why it matters: A shared engine prevents every reminder feature from inventing
its own persistence, deduplication, notification, and reconnect logic.
Smallest useful version: A typed local timer record, restart-safe persistence,
deduplication by object identity/location, and existing-style notifications,
used only by the Beehive Pyre feature first.
Risks: Incorrect duration assumptions create misleading alerts; too many alerts
become noise. Prefer observed server state and clearly label estimates.
Dependencies: Beehive Pyre evidence and the current notification system.
```

### Personal World Knowledge Layer

```text
Name: Personal world knowledge layer
Description: Turn observed map data into a searchable private notebook of
resources, claims, dangerous locations, useful structures, forage routes, and
user notes, with filters and staleness indicators.
Why it matters: The map becomes a durable planning tool instead of a collection
of disconnected markers.
Smallest useful version: Search and filter existing local markers by type,
distance, age, and free-text note; include a direct map focus action.
Risks: Map data can become stale or reveal sensitive locations if exported.
Keep storage local by default and make any sharing an explicit reviewed action.
Dependencies: Existing map marker storage and fishing observation journal.
```

### Journey Planner And Safe Return Tools

```text
Name: Journey planner and safe return tools
Description: Let the player compose a route from private markers, estimate
distance and travel resources, record a breadcrumb trail, and surface a clear
return path without automatically moving the character.
Why it matters: Long trips become easier to plan and recover from while keeping
the player in control.
Smallest useful version: Manual start/destination selection, distance estimate,
bearing, and an optional local breadcrumb overlay.
Risks: Terrain changes and unknown obstacles make route estimates imperfect.
Do not silently turn guidance into auto-travel.
Dependencies: Map coordinates, marker selection, and movement-speed evidence.
```

### Contextual Object Inspector

```text
Name: Contextual object inspector
Description: Expand hover/inspect information for world objects and inventory
items with concise client-known facts, relevant cookbook/wiki knowledge, local
observations, timers, and safe next-action hints.
Why it matters: It reduces window switching and makes the client's existing
knowledge systems useful at the exact moment the player needs them.
Smallest useful version: A modifier-key tooltip section for one object family,
using only cached or already-known data.
Risks: Large tooltips and stale wiki facts can obscure authoritative game data.
Clearly separate server facts, local observations, and external reference text.
Dependencies: Existing native wiki reader, cookbook repository, and tooltip UI.
```

### Session Journal And Searchable Timeline

```text
Name: Session journal and searchable timeline
Description: Record a privacy-conscious local timeline of useful events such as
discoveries, wounds, deaths, combat encounters, map-marker changes, crafted
milestones, timer completions, and connection problems.
Why it matters: Players can answer "what happened and where?" and diagnose
problems without digging through raw logs.
Smallest useful version: Opt-in recording for a few non-sensitive event types,
with timestamp, map link where appropriate, filters, and bounded retention.
Risks: Character/account names, chat, coordinates, and session material are
sensitive. Default to minimal local data, provide deletion/retention controls,
and never include the journal in releases.
Dependencies: Existing combat log work, marker system, and local persistence.
```

### Adaptive HUD Profiles

```text
Name: Adaptive HUD profiles
Description: Save named arrangements and visibility presets for activities such
as exploration, combat, crafting, farming, and multi-account monitoring.
Why it matters: MoonFlower already has several specialized panels; profiles keep
them useful without permanently crowding the screen.
Smallest useful version: Manually save, restore, rename, and delete one local HUD
layout profile. Automatic activity switching is a later optional slice.
Risks: Resolution/UI-scale changes can move widgets off-screen. Profile restore
must clamp safely and preserve a recoverable default layout.
Dependencies: Stable widget position persistence and HUD edit mode.
```

### Client Health And Performance Dashboard

```text
Name: Client health and performance dashboard
Description: Provide a compact diagnostics view for frame time, memory, resource
loading, network latency, reconnect state, recent errors, and optional multi-
session load, with a one-click privacy-safe diagnostic export.
Why it matters: It makes freezes, crashes, lag, and multi-client capacity easier
to understand before changing code or runtime settings.
Smallest useful version: Rolling FPS/frame-time, heap use, ping where available,
and the latest non-sensitive error summary.
Risks: Continuous instrumentation can add overhead, and exports can leak paths,
names, coordinates, or server/account data. Measure cost and redact by default.
Dependencies: Existing logging/telemetry seams and a reviewed redaction policy.
```
