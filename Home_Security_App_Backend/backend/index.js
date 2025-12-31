import express from "express";
import { startStream, stopStream } from "./streamManager.js";

const app = express();
app.use(express.json());
app.use("/hls", express.static("hls", { setHeaders: r => r.set("Cache-Control", "no-cache") }));

app.post("/start", (req, res) => {
  const { type, value } = req.body;

  let input;
  if (type === "webcam") {
    input = ["-f", "avfoundation", "-framerate", "30", "-i", "0"];
  } else if (type === "rtsp") {
    input = ["-rtsp_transport", "tcp", "-i", value];
  } else if (type === "file") {
    input = ["-re", "-i", value];
  } else {
    return res.status(400).send("Invalid input");
  }

  startStream(input);
  res.send("Stream started");
});

app.post("/stop", (_req, res) => {
  stopStream();
  res.send("Stream stopped");
});

app.listen(4000, () =>
  console.log("Backend on http://localhost:4000")
);
