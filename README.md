# SIGMA — Autonomous Android AI Voice Assistant

SIGMA is a production-grade, local-first Android Voice Assistant built using Kotlin and Jetpack Compose. Inspired by advanced holographic AI aesthetics, SIGMA combines Google Gemini AI intelligence with on-device Android Accessibility and MediaProjection automation.

---

## ⚡ Key Highlights & Capabilities

- **Cyberpunk Tactical HUD**: High-contrast OLED dark theme (`#080808`), glowing neon crimson accents (`#FF003C`), real-time audio reactive particle/waveform visualizer, and custom animated state orb.
- **Continuous Voice Interaction**:
  - Offline wake word processing for *"Hey Sigma"*, *"Sigma"*, and *"Ok Sigma"*.
  - Android native STT (`SpeechRecognizer`) with real-time audio RMS visualizer feedback.
  - Natural, low-latency male TTS speech synthesis.
- **The Beast Loop Automation Architecture**:
  - `OBSERVE` ➔ captures current screen hierarchy or pixels via Accessibility / MediaProjection.
  - `PLAN` ➔ parses complex instructions (English, Hindi, Hinglish) into actionable sequential steps.
  - `ACT` ➔ performs real taps, text typing, swipes, app launches, volume adjustments, and device lock.
  - `VERIFY` ➔ checks if the expected target UI element or app appeared.
  - `RECOVER` ➔ intelligently retries, falls back to alternative element selectors or search inputs.
- **Zero-Bypass Device Security**:
  - Real device locking via `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN` (API 28+).
  - Proper native Keyguard authentication prompt for unlocking (`KeyguardManager.requestDismissKeyguard`). Never simulates fake lock screens or bypasses biometric/PIN security.
- **Safety & Permissions**:
  - Emergency Kill Switch button accessible on all screens (`HALT`).
  - Mandatory confirmation modal for destructive/sensitive operations (placing phone calls, sending SMS).
  - Full audit logging into an encrypted local Room SQLite database (`CommandHistoryItem`, `ActionAuditLog`).
- **MediaProjection & Screen Vision**:
  - Pixel-perfect screen capture and multimodal AI visual explanation using Google Gemini.

---

## 📱 Architecture Overview

```
app/src/main/java/com/example/
├── ai/
│   ├── AiProvider.kt          # Gemini API provider with model fallback
│   ├── AiService.kt           # AI brain & chat handling
│   ├── ActionPlanner.kt       # Multi-step command planner (Hinglish/English)
│   └── VisionService.kt       # Screen capture analyzer + Gemini vision
├── automation/
│   ├── AutomationEngine.kt    # Beast Loop orchestrator (Observe-Plan-Act-Verify)
│   ├── ActionExecutor.kt      # Real accessibility actions (Tap, Type, Scroll, Lock)
│   ├── ActionVerifier.kt      # UI state verification
│   ├── RecoveryEngine.kt      # Retry and fallback heuristics
│   └── ScreenObserver.kt      # Hierarchy extraction & node searching
├── apps/
│   ├── AppResolver.kt         # Fuzzy & direct app name resolution
│   └── InstalledAppRepository.kt
├── data/
│   ├── ConversationDao.kt     # High-speed command history DAO
│   ├── SettingsRepository.kt  # User preferences & API key storage
│   └── db/                    # Room Database (Entities, DAO, Repositories)
├── device/
│   ├── DeviceController.kt    # Volume control, contacts search, intent launcher
│   └── LockController.kt      # Real lock & Keyguard unlock manager
├── router/
│   └── ActionRouter.kt        # Primary command dispatch & safety router
├── screen/
│   ├── MediaProjectionManager.kt
│   ├── ScreenCaptureManager.kt
│   └── ScreenAnalysisManager.kt
├── service/
│   ├── SigmaAccessibilityService.kt # Foreground Accessibility Service
│   ├── SigmaNotification.kt         # Persistent notification manager
│   └── SigmaVoiceService.kt         # Background hands-free wake word service
├── ui/
│   ├── components/            # SigmaOrb, SigmaGlassCard, SigmaButton, Waveform, etc.
│   ├── theme/                 # Cyberpunk Neon Red / Deep Black M3 Theme
│   ├── MainScreen.kt          # Active voice HUD with live orb visualizer
│   ├── AutomationScreen.kt    # Screen inspection and direct gestures
│   ├── HistoryScreen.kt       # Complete command audit log
│   ├── SettingsScreen.kt      # API key, speech rate, wake word, background service
│   └── PermissionScreen.kt    # Step-by-step permissions checklist
└── voice/
    ├── SpeechRecognitionManager.kt
    ├── TextToSpeechManager.kt
    ├── WakeWordManager.kt
    └── VoiceSessionManager.kt
```

---

## 🛠️ Required Android Permissions & Setup

1. **Microphone** (`RECORD_AUDIO`): For voice recognition.
2. **Accessibility Service**: Enable `SIGMA Accessibility Service` in Android Settings ➔ Accessibility ➔ Installed apps.
3. **Screen Capture** (`MediaProjection`): Triggered via Status/Permission tab for live visual inspection.
4. **Phone Calls & Contacts** (`CALL_PHONE`, `READ_CONTACTS`): Optional for initiating hands-free calls.
5. **Notifications** (`POST_NOTIFICATIONS`, Android 13+): For background persistent service.

---

## 🚀 Building the Project

Run with Gradle:
```bash
./gradlew assembleDebug
```
Or execute the automated build script:
```bash
bash build.sh
```
