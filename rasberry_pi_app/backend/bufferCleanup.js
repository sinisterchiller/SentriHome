import fs from "fs";
import path from "path";

const BUFFER_DIR = "buffer";
const MAX_AGE_SECONDS = 120;

export function startBufferCleanup() {
  setInterval(() => {
    if (!fs.existsSync(BUFFER_DIR)) return;

    const now = Date.now();
    for (const file of fs.readdirSync(BUFFER_DIR)) {
      if (!file.endsWith(".ts")) continue;

      const full = path.join(BUFFER_DIR, file);
      const age = (now - fs.statSync(full).mtimeMs) / 1000;

      if (age > MAX_AGE_SECONDS) {
        fs.unlinkSync(full);
      }
    }
  }, 5000);
}
