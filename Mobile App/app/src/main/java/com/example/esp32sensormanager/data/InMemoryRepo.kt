package com.example.esp32sensormanager.data

import java.util.UUID

class InMemoryRepo {

    private var master = MasterDevice(ssid = "", paired = false)
    private val sensors = mutableListOf<SensorDevice>()

    fun getMaster(): MasterDevice = master
    fun getSensors(): List<SensorDevice> = sensors.toList()

    fun pairMaster(ssid: String, password: String): Result<MasterDevice> {
        if (ssid.isBlank()) return Result.failure(IllegalArgumentException("SSID required"))
        if (password.length < 8) return Result.failure(IllegalArgumentException("Password must be 8+ chars"))
        master = MasterDevice(ssid = ssid.trim(), paired = true)
        return Result.success(master)
    }

    fun addSensor(sensorSsid: String, sensorName: String): Result<SensorDevice> {
        if (!master.paired) return Result.failure(IllegalStateException("Pair master first"))
        if (sensorSsid.isBlank()) return Result.failure(IllegalArgumentException("Sensor SSID required"))
        if (sensorName.isBlank()) return Result.failure(IllegalArgumentException("Sensor name required"))

        val sensor = SensorDevice(
            id = UUID.randomUUID().toString(),
            ssid = sensorSsid.trim(),
            name = sensorName.trim(),
            paired = true
        )
        sensors.add(sensor)
        return Result.success(sensor)
    }

    fun renameSensor(sensorId: String, newName: String): Result<Unit> {
        if (newName.isBlank()) return Result.failure(IllegalArgumentException("Name required"))
        val idx = sensors.indexOfFirst { it.id == sensorId }
        if (idx < 0) return Result.failure(IllegalArgumentException("Sensor not found"))
        sensors[idx] = sensors[idx].copy(name = newName.trim())
        return Result.success(Unit)
    }

    fun removeSensor(sensorId: String): Result<Unit> {
        val removed = sensors.removeIf { it.id == sensorId }
        return if (removed) Result.success(Unit)
        else Result.failure(IllegalArgumentException("Sensor not found"))
    }

    fun getSensor(sensorId: String): SensorDevice? = sensors.firstOrNull { it.id == sensorId }
}
