package com.example.spaceauth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.spaceauth.acoustic.AcousticProcessor
import com.example.spaceauth.acoustic.AcousticSensor
import com.example.spaceauth.acoustic.MagnetometerSensor
import com.example.spaceauth.wifi.WifiScanner
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var magText: TextView
    private lateinit var wifiText: TextView
    private lateinit var acousticText: TextView
    private lateinit var scanButton: MaterialButton

    private lateinit var magSensor: MagnetometerSensor
    private var acousticSensor: AcousticSensor? = null
    private lateinit var wifiScanner: WifiScanner

    private val permissionRequestCode = 100
    private val handler = Handler(Looper.getMainLooper())

    private val magUpdateRunnable = object : Runnable {
        override fun run() {
            if (::magSensor.isInitialized) {
                val features = magSensor.getAverageFeatures()
                magText.text = String.format(
                    Locale.getDefault(),
                    "X: %.2f\nY: %.2f\nZ: %.2f",
                    features.magX_avg, features.magY_avg, features.magZ_avg
                )
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI
        magText = findViewById(R.id.magText)
        wifiText = findViewById(R.id.wifiText)
        acousticText = findViewById(R.id.acousticText)
        scanButton = findViewById(R.id.scanButton)

        // Initialize Modules
        magSensor = MagnetometerSensor(this)
        acousticSensor = AcousticSensor()
        wifiScanner = WifiScanner(this)

        magSensor.startListening()

        scanButton.setOnClickListener { checkPermissions() }

        // Live Magnetometer Update
        handler.post(magUpdateRunnable)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), permissionRequestCode)
        } else {
            startScans()
        }
    }

    private fun startScans() {
        wifiText.text = "Scanning WiFi..."
        acousticText.text = "Capturing Acoustic Signature..."
        magSensor.reset()
        
        // WiFi Scan via separate module
        wifiScanner.startScan { results ->
            runOnUiThread {
                if (results == null) {
                    wifiText.text = "WiFi Scan Failed."
                } else {
                    val sb = StringBuilder()
                    results.sortedByDescending { it.level }.take(3).forEachIndexed { index, scanResult ->
                        @Suppress("DEPRECATION")
                        sb.append("wifi${index + 1}: ${scanResult.level} dBm\n")
                    }
                    wifiText.text = if (sb.isEmpty()) "No networks found." else sb.toString()
                }
            }
        }

        // Acoustic Scan
        runAcousticTest()
    }

    private fun runAcousticTest() {
        // Capture for 3 seconds for stability
        acousticSensor?.startCapture(3000) { buffer, error ->
            if (buffer != null) {
                val fftFeatures = AcousticProcessor.extractForML(buffer, 44100)
                
                runOnUiThread {
                    acousticText.text = String.format(
                        Locale.getDefault(),
                        "fft1: %.4f\nfft2: %.4f\nfft3: %.4f\nfft4: %.4f\nfft5: %.4f",
                        fftFeatures[0], fftFeatures[1], fftFeatures[2], fftFeatures[3], fftFeatures[4]
                    )
                }
                Log.d("Acoustic", "Features: ${fftFeatures.joinToString()}")
            } else {
                runOnUiThread { acousticText.text = "Acoustic Error: ${error?.message}" }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        wifiScanner.unregister()
    }

    override fun onDestroy() {
        super.onDestroy()
        magSensor.stopListening()
        wifiScanner.unregister()
        handler.removeCallbacksAndMessages(null)
    }
}
