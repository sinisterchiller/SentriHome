package com.example.esp32pairingapp

import android.Manifest
import android.net.Network
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import com.example.esp32pairingapp.network.NetworkBinder
import com.example.esp32pairingapp.ui.theme.ESP32PairingAppTheme
import com.example.esp32pairingapp.wifi.WifiConnector
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.example.esp32pairingapp.network.CloudBackendPrefs
import com.example.esp32pairingapp.network.PiBackendPrefs
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import kotlin.collections.isNotEmpty
import kotlin.collections.take
import androidx.compose.ui.graphics.asImageBitmap

class MainActivity : ComponentActivity() {

    // This will hold the permission state for the UI
    private var hasLocationPermission by mutableStateOf(false)

    // Launcher that requests ACCESS_FINE_LOCATION and returns granted/denied
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val nearbyGranted = results[Manifest.permission.NEARBY_WIFI_DEVICES] == true

            // On Android 13+, nearby is the important one; on older versions, location is.
            hasLocationPermission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) nearbyGranted else locationGranted
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check permission when app starts
        hasLocationPermission = isLocationPermissionGranted()

        setContent {
            ESP32PairingAppTheme {
                /**PermissionScreen(
                hasPermission = hasLocationPermission,
                onRequestPermission = { requestRequiredPermissions() }
                )**/
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiConnectTestScreen(connector = WifiConnector(this))
                } else {
                    Text("Android 10+ required for this test screen.")
                }
            }
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES))
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

}

private const val POLL_INTERVAL_MS = 750L
private const val POLL_TIMEOUT_MS = 60_000L  // Stop after 60 seconds

