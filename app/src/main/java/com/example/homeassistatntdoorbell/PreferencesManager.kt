package com.example.homeassistatntdoorbell

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages app preferences with secure storage for sensitive data like access tokens
 */
class PreferencesManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "doorbell_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to regular SharedPreferences if encrypted prefs fail
        context.getSharedPreferences("doorbell_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_HA_URL = "ha_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_DOORBELL_ENTITY = "doorbell_entity"
        private const val KEY_LOCK_ENTITY = "lock_entity"
        private const val KEY_CAMERA_URL = "camera_url"
        private const val KEY_SCRYPTED_TIMELINE_URL = "scrypted_timeline_url"
        private const val KEY_AUTO_DISMISS_SECONDS = "auto_dismiss_seconds"
        private const val KEY_SERVICE_ENABLED = "service_enabled"

        // Default values
        const val DEFAULT_DOORBELL_ENTITY = "binary_sensor.doorbell_visitor"
        const val DEFAULT_LOCK_ENTITY = "lock.front_door_lock"
        const val DEFAULT_CAMERA_URL = "/api/camera_proxy/camera.doorbell_clear"
        const val DEFAULT_AUTO_DISMISS_SECONDS = 30
    }

    // Home Assistant URL
    var haUrl: String
        get() = prefs.getString(KEY_HA_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HA_URL, value.trimEnd('/')).apply()

    // Access Token
    var accessToken: String
        get() = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    // Doorbell Entity ID
    var doorbellEntity: String
        get() = prefs.getString(KEY_DOORBELL_ENTITY, DEFAULT_DOORBELL_ENTITY) ?: DEFAULT_DOORBELL_ENTITY
        set(value) = prefs.edit().putString(KEY_DOORBELL_ENTITY, value).apply()

    // Lock Entity ID
    var lockEntity: String
        get() = prefs.getString(KEY_LOCK_ENTITY, DEFAULT_LOCK_ENTITY) ?: DEFAULT_LOCK_ENTITY
        set(value) = prefs.edit().putString(KEY_LOCK_ENTITY, value).apply()

    // Camera URL
    var cameraUrl: String
        get() = prefs.getString(KEY_CAMERA_URL, DEFAULT_CAMERA_URL) ?: DEFAULT_CAMERA_URL
        set(value) = prefs.edit().putString(KEY_CAMERA_URL, value).apply()

    // Scrypted Timeline URL Template
    var scryptedTimelineUrl: String
        get() = prefs.getString(KEY_SCRYPTED_TIMELINE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCRYPTED_TIMELINE_URL, value).apply()

    // Auto-dismiss timeout
    var autoDismissSeconds: Int
        get() = prefs.getInt(KEY_AUTO_DISMISS_SECONDS, DEFAULT_AUTO_DISMISS_SECONDS)
        set(value) = prefs.edit().putInt(KEY_AUTO_DISMISS_SECONDS, value).apply()

    // Service enabled status
    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putString(KEY_SERVICE_ENABLED, value.toString()).apply()

    /**
     * Check if all required settings are configured
     */
    fun isConfigured(): Boolean {
        return haUrl.isNotBlank() &&
               accessToken.isNotBlank() &&
               doorbellEntity.isNotBlank()
    }

    /**
     * Get full camera URL (prepends HA URL if relative)
     */
    fun getFullCameraUrl(): String {
        return if (cameraUrl.startsWith("http")) {
            cameraUrl
        } else {
            "$haUrl$cameraUrl"
        }
    }

    /**
     * Clear all stored preferences
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
