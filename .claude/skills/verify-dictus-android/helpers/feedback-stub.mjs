// Local stand-in for POST https://www.getdictus.com/api/feedback (#373), so a feedback test never
// posts to production. It runs the website's own handler (getdictus/dictus-website
// src/lib/feedback.ts, contract in docs/feedback-api.md): same validation, same status codes,
// same Retry-After. Only Telegram and the Vercel rate limit are faked.
//
//   node feedback-stub.mjs <dictus-website checkout> [port] [record.jsonl]
//
// Needs Node 22.18+ (it imports TypeScript). The emulator reaches it at
// http://10.0.2.2:<port>/api/feedback; build with -Pdictus.feedbackEndpoint=<that URL>.
// Switch what the next POSTs get with GET /__mode/<ok|429|429-bare|502|503>.
// Each valid report is appended to record.jsonl with the log replaced by its size and first two lines (title, export time).
import { appendFileSync } from "node:fs";
import { createServer } from "node:http";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const [website, port = "8787", record = "feedback-stub.jsonl"] = process.argv.slice(2);
if (!website) {
  console.error("usage: node feedback-stub.mjs <dictus-website checkout> [port] [record.jsonl]");
  process.exit(2);
}
const lib = (name) => import(pathToFileURL(resolve(website, "src/lib", name)).href);
const { handleFeedbackPost, methodNotAllowed } = await lib("feedback.ts");
const { escapeMarkdownV2 } = await lib("telegram.ts");

let mode = "ok";
let received = null;

const deps = () => ({
  env: mode === "503" ? {} : { TELEGRAM_BOT_TOKEN: "stub-token", TELEGRAM_FEEDBACK_CHAT_ID: "-100stub" },
  telegram: {
    escapeMarkdownV2,
    sendTelegramText: async () => {
      if (mode === "502") return { ok: false, error: "http 400" };
      return { ok: true, messageId: 1 };
    },
    sendTelegramDocument: async (_token, _chat, file) => {
      received.logFile = file.filename;
      return { ok: true, messageId: 2 };
    },
  },
  checkRateLimit: async () => ({ rateLimited: mode === "429" }),
  now: () => Date.now(),
});

createServer(async (req, res) => {
  const control = req.url.match(/^\/__mode\/(ok|429|429-bare|502|503)$/);
  if (control) {
    mode = control[1];
    console.log(`[stub] mode ${mode}`);
    res.end(`mode ${mode}\n`);
    return;
  }
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const body = Buffer.concat(chunks);
  let response;
  if (mode === "429-bare") {
    // The contract always sends Retry-After; this checks the app's 60 s default without it.
    response = new Response(JSON.stringify({ ok: false, error: "rate_limited" }), { status: 429 });
  } else if (req.method !== "POST") {
    response = methodNotAllowed();
  } else {
    let parsed = null;
    try { parsed = JSON.parse(body.toString("utf8")); } catch {}
    received = parsed && {
      ...parsed,
      log: parsed.log === undefined ? undefined : { bytes: Buffer.byteLength(parsed.log), head: parsed.log.split("\n").slice(0, 2) },
    };
    response = await handleFeedbackPost(
      new Request(`http://localhost${req.url}`, { method: "POST", headers: req.headers, body }),
      deps(),
    );
  }
  const text = await response.text();
  console.log(`[stub] ${req.method} ${req.url} ${body.length} B -> ${response.status} ${text}`);
  if (received) {
    appendFileSync(record, JSON.stringify({ at: new Date().toISOString(), mode, status: response.status, wireBytes: body.length, report: received }) + "\n");
    received = null;
  }
  res.writeHead(response.status, Object.fromEntries(response.headers));
  res.end(text);
}).listen(Number(port), "0.0.0.0", () => console.log(`[stub] feedback stub on :${port}, mode ${mode}`));
