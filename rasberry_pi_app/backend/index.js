import express from "express";
import fs from "fs";
import path from "path";
import { startStream, stopStream, getStreamState } from "./streamManager.js";
import { startBufferCleanup } from "./bufferCleanup.js";
import { extractClip } from "./clipExtractor.js";

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
app.post("/stop", (_req, res) => {
  const stopped = stopStream();
  if (!stopped) return res.status(409).json({ error: "No active stream" });
  res.json({ status: "ok", message: "Stream stopped" });
});

// Motion trigger
app.post("/motion", (_req, res) => {
  extractClip({ beforeSeconds: 5, afterSeconds: 5 });
  res.send("Motion event captured");
});

// Health check
app.get("/health", (_req, res) => {
  const state = getStreamState();

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

app.listen(4000, () => {
  console.log("📹 Raspberry Pi App running on http://localhost:4000");
});
