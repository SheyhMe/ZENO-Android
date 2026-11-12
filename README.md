# ZENO Android — first build

A native Kotlin Android starter for Uzbek voice commands. It currently supports button-activated speech recognition, spoken replies (if the phone has a compatible Uzbek TTS voice), launching installed apps by name, opening alarm/timer UI, opening the dialer, opening the SMS composer, and web search.

## Build on GitHub (phone only)
1. Create or open `https://github.com/SheyhMe/ZENO-Android`.
2. Upload the contents of this ZIP to the repository root (not the ZIP file itself). Keep the `.github/workflows/android.yml` path.
3. Commit to `main`.
4. Open the repository's **Actions** tab and wait for **Build ZENO APK** to finish.
5. Open the successful run, download the `ZENO-debug-apk` artifact, extract it, and install `app-debug.apk`.

## Current limits
- No always-listening “Hey ZENO” wake phrase yet; listening starts when you tap the button.
- Android speech recognition availability and Uzbek recognition depend on the installed speech service and network.
- The app opens the dialer and SMS composer; it does not place calls or send messages automatically.
- Alarm and timer actions open the phone's clock UI for confirmation.
- Accessibility control, system-setting toggles, persistent reminders, cloud AI chat, and ElevenLabs voice are not implemented yet.
- Android restricts background microphone use and protected settings. Do not grant Accessibility permissions to untrusted apps.