// Fallback values in case BuildConfig fields aren't generated for this module/flavor.
// (You can replace these with the real SSID/password, or re-enable BuildConfig fields in Gradle.)
private const val ESP32_DEFAULT_SSID_FALLBACK = "ESP32"
private const val ESP32_DEFAULT_PASSWORD_FALLBACK = "12345678"

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun WifiConnectTestScreen(connector: WifiConnector) {
    var status by remember { mutableStateOf("Idle") }
    var network by remember { mutableStateOf<android.net.Network?>(null) }
    var showWifiDialog by remember { mutableStateOf(false) }
    var showStreamPage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val networkBinder = remember { NetworkBinder() }
    val httpClient = remember { com.example.esp32pairingapp.network.EspHttpClient() }

    if (showStreamPage) {
        StreamPage(
            network = network,
            httpClient = httpClient,
            onBack = { showStreamPage = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Text("Wi-Fi Test Status: $status", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    status = "Connecting to ESP32 WiFi..."
                    try {
                        val connectedNetwork = connector.connectAndroid10Plus(
                            ssid = ESP32_DEFAULT_SSID_FALLBACK,
                            password = ESP32_DEFAULT_PASSWORD_FALLBACK,
                            timeoutMs = 30_000L
                        )
                        network = connectedNetwork

                        // Bind app traffic to this network
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val bound = networkBinder.bindProcessToNetwork(connectedNetwork)
                            if (bound) {
                                status = "Connected ✅\nNetwork bound - all app traffic routed through ESP32"
                            } else {
                                status = "Connected ⚠️\nNetwork binding failed - Chrome may not work"
                            }
                        } else {
                            status = "Connected ✅"
                        }
                    } catch (e: Exception) {
                        status = "Failed ❌: ${e.message}"
                        network = null
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("1. Connect to ESP32")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                scope.launch {
                    status = "Testing HTTP connection..."
                    val testNetwork = network
                    if (testNetwork == null) {
                        status = "Error: Not connected to ESP32"
                        return@launch
                    }

                    try {
                        val response = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            httpClient.get(com.example.esp32pairingapp.network.ApiConfig.getHealthUrl(), testNetwork)
                        } else {
                            "HTTP test requires Android 5.0+"
                        }
                        status = "HTTP test succeeded ✅\nReceived ${response.length} bytes"
                    } catch (e: Exception) {
                        status = "HTTP test failed ❌: ${e.message}"
                    }
                }
            },
            enabled = network != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2. Test HTTP (/api/health)")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                showWifiDialog = true
            },
            enabled = network != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("3. Set WiFi Creds")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                showStreamPage = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("4. View Stream & Clips")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                // Unbind network first
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    networkBinder.unbindProcessFromNetwork()
                }
                connector.disconnect()
                network = null
                status = "Disconnected"
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Disconnect")
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Instructions:",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "1. Click 'Connect to ESP32'\n" +
                            "2. Wait for 'Connected ✅'\n" +
                            "3. Test HTTP to verify binding\n" +
                            "4. Open Chrome → http://192.168.10.1\n\n" +
                            "Note: Network binding routes ALL app traffic through ESP32, " +
                            "including Chrome when opened from this app.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // WiFi credentials dialog
    if (showWifiDialog) {
        WifiCredentialsDialog(
            onDismiss = { showWifiDialog = false },
            onSubmit = { ssid, password ->
                scope.launch {
                    status = "Sending WiFi credentials...\nSSID: '$ssid'\nPass: '${password.take(3)}...'"
                    val testNetwork = network
                    if (testNetwork == null) {
                        status = "Error: Not connected to ESP32"
                        showWifiDialog = false
                        return@launch
                    }

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            // URL encode the values
                            val encodedSsid = URLEncoder.encode(ssid, "UTF-8")
                            val encodedPassword = URLEncoder.encode(password, "UTF-8")

                            // Send SSID to /api/newssid
                            val ssidBody = "SSID=$encodedSsid"
                            Log.d("WifiSetup", "Sending SSID: $ssidBody")
                            val ssidResponse = httpClient.post(
                                com.example.esp32pairingapp.network.ApiConfig.getNewSsidUrl(),
                                ssidBody,
                                "application/x-www-form-urlencoded",
                                testNetwork
                            )
                            Log.d("WifiSetup", "SSID response: $ssidResponse")

                            // Send password to /api/newpass
                            val passBody = "pass=$encodedPassword"
                            Log.d("WifiSetup", "Sending password: pass=***")
                            val passResponse = httpClient.post(
                                com.example.esp32pairingapp.network.ApiConfig.getNewPassUrl(),
                                passBody,
                                "application/x-www-form-urlencoded",
                                testNetwork
                            )
                            Log.d("WifiSetup", "Password response: $passResponse")

                            status = "Credentials sent ✅\nPolling for WiFi connection..."
                            showWifiDialog = false

                            // Give ESP32 a moment to apply credentials before polling
                            delay(1_000L)

                            // Poll /api/wifistatus every 750ms until ESP confirms home WiFi connection
                            pollWifiStatus(testNetwork, httpClient, scope) { isConnected, pollStatus ->
                                status = pollStatus
                                if (isConnected) {
                                    status = "ESP32 connected to home WiFi ✅\nSetup complete!"
                                }
                            }
                        } else {
                            status = "WiFi setup requires Android 5.0+"
                            showWifiDialog = false
                        }
                    } catch (e: Exception) {
                        Log.e("WifiSetup", "Failed to send credentials", e)
                        status = "Failed to send credentials ❌: ${e.message}"
                        showWifiDialog = false
                    }
                }
            }
        )
    }
}

