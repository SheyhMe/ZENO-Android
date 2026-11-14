type ChatMessage = {
  role: "user" | "assistant";
  content: string;
};

const MAX_MESSAGES = 20;
const MAX_MESSAGE_LENGTH = 6000;

function replyError(res: any, status: number, error: string) {
  res.status(status).json({ error });
}

export default async function handler(req: any, res: any) {
  res.setHeader("Cache-Control", "no-store");

  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return replyError(res, 405, "Method not allowed");
  }

  const authorization = req.headers.authorization ?? "";
  const accessToken = authorization.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!accessToken) return replyError(res, 401, "Sign in to ZENO Cloud first");

  const supabaseUrl = process.env.SUPABASE_URL;
  const supabaseKey = process.env.SUPABASE_PUBLISHABLE_KEY;
  const gatewayToken = req.headers["x-vercel-oidc-token"] ?? process.env.VERCEL_OIDC_TOKEN;
  if (!supabaseUrl || !supabaseKey || !gatewayToken) {
    return replyError(res, 503, "ZENO backend is not fully configured");
  }

  try {
    const authResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
      headers: {
        apikey: supabaseKey,
        Authorization: `Bearer ${accessToken}`,
      },
    });
    if (!authResponse.ok) return replyError(res, 401, "Session expired. Sign in again.");

    const body = typeof req.body === "string" ? JSON.parse(req.body) : req.body;
    if (!body || !Array.isArray(body.messages) || body.messages.length === 0) {
      return replyError(res, 400, "A non-empty messages array is required");
    }

    const messages: ChatMessage[] = body.messages
      .slice(-MAX_MESSAGES)
      .map((item: any) => ({
        role: item?.role === "assistant" ? "assistant" : "user",
        content: typeof item?.content === "string" ? item.content.trim() : "",
      }))
      .filter((item: ChatMessage) => item.content.length > 0 && item.content.length <= MAX_MESSAGE_LENGTH);

    if (messages.length === 0 || messages[messages.length - 1].role !== "user") {
      return replyError(res, 400, "The last message must be a valid user message");
    }

    const aiResponse = await fetch("https://ai-gateway.vercel.sh/v1/responses", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${gatewayToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "openai/gpt-5.4-mini",
        instructions: [
          "You are ZENO, a personal AI assistant.",
          "Understand Uzbek Latin and English. Reply in the same language as the latest user message.",
          "Use natural, clear Uzbek when the user writes Uzbek. Address the user respectfully with siz.",
          "Be practical and concise. Do not claim to control the user's phone or complete external actions.",
          "If a request needs a phone action, explain that the user can use ZENO's voice controls for that action.",
          "Never invent live facts, search results, or completed actions."
        ].join("\n"),
        input: messages,
        max_output_tokens: 1200,
      }),
    });

    if (!aiResponse.ok) {
      const providerError = await aiResponse.json().catch(() => ({}));
      const status = aiResponse.status === 429 ? 429 : 502;
      return replyError(res, status, status === 429
        ? "AI so'rovlari vaqtincha cheklangan. Birozdan keyin urinib ko'ring."
        : "AI xizmati hozir javob bermayapti.");
    }

    const result = await aiResponse.json();
    const text = typeof result.output_text === "string"
      ? result.output_text.trim()
      : (result.output ?? [])
          .flatMap((item: any) => item.content ?? [])
          .filter((part: any) => part.type === "output_text" && typeof part.text === "string")
          .map((part: any) => part.text)
          .join("\n")
          .trim();

    if (!text) return replyError(res, 502, "AI javobi bo'sh qaytdi.");
    return res.status(200).json({ reply: text, model: "openai/gpt-5.4-mini" });
  } catch {
    return replyError(res, 500, "ZENO serverida vaqtinchalik xatolik yuz berdi.");
  }
}
