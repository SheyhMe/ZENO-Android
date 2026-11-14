# ZENO private backend

This Vercel serverless API provides authenticated ZENO AI chat, user-approved memory context, and Android device-action requests through Vercel AI Gateway.

## Runtime configuration

Set these Vercel Production and Preview environment variables:

- `SUPABASE_URL`: ZENO Supabase project URL.
- `SUPABASE_PUBLISHABLE_KEY`: the public Supabase publishable key.
- `VERCEL_OIDC_TOKEN`: supplied by Vercel deployments for AI Gateway authentication; do not create or commit a static provider key for this route.

The route verifies every Supabase access token against Supabase Auth before calling AI Gateway. The Supabase service-role key is not required or used.

## Endpoint

`POST /api/chat`

Headers:
- `Authorization: Bearer <Supabase access token>`
- `Content-Type: application/json`

Body:
```json
{
  "messages": [
    { "role": "user", "content": "Salom, ZENO" }
  ]
}
```

The endpoint accepts the latest 20 user/assistant messages, enforces per-message size limits, loads only the signed-in user's active memories through Supabase RLS, and returns either a text reply or a list of device actions for the Android app to execute. The Android app sends the action results back so the model can confirm what actually happened. The endpoint also exposes a public GET health check.

## Deployment

Create a Vercel project linked to `SheyhMe/ZENO-Android`, set its Root Directory to `backend`, and set its Production Branch to `zeno-full-upgrade`. Add the two Supabase environment variables above. Vercel OIDC supplies the Gateway request token at runtime.
