package com.example.esp32sensormanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.example.esp32sensormanager.ui.navigation.AppNavGraph

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    Surface(color = MaterialTheme.colorScheme.background) {
        AppNavGraph(navController = navController)
    }
}
