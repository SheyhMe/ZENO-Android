import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const MODEL = "gemini-3.1-flash-lite";
const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`;
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

const declarations = [
  { name: "open_app", description: "Open an installed Android app by its visible name. Use only when the user asks to open an app.", parameters: { type: "OBJECT", properties: { app_name: { type: "STRING" } }, required: ["app_name"] } },
  { name: "go_home", description: "Go to the Android home screen when the user asks.", parameters: { type: "OBJECT", properties: {} } },
  { name: "go_back", description: "Press Android Back when the user asks.", parameters: { type: "OBJECT", properties: {} } },
  { name: "show_notifications", description: "Open the notification shade when the user asks.", parameters: { type: "OBJECT", properties: {} } },
  { name: "show_quick_settings", description: "Open Android quick settings when the user asks.", parameters: { type: "OBJECT", properties: {} } },
  { name: "scroll_screen", description: "Scroll the currently visible app screen.", parameters: { type: "OBJECT", properties: { direction: { type: "STRING", enum: ["up", "down"] } }, required: ["direction"] } },
  { name: "tap_visible_text", description: "Tap a visible UI element by its exact or distinctive visible text. Only when explicitly requested.", parameters: { type: "OBJECT", properties: { text: { type: "STRING" } }, required: ["text"] } },
  { name: "type_text", description: "Type text into the currently focused input field. Only when explicitly requested.", parameters: { type: "OBJECT", properties: { text: { type: "STRING" } }, required: ["text"] } },
  { name: "search_google", description: "Search Google in Chrome for the user's query.", parameters: { type: "OBJECT", properties: { query: { type: "STRING" } }, required: ["query"] } },
  { name: "set_alarm", description: "Open Clock to create an alarm. User must confirm/save it.", parameters: { type: "OBJECT", properties: { hour: { type: "INTEGER" }, minute: { type: "INTEGER" } }, required: ["hour", "minute"] } },
  { name: "set_timer", description: "Open Clock to set a timer. User must confirm it.", parameters: { type: "OBJECT", properties: { duration_seconds: { type: "INTEGER" } }, required: ["duration_seconds"] } },
  { name: "open_wifi_settings", description: "Open Android Wi-Fi settings.", parameters: { type: "OBJECT", properties: {} } },
  { name: "open_bluetooth_settings", description: "Open Android Bluetooth settings.", parameters: { type: "OBJECT", properties: {} } },
  { name: "open_maps", description: "Open Google Maps with a destination search.", parameters: { type: "OBJECT", properties: { destination: { type: "STRING" } }, required: ["destination"] } },
  { name: "open_dialer", description: "Open the phone dialer with a number filled in. Never place a call automatically.", parameters: { type: "OBJECT", properties: { phone_number: { type: "STRING" } }, required: ["phone_number"] } },
  { name: "compose_sms", description: "Open an SMS draft with recipient and message. Never send automatically.", parameters: { type: "OBJECT", properties: { phone_number: { type: "STRING" }, message: { type: "STRING" } }, required: ["phone_number", "message"] } },
  { name: "open_calendar", description: "Open Android Calendar to create an event. User must review and save it.", parameters: { type: "OBJECT", properties: {} } },
];

const instructions = `You are ZENO, a capable Jarvis-like personal assistant inside an Android phone. Speak naturally and clearly in the user's language; use Uzbek when the user writes Uzbek. Be concise but thoughtful. Understand context and multi-step requests, ask one focused question when needed, and never claim an action succeeded unless the phone reports success. Use Android tools only when the user clearly requests an action. Never tap or type based on guesses. Never bypass Android security, permissions, lock screens, banking protections, or app safeguards. Never make purchases, transfer money, send messages, or place calls without the user's explicit final confirmation in the relevant Android app. For SMS and calls, only open the draft/dialer. For alarms and timers, open Clock and tell the user to confirm. If a requested action is unavailable, explain the permission or limitation. Do not invent personal memory. Recent conversation is included as short-term context.`;

function toGeminiContents(input: any[]) {
  const contents: any[] = [];
  const callNames = new Map<string, string>();
  for (const item of input) {
    if (!item || typeof item !== "object") continue;
    if (item.type === "function_call") {
      const id = String(item.call_id || item.id || crypto.randomUUID());
      callNames.set(id, String(item.name));
      contents.push({ role: "model", parts: [{ functionCall: { name: String(item.name), args: (() => { try { return JSON.parse(item.arguments || "{}"); } catch { return {}; } })() } }] });
      continue;
    }
    if (item.type === "function_call_output") {
      const id = String(item.call_id || "");
      const name = callNames.get(id) || "unknown_tool";
      contents.push({ role: "user", parts: [{ functionResponse: { name, response: { result: String(item.output ?? "") } } }] });
      continue;
    }
    const role = item.role === "assistant" || item.role === "model" ? "model" : "user";
    let text = "";
    if (typeof item.content === "string") text = item.content;
    else if (Array.isArray(item.content)) text = item.content.map((p: any) => p?.text || "").join("\n");
    else if (item.type === "message" && Array.isArray(item.content)) text = item.content.map((p: any) => p?.text || "").join("\n");
    if (text.trim()) contents.push({ role, parts: [{ text }] });
  }
  return contents;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return new Response(JSON.stringify({ error: "POST required" }), { status: 405, headers: corsHeaders });
  const apiKey = Deno.env.get("GEMINI_API_KEY");
  if (!apiKey) return new Response(JSON.stringify({ error: "Gemini API kaliti sozlanmagan. Supabase Secrets bo‘limiga GEMINI_API_KEY qo‘shing." }), { status: 503, headers: corsHeaders });
  try {
    const body = await req.json();
    const input = body?.input;
    if (!Array.isArray(input) || input.length < 1 || input.length > 32) return new Response(JSON.stringify({ error: "input must contain 1 to 32 conversation items" }), { status: 400, headers: corsHeaders });
    if (JSON.stringify(input).length > 24000) return new Response(JSON.stringify({ error: "Conversation is too large. Start a new chat." }), { status: 413, headers: corsHeaders });

    const upstream = await fetch(`${GEMINI_URL}?key=${encodeURIComponent(apiKey)}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: instructions }] },
        contents: toGeminiContents(input),
        tools: [{ functionDeclarations: declarations }],
        toolConfig: { functionCallingConfig: { mode: "AUTO" } },
        generationConfig: { temperature: 0.7, maxOutputTokens: 1200 },
      }),
      signal: AbortSignal.timeout(45000),
    });
    const data = await upstream.json();
    if (!upstream.ok) {
      const detail = data?.error?.message || "Gemini API request failed";
      return new Response(JSON.stringify({ error: detail }), { status: upstream.status === 429 ? 429 : 502, headers: corsHeaders });
    }

    const parts = data?.candidates?.[0]?.content?.parts || [];
    const output: any[] = [];
    const textParts = parts.filter((p: any) => typeof p.text === "string" && p.text.trim());
    if (textParts.length) {
      output.push({ type: "message", role: "assistant", content: textParts.map((p: any) => ({ type: "output_text", text: p.text })) });
    }
    for (const part of parts) {
      if (!part.functionCall?.name) continue;
      output.push({
        type: "function_call",
        id: crypto.randomUUID(),
        call_id: crypto.randomUUID(),
        name: part.functionCall.name,
        arguments: JSON.stringify(part.functionCall.args || {}),
      });
    }
    if (!output.length) {
      const reason = data?.candidates?.[0]?.finishReason || "empty response";
      return new Response(JSON.stringify({ error: `Gemini javobi bo‘sh: ${reason}` }), { status: 502, headers: corsHeaders });
    }
    return new Response(JSON.stringify({ output, model: MODEL }), { status: 200, headers: corsHeaders });
  } catch (error) {
    return new Response(JSON.stringify({ error: error instanceof Error ? error.message : "Unexpected server error" }), { status: 500, headers: corsHeaders });
  }
});
