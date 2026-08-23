# Rolling Replay And Clip Capture

## Implemented Design

The Python media gateway owns the media pipeline. For each enabled bot it reads
the server's JPEG live feed, serves the frames as WebRTC video, and keeps a
bounded in-memory replay deque. A save request selects the requested time window
and writes an MP4 under the repository sibling path `..\server-data\clips`.

The server coordinates replay enable/release/save operations, stores clip
metadata, audits saves, and broadcasts `clip-saved` events. The React dashboard
offers manual replay saves and recent clip links. Server health thresholds can
also trigger replay saves.

This architecture intentionally keeps video encoding out of the Java game
client, whose responsibilities already include rendering, protocol handling,
telemetry, screenshots, and visible control execution.

## Current Limits

- The rolling buffer is decoded JPEG data held in memory, not rotating on-disk
  media segments. Memory grows with configured window, frame rate, and image
  size.
- Meaningful video verification requires an online bot producing frames; local
  health checks prove gateway startup but not live WebRTC quality.
- There is no audio stream.
- Multi-bot load and long-duration memory behavior need measurement before
  increasing the default replay window.

## Future Hardening

If memory use becomes material, replace the in-memory deque with short rotating
segments and preserve only the segments needed for a clip. OBS Replay Buffer is
also a useful external fallback for operator-visible recording without changing
the client.
