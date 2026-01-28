package com.example.esp32sensormanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.esp32sensormanager.ui.vm.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onGoPairMaster: () -> Unit,
    onGoAddSensor: () -> Unit,
    onOpenSensor: (String) -> Unit
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state.message) {
        // auto-clear snack state later if you want
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ESP32 Sensor Manager") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onGoAddSensor) {
                Text("+")
            }
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
                    Text("Master", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.master.paired) "Paired to: ${state.master.ssid}"
                        else "Not paired"
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGoPairMaster) {
                        Text(if (state.master.paired) "Re-pair Master" else "Pair Master")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Sensors", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (state.sensors.isEmpty()) {
                Text(
                    if (state.master.paired) "No sensors paired yet. Tap + to add one."
                    else "Pair the master first, then add sensors."
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.sensors) { sensor ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onOpenSensor(sensor.id) }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(sensor.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text("SSID: ${sensor.ssid}")
                                Text(if (sensor.paired) "Status: Paired" else "Status: Not paired")
                            }
                        }
                    }
                }
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
