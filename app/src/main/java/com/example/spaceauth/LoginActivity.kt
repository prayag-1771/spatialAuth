package com.example.spaceauth

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Security Hardening: Prevent screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        authManager = AuthManager(this)

        // We removed the auto-skip check to ensure the login page 
        // always appears first when the app is started.

        setContentView(R.layout.activity_login)

        val loginButton = findViewById<Button>(R.id.loginButton)
        loginButton.setOnClickListener {
            // Any credentials grant access as requested
            authManager.setAuthenticated(true)
            goToDashboard()
        }

        // Prevent bypass via back gesture/button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing to prevent login bypass
            }
        })
    }

    private fun goToDashboard() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}