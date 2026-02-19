import fs from "fs";
import path from "path";
import FormData from "form-data";
import axios from "axios";
import { getConfig, getCloudUploadUrl } from "./config.js";

const EVENTS_DIR = "events";
const MAX_RETRIES = 3;

let running = false;

function isVideoFile(name) {
  const lower = name.toLowerCase();
  return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv");
}

function listEventFiles() {
  if (!fs.existsSync(EVENTS_DIR)) return [];
  return fs
    .readdirSync(EVENTS_DIR)
    .filter((f) => isVideoFile(f))
    .filter((f) => !f.endsWith(".uploading"))
    .sort(); // oldest first (by name)
}

async function uploadFile(filePath) {
  const url = getCloudUploadUrl();
  const { DEVICE_ID } = getConfig();
  let retries = 0;

  while (retries < MAX_RETRIES) {
    try {
      const form = new FormData();
      form.append("deviceId", DEVICE_ID);
      form.append("file", fs.createReadStream(filePath));
      
      const response = await axios.post(url, form, {
        headers: {
          ...form.getHeaders(),
          'Content-Type': 'multipart/form-data'
        },
        timeout: 30000
      });
      
      if (response.data.status === 'ok') {
        console.log(`✅ Cloud response: ${response.data.driveFileId}`);
        return true;
      }
      
      return false;
    } catch (error) {
      retries++;
      console.log(`⚠️ Upload attempt ${retries} failed: ${error.message}`);
      
      if (retries >= MAX_RETRIES) {
        return false;
      }
      
      // Wait before retry (exponential backoff)
      await new Promise(resolve => setTimeout(resolve, Math.pow(2, retries) * 1000));
    }
  }
  
  return false;
}

export function startUploadQueue() {
  if (running) return;
  running = true;

  const { UPLOAD_POLL_MS: POLL_MS } = getConfig();
  fs.mkdirSync(EVENTS_DIR, { recursive: true });

  console.log(
    `☁️ Upload queue started: polling every ${POLL_MS}ms -> ${getCloudUploadUrl()}`
  );

  setInterval(async () => {
    // Only process 1 file per tick to keep it simple & safe
    const files = listEventFiles();
    if (files.length === 0) return;

    const name = files[0];
    const full = path.join(EVENTS_DIR, name);

    const locked = `${full}.uploading`;

    try {
      // lock it so we don't double upload
      fs.renameSync(full, locked);

      console.log("⬆️ Uploading:", path.basename(locked));

      const success = await uploadFile(locked);

      if (success) {
        console.log("✅ Uploaded");
        // delete local after success
        fs.unlinkSync(locked);
        console.log("🧹 Deleted local clip:", path.basename(locked));
      } else {
        throw new Error("Upload failed");
      }
    } catch (err) {
      console.error("❌ Upload error:", err.message);

      // unlock back so it can retry next tick
      try {
        if (fs.existsSync(locked)) {
          fs.renameSync(locked, full);
        }
      } catch (e) {
        console.error("⚠️ Failed to unlock file:", e.message);
      }
    }
  }, getConfig().UPLOAD_POLL_MS);
}
