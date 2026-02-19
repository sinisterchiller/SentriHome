import fs from "fs";
import path from "path";
import axios from "axios";
import FormData from "form-data";

import { loadPendingEvents, updateEvent } from "./eventStore.js";
import { getConfig, getCloudUploadUrl } from "./config.js";

const EVENTS_DIR = "events";

export function startUploader() {
  console.log("☁️ Pi uploader started");

  setInterval(async () => {
    const { UPLOAD_INTERVAL_MS, MAX_ATTEMPTS, DELETE_AFTER_UPLOAD, DEVICE_ID } = getConfig();
    const CLOUD_URL = getCloudUploadUrl();
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
        form.append("deviceId", DEVICE_ID);
        form.append("cameraId", data.cameraId || DEVICE_ID);
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
  }, getConfig().UPLOAD_INTERVAL_MS);
}
