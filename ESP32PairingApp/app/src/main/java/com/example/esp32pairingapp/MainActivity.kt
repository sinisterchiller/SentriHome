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
                PermissionScreen(
                    hasPermission = hasLocationPermission,
                    onRequestPermission = { requestRequiredPermissions() }
                )
                /**if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiConnectTestScreen(connector = WifiConnector(this))
                } else {
                    Text("Android 10+ required for this test screen.")
                }**/
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

/**@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun WifiConnectTestScreen(connector: WifiConnector) {
    var status by remember { mutableStateOf("Idle") }
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(16.dp)) {
        Text("Wi-Fi Test Status: $status")
        Spacer(Modifier.height(12.dp))

        Button(onClick = {
            scope.launch {
                status = "Connecting..."
                try {
                    connector.connectAndroid10Plus(
                        ssid = "ESP32_Master_Config",
                        password = "12345678",
                        timeoutMs = 30_000L
                    )
                    status = "Connected ✅"
                } catch (e: Exception) {
                    status = "Failed ❌: ${e.message}"
                }
            }
        }) {
            Text("Connect to ESP32")
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            connector.disconnect()
            status = "Disconnected"
        }) {
            Text("Disconnect")
        }
    }
}**/

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
