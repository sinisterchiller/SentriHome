import fs from "fs";
import path from "path";
import { exec } from "child_process";

const BUFFER_DIR = "buffer";
const EVENTS_DIR = "events";
const SEGMENT_DURATION = 2;

let extracting = false;

export function extractClip({ beforeSeconds = 5, afterSeconds = 5 }) {
  if (extracting) return;
  extracting = true;

  try {
    if (!fs.existsSync(BUFFER_DIR)) return;

    fs.mkdirSync(EVENTS_DIR, { recursive: true });

    const files = fs.readdirSync(BUFFER_DIR)
      .filter(f => f.endsWith(".ts"))
      .sort();

    const endIndex = files.length - 2;
    if (endIndex <= 0) return;

    const beforeSegs = Math.ceil(beforeSeconds / SEGMENT_DURATION);
    const afterSegs = Math.ceil(afterSeconds / SEGMENT_DURATION);

    const startIndex = Math.max(0, endIndex - beforeSegs - afterSegs);
    const selected = files.slice(startIndex, endIndex + 1);

    const listFile = path.join(EVENTS_DIR, "files.txt");
    const output = path.join(EVENTS_DIR, `event_${Date.now()}.mp4`);

    fs.writeFileSync(
      listFile,
      selected.map(f => `file '${path.resolve(BUFFER_DIR, f)}'`).join("\n")
    );

    exec(`ffmpeg -y -f concat -safe 0 -i "${listFile}" -c copy "${output}"`, () => {
      extracting = false;
      console.log("🎥 Event saved:", output);
    });
  } catch {
    extracting = false;
  }
}
