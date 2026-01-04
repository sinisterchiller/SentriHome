import fs from "fs";
import path from "path";

const QUEUE_FILE = path.resolve("queue.json");

function loadQueue() {
  if (!fs.existsSync(QUEUE_FILE)) return [];
  return JSON.parse(fs.readFileSync(QUEUE_FILE, "utf-8"));
}

function saveQueue(queue) {
  fs.writeFileSync(QUEUE_FILE, JSON.stringify(queue, null, 2));
}

export function enqueueEvent({ videoPath, thumbnailPath }) {
  const queue = loadQueue();

  queue.push({
    id: Date.now().toString(),
    videoPath,
    thumbnailPath,
    status: "pending",
    attempts: 0,
    lastError: null,
    createdAt: new Date().toISOString(),
  });

  saveQueue(queue);
}

export function getNextPending() {
  const queue = loadQueue();
  return queue.find(e => e.status === "pending");
}

export function updateEvent(id, updates) {
  const queue = loadQueue();
  const index = queue.findIndex(e => e.id === id);
  if (index === -1) return;

  queue[index] = { ...queue[index], ...updates };
  saveQueue(queue);
}

export function getQueue() {
  return loadQueue();
}
