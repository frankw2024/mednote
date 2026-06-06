"""
Live phone-call transcription for MedNote.

During an active call, audio is buffered and transcribed to English in ~3–6 s
chunks (configurable delay). Non-English UI languages get a parallel translation
stream for live display and TTS.

Environment:
  GROQ_API_KEY                 Required for transcription / translation
  LIVE_TRANSCRIPT_CHUNK_SEC      Target seconds of audio per chunk (default 5)
  LIVE_TRANSCRIPT_MIN_CHUNK_SEC  Minimum buffer before first chunk (default 3)
  LIVE_TRANSCRIPT_OVERLAP_SEC    Overlap between chunks to avoid cut words (default 0.75)
"""

from __future__ import annotations

import io
import os
import threading
import time
import wave
from collections import deque
from dataclasses import dataclass, field
from typing import Any, Deque, Dict, List, Optional

try:
    import requests
except ImportError:  # pragma: no cover
    requests = None  # type: ignore

CHUNK_SEC = float(os.environ.get("LIVE_TRANSCRIPT_CHUNK_SEC", "5"))
MIN_CHUNK_SEC = float(os.environ.get("LIVE_TRANSCRIPT_MIN_CHUNK_SEC", "3"))
OVERLAP_SEC = float(os.environ.get("LIVE_TRANSCRIPT_OVERLAP_SEC", "0.75"))
SAMPLE_RATE = 8000
SAMPLE_WIDTH = 2
GROQ_WHISPER_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"

LANG_NAMES: Dict[str, str] = {
    "en": "English",
    "zh": "Chinese",
    "fr": "French",
    "de": "German",
    "ja": "Japanese",
    "ko": "Korean",
    "es": "Spanish",
    "hi": "Hindi",
    "vi": "Vietnamese",
    "ar": "Arabic",
}


@dataclass
class LiveTranscriptSession:
    call_id: str
    user_lang: str = "en"
    english_lines: List[str] = field(default_factory=list)
    translated_lines: List[str] = field(default_factory=list)
    pending_pcm: bytearray = field(default_factory=bytearray)
    seq: int = 0
    active: bool = True
    error: Optional[str] = None
    processing: bool = False
    queue_depth: int = 0
    chunks_done: int = 0
    started_at: float = field(default_factory=time.time)
    last_update_at: Optional[float] = None
    _lock: threading.Lock = field(default_factory=threading.Lock)
    _queue: Deque[bytes] = field(default_factory=deque)
    _worker_running: bool = False


_lock = threading.Lock()
_sessions: Dict[str, LiveTranscriptSession] = {}


def _groq_key() -> str:
    return os.environ.get("GROQ_API_KEY", "").strip()


def configured() -> bool:
    return bool(_groq_key()) and requests is not None


def _chunk_threshold() -> int:
    return int(SAMPLE_RATE * SAMPLE_WIDTH * CHUNK_SEC)


def _min_threshold() -> int:
    return int(SAMPLE_RATE * SAMPLE_WIDTH * MIN_CHUNK_SEC)


def _overlap_bytes() -> int:
    return int(SAMPLE_RATE * SAMPLE_WIDTH * OVERLAP_SEC)


def _buffered_seconds(pending_len: int) -> float:
    if pending_len <= 0:
        return 0.0
    return pending_len / (SAMPLE_RATE * SAMPLE_WIDTH)


def _pcm_to_wav(pcm: bytes, sample_rate: int = SAMPLE_RATE) -> bytes:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(SAMPLE_WIDTH)
        wf.setframerate(sample_rate)
        wf.writeframes(pcm)
    return buf.getvalue()


def _transcribe_english(pcm: bytes) -> Optional[str]:
    key = _groq_key()
    if not key or not pcm or requests is None:
        return None
    if len(pcm) < SAMPLE_RATE * SAMPLE_WIDTH:
        return None
    wav = _pcm_to_wav(pcm)
    try:
        r = requests.post(
            GROQ_WHISPER_URL,
            headers={"Authorization": f"Bearer {key}"},
            files={"file": ("chunk.wav", wav, "audio/wav")},
            data={
                "model": "whisper-large-v3-turbo",
                "language": "en",
                "response_format": "json",
                "temperature": "0",
            },
            timeout=45,
        )
        if not r.ok:
            return None
        text = (r.json().get("text") or "").strip()
        return text or None
    except Exception:
        return None


def _translate(text: str, target_lang: str) -> Optional[str]:
    if not text or target_lang == "en":
        return text
    key = _groq_key()
    if not key or requests is None:
        return None
    lang_name = LANG_NAMES.get(target_lang, target_lang)
    try:
        r = requests.post(
            GROQ_CHAT_URL,
            headers={
                "Authorization": f"Bearer {key}",
                "Content-Type": "application/json",
            },
            json={
                "model": "llama-3.3-70b-versatile",
                "messages": [
                    {
                        "role": "system",
                        "content": (
                            f"Medical conversation translator. Translate to {lang_name}. "
                            "Keep drug names and numbers accurate. Return only the translation."
                        ),
                    },
                    {"role": "user", "content": text},
                ],
                "temperature": 0.1,
            },
            timeout=45,
        )
        if not r.ok:
            return None
        content = r.json()["choices"][0]["message"]["content"]
        return (content or "").strip() or None
    except Exception:
        return None


