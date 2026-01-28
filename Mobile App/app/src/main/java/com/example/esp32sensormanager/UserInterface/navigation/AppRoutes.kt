package com.example.esp32sensormanager.ui.navigation

object AppRoutes {
    const val Home = "home"
    const val PairMaster = "pair_master"
    const val AddSensor = "add_sensor"
    const val SensorDetail = "sensor_detail/{sensorId}"

    fun sensorDetail(sensorId: String) = "sensor_detail/$sensorId"
}
