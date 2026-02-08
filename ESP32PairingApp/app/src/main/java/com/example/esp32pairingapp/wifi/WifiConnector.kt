package com.example.esp32pairingapp.wifi

import android.content.Context
import android.net.*
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.Inet4Address
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WifiConnector(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Connect to ESP32 AP on Android 10+.
     *
     * Improvements vs your previous version:
     * - waits until the network has an IP address (link properties)
     * - retries automatically if Android drops the connection
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun connectAndroid10Plus(
        ssid: String,
        password: String,
        timeoutMs: Long = 30_000L,
        attempts: Int = 3
    ): Network = withContext(Dispatchers.Main) {

        var lastError: Throwable? = null

        repeat(attempts) { attemptIndex ->
            try {
                // Backoff between attempts (0ms, 600ms, 1200ms...)
                if (attemptIndex > 0) delay(600L * attemptIndex)

                return@withContext connectOnce(
                    ssid = ssid,
                    password = password,
                    timeoutMs = timeoutMs
                )
            } catch (e: Throwable) {
                lastError = e
                // Clean up before retrying
                disconnect()
            }
        }

        throw RuntimeException(
            "Failed to connect to $ssid after $attempts attempts: ${lastError?.message}",
            lastError
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun connectOnce(
        ssid: String,
        password: String,
        timeoutMs: Long
    ): Network {

        // Cancel any previous request
        disconnect()

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            // Important: ESP32 AP typically has NO internet. Removing INTERNET capability
            // reduces the chance Android rejects it for "no internet".
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->

                var lastLinkProps: LinkProperties? = null

                val callback = object : ConnectivityManager.NetworkCallback() {

                    override fun onAvailable(network: Network) {
                        // onAvailable can fire before DHCP/IP is ready.
                        // We wait for onLinkPropertiesChanged to confirm IP.
                    }

                    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                        lastLinkProps = linkProperties

                        // Check that we have an IPv4 address (most ESP32 AP setups)
                        val hasIpv4 = linkProperties.linkAddresses.any {
                            it.address is Inet4Address
                        }

                        if (hasIpv4 && cont.isActive) {
                            activeCallback = this
                            cont.resume(network)
                        }
                    }

                    override fun onUnavailable() {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                RuntimeException("Wi-Fi network unavailable (SSID not found or wrong password).")
                            )
                        }
                    }

                    override fun onLost(network: Network) {
                        // If we lose it before we resume success, treat as failure.
                        if (cont.isActive) {
                            val msg = buildString {
                                append("Wi-Fi disconnected before it stabilized")
                                lastLinkProps?.let { lp ->
                                    append(" (linkProps had ${lp.linkAddresses.size} addresses)")
                                }
                            }
                            cont.resumeWithException(RuntimeException(msg))
                        }
                    }
                }

                activeCallback = callback
                connectivityManager.requestNetwork(request, callback)

                cont.invokeOnCancellation {
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
            // ignore
        } finally {
            activeCallback = null
        }
    }
}
