package com.example.esp32pairingapp.network

import android.net.Network
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class EspHttpClient {

    /**
     * Performs HTTP GET request
     * @param url The full URL to request
     * @param network Optional network to bind request to (for ESP32 direct connection)
     * @return Response body as String
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun get(url: String, network: Network? = null): String {
        Log.d("EspHttpClient", "GET request to: $url")

        val urlObj = URL(url)
        val connection = if (network != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            network.openConnection(urlObj) as HttpURLConnection
        } else {
            urlObj.openConnection() as HttpURLConnection
        }

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            Log.d("EspHttpClient", "Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                Log.d("EspHttpClient", "Response: ${response.take(200)}")
                response
            } else {
                throw Exception("HTTP $responseCode: ${connection.responseMessage}")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Performs HTTP POST request
     * @param url The full URL to request
     * @param body The request body (JSON string)
     * @param contentType Content-Type header (default: application/json)
     * @param network Optional network to bind request to (for ESP32 direct connection)
     * @return Response body as String
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun post(url: String, body: String = "", contentType: String = "application/json", network: Network? = null): String {
        Log.d("EspHttpClient", "POST request to: $url")
        Log.d("EspHttpClient", "Body: $body")

        val urlObj = URL(url)
        val connection = if (network != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            network.openConnection(urlObj) as HttpURLConnection
        } else {
            urlObj.openConnection() as HttpURLConnection
        }

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true

            // Write body if present
            if (body.isNotEmpty()) {
                connection.outputStream.use { outputStream ->
                    val writer = outputStream.bufferedWriter()
                    writer.write(body)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            Log.d("EspHttpClient", "Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                Log.d("EspHttpClient", "Response: ${response.take(200)}")
                response
            } else {
                throw Exception("HTTP $responseCode: ${connection.responseMessage}")
            }
        } finally {
            connection.disconnect()
        }
    }
}