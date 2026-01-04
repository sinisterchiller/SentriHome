import { spawn } from "child_process";
import fs from "fs";
import path from "path";

// Use global object to ensure FFmpeg process persists across imports
global.ffmpegState = {
  process: null,
  lastFfmpegError: null,
  lastExit: null,
};

function cleanDirectory(dir) {
  if (!fs.existsSync(dir)) return;

  try {
    for (const file of fs.readdirSync(dir)) {
      const filePath = path.join(dir, file);
      fs.unlinkSync(filePath);
    }
    console.log(`🧹 Cleaned directory: ${dir}`);
  } catch (err) {
    console.error(`Error cleaning ${dir}:`, err.message);
  }
}

export function startStream(inputArgs) {
  if (global.ffmpegState.process) return false;

  // Clean directories BEFORE starting FFmpeg to avoid conflicts
  cleanDirectory("buffer");
  cleanDirectory("hls");

  fs.mkdirSync("buffer", { recursive: true });
  fs.mkdirSync("hls", { recursive: true });

  console.log("🎥 Starting FFmpeg with args:", inputArgs);

  const args = [
    "-y",
    ...inputArgs,

    "-c:v", "libx264",
    "-preset", "ultrafast",
    "-pix_fmt", "yuv420p",
    "-r", "30",

    "-map", "0:v",
    "-f", "tee",
    `[f=segment:segment_time=2:reset_timestamps=1:segment_format=mpegts]buffer/chunk_%05d.ts|` +
    `[f=hls:hls_time=2:hls_list_size=6:hls_flags=delete_segments+append_list:hls_segment_filename=hls/seg_%05d.ts]hls/stream.m3u8`
  ];

  global.ffmpegState.process = spawn("ffmpeg", args);

  console.log("🎥 FFmpeg spawned with PID:", global.ffmpegState.process.pid);
  console.log("🎥 FFmpeg args:", args.join(" "));

  global.ffmpegState.process.stderr.on("data", data => {
    global.ffmpegState.lastFfmpegError = data.toString();
  });

  global.ffmpegState.process.on("close", (code, signal) => {
    console.log(`🎥 FFmpeg closed with code: ${code}, signal: ${signal}`);
    global.ffmpegState.lastExit = { code, signal };
    global.ffmpegState.process = null;
    console.log("🔍 Updated global state - process:", global.ffmpegState.process);
  });

  return true;
}

export function stopStream() {
  if (!global.ffmpegState.process) return false;

  console.log("🛑 Stopping FFmpeg process...");
  global.ffmpegState.process.kill("SIGTERM");
  
  // Wait for process to fully exit before cleaning
  return new Promise((resolve) => {
    const checkExit = setInterval(() => {
      if (!global.ffmpegState.process) {
        clearInterval(checkExit);
        
        // Clean directories AFTER FFmpeg has fully stopped
        cleanDirectory("hls");
        cleanDirectory("buffer");
        
        console.log("✅ Stream stopped and directories cleaned");
        resolve(true);
      }
    }, 100);
  });
}

export function getStreamState() {
  return {
    running: !!global.ffmpegState.process,
    lastFfmpegError: global.ffmpegState.lastFfmpegError,
    lastExit: global.ffmpegState.lastExit,
  };
}

export function forceUpdateState() {
  console.log("🔍 Manual state check - process:", !!global.ffmpegState.process);
  console.log("🔍 Manual state check - PID:", global.ffmpegState.process?.pid);
  return getStreamState();
}
