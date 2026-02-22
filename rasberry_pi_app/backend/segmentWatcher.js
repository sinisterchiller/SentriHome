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

  // Ensure directory exists before watching
  fs.mkdirSync(HLS_DIR, { recursive: true });

  watcher = chokidar.watch(HLS_DIR, {
    ignoreInitial: true,
    usePolling: true,
    interval: 500,
  });

  watcher.on("ready", () => {
    console.log("Segment watcher ready and watching hls/");
  });

  watcher.on("error", (err) => {
    console.error("Segment watcher error:", err.message);
  });

  watcher.on("add", async (filePath) => {
    if (!filePath.endsWith(".ts")) return;
    const { CLOUD_BASE_URL, DEVICE_ID } = getConfig();
    if (!CLOUD_BASE_URL) {
      console.warn("No CLOUD_BASE_URL configured, skipping segment upload");
      return;
    }

    console.log(`New segment detected: ${filePath}`);

    try {
      const form = new FormData();
      form.append("segment", fs.createReadStream(filePath));
      form.append("deviceId", DEVICE_ID || "pi-1");
      form.append("seq", String(segSeq++));

      const url = `${CLOUD_BASE_URL.replace(/\/$/, "")}/api/stream/segment`;
      console.log(`Uploading to: ${url}`);

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

  console.log("Segment watcher starting on hls/ (polling) ...");
}

export function stopSegmentWatcher() {
  if (watcher) {
    watcher.close();
    watcher = null;
    segSeq = 0;
  }
}