def _normalize(text: str) -> str:
    return " ".join((text or "").lower().split())


def _is_duplicate(session: LiveTranscriptSession, text: str) -> bool:
    if not session.english_lines:
        return False
    cur = _normalize(text)
    if len(cur) < 4:
        return True
    last = _normalize(session.english_lines[-1])
    if cur == last:
        return True
    if cur in last or last in cur:
        return True
    return False


def _append_segment(session: LiveTranscriptSession, english_text: str) -> None:
    line = english_text.strip()
    if not line or _is_duplicate(session, line):
        return
    session.english_lines.append(line)
    if session.user_lang != "en":
        translated = _translate(line, session.user_lang)
        session.translated_lines.append(translated or line)
    session.seq += 1
    session.chunks_done += 1
    session.last_update_at = time.time()


def _worker_loop(session: LiveTranscriptSession) -> None:
    while True:
        pcm: Optional[bytes] = None
        with session._lock:
            if session._queue:
                pcm = session._queue.popleft()
                session.queue_depth = len(session._queue)
                session.processing = True
            elif not session.active:
                session.processing = False
                session.queue_depth = 0
                session._worker_running = False
                break
            else:
                session.processing = False
        if pcm is None:
            time.sleep(0.08)
            continue
        try:
            if not configured():
                with session._lock:
                    session.error = "GROQ_API_KEY not set on server"
                continue
            text = _transcribe_english(pcm)
            if text:
                with session._lock:
                    _append_segment(session, text)
        finally:
            with session._lock:
                if not session._queue:
                    session.processing = False
                session.queue_depth = len(session._queue)


def _ensure_worker(session: LiveTranscriptSession) -> None:
    with session._lock:
        if session._worker_running:
            return
        session._worker_running = True
    threading.Thread(target=_worker_loop, args=(session,), daemon=True).start()


def _enqueue_pcm(session: LiveTranscriptSession, pcm: bytes) -> None:
    with session._lock:
        session._queue.append(pcm)
        session.queue_depth = len(session._queue)
    _ensure_worker(session)


def _maybe_cut_chunk(session: LiveTranscriptSession) -> None:
    threshold = _chunk_threshold()
    min_threshold = _min_threshold()
    overlap = min(_overlap_bytes(), threshold // 4)
    with session._lock:
        pending_len = len(session.pending_pcm)
        if pending_len < min_threshold:
            return
        cut_at = threshold if pending_len >= threshold else pending_len
        chunk = bytes(session.pending_pcm[:cut_at])
        keep_from = max(0, cut_at - overlap)
        del session.pending_pcm[:keep_from]
    _enqueue_pcm(session, chunk)


def start_session(call_id: str, user_lang: str = "en") -> None:
    lang = (user_lang or "en").strip().lower()[:8]
    session = LiveTranscriptSession(call_id=call_id, user_lang=lang)
    if not configured():
        session.error = "GROQ_API_KEY not set on server"
    with _lock:
        _sessions[call_id] = session


def feed_audio(call_id: str, pcm: bytes) -> None:
    if not pcm:
        return
    with _lock:
        session = _sessions.get(call_id)
    if not session or not session.active:
        return

    with session._lock:
        session.pending_pcm.extend(pcm)
    _maybe_cut_chunk(session)


def _flush_session(session: LiveTranscriptSession) -> None:
    with session._lock:
        pcm = bytes(session.pending_pcm)
        session.pending_pcm.clear()
        session.active = False
    min_bytes = SAMPLE_RATE * SAMPLE_WIDTH
    if len(pcm) >= min_bytes:
        _enqueue_pcm(session, pcm)


def stop_session(call_id: str) -> None:
    with _lock:
        session = _sessions.get(call_id)
    if not session:
        return
    _flush_session(session)


def get_transcript(call_id: str, since_seq: int = 0) -> Dict[str, Any]:
    with _lock:
        session = _sessions.get(call_id)
    if not session:
        raise KeyError("Live transcript session not found")

    with session._lock:
        buffered = _buffered_seconds(len(session.pending_pcm))
        status = "transcribing" if session.processing or session.queue_depth else (
            "listening" if session.active else "done"
        )
        return {
            "callId": call_id,
            "seq": session.seq,
            "userLang": session.user_lang,
            "english": list(session.english_lines),
            "translated": list(session.translated_lines) if session.user_lang != "en" else [],
            "configured": configured(),
            "error": session.error,
            "hasUpdate": session.seq > since_seq,
            "processing": session.processing,
            "queueDepth": session.queue_depth,
            "bufferedSec": round(buffered, 1),
            "chunksDone": session.chunks_done,
            "status": status,
            "chunkSec": CHUNK_SEC,
            "minChunkSec": MIN_CHUNK_SEC,
        }


def drop_session(call_id: str) -> None:
    with _lock:
        session = _sessions.pop(call_id, None)
    if session:
        with session._lock:
            session.active = False
