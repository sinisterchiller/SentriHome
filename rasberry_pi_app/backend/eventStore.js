import fs from "fs";
import path from "path";

const EVENTS_DIR = "events";

export function createEventRecord(filename) {
  fs.mkdirSync(EVENTS_DIR, { recursive: true });

  const record = {
    file: filename,
    timestamp: new Date().toISOString(),
    cameraId: "cam_001",
    uploaded: false,
    attempts: 0,
  };

  const recordPath = path.join(
    EVENTS_DIR,
    filename.replace(".mp4", ".json")
  );

  fs.writeFileSync(recordPath, JSON.stringify(record, null, 2));
}

export function loadPendingEvents() {
  if (!fs.existsSync(EVENTS_DIR)) return [];

  return fs
    .readdirSync(EVENTS_DIR)
    .filter((f) => f.endsWith(".json"))
    .map((f) => {
      const fullPath = path.join(EVENTS_DIR, f);
      const data = JSON.parse(fs.readFileSync(fullPath, "utf8"));
      return { path: fullPath, data };
    })
    .filter((e) => e.data.uploaded === false);
}

export function updateEvent(recordPath, data) {
  fs.writeFileSync(recordPath, JSON.stringify(data, null, 2));
}
