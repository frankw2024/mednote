# MedNote for iPhone

Native SwiftUI app for recording doctor phone calls and in-person visits, with the same AI pipeline as the MedNote web app (Groq Whisper + Gemini/Groq summaries).

## Features

| Tab | What it does |
|-----|----------------|
| **Home** | Quick links + recent visits |
| **Call** | One-tap: start recording → dial doctor → end call → auto transcribe & summarize |
| **Record** | In-person clinic recording |
| **Visits** | Past visits with transcript, summary, extracted meds/actions |
| **Settings** | Groq/Gemini API keys (Keychain), language, default phone |

### Phone call workflow

1. Enter doctor's number (saved automatically)
2. Tap **Call & Record**
3. MedNote starts the microphone, then opens the Phone app
4. Use **speakerphone** during the call
5. Return to MedNote → **End Call & Transcribe**
6. Or **Import Voice Memo** if iOS paused the mic during the cellular call

> **Note:** iOS does not allow any app to record cellular calls directly. This app records via the microphone (speakerphone) or lets you import a Voice Memo — same constraint as Safari.

## Requirements

- Mac with **Xcode 15+**
- iPhone with **iOS 17+**
- Free [Groq API key](https://console.groq.com) (transcription + summaries)
- Optional [Gemini API key](https://aistudio.google.com)

## Install on your iPhone

### Option A — Xcode (recommended for development)

1. Clone this repo on a Mac
2. Open `app/ios/MedNote.xcodeproj` in Xcode
3. Select your **Apple ID** under Signing & Capabilities → Team
4. Connect your iPhone via USB (or use wireless debugging)
5. Choose your iPhone as the run destination
6. Press **Run** (⌘R)
7. On first install: iPhone → **Settings → General → VPN & Device Management** → trust your developer certificate

### Option B — TestFlight (for sharing)

1. Enroll in the [Apple Developer Program](https://developer.apple.com) ($99/year)
2. Archive the app in Xcode → **Product → Archive**
3. Upload to App Store Connect → distribute via TestFlight

### Option C — App Store

Follow Apple's review guidelines for medical/health apps. This app is a personal health record tool, not a medical device.

## API keys

Keys are stored in the **iOS Keychain** on device only — never sent anywhere except Groq and Google APIs.

| Key | Purpose |
|-----|---------|
| Groq | Whisper transcription + Llama 3.3 summaries |
| Gemini | Optional — Gemini Flash summaries |

## Project structure

```
app/ios/
├── MedNote.xcodeproj/
└── MedNote/
    ├── MedNoteApp.swift
    ├── ContentView.swift
    ├── Models/Visit.swift
    ├── Services/
    │   ├── AIService.swift          # Groq Whisper + Gemini/Groq chat
    │   ├── AudioRecorderService.swift
    │   ├── SettingsStore.swift      # Keychain + UserDefaults
    │   ├── VisitStore.swift
    │   └── VisitPipeline.swift
    ├── Views/
    │   ├── CallVisitView.swift      # Phone call + import flow
    │   ├── RecordView.swift
    │   ├── VisitsListView.swift
    │   ├── VisitDetailView.swift
    │   ├── HomeView.swift
    │   └── SettingsView.swift
    ├── Assets.xcassets/
    └── Info.plist
```

## Bundle ID

Default: `com.mednote.ios` — change in Xcode if needed.

## Privacy

- No account required
- Visit data stored locally in app Documents
- API keys in Keychain
- Audio sent only to Groq for transcription when you process a visit

## Related

- Web app: [`RecallMD_v12.html`](../../RecallMD_v12.html)
- Backend sync (optional): [`backend/`](../../backend/)
