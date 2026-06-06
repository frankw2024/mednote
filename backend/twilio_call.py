"""
Twilio Voice (browser WebRTC → PSTN) for MedNote.

Env vars:
  TWILIO_ACCOUNT_SID      Account SID
  TWILIO_AUTH_TOKEN       Auth token (recording download)
  TWILIO_API_KEY          API key SID (for access tokens)
  TWILIO_API_SECRET       API key secret
  TWILIO_TWIML_APP_SID    TwiML App SID (Voice URL → /api/call/twilio/voice)
  TWILIO_CALLER_ID        Outbound caller ID (your Twilio number, E.164)
  PUBLIC_BASE_URL         Public HTTPS base, e.g. https://fwang2024.onrender.com
                          (falls back to RENDER_EXTERNAL_URL on Render)
"""

from __future__ import annotations

import audio_util

audioop = audio_util.audioop
import base64
import json
import os
import re
import threading
import time
import uuid
import xml.sax.saxutils as saxutils
from dataclasses import dataclass, field
from typing import Any, Dict, Optional
from urllib.parse import urlparse

import livetranscript

try:
    import requests
except ImportError:  # pragma: no cover
    requests = None

from call import normalize_phone

RECORD_DIR = os.environ.get("CALL_RECORD_DIR", "/tmp/mednote_calls")
os.makedirs(RECORD_DIR, exist_ok=True)


def _env(name: str) -> str:
    return os.environ.get(name, "").strip()


def configured() -> bool:
    return bool(
        _env("TWILIO_ACCOUNT_SID")
        and _env("TWILIO_API_KEY")
        and _env("TWILIO_API_SECRET")
        and _env("TWILIO_TWIML_APP_SID")
        and _env("TWILIO_CALLER_ID")
        and public_base_url()
        and media_stream_url()
    )


def public_base_url() -> str:
    url = _env("PUBLIC_BASE_URL") or _env("RENDER_EXTERNAL_URL")
    return url.rstrip("/")


def media_stream_url() -> str:
    base = public_base_url()
    if not base:
        return ""
    parsed = urlparse(base)
    host = parsed.netloc or parsed.path
    if not host:
        return ""
    scheme = "wss" if parsed.scheme == "https" else "ws"
    return f"{scheme}://{host}/api/call/twilio/media"


@dataclass
class TwilioCall:
    call_id: str
    phone: str
    user_lang: str = "en"
    phase: str = "dialing"
    error: Optional[str] = None
    call_sid: Optional[str] = None
    recording_path: Optional[str] = None
    started_at: float = field(default_factory=time.time)
    ended_at: Optional[float] = None


_lock = threading.Lock()
_calls: Dict[str, TwilioCall] = {}
_call_sid_to_id: Dict[str, str] = {}


def has_call(call_id: str) -> bool:
    with _lock:
        return call_id in _calls


def create_token(identity: str) -> str:
    from twilio.jwt.access_token import AccessToken
    from twilio.jwt.access_token.grants import VoiceGrant

    account_sid = _env("TWILIO_ACCOUNT_SID")
    api_key = _env("TWILIO_API_KEY")
    api_secret = _env("TWILIO_API_SECRET")
    app_sid = _env("TWILIO_TWIML_APP_SID")

    token = AccessToken(account_sid, api_key, api_secret, identity=identity)
    grant = VoiceGrant(outgoing_application_sid=app_sid, incoming_allow=False)
    token.add_grant(grant)
    return token.to_jwt()


def prepare_call(
    phone: str, user_lang: str = "en", identity: str = "mednote-user"
) -> Dict[str, Any]:
    number = normalize_phone(phone)
    if not number:
        raise ValueError("Phone number is required")
    if not configured():
        raise RuntimeError("Twilio not configured")

    call_id = "c" + uuid.uuid4().hex[:12]
    tc = TwilioCall(call_id=call_id, phone=number, user_lang=user_lang or "en")
    try:
        livetranscript.start_session(call_id, user_lang=user_lang or "en")
    except Exception:
        pass

    with _lock:
        _calls[call_id] = tc

    safe_identity = re.sub(r"[^\w@.-]", "_", identity)[:64] if identity else "mednote-user"
    jwt = create_token(safe_identity)
    return {
        "callId": call_id,
        "token": jwt,
        "identity": safe_identity,
        "provider": "twilio",
    }


