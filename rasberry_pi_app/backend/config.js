import fs from "fs";
import path from "path";

const CONFIG_PATH = path.resolve(process.cwd(), "config.json");

const DEFAULTS = {
  CLOUD_BASE_URL: "http://localhost:3001",
  CLOUD_UPLOAD_ENDPOINT: "/api/events/upload",
  DEVICE_ID: "pi-1",
  UPLOAD_POLL_MS: 8000,
  UPLOAD_INTERVAL_MS: 30_000,
  MAX_ATTEMPTS: 5,
  DELETE_AFTER_UPLOAD: true,
  UDP_PORT: 5005,
};

/**
 * Load config: config.json (from setup page) > process.env > defaults.
 * Re-reads config.json on each call so POST /setup takes effect without restart.
 */
export function getConfig() {
  const fromEnv = {};
  if (process.env.CLOUD_BASE_URL) fromEnv.CLOUD_BASE_URL = process.env.CLOUD_BASE_URL;
  if (process.env.CLOUD_UPLOAD_ENDPOINT) fromEnv.CLOUD_UPLOAD_ENDPOINT = process.env.CLOUD_UPLOAD_ENDPOINT;
  if (process.env.DEVICE_ID) fromEnv.DEVICE_ID = process.env.DEVICE_ID;
  if (process.env.UPLOAD_POLL_MS != null) fromEnv.UPLOAD_POLL_MS = Number(process.env.UPLOAD_POLL_MS);
  if (process.env.UPLOAD_INTERVAL_MS != null) fromEnv.UPLOAD_INTERVAL_MS = Number(process.env.UPLOAD_INTERVAL_MS);
  if (process.env.MAX_ATTEMPTS != null) fromEnv.MAX_ATTEMPTS = Number(process.env.MAX_ATTEMPTS);
  if (process.env.DELETE_AFTER_UPLOAD != null) fromEnv.DELETE_AFTER_UPLOAD = process.env.DELETE_AFTER_UPLOAD !== "false";
  if (process.env.UDP_PORT != null) fromEnv.UDP_PORT = Number(process.env.UDP_PORT);

  let fromFile = {};
  if (fs.existsSync(CONFIG_PATH)) {
    try {
      fromFile = JSON.parse(fs.readFileSync(CONFIG_PATH, "utf-8"));
    } catch (e) {
      console.warn("Could not parse config.json:", e.message);
    }
  }

  return {
    ...DEFAULTS,
    ...fromEnv,
    ...fromFile,
  };
}

/** Full cloud upload URL (base + endpoint). */
export function getCloudUploadUrl() {
  const c = getConfig();
  const base = (c.CLOUD_BASE_URL || "").replace(/\/$/, "");
  const endpoint = (c.CLOUD_UPLOAD_ENDPOINT || "").replace(/^\//, "");
  return `${base}/${endpoint}`;
}

/** True if config has been customized (config.json exists or relevant env set). */
export function hasConfigured() {
  if (fs.existsSync(CONFIG_PATH)) return true;
  return !!(
    process.env.CLOUD_BASE_URL ||
    process.env.CLOUD_UPLOAD_ENDPOINT ||
    process.env.DEVICE_ID
  );
}

/**
 * Save config from setup page. Writes config.json.
 */
export function saveConfig(obj) {
  const safe = {
    CLOUD_BASE_URL: String(obj.CLOUD_BASE_URL ?? "").trim() || undefined,
    CLOUD_UPLOAD_ENDPOINT: String(obj.CLOUD_UPLOAD_ENDPOINT ?? "").trim() || undefined,
    DEVICE_ID: String(obj.DEVICE_ID ?? "").trim() || undefined,
    UPLOAD_POLL_MS: obj.UPLOAD_POLL_MS != null ? Number(obj.UPLOAD_POLL_MS) : undefined,
    UPLOAD_INTERVAL_MS: obj.UPLOAD_INTERVAL_MS != null ? Number(obj.UPLOAD_INTERVAL_MS) : undefined,
    MAX_ATTEMPTS: obj.MAX_ATTEMPTS != null ? Number(obj.MAX_ATTEMPTS) : undefined,
    DELETE_AFTER_UPLOAD: obj.DELETE_AFTER_UPLOAD !== false,
    UDP_PORT: obj.UDP_PORT != null ? Number(obj.UDP_PORT) : undefined,
  };
  fs.writeFileSync(CONFIG_PATH, JSON.stringify(safe, null, 2), "utf-8");
}
