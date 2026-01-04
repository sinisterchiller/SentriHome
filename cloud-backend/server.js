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
} from "./googleAuth.js";

import { uploadToDrive, handleEventUpload } from "./driveUploader.js";
import { connectMongo } from "./mongo.js";
import { Event } from "./models/Event.js";

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
  try {
    const client = createOAuthClient();
    await handleOAuthCallback(client, req.query.code);
    res.send("✅ Google Drive connected. You can close this tab.");
  } catch (err) {
    console.error("OAuth callback error:", err.message);
    res.status(500).send("OAuth failed");
  }
});

/* =========================
   Health check
========================= */

app.get("/status", (_req, res) => {
  res.json({ status: "ok", service: "cloud-backend" });
});

/* =========================
   Stream control endpoints (for frontend convenience)
========================= */

app.post("/start", (req, res) => {
  res.json({ 
    status: "ok", 
    message: "Stream control not available on cloud backend. Use Pi backend at localhost:4000" 
  });
});

app.post("/stop", (req, res) => {
  res.json({ 
    status: "ok", 
    message: "Stream control not available on cloud backend. Use Pi backend at localhost:4000" 
  });
});

app.post("/motion", (req, res) => {
  res.json({ 
    status: "ok", 
    message: "Motion trigger not available on cloud backend. Use Pi backend at localhost:4000" 
  });
});

/* =========================
   Event upload from Pi
========================= */

app.post("/api/events/upload", upload.single("file"), async (req, res) => {
  const deviceId = req.body.deviceId || "unknown";
  const type = req.body.type || "video";

  if (!req.file) {
    return res.status(400).json({ error: "Missing file" });
  }

  try {
    const event = await handleEventUpload(req.file.path, deviceId, type);

    // OPTIONAL: delete local copy after upload
    fs.unlinkSync(req.file.path);

    res.json({
      status: "ok",
      event,
    });
  } catch (err) {
    console.error("❌ Upload failed:", err.message);
    res.status(500).json({ error: "Upload failed" });
  }
});

/* =========================
   Frontend API - List events
========================= */

app.get("/api/events", async (_req, res) => {
  const events = await Event.find({})
    .sort({ createdAt: -1 })
    .limit(100);

  // Transform to match frontend expected format
  const formattedEvents = events.map(event => ({
    _id: event._id,
    id: event._id,
    deviceId: event.deviceId,
    createdAt: event.createdAt,
    timestamp: event.createdAt,
    status: event.status,
    thumbnail: event.thumbnailDriveId ? {
      driveFileId: event.thumbnailDriveId,
      webViewLink: event.thumbnailUrl,
      directUrl: `https://drive.google.com/uc?id=${event.thumbnailDriveId}`
    } : null,
    video: {
      driveFileId: event.driveFileId,
      webViewLink: event.driveLink,
      directUrl: `https://drive.google.com/uc?id=${event.driveFileId}`
    }
  }));

  res.json(formattedEvents);
});

/* =========================
   Clear all data endpoint
========================= */

app.delete("/api/clear-all", async (_req, res) => {
  try {
    // Clear MongoDB events
    await Event.deleteMany({});
    
    // Note: Drive files would need additional cleanup logic
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
app.listen(PORT, () => {
  console.log(`☁️ Cloud backend running at http://localhost:${PORT}`);
});
