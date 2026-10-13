package com.livemap.app.data

import com.google.gson.Gson
import com.livemap.app.data.model.LocationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử giả lập kịch bản 3 thiết bị và cơ chế ghép đôi 2 chiều:
 * - Máy 1 (DEV_1) và Máy 2 (DEV_2) kết nối trong phòng.
 * - Máy 3 (DEV_3) nhảy vào phòng.
 * - Đảm bảo Máy 3 bị chặn, không kick ai, không vẽ tọa độ.
 * - Đảm bảo khi Máy 3 rời phòng thì Máy 1 và Máy 2 không bao giờ bị mất kết nối.
 */
class RoomHandshakeAndThreeDeviceTest {

    private val gson = Gson()

    class MockDevice(
        val deviceId: String,
        val myUserId: String,
        val myName: String
    ) {
        var lockedPartnerDeviceId: String? = null
        var isPairConfirmed: Boolean = false
        var displayedPartnerLocation: LocationData? = null
        var kickedFromRoom: String? = null
        val sentPackets = mutableListOf<LocationData>()

        fun receivePacket(data: LocationData, currentRoom: String): Boolean {
            val senderDevId = data.deviceId

            // 1. Loopback
            if (senderDevId.isNotBlank() && senderDevId == deviceId) return false
            if (senderDevId.isBlank() && data.senderId == myUserId) return false

            // 2. ROOM_FULL nhắm vào chính máy mình
            if (data.status == "ROOM_FULL" && data.targetDeviceId == deviceId) {
                kickedFromRoom = currentRoom
                displayedPartnerLocation = null
                lockedPartnerDeviceId = null
                isPairConfirmed = false
                return false
            }

            // 3. Phát hiện phòng đã có cặp đôi (A <-> B)
            if (lockedPartnerDeviceId == null && data.targetDeviceId != null && data.targetDeviceId != deviceId) {
                kickedFromRoom = currentRoom
                displayedPartnerLocation = null
                lockedPartnerDeviceId = null
                isPairConfirmed = false
                return false
            }

            // 4. Xử lý OFFLINE
            if (data.status == "OFFLINE") {
                if (lockedPartnerDeviceId != null && senderDevId == lockedPartnerDeviceId) {
                    displayedPartnerLocation = null
                    // Giữ slot hoặc giải phóng nếu rời hẳn
                }
                return false
            }

            // 5. Quản lý slot 2 người
            if (lockedPartnerDeviceId == null) {
                if (senderDevId.isNotBlank()) {
                    lockedPartnerDeviceId = senderDevId
                    if (data.targetDeviceId == deviceId) {
                        isPairConfirmed = true
                    }
                }
            } else if (senderDevId.isNotBlank() && senderDevId != lockedPartnerDeviceId) {
                // Người thứ 3!
                if (isPairConfirmed || displayedPartnerLocation != null) {
                    val kickMsg = LocationData(
                        senderId = myUserId,
                        senderName = myName,
                        latitude = 0.0,
                        longitude = 0.0,
                        status = "ROOM_FULL",
                        deviceId = deviceId,
                        targetDeviceId = senderDevId
                    )
                    sentPackets.add(kickMsg)
                }
                return false
            }

            // 6. Nhận dữ liệu hợp lệ
            if (senderDevId == lockedPartnerDeviceId) {
                if (data.targetDeviceId == deviceId) {
                    isPairConfirmed = true
                }
                if (data.status == "ACTIVE") {
                    displayedPartnerLocation = data
                }
                return true
            }

            return false
        }
    }

    @Test
    fun testTwoDevicesPairSuccessfully() {
        val dev1 = MockDevice("DEV_1", "USER_A", "Máy 1")
        val dev2 = MockDevice("DEV_2", "USER_B", "Máy 2")
        val room = "LM-8888"

        // Dev1 gửi gói ACTIVE (chưa biết ai)
        val packet1 = LocationData(
            senderId = dev1.myUserId,
            senderName = dev1.myName,
            latitude = 21.0,
            longitude = 105.0,
            status = "ACTIVE",
            deviceId = dev1.deviceId,
            targetDeviceId = null
        )

        // Dev2 nhận gói từ Dev1
        dev2.receivePacket(packet1, room)
        assertEquals("DEV_1", dev2.lockedPartnerDeviceId)
        assertEquals(21.0, dev2.displayedPartnerLocation?.latitude ?: 0.0, 0.001)

        // Dev2 gửi lại gói ACTIVE nhắm vào Dev1
        val packet2 = LocationData(
            senderId = dev2.myUserId,
            senderName = dev2.myName,
            latitude = 21.1,
            longitude = 105.1,
            status = "ACTIVE",
            deviceId = dev2.deviceId,
            targetDeviceId = dev1.deviceId
        )

        // Dev1 nhận gói từ Dev2
        dev1.receivePacket(packet2, room)
        assertEquals("DEV_2", dev1.lockedPartnerDeviceId)
        assertTrue("Dev1 must confirm pairing with Dev2", dev1.isPairConfirmed)
        assertEquals(21.1, dev1.displayedPartnerLocation?.latitude ?: 0.0, 0.001)

        // Dev1 gửi lại gói tiếp theo có targetDeviceId = DEV_2
        val packet1Confirmed = packet1.copy(targetDeviceId = dev2.deviceId)
        dev2.receivePacket(packet1Confirmed, room)
        assertTrue("Dev2 must confirm pairing with Dev1", dev2.isPairConfirmed)
    }

