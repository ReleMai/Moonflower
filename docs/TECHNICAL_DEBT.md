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
```
