import fs from "fs";
import axios from "axios";
import FormData from "form-data";
import {
  getNextPending,
  updateEvent,
} from "./queueManager.js";
import { getCloudUploadUrl, getConfig } from "./config.js";

export function startUploader() {
  setInterval(async () => {
    const CLOUD_UPLOAD_URL = getCloudUploadUrl();
    const event = getNextPending();
    if (!event) return;

    try {
      updateEvent(event.id, { status: "uploading" });

      const deviceId = getConfig().DEVICE_ID;

      // Upload thumbnail first
      if (event.thumbnailPath && fs.existsSync(event.thumbnailPath)) {
        const thumbForm = new FormData();
        thumbForm.append("file", fs.createReadStream(event.thumbnailPath));
        thumbForm.append("type", "thumbnail");
        thumbForm.append("deviceId", deviceId);

        await axios.post(CLOUD_UPLOAD_URL, thumbForm, {
          headers: thumbForm.getHeaders(),
        });
      }

      // Upload video
      if (event.videoPath && fs.existsSync(event.videoPath)) {
        const videoForm = new FormData();
        videoForm.append("file", fs.createReadStream(event.videoPath));
        videoForm.append("type", "video");
        videoForm.append("deviceId", deviceId);

        await axios.post(CLOUD_UPLOAD_URL, videoForm, {
          headers: videoForm.getHeaders(),
        });
      }

      updateEvent(event.id, { status: "uploaded" });
      console.log("☁️ Uploaded event:", event.id);
    } catch (err) {
      updateEvent(event.id, {
        status: "pending",
        attempts: event.attempts + 1,
        lastError: err.message,
      });
      console.error("⚠️ Upload failed:", err.message);
    }
  }, 10_000);
}
