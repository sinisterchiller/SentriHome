import fs from "fs";
import { google } from "googleapis";


const SCOPES = ["https://www.googleapis.com/auth/drive.file"];
const TOKEN_PATH = "token.json";

export function createOAuthClient() {
  const creds = JSON.parse(fs.readFileSync("credentials.json", "utf8"));
  const config = creds.web || creds.installed;

  if (!config) {
    throw new Error("Invalid OAuth credentials file");
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

export function getAuthUrl(oauth2Client) {
  return oauth2Client.generateAuthUrl({
    access_type: "offline",
    scope: SCOPES,
    prompt: "consent",
  });
}

export async function handleOAuthCallback(oauth2Client, code) {
  const { tokens } = await oauth2Client.getToken(code);
  oauth2Client.setCredentials(tokens);

  fs.writeFileSync(TOKEN_PATH, JSON.stringify(tokens, null, 2));
}

export function getAuthorizedClient() {
  if (!fs.existsSync(TOKEN_PATH)) {
    throw new Error("User not authenticated with Google");
  }

  const oauth2Client = createOAuthClient();
  const tokens = JSON.parse(fs.readFileSync(TOKEN_PATH, "utf8"));
  oauth2Client.setCredentials(tokens);

  return oauth2Client;
}
