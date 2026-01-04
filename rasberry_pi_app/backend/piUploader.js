import fs from "fs";
import path from "path";
import axios from "axios";
import FormData from "form-data";

import { loadPendingEvents, updateEvent } from "./eventStore.js";

const CLOUD_URL = "http://localhost:3001/api/events/upload";
const EVENTS_DIR = "events";
const UPLOAD_INTERVAL_MS = 30_000;
const MAX_ATTEMPTS = 5;
const DELETE_AFTER_UPLOAD = true;

export function startUploader() {
  console.log("☁️ Pi uploader started");

  setInterval(async () => {
    const pending = loadPendingEvents();

    for (const event of pending) {
      const { data, path: recordPath } = event;
      const videoPath = path.join(EVENTS_DIR, data.file);

      if (!fs.existsSync(videoPath)) {
        data.uploaded = true;
        updateEvent(recordPath, data);
        continue;
      }

      if (data.attempts >= MAX_ATTEMPTS) {
        console.warn("⚠️ Max upload attempts reached:", data.file);
        continue;
      }

      try {
        data.attempts += 1;
        updateEvent(recordPath, data);

        const form = new FormData();
        form.append("file", fs.createReadStream(videoPath));
        form.append("cameraId", data.cameraId);
        form.append("timestamp", data.timestamp);

        await axios.post(CLOUD_URL, form, {
          headers: form.getHeaders(),
          maxBodyLength: Infinity,
        });

        console.log("☁️ Uploaded:", data.file);

        data.uploaded = true;
        updateEvent(recordPath, data);

        if (DELETE_AFTER_UPLOAD) {
          fs.unlinkSync(videoPath);
        }
      } catch (err) {
        console.error("❌ Upload failed:", data.file, err.message);
      }
    }
  }, UPLOAD_INTERVAL_MS);
}
