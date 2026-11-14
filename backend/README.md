# ZENO private backend

This Vercel serverless API provides authenticated text chat through Vercel AI Gateway.

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

The endpoint accepts the latest 20 user/assistant messages, enforces per-message size limits, does not persist request content in backend logs, and returns `{ "reply": "..." }`.

## Deployment

Create a Vercel project linked to `SheyhMe/ZENO-Android`, set its Root Directory to `backend`, and set its Production Branch to `zeno-full-upgrade`. Add the two Supabase environment variables above. Vercel OIDC supplies the Gateway request token at runtime.
