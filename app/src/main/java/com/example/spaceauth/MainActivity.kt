package com.example.spaceauth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.spaceauth.acoustic.AcousticProcessor
import com.example.spaceauth.acoustic.AcousticSensor
import com.example.spaceauth.acoustic.MagnetometerSensor
import com.example.spaceauth.api.AuthRequest
import com.example.spaceauth.api.EnrollmentRequest
import com.example.spaceauth.api.RetrofitClient
import com.example.spaceauth.wifi.WifiScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var magText: TextView
    private lateinit var wifiText: TextView
    private lateinit var acousticText: TextView
    private lateinit var statusProgressText: TextView
    private lateinit var dataLogText: TextView
    private lateinit var roomSpinner: Spinner
    private lateinit var roomInput: EditText
    
    private lateinit var enrollButton: Button
    private lateinit var authenticateButton: Button
    
    private lateinit var folderPublic: View
    private lateinit var folderSecure1: View
    private lateinit var folderSecure2: View

    private lateinit var magSensor: MagnetometerSensor
    private var acousticSensor: AcousticSensor? = null
    private lateinit var wifiScanner: WifiScanner

    private val permissionRequestCode = 100
    private val handler = Handler(Looper.getMainLooper())

    private var currentMagData = floatArrayOf(0f, 0f, 0f)
    private var currentWifiData = floatArrayOf(-100f, -100f, -100f)
    private var currentAcousticData = floatArrayOf(0f, 0f, 0f, 0f, 0f)
    
    private var selectedRoomIdForAuth: String = ""
    private var existingRooms: List<String> = emptyList()
    
    private var isRecentlyAuthenticated: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        magText = findViewById(R.id.magText)
        wifiText = findViewById(R.id.wifiText)
        acousticText = findViewById(R.id.acousticText)
        statusProgressText = findViewById(R.id.statusProgressText)
        dataLogText = findViewById(R.id.dataLogText)
        roomSpinner = findViewById(R.id.roomSpinner)
        roomInput = findViewById(R.id.roomInput)
        enrollButton = findViewById(R.id.enrollButton)
        authenticateButton = findViewById(R.id.authenticateButton)
        
        folderPublic = findViewById(R.id.folderPublic)
        folderSecure1 = findViewById(R.id.folderSecure1)
        folderSecure2 = findViewById(R.id.folderSecure2)

        magSensor = MagnetometerSensor(this)
        acousticSensor = AcousticSensor()
        wifiScanner = WifiScanner(this)

        magSensor.startListening()

        enrollButton.setOnClickListener { handleEnrollmentClick() }
        authenticateButton.setOnClickListener { startCollectionSequence(isEnrollment = false) }

        folderPublic.setOnClickListener { showFolderContent("Public Documents", "This data is accessible to anyone.\n\n- Readme.txt\n- Public_Key.asc") }
        
        folderSecure1.setOnClickListener { handleSecureFolderClick("Financial Records", "BALANCE: $5,240.00\nLAST TRANSACTION: -$40.00 (Cafeteria)") }
        folderSecure2.setOnClickListener { handleSecureFolderClick("System Logs", "TRACE: User logged in from unauthorized location\nERROR: Magnetometer jitter detected") }

        roomSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRoomIdForAuth = existingRooms[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        handler.post(object : Runnable {
            override fun run() {
                val features = magSensor.getAverageFeatures()
                currentMagData = floatArrayOf(features.magX_avg, features.magY_avg, features.magZ_avg)
                magText.text = String.format(Locale.getDefault(), "MAG: X:%.1f Y:%.1f Z:%.1f", 
                    currentMagData[0], currentMagData[1], currentMagData[2])
                handler.postDelayed(this, 1000)
            }
        })
        
        fetchRooms()
    }

    private fun handleSecureFolderClick(title: String, content: String) {
        if (isRecentlyAuthenticated) {
            showFolderContent(title, content)
        } else {
            Toast.makeText(this, "AUTHENTICATION_REQUIRED: Use SECURE_AUTHENTICATE first", Toast.LENGTH_LONG).show()
        }
    }

    private fun showFolderContent(title: String, content: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(content)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun fetchRooms() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getEnrolledRooms()
                if (response.isSuccessful) {
                    existingRooms = response.body()?.rooms ?: emptyList()
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, existingRooms)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    roomSpinner.adapter = adapter
                    if (existingRooms.isNotEmpty()) selectedRoomIdForAuth = existingRooms[0]
                }
            } catch (e: Exception) {
                Log.e("DEBUG_API", "Failed to fetch rooms", e)
            }
        }
    }

    private fun handleEnrollmentClick() {
        val newRoomName = roomInput.text.toString().trim()
        if (newRoomName.isEmpty()) {
            Toast.makeText(this, "Enter room name!", Toast.LENGTH_SHORT).show()
            return
        }
        startCollectionSequence(isEnrollment = true)
    }

    private fun startCollectionSequence(isEnrollment: Boolean) {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), permissionRequestCode)
        } else {
            if (isEnrollment) runEnrollmentCycle() else runAuthenticationCycle()
        }
    }

    private fun runEnrollmentCycle() {
        val roomId = roomInput.text.toString().trim()
        setButtonsEnabled(false)
        val allSamples = mutableListOf<List<Float>>()
        lifecycleScope.launch(Dispatchers.Main) {
            for (i in 1..20) {
                statusProgressText.text = "ENROLLING: $i/20"
                val sample = collectSingle11DVector()
                if (sample != null) allSamples.add(sample) else break
            }
            uploadEnrollment(roomId, allSamples)
        }
    }

    private fun runAuthenticationCycle() {
        if (selectedRoomIdForAuth.isEmpty()) return
        setButtonsEnabled(false)
        isRecentlyAuthenticated = false
        val allSamples = mutableListOf<List<Float>>()
        lifecycleScope.launch(Dispatchers.Main) {
            for (i in 1..5) {
                statusProgressText.text = "VERIFYING: $i/5"
                val sample = collectSingle11DVector()
                if (sample != null) allSamples.add(sample) else break
            }
            uploadAuthentication(selectedRoomIdForAuth, allSamples)
        }
    }

    private suspend fun collectSingle11DVector(): List<Float>? = withContext(Dispatchers.Default) {
        magSensor.reset()
        val wifiJob = withContext(Dispatchers.Main) {
            val wifiPromise = kotlinx.coroutines.CompletableDeferred<Boolean>()
            wifiScanner.startScan { results ->
                if (results != null) {
                    val sorted = results.sortedByDescending { it.level }.take(3)
                    currentWifiData = floatArrayOf(-100f, -100f, -100f)
                    sorted.forEachIndexed { index, scanResult -> currentWifiData[index] = scanResult.level.toFloat() }
                    wifiPromise.complete(true)
                } else wifiPromise.complete(false)
            }
            wifiPromise.await()
        }
        if (!wifiJob) return@withContext null

        val acousticJob = withContext(Dispatchers.Main) {
            val acousticPromise = kotlinx.coroutines.CompletableDeferred<FloatArray?>()
            acousticSensor?.startCapture(1000) { buffer, _ ->
                if (buffer != null) {
                    val features = AcousticProcessor.extractForML(buffer, 44100)
                    acousticPromise.complete(features)
                } else acousticPromise.complete(null)
            }
            acousticPromise.await()
        }
        if (acousticJob == null) return@withContext null
        currentAcousticData = acousticJob

        withContext(Dispatchers.Main) {
            wifiText.text = String.format("WIFI: w1:%.0f w2:%.0f w3:%.0f", currentWifiData[0], currentWifiData[1], currentWifiData[2])
            acousticText.text = String.format("FFT: %.3f %.3f %.3f", currentAcousticData[0]/5000f, currentAcousticData[1]/5000f, currentAcousticData[2]/5000f)
        }

        val vector = mutableListOf<Float>()
        vector.add(currentMagData[0]); vector.add(currentMagData[1]); vector.add(currentMagData[2])
        vector.add(currentWifiData[0]); vector.add(currentWifiData[1]); vector.add(currentWifiData[2])
        currentAcousticData.forEach { vector.add(it / 5000f) }
        vector
    }

    private fun uploadEnrollment(roomId: String, samples: List<List<Float>>) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.enrollRoom(EnrollmentRequest(roomId, samples))
                statusProgressText.text = if (response.isSuccessful) "ENROLL_SUCCESS: $roomId" else "ENROLL_FAILED"
                fetchRooms()
            } catch (e: Exception) { statusProgressText.text = "NETWORK_ERROR" }
            setButtonsEnabled(true)
        }
    }

    private fun uploadAuthentication(roomId: String, samples: List<List<Float>>) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.authenticateRoom(AuthRequest(roomId, samples))
                val decision = response.body()?.decision ?: "REJECT"
                statusProgressText.text = "DECISION: $decision"
                if (decision == "ACCEPT") {
                    isRecentlyAuthenticated = true
                    Toast.makeText(this@MainActivity, "✅ SYSTEM_UNLOCKED", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "❌ ACCESS_DENIED", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { statusProgressText.text = "NETWORK_ERROR" }
            setButtonsEnabled(true)
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        enrollButton.isEnabled = enabled
        authenticateButton.isEnabled = enabled
    }

    override fun onDestroy() {
        super.onDestroy()
        magSensor.stopListening()
        wifiScanner.unregister()
        handler.removeCallbacksAndMessages(null)
    }
}
