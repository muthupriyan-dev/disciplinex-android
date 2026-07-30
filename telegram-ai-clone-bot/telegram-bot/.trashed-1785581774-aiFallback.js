// aiFallback.js
// Multi-provider AI fallback chain.
// Order: Gemini 2.5 Flash -> Gemini 2.5 Flash Lite -> Gemini 2.0 Flash
//        -> Groq -> OpenRouter -> Cohere -> HuggingFace

const { GoogleGenerativeAI } = require("@google/generative-ai");
const { CohereClient } = require("cohere-ai");
const Groq = require("groq-sdk");

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const cohere = new CohereClient({ token: process.env.COHERE_API_KEY });
const groq = new Groq({ apiKey: process.env.GROQ_API_KEY });

// Edit this order / add-remove models as you like.
const MODEL_CHAIN = [
  { provider: "gemini", model: "gemini-2.5-flash" },
  { provider: "gemini", model: "gemini-2.5-flash-lite" },
  { provider: "gemini", model: "gemini-2.0-flash" },
  { provider: "groq", model: "llama-3.3-70b-versatile" },
  { provider: "openrouter", model: "meta-llama/llama-3.3-70b-instruct:free" },
  { provider: "cohere", model: "command-r-plus" },
  { provider: "huggingface", model: "meta-llama/Llama-3.1-8B-Instruct" },
];

function isSkippableError(error) {
  const msg = (error?.message || "").toLowerCase();
  return (
    error?.status === 429 ||
    error?.status === 503 ||
    msg.includes("resource_exhausted") ||
    msg.includes("quota") ||
    msg.includes("rate_limit") ||
    msg.includes("rate limit") ||
    msg.includes("overloaded")
  );
}

async function callGemini(model, prompt) {
  const genModel = genAI.getGenerativeModel({ model });
  const result = await genModel.generateContent(prompt);
  return result.response.text();
}

async function callGroq(model, prompt) {
  const completion = await groq.chat.completions.create({
    messages: [{ role: "user", content: prompt }],
    model,
  });
  return completion.choices[0].message.content;
}

async function callOpenRouter(model, prompt) {
  const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${process.env.OPENROUTER_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model,
      messages: [{ role: "user", content: prompt }],
    }),
  });

  const data = await res.json();
  if (!res.ok) {
    const err = new Error(data?.error?.message || "OpenRouter error");
    err.status = res.status;
    throw err;
  }
  return data.choices[0].message.content;
}

async function callCohere(model, prompt) {
  const response = await cohere.chat({
    model,
    message: prompt,
  });
  return response.text;
}

async function callHuggingFace(model, prompt) {
  const res = await fetch(
    `https://api-inference.huggingface.co/models/${model}`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${process.env.HF_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ inputs: prompt }),
    }
  );

  const data = await res.json();
  if (!res.ok) {
    const err = new Error(data?.error || "HuggingFace error");
    err.status = res.status;
    throw err;
  }
  // Response shape varies by model type
  if (Array.isArray(data)) return data[0].generated_text;
  if (data.generated_text) return data.generated_text;
  return JSON.stringify(data);
}

async function generateWithFallback(prompt, index = 0) {
  if (index >= MODEL_CHAIN.length) {
    throw new Error("🚫 All AI providers exhausted. Try again later.");
  }

  const { provider, model } = MODEL_CHAIN[index];

  try {
    let text;
    switch (provider) {
      case "gemini":
        text = await callGemini(model, prompt);
        break;
      case "groq":
        text = await callGroq(model, prompt);
        break;
      case "openrouter":
        text = await callOpenRouter(model, prompt);
        break;
      case "cohere":
        text = await callCohere(model, prompt);
        break;
      case "huggingface":
        text = await callHuggingFace(model, prompt);
        break;
      default:
        throw new Error(`Unknown provider: ${provider}`);
    }

    if (!text || !text.trim()) {
      throw new Error("Empty response");
    }

    console.log(`✅ Reply generated using: ${provider}/${model}`);
    return text;
  } catch (error) {
    console.log(`⚠️  Failed: ${provider}/${model} — ${error.message}`);

    // Skip to next provider whether it's a quota error or any other
    // failure — the priority is keeping the bot responsive.
    return generateWithFallback(prompt, index + 1);
  }
}

module.exports = { generateWithFallback, isSkippableError, MODEL_CHAIN };
