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

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    private lateinit var wifiManager: WifiManager

    private val LOCATION_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        scanButton = findViewById(R.id.scanButton)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        scanButton.setOnClickListener {
            checkPermission()
        }
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            scanWifi()
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

    private fun scanWifi() {

        if (!wifiManager.isWifiEnabled) {
            statusText.text = "Please enable WiFi first."
            return
        }

        wifiManager.startScan()
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

        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                scanWifi()
            } else {
                statusText.text = "Permission Denied."
            }
        }
    }
}