package com.example.esp32pairingapp.network

import android.net.Network
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP client that can make requests through a specific Network.
 * Required for accessing ESP32 web server at 192.168.10.1 through the ESP32 WiFi network.
 */
class EspHttpClient {
    
    /**
     * Make a GET request through the specified network.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    suspend fun get(url: String, network: Network? = null): String = withContext(Dispatchers.IO) {
        val urlObj = URL(url)
        val connection = if (network != null) {
            network.openConnection(urlObj) as HttpURLConnection
        } else {
            urlObj.openConnection() as HttpURLConnection
        }
        
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                throw Exception("HTTP $responseCode: ${connection.responseMessage}")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Make a POST request through the specified network.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    suspend fun post(
        url: String, 
        body: String, 
        contentType: String = "application/json",
        network: Network? = null
    ): String = withContext(Dispatchers.IO) {
        val urlObj = URL(url)
        val connection = if (network != null) {
            network.openConnection(urlObj) as HttpURLConnection
        } else {
            urlObj.openConnection() as HttpURLConnection
        }
        
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            
            // Write body
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                throw Exception("HTTP $responseCode: ${connection.responseMessage}")
            }
        } finally {
            connection.disconnect()
        }
    }
}