package com.livemap.app.data.model

data class LocationData(
    val senderId: String,
    val senderName: String,
    val latitude: Double,
    val longitude: Double,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val accuracy: Float = 0f,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE",
    val deviceId: String = "",
    val targetDeviceId: String? = null
)
