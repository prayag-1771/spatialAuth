package com.example.spaceauth.acoustic

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

data class MagFeatures(
    val magX_avg: Float,
    val magY_avg: Float,
    val magZ_avg: Float
)

class MagnetometerSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val magneticSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var sumX = 0.0
    private var sumY = 0.0
    private var sumZ = 0.0
    private var count = 0
    private var isListening = false

    private val lock = Any()

    fun startListening() {
        if (isListening) return
        magneticSensor?.let {
            val success = sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) ?: false
            isListening = success
        }
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
    }

    fun getAverageFeatures(): MagFeatures {
        synchronized(lock) {
            return if (count > 0) {
                MagFeatures(
                    (sumX / count).toFloat(),
                    (sumY / count).toFloat(),
                    (sumZ / count).toFloat()
                )
            } else {
                MagFeatures(0f, 0f, 0f)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            synchronized(lock) {
                sumX += event.values[0]
                sumY += event.values[1]
                sumZ += event.values[2]
                count++
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun reset() {
        synchronized(lock) {
            sumX = 0.0
            sumY = 0.0
            sumZ = 0.0
            count = 0
        }
    }
}
