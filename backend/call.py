"""
MedNote phone call service — uses pyVoIP (https://github.com/tayler6000/pyVoIP).

Configure SIP credentials via environment variables to place outbound calls and
record conversation audio server-side. When SIP is not configured, the frontend
falls back to tel: dial + local microphone recording.

Env vars:
  SIP_SERVER      SIP registrar host (e.g. sip.linphone.org)
  SIP_PORT        SIP port (default 5060)
  SIP_USERNAME    SIP account username
  SIP_PASSWORD    SIP account password
  SIP_MY_IP       Public/LAN IP seen by the SIP server (required for RTP)
  CALL_RECORD_DIR Directory for WAV recordings (default /tmp/mednote_calls)
"""

from __future__ import annotations

import audioop
import os
import re
import threading
import time
import uuid
import wave
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, Optional

import livetranscript

RECORD_DIR = os.environ.get("CALL_RECORD_DIR", "/tmp/mednote_calls")
os.makedirs(RECORD_DIR, exist_ok=True)


def normalize_phone(number: str) -> str:
    n = (number or "").strip()
    if not n:
        return ""
    if n.startswith("+"):
        return "+" + re.sub(r"\D", "", n[1:])
    digits = re.sub(r"\D", "", n)
    return digits or n


def sip_configured() -> bool:
    return bool(
        os.environ.get("SIP_SERVER", "").strip()
        and os.environ.get("SIP_USERNAME", "").strip()
        and os.environ.get("SIP_PASSWORD", "").strip()
        and os.environ.get("SIP_MY_IP", "").strip()
    )


class CallPhase(str, Enum):
    IDLE = "idle"
    DIALING = "dialing"
    RINGING = "ringing"
    ACTIVE = "active"
    ENDED = "ended"
    FAILED = "failed"


@dataclass
class ActiveCall:
    call_id: str
    phone: str
    phase: CallPhase = CallPhase.DIALING
    error: Optional[str] = None
    wav_path: Optional[str] = None
    started_at: float = field(default_factory=time.time)
    ended_at: Optional[float] = None
    _stop: threading.Event = field(default_factory=threading.Event)
    _thread: Optional[threading.Thread] = None


_lock = threading.Lock()
_calls: Dict[str, ActiveCall] = {}
_phone_singleton = None
_phone_lock = threading.Lock()


def _get_voip_phone():
    global _phone_singleton
    with _phone_lock:
        if _phone_singleton is not None:
            return _phone_singleton
        if not sip_configured():
            return None
        from pyVoIP.VoIP import VoIPPhone

        server = os.environ["SIP_SERVER"].strip()
        port = int(os.environ.get("SIP_PORT", "5060"))
        user = os.environ["SIP_USERNAME"].strip()
        password = os.environ["SIP_PASSWORD"].strip()
        my_ip = os.environ["SIP_MY_IP"].strip()
        phone = VoIPPhone(server, port, user, password, myIP=my_ip)
        phone.start()
        _phone_singleton = phone
        return phone


def _record_loop(active: ActiveCall, voip_call) -> None:
    from pyVoIP.VoIP import CallState

    wav_path = os.path.join(RECORD_DIR, f"{active.call_id}.wav")
    active.wav_path = wav_path
    wf = wave.open(wav_path, "wb")
    wf.setnchannels(1)
    wf.setsampwidth(2)
    wf.setframerate(8000)

    try:
        while not active._stop.is_set():
            state = voip_call.state
            if state == CallState.ANSWERED:
                active.phase = CallPhase.ACTIVE
                try:
                    chunk = voip_call.read_audio(160, blocking=True)
                except Exception:
                    chunk = b""
                if chunk:
                    try:
                        pcm = audioop.ulaw2lin(chunk, 2)
                    except Exception:
                        pcm = chunk if len(chunk) % 2 == 0 else chunk + b"\x00"
                    wf.writeframes(pcm)
                    try:
                        livetranscript.feed_audio(active.call_id, pcm)
                    except Exception:
                        pass
            elif state == CallState.ENDED:
                break
            elif state in (CallState.DIALING, CallState.RINGING):
                active.phase = (
                    CallPhase.RINGING if state == CallState.RINGING else CallPhase.DIALING
                )
            time.sleep(0.02)
    finally:
        wf.close()
        try:
            if voip_call.state == CallState.ANSWERED:
                voip_call.hangup()
        except Exception:
            try:
                voip_call.bye()
            except Exception:
                pass
        active.phase = CallPhase.ENDED
        active.ended_at = time.time()


