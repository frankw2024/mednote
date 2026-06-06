# MedNote Backend

FastAPI + SQLite backend that adds cloud sync to MedNote.
Deploy to Render free tier in under 5 minutes.

---

## Run Locally

```bash
cd backend
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

Once running, visit `http://localhost:8000/docs` for the interactive API docs.

Then in your HTML file, update one line:
```js
window.MEDNOTE_API_BASE = "http://localhost:8000";
```

---

## Deploy to Render (Free, ~5 min)

### Step 1 — Push to GitHub
```bash
git init
git add .
git commit -m "initial commit"
git remote add origin https://github.com/frankw2024/mednote.git
git push -u origin main
```

### Step 2 — Connect to Render (Blueprint — fastest)

1. Push this repo to GitHub (see Step 1 below if needed).
2. Go to [Render Dashboard → Blueprints](https://dashboard.render.com/blueprints) → **New Blueprint Instance**.
3. Connect GitHub → select **`frankw2024/mednote`**.
4. Render reads **`render.yaml`** at the repo root (`rootDir: backend`, service name **`fwang2024`**).
5. Click **Apply** — Render creates the web service at **`https://fwang2024.onrender.com`**.

**Manual setup** (same result):

1. Go to [render.com](https://render.com) → **New** → **Web Service**
2. Connect your GitHub account → select the `mednote` repo
3. Fill in the settings:

| Field | Value |
|---|---|
| Name | `fwang2024` |
| Environment | `Python 3` |
| Build Command | `pip install -r requirements.txt` |
| Start Command | `uvicorn main:app --host 0.0.0.0 --port $PORT` |

4. Click **Advanced** → **Add Environment Variable**:
   - Key: `DB_PATH`  →  Value: `/tmp/mednote.db`

5. Click **Create Web Service**

### Step 3 — Paste the URL into your HTML
Once deployed, the API URL is **`https://fwang2024.onrender.com`**.

The HTML files auto-select this URL on GitHub Pages. To set it manually:
```js
window.MEDNOTE_API_BASE = "https://fwang2024.onrender.com";
```

---

## ⚠️ Render Free Tier — Known Limitations

| Issue | What happens | How it's handled |
|---|---|---|
| **Cold start** | Server sleeps after 15 min of inactivity → first request takes ~30s | HTML pings `/ping` every 10 min while the tab is open — keeps server awake during your demo |
| **DB resets on redeploy** | `/tmp/mednote.db` is wiped when Render redeploys | Fine for a hackathon demo. For persistence across deploys, add a Render Disk (mount at `/data`, set `DB_PATH=/data/mednote.db`) |

---

## API Reference

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | Health check |
| `GET` | `/ping` | Keep-alive (prevents cold start during demo) |
| `POST` | `/api/user` | Create or get a user |
| `POST` | `/api/sync` | Push all visits + reminders |
| `GET` | `/api/records/{userId}` | Pull all visits + reminders |
| `POST` | `/api/visit` | Save a single visit |
| `DELETE` | `/api/visit/{userId}/{visitId}` | Delete a visit |
| `POST` | `/api/reminder` | Save a single reminder |
| `DELETE` | `/api/reminder/{userId}/{reminderId}` | Delete a reminder |
| `GET` | `/api/call/config` | SIP / pyVoIP availability |
| `GET` | `/api/call/live-transcript/{callId}` | Live English transcript + translation (poll every ~1s) |
| `POST` | `/api/call/start` | Place outbound call `{ "phone": "+15551234567", "userLang": "zh" }` |
| `GET` | `/api/call/status/{callId}` | Call phase (dialing / active / ended) |
| `POST` | `/api/call/stop` | Hang up and finalize recording |
| `GET` | `/api/call/recording/{callId}` | Download call WAV |

---

## Phone calls (optional — pyVoIP)

Server-side calling uses [pyVoIP](https://github.com/tayler6000/pyVoIP) when SIP credentials are set.
Without SIP, the app **Call** button opens the phone dialer and records via the microphone (speakerphone).

Set these environment variables on the backend (local `.env` or Render dashboard):

| Variable | Example |
|---|---|
| `SIP_SERVER` | `sip.linphone.org` |
| `SIP_PORT` | `5060` |
| `SIP_USERNAME` | your SIP username |
| `SIP_PASSWORD` | your SIP password |
| `SIP_MY_IP` | your public IP (required for RTP) |
| `CALL_RECORD_DIR` | `/tmp/mednote_calls` |
| `GROQ_API_KEY` | Groq key for live call transcription + translation (Whisper + Llama) |
| `LIVE_TRANSCRIPT_CHUNK_SEC` | Seconds of audio per live transcript chunk (default `5`) |
| `LIVE_TRANSCRIPT_MIN_CHUNK_SEC` | Minimum buffer before first chunk (default `3`) |
| `LIVE_TRANSCRIPT_OVERLAP_SEC` | Overlap between chunks (default `0.75`) |

Free SIP accounts: [Linphone](https://linphone.org), [Antisip](https://www.antisip.com), or any VoIP provider that supports SIP + PSTN outbound.

**Note:** Render’s free tier may block UDP/RTP needed for SIP. Local backend (`localhost:8000`) works best for VoIP calls; GitHub Pages + local mic fallback works everywhere.

## Architecture

```
Browser (local HTML file)
  │
  ├─ On load:          GET  /api/records/{userId}   → pull saved data
  ├─ On data change:   POST /api/sync               → auto-save (1.5s debounce)
  └─ Every 10 min:     GET  /ping                   → prevent cold start
  │
  ▼
FastAPI  (Render free tier)
  │
  ▼
SQLite  (/tmp/mednote.db)
  ├── users
  ├── visits
  └── reminders
```

Users are identified by a random `userId` generated on first visit and stored in `localStorage` — no login required. Suitable for a hackathon demo; add JWT auth for production.
