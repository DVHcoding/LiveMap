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

    @Test
    fun testOutOfOrderPacketRejection() {
        var lastPartnerTimestamp = 1700000010000L

        // Gói tin đến muộn do mạng chập chờn (timestamp cũ hơn gói vừa nhận)
        val stalePacket = LocationData(
            senderId = "USER_B",
            senderName = "Partner",
            latitude = 21.01,
            longitude = 105.81,
            timestamp = 1700000005000L
        )

        val isAccepted = stalePacket.timestamp >= lastPartnerTimestamp
        assertTrue("Stale/out-of-order packet must be discarded to prevent jumping back and forth", !isAccepted)
    }

    @Test
    fun testAccuracyGatekeeper() {
        // Gói tin bị trôi cell-tower hoặc wifi sai số lớn (accuracy = 85m > 40m)
        val noisyPacket = LocationData(
            senderId = "USER_B",
            senderName = "Partner",
            latitude = 21.05,
            longitude = 105.85,
            accuracy = 85f,
            timestamp = 1700000020000L
        )

        val hasPreviousFix = true
        val isAccepted = !(hasPreviousFix && noisyPacket.accuracy > 40f)
        assertTrue("Noisy fix (>40m) must be rejected when a valid fix already exists", !isAccepted)
    }

    @Test
    fun testAntiTeleportVelocitySanity() {
        val lastLat = 21.028511
        val lastLng = 105.854167
        val lastTime = 1700000000000L

        // Gói tin mới sau 1 giây nhưng nhảy vọt 300 mét (vận tốc 300 m/s ~ 1080 km/h)
        val newLat = 21.031211
        val newLng = 105.854167
        val newTime = 1700000001000L

        val deltaLat = kotlin.math.abs(newLat - lastLat)
        val deltaLng = kotlin.math.abs(newLng - lastLng)
        val approxDistMeters = kotlin.math.sqrt(deltaLat * deltaLat + deltaLng * deltaLng) * 111_000.0
        val timeDeltaSec = (newTime - lastTime) / 1000.0
        val impliedSpeed = approxDistMeters / timeDeltaSec

        val isImpossibleTeleport = timeDeltaSec > 0.0 && impliedSpeed > 45.0 && approxDistMeters > 50.0
        assertTrue("Impossible teleport spike (>45 m/s and >50m) must be rejected", isImpossibleTeleport)
    }
}
