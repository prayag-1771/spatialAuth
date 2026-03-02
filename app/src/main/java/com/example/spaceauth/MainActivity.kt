package com.example.spaceauth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.spaceauth.magnetometer.MagnetometerSensor

class MainActivity : AppCompatActivity() {

    private lateinit var magText: TextView

    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    private lateinit var wifiManager: WifiManager
    private lateinit var magSensor: MagnetometerSensor

    private val LOCATION_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        magText = findViewById(R.id.magText) // Add this TextView in your activity_main.xml
        magSensor = MagnetometerSensor(this)
        magSensor.startListening()

        statusText = findViewById(R.id.statusText)
        scanButton = findViewById(R.id.scanButton)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        magSensor = MagnetometerSensor(this)

        scanButton.setOnClickListener {
            checkPermission()
        }
        handler.post(updateMagRunnable)
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startScan()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ),
                LOCATION_PERMISSION_CODE
            )
        }
    }

    private fun startScan() {
        statusText.text = "Scanning WiFi and Magnetometer..."

        // --- Magnetometer Scan ---
        magSensor.reset()
        magSensor.startListening()

        // Collect magnetometer data for 5 seconds
        scanButton.postDelayed({
            val magFeatures = magSensor.getAverageFeatures()
            magSensor.stopListening()

            // --- WiFi Scan ---
            val wifiResults = scanWifi()

            // Combine or show separately
            statusText.text = """
                Magnetometer:
                X: ${magFeatures.magX_avg}
                Y: ${magFeatures.magY_avg}
                Z: ${magFeatures.magZ_avg}
                
                WiFi Networks:
                $wifiResults
            """.trimIndent()

        }, 5000)
    }

    private fun scanWifi(): String {
        // Explicitly check permission again
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Location permission not granted."
        }

        if (!wifiManager.isWifiEnabled) {
            return "Please enable WiFi first."
        }

        try {
            wifiManager.startScan()
            val results = wifiManager.scanResults

            if (results.isEmpty()) {
                return "No networks found. Ensure Location is ON."
            }

            val builder = StringBuilder()
            for (result in results.take(5)) {
                builder.append("SSID: ${result.SSID}, RSSI: ${result.level} dBm\n")
            }

            return builder.toString()
        } catch (e: SecurityException) {
            return "Cannot scan WiFi: permission denied."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startScan()
            } else {
                statusText.text = "Permission Denied."
            }
        }
    }
    private val handler = android.os.Handler()
    private val updateMagRunnable = object : Runnable {
        override fun run() {
            val magFeatures = magSensor.getAverageFeatures()
            magText.text = "Magnetometer\nX: ${magFeatures.magX_avg}\nY: ${magFeatures.magY_avg}\nZ: ${magFeatures.magZ_avg}"

            handler.postDelayed(this, 1000) // update every 1 second
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        magSensor.stopListening()
        handler.removeCallbacks(updateMagRunnable)
    }
}