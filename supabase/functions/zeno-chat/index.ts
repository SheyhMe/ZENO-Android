import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const OPENAI_URL = "https://api.openai.com/v1/responses";
const MODEL = "gpt-6.1-terra";
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};
const tools = [
  { type: "function", name: "open_app", description: "Open an installed Android app by its visible name. Use only when the user asks to open an app.", parameters: { type: "object", properties: { app_name: { type: "string" } }, required: ["app_name"], additionalProperties: false } },
  { type: "function", name: "go_home", description: "Go to the Android home screen when the user asks.", parameters: { type: "object", properties: {}, additionalProperties: false } },
  { type: "function", name: "go_back", description: "Press Android Back when the user asks.", parameters: { type: "object", properties: {}, additionalProperties: false } },
  { type: "function", name: "show_notifications", description: "Open the notification shade when the user asks.", parameters: { type: "object", properties: {}, additionalProperties: false } },
  { type: "function", name: "show_quick_settings", description: "Open Android quick settings when the user asks.", parameters: { type: "object", properties: {}, additionalProperties: false } },
  { type: "function", name: "scroll_screen", description: "Scroll the currently visible app screen.", parameters: { type: "object", properties: { direction: { type: "string", enum: ["up", "down"] } }, required: ["direction"], additionalProperties: false } },
  { type: "function", name: "tap_visible_text", description: "Tap a visible UI element by its exact or distinctive visible text. Only do this when the user explicitly asks to tap/select it.", parameters: { type: "object", properties: { text: { type: "string" } }, required: ["text"], additionalProperties: false } },
  { type: "function", name: "type_text", description: "Type text into the currently focused input field. Only do this when the user explicitly asks to enter text.", parameters: { type: "object", properties: { text: { type: "string" } }, required: ["text"], additionalProperties: false } },
  { type: "function", name: "search_google", description: "Search Google in Chrome for the user's query.", parameters: { type: "object", properties: { query: { type: "string" } }, required: ["query"], additionalProperties: false } },
  { type: "function", name: "set_alarm", description: "Open the Clock app to create an alarm. User must confirm/save it in Clock.", parameters: { type: "object", properties: { hour: { type: "integer", minimum: 0, maximum: 23 }, minute: { type: "integer", minimum: 0, maximum: 59 } }, required: ["hour", "minute"], additionalProperties: false } },
  { type: "function", name: "set_timer", description: "Open the Clock app to set a timer. User must confirm it in Clock.", parameters: { type: "object", properties: { duration_seconds: { type: "integer", minimum: 1, maximum: 86400 } }, required: ["duration_seconds"], additionalProperties: false } },
  { type: "function", name: "open_wifi_settings", description: "Open Android Wi-Fi settings.", parameters: { type: "object", properties: {}, additionalProperties: false } },
  { type: "function", name: "open_bluetooth_settings", description: "Open Android Bluetooth settings.", parameters: { type: "object", properties: {}, additionalProperties: false } },
  { type: "function", name: "open_maps", description: "Open Google Maps with a destination search.", parameters: { type: "object", properties: { destination: { type: "string" } }, required: ["destination"], additionalProperties: false } },
  { type: "function", name: "open_dialer", description: "Open the phone dialer with a number filled in. Never place a call automatically.", parameters: { type: "object", properties: { phone_number: { type: "string" } }, required: ["phone_number"], additionalProperties: false } },
  { type: "function", name: "compose_sms", description: "Open an SMS draft with recipient and message. Never send automatically; the user must review and tap Send.", parameters: { type: "object", properties: { phone_number: { type: "string" }, message: { type: "string" } }, required: ["phone_number", "message"], additionalProperties: false } },
  { type: "function", name: "open_calendar", description: "Open Android Calendar to create an event. User must review and save it.", parameters: { type: "object", properties: {}, additionalProperties: false } },
];
const instructions = `You are ZENO, a capable Jarvis-like personal assistant inside an Android phone. Speak naturally and clearly in the user's language; use Uzbek when the user writes Uzbek. Be concise but thoughtful. Understand context and multi-step requests, ask one focused question when needed, and never claim an action succeeded unless the phone reports success. Use Android tools only when the user clearly requests an action. Never tap or type based on guesses. Never bypass Android security, permissions, lock screens, banking protections, or app safeguards. Never make purchases, transfer money, send messages, or place calls without the user's explicit final confirmation in the relevant Android app. For SMS and calls, only open the draft/dialer. For alarms and timers, open Clock and tell the user to confirm. If a requested action is unavailable, explain the permission or limitation. Do not invent personal memory. You can use the recent conversation included in input as short-term context.`;
Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response(JSON.stringify({ error: "POST required" }), { status: 405, headers: corsHeaders });
  const apiKey = Deno.env.get("OPENAI_API_KEY");
  if (!apiKey) return new Response(JSON.stringify({ error: "AI server key is not configured. Add OPENAI_API_KEY in Supabase Edge Function Secrets." }), { status: 503, headers: corsHeaders });
  try {
    const body = await req.json();
    const input = body?.input;
    if (!Array.isArray(input) || input.length < 1 || input.length > 32) return new Response(JSON.stringify({ error: "input must contain 1 to 32 conversation items" }), { status: 400, headers: corsHeaders });
    if (JSON.stringify(input).length > 24000) return new Response(JSON.stringify({ error: "Conversation is too large. Start a new chat." }), { status: 413, headers: corsHeaders });
    const upstream = await fetch(OPENAI_URL, {
      method: "POST",
      headers: { "Authorization": `Bearer ${apiKey}`, "Content-Type": "application/json" },
      body: JSON.stringify({ model: MODEL, instructions, input, tools, tool_choice: "auto", parallel_tool_calls: false, max_output_tokens: 1200, reasoning: { effort: "low" }, store: false }),
      signal: AbortSignal.timeout(45000),
    });
    const data = await upstream.json();
    if (!upstream.ok) return new Response(JSON.stringify({ error: data?.error?.message || "OpenAI API request failed" }), { status: upstream.status === 429 ? 429 : 502, headers: corsHeaders });
    return new Response(JSON.stringify(data), { status: 200, headers: corsHeaders });
  } catch (error) {
    return new Response(JSON.stringify({ error: error instanceof Error ? error.message : "Unexpected server error" }), { status: 500, headers: corsHeaders });
  }
});
