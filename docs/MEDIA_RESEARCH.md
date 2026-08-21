# Rolling Replay And Clip Capture

## Summary

There are two practical ways to add reviewable footage and auto-saved clips to the current Haven bot stack:

1. External replay buffer using OBS Studio.
2. Built-in rolling recorder inside `media-gateway`.

The fastest low-risk option is OBS replay buffer. The best integrated long-term option is a built-in recorder in `media-gateway` using the existing WebRTC video path and FFmpeg-backed segment rotation.

## Best Immediate Option: OBS Replay Buffer

OBS already supports replay-buffer recording and can auto-start it with `--startreplaybuffer`.

Why it fits:

- No client patching is required for video encoding.
- It already solves "save the last N seconds/minutes" well.
- We can later automate clip saves by calling OBS hotkeys or the OBS WebSocket API.

Recommended use:

- Run OBS in a dedicated profile for bot monitoring.
- Capture the client window or desktop.
- Enable Replay Buffer.
- Set the replay length to 5 minutes or whatever window you want.
- Save replay on important events like heavy health loss, knockout, disconnect, or rare item pickup.

## Best Integrated Option: Built-In Recorder

The current stack already has a Python `media-gateway` using `aiortc`, which means we can record the same stream the operator sees.

Recommended architecture:

1. Keep the existing live WebRTC path.
2. Add a recorder subscriber in `media-gateway`.
3. Write video into short segments.
4. Rotate those segments as a ring buffer.
5. On a trigger, preserve the last N minutes instead of letting the oldest segments be overwritten.

Why this is the right internal design:

- `aiortc` exposes `MediaRecorder` for writing audio/video to files.
- FFmpeg's segment muxer supports time-based splitting with wraparound rotation.
- This avoids trying to keep a huge in-memory replay buffer inside the Java client.

Suggested defaults:

- 5-second segments
- 60 segments for a 5-minute rolling window
- One recorder per bot
- Preserve clips into `server-data/clips/<bot-id>/`

## Event Triggers For Clips

The current telemetry already exposes enough signals to begin clip-triggering logic:

- Health drops sharply
- Health reaches critical thresholds
- Knockout / disconnected / transport error
- Inventory changes indicating rare pickups
- Route failures or stuck detection

Recommended first trigger set:

- Health drops by 20%+ inside 10 seconds
- Health falls below 40%
- Health falls below 15%
- Session disconnects unexpectedly
- Client error events

Recommended later trigger set:

- Rare item allowlist
- Enemy/player proximity
- Combat state start
- Route stuck recovery

## Suggested Implementation Order

1. Add a built-in rolling segment recorder to `media-gateway`.
2. Add server config for replay window length and segment duration.
3. Add clip-trigger rules on the server using existing activity/state events.
4. Add a manual "Save Last 5 Minutes" button to the web UI.
5. Add a clip browser in the operator dashboard.

## Why Not Record In The Java Client

The client is already responsible for gameplay, UI, automation, screenshots, and remote input. Moving rolling video encoding there would increase contention and make debugging harder. The Python gateway is a better place because it already owns the media pipeline.
