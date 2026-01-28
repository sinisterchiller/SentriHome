package com.example.esp32sensormanager.data.esp

data class PairMasterRequest(
    val masterSsid: String,
    val masterPassword: String,
    val phoneDeviceId: String
)

data class PairMasterResponse(
    val ok: Boolean,
    val masterId: String? = null,
    val masterSecret: String? = null,
    val message: String? = null
)

data class AddSensorRequest(
    val sensorSsid: String,
    val sensorName: String,
    val masterSecret: String
)

data class AddSensorResponse(
    val ok: Boolean,
    val sensorId: String? = null,
    val message: String? = null
)

data class RenameSensorRequest(
    val sensorId: String,
    val newName: String,
    val masterSecret: String
)

data class GenericResponse(
    val ok: Boolean,
    val message: String? = null
)
