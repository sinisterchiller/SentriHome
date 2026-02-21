import chokidar from "chokidar";
import FormData from "form-data";
import fetch from "node-fetch";
import fs from "fs";
import { getConfig } from "./config.js";

const HLS_DIR = "hls";
let segSeq = 0;
let watcher = null;

export function startSegmentWatcher() {
  if (watcher) return;

  watcher = chokidar.watch(`${HLS_DIR}/*.ts`, { ignoreInitial: true });

  watcher.on("add", async (filePath) => {
    // Brief delay so ffmpeg finishes writing the segment
    await new Promise((r) => setTimeout(r, 500));

    const { CLOUD_BASE_URL, DEVICE_ID } = getConfig();
    if (!CLOUD_BASE_URL) return;

    try {
      const form = new FormData();
      form.append("segment", fs.createReadStream(filePath));
      form.append("deviceId", DEVICE_ID || "pi-1");
      form.append("seq", String(segSeq++));

      const url = `${CLOUD_BASE_URL.replace(/\/$/, "")}/api/stream/segment`;

      const res = await fetch(url, {
        method: "POST",
        body: form,
        headers: form.getHeaders(),
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      console.log(`Uploaded segment: ${filePath}`);
    } catch (err) {
      console.error(`Segment upload failed: ${err.message}`);
    }
  });

  console.log("Segment watcher started on hls/*.ts");
}

export function stopSegmentWatcher() {
  if (watcher) {
    watcher.close();
    watcher = null;
    segSeq = 0;
  }
}
