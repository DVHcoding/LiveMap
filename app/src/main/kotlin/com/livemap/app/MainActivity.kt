package com.livemap.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.livemap.app.data.mqtt.MqttManager
import com.livemap.app.data.pref.AppPreferences
import com.livemap.app.service.LiveLocationService
import com.livemap.app.ui.screens.MainScreen
import com.livemap.app.ui.theme.LiveMapTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineLocationGranted || coarseLocationGranted) {
            requestBackgroundLocationPermission()
            startLocationServiceIfPermitted()
        } else {
            Toast.makeText(this, "Cần cấp quyền Vị trí để chia sẻ tọa độ", Toast.LENGTH_LONG).show()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkBatteryOptimization()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appPreferences = AppPreferences(this)

        try {
            com.google.android.gms.maps.MapsInitializer.initialize(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            LiveMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val myLocation by LiveLocationService.myLocationFlow.collectAsState()
                    val partnerLocation by MqttManager.partnerLocation.collectAsState(initial = null)
                    val isMqttConnected by MqttManager.isConnected.collectAsState()
                    val isServiceRunning by LiveLocationService.isServiceRunning.collectAsState()

                    val pairCode by appPreferences.pairCodeFlow.collectAsState(initial = "")
                    val myUserId by appPreferences.myUserIdFlow.collectAsState(initial = AppPreferences.DEFAULT_USER_A)
                    val myName by appPreferences.myNameFlow.collectAsState(initial = "Tôi")
                    val partnerName by appPreferences.partnerNameFlow.collectAsState(initial = "Người ấy")
                    val mapProvider by appPreferences.mapProviderFlow.collectAsState(initial = AppPreferences.MAP_PROVIDER_OSM)

                    var deviceId by remember { mutableStateOf("") }

                    // Khởi tạo mã phòng & mã thiết bị duy nhất nếu cài app lần đầu
                    LaunchedEffect(Unit) {
                        appPreferences.ensurePairCodeInitialized()
                        deviceId = appPreferences.getOrCreateDeviceId()
                        checkAndRequestPermissions()
                    }

                    // Lắng nghe sự kiện phòng đã đủ 2 người và bị từ chối kết nối
                    LaunchedEffect(Unit) {
                        MqttManager.roomFullEvent.collect { alertMessage ->
                            Toast.makeText(this@MainActivity, alertMessage, Toast.LENGTH_LONG).show()
                        }
                    }

                    // Tự động kết nối MQTT khi vào app để luôn nhận được vị trí đối phương
                    LaunchedEffect(pairCode, myUserId, deviceId) {
                        if (pairCode.isNotBlank() && deviceId.isNotBlank()) {
                            MqttManager.connect(
                                pairCode = pairCode,
                                myUserId = myUserId,
                                deviceId = deviceId
                            )
                        }
                    }

                    MainScreen(
                        myLocation = myLocation,
                        partnerLocation = partnerLocation,
                        isMqttConnected = isMqttConnected,
                        isServiceRunning = isServiceRunning,
                        pairCode = pairCode,
                        myUserId = myUserId,
                        myName = myName,
                        partnerName = partnerName,
                        mapProvider = mapProvider,
                        onSelectMapProvider = { newProvider ->
                            lifecycleScope.launch {
                                appPreferences.saveMapProvider(newProvider)
                            }
                        },
                        onToggleSharing = {
                            if (isServiceRunning) {
                                LiveLocationService.stopService(this@MainActivity)
                            } else {
                                checkAndRequestPermissions()
                            }
                        },
                        onSaveSettings = { newCode, newUserId, newMyName, newPartnerName, newProvider ->
                            lifecycleScope.launch {
                                appPreferences.savePairCode(newCode)
                                appPreferences.saveMyUserId(newUserId)
                                appPreferences.saveMyName(newMyName)
                                appPreferences.savePartnerName(newPartnerName)
                                appPreferences.saveMapProvider(newProvider)

                                MqttManager.disconnect()
                                MqttManager.connect(
                                    pairCode = newCode,
                                    myUserId = newUserId,
                                    deviceId = deviceId
                                )

                                if (isServiceRunning) {
                                    LiveLocationService.stopService(this@MainActivity)
                                    LiveLocationService.startService(this@MainActivity)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            requestBackgroundLocationPermission()
            startLocationServiceIfPermitted()
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
        checkBatteryOptimization()
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    private fun startLocationServiceIfPermitted() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            LiveLocationService.startService(this)
        }
    }
}
