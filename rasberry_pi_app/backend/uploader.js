import fs from "fs";
import axios from "axios";
import FormData from "form-data";
import {
  getNextPending,
  updateEvent,
} from "./queueManager.js";

const CLOUD_UPLOAD_URL = "http://localhost:3001/api/events/upload";

export function startUploader() {
  setInterval(async () => {
    const event = getNextPending();
    if (!event) return;

    try {
      updateEvent(event.id, { status: "uploading" });

      // Upload thumbnail first
      if (event.thumbnailPath && fs.existsSync(event.thumbnailPath)) {
        const thumbForm = new FormData();
        thumbForm.append("file", fs.createReadStream(event.thumbnailPath));
        thumbForm.append("type", "thumbnail");

        await axios.post(CLOUD_UPLOAD_URL, thumbForm, {
          headers: thumbForm.getHeaders(),
        });
      }

      // Upload video
      if (event.videoPath && fs.existsSync(event.videoPath)) {
        const videoForm = new FormData();
        videoForm.append("file", fs.createReadStream(event.videoPath));
        videoForm.append("type", "video");

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
