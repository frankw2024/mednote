# MedNote Hosted Backend Status Report

**Date:** 2025-06-05  
**Scope:** Backend deployment configuration (`backend/`) and live availability of the configured production API  
**Verdict:** The frontend is wired for a Railway-hosted FastAPI backend, but the configured Railway URL is **not reachable** (`Application not found`). Cloud sync is effectively broken until the backend is redeployed or the frontend URL is updated.

---

## Executive Summary

MedNote uses a **split architecture**:

| Layer | Host | Role |
|-------|------|------|
| Frontend | GitHub Pages (static HTML) | Serves `index.html` and related SPA files |
| AI features | Groq / Google Gemini (called from browser) | Transcription, summarisation, chat, document analysis |
| Sync / storage | Railway or Render (intended) | FastAPI + SQLite for visits and reminders |

GitHub hosts only the static web app. The backend is designed to run on a third-party PaaS (Railway or Render), not on GitHub Pages or GitHub Actions.

The repo currently points all HTML entry points at:

```js
window.MEDNOTE_API_BASE = "https://mednote-production-63a5.up.railway.app";
```

Live testing of that URL on 2025-06-05 returned **HTTP 404** with body:

```json
{"status":"error","code":404,"message":"Application not found","request_id":"..."}
```

This is a Railway platform response indicating the service is **down, deleted, renamed, or never deployed** at that hostname—not a MedNote application error.

---

## Architecture

```
GitHub Pages
  └── Serves static index.html / RecallMD_*.html

Browser (client)
  ├── Groq API          → audio transcription (Whisper), chat (Llama fallback)
  ├── Gemini API        → summaries, document analysis, preferred chat path
  └── MedNote API base  → sync visits/reminders (currently broken URL)

Railway / Render (intended)
  └── FastAPI + SQLite
        ├── users
        ├── visits
        └── reminders
```

### What runs where

- **GitHub Pages:** Static assets only. No server-side logic, no database, no API keys stored in the repo.
- **Browser:** All AI calls go directly from the client to Groq/Gemini using keys stored in `localStorage` (`recallmd_groq`, `recallmd_gemini`).
- **Backend:** Optional cloud sync. If `MEDNOTE_API_BASE` is empty, the app runs in local-only mode (data stays in `localStorage`).

---

## Frontend Configuration

All three HTML entry points hard-code the same Railway URL:

| File | `MEDNOTE_API_BASE` |
|------|---------------------|
| `index.html` | `https://mednote-production-63a5.up.railway.app` |
| `RecallMD_web.html` | `https://mednote-production-63a5.up.railway.app` |
| `RecallMD_v12.html` | `https://mednote-production-63a5.up.railway.app` |

Commented alternatives in source suggest prior or alternate deployments:

```js
// window.MEDNOTE_API_BASE = "https://mednote-api.up.railway.app";
// window.MEDNOTE_API_BASE = "http://localhost:8000";  // local dev
```

`backend/README.md` documents a Render example:

```js
window.MEDNOTE_API_BASE = "https://fwang2024.onrender.com";
```

### Frontend sync behaviour

When `API_BASE` is set, the client:

1. **On load:** `POST /api/user`, then `GET /api/records/{userId}` to pull cloud data
2. **On change:** `POST /api/sync` (debounced 1.5s) to push visits and reminders
3. **Every 10 min:** `GET /ping` keep-alive to reduce Render cold starts

User identity is a random ID in `localStorage` (`recallmd_uid`); no login/JWT.

---

## Backend Implementation (`backend/`)

### Stack

- **Framework:** FastAPI 0.115+
- **Server:** Uvicorn
- **Database:** SQLite (`DB_PATH` env var, default `mednote.db`)
- **CORS:** `allow_origins=["*"]` so the static app works from GitHub Pages, localhost, or `file://`

### Deployment manifests

| File | Platform | Start command |
|------|----------|---------------|
| `Procfile` | Heroku-style / generic | `uvicorn main:app --host 0.0.0.0 --port $PORT` |
| `railway.json` | Railway | Same; health check on `/health` |
| `render.yaml` | Render | Same; `DB_PATH=/tmp/mednote.db` |

