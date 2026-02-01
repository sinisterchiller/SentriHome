package com.example.esp32pairingapp.pairing

import java.security.SecureRandom

object PasswordGenerator {
    private const val DEFAULT_LENGTH = 16

    // WPA2 passwords can include many chars, but alphanumeric avoids edge cases
    private const val CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private val random = SecureRandom()

    /**
     * Generates a random alphanumeric password.
     * WPA2 requires 8..63 chars; default is 16.
     */
    fun generate(length: Int = DEFAULT_LENGTH): String {
        require(length in 8..63) { "Password length must be between 8 and 63 (WPA2 requirement)." }

        val sb = StringBuilder(length)
        repeat(length) {
            val idx = random.nextInt(CHARSET.length)
            sb.append(CHARSET[idx])
        }
        return sb.toString()
    }
}