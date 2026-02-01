package com.example.esp32pairingapp.wifi

import android.content.Context
import android.net.*
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WifiConnector(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Connect to a WPA2/WPA3 AP with SSID + password (Android 10+).
     *
     * Returns the Network that became available. You should later bind to it using NetworkBinder.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun connectAndroid10Plus(
        ssid: String,
        password: String,
        timeoutMs: Long = 30_000L
    ): Network = withContext(Dispatchers.Main) {

        // Cancel any previous request
        disconnect()

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            // optional: ask for internet capability? we do NOT need internet for ESP32 AP
            .build()

        withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        // Connected to the requested Wi-Fi network
                        activeCallback = this
                        if (cont.isActive) cont.resume(network)
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) {
                            cont.resumeWithException(RuntimeException("Wi-Fi network unavailable (SSID not found or wrong password)."))
                        }
                    }

                    override fun onLost(network: Network) {
                        // Network lost after it was available — you may handle this in PairingManager later
                    }
                }

                activeCallback = callback
                connectivityManager.requestNetwork(request, callback)

                cont.invokeOnCancellation {
                    // If coroutine is cancelled, clean up the network request
                    disconnect()
                }
            }
        }
    }

    /**
     * Disconnect/stop the active requestNetwork call.
     * For Android 10+ specifier networks, connection lasts only while the request is active.
     */
    fun disconnect() {
        val cb = activeCallback ?: return
        try {
            connectivityManager.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
            // ignore (already unregistered)
        } finally {
            activeCallback = null
        }
    }
}