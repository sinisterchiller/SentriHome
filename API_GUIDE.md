# Home Security API Guide

## 🚨 **IMPORTANT: Use Correct Backend Ports**

### **Cloud Backend (Port 3001)**
- ✅ `GET /status` - Health check
- ✅ `GET /api/events` - List events with thumbnails
- ✅ `POST /api/events/upload` - Upload files (Pi only)
- ✅ `DELETE /api/clear-all` - Clear database
- ❌ `POST /start` - Not available (use Pi backend)
- ❌ `POST /stop` - Not available (use Pi backend)
- ❌ `POST /motion` - Not available (use Pi backend)

### **Pi Backend (Port 4000)**
- ✅ `GET /health` - System status
- ✅ `POST /start` - Start stream
- ✅ `POST /stop` - Stop stream
- ✅ `POST /motion` - Trigger motion
- ✅ `DELETE /clear-all` - Clear local data

## 🎯 **Correct Usage Examples**

### **Stream Control (Pi Backend)**
```bash
# Start stream
curl -X POST http://localhost:4000/start \
  -H "Content-Type: application/json" \
  -d '{"type":"webcam","value":""}'

# Stop stream
curl -X POST http://localhost:4000/stop

# Trigger motion
curl -X POST http://localhost:4000/motion
```

### **Cloud Data (Cloud Backend)**
```bash
# Get events
curl http://localhost:3001/api/events

# Clear database
curl -X DELETE http://localhost:3001/api/clear-all

# Health check
curl http://localhost:3001/status
```

## 🖥️ **Frontend Integration**

The frontend automatically routes requests correctly:
- `/api/*` → Pi backend (port 4000)
- `/cloud/*` → Cloud backend (port 3001)

## 🚨 **Common Mistakes to Avoid**

1. **❌ Wrong:** `curl -X POST http://localhost:3001/stop`
   **✅ Right:** `curl -X POST http://localhost:4000/stop`

2. **❌ Wrong:** `curl http://localhost:4000/api/events`
   **✅ Right:** `curl http://localhost:3001/api/events`

3. **❌ Wrong:** `curl -X DELETE http://localhost:4000/api/clear-all`
   **✅ Right:** `curl -X DELETE http://localhost:3001/api/clear-all`

## 🎯 **Quick Test Commands**

```bash
# Test Pi backend
curl http://localhost:4000/health

# Test cloud backend  
curl http://localhost:3001/status

# Trigger motion (full pipeline test)
curl -X POST http://localhost:4000/motion
sleep 5
curl http://localhost:3001/api/events
```
