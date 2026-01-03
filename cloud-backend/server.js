import express from "express";
import { startOAuth, oauthCallback } from "./googleAuth.js";

const app = express();

app.get("/auth/google", startOAuth);
app.get("/oauth2callback", oauthCallback);

app.get("/status", (_req, res) => {
  res.send("Cloud backend running");
});

app.listen(5000, () => {
  console.log("☁️ Cloud backend on http://localhost:5000");
});