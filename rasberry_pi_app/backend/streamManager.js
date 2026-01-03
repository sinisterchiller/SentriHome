import { spawn } from "child_process";
import fs from "fs";
import path from "path";
let ffmpegProcess = null;
let lastFfmpegError = null;
let lastExit = null;

function cleanDirectory(dir) {
  if (!fs.existsSync(dir)) return;

  for (const file of fs.readdirSync(dir)) {
    fs.unlinkSync(path.join(dir, file));
  }
}

export function startStream(inputArgs) {
  if (ffmpegProcess) return false;

  fs.mkdirSync("buffer", { recursive: true });
  fs.mkdirSync("hls", { recursive: true });

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

  ffmpegProcess = spawn("ffmpeg", args);

  ffmpegProcess.stderr.on("data", data => {
    lastFfmpegError = data.toString();
  });

  ffmpegProcess.on("close", (code, signal) => {
    lastExit = { code, signal };
    ffmpegProcess = null;
  });

  return true;
}


export function stopStream() {
  if (!ffmpegProcess) return false;

  ffmpegProcess.kill("SIGTERM");
  ffmpegProcess = null;

  cleanDirectory("hls");
  cleanDirectory("buffer");

  return true;
}


export function getStreamState() {
  return {
    running: !!ffmpegProcess,
    lastFfmpegError,
    lastExit,
  };
}
