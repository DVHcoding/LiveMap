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
    onDismiss: () -> Unit,
    onSave: (pairCode: String, userId: String, myName: String, partnerName: String) -> Unit
) {
    var pairCode by remember { mutableStateOf(currentPairCode) }
    var selectedUserId by remember { mutableStateOf(currentUserId) }
    var myName by remember { mutableStateOf(currentMyName) }
    var partnerName by remember { mutableStateOf(currentPartnerName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Cài đặt ghép đôi 2 người",
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
                    text = "Chỉ cần 2 máy nhập cùng Mã ghép đôi và chọn 2 vai trò khác nhau (1 máy chọn Người A, 1 máy chọn Người B) là sẽ tự động thấy nhau.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 18.sp
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
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedUserId == AppPreferences.DEFAULT_USER_A,
                        onClick = { selectedUserId = AppPreferences.DEFAULT_USER_A },
                        label = { Text("Người A (Thiết bị 1)") }
                    )
                    FilterChip(
                        selected = selectedUserId == AppPreferences.DEFAULT_USER_B,
                        onClick = { selectedUserId = AppPreferences.DEFAULT_USER_B },
                        label = { Text("Người B (Thiết bị 2)") }
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(pairCode, selectedUserId, myName, partnerName)
                    onDismiss()
                }
            ) {
                Text("Lưu & Kết nối")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
