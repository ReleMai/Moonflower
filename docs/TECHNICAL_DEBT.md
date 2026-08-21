# Technical Debt

## Purpose
This file tracks shortcuts that are acceptable for now but should be revisited later.

## Rules
- Record debt when taking a shortcut on purpose.
- Keep the note specific and easy to act on.
- Link the debt to the file, feature, or system that owns it.
- Remove or update the entry when the issue is resolved.

## Entry Template

```text
Date:
Area:
Shortcut taken:
Why it is acceptable now:
Better long-term approach:
Where it lives:
```

## Current Entries

```text
Date: 2026-08-21
Area: Shared bot protocol
Shortcut taken: Client and server exchange named JSON messages without an explicit protocol version or capability negotiation.
Why it is acceptable now: The client and local server are built and deployed from the same workspace.
Better long-term approach: Add a handshake with protocol version, client build, and supported-command capabilities; reject incompatible peers clearly.
Where it lives: client/src/haven/botcontrol, shared-protocol, server websocket handlers

Date: 2026-08-21
Area: React dashboard structure
Shortcut taken: Most dashboard presentation and orchestration remains concentrated in App.tsx.
Why it is acceptable now: Recovery changes stabilized behavior without a risky UI rewrite.
Better long-term approach: Extract feature panels and their focused data hooks incrementally with tests.
Where it lives: web/src/App.tsx

Date: 2026-08-21
Area: MJPEG subscriber lifecycle
Shortcut taken: The live stream uses an unlimited Servlet async timeout and discovers disconnects on a later frame write.
Why it is acceptable now: It fixes the observed 30-second timeout and active feeds clean up on write failure.
Better long-term approach: Add an explicit heartbeat/cancellation signal so an idle disconnected subscriber is released without waiting for another frame.
Where it lives: server/src/main/java/io/havenbot/server/config/WebMvcConfig.java and service/LiveFeedService.java

Date: 2026-08-21
Area: Java dependency/toolchain maintenance
Shortcut taken: The compatibility port retains upstream/client and existing Maven dependency versions; only npm advisories were automatically repaired.
Why it is acceptable now: Updating unrelated frameworks during a game-protocol port would enlarge the regression surface, and services remain loopback-only.
Better long-term approach: Run a dedicated Maven/client dependency advisory review and upgrade in isolated, tested slices.
Where it lives: client/build.xml, client/lib, pom.xml, server/pom.xml

Date: 2026-08-21
Area: In-game cookbook ingredient parsing
Shortcut taken: Resource-delivered Ingredient and Smoke tooltip classes are recognized by class name and their public name/val fields.
Why it is acceptable now: Those tooltip types have no stable compile-time Java classes, and the reflection is isolated in one parser rather than spread through UI or persistence code.
Better long-term approach: Record live class/resource evidence, then add explicit adapters or a stable tooltip capability interface if upstream exposes one.
Where it lives: client/src/haven/cookbook/CookbookFoodParser.java

Date: 2026-08-21
Area: In-game cookbook ingredient planner evidence
Shortcut taken: Main ingredient attributes are presented as averages of the Q10-normalized recipes containing that ingredient, while spice boosts require a locally observed recipe pair with the same output and non-spice ingredients.
Why it is acceptable now: The client has authoritative finished-food outcomes but no intrinsic per-ingredient FEP payload; the UI labels recipe averages and measured comparisons explicitly instead of claiming unsupported ingredient values.
Better long-term approach: Capture stable Makewindow input-slot metadata and additional controlled recipe pairs, then separate intrinsic slot data from observed outcome correlations when the protocol exposes enough evidence.
Where it lives: client/src/haven/cookbook/CookbookRepository.java and client/src/haven/cookbook/CookbookWindow.java

Date: 2026-08-21
Area: Cookbook character-adjusted outcomes
Shortcut taken: The details panel stores and labels the FEP and hunger efficiencies from the most recent tooltip capture instead of continuously recalculating them while the window is open.
Why it is acceptable now: Character hunger, satiations, account bonuses, and table modifiers are transient; retaining the authoritative unmodified outcome plus an explicitly labeled last-captured result prevents either value from being mistaken for the other.
Better long-term approach: Persist stable food-type identifiers and expose one shared live efficiency calculator so the cookbook can refresh selected outcomes immediately when character or table state changes.
Where it lives: client/src/haven/resutil/FoodInfo.java and client/src/haven/cookbook

Date: 2026-08-21
Area: In-game cookbook recipe opening
Shortcut taken: The Open recipe button resolves a currently known craft page by exact, case-insensitive display-name match.
Why it is acceptable now: The captured food tooltip does not expose its originating craft-page resource, and exact matching avoids silently opening a different recipe.
Better long-term approach: Correlate Makewindow outputs with their MenuGrid page and persist the stable page resource alongside the observed recipe.
Where it lives: client/src/haven/cookbook/CookbookWindow.java

Date: 2026-08-21
Area: Fishing catch provenance
Shortcut taken: A catch is recorded as a candidate when a newly observed main-inventory item classifies as a fish during a bounded active fishing attempt.
Why it is acceptable now: The current source does not expose a proven authoritative catch callback, and candidate confidence prevents timing correlation from being presented as fact.
Better long-term approach: Capture supervised live protocol/widget evidence and add an explicit caught-fish event adapter, retaining candidate records for backward compatibility.
Where it lives: client/src/haven/automated/FishingBot.java and client/src/haven/fishing

Date: 2026-08-21
Area: Fishing map projection scale
Shortcut taken: The display-only map layer projects the 2,000 most recent world-scoped fishing observations and retries unresolved map grids periodically.
Why it is acceptable now: It bounds UI/database work while retaining a large local history and never mutates or uploads normal map markers.
Better long-term approach: Add paged spatial queries keyed by visible segment bounds if real journals grow beyond this limit.
Where it lives: client/src/haven/fishing/FishingMapMarkers.java
```
