# MedNote 🩺📓

> **AI-powered medical visit recorder & health assistant — runs entirely in your browser, zero backend required.**

**Author:** [fwang2024](https://github.com/frankw2024)

MedNote turns doctor appointments into structured, multilingual health records. Record or upload a consultation, and AI automatically transcribes, summarises, and extracts medications, appointments, and action items — all in the patient's chosen language.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🎙️ **Live Recording** | Record consultations in real-time with live speech-to-text transcription |
| 📤 **Audio Upload** | Upload MP3, M4A, WAV, AAC, or WebM files (including iPhone Voice Memos) |
| 📄 **Document Upload** | Upload PDFs or images of doctor notes, lab results, and prescriptions |
| 🧠 **AI Summarisation** | Auto-generates structured summaries with Findings, Medications, Watch-for, Next Steps |
| 💊 **Medication Extraction** | Pulls medications from transcripts and adds them to your reminder schedule |
| 📅 **Smart Reminders** | Timed medication and appointment reminders with overdue alerts |
| 🌐 **10 Languages** | Full UI + AI output in EN, 中文, FR, DE, 日本語, 한국어, ES, हिंदी, Tiếng Việt, عربي |
| 📆 **Calendar Export** | Export reminders to Google Calendar, Apple Calendar (.ics), and Outlook |
| 🤖 **AI Chat** | Ask your health assistant questions about your visit records |
| 🌙 **Dark / Light Mode** | Fully themed UI for both modes |
| 👴 **Elder Mode** | Larger fonts and simplified navigation for accessibility |
| 🔒 **100% Client-side** | All data stays in your browser — no server, no account required |

---

## 📁 Files

```
mednote/
├── RecallMD_web.html      # Desktop web layout (sidebar navigation)
├── RecallMD_v12.html      # Mobile-first layout (bottom tab bar)
└── README.md
```

Both files are fully self-contained single-page apps — no build step, no dependencies to install.

---

## 🚀 Quick Start

**Live URLs**

| | URL |
|---|---|
| Phone / web app | https://frankw2024.github.io/mednote/RecallMD_v12.html |
| Sync API (Render) | https://fwang2024.onrender.com |

1. **Download** either HTML file, or open a live URL above
2. **Open** it in any modern browser (Chrome, Safari, Firefox, Edge)
3. *(Optional)* Add API keys in **⚙️ Settings** to unlock AI features:
   - **Groq** — free key at [console.groq.com](https://console.groq.com) → powers transcription (Whisper) + AI summaries (Llama 3.3 70B)
   - **Gemini** — key at [aistudio.google.com](https://aistudio.google.com) → powers AI summaries (Gemini 2.0 Flash)

> Without API keys, live browser speech-to-text still works for recording. AI summaries and file transcription require at least one key.

---

## 🔑 API Keys

MedNote is free to run. The AI features use third-party APIs that have generous free tiers:

| Service | Used For | Free Tier |
|---|---|---|
| [Groq](https://console.groq.com) | Audio transcription (Whisper) + AI summaries (Llama 3.3) | ✅ Free |
| [Google Gemini](https://aistudio.google.com) | AI summaries + document analysis | ✅ Free |

Keys are stored only in your browser's `localStorage` — never sent anywhere except directly to those APIs.

---

## 🌐 Supported Languages

English · 中文 (Chinese) · Français · Deutsch · 日本語 · 한국어 · Español · हिंदी · Tiếng Việt · العربية

All AI-generated content (summaries, extracted reminders, chat responses) is delivered in the user's selected language.

---

## 🏗️ Tech Stack

- **React 18** (via CDN, no build step)
- **Babel Standalone** (JSX in-browser transpilation)
- **Groq Whisper API** — audio transcription
- **Groq Llama 3.3 70B** — AI summaries & chat
- **Google Gemini 2.0 Flash / 1.5 Flash** — AI summaries & document analysis
- **Web Speech API** — live browser STT (no key needed)
- **LocalStorage** — persistent client-side data

---

## 📱 Layouts

### `RecallMD_web.html` — Desktop
Full sidebar layout with persistent navigation. Best for tablets and desktop browsers.

### `RecallMD_v12.html` — Mobile
Bottom tab bar with compact header. Optimised for phones.

---

## 🔐 Privacy

- **No account required**
- **No data sent to any server** (other than directly to Groq/Gemini when you use those features)
- **All records stored in browser localStorage**
- **API keys never leave your device**

---

## 🤝 Contributing

Pull requests welcome. Some ideas for contributions:

- [ ] PWA / offline support with Service Worker
- [ ] Export records as PDF
- [ ] More language support
- [ ] Sync across devices (optional cloud backend)
- [ ] Custom user profiles (default display name in Settings)

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

<p align="center">Made with ❤️ for patients and caregivers everywhere</p>
