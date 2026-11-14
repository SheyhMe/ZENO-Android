# ZENO Integration Setup

This document lists the owner-controlled configuration needed before cloud features can be enabled. Never commit real credentials.

## Environments

Use separate development and production projects. Do not connect production user data to preview deployments.

## Backend environment variables

Configure these in the backend provider's encrypted environment-variable store:

- `OPENAI_API_KEY` — server-side OpenAI API access.
- `SUPABASE_URL` — project URL.
- `SUPABASE_ANON_KEY` — public client key; still enforce authentication and RLS.
- `SUPABASE_SERVICE_ROLE_KEY` — server only; never expose to Android or browser clients.
- `TOKEN_ENCRYPTION_KEY` — high-entropy key used only by the backend to encrypt provider refresh tokens.
- `APP_BASE_URL` — canonical app/backend origin.
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` — Google OAuth web application credentials.
- `NOTION_CLIENT_ID`, `NOTION_CLIENT_SECRET` — Notion OAuth credentials.
- `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` — GitHub OAuth app credentials.
- `ELEVENLABS_API_KEY` — only if the chosen ElevenLabs server-side API flow requires it.

Do not add secrets to Gradle files, Android resources, BuildConfig, APK assets, GitHub source files, issue comments, or logs.

## OAuth callback setup

Register exact HTTPS callback URLs from the deployed backend with each provider. Use Authorization Code + PKCE where supported. Validate state, nonce (for OIDC), issuer, audience, redirect URI and token response. Do not accept arbitrary redirect URLs.

Start with minimum scopes:
- Google: request only the Gmail or Calendar scopes required by a feature, not broad account access.
- Notion: use the workspace integration authorization flow and request only the selected workspace.
- GitHub: prefer a GitHub App with narrowly scoped installation permissions where feasible; otherwise use the minimum OAuth scopes.

The app must show the provider, requested access, and connected account before the user approves. Provide disconnect/revoke controls.

## Data and security requirements

- Enable Supabase Row Level Security on every user-owned table.
- Derive the user identity from a verified backend session; never trust a client-supplied user ID.
- Encrypt provider refresh tokens at rest and never return them to Android.
- Keep audit records for integration actions without logging tokens, message bodies, or other unnecessary personal data.
- Require an explicit in-app confirmation before sending email, creating or deleting calendar events, publishing code, or performing other external side effects.
- Rate-limit AI and integration endpoints and validate every tool argument server-side.

## Android configuration

Android receives only non-secret public configuration (for example, the Supabase project URL and anon key if direct Supabase client access is used). Prefer backend-mediated OAuth so provider secrets and refresh tokens remain server-side. Use Android App Links with verified HTTPS domains for OAuth return navigation; do not put client secrets in the app.

## Release gate

Do not call OAuth or AI integration complete until:
1. Login and callback validation pass on a clean install.
2. Token refresh, revocation, account switching and expired-session handling are tested.
3. RLS isolation is tested with two separate users.
4. Side-effect confirmation is tested, including cancellation.
5. Logs and APK contents are checked for credential leakage.

## Android voice client-tool contract

The Android voice service exposes these client tools to the ElevenLabs conversation session:

| Tool name | Parameters | Behavior |
| --- | --- | --- |
| `set_alarm` | `hour` (0–23), optional `minute` (0–59), optional `label` | Opens Android's alarm setup screen; the user confirms the alarm in the clock app |
| `set_timer` | `duration_seconds` (1–86400), or `minutes` | Opens Android's timer setup screen; the user confirms in the clock app |
| `search_web` | `query` | Opens a browser search |
| `open_maps_search` | `query` or `place` | Opens a map search |
| `open_app` | `app_name` or `name` | Launches an installed app |
| `open_wifi_settings` | none | Opens Wi-Fi settings |
| `open_bluetooth_settings` | none | Opens Bluetooth settings |

The ElevenLabs agent must have matching client-tool definitions (same names and parameter schemas) and must be instructed when to call each tool. Adding a tool to the Android client alone does not configure the hosted agent. Alarm and timer actions intentionally open the system confirmation UI rather than silently scheduling.

## Current Android client improvements

- Installed-app search filters launchable apps on-device; selecting a result opens that app.
- Chat transcript is retained locally between activity restarts (bounded to the latest 6,000 characters).
- Uzbek/English preference is retained locally. The current language toggle covers core navigation and input labels; full localization of every screen and agent speech remains a follow-up.
- The settings shortcut opens Android's app-permission page.
- Android 13+ notification permission is requested when voice mode is first enabled.

## Remaining backend provisioning

Supabase is provisioned and the Android client now supports email/password accounts, per-user cloud chat writes and preference sync. The production email flow still needs a custom SMTP provider. Vercel hosting, private OpenAI orchestration, server-side ElevenLabs credentials and Google/GitHub/Notion OAuth remain unconfigured. Do not place provider secrets in the Android app. Configure the server-side environment variables listed above before enabling those integrations.

## Supabase cloud foundation

The provisioned Supabase project is zeno-core in eu-central-1 (Frankfurt). The database migrations are tracked in supabase/migrations/.

Tables:
- profiles: account display name and language.
- conversations and messages: user-owned chat history.
- user_memories: user-owned memory entries.
- assistant_preferences: language, theme and voice preferences.

Every table has RLS enabled. Policies compare the row owner with auth.uid(). Only the authenticated role receives table privileges; anon has no table access. The Android client uses only the public publishable key. Never add a service-role or secret key to the APK.

Email/password sign-up uses Supabase Auth. Hosted projects require email confirmation by default. The built-in SMTP sender is test-only: it only sends to organization team addresses and is limited to 2 emails per hour. Before inviting other users, configure a custom SMTP provider under Authentication > Emails > SMTP Settings. Keep email confirmation enabled for production.
