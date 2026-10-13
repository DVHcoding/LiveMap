package com.livemap.app.data.mqtt

import android.util.Log
import com.google.gson.Gson
import com.livemap.app.data.model.LocationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID

object MqttManager {
    private const val TAG = "LiveMap_MQTT"
    private const val BROKER_URL = "tcp://broker.emqx.io:1883"

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var client: MqttAsyncClient? = null
    private var currentPairCode: String? = null
    private var currentMyUserId: String = "USER_A"
    private var currentMyName: String = "Tôi"
    private var currentDeviceId: String = ""
    private var lockedPartnerDeviceId: String? = null
    private var isPairConfirmed: Boolean = false
    private var kickedFromRoom: String? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isRoomFullRejected = MutableStateFlow(false)
    val isRoomFullRejected: StateFlow<Boolean> = _isRoomFullRejected.asStateFlow()

    private val _roomFullEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val roomFullEvent: SharedFlow<String> = _roomFullEvent.asSharedFlow()

    private var lastRoomFullAlertTime: Long = 0L

    private fun emitRoomFullAlert(pairCode: String) {
        val now = System.currentTimeMillis()
        if (now - lastRoomFullAlertTime > 4000L) {
            lastRoomFullAlertTime = now
            _roomFullEvent.tryEmit("Phòng '$pairCode' đã đủ 2 người kết nối! Bạn không thể tham gia.")
        }
    }

    private val _partnerLocation = MutableStateFlow<LocationData?>(null)
    val partnerLocation: StateFlow<LocationData?> = _partnerLocation.asStateFlow()

    private var lastReceivedTimestamp: Long = 0L
    private var offlineCheckerJob: Job? = null
    private var presenceJob: Job? = null

    init {
        startOfflineChecker()
    }

    private fun startOfflineChecker() {
        offlineCheckerJob?.cancel()
        offlineCheckerJob = scope.launch {
            while (isActive) {
                delay(2000L)
                val current = _partnerLocation.value
                val now = System.currentTimeMillis()
                // 1. Quá 15s không có GPS update -> ẩn marker vị trí đối phương
                if (current != null && now - lastReceivedTimestamp > 15_000L) {
                    Log.d(TAG, "Partner GPS timeout (>15s). Hiding partner marker.")
                    _partnerLocation.value = null
                }
                // 2. Quá 35s mất hoàn toàn cả Heartbeat lẫn GPS -> coi như ngắt mạng hẳn, giải phóng slot
                if (lockedPartnerDeviceId != null && lastReceivedTimestamp > 0L && now - lastReceivedTimestamp > 35_000L) {
                    Log.d(TAG, "Partner total connection timeout (>35s). Releasing partner slot.")
                    clearPartnerLocation()
                }
            }
        }
    }

    private fun startPresenceHeartbeat() {
        presenceJob?.cancel()
        presenceJob = scope.launch {
            while (isActive) {
                delay(4000L)
                val pairCode = currentPairCode
                if (client?.isConnected == true && pairCode != null && kickedFromRoom != pairCode) {
                    sendPresencePing(targetDevId = lockedPartnerDeviceId)
                }
            }
        }
    }

    private fun sendPresencePing(targetDevId: String? = lockedPartnerDeviceId) {
        val pairCode = currentPairCode ?: return
        if (client?.isConnected != true || kickedFromRoom == pairCode) return
        try {
            val pingMsg = LocationData(
                senderId = currentMyUserId,
                senderName = currentMyName,
                latitude = 0.0,
                longitude = 0.0,
                status = "PING",
                deviceId = currentDeviceId,
                targetDeviceId = targetDevId,
                timestamp = System.currentTimeMillis()
            )
            val json = gson.toJson(pingMsg)
            val message = MqttMessage(json.toByteArray()).apply {
                qos = 0
            }
            client?.publish(getTopic(pairCode), message)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send presence ping: ${e.message}")
        }
    }

    private fun getTopic(pairCode: String): String = "livemap/pair/$pairCode/location"

    fun clearPartnerLocation() {
        _partnerLocation.value = null
        lockedPartnerDeviceId = null
        isPairConfirmed = false
        lastReceivedTimestamp = 0L
    }

    fun publishOfflineStatus() {
        val pairCode = currentPairCode ?: return
        if (client?.isConnected != true || kickedFromRoom == pairCode) return
        try {
            val offlineMsg = LocationData(
                senderId = currentMyUserId,
                senderName = currentMyName,
                latitude = 0.0,
                longitude = 0.0,
                status = "OFFLINE",
                deviceId = currentDeviceId,
                targetDeviceId = lockedPartnerDeviceId,
                timestamp = System.currentTimeMillis()
            )
            val json = gson.toJson(offlineMsg)
            val message = MqttMessage(json.toByteArray()).apply {
                qos = 1
            }
            client?.publish(getTopic(pairCode), message)
            Log.d(TAG, "Published OFFLINE status to topic: ${getTopic(pairCode)}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish offline status: ${e.message}")
        }
    }

