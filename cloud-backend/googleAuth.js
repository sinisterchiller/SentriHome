import fs from "fs";
import { google } from "googleapis";
import open from "open";

const CREDENTIALS_PATH = "./google-oauth.json";
const TOKEN_PATH = "./tokens.json";
const SCOPES = ["https://www.googleapis.com/auth/drive.file"];

function createOAuthClient() {
  const creds = JSON.parse(fs.readFileSync(CREDENTIALS_PATH));

  const config = creds.installed || creds.web;

  if (!config) {
    throw new Error(
      "Invalid OAuth file: expected 'installed' or 'web' key"
    );
  }

  const { client_id, client_secret, redirect_uris } = config;

  if (!redirect_uris || redirect_uris.length === 0) {
    throw new Error("OAuth config missing redirect_uris");
  }

  return new google.auth.OAuth2(
    client_id,
    client_secret,
    redirect_uris[0]
  );
}

export function startOAuth(req, res) {
  const client = createOAuthClient();

  const url = client.generateAuthUrl({
    access_type: "offline",
    scope: SCOPES,
    prompt: "consent"
  });

  open(url);
  res.send("OAuth started. Check your browser.");
}

export async function oauthCallback(req, res) {
  const code = req.query.code;
  if (!code) return res.status(400).send("Missing code");

  const client = createOAuthClient();
  const { tokens } = await client.getToken(code);

  fs.writeFileSync(TOKEN_PATH, JSON.stringify(tokens, null, 2));

  res.send("✅ Google Drive connected. You may close this tab.");
}