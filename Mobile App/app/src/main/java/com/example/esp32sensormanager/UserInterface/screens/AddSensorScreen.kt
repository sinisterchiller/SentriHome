package com.example.esp32sensormanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.esp32sensormanager.ui.vm.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSensorScreen(
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    var sensorSsid by remember { mutableStateOf("") }
    var sensorName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Sensor") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            if (!state.master.paired) {
                Text("You must pair the master first.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Go Back") }
                return@Column
            }

            Text("Enter the sensor AP details and a friendly name.")
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = sensorSsid,
                onValueChange = { sensorSsid = it },
                label = { Text("Sensor SSID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = sensorName,
                onValueChange = { sensorName = it },
                label = { Text("Sensor Name (e.g., Front Door Motion)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.addSensor(sensorSsid, sensorName) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pair Sensor")
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
