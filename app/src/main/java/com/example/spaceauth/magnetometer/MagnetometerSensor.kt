package com.example.spaceauth.magnetometer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

data class MagFeatures(
    val magX_avg: Float,
    val magY_avg: Float,
    val magZ_avg: Float
)

class MagnetometerSensor(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magneticSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val xValues = mutableListOf<Float>()
    private val yValues = mutableListOf<Float>()
    private val zValues = mutableListOf<Float>()

    fun startListening() {
        magneticSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    fun getAverageFeatures(): MagFeatures {
        val magX_avg = if (xValues.isNotEmpty()) xValues.average().toFloat() else 0f
        val magY_avg = if (yValues.isNotEmpty()) yValues.average().toFloat() else 0f
        val magZ_avg = if (zValues.isNotEmpty()) zValues.average().toFloat() else 0f

        return MagFeatures(magX_avg, magY_avg, magZ_avg)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                xValues.add(it.values[0])
                yValues.add(it.values[1])
                zValues.add(it.values[2])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Optional: handle accuracy changes if needed
    }

    fun reset() {
        xValues.clear()
        yValues.clear()
        zValues.clear()
    }
}