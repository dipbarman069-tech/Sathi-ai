# Saathi — Offline-First Private Android Voice Assistant

## Architecture & Project Structure
- `app/src/main/java/com/example/MainActivity.kt` — Single-activity (`FragmentActivity`) Compose host with edge-to-edge UI, adaptive `NavigationBar`/`NavigationRail`, individual permission explanation flow, and Biometric/PIN dialogs.
- `app/src/main/java/com/example/ui/SaathiViewModel.kt` — MVVM coordinator linking encrypted Room repository, DataStore preferences, NLU intent parser, modular skills, speech manager, and the "Stop Everything" kill switch.
- `app/src/main/java/com/example/ui/screens/` — `ChatScreen.kt`, `LiveVoiceScreen.kt`, `ActionLogScreen.kt`, `SafetySettingsScreen.kt`.
- `app/src/main/java/com/example/ui/components/` — `VoiceOrbCanvas.kt` (custom RMS-reactive sonic orb), `SecurityDialogs.kt` (Confirmation card, PIN dialogs, Permission explainer, Model guide).
- `app/src/main/java/com/example/domain/nlu/` — `IntentParser.kt` (Hindi/Hinglish/English -> whitelisted JSON intent), `OnDeviceModelManager.kt`.
- `app/src/main/java/com/example/domain/skills/` — `AssistantSkill.kt`, `SkillRegistry.kt` (whitelist + blocklist + confirmation gate), `CorePhoneSkills.kt`.
- `app/src/main/java/com/example/services/` — `SaathiSpeechManager.kt`, `SaathiWakeWordService.kt`, `SaathiAccessibilityService.kt`, `SaathiNotificationListenerService.kt`.
- `app/src/main/java/com/example/data/` — `LocalCryptoManager.kt` (Android Keystore AES-256-GCM), `PinAndBiometricManager.kt`, `VoiceProfileManager.kt`, Room DB (`SaathiDatabase.kt`, `SaathiDao.kt`, `SaathiEntities.kt`, `SaathiRepository.kt`), and `SaathiPreferences.kt`.

## Phased Feature Coverage
1. **Phase 1 (MVP)**: ChatGPT-style dark UI, big mic button, Live Voice Mode sonic orb, female Hindi/Hinglish/English TTS, open app, phone call, and alarm/timer intents.
2. **Phase 2 (Device & Accessibility Control)**: Torch toggle, media volume, screen brightness, Wi-Fi/Bluetooth settings panels, Accessibility screen scroll/tap/type/screenshot, and transient Notification Reader.
3. **Phase 3 (Safety & Privacy)**: Android Keystore AES-256-GCM local encryption, strict JSON action whitelist, Voice + On-Screen confirmation gate, Biometric/PIN + optional 40-band speaker voiceprint lock, pre-seeded Banking/UPI/Password app blocklist, encrypted Action Log, and one-tap **Stop Everything** kill switch.

## Offline Model Download & Setup Instructions
Saathi works out-of-the-box offline with its built-in multilingual Hinglish/Hindi/English parser and system offline speech/TTS. To pair external local models, place them inside app-private storage (`filesDir/saathi_models/`):
1. **On-Device LLM**: Download a quantized Gemma 2B IT INT4 (`.task` for MediaPipe LLM Inference) or Qwen2.5-1.5B-Instruct (`.gguf` for llama.cpp) bundle and copy it into `saathi_models/`.
2. **Offline Speech Recognition (STT)**: Download `vosk-model-small-hi-0.22` (Hindi) and `vosk-model-small-en-in-0.4` (Indian English) or Whisper `tiny`/`base` weights into `saathi_models/`.
3. **Female Neural TTS**: Install Android's offline Indian Hindi/English high-quality voice data in Android Settings -> Text-to-speech output, or bundle Piper ONNX `hi_IN` / `en_US` female voices.

## Build Steps
1. Open the project in Android Studio or run `gradle assembleDebug` (JDK 17+, Android SDK 36).
2. Run unit & Robolectric tests with `gradle :app:testDebugUnitTest`.
3. Install on a physical Android 8.0+ (API 26+) device and grant individual permissions from **Safety -> Permissions** as needed.
