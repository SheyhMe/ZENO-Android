# ZENO Android

ZENO is a native Kotlin Android assistant prototype being upgraded toward a hybrid AI architecture: Android handles the interface and permission-gated device actions; a private backend will handle AI orchestration, OAuth integrations and durable user data.

## Current app capabilities

The current Android prototype includes:
- Home, chat, apps, voice and profile/settings pages.
- Text and voice conversation through the configured ElevenLabs agent.
- A foreground microphone service that attempts the “Hey ZENO” wake phrase using the device speech-recognition service.
- A limited set of Android app-launch and system-settings actions.

These capabilities depend on device OS behavior, installed apps, speech-recognition availability and the configured voice agent. Wake-phrase detection is not guaranteed to work continuously on every device. The current chat is not yet backed by the planned private OpenAI orchestration backend.

## Build an APK

1. Open the repository's **Actions** tab.
2. Select **Build ZENO APK** and run it with **Run workflow**, or push a commit to a configured branch.
3. Wait for the **Build debug APK** and artifact-upload steps to succeed.
4. Download the `ZENO-debug-apk` artifact and extract `app-debug.apk`.
5. Install the APK on an Android device. Debug builds are for testing, not public release.

## Upgrade architecture

See:
- [Full upgrade architecture](docs/ARCHITECTURE.md)
- [OAuth and backend integration setup](docs/INTEGRATION_SETUP.md)

The target design uses OAuth 2.0/OIDC for optional account connections, OpenAI for reasoning/tool selection, ElevenLabs for speech, and Supabase for identity and user data. Vercel is the planned backend hosting option.

## Security

- Never commit API keys, OAuth client secrets, service-role keys or refresh tokens.
- Provider refresh tokens must remain encrypted on the backend.
- Apply Supabase Row Level Security to all user-owned data.
- Ask for in-app confirmation before external side effects such as sending email or changing calendar events.
- Android permissions and protected system settings must remain under the user's control.

## Current prerequisites

The connected accounts currently expose no Supabase projects or Vercel teams. Backend deployment and live OAuth integrations therefore remain unconfigured. The owner must create/select those resources and register provider OAuth applications before those features can be enabled. See the integration setup guide for the exact configuration checklist.
