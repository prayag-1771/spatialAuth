package com.example.spaceauth.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

data class WifiFeature(
    val ssid: String,
    val rssi: Int
)

class WifiScanner(private val context: Context) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var scanResultsCallback: ((List<WifiFeature>) -> Unit)? = null
    private var isReceiverRegistered = false

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
                val results = wifiManager?.scanResults
                if (results == null) {
                    scanResultsCallback?.invoke(emptyList())
                    return
                }

                val topResults = results
                    .sortedByDescending { it.level }
                    .distinctBy { it.SSID }
                    .filter { !it.SSID.isNullOrEmpty() }
                    .take(3)

                val wifiFeatures = topResults.map { WifiFeature(it.SSID, it.level) }
                scanResultsCallback?.invoke(wifiFeatures)
            } catch (e: Exception) {
                scanResultsCallback?.invoke(emptyList())
            }
        }
    }

    fun startScan(callback: (List<WifiFeature>) -> Unit) {
        scanResultsCallback = callback

        if (wifiManager == null) {
            callback(emptyList())
            return
        }

        // Check location permission (Required for WiFi scanning)
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback(emptyList())
            return
        }

        if (!isReceiverRegistered) {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(scanReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (e: Exception) {
                // Handle registration failure
            }
        }

        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (e: Exception) {
            // Handle startScan failure
        }
    }

    fun stopScan() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(scanReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            isReceiverRegistered = false
        }
    }
}
