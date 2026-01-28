package com.example.esp32sensormanager.data.esp

import retrofit2.http.Body
import retrofit2.http.POST

interface EspApi {

    @POST("api/pair/master")
    suspend fun pairMaster(@Body req: PairMasterRequest): PairMasterResponse

    @POST("api/pair/sensor")
    suspend fun addSensor(@Body req: AddSensorRequest): AddSensorResponse

    @POST("api/sensor/rename")
    suspend fun renameSensor(@Body req: RenameSensorRequest): GenericResponse
}
