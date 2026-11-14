# ZENO — Full Upgrade Architecture

## Product decision
- Account linking: OAuth 2.0 / OpenID Connect; never collect provider passwords.
- Assistant architecture: hybrid. Android owns device actions and UX; a private backend owns AI orchestration, OAuth token exchange, tool authorization, and durable memory.
- Scope: full product, delivered in verifiable phases.

## Target architecture
1. Android app (Kotlin): chat/voice UI, Android intents, permission-gated device actions, local encrypted preferences, offline status and diagnostics.
2. Backend API (Vercel): authenticated chat endpoint, tool router, provider adapters, rate limits, audit events, and secret storage in server environment variables.
3. AI orchestration: OpenAI for reasoning and tool selection; ElevenLabs for real-time speech/voice. The app must not contain provider secret keys.
4. Identity and data: OAuth/OIDC with PKCE for supported providers; Supabase Auth/Postgres for user identity, settings, consent, memory and task metadata. RLS must isolate every user's records.
5. Integrations: Gmail, Google Calendar, Notion and GitHub via least-privilege OAuth scopes. Each integration is optional and revocable.
6. Operations: GitHub Actions builds, artifact verification, crash reporting/analytics, server logs and health checks.

## Safety and permission model
- Read actions may run after a valid connection and explicit scope grant.
- External side effects (send email, create/delete calendar events, publish code, delete data, payments) require an in-app confirmation showing the exact action and target.
- Android protected settings are opened for the user; the app does not bypass OS permission controls.
- No always-on microphone promise until device/OS restrictions and foreground-service behavior are tested on supported devices.
- Store refresh tokens only server-side encrypted; never in APK, source control, analytics, or logs.
- Provide disconnect, revoke, export and delete controls.

## Delivery phases
- Phase 0: baseline audit, isolated upgrade branch, CI build trigger, day/night resource foundation.
- Phase 1: stabilize Android architecture, localization, chat transcript model, permissions, and automated tests.
- Phase 2: backend deployment, authentication, OpenAI orchestration and ElevenLabs voice adapter.
- Phase 3: OAuth connections for Google, Notion and GitHub; scoped integration tools and confirmations.
- Phase 4: persistent memory, reminders, automation scheduler, notifications and observability.
- Phase 5: accessibility, security review, device matrix, release signing and APK/AAB release.

## Current blockers / prerequisites
- Firebase integration has been suggested but is not yet confirmed connected. It is optional; crash reporting and push can be delivered through Firebase once connected.
- Production OAuth requires provider client IDs, redirect URIs and verified callback domains configured by the account owner.
- Vercel/Supabase projects and deployment secrets must be selected/configured before backend features can be live.
- Gmail, Calendar, Notion and GitHub ChatGPT connectors are not automatically reusable as Android OAuth credentials. ZENO needs its own OAuth app registrations and consent screens.