    fun publishRoomFull(targetDeviceId: String) {
        val pairCode = currentPairCode ?: return
        if (client?.isConnected != true || kickedFromRoom == pairCode) return
        // CHỈ máy đã xác nhận ghép đôi chính thức mới có quyền đuổi người thứ 3
        if (!isPairConfirmed && lockedPartnerDeviceId == null) {
            Log.w(TAG, "Ignoring publishRoomFull: this device is not yet confirmed in the room.")
            return
        }
        try {
            val kickMsg = LocationData(
                senderId = currentMyUserId,
                senderName = currentMyName,
                latitude = 0.0,
                longitude = 0.0,
                status = "ROOM_FULL",
                deviceId = currentDeviceId,
                targetDeviceId = targetDeviceId,
                timestamp = System.currentTimeMillis()
            )
            val json = gson.toJson(kickMsg)
            val message = MqttMessage(json.toByteArray()).apply {
                qos = 1
            }
            client?.publish(getTopic(pairCode), message)
            Log.w(TAG, "Published ROOM_FULL against unauthorized 3rd device: $targetDeviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish ROOM_FULL: ${e.message}")
        }
    }

    @Synchronized
    fun connect(
        pairCode: String,
        myUserId: String,
        deviceId: String = currentDeviceId,
        myName: String = currentMyName
    ) {
        if (pairCode == kickedFromRoom) {
            Log.w(TAG, "Cannot connect: this device was kicked from room '$pairCode'")
            return
        }

        currentDeviceId = deviceId
        currentMyName = myName

        val isDifferentRoom = currentPairCode != null && currentPairCode != pairCode
        val isDifferentUser = currentMyUserId != myUserId

        if (isDifferentRoom || isDifferentUser) {
            kickedFromRoom = null
            _isRoomFullRejected.value = false
            disconnect(sendOffline = isPairConfirmed)
        }

        if (pairCode == kickedFromRoom) {
            Log.w(TAG, "Cannot connect: this device was kicked from room '$pairCode'")
            _isConnected.value = false
            _isRoomFullRejected.value = true
            return
        }
        currentPairCode = pairCode
        currentMyUserId = myUserId

        // Nếu client đã tồn tại (đang kết nối hoặc đã kết nối) cho phòng này -> không tạo thêm client mới
        if (client != null) {
            if (client?.isConnected == true) {
                subscribeToTopic(pairCode)
                startPresenceHeartbeat()
                sendPresencePing()
            }
            return
        }

        try {
            val clientId = "LiveMap_${myUserId}_${UUID.randomUUID().toString().take(6)}"
            client = MqttAsyncClient(BROKER_URL, clientId, MemoryPersistence())

            client?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d(TAG, "MQTT Connected to $serverURI (reconnect: $reconnect)")
                    val room = currentPairCode
                    if (room != null && room != kickedFromRoom) {
                        _isConnected.value = true
                        _isRoomFullRejected.value = false
                        subscribeToTopic(room)
                        startPresenceHeartbeat()
                        sendPresencePing()
                    } else {
                        _isConnected.value = false
                    }
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "MQTT Connection lost: ${cause?.message}")
                    _isConnected.value = false
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.payload?.let { bytes ->
                        try {
                            val json = String(bytes)
                            val data = gson.fromJson(json, LocationData::class.java)

                            val senderDevId = data.deviceId

                            // 1. Bỏ qua tin do chính máy mình phát đi (loopback)
                            if (senderDevId.isNotBlank() && senderDevId == currentDeviceId) {
                                return
                            }
                            if (senderDevId.isBlank() && data.senderId == currentMyUserId) {
                                return
                            }

                            // 2. Kiểm tra nếu là lệnh đuổi người thứ 3 (ROOM_FULL) nhắm vào chính máy mình
                            if (data.status == "ROOM_FULL" && data.targetDeviceId == currentDeviceId) {
                                Log.w(TAG, "Received ROOM_FULL for our device! Disconnecting immediately.")
                                val kickedRoom = currentPairCode ?: ""
                                kickedFromRoom = kickedRoom
                                _isRoomFullRejected.value = true
                                _isConnected.value = false
                                disconnect(sendOffline = false)
                                emitRoomFullAlert(kickedRoom)
                                return
                            }

                            // 3. Kiểm tra nếu phòng này đã có 2 người khác ghép đôi với nhau (A <-> B)
                            // Nếu sender gửi kèm targetDeviceId chỉ định người khác (không phải mình),
                            // mà máy mình chưa có partner nào -> mình là người thứ 3 dư thừa!
                            if (lockedPartnerDeviceId == null && data.targetDeviceId != null && data.targetDeviceId != currentDeviceId) {
                                Log.w(TAG, "Room '$currentPairCode' is occupied by another pair (${data.deviceId} <-> ${data.targetDeviceId}). Kicking self.")
                                val kickedRoom = currentPairCode ?: ""
                                kickedFromRoom = kickedRoom
                                _isRoomFullRejected.value = true
                                _isConnected.value = false
                                disconnect(sendOffline = false)
                                emitRoomFullAlert(kickedRoom)
                                return
                            }

                            // 4. Xử lý khi đối phương rời phòng / ngắt kết nối
                            if (data.status == "OFFLINE") {
                                // CHỈ xử lý khi người gửi đúng là đối phương hiện tại của mình
                                if (lockedPartnerDeviceId != null && senderDevId == lockedPartnerDeviceId) {
                                    Log.d(TAG, "Paired partner ($senderDevId) sent OFFLINE: clearing marker & releasing slot")
                                    clearPartnerLocation()
                                }
                                return
                            }

                            // 5. Cơ chế Khóa chốt động 2 người (Dynamic 2-Slot Room Presence):
                            if (lockedPartnerDeviceId == null) {
                                // Slot trống: chấp nhận thiết bị này làm partner
                                if (senderDevId.isNotBlank()) {
                                    lockedPartnerDeviceId = senderDevId
                                    if (data.targetDeviceId == currentDeviceId) {
                                        isPairConfirmed = true
                                    }
                                    Log.d(TAG, "Partner slot acquired by device: $senderDevId (confirmed: $isPairConfirmed)")
                                    // Đáp lại ngay 1 gói PING có targetDeviceId để 2 bên khóa chặt nhau
                                    sendPresencePing(targetDevId = senderDevId)
                                }
                            } else if (senderDevId.isNotBlank() && senderDevId != lockedPartnerDeviceId) {
                                // ĐÃ CÓ PARTNER RỒI! Thiết bị này là người thứ 3 cố tình vào phòng!
                                // CHỈ gửi ROOM_FULL nếu máy mình đã là thành viên chính thức
                                if (isPairConfirmed || lastReceivedTimestamp > 0L) {
                                    Log.w(TAG, "Room full (paired with $lockedPartnerDeviceId). Rejecting 3rd device: $senderDevId")
                                    publishRoomFull(targetDeviceId = senderDevId)
                                }
                                return // BỎ QUA HOÀN TOÀN TỌA ĐỘ NGƯỜI THỨ 3, TUYỆT ĐỐI KHÔNG VẼ LÊN MAP!
                            }

                            // 6. Xử lý dữ liệu từ partner hợp lệ
                            if (senderDevId == lockedPartnerDeviceId) {
                                lastReceivedTimestamp = System.currentTimeMillis()
                                if (data.targetDeviceId == currentDeviceId) {
                                    isPairConfirmed = true
                                }
                                if (data.status == "ACTIVE") {
                                    Log.d(TAG, "Partner location arrived: lat=${data.latitude}, lng=${data.longitude}, speed=${data.speed}")
                                    _partnerLocation.value = data
                                }
                            }
                            Unit
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing location JSON: ${e.message}")
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 20

                val willMsg = LocationData(
                    senderId = currentMyUserId,
                    senderName = currentMyName,
                    latitude = 0.0,
                    longitude = 0.0,
                    status = "OFFLINE",
                    deviceId = currentDeviceId,
                    targetDeviceId = lockedPartnerDeviceId,
                    timestamp = System.currentTimeMillis()
                )
                setWill(getTopic(pairCode), gson.toJson(willMsg).toByteArray(), 1, false)
            }

            client?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connect initiate success")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Connect initiate failure: ${exception?.message}")
                    _isConnected.value = false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MQTT client: ${e.message}")
        }
    }

    private fun subscribeToTopic(pairCode: String) {
        val topic = getTopic(pairCode)
        try {
            client?.subscribe(topic, 0, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed successfully to: $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to subscribe to $topic: ${exception?.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing: ${e.message}")
        }
    }

    fun publishLocation(data: LocationData) {
        val pairCode = currentPairCode ?: return
        if (pairCode == kickedFromRoom) return

        if (client?.isConnected != true) {
            connect(pairCode, currentMyUserId, currentDeviceId, currentMyName)
            return
        }

        try {
            val dataToSend = data.copy(
                deviceId = if (data.deviceId.isBlank()) currentDeviceId else data.deviceId,
                targetDeviceId = data.targetDeviceId ?: lockedPartnerDeviceId
            )
            val json = gson.toJson(dataToSend)
            val message = MqttMessage(json.toByteArray()).apply {
                qos = 0 // QoS 0 cho streaming realtime mượt nhất, không dồn hàng đợi
            }
            client?.publish(getTopic(pairCode), message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish location: ${e.message}")
        }
    }

    @Synchronized
    fun disconnect(sendOffline: Boolean = true) {
        try {
            presenceJob?.cancel()
            presenceJob = null
            if (sendOffline && isPairConfirmed && lockedPartnerDeviceId != null) {
                publishOfflineStatus()
            }
            val oldCode = currentPairCode
            if (oldCode != null) {
                val oldTopic = getTopic(oldCode)
                try {
                    if (client?.isConnected == true) {
                        client?.unsubscribe(oldTopic)
                    }
                } catch (_: Exception) {}
            }
            val oldClient = client
            client = null
            try {
                oldClient?.setCallback(null)
                if (oldClient?.isConnected == true) {
                    oldClient.disconnectForcibly(300, 300)
                }
                oldClient?.close()
            } catch (_: Exception) {}

            _isConnected.value = false
            clearPartnerLocation()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting MQTT: ${e.message}")
        }
    }
}
