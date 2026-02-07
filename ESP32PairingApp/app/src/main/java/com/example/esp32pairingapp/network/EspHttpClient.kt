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
import java.net.URLEncoder

/**
 * HTTP client that can make requests through a specific Network.
 * Required for accessing ESP32 web server at 192.168.10.1 through the ESP32 WiFi network.
 */
class EspHttpClient {

    /**
     * Remove leading/trailing whitespace and invisible control characters
     * (e.g., \n, \r) that can sneak in from input fields.
     */
    private fun cleanInput(value: String): String {
        return value
            .trim()
            .replace(Regex("\\p{C}"), "") // removes control chars like \n \r \t etc.
    }

    /**
     * URL-encode so spaces/special chars are transmitted safely.
     */
    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

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

    /**
     * Send SSID to ESP32 in the exact plain-text format required:
     * "SSID=[user ssid]"
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    suspend fun postNewSsid(
        baseUrl: String = "http://192.168.10.1",
        ssid: String,
        network: Network? = null
    ): String {
        val cleaned = cleanInput(ssid)
        val encoded = encode(cleaned)
        val body = "SSID=$encoded"

        return post(
            url = "$baseUrl/api/newssid",
            body = body,
            contentType = "text/plain",
            network = network
        )
    }

    /**
     * Send password to ESP32 in the exact plain-text format required:
     * "pass=[user password]"
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    suspend fun postNewPass(
        baseUrl: String = "http://192.168.10.1",
        password: String,
        network: Network? = null
    ): String {
        val cleaned = cleanInput(password)
        val encoded = encode(cleaned)
        val body = "pass=$encoded"

        return post(
            url = "$baseUrl/api/newpass",
            body = body,
            contentType = "text/plain",
            network = network
        )
    }
}
