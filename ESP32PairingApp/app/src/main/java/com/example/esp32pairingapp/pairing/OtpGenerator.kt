package com.example.esp32pairingapp.pairing

import kotlin.random.Random

object OtpGenerator {

    /**
     * Generates a random 6-digit numeric OTP.
     * Range: 100000..999999 (always 6 digits, no leading zero)
     */
    fun generate(): String {
        return Random.nextInt(100000, 999999).toString()
    }
}