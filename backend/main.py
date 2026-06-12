from fastapi import FastAPI, HTTPException, Request, WebSocket
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse, Response
from pydantic import BaseModel
from typing import List, Optional, Any
import sqlite3, json, os, hashlib, time

import call as call_service
from customerService.routes import router as customer_service_router
import livetranscript as live_transcript_service
import twilio_call as twilio_call_service

app = FastAPI(title="MedNote API", version="1.0.0")

# ── CORS ──────────────────────────────────────────────────────────────────────
# Allow any origin so the HTML file works whether opened locally,
# from GitHub Pages, or any other host.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(customer_service_router)

# ── DATABASE ──────────────────────────────────────────────────────────────────
DB_PATH = os.environ.get("DB_PATH", "mednote.db")

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db()
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS users (
            id          TEXT PRIMARY KEY,
            name        TEXT NOT NULL DEFAULT 'User',
            created_at  INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS visits (
            id          TEXT PRIMARY KEY,
            user_id     TEXT NOT NULL,
            data        TEXT NOT NULL,
            updated_at  INTEGER NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS reminders (
            id          TEXT PRIMARY KEY,
            user_id     TEXT NOT NULL,
            data        TEXT NOT NULL,
            updated_at  INTEGER NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id)
        );
    """)
    conn.commit()
    conn.close()

init_db()

# ── MODELS ────────────────────────────────────────────────────────────────────
class UserInit(BaseModel):
    userId: str
    name: Optional[str] = "User"

class SyncPayload(BaseModel):
    userId: str
    visits: List[Any]
    reminders: List[Any]

class VisitPayload(BaseModel):
    userId: str
    visit: Any                  # single visit object

class ReminderPayload(BaseModel):
    userId: str
    reminder: Any               # single reminder object

class CallStartPayload(BaseModel):
    phone: str
    userId: Optional[str] = None
    userLang: Optional[str] = "en"

class CallStopPayload(BaseModel):
    callId: str

# ── HELPERS ───────────────────────────────────────────────────────────────────
def ensure_user(user_id: str, name: str = "User"):
    conn = get_db()
    exists = conn.execute("SELECT id FROM users WHERE id=?", (user_id,)).fetchone()
    if not exists:
        conn.execute(
            "INSERT INTO users(id,name,created_at) VALUES(?,?,?)",
            (user_id, name, int(time.time()))
        )
        conn.commit()
    conn.close()

# ── ROUTES ────────────────────────────────────────────────────────────────────

@app.get("/")
def root():
    return {"status": "ok", "service": "MedNote API"}

@app.get("/health")
def health():
    return {"status": "ok"}

@app.get("/ping")
def ping():
    return {"status": "ok"}

# ── User ──────────────────────────────────────────────────────────────────────
@app.post("/api/user")
def create_or_get_user(payload: UserInit):
    """Create user if not exists, return user info."""
    ensure_user(payload.userId, payload.name)
    conn = get_db()
    row = conn.execute("SELECT * FROM users WHERE id=?", (payload.userId,)).fetchone()
    conn.close()
    return {"userId": row["id"], "name": row["name"], "createdAt": row["created_at"]}

@app.get("/api/user/{user_id}")
def get_user(user_id: str):
    conn = get_db()
    row = conn.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
    conn.close()
    if not row:
        raise HTTPException(status_code=404, detail="User not found")
    return {"userId": row["id"], "name": row["name"], "createdAt": row["created_at"]}

# ── Full sync (push everything at once) ───────────────────────────────────────
@app.post("/api/sync")
def sync(payload: SyncPayload):
    """
    Replace all visits + reminders for a user.
    Called on app load and after every significant change.
    """
    ensure_user(payload.userId)
    conn = get_db()
    now = int(time.time())

    # Upsert every visit
    conn.execute("DELETE FROM visits WHERE user_id=?", (payload.userId,))
    for v in payload.visits:
        conn.execute(
            "INSERT INTO visits(id,user_id,data,updated_at) VALUES(?,?,?,?)",
            (v.get("id", f"v{now}"), payload.userId, json.dumps(v), now)
        )

    # Upsert every reminder
    conn.execute("DELETE FROM reminders WHERE user_id=?", (payload.userId,))
    for r in payload.reminders:
        conn.execute(
            "INSERT INTO reminders(id,user_id,data,updated_at) VALUES(?,?,?,?)",
            (str(r.get("id", now)), payload.userId, json.dumps(r), now)
        )

    conn.commit()
    conn.close()
    return {"ok": True, "synced": {"visits": len(payload.visits), "reminders": len(payload.reminders)}}

# ── Pull all records ───────────────────────────────────────────────────────────
@app.get("/api/records/{user_id}")
def get_records(user_id: str):
    """Fetch all visits + reminders for a user."""
    conn = get_db()
    visits   = [json.loads(r["data"]) for r in conn.execute(
        "SELECT data FROM visits WHERE user_id=? ORDER BY updated_at DESC", (user_id,)
    ).fetchall()]
    reminders = [json.loads(r["data"]) for r in conn.execute(
        "SELECT data FROM reminders WHERE user_id=? ORDER BY updated_at ASC", (user_id,)
    ).fetchall()]
    conn.close()
    return {"visits": visits, "reminders": reminders}

# ── Single visit CRUD ──────────────────────────────────────────────────────────
@app.post("/api/visit")
def save_visit(payload: VisitPayload):
    ensure_user(payload.userId)
    conn = get_db()
    vid = payload.visit.get("id", f"v{int(time.time())}")
    conn.execute(
        "INSERT OR REPLACE INTO visits(id,user_id,data,updated_at) VALUES(?,?,?,?)",
        (vid, payload.userId, json.dumps(payload.visit), int(time.time()))
    )
    conn.commit()
    conn.close()
    return {"ok": True, "id": vid}

@app.delete("/api/visit/{user_id}/{visit_id}")
def delete_visit(user_id: str, visit_id: str):
    conn = get_db()
    conn.execute("DELETE FROM visits WHERE id=? AND user_id=?", (visit_id, user_id))
    conn.commit()
    conn.close()
    return {"ok": True}

# ── Single reminder CRUD ───────────────────────────────────────────────────────
@app.post("/api/reminder")
def save_reminder(payload: ReminderPayload):
    ensure_user(payload.userId)
    conn = get_db()
    rid = str(payload.reminder.get("id", int(time.time())))
    conn.execute(
        "INSERT OR REPLACE INTO reminders(id,user_id,data,updated_at) VALUES(?,?,?,?)",
        (rid, payload.userId, json.dumps(payload.reminder), int(time.time()))
    )
    conn.commit()
    conn.close()
    return {"ok": True, "id": rid}

@app.delete("/api/reminder/{user_id}/{reminder_id}")
def delete_reminder(user_id: str, reminder_id: str):
    conn = get_db()
    conn.execute("DELETE FROM reminders WHERE id=? AND user_id=?", (reminder_id, user_id))
    conn.commit()
    conn.close()
    return {"ok": True}

# ── Phone calls (pyVoIP — optional SIP credentials) ───────────────────────────
@app.get("/api/call/config")
def call_config():
    return call_service.call_config()

@app.post("/api/call/start")
def call_start(payload: CallStartPayload):
    try:
        return call_service.start_call(payload.phone, user_lang=payload.userLang or "en")
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/call/live-transcript/{call_id}")
def call_live_transcript(call_id: str, since: int = 0):
    try:
        return live_transcript_service.get_transcript(call_id, since_seq=since)
    except KeyError:
        raise HTTPException(status_code=404, detail="Live transcript not found")

@app.get("/api/call/status/{call_id}")
def call_status(call_id: str):
    try:
        if twilio_call_service.has_call(call_id):
            return twilio_call_service.get_call(call_id)
        return call_service.get_call(call_id)
    except KeyError:
        raise HTTPException(status_code=404, detail="Call not found")

@app.post("/api/call/stop")
def call_stop(payload: CallStopPayload):
    try:
        if twilio_call_service.has_call(payload.callId):
            return twilio_call_service.stop_call(payload.callId)
        return call_service.stop_call(payload.callId)
    except KeyError:
        raise HTTPException(status_code=404, detail="Call not found")

@app.get("/api/call/recording/{call_id}")
def call_recording(call_id: str):
    try:
        if twilio_call_service.has_call(call_id):
            path = twilio_call_service.recording_path(call_id)
        else:
            path = call_service.recording_path(call_id)
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail="Recording not found")
    return FileResponse(path, media_type="audio/wav", filename=f"call_{call_id}.wav")

# ── Twilio Voice (browser WebRTC → PSTN) ─────────────────────────────────────
@app.post("/api/call/twilio/token")
def twilio_token(payload: CallStartPayload):
    identity = (payload.userId or "mednote-user").strip() or "mednote-user"
    try:
        return twilio_call_service.prepare_call(
            payload.phone, user_lang=payload.userLang or "en", identity=identity
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/call/twilio/health")
def twilio_health():
    """Browser-friendly check that Twilio routes are deployed and configured."""
    return {
        "ok": True,
        "configured": twilio_call_service.configured(),
        "mediaStreamUrl": twilio_call_service.media_stream_url() or None,
        "voiceWebhook": f"{twilio_call_service.public_base_url()}/api/call/twilio/voice"
        if twilio_call_service.public_base_url()
        else None,
        "browserTestNote": (
            "Opening /api/call/twilio/voice in a browser without To and callId is normal — "
            "that does not mean setup failed. Real calls from MedNote send those via POST."
        ),
    }


@app.get("/api/call/twilio/voice")
@app.post("/api/call/twilio/voice")
async def twilio_voice(request: Request):
    params = dict(request.query_params)
    if request.method == "POST":
        form = await request.form()
        params.update({k: str(v) for k, v in form.items()})
    twiml = twilio_call_service.handle_voice_webhook(params)
    return Response(content=twiml, media_type="application/xml")


@app.websocket("/api/call/twilio/media")
async def twilio_media(websocket: WebSocket):
    await websocket.accept()
    await twilio_call_service.handle_media_websocket(websocket)


@app.post("/api/call/twilio/recording")
async def twilio_recording_status(request: Request):
    form = await request.form()
    twilio_call_service.handle_recording_callback({k: str(v) for k, v in form.items()})
    return {"ok": True}

# ── Serve frontend (optional — if you want one-server deployment) ──────────────
# If RecallMD_web.html and RecallMD_v12.html are in a `static/` folder,
# this serves them at /web and /mobile
STATIC_DIR = os.path.join(os.path.dirname(__file__), "static")
if os.path.isdir(STATIC_DIR):
    app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")

    @app.get("/web")
    def serve_web():
        return FileResponse(os.path.join(STATIC_DIR, "RecallMD_web.html"))

    @app.get("/mobile")
    def serve_mobile():
        return FileResponse(os.path.join(STATIC_DIR, "RecallMD_v12.html"))
