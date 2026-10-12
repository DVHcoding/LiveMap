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

    private val _partnerLocation = MutableSharedFlow<LocationData>(extraBufferCapacity = 10)
    val partnerLocation: SharedFlow<LocationData> = _partnerLocation.asSharedFlow()

    private fun getTopic(pairCode: String): String = "livemap/pair/$pairCode/location"

    @Synchronized
    fun connect(pairCode: String, myUserId: String) {
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
                                scope.launch {
                                    _partnerLocation.emit(data)
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
            if (client?.isConnected == true) {
                client?.disconnect()
            }
            client = null
            _isConnected.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting MQTT: ${e.message}")
        }
    }
}
