# ZENO + OpenJarvis integration

## Goal

Use OpenJarvis as the reasoning, agent-orchestration, and persistent-memory layer for ZENO while keeping ZENO a native Android assistant.

OpenJarvis is a Python framework, not an Android SDK. It must run as a separate backend service; the Android app must not try to package the Python runtime or model weights into the APK.

## Current ZENO components to preserve

- Native Kotlin Android app and existing UI.
- `ZenoVoiceService` and the current wake-word / speech interaction flow.
- `LocalCommandRouter` and `ZenoAccessibilityService` for on-device actions.
- Existing Supabase Edge Function and Gemini path as a temporary fallback during migration.
- ElevenLabs voice integration until a tested replacement is ready.

## Target architecture

```text
ZENO Android (Kotlin)
  |-- voice input / text chat
  |-- local action executor (Android permissions remain on device)
  |-- conversation/session identifier
  |
  v
ZENO API gateway (authenticated HTTPS)
  |
  v
OpenJarvis service (Python)
  |-- OrchestratorAgent for multi-step reasoning
  |-- SimpleAgent for low-latency ordinary chat
  |-- SQLite-backed per-user memory
  |-- model engine adapter (cloud or Ollama on a separately hosted machine)
  |-- policy gate for proposed device actions
  |
  v
Structured response: assistant text + optional proposed action
  |
  v
ZENO Android confirms / executes permitted local action
```

## API contract (initial)

### Request

```json
{
  "conversation_id": "client-generated-session-id",
  "message": "Open Chrome and search for Android security",
  "history": [
    {"role": "user", "content": "Earlier message"},
    {"role": "assistant", "content": "Earlier reply"}
  ],
  "locale": "uz-UZ"
}
```

The API must authenticate the caller. A client-provided user ID is not an authentication mechanism; derive the user identity from a verified token. Limit request size and history length.

### Response

```json
{
  "reply": "Chrome uchun qidiruv tayyorlayman.",
  "actions": [
    {
      "name": "search_google",
      "arguments": {"query": "Android security"},
      "requires_confirmation": true
    }
  ],
  "conversation_id": "client-generated-session-id"
}
```

The Android client must validate every action against its local allowlist. The backend can suggest an action but cannot directly control the phone. Calls, SMS, purchases, payments, and destructive actions must never execute without explicit user confirmation.

## OpenJarvis mapping

- Agent orchestration: `OrchestratorAgent` for tool-enabled multi-step tasks; `SimpleAgent` for ordinary chat.
- Model engine: start with a configurable OpenJarvis engine adapter. Do not assume Ollama is reachable from Android; it must run on a separate host or the service must use a supported cloud engine.
- Memory: begin with OpenJarvis SQLite/FTS5 memory. Partition records by authenticated user and conversation; never use one shared unfiltered memory store.
- Learning: collect opt-in, redacted traces first. Do not enable automatic prompt/model self-modification in production.

## Migration stages

1. **Foundation (this branch):** document the boundary, API contract, privacy rules, and migration plan without changing working Android behavior.
2. **Backend prototype:** implement a small authenticated Python API using OpenJarvis primitives; add health checks and tests.
3. **Android adapter:** add a configurable backend client and typed response parsing. Keep the Gemini client as a fallback until parity tests pass.
4. **Tool bridge:** translate OpenJarvis tool proposals into a strict Android action allowlist; require confirmation for sensitive actions.
5. **Memory:** add per-user persistent memory with deletion/export controls and tests for cross-user isolation.
6. **Voice integration:** connect voice turns to the same conversation/session API only after text chat is stable.
7. **Release gate:** run unit tests, Android Gradle build, backend tests, permission review, and manual device tests before publishing an APK.

## Important deployment constraint

OpenJarvis is Python-based and includes optional inference and server dependencies. A free serverless Android/Vercel deployment is not automatically a suitable place to run its full runtime or local model. Choose the backend host and model engine only after checking available memory, CPU/GPU, persistent disk, cold-start limits, and cost. Keep secrets in server-side environment variables; never put model-provider keys in the APK.

## Acceptance criteria

- Existing ZENO chat and voice flows continue to work while the new backend is unavailable.
- Uzbek and English conversation both work.
- A multi-turn conversation retains only its own history.
- Memory is isolated per authenticated user and can be cleared.
- A model-generated action is never treated as proof that the Android action succeeded.
- The app reports permission errors and backend errors clearly.
- No private key, service-role key, or model-provider key is committed to Git.
