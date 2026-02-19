package com.example.esp32pairingapp.network

object ApiConfig {
    // Default base URLs.
    // Cloud backend (port 3001). 10.0.2.2 maps to the host machine when running on Android emulator.
    // On a real device you'll typically override this at runtime via setCloudBaseUrlOverride().
    private val CLOUD_BASE_URL_DEFAULT = "http://10.0.2.2:3001"

    // Pi backend (port 4000)
    private val PI_BASE_URL_DEFAULT = "http://10.0.2.2:4000"

    // Runtime overrides (e.g., user-entered IP/host)
    @Volatile
    private var cloudBaseUrlOverride: String? = null

    @Volatile
    private var piBaseUrlOverride: String? = null

    fun setCloudBaseUrlOverride(baseUrl: String?) {
        cloudBaseUrlOverride = baseUrl?.trim()?.removeSuffix("/")
    }

    fun setPiBaseUrlOverride(baseUrl: String?) {
        piBaseUrlOverride = baseUrl?.trim()?.removeSuffix("/")
    }

    private fun cloudBaseUrl(): String = cloudBaseUrlOverride ?: CLOUD_BASE_URL_DEFAULT
    private fun piBaseUrl(): String = piBaseUrlOverride ?: PI_BASE_URL_DEFAULT

    // Cloud Backend Endpoints (port 3001)
    object CloudEndpoints {
        const val HEALTH = "/api/health"
        const val STATUS = "/status"
        const val EVENTS = "/api/events"
        const val CLIPS = "/api/clips"
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
        const val STATUS = "/status"
    }

    // Cloud Backend URLs (port 3001)
    fun getHealthUrl() = "${cloudBaseUrl()}${CloudEndpoints.HEALTH}"
    fun getStatusUrl() = "${cloudBaseUrl()}${CloudEndpoints.STATUS}"

    /** GET /api/events */
    fun getEventsUrl() = "${cloudBaseUrl()}${CloudEndpoints.EVENTS}"

    /** GET /api/clips/:eventId */
    fun getClipStreamUrl(eventId: String) = "${cloudBaseUrl()}${CloudEndpoints.CLIPS}/$eventId"

    /** GET /api/clips/:eventId/thumbnail */
    fun getClipThumbnailUrl(eventId: String) = "${cloudBaseUrl()}${CloudEndpoints.CLIPS}/$eventId/thumbnail"

    // Backwards-compatible alias (older UI name)
    fun getClipsUrl() = getEventsUrl()
    fun getNewSsidUrl() = "${cloudBaseUrl()}${CloudEndpoints.NEW_SSID}"
    fun getNewPassUrl() = "${cloudBaseUrl()}${CloudEndpoints.NEW_PASS}"
    fun getWifiStatusUrl() = "${cloudBaseUrl()}${CloudEndpoints.WIFI_STATUS}"
    fun getClearAllUrl() = "${cloudBaseUrl()}${CloudEndpoints.CLEAR_ALL}"

    // Pi Backend URLs (port 4000)
    fun getPiHealthUrl() = "${piBaseUrl()}${PiEndpoints.HEALTH}"
    fun getStartStreamUrl() = "${piBaseUrl()}${PiEndpoints.START}"
    fun getStopStreamUrl() = "${piBaseUrl()}${PiEndpoints.STOP}"
    fun getMotionTriggerUrl() = "${piBaseUrl()}${PiEndpoints.MOTION}"
    fun getStreamUrl() = "${piBaseUrl()}${PiEndpoints.STREAM}"
    fun getPiClearAllUrl() = "${piBaseUrl()}${PiEndpoints.CLEAR_ALL}"
    fun getPiStatusUrl() = "${piBaseUrl()}${PiEndpoints.STATUS}"

    // For custom endpoints
    fun buildCloudUrl(endpoint: String) = "${cloudBaseUrl()}$endpoint"
    fun buildPiUrl(endpoint: String) = "${piBaseUrl()}$endpoint"

    // Get base URLs (useful for debugging)
    fun getCloudBaseUrl() = cloudBaseUrl()
    fun getPiBaseUrl() = piBaseUrl()
}