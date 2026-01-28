package com.example.esp32sensormanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.esp32sensormanager.ui.vm.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailScreen(
    vm: AppViewModel,
    sensorId: String,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val sensor = remember(state.sensors, sensorId) { vm.getSensor(sensorId) }

    if (sensor == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sensor") },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).padding(16.dp)) {
                Text("Sensor not found.")
            }
        }
        return
    }

    var newName by remember { mutableStateOf(sensor.name) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sensor.name) },
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Details", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("SSID: ${sensor.ssid}")
                    Text(if (sensor.paired) "Status: Paired" else "Status: Not paired")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Rename", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { vm.renameSensor(sensor.id, newName) },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }

                Spacer(Modifier.width(12.dp))

                OutlinedButton(
                    onClick = { vm.removeSensor(sensor.id); onBack() },
                    modifier = Modifier.weight(1f)
                ) { Text("Delete") }
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
