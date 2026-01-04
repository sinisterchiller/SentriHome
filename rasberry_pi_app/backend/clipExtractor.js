import fs from "fs";
import path from "path";
import { exec } from "child_process";
import { enqueueEvent } from "./queueManager.js";

const BUFFER_DIR = "buffer";
const EVENTS_DIR = "events";
const SEGMENT_DURATION = 2; // must match your segment_time in streamManager

let extracting = false;

export function extractClip({ beforeSeconds = 5, afterSeconds = 5 } = {}) {
  if (extracting) return Promise.resolve(null);
  extracting = true;

  return new Promise((resolve) => {
    try {
      if (!fs.existsSync(BUFFER_DIR)) {
        extracting = false;
        return resolve(null);
      }

      fs.mkdirSync(EVENTS_DIR, { recursive: true });

      const files = fs
        .readdirSync(BUFFER_DIR)
        .filter((f) => f.endsWith(".ts"))
        .sort();

      // skip newest segment (may still be writing)
      const endIndex = files.length - 2;
      if (endIndex <= 0) {
        extracting = false;
        return resolve(null);
      }

      const beforeSegs = Math.ceil(beforeSeconds / SEGMENT_DURATION);
      const afterSegs = Math.ceil(afterSeconds / SEGMENT_DURATION);
      const startIndex = Math.max(0, endIndex - beforeSegs - afterSegs);

      const selected = files.slice(startIndex, endIndex + 1);
      if (selected.length === 0) {
        extracting = false;
        return resolve(null);
      }

      const listFile = path.resolve(EVENTS_DIR, "files.txt");
      const output = path.resolve(EVENTS_DIR, `event_${Date.now()}.mp4`);

      fs.writeFileSync(
        listFile,
        selected.map((f) => `file '${path.resolve(BUFFER_DIR, f)}'`).join("\n")
      );

      const cmd =
        `ffmpeg -y -f concat -safe 0 -i "${listFile}" ` +
        `-c copy -movflags +faststart "${output}"`;

      exec(cmd, (err, _stdout, stderr) => {
        extracting = false;

        if (err) {
          console.error("❌ Clip extraction failed:", err.message);
          if (stderr) console.error("ffmpeg stderr:", stderr.toString().trim());
          return resolve(null);
        }

        if (!fs.existsSync(output)) {
          console.error("❌ Clip extraction finished but file not found:", output);
          return resolve(null);
        }

        console.log("🎥 Event saved:", output);
        
        // Generate thumbnail
        const thumbnailPath = output.replace(".mp4", ".jpg");
        const thumbCmd = `ffmpeg -y -i "${output}" -ss 00:00:01 -vframes 1 "${thumbnailPath}"`;
        
        exec(thumbCmd, (thumbErr) => {
          if (thumbErr) {
            console.error("⚠️ Thumbnail generation failed:", thumbErr.message);
          } else {
            console.log("🖼️ Thumbnail created:", thumbnailPath);
          }
          
          enqueueEvent({
            videoPath: output,
            thumbnailPath,
          });
        });
        
        resolve(output);
      });
    } catch (e) {
      extracting = false;
      console.error("❌ Clip extraction error:", e.message);
      resolve(null);
    }
  });
}
