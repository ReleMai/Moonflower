# Architecture

## Runtime Boundaries

```text
Haven game servers
        ^
        | official client protocol/resources
        v
Hurricane Java client (one visible process per bot)
        |
        | authenticated bot WebSocket + state/events/commands
        v
Spring Boot control server ---- SQLite / screenshots / clips / audit
        |                         |
        | REST + operator WS      | HTTP frame/replay coordination
        v                         v
React operator dashboard <---- Python WebRTC media gateway
```

## Responsibilities

- `client/` owns game protocol/UI compatibility, visible automation actions,
  state collection, screenshots, and input execution.
- `shared-protocol/` defines stable server-side command/event and state models.
  The client currently emits equivalent JSON directly rather than depending on
  this Maven module.
- `server/` owns operator authentication, encrypted account storage, bot process
  lifecycle, task routing, persistence, audit, screenshots, and media metadata.
- `web/` is the single-operator control surface.
- `media-gateway/` converts the server's JPEG frame stream to WebRTC and keeps a
  rolling replay window for MP4 clip creation.
- `references/` is research material and is not an operational dependency.
- `artifacts/` is preserved evidence only and is excluded from source control.

## Fragile Seams

- Login widgets and character-list messages change with upstream UI work.
- Client widget/resource classes are both gameplay dependencies and telemetry
  inputs.
- Remote input and screenshots depend on upstream toolkit/window APIs.
- Bot actions depend on Hurricane automation classes with no compatibility
  interface.
- Protocol messages have enums but no explicit negotiated version.
- The dashboard currently concentrates most UI behavior in `web/src/App.tsx`.
