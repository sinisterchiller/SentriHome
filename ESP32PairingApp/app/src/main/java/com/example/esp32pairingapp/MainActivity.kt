package com.example.esp32pairingapp

import android.Manifest
import kotlinx.coroutines.currentCoroutineContext
import android.net.Network
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.esp32pairingapp.network.CloudBackendPrefs
import com.example.esp32pairingapp.network.EspHttpClient
import com.example.esp32pairingapp.clips.HlsPlayerView
import com.example.esp32pairingapp.clips.SavedClipsContent
import com.example.esp32pairingapp.clips.SavedClipsScreen
import com.example.esp32pairingapp.clips.VideoClip
import com.example.esp32pairingapp.pairing.PasswordGenerator
import com.example.esp32pairingapp.pairing.OtpGenerator
import com.example.esp32pairingapp.network.PiBackendPrefs
import com.example.esp32pairingapp.ui.theme.ESP32PairingAppTheme
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import android.content.Intent
import android.net.Uri
import androidx.annotation.RequiresApi
import android.widget.Toast
import kotlin.collections.isNotEmpty
import kotlin.collections.take
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Arrangement

private const val BASE_URL = "http://192.168.10.1"
private const val HEALTH_URL = "$BASE_URL/api/health"
private const val NEWSSID_URL = "$BASE_URL/api/newssid"
private const val NEWPASS_URL = "$BASE_URL/api/newpass"
private const val ENCRYPTEDPASS_URL = "$BASE_URL/api/encryptedpass"

private const val ONETIMEPASS_URL = "$BASE_URL/api/onetimepass"
private const val PERMANENTPASS_URL = "$BASE_URL/api/permanentpass"
private const val WIFISTATUS_URL = "$BASE_URL/api/wifistatus"
private const val POLL_INTERVAL_MS = 750L
private const val POLL_TIMEOUT_MS = 60_000L
private const val ARM_URL       = "$BASE_URL/api/arm"
private const val SCHEDULE_URL  = "$BASE_URL/api/schedule"
private const val ARMSTATUS_URL = "$BASE_URL/api/armstatus"

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

        handleAuthDeepLink(intent)

        hasLocationPermission = isLocationPermissionGranted()

        setContent {
            ESP32PairingAppTheme {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "home-security") return
        when (uri.host) {
            "auth-success" -> {
                val email = uri.getQueryParameter("email")
                val token = uri.getQueryParameter("token")
                if (!token.isNullOrBlank()) {
                    CloudBackendPrefs.setAuthToken(this, token)
                    Log.d("MainActivity", "Cloud auth token saved")
                }
                if (!email.isNullOrBlank()) {
                    CloudBackendPrefs.setDriveAccountEmail(this, email)
                    Log.d("MainActivity", "Google Drive connected as: $email")
                    Toast.makeText(this, "Google Drive connected as $email", Toast.LENGTH_LONG).show()
                } else if (token.isNullOrBlank()) {
                    Toast.makeText(this, "Google Drive connected", Toast.LENGTH_LONG).show()
                }
            }
            "auth-error" -> {
                val msg = uri.getQueryParameter("msg") ?: "OAuth failed"
                Toast.makeText(this, "Drive auth failed: $msg", Toast.LENGTH_LONG).show()
            }
        }
        intent.setData(null)
    }
}

@Composable
fun WifiManualScreen(httpClient: EspHttpClient) {
    var status by remember { mutableStateOf("Manual connection mode:\n" +
            "1) Connect your phone to the ESP32 Wi-Fi in Android Settings\n" +
            "2) Return here and tap \"Test Connection\"") }
    var showWifiDialog by remember { mutableStateOf(false) }
    var showStreamPage by remember { mutableStateOf(false) }
    var showSavedClipsScreen by remember { mutableStateOf(false) }
    var pendingOtp by remember { mutableStateOf("") }
    var showOtpDialog by remember { mutableStateOf(false) }
    var showScheduleSection by remember { mutableStateOf(false) }
    var showPermanentPassDialog by remember { mutableStateOf(false) }
    var permanentPass by remember { mutableStateOf("") }
    var permanentPassError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()


    if (showStreamPage) {
        StreamPage(
            network = null,
            httpClient = httpClient,
            onBack = { showStreamPage = false }
        )
        return
    }
    if (showSavedClipsScreen) {
        SavedClipsScreen(
            httpClient = httpClient,
            onBack = { showSavedClipsScreen = false }
        )
        return
    }

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
                                withContext(Dispatchers.IO) {
                                    httpClient.get(HEALTH_URL, network = null)
                                }
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

        Button(
            onClick = { showWifiDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send WiFi Credentials")
        }
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                pendingOtp = OtpGenerator.generate()
                showOtpDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate OTP")
        }
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                permanentPass = ""
                permanentPassError = null
                showPermanentPassDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set Permanent Password")
        }
        Spacer(Modifier.height(8.dp))

