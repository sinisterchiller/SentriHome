import express from "express";
import fs from "fs";
import path from "path";
import { startStream, stopStream, getStreamState, forceUpdateState } from "./streamManager.js";
import { startBufferCleanup } from "./bufferCleanup.js";
import { extractClip } from "./clipExtractor.js";
import { startUploader } from "./uploader.js";
import { startUdpListener } from "./udpListener.js";


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
