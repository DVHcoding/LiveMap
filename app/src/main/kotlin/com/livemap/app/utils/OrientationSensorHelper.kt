package com.livemap.app.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OrientationSensorHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _azimuthFlow = MutableStateFlow(0f)
    val azimuthFlow: StateFlow<Float> = _azimuthFlow.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var lastFilteredAzimuth = -1f

    fun startListening() {
        rotationVectorSensor?.let { sensor ->
            // SENSOR_DELAY_UI để giảm tải CPU và loại bỏ nhiễu tần số cao của GAME delay
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        lastFilteredAzimuth = -1f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            // azimuth theo radian chuyển sang độ (0 - 360)
            val azimuthRad = orientationAngles[0]
            var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            if (azimuthDeg < 0) {
                azimuthDeg += 360f
            }

            if (lastFilteredAzimuth < 0f) {
                lastFilteredAzimuth = azimuthDeg
                _azimuthFlow.value = azimuthDeg
                return
            }

            // Tính độ chênh lệch góc theo vòng tròn 360 độ [-180, 180]
            var diff = (azimuthDeg - lastFilteredAzimuth) % 360f
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f

            // Khử rung tay và nhiễu cảm biến (Deadband 1.0 độ): nếu rung nhẹ thì giữ nguyên đèn pin
            if (kotlin.math.abs(diff) < 1.0f) return

            // Lọc thông thấp mượt mà (alpha = 0.35)
            lastFilteredAzimuth = (lastFilteredAzimuth + diff * 0.35f + 360f) % 360f
            _azimuthFlow.value = lastFilteredAzimuth
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