// Toggle schedule section visibility
        Button(
            onClick = { showScheduleSection = !showScheduleSection },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showScheduleSection) "Hide Schedule" else "Set Schedule")
        }

        if (showScheduleSection) {
            ScheduleSection(httpClient = httpClient, onStatus = { status = it })
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { showStreamPage = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Stream & Clips")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { showSavedClipsScreen = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Watch saved clips")
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
                                "• Step 3: Tap \"Test Connection\"\n" +
                                "• Step 4: Tap \"Send Wi-Fi Credentials\" to send your home Wi-Fi info\n\n" +
                                "If \"Test Connection\" fails, you're probably not connected to ESP32 Wi-Fi.",
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

                        val cleanedSsid = ssid.trim().replace(Regex("\\p{C}"), "")
                        val cleanedPass = password.trim().replace(Regex("\\p{C}"), "")
                        val encodedSsid = URLEncoder.encode(cleanedSsid, "UTF-8")
                        val encodedPass = URLEncoder.encode(cleanedPass, "UTF-8")

                        withContext(Dispatchers.IO) {
                            httpClient.post(
                                url = NEWSSID_URL,
                                body = "SSID=$encodedSsid",
                                contentType = "application/x-www-form-urlencoded",
                                network = null
                            )
                            httpClient.post(
                                url = NEWPASS_URL,
                                body = "pass=$encodedPass",
                                contentType = "application/x-www-form-urlencoded",
                                network = null
                            )
                            // Send encrypted password
                            val generatedPass = PasswordGenerator.generate()
                            httpClient.post(
                                url = ENCRYPTEDPASS_URL,
                                body = "pass=${URLEncoder.encode(generatedPass, "UTF-8")}",
                                contentType = "application/x-www-form-urlencoded",
                                network = null
                            )
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
    // OTP confirmation dialog
    if (showOtpDialog && pendingOtp.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { /* prevent accidental dismiss */ },
            title = { Text("SET OTP") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Your One-Time Password:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = pendingOtp,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Send this OTP to the ESP32?",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val otpToSend = pendingOtp  // capture before clearing
                    showOtpDialog = false
                    pendingOtp = ""
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                httpClient.post(
                                    url = ONETIMEPASS_URL,
                                    body = "otp=${URLEncoder.encode(otpToSend, "UTF-8")}",
                                    contentType = "application/x-www-form-urlencoded",
                                    network = null
                                )
                            }
                            status = "OTP sent successfully ✅"
                        } catch (e: Exception) {
                            status = "Failed to send OTP ❌: ${e.message}"
                        }
                    }
                }) { Text("Yes") }
            },
            dismissButton = {
                Button(onClick = {
                    showOtpDialog = false
                    pendingOtp = ""
                    status = "OTP cancelled. Resend credentials to generate a new OTP."
                }) { Text("No") }
            }
        )
    }

    if (showPermanentPassDialog) {
        val allowedChars = ('0'..'9').toSet() + ('A'..'D').toSet() + setOf('#', '*')

        AlertDialog(
            onDismissRequest = { showPermanentPassDialog = false },
            title = { Text("Set Permanent Password") },
            text = {
                Column {
                    Text(
                        text = "Allowed characters: 0-9, A, B, C, D, #, *",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = permanentPass,
                        onValueChange = { newValue ->
                            val upper = newValue.uppercase()
                            if (upper.length > 8) {
                                permanentPassError = "Password must be exactly 8 characters"
                            } else if (upper.all { ch -> ch in allowedChars }) {
                                permanentPass = upper
                                permanentPassError = null
                            } else {
                                permanentPassError = "Only 0-9, A-D, # and * are allowed"
                            }
                        },
                        label = { Text("Password") },
                        isError = permanentPassError != null,
                        supportingText = permanentPassError?.let { err -> { Text(err) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (permanentPass.length != 8) {
                            permanentPassError = "Password must be exactly 8 characters"
                            return@Button
                        }
                        val passToSend = permanentPass
                        showPermanentPassDialog = false
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    httpClient.post(
                                        url = PERMANENTPASS_URL,
                                        body = "pass=${URLEncoder.encode(passToSend, "UTF-8")}",
                                        contentType = "application/x-www-form-urlencoded",
                                        network = null
                                    )
                                }
                                status = "Permanent password set successfully ✅"
                            } catch (e: Exception) {
                                status = "Failed to set permanent password ❌: ${e.message}"
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Button(onClick = {
                    showPermanentPassDialog = false
                    permanentPass = ""
                    permanentPassError = null
                }) { Text("Cancel") }
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@Composable
fun StreamPage(
    network: Network?,
    httpClient: com.example.esp32pairingapp.network.EspHttpClient,
    onBack: () -> Unit
) {
    val deviceId = com.example.esp32pairingapp.network.ApiConfig.DEFAULT_DEVICE_ID
    var isLoadingStream by remember { mutableStateOf(false) }
    var isStreaming by remember { mutableStateOf(false) }
    var userStopped by remember { mutableStateOf(false) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var piBackendStatus by remember { mutableStateOf<String?>(null) }
    var hlsLive by remember { mutableStateOf<Boolean?>(null) }
    var driveAccountEmail by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Always poll cloud HLS status every 3s regardless of whether Start was pressed.
    // This means if the Pi is already streaming, the player appears without needing to tap Start.
    LaunchedEffect(deviceId) {
        while (true) {
            try {
                val statusUrl = com.example.esp32pairingapp.network.ApiConfig.getStreamStatusUrl(deviceId)
                val response = withContext(Dispatchers.IO) { httpClient.get(statusUrl, null) }
                val json = JSONObject(response)
                val live = json.optBoolean("live", false)
                hlsLive = live
                if (live && !isStreaming && !userStopped) isStreaming = true
                if (!live && isStreaming) {
                    isStreaming = false
                    hlsLive = false
                }
            } catch (e: Exception) {
                Log.d("StreamPage", "HLS status poll failed: ${e.message}")
            }
            delay(3000)
        }
    }

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

        // Check Pi backend connectivity.
        scope.launch {
            try {
                val piUrl = com.example.esp32pairingapp.network.ApiConfig.getPiHealthUrl()
                Log.d("StreamPage", "Checking Pi at: $piUrl")
                withContext(Dispatchers.IO) { httpClient.get(piUrl, null) }
                piBackendStatus = "✅ Pi backend reachable at $piUrl"
            } catch (e: Exception) {
                piBackendStatus = formatConnectionError(e, "Pi", com.example.esp32pairingapp.network.ApiConfig.getPiHealthUrl())
                Log.e("StreamPage", "Pi check failed", e)
            }
        }

        // Load saved Drive email; if we have a token, refresh from /api/auth/me
        driveAccountEmail = CloudBackendPrefs.getDriveAccountEmail(context)
        val token = CloudBackendPrefs.getAuthToken(context)
        if (!token.isNullOrBlank()) {
            try {
                val response = withContext(Dispatchers.IO) {
                    httpClient.get(com.example.esp32pairingapp.network.ApiConfig.getAuthMeUrl(), null, token)
                }
                val json = org.json.JSONObject(response)
                val email = json.optString("email", "").takeIf { it.isNotBlank() }
                if (email != null) {
                    driveAccountEmail = email
                    CloudBackendPrefs.setDriveAccountEmail(context, email)
                    Log.d("StreamPage", "Drive logged in as: $email")
                }
            } catch (_: Exception) { }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
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
                    text = if (network != null) "✅ Connected to ESP32 Network" else "📡 Backend",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Cloud: ${com.example.esp32pairingapp.network.ApiConfig.getCloudBaseUrl()}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pi: ${com.example.esp32pairingapp.network.ApiConfig.getPiBaseUrl()}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!driveAccountEmail.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Drive: logged in as $driveAccountEmail",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { showCloudDialog = true }) {
                        Text("Edit Cloud")
                    }
                    Button(onClick = { showPiDialog = true }) {
                        Text("Edit Pi")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val authUrl = com.example.esp32pairingapp.network.ApiConfig.getCloudBaseUrl() + "/auth/google"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Google Drive")
                }
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val token = CloudBackendPrefs.getAuthToken(context)
                                if (!token.isNullOrBlank()) {
                                    withContext(Dispatchers.IO) {
                                        httpClient.post(
                                            com.example.esp32pairingapp.network.ApiConfig.getAuthLogoutUrl(),
                                            "",
                                            "application/json",
                                            null,
                                            token
                                        )
                                    }
                                }
                                CloudBackendPrefs.setAuthToken(context, null)
                                CloudBackendPrefs.setDriveAccountEmail(context, null)
                                driveAccountEmail = null
                                errorMessage = "Logged out from Google Drive"
                                Log.d("StreamPage", "Logged out from Drive")
                            } catch (e: Exception) {
                                errorMessage = "Log out failed: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text("Log out from Google Drive")
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
                                    val response = withContext(Dispatchers.IO) {
                                        httpClient.post(
                                            url,
                                            """{"type":"webcam","value":""}""",
                                            "application/json",
                                            null
                                        )
                                    }

                                    Log.d("StreamPage", "Start response: $response")

                                    val json = JSONObject(response)
                                    val status = json.optString("status", "error")

                                    if (status == "ok" || status == "success") {
                                        userStopped = false
                                        isStreaming = true
                                        hlsLive = null
                                        streamUrl = com.example.esp32pairingapp.network.ApiConfig
                                            .getStreamPlaylistUrl(deviceId)
                                        errorMessage = "✅ Stream started. Waiting for segments..."
                                    } else {
                                        errorMessage = "⚠️ Start failed: ${json.optString("message", "Unknown error")}"
                                    }
                                } catch (e: Exception) {
                                    val errMsg = e.message ?: ""
                                    if (errMsg.contains("409") || errMsg.contains("already running", ignoreCase = true)) {
                                        // Stream already running on Pi — treat as success, the poller will pick it up
                                        userStopped = false
                                        isStreaming = true
                                        errorMessage = "✅ Stream already running. Waiting for cloud segments..."
                                    } else {
                                        errorMessage = formatConnectionError(e, "Pi", com.example.esp32pairingapp.network.ApiConfig.getStartStreamUrl())
                                    }
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
                                    val response = withContext(Dispatchers.IO) {
                                        httpClient.post(
                                            com.example.esp32pairingapp.network.ApiConfig.getStopStreamUrl(),
                                            "",
                                            "application/json",
                                            null
                                        )
                                    }

                                    Log.d("StreamPage", "Stop response: $response")

                                    userStopped = true
                                    isStreaming = false
                                    hlsLive = false
                                    streamUrl = null
                                    errorMessage = "⏹️ Stream stopped"
                                } catch (e: Exception) {
                                    errorMessage = formatConnectionError(e, "Pi", com.example.esp32pairingapp.network.ApiConfig.getStopStreamUrl())
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
                                val response = withContext(Dispatchers.IO) {
                                    httpClient.post(
                                        motionUrl,
                                        "",
                                        "application/json",
                                        null
                                    )
                                }

                                Log.d("StreamPage", "Motion response: $response")
                                val msg = runCatching { JSONObject(response).optString("message", "") }.getOrNull() ?: ""
                                if (msg.contains("cloud backend", ignoreCase = true)) {
                                    errorMessage = "❌ Wrong backend! Motion hit Cloud (3001) not Pi (4000). Tap Edit Pi and set Pi IP (port 4000)."
                                } else {
                                    errorMessage = "📸 Motion triggered! Clip will be saved to Google Drive. Tap \"Load Clips from Drive\" below to refresh the list."
                                }
                                delay(2000)
                            } catch (e: Exception) {
                                errorMessage = formatConnectionError(e, "Pi", com.example.esp32pairingapp.network.ApiConfig.getMotionTriggerUrl())
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

                if (isStreaming) {
                    Spacer(Modifier.height(12.dp))
                    when {
                        hlsLive == true -> {
                            val playlistUrl = com.example.esp32pairingapp.network.ApiConfig.getStreamPlaylistUrl(deviceId)
                            HlsPlayerView(
                                playlistUrl = playlistUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Waiting for stream...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        "Ensure Pi can reach Cloud (CLOUD_BASE_URL in config.json)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Recorded Clips Section (shared with Watch saved clips screen)
        SavedClipsContent(
            httpClient = httpClient,
            onError = { errorMessage = it.ifBlank { null } }
        )

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
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
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
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Converts connection exceptions into clear, actionable error messages.
 */
private fun formatConnectionError(e: Exception, target: String, url: String): String {
    val msg = (e.message ?: "").lowercase()
    val hint = when {
        msg.contains("failed to connect") || msg.contains("connection refused") ->
            "Check: 1) Phone on same WiFi as $target. 2) $target backend running. 3) Tap Edit ${if (target == "Pi") "Pi" else "Cloud"} to verify IP."
        msg.contains("network is unreachable") || msg.contains("no route to host") ->
            "Phone can't reach $target. Ensure phone is on same WiFi (e.g. 192.168.1.x)."
        msg.contains("timeout") || msg.contains("timed out") ->
            "Connection timed out. Is $target running? Check firewall allows port."
        msg.contains("unknown host") || msg.contains("unable to resolve") ->
            "Invalid host. Tap Edit ${if (target == "Pi") "Pi" else "Cloud"} and enter correct IP (e.g. 192.168.1.66)."
        else ->
            "Tap Edit ${if (target == "Pi") "Pi" else "Cloud"} to verify IP. Use IP only (no slash), e.g. 192.168.1.66."
    }
    return "❌ Can't reach $target at $url\n\n$hint"
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

@Composable
fun ScheduleSection(
    httpClient: EspHttpClient,
    onStatus: (String) -> Unit
) {
    var armTime by remember { mutableStateOf("") }
    var disarmTime by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text("Schedule", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))

            // Arm time input
            OutlinedTextField(
                value = armTime,
                onValueChange = { armTime = it },
                label = { Text("Arm Time (HH:MM)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Disarm time input
            OutlinedTextField(
                value = disarmTime,
                onValueChange = { disarmTime = it },
                label = { Text("Disarm Time (HH:MM)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Send schedule button
            Button(
                onClick = {
                    if (armTime.isBlank() || disarmTime.isBlank()) {
                        onStatus("Please enter both arm and disarm times.")
                        return@Button
                    }
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                httpClient.post(
                                    url = SCHEDULE_URL,
                                    body = "start=${URLEncoder.encode(armTime, "UTF-8")}" +
                                            "&stop=${URLEncoder.encode(disarmTime, "UTF-8")}",
                                    contentType = "application/x-www-form-urlencoded",
                                    network = null
                                )
                            }
                            onStatus("Schedule set ✅\nStart: $armTime | Stop: $disarmTime")
                        } catch (e: Exception) {
                            onStatus("Failed to set schedule ❌: ${e.message}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Schedule to ESP32")
            }
        }
    }
}

/**
 * Polls /api/wifistatus every ~750ms until ESP32 returns connected=true.
 * Stops on success, timeout, or coroutine cancellation.
 * When connected is true, calls onConnected (e.g. to send encrypted pass) then onStatus(true, ...).
 */
private suspend fun pollWifiStatus(
    httpClient: EspHttpClient,
    onStatus: (isConnected: Boolean, status: String) -> Unit,
    onConnected: suspend () -> Unit = {}
) {
    val startTime = System.currentTimeMillis()

    fun secondsElapsed(): Long = (System.currentTimeMillis() - startTime) / 1000

    while (currentCoroutineContext().isActive &&
        (System.currentTimeMillis() - startTime) < POLL_TIMEOUT_MS
    ) {
        try {
            val response = withContext(Dispatchers.IO) {
                httpClient.get(WIFISTATUS_URL, network = null)
            }

            val json = runCatching { JSONObject(response) }.getOrNull()

            if (json != null) {
                val connected = json.optBoolean("connected", false)
                val state = json.optString("state", "")
                val reason = json.optString("reason", "")
                val ip = json.optString("ip", "")

                if (connected) {
                    onConnected()
                    val extra = if (ip.isNotBlank()) "\nIP: $ip" else ""
                    onStatus(true, "Connected to home Wi-Fi ✅$extra")
                    return
                }

                val detail = when {
                    state.isNotBlank() -> state
                    reason.isNotBlank() -> "Error: $reason"
                    else -> "Connecting…"
                }

                onStatus(false, "Waiting for ESP32… ($detail)\nElapsed: ${secondsElapsed()}s")
            } else {
                val shortRaw = response.trim().take(80)
                onStatus(
                    false,
                    "Waiting for ESP32…\nStatus: $shortRaw\nElapsed: ${secondsElapsed()}s"
                )
            }

        } catch (_: Exception) {
            onStatus(false, "Waiting for ESP32…\nElapsed: ${secondsElapsed()}s")
        }

        delay(POLL_INTERVAL_MS)
    }

    onStatus(
        false,
        "Timeout ❌\nESP32 did not confirm home Wi-Fi within ${POLL_TIMEOUT_MS / 1000}s."
    )
}
