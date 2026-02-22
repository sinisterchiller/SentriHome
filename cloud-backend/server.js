// cloud-backend/server.js

import express from "express";
import cors from "cors";
import fs from "fs";
import path from "path";
import multer from "multer";

import {
  createOAuthClient,
  getAuthUrl,
  handleOAuthCallback,
  getDriveUserEmail,
  saveTokensForUser,
} from "./googleAuth.js";
import { requireAuth, requireAuthOrQueryToken, createToken, invalidateToken } from "./auth.js";
import { connectMongo } from "./mongo.js";
import { Event } from "./models/Event.js";
import { Device } from "./models/Device.js";
import { uploadToS3, getPresignedUrl, pruneOldSegments } from "./s3Client.js";
import { getHlsCache } from "./hlsCache.js";

const app = express();
app.use(cors({
  origin: ["http://localhost:5173", "http://localhost:3000", "http://localhost:5174"],
  methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
  allowedHeaders: ["Content-Type", "Authorization"],
}));
app.use(express.json());

/* =========================
   Upload (multer) setup
========================= */

const UPLOAD_DIR = "uploads";
fs.mkdirSync(UPLOAD_DIR, { recursive: true });

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
  filename: (_req, file, cb) => {
    const ext = path.extname(file.originalname) || ".mp4";
    cb(null, `event_${Date.now()}${ext}`);
  },
});

const upload = multer({ storage });

/* =========================
   MongoDB connection
========================= */

await connectMongo();

/* =========================
   OAuth routes
========================= */

app.get("/auth/google", (_req, res) => {
  const client = createOAuthClient();
  res.redirect(getAuthUrl(client));
});

app.get("/auth/google/callback", async (req, res) => {
  console.log("OAuth callback hit, code=" + (req.query.code ? "present" : "MISSING"));
  try {
    const client = createOAuthClient();
    const tokens = await handleOAuthCallback(client, req.query.code);
    let email = null;
    try {
      email = await getDriveUserEmail(client);
      if (email) {
        console.log("Google Drive connected as:", email);
        saveTokensForUser(email, tokens);
      }
    } catch (e) {
      console.warn("Could not fetch user email:", e.message);
    }
    if (!email) {
      res.redirect("home-security://auth-error?msg=" + encodeURIComponent("Could not get user email"));
      return;
    }
    const authToken = createToken(email);
    const params = new URLSearchParams({ email, token: authToken });
    res.redirect("home-security://auth-success?" + params.toString());
  } catch (err) {
    console.error("OAuth callback error:", err.message);
    res.redirect("home-security://auth-error?msg=" + encodeURIComponent(err.message || "OAuth failed"));
  }
});

/* =========================
   Auth status (requires Bearer token)
========================= */

app.get("/api/auth/me", requireAuth, (req, res) => {
  res.json({ email: req.user.email });
});

app.post("/api/auth/logout", requireAuth, (req, res) => {
  const raw = req.headers.authorization;
  const token = raw && raw.startsWith("Bearer ") ? raw.slice(7).trim() : null;
  invalidateToken(token);
  console.log("User logged out:", req.user.email);
  res.json({ status: "ok", message: "Logged out" });
});

/* =========================
   Device linking (Pi -> user, for upload attribution)
========================= */

app.post("/api/devices/link", requireAuth, async (req, res) => {
  const deviceId = req.body?.deviceId?.trim?.();
  if (!deviceId) {
    return res.status(400).json({ error: "deviceId required" });
  }
  await Device.findOneAndUpdate(
    { deviceId },
    { ownerEmail: req.user.email, updatedAt: new Date() },
    { upsert: true, new: true }
  );
  console.log("Device linked: %s → %s", deviceId, req.user.email);
  res.json({ status: "ok", deviceId, ownerEmail: req.user.email });
});

/* =========================
   Health check
========================= */

app.get("/status", (_req, res) => {
  res.json({ status: "ok", service: "cloud-backend" });
});

/* =========================
   Stream: Pi pushes HLS segments
========================= */

/**
 * POST /api/stream/segment
 * Body: multipart, field "segment" = .ts file, "deviceId", "seq"
 * Called by Pi backend for every ffmpeg output segment.
 */
app.post("/api/stream/segment", upload.single("segment"), async (req, res) => {
  const deviceId = req.body.deviceId || "default";
  const file = req.file;
  if (!file) return res.status(400).json({ error: "No segment file" });

  try {
    const seq = req.body.seq || Date.now();
    const s3Key = `live/${deviceId}/seg_${seq}.ts`;

    const data = fs.readFileSync(file.path);
    await uploadToS3(s3Key, data, "video/mp2t");
    fs.unlinkSync(file.path);

    const cache = getHlsCache(deviceId);
    cache.addSegment(s3Key);

    pruneOldSegments(`live/${deviceId}/`, 10 * 60 * 1000).catch(console.error);

    res.json({ status: "ok", key: s3Key });
  } catch (err) {
    if (file?.path && fs.existsSync(file.path)) fs.unlinkSync(file.path);
    console.error("Segment upload error:", err.message);
    res.status(500).json({ error: "Segment upload failed" });
  }
});

/**
 * GET /api/stream/playlist/:deviceId
 * Returns a live HLS m3u8 playlist with presigned S3 URLs.
 */
