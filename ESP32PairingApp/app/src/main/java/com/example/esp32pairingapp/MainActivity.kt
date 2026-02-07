package com.example.esp32pairingapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.esp32pairingapp.ui.theme.ESP32PairingAppTheme
import android.util.Log
import com.example.esp32pairingapp.pairing.PasswordGenerator
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.example.esp32pairingapp.wifi.WifiConnector
import com.example.esp32pairingapp.network.NetworkBinder
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.Network
import kotlinx.coroutines.NonCancellable.isActive
import java.net.URLEncoder
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


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

private const val WIFISTATUS_URL = "http://192.168.10.1/api/wifistatus"
private const val POLL_INTERVAL_MS = 750L
private const val POLL_TIMEOUT_MS = 60_000L  // Stop after 60 seconds

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun WifiConnectTestScreen(connector: WifiConnector) {
    var status by remember { mutableStateOf("Idle") }
    var network by remember { mutableStateOf<android.net.Network?>(null) }
    var showWifiDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val networkBinder = remember { NetworkBinder() }
    val httpClient = remember { com.example.esp32pairingapp.network.EspHttpClient() }

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
                            ssid = "ESP32_Master_Config",
                            password = "12345678",
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
                            httpClient.get("http://192.168.10.1/api/health", testNetwork)
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
                                "http://192.168.10.1/api/newssid",
                                ssidBody,
                                "application/x-www-form-urlencoded",
                                testNetwork
                            )
                            Log.d("WifiSetup", "SSID response: $ssidResponse")
                            
                            // Send password to /api/newpass
                            val passBody = "pass=$encodedPassword"
                            Log.d("WifiSetup", "Sending password: pass=***")
                            val passResponse = httpClient.post(
                                "http://192.168.10.1/api/newpass",
                                passBody,
                                "application/x-www-form-urlencoded",
                                testNetwork
                            )
                            Log.d("WifiSetup", "Password response: $passResponse")
                            
                            status = "Credentials sent ✅\nPolling for WiFi connection..."
                            showWifiDialog = false
                            
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

/**
 * Polls /api/wifistatus every 500-1000ms until ESP32 returns {"connected": "true"}.
 * Stops on success, timeout (60s), or when the coroutine is cancelled.
 */
private suspend fun pollWifiStatus(
    network: android.net.Network,
    httpClient: com.example.esp32pairingapp.network.EspHttpClient,
    scope: kotlinx.coroutines.CoroutineScope,
    onStatus: (isConnected: Boolean, status: String) -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
    
    val startTime = System.currentTimeMillis()
    var pollCount = 0
    
    while (isActive && (System.currentTimeMillis() - startTime) < POLL_TIMEOUT_MS) {
        pollCount++
        try {
            val response = httpClient.get(WIFISTATUS_URL, network)
            val json = JSONObject(response)
            val connected = json.optString("connected", "").lowercase() == "true"
            
            if (connected) {
                Log.d("WifiSetup", "ESP32 confirmed home WiFi connection")
                onStatus(true, "ESP32 connected to home WiFi ✅")
                return
            }
            
            onStatus(false, "Waiting for ESP to connect... (poll #$pollCount)")
        } catch (e: Exception) {
            Log.d("WifiSetup", "Poll #$pollCount failed: ${e.message}")
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
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
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
                    text = if (hasPermission) "Location Permission Granted"
                    else "Location Permission Required",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "This permission is needed for Wi-Fi pairing to discover and connect to the ESP32.",
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
