package com.example.esp32sensormanager.data

import com.example.esp32sensormanager.data.esp.*

class EspRepository(private val api: EspApi) {

    // store in memory for now; later use EncryptedSharedPreferences
    private var masterSecret: String? = null
    private var masterId: String? = null

    fun getSecret(): String? = masterSecret
    fun getMasterId(): String? = masterId

    suspend fun pairMaster(masterSsid: String, masterPassword: String, phoneDeviceId: String): Result<PairMasterResponse> {
        return runCatching {
            val res = api.pairMaster(
                PairMasterRequest(masterSsid, masterPassword, phoneDeviceId)
            )
            if (!res.ok) error(res.message ?: "Pair master failed")
            masterSecret = res.masterSecret
            masterId = res.masterId
            res
        }
    }

    suspend fun addSensor(sensorSsid: String, sensorName: String): Result<AddSensorResponse> {
        return runCatching {
            val secret = masterSecret ?: error("Master not paired yet")
            val res = api.addSensor(AddSensorRequest(sensorSsid, sensorName, secret))
            if (!res.ok) error(res.message ?: "Add sensor failed")
            res
        }
    }

    suspend fun renameSensor(sensorId: String, newName: String): Result<Unit> {
        return runCatching {
            val secret = masterSecret ?: error("Master not paired yet")
            val res = api.renameSensor(RenameSensorRequest(sensorId, newName, secret))
            if (!res.ok) error(res.message ?: "Rename failed")
        }
    }
}