    @Test
    fun testThirdDeviceIsRejectedAndDoesNotDisruptExistingPair() {
        val dev1 = MockDevice("DEV_1", "USER_A", "Máy 1").apply {
            lockedPartnerDeviceId = "DEV_2"
            isPairConfirmed = true
            displayedPartnerLocation = LocationData("USER_B", "Máy 2", 21.1, 105.1, deviceId = "DEV_2")
        }
        val dev2 = MockDevice("DEV_2", "USER_B", "Máy 2").apply {
            lockedPartnerDeviceId = "DEV_1"
            isPairConfirmed = true
            displayedPartnerLocation = LocationData("USER_A", "Máy 1", 21.0, 105.0, deviceId = "DEV_1")
        }
        val dev3 = MockDevice("DEV_3", "USER_C", "Máy 3")
        val room = "LM-8888"

        // 1. Dev3 nhận gói tin từ Dev1 đang chỉ định Dev2
        val packetFromDev1 = LocationData(
            senderId = dev1.myUserId,
            senderName = dev1.myName,
            latitude = 21.0,
            longitude = 105.0,
            status = "ACTIVE",
            deviceId = dev1.deviceId,
            targetDeviceId = dev2.deviceId
        )

        dev3.receivePacket(packetFromDev1, room)

        // Dev3 phải tự nhận biết phòng đã có cặp và tự kick mình
        assertEquals(room, dev3.kickedFromRoom)
        assertNull("Dev3 must NOT display Dev1 location", dev3.displayedPartnerLocation)

        // 2. Nếu Dev3 lỡ gửi 1 gói tin vào phòng
        val packetFromDev3 = LocationData(
            senderId = dev3.myUserId,
            senderName = dev3.myName,
            latitude = 21.2,
            longitude = 105.2,
            status = "ACTIVE",
            deviceId = dev3.deviceId,
            targetDeviceId = null
        )

        // Dev2 nhận gói từ Dev3
        dev2.receivePacket(packetFromDev3, room)
        // Dev2 không thay đổi partner, vẫn là Dev1
        assertEquals("DEV_1", dev2.lockedPartnerDeviceId)
        assertEquals(21.0, dev2.displayedPartnerLocation?.latitude ?: 0.0, 0.001)

        // Dev2 phát lệnh ROOM_FULL đá Dev3
        val kickFromDev2 = dev2.sentPackets.lastOrNull { it.status == "ROOM_FULL" }
        assertNotNull("Dev2 must issue ROOM_FULL against Dev3", kickFromDev2)
        assertEquals("DEV_3", kickFromDev2?.targetDeviceId)

        // 3. Dev3 chuyển sang phòng khác và phát OFFLINE
        val offlineFromDev3 = LocationData(
            senderId = dev3.myUserId,
            senderName = dev3.myName,
            latitude = 0.0,
            longitude = 0.0,
            status = "OFFLINE",
            deviceId = dev3.deviceId
        )

        // Dev1 và Dev2 nhận OFFLINE từ Dev3 -> KHÔNG BỊ ẢNH HƯỞNG
        dev1.receivePacket(offlineFromDev3, room)
        dev2.receivePacket(offlineFromDev3, room)

        assertEquals("DEV_2", dev1.lockedPartnerDeviceId)
        assertEquals("DEV_1", dev2.lockedPartnerDeviceId)
        assertNotNull("Dev1 still has Dev2 location", dev1.displayedPartnerLocation)
        assertNotNull("Dev2 still has Dev1 location", dev2.displayedPartnerLocation)
    }
}