def handle_voice_webhook(params: Dict[str, str]) -> str:
    to = (params.get("To") or params.get("to") or "").strip()
    call_id = (params.get("callId") or params.get("CallId") or "").strip()

    if not to or not call_id:
        return (
            '<?xml version="1.0" encoding="UTF-8"?>'
            "<Response><Say>Missing call parameters.</Say></Response>"
        )

    with _lock:
        tc = _calls.get(call_id)
        if tc:
            tc.phase = "dialing"

    stream_url = media_stream_url()
    caller_id = _env("TWILIO_CALLER_ID")
    base = public_base_url()
    rec_cb = f"{base}/api/call/twilio/recording"

    esc = saxutils.escape
    twiml = f"""<?xml version="1.0" encoding="UTF-8"?>
<Response>
  <Start>
    <Stream url="{esc(stream_url)}" track="both_tracks">
      <Parameter name="callId" value="{esc(call_id)}" />
    </Stream>
  </Start>
  <Dial callerId="{esc(caller_id)}" record="record-from-answer" recordingStatusCallback="{esc(rec_cb)}" recordingStatusCallbackMethod="POST">
    <Number>{esc(to)}</Number>
  </Dial>
</Response>"""
    return twiml


async def handle_media_websocket(websocket) -> None:
    call_id: Optional[str] = None
    try:
        while True:
            raw = await websocket.receive_text()
            msg = json.loads(raw)
            event = msg.get("event")

            if event == "start":
                start = msg.get("start") or {}
                custom = start.get("customParameters") or {}
                call_id = custom.get("callId") or call_id
                call_sid = start.get("callSid")
                if call_id:
                    with _lock:
                        tc = _calls.get(call_id)
                        if tc:
                            tc.phase = "active"
                            if call_sid:
                                tc.call_sid = call_sid
                                _call_sid_to_id[call_sid] = call_id
                elif call_sid:
                    with _lock:
                        call_id = _call_sid_to_id.get(call_sid)

            elif event == "media" and call_id:
                payload = (msg.get("media") or {}).get("payload")
                if not payload:
                    continue
                try:
                    mulaw = base64.b64decode(payload)
                    pcm = audioop.ulaw2lin(mulaw, 2)
                    livetranscript.feed_audio(call_id, pcm)
                except Exception:
                    pass

            elif event == "stop":
                break
    except Exception:
        pass
    finally:
        if call_id:
            with _lock:
                tc = _calls.get(call_id)
                if tc and tc.phase == "active":
                    tc.phase = "ended"
                    tc.ended_at = time.time()


def handle_recording_callback(form: Dict[str, str]) -> None:
    recording_url = (form.get("RecordingUrl") or "").strip()
    call_sid = (form.get("CallSid") or "").strip()
    status = (form.get("RecordingStatus") or "").strip().lower()

    if status and status != "completed":
        return
    if not recording_url or not call_sid:
        return

    with _lock:
        call_id = _call_sid_to_id.get(call_sid)
        tc = _calls.get(call_id) if call_id else None

    if not call_id:
        return

    account_sid = _env("TWILIO_ACCOUNT_SID")
    auth_token = _env("TWILIO_AUTH_TOKEN")
    if not requests or not account_sid or not auth_token:
        return

    url = recording_url if recording_url.endswith(".wav") else recording_url + ".wav"
    try:
        resp = requests.get(url, auth=(account_sid, auth_token), timeout=90)
        if not resp.ok:
            return
        path = os.path.join(RECORD_DIR, f"{call_id}.wav")
        with open(path, "wb") as f:
            f.write(resp.content)
        with _lock:
            if tc:
                tc.recording_path = path
    except Exception:
        pass


def get_call(call_id: str) -> Dict[str, Any]:
    with _lock:
        tc = _calls.get(call_id)
    if not tc:
        raise KeyError("Call not found")
    ready = bool(
        tc.recording_path and os.path.isfile(tc.recording_path) and os.path.getsize(tc.recording_path) > 44
    )
    return {
        "callId": tc.call_id,
        "phone": tc.phone,
        "phase": tc.phase,
        "error": tc.error,
        "recordingReady": ready,
        "startedAt": tc.started_at,
        "endedAt": tc.ended_at,
        "provider": "twilio",
    }


def stop_call(call_id: str) -> Dict[str, Any]:
    with _lock:
        tc = _calls.get(call_id)
    if not tc:
        raise KeyError("Call not found")
    if tc.phase not in ("failed", "ended"):
        tc.phase = "ended"
    tc.ended_at = tc.ended_at or time.time()
    try:
        livetranscript.stop_session(call_id)
    except Exception:
        pass
    return get_call(call_id)


def recording_path(call_id: str) -> str:
    with _lock:
        tc = _calls.get(call_id)
    if not tc or not tc.recording_path or not os.path.isfile(tc.recording_path):
        raise FileNotFoundError("Recording not available")
    return tc.recording_path


def call_config() -> Dict[str, Any]:
    return {
        "configured": configured(),
        "mediaStreamUrl": media_stream_url() if configured() else None,
    }
