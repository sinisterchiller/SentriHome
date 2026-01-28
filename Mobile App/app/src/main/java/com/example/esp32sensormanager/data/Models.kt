package com.example.esp32sensormanager.data

data class MasterDevice(
    val ssid: String,
    val paired: Boolean,
    val displayName: String = "Master ESP"
)

data class SensorDevice(
    val id: String,
    val ssid: String,
    val name: String,
    val paired: Boolean
)
