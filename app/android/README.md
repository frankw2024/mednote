# MedNote for Android

Native Kotlin + Jetpack Compose app — same phone-call workflow as the iOS and web apps.

## Features

| Tab | What it does |
|-----|----------------|
| **Home** | Quick actions + recent visits |
| **Call** | Record → open dialer → speakerphone → End & Transcribe |
| **Record** | In-person clinic visit |
| **Visits** | History with transcript, summary, extracted items |
| **Settings** | Groq/Gemini keys (encrypted), language, default phone |

## Install from Windows (USB)

Unlike iOS, you **can build and install from Windows**.

### Prerequisites

1. [Android Studio](https://developer.android.com/studio) (Ladybug or newer)
2. Android phone with **Developer options → USB debugging** enabled
3. USB cable

### Steps

1. Open Android Studio → **Open** → select `app/android/`
2. Wait for Gradle sync to finish
3. Connect your Android phone via USB → allow debugging on the phone
4. Select your phone in the device dropdown (top toolbar)
5. Click **Run** ▶ (or **Build → Build Bundle(s) / APK(s) → Build APK(s)**)

First launch: grant **Microphone** permission when prompted.

### Install APK without USB (share to phone)

1. In Android Studio: **Build → Build APK(s)**
2. APK path: `app/android/app/build/outputs/apk/debug/app-debug.apk`
3. Copy to phone (email, Drive, etc.)
4. Open on phone → allow **Install unknown apps** for your file manager → install

## API keys

Add in **Settings** tab:

| Key | Get it at |
|-----|-----------|
| Groq (required for transcription) | https://console.groq.com |
| Gemini (optional) | https://aistudio.google.com |

Keys are stored with **EncryptedSharedPreferences** on device only.

## Phone call workflow

1. Enter doctor's number
2. **Call & Record** — starts mic, opens dialer
3. Turn on **speakerphone**
4. After the call, return to MedNote → **End Call & Transcribe**
5. Or **Import audio** if the mic was muted during the call

> Android may still pause mic access during some cellular calls. Importing a recorder app file is the fallback (same as iPhone Voice Memos).

## Build from command line (Windows)

```powershell
cd app\android
.\gradlew.bat assembleDebug
```

If `gradlew.bat` is missing, open the project once in Android Studio — it generates the wrapper — or run **Gradle → wrapper** from the IDE.

Output: `app\build\outputs\apk\debug\app-debug.apk`

## Project structure

```
app/android/
├── app/src/main/kotlin/com/mednote/
│   ├── MainActivity.kt
│   ├── MedNoteApp.kt
│   ├── models/Visit.kt
│   ├── data/SettingsStore.kt, VisitStore.kt
│   ├── service/AIService.kt, AudioRecorderService.kt, VisitPipeline.kt
│   └── ui/screens/…
├── build.gradle.kts
└── settings.gradle.kts
```

## Requirements

- **minSdk 26** (Android 8.0+)
- **targetSdk 34**
- Internet for Groq/Gemini APIs

## Bundle ID

`com.mednote.android`

## Related

- iOS app: [`../ios/`](../ios/)
- Web app: [`../../RecallMD_v12.html`](../../RecallMD_v12.html)
