package com.example.esp32pairingapp.network

import com.example.esp32pairingapp.BuildConfig

object ApiConfig {
    // Base URLs from BuildConfig
    private val CLOUD_BASE_URL = BuildConfig.API_BASE_URL  // Cloud backend (port 3001)
    private val PI_BASE_URL = "http://10.0.2.2:4000"       // Pi backend (port 4000)

    // Cloud Backend Endpoints (port 3001)
    object CloudEndpoints {
        const val HEALTH = "/api/health"
        const val STATUS = "/status"
        const val EVENTS = "/api/events"
        const val CLIPS = "/api/clips"  // Alias for events
        const val EVENTS_UPLOAD = "/api/events/upload"
        const val CLEAR_ALL = "/api/clear-all"
        const val NEW_SSID = "/api/newssid"
        const val NEW_PASS = "/api/newpass"
        const val WIFI_STATUS = "/api/wifistatus"
    }

    // Pi Backend Endpoints (port 4000)
    object PiEndpoints {
        const val HEALTH = "/health"
        const val START = "/start"
        const val STOP = "/stop"
        const val MOTION = "/motion"
        const val STREAM = "/stream"
        const val CLEAR_ALL = "/clear-all"
    }

    // Cloud Backend URLs (port 3001)
    fun getHealthUrl() = "$CLOUD_BASE_URL${CloudEndpoints.HEALTH}"
    fun getStatusUrl() = "$CLOUD_BASE_URL${CloudEndpoints.STATUS}"
    fun getEventsUrl() = "$CLOUD_BASE_URL${CloudEndpoints.EVENTS}"
    fun getClipsUrl() = "$CLOUD_BASE_URL${CloudEndpoints.EVENTS}"  // Same as events
    fun getNewSsidUrl() = "$CLOUD_BASE_URL${CloudEndpoints.NEW_SSID}"
    fun getNewPassUrl() = "$CLOUD_BASE_URL${CloudEndpoints.NEW_PASS}"
    fun getWifiStatusUrl() = "$CLOUD_BASE_URL${CloudEndpoints.WIFI_STATUS}"
    fun getClearAllUrl() = "$CLOUD_BASE_URL${CloudEndpoints.CLEAR_ALL}"

    // Pi Backend URLs (port 4000)
    fun getPiHealthUrl() = "$PI_BASE_URL${PiEndpoints.HEALTH}"
    fun getStartStreamUrl() = "$PI_BASE_URL${PiEndpoints.START}"
    fun getStopStreamUrl() = "$PI_BASE_URL${PiEndpoints.STOP}"
    fun getMotionTriggerUrl() = "$PI_BASE_URL${PiEndpoints.MOTION}"
    fun getStreamUrl() = "$PI_BASE_URL${PiEndpoints.STREAM}"
    fun getPiClearAllUrl() = "$PI_BASE_URL${PiEndpoints.CLEAR_ALL}"

    // For custom endpoints
    fun buildCloudUrl(endpoint: String) = "$CLOUD_BASE_URL$endpoint"
    fun buildPiUrl(endpoint: String) = "$PI_BASE_URL$endpoint"

    // Get base URLs (useful for debugging)
    fun getCloudBaseUrl() = CLOUD_BASE_URL
    fun getPiBaseUrl() = PI_BASE_URL
}