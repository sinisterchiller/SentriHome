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

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun WifiConnectTestScreen(connector: WifiConnector) {
    var status by remember { mutableStateOf("Idle") }
    var network by remember { mutableStateOf<android.net.Network?>(null) }
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
                            httpClient.get("http://192.168.10.1/", testNetwork)
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
            Text("2. Test HTTP (192.168.10.1)")
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
