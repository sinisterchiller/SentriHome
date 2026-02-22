package com.example.esp32pairingapp.setup

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.esp32pairingapp.network.EspHttpClient
import com.example.esp32pairingapp.network.EspSetupPrefs
import com.example.esp32pairingapp.pairing.PasswordGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

private const val ESP_BASE = "http://192.168.10.1"
private const val HEALTH_URL = "$ESP_BASE/api/health"
private const val PERMANENTPASS_URL = "$ESP_BASE/api/permanentpass"
private const val ENCRYPTEDPASS_URL = "$ESP_BASE/api/encryptedpass"
private const val MAINCONNECTION_URL = "$ESP_BASE/api/mainconnection"

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@Composable
fun EspMainSetupScreen(
    httpClient: EspHttpClient,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Step tracking: 0 = connect, 1 = permanent pass, 2 = random pass, 3 = module pair, 4 = done
    var currentStep by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }

    // Step 0: Connection
    var connectionOk by remember { mutableStateOf(false) }

    // Step 1: Permanent password
    var permanentPass by remember { mutableStateOf("") }
    var permanentPassError by remember { mutableStateOf<String?>(null) }
    var permanentPassSent by remember { mutableStateOf(false) }

    // Step 2: Random password
    var randomPass by remember { mutableStateOf("") }
    var randomPassSent by remember { mutableStateOf(false) }

    // Step 3: Module pairing
    var modulePaired by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }

    // Load previously saved random password if it exists
    LaunchedEffect(Unit) {
        EspSetupPrefs.getSavedRandomPassword(context)?.let { saved ->
            randomPass = saved
        }
    }

    val stepLabels = listOf("Connect", "Permanent Pass", "Random Pass", "Pair Module", "Done")
    val allowedChars = ('0'..'9').toSet() + ('A'..'D').toSet() + setOf('#', '*')

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("ESP Main Setup") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Step indicator
                StepIndicator(currentStep = currentStep, labels = stepLabels)

                Spacer(Modifier.height(4.dp))

                // Status card
                if (status.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // ── Step 0: Connect to ESP Main ──
                StepCard(
                    stepNumber = 0,
                    title = "Connect to ESP Main",
                    subtitle = "Make sure your phone is on the same Home Wi-Fi as the ESP Main hardware, then test the connection.",
                    isActive = currentStep == 0,
                    isComplete = connectionOk
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                status = "Testing ESP Main connection..."
                                try {
                                    val resp = withContext(Dispatchers.IO) {
                                        httpClient.get(HEALTH_URL, network = null)
                                    }
                                    connectionOk = true
                                    status = "ESP Main connected ✅"
                                    currentStep = 1
                                } catch (e: Exception) {
                                    connectionOk = false
                                    status = "Connection failed ❌: ${e.message}\n\nMake sure you're on the same Wi-Fi network as the ESP."
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && !connectionOk,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (connectionOk) "Connected ✅" else "Test Connection")
                    }
                }

                // ── Step 1: Set Permanent Password ──
                StepCard(
                    stepNumber = 1,
                    title = "Set Permanent Password",
                    subtitle = "Enter an 8-character password using: 0-9, A, B, C, D, #, *",
                    isActive = currentStep == 1,
                    isComplete = permanentPassSent
                ) {
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
                        label = { Text("Permanent Password") },
                        isError = permanentPassError != null,
                        supportingText = {
                            Text(permanentPassError ?: "${permanentPass.length}/8 characters")
                        },
                        singleLine = true,
                        enabled = !permanentPassSent,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (permanentPass.length != 8) {
                                permanentPassError = "Password must be exactly 8 characters"
                                return@Button
                            }
                            scope.launch {
                                isLoading = true
                                status = "Sending permanent password..."
                                try {
                                    withContext(Dispatchers.IO) {
                                        httpClient.post(
                                            url = PERMANENTPASS_URL,
                                            body = "pass=${URLEncoder.encode(permanentPass, "UTF-8")}",
                                            contentType = "application/x-www-form-urlencoded",
                                            network = null
                                        )
                                    }
                                    permanentPassSent = true
                                    status = "Permanent password set ✅"
                                    currentStep = 2
                                } catch (e: Exception) {
                                    status = "Failed to set permanent password ❌: ${e.message}"
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && !permanentPassSent && permanentPass.length == 8,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (permanentPassSent) "Saved ✅" else "Save Permanent Password")
                    }
                }

                // ── Step 2: Generate & Send Random Password ──
                StepCard(
                    stepNumber = 2,
                    title = "Random Password",
                    subtitle = "Generate a random password, send it to the ESP, and save it to your phone for module pairing.",
                    isActive = currentStep == 2,
                    isComplete = randomPassSent
                ) {
                    if (randomPass.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = randomPass,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { randomPass = PasswordGenerator.generate() },
                            enabled = !randomPassSent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Generate")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    status = "Sending random password to ESP..."
                                    try {
                                        withContext(Dispatchers.IO) {
                                            httpClient.post(
                                                url = ENCRYPTEDPASS_URL,
                                                body = "pass=${URLEncoder.encode(randomPass, "UTF-8")}",
                                                contentType = "application/x-www-form-urlencoded",
                                                network = null
                                            )
                                        }
                                        EspSetupPrefs.setSavedRandomPassword(context, randomPass)
                                        randomPassSent = true
                                        status = "Random password sent & saved to phone ✅"
                                        currentStep = 3
                                    } catch (e: Exception) {
                                        status = "Failed to send random password ❌: ${e.message}"
                                    }
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading && !randomPassSent && randomPass.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (randomPassSent) "Sent ✅" else "Send & Save")
                        }
                    }
                }

                // ── Step 3: Pair Module ──
                StepCard(
                    stepNumber = 3,
                    title = "Pair Module",
                    subtitle = "Go to your phone's Wi-Fi settings and connect to the ESP32 Module network. Then return here and tap Start Pairing.",
                    isActive = currentStep == 3,
                    isComplete = modulePaired
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                status = "Pairing module..."
                                try {
                                    val passToSend = EspSetupPrefs.getSavedRandomPassword(context)
                                        ?: randomPass
                                    withContext(Dispatchers.IO) {
                                        httpClient.post(
                                            url = MAINCONNECTION_URL,
                                            body = "pass=${URLEncoder.encode(passToSend, "UTF-8")}",
                                            contentType = "application/x-www-form-urlencoded",
                                            network = null
                                        )
                                    }
                                    modulePaired = true
                                    status = "Module paired successfully ✅"
                                    currentStep = 4
                                } catch (e: Exception) {
                                    status = "Module pairing failed ❌: ${e.message}\n\nMake sure you're connected to the Module Wi-Fi."
                                }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading && !modulePaired,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (modulePaired) "Paired ✅" else "Start Pairing")
                    }
                }

                // ── Step 4: Done ──
                if (currentStep == 4) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Setup Complete!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("✅ ESP Main connected")
                            Text("✅ Permanent password set")
                            Text("✅ Random password saved")
                            Text("✅ Module paired")
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                                Text("Back to Home")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // Loading overlay
            if (isLoading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(status.lines().first())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, labels: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, label ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when {
                        index < currentStep -> MaterialTheme.colorScheme.primary
                        index == currentStep -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (index < currentStep) "✓" else "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                index < currentStep -> MaterialTheme.colorScheme.onPrimary
                                index == currentStep -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index <= currentStep)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isActive: Boolean,
    isComplete: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isComplete -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                isActive -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 2.dp else 0.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isComplete) "✅" else "Step ${stepNumber + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isActive || isComplete) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isActive && !isComplete) {
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}