### API endpoints (implemented in `main.py`)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/` | Service info: `{"status":"ok","service":"MedNote API"}` |
| `GET` | `/health` | Health check: `{"status":"ok"}` |
| `POST` | `/api/user` | Create or get user |
| `GET` | `/api/user/{user_id}` | Get user |
| `POST` | `/api/sync` | Replace all visits + reminders for a user |
| `GET` | `/api/records/{user_id}` | Pull all visits + reminders |
| `POST` | `/api/visit` | Upsert single visit |
| `DELETE` | `/api/visit/{user_id}/{visit_id}` | Delete visit |
| `POST` | `/api/reminder` | Upsert single reminder |
| `DELETE` | `/api/reminder/{user_id}/{reminder_id}` | Delete reminder |

Optional static routes (`/web`, `/mobile`) exist if a `static/` folder is present; not required for GitHub Pages deployment.

### Local verification

```bash
cd backend
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
python test_api.py   # smoke test against localhost:8000
```

Interactive docs: `http://localhost:8000/docs`

---

## Live Production Test Results

**Target:** `https://mednote-production-63a5.up.railway.app`

| Endpoint | Expected | Actual (2025-06-05) |
|----------|----------|---------------------|
| `GET /health` | `200`, `{"status":"ok"}` | `404`, `Application not found` |
| `GET /` | `200`, service JSON | `404`, `Application not found` |

**Interpretation:** The Railway project or service behind this hostname is unavailable. Common causes:

- Service deleted or never deployed on Railway
- Railway project renamed or URL changed after redeploy
- Billing/plan suspension on the Railway account
- Wrong URL left in the frontend after migration to another host (e.g. Render)

---

## Known Gaps and Risks

### 1. Configured backend is down

Cloud sync, pull-on-load, and cross-device persistence **do not work** for users of the GitHub Pages build until `MEDNOTE_API_BASE` points to a live backend.

The app degrades gracefully: sync errors are logged to the console and `syncStatus` may show `"error"`, but local `localStorage` data still works.

### 2. Missing `/ping` route

The frontend and `backend/README.md` reference `GET /ping` for keep-alive, but **`main.py` does not define `/ping`**. On a live backend this would return 404. Impact is low (keep-alive is optional; primary sync uses `/health`-backed deploy checks on Railway/Render), but the docs and code are inconsistent.

### 3. Render free-tier data persistence

`render.yaml` sets `DB_PATH=/tmp/mednote.db`. On Render’s free tier, `/tmp` is wiped on redeploy. Documented in `backend/README.md` as acceptable for demos; not suitable for long-term production without a persistent disk.

### 4. No authentication

Users are identified only by client-generated `userId`. Anyone who knows or guesses an ID could read/write that user’s records. Fine for hackathon/demo; not production-ready.

### 5. AI vs sync separation

Groq/Gemini keys are **not** handled by the backend. AI features work independently of backend health, as long as the user has configured API keys in Settings.

---

## Recommendations

1. **Redeploy the backend** on Railway or Render using `backend/` and update `MEDNOTE_API_BASE` in all HTML files to the new URL.
2. **Verify health** after deploy: `GET {API_BASE}/health` should return `200` and `{"status":"ok"}`.
3. **Add `/ping`** to `main.py` (e.g. alias to `/health`) or remove keep-alive calls from the frontend to match implementation.
4. **Document the live URL** in README or environment-specific config so stale Railway hostnames are not committed long-term.
5. **For production:** add auth, persistent DB storage, and consider moving API base URL to a build-time or user-configurable setting instead of hard-coding.

---

## Bottom Line

| Component | Status |
|-----------|--------|
| GitHub Pages frontend | Intended static host; repo configured for external API |
| Railway backend (`mednote-production-63a5.up.railway.app`) | **Not working** — `Application not found` |
| Backend code in repo | Present and deployable (FastAPI + SQLite) |
| AI (Groq/Gemini) | Browser-direct; independent of backend |
| Cloud sync | **Broken** until backend is redeployed and URL updated |

GitHub is only hosting the static web app. The backend is meant to run on Railway or Render, and the **currently configured Railway backend is not operational**.