app.get("/api/stream/playlist/:deviceId", async (req, res) => {
  const deviceId = req.params.deviceId || "default";
  const cache = getHlsCache(deviceId);

  if (!cache.isLive || cache.age > 15000) {
    return res.status(404).json({ error: "Stream offline" });
  }

  const playlist = await cache.buildPlaylist(getPresignedUrl);
  if (!playlist) return res.status(404).json({ error: "No segments yet" });

  res.setHeader("Content-Type", "application/vnd.apple.mpegurl");
  res.setHeader("Cache-Control", "no-cache, no-store");
  res.send(playlist);
});

/**
 * GET /api/stream/status/:deviceId
 * Lightweight poll endpoint to check if stream is live.
 */
app.get("/api/stream/status/:deviceId", (req, res) => {
  const cache = getHlsCache(req.params.deviceId || "default");
  res.json({
    live: cache.isLive && cache.age < 15000,
    segmentCount: cache.segments.length,
    lastUpdated: cache.lastUpdated,
  });
});

/* =========================
   Event upload from Pi
========================= */

app.post("/api/events/upload", upload.single("file"), async (req, res) => {
  const deviceId = req.body.deviceId || "unknown";
  const type = req.body.type || "video";
  if (!req.file) return res.status(400).json({ error: "Missing file" });

  try {
    const ext = path.extname(req.file.originalname) || (type === "thumbnail" ? ".jpg" : ".mp4");
    const eventId = `${deviceId}_${Date.now()}`;
    const s3Key = `events/${deviceId}/${eventId}${ext}`;
    const contentType = type === "thumbnail" ? "image/jpeg" : "video/mp4";

    let ownerEmail = null;
    const device = await Device.findOne({ deviceId }).lean();
    if (device?.ownerEmail) ownerEmail = device.ownerEmail;

    const data = fs.readFileSync(req.file.path);
    await uploadToS3(s3Key, data, contentType);
    fs.unlinkSync(req.file.path);

    // Save to MongoDB (with ownerEmail for multi-user)
    let event;
    if (type === "video") {
      event = await Event.create({
        ownerEmail: ownerEmail || undefined,
        deviceId,
        filename: req.file.originalname || `${eventId}.mp4`,
        s3Key,
        status: "ready",
      });
    } else {
      // Thumbnail — attach to latest pending event for this device
      event = await Event.findOneAndUpdate(
        { deviceId, thumbnailS3Key: { $exists: false } },
        { thumbnailS3Key: s3Key },
        { new: true, sort: { createdAt: -1 } }
      );
    }

    res.json({ status: "ok", event });
  } catch (err) {
    if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    res.status(500).json({ error: "Upload failed" });
  }
});

/* =========================
   Frontend API - List events (only this user's)
========================= */

app.get("/api/events", requireAuth, async (req, res) => {
  const events = await Event.find({ ownerEmail: req.user.email })
    .sort({ createdAt: -1 })
    .limit(100);
  res.json(events.map(e => ({
    _id: e._id,
    id: e._id,
    deviceId: e.deviceId,
    filename: e.filename,
    createdAt: e.createdAt,
    timestamp: e.createdAt,
    status: e.status,
    hasVideo: !!e.s3Key,
    hasThumbnail: !!e.thumbnailS3Key,
  })));
});

/* =========================
   Serve clips from S3 (auth required, owner-only)
========================= */

app.get("/api/clips/:eventId", requireAuthOrQueryToken, async (req, res) => {
  const eventId = req.params.eventId?.trim();
  if (!eventId) return res.status(400).json({ error: "Invalid event ID" });
  const event = await Event.findById(eventId);
  if (!event?.s3Key) return res.status(404).json({ error: "Not found" });
  if (event.ownerEmail && event.ownerEmail !== req.user.email) {
    return res.status(403).json({ error: "Forbidden: not your clip" });
  }
  const url = await getPresignedUrl(event.s3Key, 3600);
  res.redirect(302, url);
});

app.get("/api/clips/:eventId/thumbnail", requireAuthOrQueryToken, async (req, res) => {
  const eventId = req.params.eventId?.trim();
  if (!eventId) return res.status(400).json({ error: "Invalid event ID" });
  const event = await Event.findById(eventId);
  if (!event?.thumbnailS3Key) return res.status(404).json({ error: "No thumbnail" });
  if (event.ownerEmail && event.ownerEmail !== req.user.email) {
    return res.status(403).json({ error: "Forbidden: not your clip" });
  }
  const url = await getPresignedUrl(event.thumbnailS3Key, 3600);
  res.redirect(302, url);
});

/* =========================
   Clear all data endpoint
========================= */

app.delete("/api/clear-all", async (_req, res) => {
  try {
    // Clear MongoDB events
    await Event.deleteMany({});
    
    // Note: S3 files would need additional cleanup logic
    // For now, just clear the database
    
    res.json({ 
      status: "ok", 
      message: "All data cleared from database",
      deletedCount: 0 
    });
  } catch (err) {
    console.error("❌ Clear data failed:", err.message);
    res.status(500).json({ error: "Failed to clear data" });
  }
});

/* =========================
   Global error handlers
========================= */

app.use((err, _req, res, _next) => {
  console.error("Unhandled error:", err);
  res.status(500).send("Internal Server Error");
});

process.on("uncaughtException", (err) => {
  console.error("Uncaught Exception:", err);
  process.exit(1);
});

process.on("unhandledRejection", (reason) => {
  console.error("Unhandled Rejection:", reason);
  process.exit(1);
});

/* =========================
   Start server
========================= */

const PORT = process.env.PORT || 3001;
app.listen(PORT, '0.0.0.0',() => {
  console.log(`☁️ Cloud backend running at http://localhost:${PORT}`);
});
