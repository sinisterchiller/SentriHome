import fs from "fs";
import path from "path";

const BUFFER_DIR = "buffer";
const MAX_AGE_SECONDS = 120;

export function startBufferCleanup() {
  setInterval(() => {
    if (!fs.existsSync(BUFFER_DIR)) return;

    const now = Date.now();
    const files = fs.readdirSync(BUFFER_DIR);
    
    for (const file of files) {
      if (!file.endsWith(".ts")) continue;

      const full = path.join(BUFFER_DIR, file);
      try {
        const age = (now - fs.statSync(full).mtimeMs) / 1000;

        if (age > MAX_AGE_SECONDS) {
          fs.unlinkSync(full);
          console.log(`🗑️ Cleaned old buffer segment: ${file}`);
        }
      } catch (err) {
        // File might be deleted by another process (FFmpeg)
        if (err.code !== 'ENOENT') {
          console.error(`Error cleaning ${file}:`, err.message);
        }
      }
    }
  }, 5000);
}
