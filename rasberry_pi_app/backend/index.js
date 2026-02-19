import "dotenv/config";
import express from "express";
import fs from "fs";
import path from "path";
import { startStream, stopStream, getStreamState, forceUpdateState } from "./streamManager.js";
import { startBufferCleanup } from "./bufferCleanup.js";
import { extractClip } from "./clipExtractor.js";
import { startUploader } from "./uploader.js";
import { startUdpListener } from "./udpListener.js";
import { getConfig, getCloudUploadUrl, hasConfigured, saveConfig } from "./config.js";

const app = express();
app.use(express.json());

// CORS (for frontend later)
app.use((req, res, next) => {
  res.header("Access-Control-Allow-Origin", "*");
  res.header("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  res.header("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") return res.sendStatus(204);
  next();
});

// Serve HLS
app.use(
  "/hls",
  express.static("hls", {
    setHeaders: (res) => res.set("Cache-Control", "no-cache"),
  })
);

// ---------- Setup page (first-time config) ----------
function setupPageHtml(current) {
  const c = current || getConfig();
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Pi Backend Setup</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body { font-family: system-ui, sans-serif; background: #1a1a2e; color: #eee; min-height: 100vh; padding: 24px; }
    .container { max-width: 520px; margin: 0 auto; }
    h1 { margin-bottom: 8px; font-size: 1.5rem; }
    .sub { color: #888; margin-bottom: 20px; font-size: 14px; }
    .form-group { margin-bottom: 14px; }
    label { display: block; margin-bottom: 4px; font-size: 14px; color: #ccc; }
    input { width: 100%; padding: 10px; border: 1px solid #444; border-radius: 8px; background: #16213e; color: #fff; font-size: 14px; }
    input:focus { outline: none; border-color: #0f3460; }
    button { padding: 12px 20px; background: #0f3460; color: #fff; border: none; border-radius: 8px; font-weight: 600; cursor: pointer; margin-top: 8px; }
    button:hover { background: #16213e; }
    .msg { margin-top: 12px; padding: 10px; border-radius: 8px; font-size: 14px; display: none; }
    .msg.ok { background: #0d3320; color: #8f8; display: block; }
    .msg.err { background: #331a1a; color: #f88; display: block; }
    a { color: #6af; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Raspberry Pi Backend Setup</h1>
    <p class="sub">Configure cloud upload and device ID. Saved to config.json (no restart needed).</p>
    <form id="setupForm">
      <div class="form-group">
        <label for="cloudBaseUrl">Cloud base URL</label>
        <input type="url" id="cloudBaseUrl" name="cloudBaseUrl" placeholder="http://192.168.1.68:3001" value="${(c.CLOUD_BASE_URL || "").replace(/"/g, "&quot;")}" />
      </div>
      <div class="form-group">
        <label for="uploadEndpoint">Upload endpoint path</label>
        <input type="text" id="uploadEndpoint" name="uploadEndpoint" placeholder="/api/events/upload" value="${(c.CLOUD_UPLOAD_ENDPOINT || "").replace(/"/g, "&quot;")}" />
      </div>
      <div class="form-group">
        <label for="deviceId">Device ID</label>
        <input type="text" id="deviceId" name="deviceId" placeholder="pi-dev-001" value="${(c.DEVICE_ID || "").replace(/"/g, "&quot;")}" />
      </div>
      <div class="form-group">
        <label for="uploadPollMs">Upload poll interval (ms)</label>
        <input type="number" id="uploadPollMs" name="uploadPollMs" min="2000" step="1000" value="${c.UPLOAD_POLL_MS ?? 8000}" />
      </div>
      <div class="form-group">
        <label for="udpPort">UDP listener port</label>
        <input type="number" id="udpPort" name="udpPort" min="1" max="65535" value="${c.UDP_PORT ?? 5005}" />
      </div>
      <button type="submit">Save configuration</button>
    </form>
    <div id="msg" class="msg"></div>
    <p style="margin-top: 16px; font-size: 13px; color: #888;">
      <a href="/health">Health</a> · <a href="/setup">Reload setup</a>
    </p>
  </div>
  <script>
    document.getElementById("setupForm").onsubmit = async (e) => {
      e.preventDefault();
      const msg = document.getElementById("msg");
      const payload = {
        CLOUD_BASE_URL: document.getElementById("cloudBaseUrl").value.trim(),
        CLOUD_UPLOAD_ENDPOINT: document.getElementById("uploadEndpoint").value.trim() || "/api/events/upload",
        DEVICE_ID: document.getElementById("deviceId").value.trim() || "pi-dev-001",
        UPLOAD_POLL_MS: parseInt(document.getElementById("uploadPollMs").value, 10) || 8000,
        UDP_PORT: parseInt(document.getElementById("udpPort").value, 10) || 5005
      };
      try {
        const r = await fetch("/setup", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
        const data = await r.json();
        if (r.ok) {
          msg.className = "msg ok";
          msg.textContent = "Configuration saved. Upload URL: " + (data.uploadUrl || "");
        } else {
          msg.className = "msg err";
          msg.textContent = data.error || "Save failed";
        }
      } catch (err) {
        msg.className = "msg err";
        msg.textContent = err.message;
      }
    };
  </script>
</body>
</html>`;
}

app.get("/", (req, res) => {
  if (!hasConfigured()) {
    res.redirect(302, "/setup");
    return;
  }
  res.type("html").send(`
    <!DOCTYPE html><html><head><meta charset="UTF-8"><title>Pi Backend</title></head>
    <body style="font-family:system-ui;padding:24px;background:#1a1a2e;color:#eee;">
      <h1>Raspberry Pi Backend</h1>
      <p>Configured. <a href="/setup">Setup</a> · <a href="/health">Health</a></p>
    </body></html>`);
});

app.get("/setup", (req, res) => {
  res.type("html").send(setupPageHtml());
});

app.post("/setup", (req, res) => {
  try {
    const body = req.body || {};
    saveConfig({
      CLOUD_BASE_URL: body.CLOUD_BASE_URL,
      CLOUD_UPLOAD_ENDPOINT: body.CLOUD_UPLOAD_ENDPOINT,
      DEVICE_ID: body.DEVICE_ID,
      UPLOAD_POLL_MS: body.UPLOAD_POLL_MS,
      UDP_PORT: body.UDP_PORT,
    });
    res.json({ status: "ok", uploadUrl: getCloudUploadUrl() });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Start ring-buffer cleanup
startBufferCleanup();
startUploader();


// Build FFmpeg input args
function buildInputArgs(type, value) {
  if (type === "webcam") {
    if (process.platform === "darwin") {
      return ["-f", "avfoundation", "-framerate", "30", "-i", "0"];
    }
    if (process.platform === "linux") {
      return ["-f", "v4l2", "-framerate", "30", "-i", value || "/dev/video0"];
    }
  }

  if (type === "rtsp") {
    if (!value) throw new Error("Missing RTSP URL");
    return ["-rtsp_transport", "tcp", "-i", value];
  }

  if (type === "file") {
    if (!value) throw new Error("Missing file path");
    return ["-re", "-i", value];
  }

  throw new Error("Invalid input type");
}

// Start stream
app.post("/start", (req, res) => {
  try {
    const input = buildInputArgs(req.body.type, req.body.value);
    const started = startStream(input);
    if (!started) return res.status(409).json({ error: "Stream already running" });
    res.json({ status: "ok", message: "Stream started" });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// Stop stream
app.post("/stop", async (req, res) => {
  try {
    const stopped = await stopStream();
    if (!stopped) return res.status(409).json({ error: "No active stream" });
    res.json({ status: "ok", message: "Stream stopped" });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Motion trigger
app.post("/motion", async (_req, res) => {
  const saved = await extractClip({ beforeSeconds: 5, afterSeconds: 5 });
  res.json({ status: "ok", saved: !!saved, file: saved });
});

// Health check
app.get("/health", (_req, res) => {
  const state = getStreamState();
  console.log("🔍 Health check - state:", state);

  let hlsSegments = [];
  if (fs.existsSync("hls")) {
    hlsSegments = fs.readdirSync("hls").filter(f => f.endsWith(".ts"));
  }

  res.json({
    stream: state,
    hls: {
      playlist: fs.existsSync("hls/stream.m3u8"),
      segmentCount: hlsSegments.length,
    }
  });
});

// Debug endpoint
app.get("/debug/state", (_req, res) => {
  const state = forceUpdateState();
  res.json({
    debug: true,
    state,
    timestamp: new Date().toISOString(),
  });
});

// Clear all local data
app.delete("/clear-all", (_req, res) => {
  try {
    // Clear events directory
    if (fs.existsSync("events")) {
      const files = fs.readdirSync("events");
      files.forEach(file => {
        fs.unlinkSync(path.join("events", file));
      });
    }

    // Clear buffer directory
    if (fs.existsSync("buffer")) {
      const files = fs.readdirSync("buffer");
      files.forEach(file => {
        fs.unlinkSync(path.join("buffer", file));
      });
    }

    // Clear HLS directory
    if (fs.existsSync("hls")) {
      const files = fs.readdirSync("hls");
      files.forEach(file => {
        fs.unlinkSync(path.join("hls", file));
      });
    }

    // Clear upload queue
    if (fs.existsSync("queue.json")) {
      fs.unlinkSync("queue.json");
    }

    res.json({ 
      status: "ok", 
      message: "All local data cleared" 
    });
  } catch (err) {
    console.error("❌ Clear local data failed:", err.message);
    res.status(500).json({ error: "Failed to clear local data" });
  }
});

app.listen(4000, () => {
  console.log("📹 Raspberry Pi App running on http://localhost:4000");
  
  // Start background services
  startBufferCleanup();
  startUploader();
  startUdpListener();
});
