package com.example.esp32pairingapp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/**
 * Binds THIS APP's network traffic to a specific Network.
 * Required so HTTP requests route through the ESP32 WiFi network
 * instead of the device's primary internet connection.
 */
class NetworkBinder(context: Context) {

    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var boundNetwork: Network? = null

    /**
     * Bind all app traffic to the specified network.
     */
    fun bindTo(network: Network): Boolean {
        val success = cm.bindProcessToNetwork(network)
        if (success) {
            boundNetwork = network
        }
        return success
    }

    /**
     * Unbind from the network, restoring default routing.
     */
    fun unbind() {
        cm.bindProcessToNetwork(null)
        boundNetwork = null
    }

    /**
     * Get the currently bound network, if any.
     */
    fun getBoundNetwork(): Network? = boundNetwork
}
