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

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _partnerLocation = MutableStateFlow<LocationData?>(null)
    val partnerLocation: StateFlow<LocationData?> = _partnerLocation.asStateFlow()

    private var lastReceivedTimestamp: Long = 0L
    private var offlineCheckerJob: Job? = null

    init {
        startOfflineChecker()
    }

    private fun startOfflineChecker() {
        offlineCheckerJob?.cancel()
        offlineCheckerJob = scope.launch {
            while (isActive) {
                delay(2000L)
                val current = _partnerLocation.value
                if (current != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastReceivedTimestamp > 15_000L) {
                        Log.d(TAG, "Partner timeout (>15s without signal). Auto-clearing partner location.")
                        _partnerLocation.value = null
                    }
                }
            }
        }
    }

    private fun getTopic(pairCode: String): String = "livemap/pair/$pairCode/location"

    fun clearPartnerLocation() {
        _partnerLocation.value = null
        lastReceivedTimestamp = 0L
    }

    fun publishOfflineStatus() {
        val pairCode = currentPairCode ?: return
        if (client?.isConnected != true) return
        try {
            val offlineMsg = LocationData(
                senderId = currentMyUserId,
                senderName = "",
                latitude = 0.0,
                longitude = 0.0,
                status = "OFFLINE",
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

    @Synchronized
    fun connect(pairCode: String, myUserId: String) {
        val isDifferentRoom = currentPairCode != null && currentPairCode != pairCode
        val isDifferentUser = currentMyUserId != myUserId

        if (isDifferentRoom || isDifferentUser) {
            // Đổi phòng hoặc đổi user: gửi tin rời phòng cũ và xóa sạch marker đối phương cũ
            publishOfflineStatus()
            clearPartnerLocation()
            val oldCode = currentPairCode
            if (oldCode != null) {
                val oldTopic = getTopic(oldCode)
                try {
                    if (client?.isConnected == true) {
                        client?.unsubscribe(oldTopic)
                        Log.d(TAG, "Unsubscribed from old topic: $oldTopic")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to unsubscribe old topic: ${e.message}")
                }
            }
        }
        currentPairCode = pairCode
        currentMyUserId = myUserId

        if (client?.isConnected == true) {
            // Đã kết nối, nếu đổi pair code thì subscribe lại
            subscribeToTopic(pairCode)
            return
        }

        try {
            val clientId = "LiveMap_${myUserId}_${UUID.randomUUID().toString().take(6)}"
            client = MqttAsyncClient(BROKER_URL, clientId, MemoryPersistence())

            client?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.d(TAG, "MQTT Connected to $serverURI (reconnect: $reconnect)")
                    _isConnected.value = true
                    currentPairCode?.let { subscribeToTopic(it) }
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
                            // Chỉ nhận tin từ đối phương, bỏ qua tin do chính mình broadcast
                            if (data.senderId != currentMyUserId) {
                                if (data.status == "OFFLINE") {
                                    Log.d(TAG, "Partner explicitly sent OFFLINE / leave room: clearing partner marker")
                                    clearPartnerLocation()
                                } else {
                                    lastReceivedTimestamp = System.currentTimeMillis()
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
        if (client?.isConnected != true) return

        try {
            val json = gson.toJson(data)
            val message = MqttMessage(json.toByteArray()).apply {
                qos = 0 // QoS 0 cho streaming realtime mượt nhất, không dồn hàng đợi
            }
            client?.publish(getTopic(pairCode), message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish location: ${e.message}")
        }
    }

    @Synchronized
    fun disconnect() {
        try {
            publishOfflineStatus()
            val oldCode = currentPairCode
            if (oldCode != null) {
                val oldTopic = getTopic(oldCode)
                try {
                    if (client?.isConnected == true) {
                        client?.unsubscribe(oldTopic)
                    }
                } catch (_: Exception) {}
            }
            if (client?.isConnected == true) {
                client?.disconnect()
            }
            client = null
            _isConnected.value = false
            clearPartnerLocation()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting MQTT: ${e.message}")
        }
    }
}
