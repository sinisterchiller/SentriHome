package com.example.esp32sensormanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.esp32sensormanager.ui.vm.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairMasterScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    var ssid by remember { mutableStateOf("SP32_Master_Config") }
    var password by remember { mutableStateOf("12345678") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair Master") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Enter the Master ESP32 AP details.")
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("Master SSID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Master Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.pairMaster(ssid, password) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pair Master")
            }

            state.message?.let { msg ->
                Spacer(Modifier.height(12.dp))
                AssistChip(
                    onClick = { vm.clearMessage() },
                    label = { Text(msg) }
                )
            }
        }
    }
}
