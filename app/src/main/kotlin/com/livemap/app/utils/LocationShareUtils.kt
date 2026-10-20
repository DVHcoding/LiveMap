package com.livemap.app.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.util.Locale

object LocationShareUtils {
    /** Sao chép tọa độ dạng "lat, lng" để dán trực tiếp vào Google Maps. */
    fun copyCoordinates(context: Context, latitude: Double, longitude: Double, label: String) {
        val coordText = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Location Coordinates", coordText))
            Toast.makeText(context, "Đã sao chép tọa độ $label:\n$coordText", Toast.LENGTH_SHORT).show()
        }
    }
}
