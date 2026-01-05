package com.example.aplikasi_deteksi_jalan.tracking.manager

import android.content.Context
import android.content.SharedPreferences

/**
 * TRACKING PREFERENCES
 * 
 * Gunakan SharedPreferences untuk simpan state tracking
 * Lebih cepat dari query database
 * 
 * USE CASE:
 * - Simpan state tracking ON/OFF
 * - Restore state saat app restart
 * - Cache user ID
 */
class TrackingPreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    /**
     * Tracking enabled/disabled
     * Default: false (opt-in)
     */
    var isTrackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRACKING_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TRACKING_ENABLED, value).apply()
            println("[TrackingPrefs] Tracking enabled: $value")
        }
    
    /**
     * Current user ID (dari Supabase Auth)
     */
    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) {
            prefs.edit().putString(KEY_USER_ID, value).apply()
            println("[TrackingPrefs] User ID saved: $value")
        }
    
    /**
     * User email (untuk display)
     */
    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()
    
    /**
     * User role (user/guardian)
     */
    var userRole: String?
        get() = prefs.getString(KEY_USER_ROLE, null)
        set(value) = prefs.edit().putString(KEY_USER_ROLE, value).apply()
    
    /**
     * Last location latitude (cache)
     */
    var lastLatitude: Double
        get() = prefs.getString(KEY_LAST_LAT, "0.0")?.toDoubleOrNull() ?: 0.0
        set(value) = prefs.edit().putString(KEY_LAST_LAT, value.toString()).apply()
    
    /**
     * Last location longitude (cache)
     */
    var lastLongitude: Double
        get() = prefs.getString(KEY_LAST_LNG, "0.0")?.toDoubleOrNull() ?: 0.0
        set(value) = prefs.edit().putString(KEY_LAST_LNG, value.toString()).apply()
    
    /**
     * Last update timestamp
     */
    var lastUpdateTime: Long
        get() = prefs.getLong(KEY_LAST_UPDATE, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE, value).apply()
    
    /**
     * Clear all tracking preferences (saat logout)
     */
    fun clear() {
        prefs.edit().clear().apply()
        println("[TrackingPrefs] All preferences cleared")
    }
    
    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean = userId != null
    
    /**
     * Save user role
     */
    fun saveUserRole(role: String) {
        userRole = role
        println("[TrackingPrefs] User role saved: $role")
    }
    
    /**
     * Save login state
     */
    fun saveLoginState(isLoggedIn: Boolean, userId: String? = null, email: String? = null) {
        if (isLoggedIn && userId != null) {
            this.userId = userId
            this.userEmail = email
        } else {
            clear()
        }
        println("[TrackingPrefs] Login state saved: $isLoggedIn")
    }
    
    /**
     * Save user session data (setelah login)
     */
    fun saveUserSession(id: String, email: String, role: String) {
        prefs.edit().apply {
            putString(KEY_USER_ID, id)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_ROLE, role)
            apply()
        }
        println("[TrackingPrefs] User session saved: $email ($role)")
    }
    
    companion object {
        private const val PREFS_NAME = "tracking_prefs"
        
        private const val KEY_TRACKING_ENABLED = "tracking_enabled"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_LAST_LAT = "last_latitude"
        private const val KEY_LAST_LNG = "last_longitude"
        private const val KEY_LAST_UPDATE = "last_update_time"
    }
}
