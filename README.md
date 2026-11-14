# ZENO Android

Native Kotlin Android personal assistant with Uzbek/English AI chat, optional voice interaction, and local Android actions.

## Current features
- Text chat and voice conversations through the configured ElevenLabs ZENO agent.
- Uzbek/English language detection in the voice agent.
- Optional “Hey ZENO” foreground microphone mode (Android microphone permission and notification required).
- Open installed apps by visible app name; quick app launcher search.
- Google search opens Google results in Chrome only; it does not automatically open search results.
- Android system screens for alarms, timers, Wi-Fi, Bluetooth, Maps, dialer, SMS composer, and calendar.
- Local chat history saved on the device.
- Optional Supabase email account and per-user cloud chat history. Email confirmation may be required by project Auth settings.
- Light/dark appearance follows Android system theme.

## Privacy and safety
- The Android app contains only the Supabase **publishable** key. No service-role or secret key is embedded in the APK.
- Supabase tables use Row Level Security and user ownership policies.
- Calls and SMS are never sent automatically; Android opens the dialer or message composer for user confirmation.
- Alarm and timer actions open the Clock app for confirmation.
- Voice listening is opt-in and uses Android's foreground-service notification. Stop listening from the Voice page or notification.
- Local history stays on the device unless the user signs in and enables cloud sync.

## Build
GitHub Actions builds a debug APK for pushes to `main` and `zeno-v2`.
Open the repository Actions tab, select the latest successful **Build ZENO APK** run, then download the `ZENO-debug-apk` artifact.
