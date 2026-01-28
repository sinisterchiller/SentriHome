package com.example.esp32sensormanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.esp32sensormanager.ui.AppRoot
import com.example.esp32sensormanager.ui.theme.ESP32SensorManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ESP32SensorManagerTheme {
                AppRoot()
            }
        }
    }
}
