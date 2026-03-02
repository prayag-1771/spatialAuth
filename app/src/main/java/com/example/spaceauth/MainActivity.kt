package com.example.spaceauth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.spaceauth.magnetometer.MagnetometerSensor
import com.example.spaceauth.wifi.WifiScanner
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var magText: TextView
    private lateinit var wifiText: TextView
    private lateinit var enrollCard: View
    private lateinit var verifyCard: View
    private lateinit var authManager: AuthManager

    private lateinit var magSensor: MagnetometerSensor
    private lateinit var wifiScanner: WifiScanner

    private val PERMISSION_CODE = 100
    private val handler = Handler(Looper.getMainLooper())
    private var updateMagRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AuthManager(this)

        if (!authManager.isAuthenticated()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Initialize UI elements
        magText = findViewById(R.id.magText)
        wifiText = findViewById(R.id.wifiText)
        enrollCard = findViewById(R.id.enrollCard)
        verifyCard = findViewById(R.id.scanButton) // Using the ID from the card
        val debugIcon: View = findViewById(R.id.debugIcon)

        // Initialize sensors
        magSensor = MagnetometerSensor(this)
        wifiScanner = WifiScanner(this)

        magSensor.startListening()

        enrollCard.setOnClickListener {
            checkPermissionAndScan()
        }

        verifyCard.setOnClickListener {
            checkPermissionAndScan()
        }

        debugIcon.setOnClickListener {
            authManager.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Update magnetometer live every 1 second
        updateMagRunnable = object : Runnable {
            override fun run() {
                val magFeatures = magSensor.getAverageFeatures()
                magText.text = String.format(
                    Locale.getDefault(),
                    "MAG: X:%.1f Y:%.1f Z:%.1f",
                    magFeatures.magX_avg,
                    magFeatures.magY_avg,
                    magFeatures.magZ_avg
                )
                handler.postDelayed(this, 1000)
            }
        }
        updateMagRunnable?.let { handler.post(it) }
    }

    private fun checkPermissionAndScan() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            performScan()
        } else {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_CODE
            )
        }
    }

    private fun performScan() {
        magSensor.reset()
        magSensor.startListening()

        wifiScanner.startScan { wifiResults ->
            val builder = StringBuilder()
            if (wifiResults.isEmpty()) {
                builder.append("No WiFi networks found.")
            } else {
                builder.append("WIFI: ")
                for (wf in wifiResults.take(2)) {
                    builder.append("${wf.ssid}(${wf.rssi}) ")
                }
            }
            wifiText.text = builder.toString()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                performScan()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        magSensor.stopListening()
        wifiScanner.stopScan()
        updateMagRunnable?.let { handler.removeCallbacks(it) }
    }
}
