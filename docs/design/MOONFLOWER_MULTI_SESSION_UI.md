# MoonFlower Multi-Session UI

Status: implemented foundation-to-worker slice; supervised live verification remains pending.

## Purpose

Keep the current MoonFlower client as the only visible game window while allowing up to four additional account sessions to run in isolated, non-windowed worker JVMs. The Session Conservatory is the selectable, resizable viewport and the only place where a worker receives input.

## State and provenance

- `LIVE`: current session and a worker that has a live frame stream.
- `CALC`: worker startup, stale preview, and capability/readiness state.
- Credentials are transient input only. The parent exchanges a password or 64-character login token for a one-shot cookie, clears its byte arrays, and sends only the cookie through a private nonce-bound pipe.
- Snapshots contain labels, state, timestamps, and server display text only. They never contain passwords, tokens, cookies, or launch arguments carrying secrets.

## Layout and resizing

- Preferred content size is 720x430; the safe minimum is 640x360.
- The lower-right Haven window resize affordance is enabled.
- The last content size is stored as `wndsz-sessionConservatory`; opening the panel clamps it to the current GameUI bounds.
- The preview preserves the worker aspect ratio with letterboxing and maps pointer coordinates only inside the displayed image.

## Interaction model

- Selecting `CURRENTLY PLAYING` leaves normal Haven input behavior attached to the visible game.
- Selecting a worker routes only that worker's pointer, wheel, and keyboard events through the framed control pipe.
- A pointer grab keeps press/drag/release events together, including releases outside the image.
- `CLOSE WORKER` performs a graceful protocol shutdown and uses a scoped process termination fallback.
- Closing the panel hides it; destroying GameUI stops all worker processes.

## Classic behavior and accessibility

- The feature is additive to the existing current-session UI and does not replace native keybinds or server messages.
- No continuous decorative animation is required for this slice; all state is expressed by text and panel treatment, so a static/reduced-motion mode is equivalent.
- No new art or asset dependency is introduced.

## Verification boundary

Deterministic checks cover single-visible-surface invariants, capability detection, safe launch arguments, protocol round trips, pointer payload preservation, and 1280x720/1920x1080 geometry. A real account login, worker resource load, server connection, rendered viewport, and in-world input response still require supervised live validation.
