import dgram from "dgram";
import { extractClip } from "./clipExtractor.js";

const UDP_PORT = 5005;
const TRIGGER_MESSAGE = "INTRUDER INTRUDER";

// ⏱ Cooldown settings
const COOLDOWN_MS = 10_000; // 10 seconds
let lastTriggerTime = 0;

export function startUdpListener() {
  const server = dgram.createSocket("udp4");

  server.on("listening", () => {
    const addr = server.address();
    console.log(`📡 UDP listener running on ${addr.address}:${addr.port}`);
  });

  server.on("message", (msg, rinfo) => {
    const payload = msg.toString("utf8").trim();
    const now = Date.now();

    console.log(
      `📨 UDP from ${rinfo.address}:${rinfo.port} → "${payload}"`
    );

    // 🔐 Exact trigger match
    if (payload !== TRIGGER_MESSAGE) {
      console.log("⚠️ Ignored UDP message (not trigger phrase)");
      return;
    }

    // ⏱ Cooldown check
    const timeSinceLast = now - lastTriggerTime;
    if (timeSinceLast < COOLDOWN_MS) {
      console.log(
        `⏳ Trigger ignored (cooldown ${Math.ceil(
          (COOLDOWN_MS - timeSinceLast) / 1000
        )}s remaining)`
      );
      return;
    }

    // ✅ Accept trigger
    lastTriggerTime = now;
    console.log("🚨 INTRUDER trigger accepted - extracting clip");
    extractClip({ beforeSeconds: 5, afterSeconds: 5 });
  });

  server.on("error", (err) => {
    console.error("❌ UDP error:", err);
    server.close();
  });

  server.bind(UDP_PORT);
}
