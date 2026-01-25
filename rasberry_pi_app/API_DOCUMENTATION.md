# Raspberry Pi App - API Documentation

## Overview

The Raspberry Pi App is a Node.js-based video surveillance system that captures video streams, detects motion events, and uploads footage to a cloud backend. It uses FFmpeg for video processing and provides a REST API for control and monitoring.

**Version:** 1.0.0  
**Default Port:** 4000  
**Authors:** Safir, Koushik, Neaz, Ishayat

---

## Table of Contents

1. [Architecture](#architecture)
2. [Environment Variables](#environment-variables)
3. [Installation & Setup](#installation--setup)
4. [API Endpoints](#api-endpoints)
5. [Background Services](#background-services)
6. [File Structure](#file-structure)
7. [Cloud Backend Connection](#cloud-backend-connection)
8. [UDP Motion Trigger](#udp-motion-trigger)

---

## Architecture

The Raspberry Pi App consists of several core components:

- **Express Server** - REST API for stream control and health monitoring
- **Stream Manager** - FFmpeg-based video streaming and recording
- **UDP Listener** - Receives motion detection triggers from ESP32 devices
- **Clip Extractor** - Creates video clips from buffer segments on motion events
- **Upload Queue Manager** - Manages pending uploads to cloud backend
- **Uploader** - Handles video and thumbnail uploads to cloud storage
- **Buffer Cleanup** - Maintains rolling buffer by removing old segments

### Data Flow

```
ESP32 Device → UDP (port 5005) → Motion Trigger
                                      ↓
Camera → FFmpeg → HLS Stream + Buffer Segments
                                      ↓
                              Motion Detected
                                      ↓
                         Extract Clip from Buffer
                                      ↓
                              Generate Thumbnail
                                      ↓
                            Add to Upload Queue
                                      ↓
                         Upload to Cloud Backend
```

---

## Environment Variables

### Required

None - the app works with default hardcoded values.

### Optional Configuration

You can modify these constants in the source files:

#### `piUploader.js`
- `CLOUD_URL` - Cloud backend upload endpoint (default: `http://localhost:3001/api/events/upload`)
- `UPLOAD_INTERVAL_MS` - Upload check interval (default: `30000` - 30 seconds)
- `MAX_ATTEMPTS` - Maximum upload retry attempts (default: `5`)
- `DELETE_AFTER_UPLOAD` - Delete local files after successful upload (default: `true`)

#### `uploader.js`
- `CLOUD_UPLOAD_URL` - Cloud backend upload endpoint (default: `http://localhost:3001/api/events/upload`)
- Upload interval: `10000` ms (10 seconds)

#### `udpListener.js`
- `UDP_PORT` - UDP listener port (default: `5005`)
- `TRIGGER_MESSAGE` - Motion trigger phrase (default: `"INTRUDER INTRUDER"`)
- `COOLDOWN_MS` - Motion trigger cooldown (default: `10000` - 10 seconds)

#### `bufferCleanup.js`
- `MAX_AGE_SECONDS` - Maximum buffer segment age (default: `120` seconds)
- Cleanup interval: `5000` ms (5 seconds)

#### `streamManager.js`
- FFmpeg segment duration: `2` seconds
- HLS list size: `6` segments

#### `index.js`
- Server port: `4000`

---

## Installation & Setup

### Prerequisites

- Node.js (v14 or higher)
- FFmpeg installed and available in PATH
- Camera or video source (webcam, RTSP stream, or video file)

### Installation Steps

1. Navigate to the backend directory:
   ```bash
   cd rasberry_pi_app/backend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Start the server:
   ```bash
   npm start
   ```

The server will start on `http://localhost:4000` and automatically initialize:
- Buffer cleanup service
- Upload queue manager
- UDP listener (port 5005)

---

## API Endpoints

### Health & Status

#### `GET /health`
Check the health status of the Raspberry Pi app.

**Response:**
```json
{
  "stream": {
    "running": true,
    "lastFfmpegError": null,
    "lastExit": null
  },
  "hls": {
    "playlist": true,
    "segmentCount": 6
  }
}
```

#### `GET /debug/state`
Get detailed debug information about the stream state.

**Response:**
```json
{
  "debug": true,
  "state": {
    "running": true,
    "lastFfmpegError": null,
    "lastExit": null
  },
  "timestamp": "2026-01-24T10:30:00.000Z"
}
```

---

### Stream Control

#### `POST /start`
Start a video stream from a camera source.

**Request Body:**
```json
{
  "type": "webcam|rtsp|file",
  "value": "optional_source_value"
}
```

**Input Types:**

1. **Webcam**
   ```json
   {
     "type": "webcam",
     "value": "/dev/video0"  // Linux: video device path, macOS: uses default
   }
   ```

2. **RTSP Stream**
   ```json
   {
     "type": "rtsp",
     "value": "rtsp://username:password@192.168.1.100:554/stream"
   }
   ```

3. **Video File**
   ```json
   {
     "type": "file",
     "value": "/path/to/video.mp4"
   }
   ```

**Success Response (200):**
```json
{
  "status": "ok",
  "message": "Stream started"
}
```

**Error Responses:**
- `400` - Invalid input type or missing required value
- `409` - Stream already running

#### `POST /stop`
Stop the currently running video stream.

**Success Response (200):**
```json
{
  "status": "ok",
  "message": "Stream stopped"
}
```

**Error Responses:**
- `409` - No active stream
- `500` - Failed to stop stream

---

### Motion Events

#### `POST /motion`
Manually trigger a motion event to extract a video clip.

**Success Response (200):**
```json
{
  "status": "ok",
  "saved": true,
  "file": "/path/to/events/event_1706094600000.mp4"
}
```

**Notes:**
- Extracts 5 seconds before and 5 seconds after the trigger
- Automatically generates a thumbnail
- Adds the event to the upload queue

---

### Data Management

#### `DELETE /clear-all`
Clear all local data including events, buffer, HLS files, and upload queue.

**Success Response (200):**
```json
{
  "status": "ok",
  "message": "All local data cleared"
}
```

**What Gets Deleted:**
- All files in `events/` directory
- All files in `buffer/` directory
- All files in `hls/` directory
- `queue.json` file

---

### HLS Streaming

#### `GET /hls/stream.m3u8`
Access the HLS playlist for live streaming.

**Response:** M3U8 playlist file

#### `GET /hls/seg_*.ts`
Access HLS video segments.

**Response:** MPEG-TS video segment

**Notes:**
- Cache-Control is set to `no-cache` for real-time streaming
- Segments are automatically deleted as per HLS configuration

---

## Background Services

### 1. Buffer Cleanup Service

**Purpose:** Maintains a rolling buffer by deleting old video segments.

**Interval:** Every 5 seconds  
**Max Age:** 120 seconds (2 minutes)  
**Directory:** `buffer/`

**Behavior:**
- Scans `buffer/` directory for `.ts` files
- Deletes segments older than 120 seconds
- Runs continuously after server start

---

### 2. Upload Queue Manager

**Purpose:** Manages pending video uploads to the cloud backend.

**Interval:** Every 10 seconds  
**Storage:** `queue.json`

**Queue Item Structure:**
```json
{
  "id": "1706094600000",
  "videoPath": "events/event_1706094600000.mp4",
  "thumbnailPath": "events/event_1706094600000.jpg",
  "status": "pending|uploading|uploaded",
  "attempts": 0,
  "lastError": null,
  "createdAt": "2026-01-24T10:30:00.000Z"
}
```

---

### 3. Uploader Service

**Purpose:** Uploads videos and thumbnails to the cloud backend.

**Interval:** Every 10 seconds  
**Endpoint:** `http://localhost:3001/api/events/upload`

**Upload Process:**
1. Check for pending events in queue
2. Upload thumbnail (if exists)
3. Upload video
4. Update event status to "uploaded"
5. Retry on failure (up to configured attempts)

**Upload Format:**
```
POST http://localhost:3001/api/events/upload
Content-Type: multipart/form-data

file: <binary_data>
type: "video" | "thumbnail"
```

---

### 4. UDP Listener Service

**Purpose:** Receives motion detection triggers from ESP32 devices.

**Port:** 5005 (UDP)  
**Trigger Message:** `"INTRUDER INTRUDER"`  
**Cooldown:** 10 seconds

**Behavior:**
- Listens for UDP messages on port 5005
- Validates exact match of trigger message
- Enforces 10-second cooldown between triggers
- Automatically calls `extractClip()` on valid triggers

**Testing the UDP Listener:**
```bash
# Send a test UDP message
echo "INTRUDER INTRUDER" | nc -u localhost 5005
```

---

## File Structure

### Directories

```
rasberry_pi_app/backend/
├── buffer/              # Rolling buffer of video segments (*.ts)
├── events/              # Extracted motion event clips (*.mp4, *.jpg, *.json)
├── hls/                 # HLS streaming files (stream.m3u8, seg_*.ts)
└── queue.json           # Upload queue state
```

### Generated Files

**Buffer Segments:**
- Format: `chunk_00001.ts`, `chunk_00002.ts`, etc.
- Duration: 2 seconds each
- Auto-deleted after 120 seconds

**Event Files:**
- Video: `event_1706094600000.mp4`
- Thumbnail: `event_1706094600000.jpg`
- Metadata: `event_1706094600000.json`

**HLS Files:**
- Playlist: `stream.m3u8`
- Segments: `seg_00001.ts`, `seg_00002.ts`, etc.
- List size: 6 segments (12 seconds of video)

---

## Cloud Backend Connection

### Overview

The Raspberry Pi app uploads motion events to a cloud backend service for long-term storage and remote access.

### Cloud Backend Location

**Default URL:** `http://localhost:3001`

The cloud backend is located in the project at:
```
Home-Security/cloud-backend/
```

### Finding the Cloud Backend

The cloud backend URL is hardcoded in two files:

1. **`piUploader.js` (line 8):**
   ```javascript
   const CLOUD_URL = "http://localhost:3001/api/events/upload";
   ```

2. **`uploader.js` (line 9):**
   ```javascript
   const CLOUD_UPLOAD_URL = "http://localhost:3001/api/events/upload";
   ```

### Changing the Cloud Backend URL

To point to a different cloud backend:

1. **For remote deployment**, update both files with your cloud server URL:
   ```javascript
   const CLOUD_URL = "https://your-cloud-server.com/api/events/upload";
   ```

2. **Using environment variables** (recommended for production):
   
   Modify the files to use environment variables:
   ```javascript
   const CLOUD_URL = process.env.CLOUD_BACKEND_URL || "http://localhost:3001/api/events/upload";
   ```
   
   Then set the environment variable before starting:
   ```bash
   export CLOUD_BACKEND_URL="https://your-cloud-server.com/api/events/upload"
   npm start
   ```

### Cloud Backend API

The cloud backend expects uploads at:

**Endpoint:** `POST /api/events/upload`

**Request Format:**
```
Content-Type: multipart/form-data

file: <binary_file>
type: "video" | "thumbnail"
deviceId: "cam_001" (optional)
timestamp: "2026-01-24T10:30:00.000Z" (optional)
```

**Response:**
```json
{
  "status": "ok",
  "event": {
    "_id": "65b1234567890abcdef",
    "deviceId": "cam_001",
    "driveFileId": "1234567890abcdef",
    "driveLink": "https://drive.google.com/file/d/...",
    "createdAt": "2026-01-24T10:30:00.000Z"
  }
}
```

### Cloud Backend Features

- **MongoDB** - Stores event metadata
- **Google Drive** - Stores video files
- **Authentication** - OAuth 2.0 for Google Drive access
- **CORS** - Configured for frontend access

### Starting the Cloud Backend

```bash
cd cloud-backend
npm install
npm start
```

The cloud backend runs on port 3001 by default.

---

## UDP Motion Trigger

### Overview

The Raspberry Pi app listens for UDP messages from ESP32 motion sensor devices to trigger video clip extraction.

### Configuration

**Port:** 5005  
**Protocol:** UDP  
**Trigger Message:** `"INTRUDER INTRUDER"`

### ESP32 Integration

Your ESP32 devices should send UDP packets to the Raspberry Pi's IP address:

**Arduino Code Example:**
```cpp
#include <WiFiUdp.h>

WiFiUDP udp;
const char* pi_ip = "192.168.1.100";  // Raspberry Pi IP
const int pi_port = 5005;
const char* trigger_msg = "INTRUDER INTRUDER";

void sendMotionTrigger() {
  udp.beginPacket(pi_ip, pi_port);
  udp.write((const uint8_t*)trigger_msg, strlen(trigger_msg));
  udp.endPacket();
}
```

### Cooldown Mechanism

To prevent spam and excessive clip generation:

- **Cooldown Period:** 10 seconds
- **Behavior:** Subsequent triggers within 10 seconds are ignored
- **Logged:** All ignored triggers are logged with remaining cooldown time

### Testing Motion Triggers

**Using netcat (nc):**
```bash
echo "INTRUDER INTRUDER" | nc -u 192.168.1.100 5005
```

**Using Python:**
```python
import socket

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.sendto(b"INTRUDER INTRUDER", ("192.168.1.100", 5005))
sock.close()
```

**Using Node.js:**
```javascript
import dgram from 'dgram';

const client = dgram.createSocket('udp4');
client.send('INTRUDER INTRUDER', 5005, '192.168.1.100', (err) => {
  client.close();
});
```

---

## Dependencies

### Production Dependencies

```json
{
  "axios": "^1.13.2",      // HTTP client for cloud uploads
  "express": "^5.2.1",     // Web server framework
  "form-data": "^4.0.5"    // Multipart form data for file uploads
}
```

### System Dependencies

- **FFmpeg** - Required for video processing, streaming, and clip extraction
- **Node.js** - Runtime environment (v14+)

---

## Troubleshooting

### Stream Won't Start

**Check:**
1. FFmpeg is installed and in PATH: `ffmpeg -version`
2. Camera device is available: `ls /dev/video*` (Linux)
3. RTSP URL is correct and accessible
4. No other process is using the camera

### UDP Triggers Not Working

**Check:**
1. UDP port 5005 is not blocked by firewall
2. ESP32 is sending to correct IP address
3. Trigger message is exactly `"INTRUDER INTRUDER"`
4. Check logs for cooldown messages

### Uploads Failing

**Check:**
1. Cloud backend is running on port 3001
2. Network connectivity between Pi and cloud backend
3. Check `queue.json` for error messages
4. Verify cloud backend logs for upload errors

### HLS Stream Not Playing

**Check:**
1. Stream is running: `GET /health`
2. HLS files exist: `ls hls/`
3. Web server can serve static files
4. CORS headers are set correctly

---

## Advanced Configuration

### Adjusting Video Quality

Edit `streamManager.js` to modify FFmpeg encoding parameters:

```javascript
const args = [
  "-c:v", "libx264",
  "-preset", "ultrafast",      // Change to "medium" or "slow" for better quality
  "-pix_fmt", "yuv420p",
  "-r", "30",                  // Frame rate
  "-b:v", "2M",                // Add bitrate control (e.g., "2M" = 2 Mbps)
];
```

### Adjusting Clip Duration

Edit `index.js` motion endpoint:

```javascript
app.post("/motion", async (_req, res) => {
  const saved = await extractClip({ 
    beforeSeconds: 10,  // 10 seconds before
    afterSeconds: 15    // 15 seconds after
  });
  res.json({ status: "ok", saved: !!saved, file: saved });
});
```

### Changing Upload Behavior

Edit `piUploader.js`:

```javascript
const DELETE_AFTER_UPLOAD = false;  // Keep local copies after upload
const MAX_ATTEMPTS = 10;            // More retry attempts
const UPLOAD_INTERVAL_MS = 60_000;  // Check every minute
```

---

## Security Considerations

### Production Deployment

1. **Use HTTPS** for cloud backend communication
2. **Implement authentication** for API endpoints
3. **Configure firewall rules** to restrict UDP port access
4. **Use environment variables** for sensitive configuration
5. **Enable CORS** only for trusted origins
6. **Implement rate limiting** on API endpoints
7. **Secure video storage** with encryption at rest

### Recommended Environment Variables

```bash
# Cloud backend
export CLOUD_BACKEND_URL="https://your-cloud-server.com/api/events/upload"
export CLOUD_API_KEY="your_secure_api_key"

# Server configuration
export PORT=4000
export NODE_ENV=production

# UDP listener
export UDP_PORT=5005
export UDP_TRIGGER_SECRET="your_secret_trigger_phrase"
```

---

## Performance Optimization

### For Low-End Devices (Raspberry Pi Zero/1)

1. Reduce video resolution in FFmpeg args
2. Lower frame rate to 15 FPS
3. Use hardware encoding if available (h264_omx)
4. Increase segment duration to reduce file operations
5. Increase upload interval to reduce network overhead

### For High-End Devices (Raspberry Pi 4/5)

1. Increase video quality with better presets
2. Enable hardware encoding (h264_v4l2m2m)
3. Support multiple camera streams
4. Reduce upload interval for faster cloud sync
5. Implement parallel uploads

---

## License

ISC

---

## Support

For issues and questions, contact the development team:
- Safir
- Koushik
- Neaz
- Ishayat
