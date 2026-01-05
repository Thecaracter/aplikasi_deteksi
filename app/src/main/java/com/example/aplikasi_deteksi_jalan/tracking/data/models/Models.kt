package com.example.aplikasi_deteksi_jalan.tracking.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DATA MODELS untuk Tracking Module
 * 
 * Menggunakan Kotlinx Serialization untuk mapping JSON dari/ke Supabase
 */

/**
 * PROFILE - User atau Guardian
 */
@Serializable
data class Profile(
    val id: String,
    val email: String,
    @SerialName("full_name") 
    val fullName: String? = null,
    val role: String, // "user" or "guardian"
    @SerialName("invitation_code")
    val invitationCode: String? = null,
    @SerialName("created_at") 
    val createdAt: String? = null,
    @SerialName("updated_at") 
    val updatedAt: String? = null
)

/**
 * GUARDIAN RELATIONSHIP - Izin akses lokasi
 */
@Serializable
data class Guardian(
    val id: String? = null,
    @SerialName("user_id") 
    val userId: String,
    @SerialName("guardian_id") 
    val guardianId: String,
    val status: String = "pending", // pending, accepted, rejected
    @SerialName("created_at") 
    val createdAt: String? = null,
    @SerialName("updated_at") 
    val updatedAt: String? = null
)

/**
 * LOCATION DATA - Posisi terakhir user
 */
@Serializable
data class LocationData(
    @SerialName("user_id") 
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    @SerialName("tracking_enabled") 
    val trackingEnabled: Boolean = false,
    @SerialName("updated_at") 
    val updatedAt: String? = null
)

/**
 * GUARDIAN WITH PROFILE - Join data untuk UI
 */
@Serializable
data class GuardianWithProfile(
    val guardian: Guardian,
    val profile: Profile
)

/**
 * USER LOCATION - Location data with user info for Guardian monitoring
 */
@Serializable
data class UserLocation(
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_email")
    val userEmail: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val timestamp: String? = null
)
