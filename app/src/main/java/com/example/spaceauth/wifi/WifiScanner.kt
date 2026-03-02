package com.example.spaceauth.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

class WifiScanner(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var isReceiverRegistered = false
    private var onResultsCallback: ((List<ScanResult>?) -> Unit)? = null

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val results = try {
                wifiManager.scanResults
            } catch (e: SecurityException) {
                null
            }
            onResultsCallback?.invoke(results)
            unregister()
        }
    }

    fun startScan(callback: (List<ScanResult>?) -> Unit) {
        this.onResultsCallback = callback
        
        if (!isReceiverRegistered) {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(wifiReceiver, filter)
            }
            isReceiverRegistered = true
        }

        try {
            @Suppress("DEPRECATION")
            val success = wifiManager.startScan()
            if (!success) {
                // If scan request was throttled or failed, return last known results
                onResultsCallback?.invoke(wifiManager.scanResults)
                unregister()
            }
        } catch (e: SecurityException) {
            onResultsCallback?.invoke(null)
            unregister()
        }
    }

    fun unregister() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiReceiver)
            } catch (e: Exception) {
                // Already unregistered or other issue
            }
            isReceiverRegistered = false
        }
    }
}
