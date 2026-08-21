import asyncio
import logging
import os
import time
import uuid
from collections import deque
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from fractions import Fraction
from io import BytesIO
from pathlib import Path
from typing import Optional

import aiohttp
import av
import numpy as np
from aiohttp import web
from aiortc import RTCPeerConnection, RTCSessionDescription, VideoStreamTrack
from aiortc.contrib.media import MediaRelay
from aiortc.mediastreams import MediaStreamError
from av import VideoFrame
from PIL import Image


SERVER_BASE_URL = os.getenv("HAVENBOT_SERVER_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
LISTEN_HOST = os.getenv("HAVENBOT_WEBRTC_HOST", "127.0.0.1")
LISTEN_PORT = int(os.getenv("HAVENBOT_WEBRTC_PORT", "8091"))
LIVE_FEED_INTERVAL_MS = max(40, int(os.getenv("HAVENBOT_WEBRTC_INTERVAL_MS", "50")))
TARGET_FPS = max(10, int(os.getenv("HAVENBOT_WEBRTC_TARGET_FPS", "20")))
IDLE_CLOSE_SECONDS = max(2, int(os.getenv("HAVENBOT_WEBRTC_IDLE_CLOSE_SECONDS", "8")))
REPLAY_BUFFER_SECONDS = max(30, int(os.getenv("HAVENBOT_REPLAY_BUFFER_SECONDS", "300")))
REPLAY_CAPTURE_INTERVAL_MS = max(100, int(os.getenv("HAVENBOT_REPLAY_CAPTURE_INTERVAL_MS", "200")))
SERVER_OPERATOR_USERNAME = os.getenv("HAVEN_OPERATOR_USERNAME", "admin")
SERVER_OPERATOR_PASSWORD = os.getenv("HAVEN_OPERATOR_PASSWORD", "changeme")
CLIP_DIR = Path(os.getenv("HAVENBOT_CLIP_DIR", "../server-data/clips")).resolve()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
LOGGER = logging.getLogger("havenbot-webrtc")


@dataclass
class ReplayFrame:
    captured_at: datetime
    payload: bytes
    width: int
    height: int


class ServerApiClient:
    def __init__(self, session: aiohttp.ClientSession) -> None:
        self.session = session
        self.operator_token: Optional[str] = None
        self.lock = asyncio.Lock()

    async def post(self, path: str, json_body: Optional[dict] = None, token: Optional[str] = None,
                   timeout_seconds: int = 10) -> aiohttp.ClientResponse:
        headers = await self._authorized_headers(token)
        response = await self.session.post(
            f"{SERVER_BASE_URL}{path}",
            json=json_body,
            headers=headers,
            timeout=aiohttp.ClientTimeout(total=timeout_seconds),
        )
        if response.status == 401 and token is None:
            response.release()
            headers = await self._authorized_headers(None, force_refresh=True)
            response = await self.session.post(
                f"{SERVER_BASE_URL}{path}",
                json=json_body,
                headers=headers,
                timeout=aiohttp.ClientTimeout(total=timeout_seconds),
        )
        if response.status >= 400:
            text = await response.text()
            response.release()
            raise RuntimeError(f"{path} failed with {response.status}: {text}")
        return response

    async def get(self, path: str, token: Optional[str] = None,
                  timeout: Optional[aiohttp.ClientTimeout] = None) -> aiohttp.ClientResponse:
        headers = await self._authorized_headers(token)
        response = await self.session.get(
            f"{SERVER_BASE_URL}{path}",
            headers=headers,
            timeout=timeout,
        )
        if response.status == 401 and token is None:
            response.release()
            headers = await self._authorized_headers(None, force_refresh=True)
            response = await self.session.get(
                f"{SERVER_BASE_URL}{path}",
                headers=headers,
                timeout=timeout,
        )
        if response.status >= 400:
            text = await response.text()
            response.release()
            raise RuntimeError(f"{path} failed with {response.status}: {text}")
        return response

    async def _authorized_headers(self, token: Optional[str], force_refresh: bool = False) -> dict[str, str]:
        if token:
            return {"X-Operator-Token": token}
        return {"X-Operator-Token": await self._ensure_operator_token(force_refresh=force_refresh)}

    async def _ensure_operator_token(self, force_refresh: bool = False) -> str:
        async with self.lock:
            if self.operator_token is not None and not force_refresh:
                return self.operator_token
            async with self.session.post(
                f"{SERVER_BASE_URL}/api/auth/login",
                json={
                    "username": SERVER_OPERATOR_USERNAME,
                    "password": SERVER_OPERATOR_PASSWORD,
                },
                timeout=aiohttp.ClientTimeout(total=10),
            ) as response:
                if response.status >= 400:
                    text = await response.text()
                    raise RuntimeError(f"Failed to authenticate media gateway with server: {response.status}: {text}")
                payload = await response.json()
                token = str(payload.get("token", "")).strip()
                if not token:
                    raise RuntimeError("Server operator login did not return a token.")
                self.operator_token = token
                return token


class BotVideoSource(VideoStreamTrack):
    kind = "video"

    def __init__(self, bot_id: str, api_client: ServerApiClient) -> None:
        super().__init__()
        self.bot_id = bot_id
        self.api_client = api_client
        self.interval_ms = LIVE_FEED_INTERVAL_MS
        self.frame_interval = 1.0 / TARGET_FPS
        self.time_base = Fraction(1, 90000)
        self.timestamp_step = int(self.frame_interval / self.time_base)
        self.next_frame_time = time.perf_counter()
        self.timestamp = 0
        self.latest_frame = np.zeros((360, 640, 3), dtype=np.uint8)
        self.reader_task: Optional[asyncio.Task[None]] = None
        self.closed = False
        self.replay_frames: deque[ReplayFrame] = deque()
        self.last_replay_append_at: Optional[datetime] = None
        self.buffer_lock = asyncio.Lock()

    async def ensure_started(self) -> None:
        if self.reader_task is None or self.reader_task.done():
            self.closed = False
            self.reader_task = asyncio.create_task(self._reader_loop(), name=f"feed-reader-{self.bot_id}")

    async def recv(self) -> VideoFrame:
        if self.readyState != "live":
            raise MediaStreamError
        if self.closed:
            raise MediaStreamError
        now = time.perf_counter()
        wait_for = self.next_frame_time - now
        if wait_for > 0:
            await asyncio.sleep(wait_for)
        self.next_frame_time = max(self.next_frame_time + self.frame_interval, time.perf_counter())
        self.timestamp += self.timestamp_step
        frame = VideoFrame.from_ndarray(self.latest_frame, format="rgb24")
        frame.pts = self.timestamp
        frame.time_base = self.time_base
        return frame

    async def close_source(self) -> None:
        if self.closed:
            return
        self.closed = True
        task = self.reader_task
        self.reader_task = None
        if task is not None:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
        try:
            response = await self.api_client.post(f"/api/bots/{self.bot_id}/live-feed/stop")
            response.release()
        except Exception:
            LOGGER.exception("Failed to stop live feed for bot %s", self.bot_id)
        self.stop()

    async def save_replay(self, trigger_type: str, reason: str, requested_seconds: Optional[int]) -> dict:
        duration_seconds = max(5, min(requested_seconds or REPLAY_BUFFER_SECONDS, REPLAY_BUFFER_SECONDS))
        async with self.buffer_lock:
            if not self.replay_frames:
                raise RuntimeError("Replay buffer is empty.")
            cutoff = self.replay_frames[-1].captured_at - timedelta(seconds=duration_seconds)
            frames = [frame for frame in self.replay_frames if frame.captured_at >= cutoff]
        if len(frames) < 2:
            raise RuntimeError("Not enough replay frames were collected yet.")

        clip_id = str(uuid.uuid4())
        file_name = f"{clip_id}.mp4"
        target = CLIP_DIR / file_name
        fps = estimate_fps(frames)
        await asyncio.to_thread(write_mp4_clip, target, frames, fps)
        created_at = datetime.now(timezone.utc)
        return {
            "clipId": clip_id,
            "fileName": file_name,
            "mediaType": "video/mp4",
            "triggerType": trigger_type,
            "reason": reason,
            "durationSeconds": int(round((frames[-1].captured_at - frames[0].captured_at).total_seconds())),
            "createdAt": created_at.isoformat(),
            "startedAt": frames[0].captured_at.isoformat(),
            "endedAt": frames[-1].captured_at.isoformat(),
            "frameCount": len(frames),
            "fps": fps,
            "width": frames[0].width,
            "height": frames[0].height,
        }

    async def _reader_loop(self) -> None:
        timeout = aiohttp.ClientTimeout(total=None, connect=5, sock_read=90)
        try:
            while not self.closed:
                try:
                    response = await self.api_client.post(
                        f"/api/bots/{self.bot_id}/live-feed/start",
                        json_body={"intervalMillis": self.interval_ms},
                    )
                    response.release()
                    async with await self.api_client.get(f"/api/bots/{self.bot_id}/live-feed", timeout=timeout) as response:
                        reader = aiohttp.MultipartReader.from_response(response)
                        while not self.closed:
                            part = await reader.next()
                            if part is None:
                                break
                            if self.closed:
                                break
                            payload = await part.read(decode=False)
                            if not payload:
                                continue
                            captured_at = parse_datetime(part.headers.get("X-Created-At"))
                            frame = decode_jpeg_frame(payload)
                            self.latest_frame = frame
                            await self._append_replay_frame(captured_at, payload, frame.shape[1], frame.shape[0])
                except asyncio.CancelledError:
                    raise
                except Exception:
                    LOGGER.exception("Live feed reader failed for bot %s; reconnecting.", self.bot_id)
                    await asyncio.sleep(1.0)
        finally:
            LOGGER.info("Live feed reader closed for bot %s", self.bot_id)

    async def _append_replay_frame(self, captured_at: datetime, payload: bytes, width: int, height: int) -> None:
        async with self.buffer_lock:
            if self.last_replay_append_at is not None:
                delta_ms = (captured_at - self.last_replay_append_at).total_seconds() * 1000.0
                if delta_ms < REPLAY_CAPTURE_INTERVAL_MS:
                    return
            self.replay_frames.append(ReplayFrame(captured_at=captured_at, payload=payload, width=width, height=height))
            self.last_replay_append_at = captured_at
            cutoff = captured_at - timedelta(seconds=REPLAY_BUFFER_SECONDS)
            while self.replay_frames and self.replay_frames[0].captured_at < cutoff:
                self.replay_frames.popleft()


def decode_jpeg_frame(payload: bytes) -> np.ndarray:
    with Image.open(BytesIO(payload)) as image:
        rgb = image.convert("RGB")
        return np.array(rgb, dtype=np.uint8)


def parse_datetime(value: Optional[str]) -> datetime:
    if not value:
        return datetime.now(timezone.utc)
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return datetime.now(timezone.utc)


def estimate_fps(frames: list[ReplayFrame]) -> int:
    if len(frames) < 2:
        return max(1, round(1000 / REPLAY_CAPTURE_INTERVAL_MS))
    duration = (frames[-1].captured_at - frames[0].captured_at).total_seconds()
    if duration <= 0:
        return max(1, round(1000 / REPLAY_CAPTURE_INTERVAL_MS))
    return max(1, min(30, round((len(frames) - 1) / duration)))


def write_mp4_clip(target: Path, frames: list[ReplayFrame], fps: int) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    container = av.open(str(target), mode="w")
    stream = container.add_stream("mpeg4", rate=fps)
    stream.width = frames[0].width
    stream.height = frames[0].height
    stream.pix_fmt = "yuv420p"
    try:
        for replay_frame in frames:
            frame_array = decode_jpeg_frame(replay_frame.payload)
            video_frame = VideoFrame.from_ndarray(frame_array, format="rgb24")
            for packet in stream.encode(video_frame):
                container.mux(packet)
        for packet in stream.encode(None):
            container.mux(packet)
    finally:
        container.close()


@dataclass
class BotStreamHandle:
    source: BotVideoSource
    relay: MediaRelay
    subscribers: int = 0
    replay_enabled: bool = False
    idle_close_task: Optional[asyncio.Task[None]] = None


class GatewayState:
    def __init__(self) -> None:
        self.session = aiohttp.ClientSession()
        self.api_client = ServerApiClient(self.session)
        self.handles: dict[str, BotStreamHandle] = {}
        self.peer_connections: set[RTCPeerConnection] = set()
        self.lock = asyncio.Lock()

    async def acquire_track(self, bot_id: str) -> VideoStreamTrack:
        async with self.lock:
            handle = await self._ensure_handle(bot_id)
            if handle.idle_close_task is not None:
                handle.idle_close_task.cancel()
                handle.idle_close_task = None
            handle.subscribers += 1
            return handle.relay.subscribe(handle.source)

    async def release_track(self, bot_id: str) -> None:
        async with self.lock:
            handle = self.handles.get(bot_id)
            if handle is None:
                return
            handle.subscribers = max(0, handle.subscribers - 1)
            await self._schedule_close_if_idle(bot_id, handle)

    async def ensure_replay(self, bot_id: str) -> None:
        async with self.lock:
            handle = await self._ensure_handle(bot_id)
            handle.replay_enabled = True
            if handle.idle_close_task is not None:
                handle.idle_close_task.cancel()
                handle.idle_close_task = None

    async def release_replay(self, bot_id: str) -> None:
        async with self.lock:
            handle = self.handles.get(bot_id)
            if handle is None:
                return
            handle.replay_enabled = False
            await self._schedule_close_if_idle(bot_id, handle)

    async def save_replay(self, bot_id: str, trigger_type: str, reason: str, requested_seconds: Optional[int]) -> dict:
        async with self.lock:
            handle = await self._ensure_handle(bot_id)
        return await handle.source.save_replay(trigger_type, reason, requested_seconds)

    async def _ensure_handle(self, bot_id: str) -> BotStreamHandle:
        handle = self.handles.get(bot_id)
        if handle is None:
            source = BotVideoSource(bot_id, self.api_client)
            await source.ensure_started()
            handle = BotStreamHandle(source=source, relay=MediaRelay())
            self.handles[bot_id] = handle
        else:
            await handle.source.ensure_started()
        return handle

    async def _schedule_close_if_idle(self, bot_id: str, handle: BotStreamHandle) -> None:
        if handle.subscribers == 0 and not handle.replay_enabled and handle.idle_close_task is None:
            handle.idle_close_task = asyncio.create_task(self._close_handle_after_idle(bot_id))

    async def _close_handle_after_idle(self, bot_id: str) -> None:
        try:
            await asyncio.sleep(IDLE_CLOSE_SECONDS)
            async with self.lock:
                handle = self.handles.get(bot_id)
                if handle is None or handle.subscribers > 0 or handle.replay_enabled:
                    return
                self.handles.pop(bot_id, None)
            await handle.source.close_source()
        except asyncio.CancelledError:
            return

    async def close(self) -> None:
        pcs = list(self.peer_connections)
        for pc in pcs:
            await pc.close()
        async with self.lock:
            handles = list(self.handles.values())
            self.handles.clear()
        for handle in handles:
            if handle.idle_close_task is not None:
                handle.idle_close_task.cancel()
            await handle.source.close_source()
        await self.session.close()


async def wait_for_ice_gathering_complete(pc: RTCPeerConnection) -> None:
    if pc.iceGatheringState == "complete":
        return
    completed = asyncio.get_running_loop().create_future()

    @pc.on("icegatheringstatechange")
    def on_ice_gathering_state_change() -> None:
        if pc.iceGatheringState == "complete" and not completed.done():
            completed.set_result(None)

    await asyncio.wait_for(completed, timeout=5)


async def health(request: web.Request) -> web.Response:
    state: GatewayState = request.app["state"]
    async with state.lock:
        buffered_bots = sum(1 for handle in state.handles.values() if handle.replay_enabled)
        active_streams = len(state.handles)
    return web.json_response(
        {
            "status": "ok",
            "streams": active_streams,
            "bufferedBots": buffered_bots,
            "peerConnections": len(state.peer_connections),
        }
    )


async def offer(request: web.Request) -> web.Response:
    payload = await request.json()
    bot_id = str(payload.get("botId", "")).strip()
    sdp = str(payload.get("sdp", "")).strip()
    offer_type = str(payload.get("type", "")).strip()
    if not bot_id or not sdp or not offer_type:
        raise web.HTTPBadRequest(text="Missing botId, sdp, or type.")

    state: GatewayState = request.app["state"]
    pc = RTCPeerConnection()
    state.peer_connections.add(pc)
    track = await state.acquire_track(bot_id)
    pc.addTrack(track)
    LOGGER.info("Created WebRTC peer %s for bot %s", id(pc), bot_id)

    released = False

    async def release_once() -> None:
        nonlocal released
        if released:
            return
        released = True
        await state.release_track(bot_id)

    @pc.on("connectionstatechange")
    async def on_connectionstatechange() -> None:
        LOGGER.info("Peer %s state changed to %s", id(pc), pc.connectionState)
        if pc.connectionState in {"failed", "closed", "disconnected"}:
            await release_once()
            if pc.connectionState != "closed":
                await pc.close()
            state.peer_connections.discard(pc)

    try:
        await pc.setRemoteDescription(RTCSessionDescription(sdp=sdp, type=offer_type))
        answer = await pc.createAnswer()
        await pc.setLocalDescription(answer)
        await wait_for_ice_gathering_complete(pc)
        return web.json_response(
            {
                "sdp": pc.localDescription.sdp,
                "type": pc.localDescription.type,
            }
        )
    except Exception as exc:
        LOGGER.exception("Failed to create WebRTC answer for bot %s", bot_id)
        await release_once()
        state.peer_connections.discard(pc)
        await pc.close()
        raise web.HTTPInternalServerError(text=str(exc)) from exc


async def ensure_replay(request: web.Request) -> web.Response:
    payload = await request.json()
    bot_id = str(payload.get("botId", "")).strip()
    if not bot_id:
        raise web.HTTPBadRequest(text="Missing botId.")
    state: GatewayState = request.app["state"]
    await state.ensure_replay(bot_id)
    return web.json_response({"status": "armed", "botId": bot_id})


async def release_replay(request: web.Request) -> web.Response:
    payload = await request.json()
    bot_id = str(payload.get("botId", "")).strip()
    if not bot_id:
        raise web.HTTPBadRequest(text="Missing botId.")
    state: GatewayState = request.app["state"]
    await state.release_replay(bot_id)
    return web.json_response({"status": "released", "botId": bot_id})


async def save_replay(request: web.Request) -> web.Response:
    payload = await request.json()
    bot_id = str(payload.get("botId", "")).strip()
    trigger_type = str(payload.get("triggerType", "manual")).strip() or "manual"
    reason = str(payload.get("reason", "")).strip()
    requested_seconds = payload.get("requestedSeconds")
    if not bot_id:
        raise web.HTTPBadRequest(text="Missing botId.")
    if requested_seconds is not None:
        try:
            requested_seconds = int(requested_seconds)
        except (TypeError, ValueError) as exc:
            raise web.HTTPBadRequest(text="requestedSeconds must be an integer.") from exc

    state: GatewayState = request.app["state"]
    try:
        clip = await state.save_replay(bot_id, trigger_type, reason, requested_seconds)
    except RuntimeError as exc:
        raise web.HTTPBadRequest(text=str(exc)) from exc
    return web.json_response(clip)


async def on_startup(app: web.Application) -> None:
    CLIP_DIR.mkdir(parents=True, exist_ok=True)
    app["state"] = GatewayState()


async def on_cleanup(app: web.Application) -> None:
    state: GatewayState = app["state"]
    await state.close()


def create_app() -> web.Application:
    app = web.Application()
    app.router.add_get("/health", health)
    app.router.add_post("/api/webrtc/offer", offer)
    app.router.add_post("/api/replay/ensure", ensure_replay)
    app.router.add_post("/api/replay/release", release_replay)
    app.router.add_post("/api/replay/save", save_replay)
    app.on_startup.append(on_startup)
    app.on_cleanup.append(on_cleanup)
    return app


if __name__ == "__main__":
    web.run_app(create_app(), host=LISTEN_HOST, port=LISTEN_PORT)
