package com.example.spaceauth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.spaceauth.acoustic.AcousticProcessor
import com.example.spaceauth.acoustic.AcousticSensor

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var acousticSensor: AcousticSensor
    private lateinit var scanButton: Button
    private lateinit var wifiManager: WifiManager

    private val permissionRequestCode = 100
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        acousticSensor = AcousticSensor()
        statusText = findViewById(R.id.statusText)
        statusText.movementMethod = ScrollingMovementMethod()
        scanButton = findViewById(R.id.scanButton)
        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager

        scanButton.setOnClickListener {
            checkPermissions()
        }
    }

    private fun startAcousticScan() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        statusText.text = "Running 10 acoustic tests...\n"
        runAcousticTest(1) // Start the first test
    }

    private fun runAcousticTest(testIndex: Int) {
        if (testIndex > 10) {
            runOnUiThread {
                statusText.append("\n\nTests complete.")
            }
            return // Stop recursion
        }

        acousticSensor.startCapture(1000) { buffer, error ->
            if (error != null) {
                Log.e("ACOUSTIC_TEST", "Error on test $testIndex: ${error.message}")
                runOnUiThread {
                    statusText.append("\nTest $testIndex failed.")
                }
            } else if (buffer != null) {
                val features = AcousticProcessor.extract(buffer, acousticSensor.sampleRate)
                Log.d(
                    "ACOUSTIC_TEST",
                    "Test $testIndex: Energy=${features.energy}, " +
                            "ZCR=${features.zeroCrossingRate}, " +
                            "Centroid=${features.spectralCentroid}"
                )
                runOnUiThread {
                    statusText.append(
                        "\nTest $testIndex:\n" +
                                "E=${features.energy}\n" +
                                "ZCR=${features.zeroCrossingRate}\n" +
                                "C=${features.spectralCentroid}\n"
                    )
                }
            }

            // Always schedule the next test. The guard at the start of the function will stop it.
            handler.postDelayed({
                runAcousticTest(testIndex + 1)
            }, 500)
        }
    }

    private fun checkPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.RECORD_AUDIO
        )
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest,
                permissionRequestCode
            )
        } else {
            startScans()
        }
    }

    private fun startScans() {
        // scanWifi()
        startAcousticScan()
    }

    private fun scanWifi() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (!wifiManager.isWifiEnabled) {
            statusText.text = "Please enable WiFi first."
            return
        }

        @Suppress("DEPRECATION")
        wifiManager.startScan()
        @Suppress("DEPRECATION")
        val results = wifiManager.scanResults

        if (results.isEmpty()) {
            statusText.text = "No networks found. Ensure Location is ON."
            return
        }

        val builder = StringBuilder()
        builder.append("Networks Found:\n\n")

        for (result in results.take(5)) {
            builder.append("SSID: ${result.SSID}\n")
            builder.append("RSSI: ${result.level} dBm\n\n")
        }

        statusText.text = builder.toString()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == permissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScans()
            } else {
                statusText.text = "Permission Denied."
            }
        }
    }
}