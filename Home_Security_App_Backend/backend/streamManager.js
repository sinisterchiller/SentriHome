import { spawn } from "child_process";

let ffmpegProcess = null;

export function startStream(input) {
  if (ffmpegProcess) return;

  const args = [
    "-y",
    ...input,                     // dynamic input
    "-c:v", "libx264",
    "-preset", "veryfast",
    "-tune", "zerolatency",
    "-f", "hls",
    "-hls_time", "1",
    "-hls_list_size", "6",
    "-hls_flags", "delete_segments+append_list",
    "-hls_segment_filename", "hls/seg_%05d.ts",
    "hls/stream.m3u8"
  ];

  ffmpegProcess = spawn("ffmpeg", args);

  ffmpegProcess.stderr.on("data", () => {});
  ffmpegProcess.on("close", () => {
    ffmpegProcess = null;
  });
}

export function stopStream() {
  if (ffmpegProcess) {
    ffmpegProcess.kill("SIGTERM");
    ffmpegProcess = null;
  }
}
