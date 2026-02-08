package com.example.esp32pairingapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.esp32pairingapp.network.EspHttpClient
import com.example.esp32pairingapp.ui.theme.ESP32PairingAppTheme
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder

private const val BASE_URL = "http://192.168.10.1"
private const val HEALTH_URL = "$BASE_URL/api/health"
private const val NEWSSID_URL = "$BASE_URL/api/newssid"
private const val NEWPASS_URL = "$BASE_URL/api/newpass"
private const val WIFISTATUS_URL = "$BASE_URL/api/wifistatus"

private const val POLL_INTERVAL_MS = 750L
private const val POLL_TIMEOUT_MS = 60_000L

class MainActivity : ComponentActivity() {

    private var hasLocationPermission by mutableStateOf(false)

    // Request permissions (keep this; some devices require it for Wi-Fi related behavior)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val nearbyGranted = results[Manifest.permission.NEARBY_WIFI_DEVICES] == true

            hasLocationPermission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) nearbyGranted else locationGranted
        }

    private val httpClient by lazy { EspHttpClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasLocationPermission = isLocationPermissionGranted()

        setContent {
            ESP32PairingAppTheme {
                // Optional: show permission screen first.
                // If you want to skip it, remove this if/else and always show WifiManualScreen.
                if (!hasLocationPermission) {
                    PermissionScreen(
                        hasPermission = hasLocationPermission,
                        onRequestPermission = { requestRequiredPermissions() }
                    )
                } else {
                    WifiManualScreen(httpClient = httpClient)
                }
            }
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES))
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }
}

@Composable
fun WifiManualScreen(httpClient: EspHttpClient) {
    var status by remember { mutableStateOf("Manual connection mode:\n" +
            "1) Connect your phone to the ESP32 Wi-Fi in Android Settings\n" +
            "2) Return here and tap “Test Connection”") }
    var showWifiDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Text(
            text = "Status",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(Modifier.height(16.dp))


        // ✅ Always enabled
        Button(
            onClick = {
                scope.launch {
                    status = "Testing HTTP connection..."
                    try {
                        val response =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                httpClient.get(HEALTH_URL, network = null)
                            } else {
                                "HTTP requires Android 5.0+"
                            }

                        status = "HTTP test succeeded ✅\n${response.take(400)}"
                    } catch (e: Exception) {
                        status =
                            "HTTP test failed ❌: ${e.message}\n\n" +
                                    "Make sure you are connected to the ESP32 Wi-Fi in Settings."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Connection")
        }

        Spacer(Modifier.height(8.dp))

        // ✅ Always enabled
        Button(
            onClick = { showWifiDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send WiFi Credentials")
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Manual mode:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                            "• Step 1: Open Android Wi-Fi settings and connect to the ESP32 network\n" +
                                "• Step 2: Return to this app\n" +
                                "• Step 3: Tap “Test Connection”\n" +
                                "• Step 4: Tap “Send Wi-Fi Credentials” to send your home Wi-Fi info\n\n" +
                                "If “Test Connection” fails, you’re probably not connected to ESP32 Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showWifiDialog) {
        WifiCredentialsDialog(
            onDismiss = { showWifiDialog = false },
            onSubmit = { ssid, password ->
                scope.launch {
                    status = "Sending WiFi credentials..."

                    try {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                            status = "WiFi setup requires Android 5.0+"
                            showWifiDialog = false
                            return@launch
                        }

                        // Clean + encode (fixes hidden whitespace / newline issue)
                        val cleanedSsid = ssid.trim().replace(Regex("\\p{C}"), "")
                        val cleanedPass = password.trim().replace(Regex("\\p{C}"), "")

                        val encodedSsid = URLEncoder.encode(cleanedSsid, "UTF-8")
                        val encodedPass = URLEncoder.encode(cleanedPass, "UTF-8")

                        val ssidBody = "SSID=$encodedSsid"
                        val passBody = "pass=$encodedPass"

                        // Manual mode: no Network parameter
                        httpClient.post(
                            url = NEWSSID_URL,
                            body = ssidBody,
                            contentType = "application/x-www-form-urlencoded",
                            network = null
                        )

                        httpClient.post(
                            url = NEWPASS_URL,
                            body = passBody,
                            contentType = "application/x-www-form-urlencoded",
                            network = null
                        )

                        showWifiDialog = false
                        status = "Credentials sent ✅\nConnecting ESP32 to your home Wi-Fi…"

                        pollWifiStatus(httpClient) { isConnected, pollStatus ->
                            status = if (isConnected) {
                                "ESP32 connected to home WiFi ✅\nSetup complete!"
                            } else {
                                pollStatus
                            }
                        }

                    } catch (e: Exception) {
                        showWifiDialog = false
                        status =
                            "Failed to send credentials ❌: ${e.message}\n\n" +
                                    "Make sure you are connected to ESP32 Wi-Fi in Settings."
                    }
                }
            }
        )
    }
}

/**
 * Polls /api/wifistatus every ~750ms until ESP32 returns connected=true.
 * Stops on success, timeout, or coroutine cancellation.
 */
private suspend fun pollWifiStatus(
    httpClient: EspHttpClient,
    onStatus: (isConnected: Boolean, status: String) -> Unit
) {
    val startTime = System.currentTimeMillis()

    fun secondsElapsed(): Long = (System.currentTimeMillis() - startTime) / 1000

    while (kotlinx.coroutines.currentCoroutineContext().isActive &&
        (System.currentTimeMillis() - startTime) < POLL_TIMEOUT_MS
    ) {
        try {
            val response = httpClient.get(WIFISTATUS_URL, network = null)

            // Try JSON first
            val json = runCatching { JSONObject(response) }.getOrNull()

            if (json != null) {
                val connected = json.optBoolean("connected", false)
                val state = json.optString("state", "")
                val reason = json.optString("reason", "")
                val ip = json.optString("ip", "")

                if (connected) {
                    val extra = if (ip.isNotBlank()) "\nIP: $ip" else ""
                    onStatus(true, "Connected to home Wi-Fi ✅$extra")
                    return
                }

                // Not connected yet — show best available info
                val detail = when {
                    state.isNotBlank() -> state
                    reason.isNotBlank() -> "Error: $reason"
                    else -> "Connecting…"
                }

                onStatus(false, "Waiting for ESP32… ($detail)\nElapsed: ${secondsElapsed()}s")
            } else {
                // Not JSON — show raw response safely
                val shortRaw = response.trim().take(80)
                onStatus(false, "Waiting for ESP32…\nStatus: $shortRaw\nElapsed: ${secondsElapsed()}s")
            }

        } catch (_: Exception) {
            onStatus(false, "Waiting for ESP32…\nElapsed: ${secondsElapsed()}s")
        }

        delay(POLL_INTERVAL_MS)
    }

    onStatus(false, "Timeout ❌\nESP32 did not confirm home Wi-Fi within ${POLL_TIMEOUT_MS / 1000}s.")
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
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PermissionScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (hasPermission) "Permission Granted"
                    else "Permission Required",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "This permission helps Wi-Fi operations on some Android versions/devices.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(onClick = onRequestPermission) {
                    Text(text = if (hasPermission) "Permission OK" else "Grant Permission")
                }
            }
        }
    }
}
