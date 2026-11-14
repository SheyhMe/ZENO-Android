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
