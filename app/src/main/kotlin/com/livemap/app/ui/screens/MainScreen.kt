package com.livemap.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livemap.app.data.model.LocationData
import com.livemap.app.data.pref.AppPreferences
import com.livemap.app.ui.map.GoogleMapView
import com.livemap.app.ui.map.MapViewController
import com.livemap.app.ui.map.OsmMapView
import com.livemap.app.ui.theme.PartnerPink
import com.livemap.app.ui.theme.PrimaryBlue
import com.livemap.app.ui.theme.StatusGreen
import com.livemap.app.ui.theme.StatusRed
import com.livemap.app.ui.theme.StatusYellow

@Composable
fun MainScreen(
    myLocation: LocationData?,
    partnerLocation: LocationData?,
    isMqttConnected: Boolean,
    isServiceRunning: Boolean,
    pairCode: String,
    myUserId: String,
    myName: String,
    partnerName: String,
    mapProvider: String,
    onSelectMapProvider: (String) -> Unit,
    onToggleSharing: () -> Unit,
    onSaveSettings: (pairCode: String, userId: String, myName: String, partnerName: String, mapProvider: String) -> Unit
) {
    val context = LocalContext.current
    val mapController = remember { MapViewController() }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Hiển thị Map tương ứng (OpenStreetMap mặc định hoặc Google Maps)
        if (mapProvider == AppPreferences.MAP_PROVIDER_GOOGLE) {
            GoogleMapView(
                myLocation = myLocation,
                partnerLocation = partnerLocation,
                myName = myName,
                partnerName = partnerName,
                mapViewController = mapController
            )
        } else {
            OsmMapView(
                myLocation = myLocation,
                partnerLocation = partnerLocation,
                myName = myName,
                partnerName = partnerName,
                mapViewController = mapController
            )
        }

        // 2. Thanh tiêu đề & trạng thái kết nối trên cùng
        TopOverlayHeader(
            pairCode = pairCode,
            isConnected = isMqttConnected,
            onOpenSettings = { showSettingsDialog = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
        )

        // 3. Cụm nút điều hướng vị trí Google Maps Style (Đặt ở góc dưới bên phải, ngay trên Dashboard)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 185.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Nút Chuyển đổi Nguồn bản đồ (OpenStreetMap <-> Google Maps)
            SmallFloatingActionButton(
                onClick = {
                    val nextProvider = if (mapProvider == AppPreferences.MAP_PROVIDER_GOOGLE) {
                        AppPreferences.MAP_PROVIDER_OSM
                    } else {
                        AppPreferences.MAP_PROVIDER_GOOGLE
                    }
                    onSelectMapProvider(nextProvider)
                    val providerName = if (nextProvider == AppPreferences.MAP_PROVIDER_GOOGLE) "Google Maps" else "OpenStreetMap"
                    Toast.makeText(context, "Đã chuyển sang $providerName", Toast.LENGTH_SHORT).show()
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = if (mapProvider == AppPreferences.MAP_PROVIDER_GOOGLE) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Đổi nguồn bản đồ")
            }

            // Nút Đặt lại hướng Bắc (0 độ) chuẩn Google Maps khi xoay bản đồ
            SmallFloatingActionButton(
                onClick = {
                    mapController.resetNorth()
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryBlue,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.Explore, contentDescription = "Đặt lại hướng Bắc")
            }

            // Nút Xem bao quát cả 2 người
            SmallFloatingActionButton(
                onClick = {
                    if (myLocation == null && partnerLocation == null) {
                        Toast.makeText(context, "Chưa có tọa độ của ai để hiển thị", Toast.LENGTH_SHORT).show()
                    } else {
                        mapController.fitBothLocations(myLocation, partnerLocation)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.CropFree, contentDescription = "Xem cả 2 người")
            }

            // Nút Tâm điểm người ấy (Partner)
            SmallFloatingActionButton(
                onClick = {
                    if (partnerLocation != null) {
                        mapController.centerOnLocation(partnerLocation.latitude, partnerLocation.longitude, zoom = 17.5)
                    } else {
                        Toast.makeText(context, "$partnerName hiện chưa có tín hiệu vị trí hoặc đang offline", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = if (partnerLocation != null) PartnerPink else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (partnerLocation != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(Icons.Default.LocationSearching, contentDescription = "Tâm điểm người ấy")
            }

            // Nút Tròn Chuẩn Google Maps: Cuộn về vị trí của chính mình khi đi xa
            FloatingActionButton(
                onClick = {
                    if (myLocation != null) {
                        mapController.centerOnLocation(myLocation.latitude, myLocation.longitude, zoom = 17.5)
                    } else {
                        Toast.makeText(context, "Đang chờ GPS xác định vị trí của bạn...", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = Color.White,
                contentColor = PrimaryBlue,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Tâm điểm vị trí của tôi (Google Map Style)",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // 4. Bảng thông tin đối phương và nút điều khiển chia sẻ chạy ngầm (Dưới cùng)
        BottomDashboard(
            partnerLocation = partnerLocation,
            partnerName = partnerName,
            isServiceRunning = isServiceRunning,
            onToggleSharing = onToggleSharing,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentPairCode = pairCode,
            currentUserId = myUserId,
            currentMyName = myName,
            currentPartnerName = partnerName,
            currentMapProvider = mapProvider,
            onDismiss = { showSettingsDialog = false },
            onSave = onSaveSettings
        )
    }
}

@Composable
private fun TopOverlayHeader(
    pairCode: String,
    isConnected: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isCodeVisible by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) StatusGreen else StatusRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isConnected) "Đã kết nối Realtime" else "Đang kết nối lại...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) StatusGreen else StatusYellow
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isCodeVisible = !isCodeVisible }
                    ) {
                        val maskedCode = if (pairCode.length > 3) "${pairCode.take(3)}••••" else "••••"
                        Text(
                            text = "Phòng: ${if (isCodeVisible) pairCode else maskedCode}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isCodeVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Ẩn/Hiện mã phòng",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (pairCode.isNotBlank()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("LiveMap Room", pairCode))
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Mã phòng LiveMap của mình là: $pairCode\nHãy mở app LiveMap và nhập mã này trong Cài đặt để kết nối với mình nhé!"
                            )
                        }
                        try {
                            context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ mã phòng LiveMap"))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Đã sao chép mã phòng: $pairCode", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Chia sẻ mã phòng",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Cài đặt ghép đôi",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomDashboard(
    partnerLocation: LocationData?,
    partnerName: String,
    isServiceRunning: Boolean,
    onToggleSharing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Thông tin người ấy
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (partnerLocation != null) PartnerPink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (partnerName.isNotBlank()) partnerName.first().uppercase() else "B",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = partnerName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (partnerLocation != null) {
                            val speedKmh = partnerLocation.speed * 3.6f
                            val statusText = if (speedKmh > 3f) {
                                "Đang di chuyển: ${String.format("%.1f", speedKmh)} km/h"
                            } else {
                                "Đang dừng lại"
                            }
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        } else {
                            Text(
                                text = "Chưa có tín hiệu (Đang chờ hoặc đã ngắt kết nối)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Thông tin Pin đối phương
                partnerLocation?.let { loc ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (loc.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            contentDescription = "Pin",
                            tint = if (loc.batteryLevel <= 20) StatusRed else StatusGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${loc.batteryLevel}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Nút Bật/Tắt chạy ngầm
            Button(
                onClick = onToggleSharing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) StatusRed else PrimaryBlue
                )
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isServiceRunning) "DỪNG CHIA SẺ VỊ TRÍ" else "BẬT CHIA SẺ CHẠY NGẦM",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