def _run_call(active: ActiveCall) -> None:
    from pyVoIP.VoIP import CallState

    try:
        phone = _get_voip_phone()
        if phone is None:
            active.phase = CallPhase.FAILED
            active.error = "SIP not configured on server"
            return
        voip_call = phone.call(active.phone)
        deadline = time.time() + 45
        while time.time() < deadline and not active._stop.is_set():
            if voip_call.state == CallState.ANSWERED:
                active.phase = CallPhase.ACTIVE
                _record_loop(active, voip_call)
                return
            if voip_call.state == CallState.ENDED:
                active.phase = CallPhase.FAILED
                active.error = "Call ended before answer"
                return
            if voip_call.state == CallState.RINGING:
                active.phase = CallPhase.RINGING
            time.sleep(0.2)
        if not active._stop.is_set():
            active.phase = CallPhase.FAILED
            active.error = "No answer (timeout)"
            try:
                voip_call.hangup()
            except Exception:
                pass
    except Exception as exc:
        active.phase = CallPhase.FAILED
        active.error = str(exc)


def start_call(phone_number: str, user_lang: str = "en") -> Dict[str, Any]:
    number = normalize_phone(phone_number)
    if not number:
        raise ValueError("Phone number is required")
    if not sip_configured():
        raise RuntimeError("SIP not configured")

    call_id = "c" + uuid.uuid4().hex[:12]
    active = ActiveCall(call_id=call_id, phone=number)
    try:
        livetranscript.start_session(call_id, user_lang=user_lang or "en")
    except Exception:
        pass
    t = threading.Thread(target=_run_call, args=(active,), daemon=True)
    active._thread = t
    with _lock:
        _calls[call_id] = active
    t.start()
    return call_public_status(active)


def stop_call(call_id: str) -> Dict[str, Any]:
    with _lock:
        active = _calls.get(call_id)
    if not active:
        raise KeyError("Call not found")
    active._stop.set()
    if active._thread:
        active._thread.join(timeout=8)
    active.ended_at = active.ended_at or time.time()
    if active.phase not in (CallPhase.FAILED, CallPhase.ENDED):
        active.phase = CallPhase.ENDED
    try:
        livetranscript.stop_session(call_id)
    except Exception:
        pass
    return call_public_status(active)


def get_call(call_id: str) -> Dict[str, Any]:
    with _lock:
        active = _calls.get(call_id)
    if not active:
        raise KeyError("Call not found")
    return call_public_status(active)


def call_public_status(active: ActiveCall) -> Dict[str, Any]:
    return {
        "callId": active.call_id,
        "phone": active.phone,
        "phase": active.phase.value,
        "error": active.error,
        "recordingReady": bool(
            active.wav_path and os.path.isfile(active.wav_path) and os.path.getsize(active.wav_path) > 44
        ),
        "startedAt": active.started_at,
        "endedAt": active.ended_at,
    }


def recording_path(call_id: str) -> str:
    with _lock:
        active = _calls.get(call_id)
    if not active or not active.wav_path or not os.path.isfile(active.wav_path):
        raise FileNotFoundError("Recording not available")
    return active.wav_path


def call_config() -> Dict[str, Any]:
    return {
        "sipConfigured": sip_configured(),
        "provider": "pyVoIP",
        "providerUrl": "https://github.com/tayler6000/pyVoIP",
        "liveTranscriptConfigured": livetranscript.configured(),
    }
