package com.example.esp32pairingapp.network

import android.content.Context

/**
 * Minimal SharedPreferences-backed config for the Cloud backend address.
 *
 * User can enter either:
 *  - 192.168.1.50
 *  - http://192.168.1.50
 *  - https://example.com
 *
 * We normalize to: http(s)://host:3001
 */
object CloudBackendPrefs {
    private const val PREFS_NAME = "cloud_backend_prefs"
    private const val KEY_CLOUD_HOST_INPUT = "cloud_host_input"
    private const val KEY_DRIVE_ACCOUNT_EMAIL = "drive_account_email"
    private const val KEY_AUTH_TOKEN = "cloud_auth_token"

    fun getRawHostInput(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CLOUD_HOST_INPUT, null)
    }

    fun setRawHostInput(context: Context, raw: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CLOUD_HOST_INPUT, raw.trim()).apply()
    }

    fun getDriveAccountEmail(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DRIVE_ACCOUNT_EMAIL, null)?.takeIf { it.isNotBlank() }
    }

    fun setDriveAccountEmail(context: Context, email: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DRIVE_ACCOUNT_EMAIL, email ?: "").apply()
    }

    fun getAuthToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_AUTH_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    fun setAuthToken(context: Context, token: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AUTH_TOKEN, token ?: "").apply()
    }

    /** True if we have a token (user is logged in for cloud/Drive). */
    fun isLoggedIn(context: Context): Boolean = !getAuthToken(context).isNullOrBlank()

    /** Returns a normalized base URL like http://192.168.1.50:3001 */
    fun computeCloudBaseUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        require(trimmed.isNotEmpty()) { "Cloud backend host/IP is empty" }

        // Add scheme if missing
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        // Remove any trailing slash
        val noTrailingSlash = withScheme.removeSuffix("/")

        // If they already included a port, strip it, then force 3001.
        // This keeps input forgiving and matches your API doc.
        val schemeSplit = noTrailingSlash.split("://", limit = 2)
        val scheme = schemeSplit[0]
        val hostAndMaybePort = schemeSplit.getOrNull(1) ?: ""

        // hostAndMaybePort might include path. We only want the host portion.
        val hostPortAndPathSplit = hostAndMaybePort.split("/", limit = 2)
        val hostPort = hostPortAndPathSplit[0]

        val hostOnly = hostPort.substringBefore(":")
        require(hostOnly.isNotBlank()) { "Invalid host/IP" }

        return "$scheme://$hostOnly:3001"
    }
}
