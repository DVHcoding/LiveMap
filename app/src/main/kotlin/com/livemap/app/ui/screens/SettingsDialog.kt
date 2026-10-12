package com.livemap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livemap.app.data.pref.AppPreferences

@Composable
fun SettingsDialog(
    currentPairCode: String,
    currentUserId: String,
    currentMyName: String,
    currentPartnerName: String,
    currentMapProvider: String,
    onDismiss: () -> Unit,
    onSave: (pairCode: String, userId: String, myName: String, partnerName: String, mapProvider: String) -> Unit
) {
    var pairCode by remember { mutableStateOf(currentPairCode) }
    var selectedUserId by remember { mutableStateOf(currentUserId) }
    var myName by remember { mutableStateOf(currentMyName) }
    var partnerName by remember { mutableStateOf(currentPartnerName) }
    var selectedMapProvider by remember { mutableStateOf(currentMapProvider) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Cài đặt ứng dụng",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Nhập cùng Mã phòng và chọn 2 vai trò khác nhau (Người A & Người B) để xem vị trí của nhau.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = pairCode,
                    onValueChange = { pairCode = it },
                    label = { Text("Mã phòng / Mã bí mật") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Thiết bị này là:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selectedUserId == AppPreferences.DEFAULT_USER_A,
                        onClick = { selectedUserId = AppPreferences.DEFAULT_USER_A },
                        label = {
                            Text(
                                text = "Người A",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 13.sp
                            )
                        }
                    )
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selectedUserId == AppPreferences.DEFAULT_USER_B,
                        onClick = { selectedUserId = AppPreferences.DEFAULT_USER_B },
                        label = {
                            Text(
                                text = "Người B",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 13.sp
                            )
                        }
                    )
                }

                OutlinedTextField(
                    value = myName,
                    onValueChange = { myName = it },
                    label = { Text("Tên của bạn") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = partnerName,
                    onValueChange = { partnerName = it },
                    label = { Text("Tên người kia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Nguồn bản đồ:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selectedMapProvider == AppPreferences.MAP_PROVIDER_OSM,
                        onClick = { selectedMapProvider = AppPreferences.MAP_PROVIDER_OSM },
                        label = {
                            Text(
                                text = "Bản đồ OSM",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 13.sp
                            )
                        }
                    )
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selectedMapProvider == AppPreferences.MAP_PROVIDER_GOOGLE,
                        onClick = { selectedMapProvider = AppPreferences.MAP_PROVIDER_GOOGLE },
                        label = {
                            Text(
                                text = "Google Maps",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(pairCode, selectedUserId, myName, partnerName, selectedMapProvider)
                    onDismiss()
                }
            ) {
                Text("Lưu cài đặt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
