package com.example.esp32pairingapp

import android.Manifest
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
import androidx.core.content.ContextCompat
import com.example.esp32pairingapp.network.EspHttpClient
import com.example.esp32pairingapp.network.NetworkBinder
import com.example.esp32pairingapp.ui.theme.ESP32PairingAppTheme
import com.example.esp32pairingapp.wifi.WifiConnector
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

    // Create these ONCE (prevents connect-then-drop issues)
    private val wifiConnector by lazy { WifiConnector(this) }
    private val networkBinder by lazy { NetworkBinder(this) }
    private val httpClient by lazy { EspHttpClient() }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val nearbyGranted = results[Manifest.permission.NEARBY_WIFI_DEVICES] == true

            hasLocationPermission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) nearbyGranted else locationGranted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasLocationPermission = isLocationPermissionGranted()

        setContent {
            ESP32PairingAppTheme {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiConnectTestScreen(
                        connector = wifiConnector,
                        networkBinder = networkBinder,
                        httpClient = httpClient
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Android 10+ required for this test screen.")
                    }
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

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun WifiConnectTestScreen(
    connector: WifiConnector,
    networkBinder: NetworkBinder,
    httpClient: EspHttpClient
) {
    var status by remember { mutableStateOf("Idle") }
    var network by remember { mutableStateOf<android.net.Network?>(null) }
    var showWifiDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Text("Wi-Fi Test Status:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                scope.launch {
                    status = "Connecting to ESP32 WiFi..."
                    try {
                        val connectedNetwork = connector.connectAndroid10Plus(
                            ssid = "ESP32_Master_Config",
                            password = "12345678",
                            timeoutMs = 30_000L
                        )
                        network = connectedNetwork

                        // Bind this app’s traffic to ESP32 network
                        val bound = networkBinder.bindTo(connectedNetwork)
                        if (bound) {
                            status = "Connected ✅\nBound to ESP32 network.\nStabilizing..."
                        } else {
                            status = "Connected ⚠️\nBind failed (traffic may go to mobile)."
                        }

                        // Small stabilize delay + ping helps Android "stick" to the AP
                        delay(400)
                        try {
                            httpClient.get(HEALTH_URL, connectedNetwork)
                            status = "Connected ✅\nBound ✅\nHealth check OK ✅"
                        } catch (e: Exception) {
                            status = "Connected ✅\nBound ✅\nHealth check failed ⚠️: ${e.message}"
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
                        val response = httpClient.get(HEALTH_URL, testNetwork)
                        status = "HTTP test succeeded ✅\n${response.take(200)}"
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
            onClick = { showWifiDialog = true },
            enabled = network != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("3. Set WiFi Creds")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                // Unbind + disconnect + clear state
                networkBinder.unbind()
                connector.disconnect()
                network = null
                status = "Disconnected"
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Disconnect")
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Notes:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "1. Click 'Connect to ESP32'\n" +
                            "2. Wait for 'Connected ✅'\n" +
                            "3. Test HTTP to verify binding\n" +
                            "4. Open Chrome → http://192.168.10.1\n\n" + "Note: Network binding routes ALL app traffic through ESP32, " +
                            "including Chrome when opened from this app.",
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
                    val testNetwork = network
                    if (testNetwork == null) {
                        status = "Error: Not connected to ESP32"
                        showWifiDialog = false
                        return@launch
                    }

                    try {
                        // Clean + encode (prevents hidden whitespace issue)
                        val cleanedSsid = ssid.trim().replace(Regex("\\p{C}"), "")
                        val cleanedPass = password.trim().replace(Regex("\\p{C}"), "")

                        val encodedSsid = URLEncoder.encode(cleanedSsid, "UTF-8")
                        val encodedPass = URLEncoder.encode(cleanedPass, "UTF-8")

                        val ssidBody = "SSID=$encodedSsid"
                        val passBody = "pass=$encodedPass"

                        httpClient.post(NEWSSID_URL, ssidBody, "application/x-www-form-urlencoded", testNetwork)
                        httpClient.post(NEWPASS_URL, passBody, "application/x-www-form-urlencoded", testNetwork)

                        status = "Credentials sent ✅\nPolling for WiFi connection..."
                        showWifiDialog = false

                        pollWifiStatus(testNetwork, httpClient) { isConnected, pollStatus ->
                            status = pollStatus
                            if (isConnected) {
                                status = "ESP32 connected to home WiFi ✅\nSetup complete!"
                            }
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

/**
 * Polls /api/wifistatus every ~750ms until ESP32 returns connected=true.
 * Stops on success, timeout, or coroutine cancellation.
 */
private suspend fun pollWifiStatus(
    network: android.net.Network,
    httpClient: EspHttpClient,
    onStatus: (isConnected: Boolean, status: String) -> Unit
) {
    val startTime = System.currentTimeMillis()
    var pollCount = 0

    while (currentCoroutineContext().isActive &&
        (System.currentTimeMillis() - startTime) < POLL_TIMEOUT_MS
    ) {
        pollCount++
        try {
            val response = httpClient.get(WIFISTATUS_URL, network)
            val json = JSONObject(response)
            val connected = json.optString("connected", "").lowercase() == "true"

            if (connected) {
                onStatus(true, "ESP32 connected to home WiFi ✅")
                return
            }

            onStatus(false, "Waiting for ESP to connect... (poll #$pollCount)")
        } catch (e: Exception) {
            onStatus(false, "Waiting for ESP... (poll #$pollCount, retrying)")
        }

        delay(POLL_INTERVAL_MS)
    }

    onStatus(false, "Timeout: ESP32 did not confirm WiFi connection within ${POLL_TIMEOUT_MS / 1000}s")
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
