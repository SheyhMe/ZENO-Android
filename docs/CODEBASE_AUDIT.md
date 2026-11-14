# ZENO Codebase Audit

Audit target: `zeno-full-upgrade`.

## Current repository shape

- `app/src/main/java/com/zeno/assistant/MainActivity.kt`: single Activity currently owns navigation, view wiring, chat transcript rendering, text-session setup and permission handling.
- `app/src/main/java/com/zeno/assistant/ZenoVoiceService.kt`: foreground microphone service, wake-phrase recognition, ElevenLabs conversation lifecycle and Android app/system intents.
- `app/src/main/res/layout/activity_main.xml`: all five pages (Home, Chat, Apps, Voice, Settings) are declared in one large XML layout.
- `app/src/main/res/values` and `values-night`: light/dark color and theme resources exist, but many labels are still hardcoded in the layout and Kotlin.
- `app/build.gradle`: minimal Android/Kotlin dependencies; JUnit support has now been added for core model tests.
- No existing `app/src/test` suite was present at audit time.

## Concrete findings

### Architecture
1. UI, conversation SDK calls, navigation and permission flow are coupled in one Activity.
2. Voice recognition, provider conversation and Android device actions are coupled in one Service.
3. The app talks directly to a configured ElevenLabs agent. The planned authenticated OpenAI/backend hybrid path is not implemented yet.
4. There is no repository boundary or typed conversation state in the original UI flow. Initial contracts have now been added under `core/`.

### UX and functionality
1. The Apps search field is visual only; it is not wired to filter installed apps.
2. The gear icon in the top bar is a non-interactive TextView.
3. Settings/profile content is static text, not persisted preferences.
4. Several screen labels and the greeting are hardcoded, so the Uzbek/English language setting is not functional yet.
5. Chat messages are appended to one TextView rather than rendered as typed message items; history is not persisted.
6. The home status label initially says ONLINE before a live connection is confirmed.

### Android platform and reliability
1. Wake-phrase recognition depends on the device speech-recognition service and foreground microphone restrictions; continuous detection cannot be guaranteed across devices.
2. Notification permission is declared but the app does not yet request it at runtime on Android 13+.
3. There is no explicit UI state for denied microphone permission, unavailable recognition service, or interrupted network beyond status text.
4. There is no automated device/instrumentation test coverage yet.

### Security and integrations
1. The current client-side voice agent identifier is configuration, not an API secret, but production provider credentials must stay server-side.
2. Gmail, Calendar, Notion and GitHub OAuth flows are not implemented.
3. External side-effect confirmation and account-disconnect/revoke UI are not yet wired into the app.

## New foundations added in this branch

- `core/model/ChatMessage.kt`: typed conversation message and delivery state.
- `core/model/AssistantAction.kt`: typed action proposals with explicit confirmation classification.
- `core/data/ChatRepository.kt`: provider-independent conversation boundary using Flow.
- `core/settings/AssistantSettings.kt`: typed language, theme and voice preferences with a wake-phrase invariant.
- `app/src/test/.../CoreModelTest.kt`: initial unit tests for confirmation policy and settings invariants.
- JUnit 4 test dependency.

These contracts are foundations only. The existing screens and voice service have not yet been migrated to them.

## Recommended module/package structure

Keep the first migration inside the existing Android app module to avoid a risky early multi-module Gradle conversion:

```text
com.zeno.assistant
  app/                  Activity, navigation and application lifecycle
  core/
    model/              ChatMessage, AssistantAction, UserProfile
    data/               Repository interfaces
    settings/           User preferences and theme/language state
    security/           Session and secure local configuration boundaries
  feature/
    home/               Home dashboard
    chat/               Conversation UI and message list
    apps/               Installed-app search and launcher
    voice/              Voice screen and voice state
    settings/           Account, language, theme, permissions
    integrations/       OAuth connection management UI
  platform/
    android/            Intents, alarms, timers, package discovery
    audio/              Speech recognition and foreground service
  data/
    local/              Encrypted preferences and local cache
    remote/             Authenticated backend client
```

## Next implementation order

1. Extract chat state and provider calls from MainActivity behind ChatRepository.
2. Extract speech recognition lifecycle from ZenoVoiceService into a testable controller.
3. Add runtime notification permission and clear permission/error states.
4. Wire Apps search to PackageManager and make Settings controls persistent.
5. Move visible strings into resources and add English translations.
6. Add authenticated backend client; keep OpenAI and provider secrets server-side.
7. Add OAuth providers with PKCE, least-privilege scopes and revocation.
8. Add UI/instrumentation tests and run the full GitHub Actions build before distributing an APK.
