package com.example.esp32pairingapp.network

import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Binds the app's network traffic to a specific Network.
 * This is required to route HTTP requests through the ESP32 WiFi network
 * instead of the device's primary internet connection.
 */
class NetworkBinder {
    
    private var boundNetwork: Network? = null
    
    /**
     * Bind all app traffic to the specified network.
     * This ensures HTTP requests to 192.168.10.1 go through the ESP32 WiFi.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun bindProcessToNetwork(network: Network): Boolean {
        val success = ConnectivityManager.setProcessDefaultNetwork(network)
        if (success) {
            boundNetwork = network
        }
        return success
    }
    
    /**
     * Unbind from the network, restoring default routing.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun unbindProcessFromNetwork() {
        ConnectivityManager.setProcessDefaultNetwork(null)
        boundNetwork = null
    }
    
    /**
     * Get the currently bound network, if any.
     */
    fun getBoundNetwork(): Network? = boundNetwork
}