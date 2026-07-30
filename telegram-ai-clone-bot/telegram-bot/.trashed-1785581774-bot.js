// bot.js
// Telegram AI clone bot — auto-replies in your personal texting style.
// Uses a multi-provider AI fallback chain so quota limits on one
// provider don't take the bot down.

require("dotenv").config();
const TelegramBot = require("node-telegram-bot-api");
const { generateWithFallback } = require("./aiFallback");

const TELEGRAM_TOKEN = process.env.TELEGRAM_BOT_TOKEN;

if (!TELEGRAM_TOKEN) {
  console.error("❌ TELEGRAM_BOT_TOKEN missing in .env / environment variables");
  process.exit(1);
}

// Polling mode — works fine on Render as a background worker / web service.
const bot = new TelegramBot(TELEGRAM_TOKEN, { polling: true });

// ---- Edit this to match your own texting style ----
const PERSONA_PROMPT = `
You are replying to Telegram messages AS ME (Muthu), in my own personal texting style.
Style rules:
- Casual, friendly Tanglish (Tamil + English mix) when it fits naturally.
- Short, natural replies — like real texting, not essays.
- No robotic or overly formal tone. No "As an AI..." disclaimers.
- Keep the same energy/tone as the incoming message.
`.trim();

// simple in-memory chat history per user (resets on restart / Render redeploy)
const chatHistory = new Map();
const MAX_HISTORY = 6; // last 6 messages kept for context

function buildPrompt(chatId, incomingText) {
  const history = chatHistory.get(chatId) || [];
  const historyText = history
    .map((h) => `${h.role === "user" ? "Them" : "Me"}: ${h.text}`)
    .join("\n");

  return `${PERSONA_PROMPT}\n\nConversation so far:\n${historyText}\n\nThem: ${incomingText}\nMe:`;
}

function pushHistory(chatId, role, text) {
  const history = chatHistory.get(chatId) || [];
  history.push({ role, text });
  while (history.length > MAX_HISTORY) history.shift();
  chatHistory.set(chatId, history);
}

bot.on("message", async (msg) => {
  const chatId = msg.chat.id;
  const incomingText = msg.text;

  if (!incomingText) return; // ignore stickers/photos/etc for now

  // Basic commands
  if (incomingText === "/start") {
    return bot.sendMessage(chatId, "Vanakkam! Bot ready 🙂");
  }
  if (incomingText === "/reset") {
    chatHistory.delete(chatId);
    return bot.sendMessage(chatId, "Memory clear pannitten. Fresh-a start pannalam.");
  }

  try {
    bot.sendChatAction(chatId, "typing");

    const prompt = buildPrompt(chatId, incomingText);
    const replyText = await generateWithFallback(prompt);

    pushHistory(chatId, "user", incomingText);
    pushHistory(chatId, "bot", replyText);

    await bot.sendMessage(chatId, replyText);
  } catch (error) {
    console.error("❌ Failed to generate reply:", error.message);
    await bot.sendMessage(
      chatId,
      "Ippo konjam busy-a irukken, konja neram kazhichi try pannu 🙏"
    );
  }
});

bot.on("polling_error", (err) => {
  console.error("Polling error:", err.message);
});

console.log("🤖 Bot started and polling for messages...");
