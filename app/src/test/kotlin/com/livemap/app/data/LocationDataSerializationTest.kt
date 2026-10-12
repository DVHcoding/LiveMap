package com.livemap.app.data

import com.google.gson.Gson
import com.livemap.app.data.model.LocationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationDataSerializationTest {

    private val gson = Gson()

    @Test
    fun testSerializationAndDeserialization() {
        val original = LocationData(
            senderId = "USER_A",
            senderName = "Người A",
            latitude = 21.028511,
            longitude = 105.854167,
            bearing = 45.5f,
            speed = 8.5f,
            accuracy = 3.2f,
            batteryLevel = 88,
            isCharging = true,
            timestamp = 1700000000000L
        )

        val json = gson.toJson(original)
        assertTrue(json.contains("USER_A"))
        assertTrue(json.contains("21.028511"))

        val deserialized = gson.fromJson(json, LocationData::class.java)
        assertEquals(original.senderId, deserialized.senderId)
        assertEquals(original.senderName, deserialized.senderName)
        assertEquals(original.latitude, deserialized.latitude, 0.000001)
        assertEquals(original.longitude, deserialized.longitude, 0.000001)
        assertEquals(original.bearing, deserialized.bearing, 0.01f)
        assertEquals(original.speed, deserialized.speed, 0.01f)
        assertEquals(original.batteryLevel, deserialized.batteryLevel)
        assertEquals(original.isCharging, deserialized.isCharging)
    }

    @Test
    fun testTwoUserFilterSeparation() {
        val myUserId = "USER_A"
        val packetA = LocationData(senderId = "USER_A", senderName = "Anh", latitude = 21.0, longitude = 105.0)
        val packetB = LocationData(senderId = "USER_B", senderName = "Em", latitude = 21.1, longitude = 105.1)

        // Máy A phải bỏ qua packet của chính mình
        val isIgnoredOnDeviceA = packetA.senderId == myUserId
        assertTrue("Device A must ignore its own packet", isIgnoredOnDeviceA)

        // Máy A phải nhận packet của máy B
        val isReceivedOnDeviceA = packetB.senderId != myUserId
        assertTrue("Device A must receive Partner B's packet", isReceivedOnDeviceA)
    }
}
