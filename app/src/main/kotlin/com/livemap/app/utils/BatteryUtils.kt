package com.livemap.app.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryUtils {
    data class BatteryInfo(val level: Int, val isCharging: Boolean)

    fun getBatteryInfo(context: Context): BatteryInfo {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            BatteryInfo(level = batteryPct, isCharging = isCharging)
        } catch (_: Exception) {
            BatteryInfo(level = 100, isCharging = false)
        }
    }
}
