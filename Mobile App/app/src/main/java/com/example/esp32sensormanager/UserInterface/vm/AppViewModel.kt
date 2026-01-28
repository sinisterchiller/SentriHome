package com.example.esp32sensormanager.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.esp32sensormanager.data.EspRepository
import com.example.esp32sensormanager.data.MasterDevice
import com.example.esp32sensormanager.data.SensorDevice
import com.example.esp32sensormanager.data.esp.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val master: MasterDevice = MasterDevice("", false),
    val sensors: List<SensorDevice> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false
)

class AppViewModel : ViewModel() {

    private val repo = EspRepository(HttpClient.api)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun pairMaster(masterSsid: String, masterPassword: String) {
        _state.update { it.copy(busy = true, message = null) }

        viewModelScope.launch {
            val phoneId = UUID.randomUUID().toString() // later: persistent id
            val res = repo.pairMaster(masterSsid, masterPassword, phoneId)

            _state.update { st ->
                if (res.isSuccess) {
                    st.copy(
                        busy = false,
                        master = MasterDevice(ssid = masterSsid, paired = true),
                        message = "Master paired (HTTP). Secret stored."
                    )
                } else {
                    st.copy(
                        busy = false,
                        message = res.exceptionOrNull()?.message ?: "Pair failed"
                    )
                }
            }
        }
    }

    fun addSensor(sensorSsid: String, sensorName: String) {
        _state.update { it.copy(busy = true, message = null) }

        viewModelScope.launch {
            val res = repo.addSensor(sensorSsid, sensorName)
            _state.update { st ->
                if (res.isSuccess) {
                    val sensorId = res.getOrThrow().sensorId ?: UUID.randomUUID().toString()
                    val newSensor = SensorDevice(
                        id = sensorId,
                        ssid = sensorSsid,
                        name = sensorName,
                        paired = true
                    )
                    st.copy(
                        busy = false,
                        sensors = st.sensors + newSensor,
                        message = "Sensor paired (HTTP)."
                    )
                } else {
                    st.copy(
                        busy = false,
                        message = res.exceptionOrNull()?.message ?: "Add sensor failed"
                    )
                }
            }
        }
    }

    fun renameSensor(sensorId: String, newName: String) {
        _state.update { it.copy(busy = true, message = null) }

        viewModelScope.launch {
            val res = repo.renameSensor(sensorId, newName)
            _state.update { st ->
                if (res.isSuccess) {
                    st.copy(
                        busy = false,
                        sensors = st.sensors.map { if (it.id == sensorId) it.copy(name = newName) else it },
                        message = "Renamed (HTTP)."
                    )
                } else {
                    st.copy(
                        busy = false,
                        message = res.exceptionOrNull()?.message ?: "Rename failed"
                    )
                }
            }
        }
    }

    fun removeSensor(sensorId: String) {
        // Optional endpoint later; for now local removal
        _state.update { st ->
            st.copy(
                sensors = st.sensors.filterNot { it.id == sensorId },
                message = "Removed locally."
            )
        }
    }

    fun getSensor(sensorId: String): SensorDevice? =
        _state.value.sensors.firstOrNull { it.id == sensorId }
}