// --- Thumbnail helpers (simple, no extra deps) ---
private fun fetchBitmap(url: String): Bitmap? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        instanceFollowRedirects = true
    }

    return try {
        if (connection.responseCode in 200..299) {
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } else {
            null
        }
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun RemoteThumbnail(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        failed = false
        bitmap = null
        bitmap = try {
            fetchBitmap(url)
        } catch (_: Exception) {
            failed = true
            null
        }
    }

    when {
        bitmap != null -> {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier
            )
        }
        failed -> {
            Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("No thumbnail", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        else -> {
            Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@Composable
fun StreamPage(
    network: Network?,
    httpClient: com.example.esp32pairingapp.network.EspHttpClient,
    onBack: () -> Unit
) {
    var isLoadingStream by remember { mutableStateOf(false) }
    var isLoadingClips by remember { mutableStateOf(false) }
    var isStreaming by remember { mutableStateOf(false) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var clips by remember { mutableStateOf<List<VideoClip>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var piBackendStatus by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Cloud and Pi backend config (persisted).
    var showCloudDialog by remember { mutableStateOf(false) }
    var showPiDialog by remember { mutableStateOf(false) }
    var cloudHostInput by remember {
        mutableStateOf(CloudBackendPrefs.getRawHostInput(context) ?: "")
    }
    var piHostInput by remember {
        mutableStateOf(PiBackendPrefs.getRawHostInput(context) ?: "")
    }

    // Apply saved URLs when screen is shown.
    LaunchedEffect(Unit) {
        val cloudSaved = CloudBackendPrefs.getRawHostInput(context)
        val piSaved = PiBackendPrefs.getRawHostInput(context)

        if (!cloudSaved.isNullOrBlank()) {
            runCatching {
                val baseUrl = CloudBackendPrefs.computeCloudBaseUrl(cloudSaved)
                com.example.esp32pairingapp.network.ApiConfig.setCloudBaseUrlOverride(baseUrl)
            }
        }
        if (!piSaved.isNullOrBlank()) {
            runCatching {
                val baseUrl = PiBackendPrefs.computePiBaseUrl(piSaved)
                com.example.esp32pairingapp.network.ApiConfig.setPiBaseUrlOverride(baseUrl)
            }
        }
        // Show Pi dialog first if not configured, then Cloud
        if (piSaved.isNullOrBlank()) showPiDialog = true
        else if (cloudSaved.isNullOrBlank()) showCloudDialog = true

        // Check Pi backend connectivity. Use null network so we reach Pi on LAN (not via ESP32).
        scope.launch {
            try {
                val piUrl = com.example.esp32pairingapp.network.ApiConfig.getPiHealthUrl()
                Log.d("StreamPage", "Checking Pi at: $piUrl")
                httpClient.get(piUrl, null)
                piBackendStatus = "✅ Pi backend reachable at $piUrl"
            } catch (e: Exception) {
                piBackendStatus = "❌ Pi NOT reachable: ${e.message}. Tap Edit Pi to set IP (port 4000)."
                Log.e("StreamPage", "Pi check failed", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) {
                Text("← Back")
            }
            Text("Stream & Clips", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(80.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Connection status indicator
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (network != null)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (network != null) "✅ Connected to ESP32 Network" else "📡 Using localhost backend",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cloud: ${com.example.esp32pairingapp.network.ApiConfig.getCloudBaseUrl()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Pi: ${com.example.esp32pairingapp.network.ApiConfig.getPiBaseUrl()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showCloudDialog = true }) {
                            Text("Edit Cloud")
                        }
                        Button(onClick = { showPiDialog = true }) {
                            Text("Edit Pi")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quick backend reachability indicator
        if (piBackendStatus != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = piBackendStatus!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (piBackendStatus!!.startsWith("✅")) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }

        // Live Stream Control Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Live Stream Control", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Start Stream Button
                    Button(
                        onClick = {
                            scope.launch {
                                isLoadingStream = true
                                errorMessage = null
                                try {
                                    // POST to Pi backend (use null = default network to reach Pi on LAN)
                                    val url = com.example.esp32pairingapp.network.ApiConfig.getStartStreamUrl()
                                    Log.d("StreamPage", "Start stream POST to: $url")
                                    val response = httpClient.post(
                                        url,
                                        """{"type":"webcam","value":""}""",
                                        "application/json",
                                        null
                                    )

                                    Log.d("StreamPage", "Start response: $response")

                                    val json = JSONObject(response)
                                    val status = json.optString("status", "error")

                                    if (status == "ok" || status == "success") {
                                        isStreaming = true
                                        streamUrl = com.example.esp32pairingapp.network.ApiConfig.getStreamUrl()
                                        errorMessage = "✅ Stream started successfully"
                                    } else {
                                        errorMessage = "⚠️ Start failed: ${json.optString("message", "Unknown error")}"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "❌ Failed to start stream: ${e.message}"
                                    Log.e("StreamPage", "Start stream error", e)
                                } finally {
                                    isLoadingStream = false
                                }
                            }
                        },
                        enabled = !isLoadingStream && !isStreaming,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isLoadingStream) "Starting..." else "▶️ Start")
                    }

                    // Stop Stream Button
                    Button(
                        onClick = {
                            scope.launch {
                                isLoadingStream = true
                                errorMessage = null
                                try {
                                    // POST to Pi backend (use null = default network)
                                    val response = httpClient.post(
                                        com.example.esp32pairingapp.network.ApiConfig.getStopStreamUrl(),
                                        "",
                                        "application/json",
                                        null
                                    )

                                    Log.d("StreamPage", "Stop response: $response")

                                    isStreaming = false
                                    streamUrl = null
                                    errorMessage = "⏹️ Stream stopped"
                                } catch (e: Exception) {
                                    errorMessage = "❌ Failed to stop stream: ${e.message}"
                                    Log.e("StreamPage", "Stop stream error", e)
                                } finally {
                                    isLoadingStream = false
                                }
                            }
                        },
                        enabled = !isLoadingStream && isStreaming,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("⏹️ Stop")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Trigger Motion Button
                Button(
                    onClick = {
                        scope.launch {
                            isLoadingStream = true
                            errorMessage = null
                                try {
                                // POST to Pi backend (use null = default network to reach Pi on LAN)
                                val motionUrl = com.example.esp32pairingapp.network.ApiConfig.getMotionTriggerUrl()
                                Log.d("StreamPage", "Motion POST to: $motionUrl")
                                val response = httpClient.post(
                                    motionUrl,
                                    "",
                                    "application/json",
                                    null
                                )

                                Log.d("StreamPage", "Motion response: $response")
                                val msg = runCatching { JSONObject(response).optString("message", "") }.getOrNull() ?: ""
                                if (msg.contains("cloud backend", ignoreCase = true)) {
                                    errorMessage = "❌ Wrong backend! Motion hit Cloud (3001) not Pi (4000). Tap Edit Pi and set Pi IP (port 4000)."
                                } else {
                                    errorMessage = "📸 Motion triggered! Clip will be saved to Google Drive"
                                }

                                // Wait a bit then refresh clips
                                delay(2000)
                                // Auto-refresh clips after motion
                                isLoadingClips = true
                                try {
                                    val clipsResponse = httpClient.get(
                                        com.example.esp32pairingapp.network.ApiConfig.getClipsUrl(),
                                        network
                                    )
                                    val clipsJson = JSONObject(clipsResponse)
                                    val clipsArray = clipsJson.optJSONArray("clips") ?: JSONArray()

                                    clips = (0 until clipsArray.length()).map { i ->
                                        val clipObj = clipsArray.getJSONObject(i)
                                        VideoClip(
                                            id = clipObj.optString("_id", ""),
                                            filename = clipObj.optString("filename", "clip_$i.mp4"),
                                            timestamp = clipObj.optString("createdAt", ""),
                                            deviceId = clipObj.optString("deviceId", "unknown"),
                                            thumbnailUrl = clipObj.optJSONObject("thumbnail")?.optString("webViewLink"),
                                            videoUrl = clipObj.optJSONObject("video")?.optString("webViewLink")
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("StreamPage", "Clips refresh error", e)
                                } finally {
                                    isLoadingClips = false
                                }
                            } catch (e: Exception) {
                                errorMessage = "❌ Failed to trigger motion: ${e.message}"
                                Log.e("StreamPage", "Motion trigger error", e)
                            } finally {
                                isLoadingStream = false
                            }
                        }
                    },
                    enabled = !isLoadingStream,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📸 Trigger Motion & Save Clip")
                }

                if (streamUrl != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Stream URL: $streamUrl",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Status: ${if (isStreaming) "🟢 LIVE" else "⚫ STOPPED"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Recorded Clips Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Recorded Clips (Google Drive)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isLoadingClips = true
                            errorMessage = null
                            try {
                                // GET from Cloud backend: /api/events
                                // Use null network so requests use default (home WiFi) to reach cloud
                                val response = httpClient.get(
                                    com.example.esp32pairingapp.network.ApiConfig.getEventsUrl(),
                                    null
                                )

                                Log.d("StreamPage", "Events response: ${response.take(500)}")

                                // Cloud backend returns raw array [{...}]. Also support { events: [...] } or { clips: [...] }.
                                val eventsArray: JSONArray = runCatching {
                                    if (response.trimStart().startsWith("[")) {
                                        JSONArray(response)
                                    } else {
                                        val json = JSONObject(response)
                                        when {
                                            json.has("events") -> json.optJSONArray("events") ?: JSONArray()
                                            json.has("clips") -> json.optJSONArray("clips") ?: JSONArray()
                                            else -> JSONArray()
                                        }
                                    }
                                }.getOrElse {
                                    Log.e("StreamPage", "Failed to parse events", it)
                                    JSONArray()
                                }

                                clips = (0 until eventsArray.length()).map { i ->
                                    val eventObj = eventsArray.getJSONObject(i)
                                    val id = eventObj.optString("_id", eventObj.optString("id", ""))
                                    val filename = eventObj.optString("filename", "event_$i.mp4")
                                    val timestamp = eventObj.optString("createdAt", eventObj.optString("timestamp", ""))
                                    val deviceId = eventObj.optString("deviceId", "unknown")

                                    VideoClip(
                                        id = id,
                                        filename = filename,
                                        timestamp = timestamp,
                                        deviceId = deviceId,
                                        thumbnailUrl = if (id.isNotBlank()) com.example.esp32pairingapp.network.ApiConfig.getClipThumbnailUrl(id) else null,
                                        videoUrl = if (id.isNotBlank()) com.example.esp32pairingapp.network.ApiConfig.getClipStreamUrl(id) else null
                                    )
                                }

                                errorMessage = "✅ Loaded ${clips.size} events"
                            } catch (e: Exception) {
                                errorMessage = "❌ Failed to load events: ${e.message}"
                                Log.e("StreamPage", "Events error", e)
                            } finally {
                                isLoadingClips = false
                            }
                        }
                    },
                    enabled = !isLoadingClips,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isLoadingClips) "Loading..." else "🔄 Load Clips from Drive")
                }

                if (clips.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))

                    clips.take(20).forEach { clip ->
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val thumb = clip.thumbnailUrl
                            if (thumb != null) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.size(width = 120.dp, height = 80.dp)
                                ) {
                                    RemoteThumbnail(
                                        url = thumb,
                                        modifier = Modifier.fillMaxSize(),
                                        contentDescription = "Thumbnail for ${clip.filename}"
                                    )
                                }
                            } else {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(width = 120.dp, height = 80.dp)
                                ) {}
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(clip.filename, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Device: ${clip.deviceId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                if (clip.timestamp.isNotBlank()) {
                                    Text(
                                        clip.timestamp.take(19),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                Button(
                                    onClick = {
                                        val url = clip.videoUrl
                                        if (url.isNullOrBlank()) {
                                            errorMessage = "❌ No video URL for this event"
                                            return@Button
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        // Let Android pick the best player (Chrome, VLC, etc.)
                                        context.startActivity(intent)
                                    },
                                    enabled = !clip.videoUrl.isNullOrBlank(),
                                ) {
                                    Text("▶️ Play")
                                }
                            }
                        }
                    }

                    if (clips.size > 20) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Showing 20 of ${clips.size}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Status/Error message display
        // Status/Error message display
        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        errorMessage!!.startsWith("✅") -> MaterialTheme.colorScheme.primaryContainer
                        errorMessage!!.startsWith("⚠️") -> MaterialTheme.colorScheme.tertiaryContainer
                        errorMessage!!.startsWith("❌") -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(
                    text = errorMessage!!,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Cloud backend dialog (Edit Cloud)
    if (showCloudDialog) {
        AlertDialog(
            onDismissRequest = { showCloudDialog = false },
            title = { Text("Cloud backend IP / Host") },
            text = {
                Column {
                    Text(
                        "Enter the computer running the Cloud backend. Port 3001 is used automatically.\n\nExamples: 192.168.1.50 or cloudbox.local",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = cloudHostInput,
                        onValueChange = { cloudHostInput = it },
                        label = { Text("Cloud host or IP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Result: " + runCatching { CloudBackendPrefs.computeCloudBaseUrl(cloudHostInput) }
                            .getOrElse { "(invalid)" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val baseUrl = CloudBackendPrefs.computeCloudBaseUrl(cloudHostInput)
                            CloudBackendPrefs.setRawHostInput(context, cloudHostInput)
                            com.example.esp32pairingapp.network.ApiConfig.setCloudBaseUrlOverride(baseUrl)
                            errorMessage = "✅ Cloud backend set to $baseUrl"
                            showCloudDialog = false
                        } catch (e: Exception) {
                            errorMessage = "❌ Invalid cloud address: ${e.message}"
                        }
                    },
                    enabled = cloudHostInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = { showCloudDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Pi backend dialog (Edit Pi)
    if (showPiDialog) {
        AlertDialog(
            onDismissRequest = { showPiDialog = false },
            title = { Text("Raspberry Pi IP / Host") },
            text = {
                Column {
                    Text(
                        "Enter the Raspberry Pi's IP or hostname. Port 4000 is used automatically.\n\nExamples: 192.168.1.73 or raspberrypi.local",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = piHostInput,
                        onValueChange = { piHostInput = it },
                        label = { Text("Pi host or IP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Result: " + runCatching { PiBackendPrefs.computePiBaseUrl(piHostInput) }
                            .getOrElse { "(invalid)" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val baseUrl = PiBackendPrefs.computePiBaseUrl(piHostInput)
                            PiBackendPrefs.setRawHostInput(context, piHostInput)
                            com.example.esp32pairingapp.network.ApiConfig.setPiBaseUrlOverride(baseUrl)
                            errorMessage = "✅ Pi backend set to $baseUrl"
                            piBackendStatus = null
                            showPiDialog = false
                            if (CloudBackendPrefs.getRawHostInput(context).isNullOrBlank()) {
                                showCloudDialog = true
                            }
                        } catch (e: Exception) {
                            errorMessage = "❌ Invalid Pi address: ${e.message}"
                        }
                    },
                    enabled = piHostInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = { showPiDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WifiCredentialsDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter WiFi Credentials") },
        text = {
            Column {
                TextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("SSID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ssid.isNotBlank() && password.isNotBlank()) {
                        onSubmit(ssid, password)
                    }
                },
                enabled = ssid.isNotBlank() && password.isNotBlank()
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Polls /api/wifistatus until ESP32 reports connected.
 */
private suspend fun pollWifiStatus(
    network: Network,
    httpClient: com.example.esp32pairingapp.network.EspHttpClient,
    scope: kotlinx.coroutines.CoroutineScope,
    onStatus: (isConnected: Boolean, status: String) -> Unit
) {
    val startTime = System.currentTimeMillis()
    var pollCount = 0

    while (scope.isActive && (System.currentTimeMillis() - startTime) < POLL_TIMEOUT_MS) {
        pollCount++
        try {
            val response = httpClient.get(com.example.esp32pairingapp.network.ApiConfig.getWifiStatusUrl(), network)
            val json = JSONObject(response)
            val connected = json.optString("connected", "").lowercase() == "true"

            if (connected) {
                onStatus(true, "ESP32 connected to home WiFi ✅")
                return
            }

            onStatus(false, "Waiting for ESP to connect... (poll #$pollCount)")
        } catch (_: Exception) {
            onStatus(false, "Waiting for ESP... (poll #$pollCount, retrying)")
        }

        delay(POLL_INTERVAL_MS)
    }

    onStatus(false, "Timeout: ESP32 did not confirm WiFi connection within ${POLL_TIMEOUT_MS / 1000}s")
}

// Model for events/clips displayed in StreamPage
data class VideoClip(
    val id: String,
    val filename: String,
    val timestamp: String,
    val deviceId: String,
    val thumbnailUrl: String?,
    val videoUrl: String?
)
