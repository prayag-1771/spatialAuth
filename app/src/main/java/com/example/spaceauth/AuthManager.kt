package com.example.spaceauth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest

class AuthManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("spaceauth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "user_pin_hash"
        private const val KEY_AUTHENTICATED = "is_authenticated"
        private const val DEFAULT_PIN = "1234"
    }

    init {
        // Initialize with default PIN if none exists
        if (!hasPin()) {
            setPin(DEFAULT_PIN)
        }
    }

    fun setPin(pin: String) {
        val hashed = hash(pin)
        prefs.edit {
            putString(KEY_PIN_HASH, hashed)
        }
    }

    fun validatePin(input: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return hash(input) == savedHash
    }

    fun hasPin(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    fun setAuthenticated(state: Boolean) {
        prefs.edit {
            putBoolean(KEY_AUTHENTICATED, state)
        }
    }

    fun isAuthenticated(): Boolean {
        return prefs.getBoolean(KEY_AUTHENTICATED, false)
    }

    fun logout() {
        prefs.edit {
            putBoolean(KEY_AUTHENTICATED, false)
        }
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}