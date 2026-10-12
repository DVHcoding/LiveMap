package com.livemap.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.livemap.app.MainActivity
import com.livemap.app.R
import com.livemap.app.data.model.LocationData
import com.livemap.app.data.mqtt.MqttManager
import com.livemap.app.data.pref.AppPreferences
import com.livemap.app.utils.BatteryUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LiveLocationService : Service() {

    companion object {
        private const val TAG = "LiveLocationService"
        private const val NOTIFICATION_ID = 9991
        private const val CHANNEL_ID = "live_map_location_channel"

        const val ACTION_START = "ACTION_START_LIVE_SHARING"
        const val ACTION_STOP = "ACTION_STOP_LIVE_SHARING"

        private val _myLocationFlow = MutableStateFlow<LocationData?>(null)
        val myLocationFlow: StateFlow<LocationData?> = _myLocationFlow.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var myUserId: String = AppPreferences.DEFAULT_USER_A
    private var myName: String = "Tôi"
    private var pairCode: String = ""
    private var deviceId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LiveMap::LocationSharingWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationSharing()
                stopSelf()
            }
            ACTION_START -> {
                startForegroundWithNotification()
                initAndStartTracking()
            }
            else -> {
                startForegroundWithNotification()
                initAndStartTracking()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Đang chạy ngầm chia sẻ vị trí trực tiếp")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        _isServiceRunning.value = true
    }

    private var lastBatteryCheckTime: Long = 0L
    private var cachedBatteryInfo: BatteryUtils.BatteryInfo = BatteryUtils.BatteryInfo(100, false)
    private var lastPublishedTime: Long = 0L
    private var lastPublishedLat: Double = 0.0
    private var lastPublishedLng: Double = 0.0

    private fun initAndStartTracking() {
        serviceScope.launch {
            val prefs = AppPreferences(applicationContext)
            pairCode = prefs.ensurePairCodeInitialized()
            myUserId = prefs.myUserIdFlow.first()
            myName = prefs.myNameFlow.first()
            deviceId = prefs.getOrCreateDeviceId()
            val lockedPartnerId = prefs.getLockedPartnerDeviceId()

            MqttManager.connect(
                pairCode = pairCode,
                myUserId = myUserId,
                deviceId = deviceId,
                lockedPartnerId = lockedPartnerId,
                onUpdateLock = { newLock ->
                    serviceScope.launch {
                        prefs.saveLockedPartnerDeviceId(newLock)
                    }
                }
            )
            startLocationUpdates()
        }
    }

    private fun handleNewLocation(loc: android.location.Location) {
        val now = System.currentTimeMillis()

        // 1. Chỉ truy vấn trạng thái Pin hệ thống 30 giây một lần (tiết kiệm CPU)
        if (now - lastBatteryCheckTime > 30_000L || lastBatteryCheckTime == 0L) {
            cachedBatteryInfo = BatteryUtils.getBatteryInfo(applicationContext)
            lastBatteryCheckTime = now
        }

        val speed = if (loc.hasSpeed()) loc.speed else 0f
        val isMoving = speed >= 0.5f
        val deltaLat = kotlin.math.abs(loc.latitude - lastPublishedLat)
        val deltaLng = kotlin.math.abs(loc.longitude - lastPublishedLng)
        val hasDisplaced = deltaLat > 0.00003 || deltaLng > 0.00003 // Di chuyển > 3 mét

        // 2. Chế độ tiết kiệm Pin thông minh: Khi đứng yên hoàn toàn, giãn nhịp gửi tin thành 3 giây
        // Khi bắt đầu di chuyển: phát sóng ngay lập tức mỗi 1 giây để đối phương thấy mượt mà nhất
        val shouldPublish = isMoving || hasDisplaced || (now - lastPublishedTime >= 3000L)

        val locationData = LocationData(
            senderId = myUserId,
            senderName = myName,
            latitude = loc.latitude,
            longitude = loc.longitude,
            bearing = if (loc.hasBearing()) loc.bearing else 0f,
            speed = speed,
            accuracy = if (loc.hasAccuracy()) loc.accuracy else 0f,
            batteryLevel = cachedBatteryInfo.level,
            isCharging = cachedBatteryInfo.isCharging,
            timestamp = now,
            deviceId = deviceId
        )

        _myLocationFlow.value = locationData

        if (shouldPublish) {
            lastPublishedTime = now
            lastPublishedLat = loc.latitude
            lastPublishedLng = loc.longitude
            MqttManager.publishLocation(locationData)
            Log.d(TAG, "Location published: lat=${loc.latitude}, lng=${loc.longitude}, speed=${speed}m/s")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (locationCallback != null) return

        // 1. Lấy ngay vị trí gần nhất lưu trong cache để hiển thị lập tức, không chờ GPS fix
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { handleNewLocation(it) }
            }
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    loc?.let { handleNewLocation(it) }
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting last location: ${e.message}")
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0f) // Liên tục ghi nhận cả khi đứng yên hoặc quay đầu
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                handleNewLocation(loc)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates requested successfully (1s interval)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        }
    }

    private fun stopLocationSharing() {
        MqttManager.publishOfflineStatus()
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LiveMap — Vị trí trực tiếp")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "Dừng chia sẻ", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chia sẻ vị trí trực tiếp",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị thông báo khi dịch vụ chia sẻ vị trí đang chạy ngầm"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Khi người dùng vuốt/gạt đóng app khỏi đa nhiệm (Recents), 
        // Foreground Service vẫn giữ nguyên và tiếp tục stream vị trí ngầm
        Log.d(TAG, "App swiped away from Recents, Foreground Service continues running in background")
    }

    override fun onDestroy() {
        stopLocationSharing()
        super.onDestroy()
    }
}
