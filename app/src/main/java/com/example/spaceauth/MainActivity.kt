package com.example.spaceauth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
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
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var magText: TextView
    private lateinit var wifiText: TextView
    private lateinit var acousticText: TextView
    private lateinit var statusProgressText: TextView
    private lateinit var roomSpinner: AutoCompleteTextView
    
    private lateinit var enrollButton: MaterialButton
    private lateinit var authenticateButton: MaterialButton

    private lateinit var magSensor: MagnetometerSensor
    private var acousticSensor: AcousticSensor? = null
    private lateinit var wifiScanner: WifiScanner

    private val permissionRequestCode = 100
    private val handler = Handler(Looper.getMainLooper())

    private var currentMagData = floatArrayOf(0f, 0f, 0f)
    private var currentWifiData = floatArrayOf(-100f, -100f, -100f)
    private var currentAcousticData = floatArrayOf(0f, 0f, 0f, 0f, 0f)
    
    private var selectedRoomId: String = "default_room"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        magText = findViewById(R.id.magText)
        wifiText = findViewById(R.id.wifiText)
        acousticText = findViewById(R.id.acousticText)
        statusProgressText = findViewById(R.id.statusProgressText)
        roomSpinner = findViewById(R.id.roomSpinner)
        enrollButton = findViewById(R.id.enrollButton)
        authenticateButton = findViewById(R.id.authenticateButton)

        magSensor = MagnetometerSensor(this)
        acousticSensor = AcousticSensor()
        wifiScanner = WifiScanner(this)

        magSensor.startListening()

        enrollButton.setOnClickListener { startCollectionSequence(isEnrollment = true) }
        authenticateButton.setOnClickListener { startCollectionSequence(isEnrollment = false) }

        // Setup Spinner
        roomSpinner.setOnItemClickListener { parent, _, position, _ ->
            selectedRoomId = parent.getItemAtPosition(position).toString()
            Log.d("UI", "Selected Room: $selectedRoomId")
        }

        handler.post(object : Runnable {
            override fun run() {
                val features = magSensor.getAverageFeatures()
                currentMagData = floatArrayOf(features.magX_avg, features.magY_avg, features.magZ_avg)
                magText.text = String.format(Locale.getDefault(), "X: %.2f\nY: %.2f\nZ: %.2f", 
                    currentMagData[0], currentMagData[1], currentMagData[2])
                handler.postDelayed(this, 1000)
            }
        })
        
        fetchRooms()
    }

    private fun fetchRooms() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getEnrolledRooms()
                if (response.isSuccessful) {
                    val rooms = response.body()?.rooms ?: emptyList()
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, rooms)
                    roomSpinner.setAdapter(adapter)
                    if (rooms.isNotEmpty()) {
                        roomSpinner.setText(rooms[0], false)
                        selectedRoomId = rooms[0]
                    }
                }
            } catch (e: Exception) {
                Log.e("API", "Failed to fetch rooms", e)
            }
        }
    }

    private fun startCollectionSequence(isEnrollment: Boolean) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE
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
            if (isEnrollment) runEnrollmentCycle() else runAuthenticationCycle()
        }
    }

    private fun runEnrollmentCycle() {
        setButtonsEnabled(false)
        val allSamples = mutableListOf<List<Float>>()
        val totalSamplesNeeded = 20

        lifecycleScope.launch(Dispatchers.Main) {
            for (i in 1..totalSamplesNeeded) {
                statusProgressText.text = "Collecting Enrollment Sample $i/$totalSamplesNeeded..."
                val sample = collectSingle11DVector()
                if (sample != null) {
                    allSamples.add(sample)
                } else {
                    statusProgressText.text = "Error in sample collection."
                    setButtonsEnabled(true)
                    return@launch
                }
            }
            uploadEnrollment(selectedRoomId, allSamples)
        }
    }

    private fun runAuthenticationCycle() {
        setButtonsEnabled(false)
        val allSamples = mutableListOf<List<Float>>()
        val totalSamplesNeeded = 5

        lifecycleScope.launch(Dispatchers.Main) {
            for (i in 1..totalSamplesNeeded) {
                statusProgressText.text = "Collecting Auth Sample $i/$totalSamplesNeeded..."
                val sample = collectSingle11DVector()
                if (sample != null) {
                    allSamples.add(sample)
                } else {
                    statusProgressText.text = "Error in collection."
                    setButtonsEnabled(true)
                    return@launch
                }
            }
            uploadAuthentication(selectedRoomId, allSamples)
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
                    sorted.forEachIndexed { index, scanResult ->
                        currentWifiData[index] = scanResult.level.toFloat()
                    }
                    wifiPromise.complete(true)
                } else {
                    wifiPromise.complete(false)
                }
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
                } else {
                    acousticPromise.complete(null)
                }
            }
            acousticPromise.await()
        }

        if (acousticJob == null) return@withContext null
        currentAcousticData = acousticJob

        withContext(Dispatchers.Main) {
            wifiText.text = String.format("w1: %.0f, w2: %.0f, w3: %.0f", currentWifiData[0], currentWifiData[1], currentWifiData[2])
            acousticText.text = String.format("f1: %.2f f2: %.2f f3: %.2f\nf4: %.2f f5: %.2f", 
                currentAcousticData[0], currentAcousticData[1], currentAcousticData[2],
                currentAcousticData[3], currentAcousticData[4])
        }

        val vector = mutableListOf<Float>()
        vector.add(currentMagData[0])
        vector.add(currentMagData[1])
        vector.add(currentMagData[2])
        vector.add(currentWifiData[0])
        vector.add(currentWifiData[1])
        vector.add(currentWifiData[2])
        vector.addAll(currentAcousticData.toList())
        vector
    }

    private fun uploadEnrollment(roomId: String, samples: List<List<Float>>) {
        statusProgressText.text = "Uploading enrollment data for $roomId..."
        lifecycleScope.launch {
            try {
                val request = EnrollmentRequest(roomId, samples)
                Log.d("API", "Enroll Request Body: $request")
                
                val response = RetrofitClient.api.enrollRoom(request)
                if (response.isSuccessful) {
                    statusProgressText.text = "Success: Room $roomId Enrolled!"
                    Toast.makeText(this@MainActivity, "Enrolled Room: $roomId", Toast.LENGTH_LONG).show()
                    fetchRooms() // Refresh the dropdown
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown Error"
                    statusProgressText.text = "API Error (${response.code()}): $errorMsg"
                    Log.e("API_ERROR", "Code: ${response.code()}, Body: $errorMsg")
                }
            } catch (e: Exception) {
                statusProgressText.text = "Network Error: ${e.localizedMessage}"
                Log.e("API_ERROR", "Exception during enrollment", e)
            }
            setButtonsEnabled(true)
        }
    }

    private fun uploadAuthentication(roomId: String, samples: List<List<Float>>) {
        statusProgressText.text = "Verifying against $roomId..."
        lifecycleScope.launch {
            try {
                val request = AuthRequest(roomId, samples)
                Log.d("API", "Auth Request Body: $request")
                
                val response = RetrofitClient.api.authenticateRoom(request)
                if (response.isSuccessful) {
                    val decision = response.body()?.decision ?: "REJECT"
                    Log.d("API", "Response Body: ${response.body()}")
                    statusProgressText.text = "Result for $roomId: $decision"
                    
                    if (decision == "ACCEPT") {
                        Toast.makeText(this@MainActivity, "✅ ACCESS GRANTED", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ ACCESS DENIED", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown Error"
                    statusProgressText.text = "API Error (${response.code()}): $errorMsg"
                    Log.e("API_ERROR", "Code: ${response.code()}, Body: $errorMsg")
                }
            } catch (e: Exception) {
                statusProgressText.text = "Network Error: ${e.localizedMessage}"
                Log.e("API_ERROR", "Exception during auth", e)
            }
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
