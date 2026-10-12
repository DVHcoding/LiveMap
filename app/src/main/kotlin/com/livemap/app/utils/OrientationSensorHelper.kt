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

    fun startListening() {
        rotationVectorSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
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
            _azimuthFlow.value = azimuthDeg
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
