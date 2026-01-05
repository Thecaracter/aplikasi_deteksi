package com.example.aplikasi_deteksi_jalan.tracking.data.repository

import com.example.aplikasi_deteksi_jalan.tracking.data.SupabaseClientManager
import com.example.aplikasi_deteksi_jalan.tracking.data.models.LocationData
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * LOCATION REPOSITORY
 * 
 * Handle CRUD location data & realtime subscription
 * 
 * DESIGN:
 * - User: Hanya UPDATE lokasi (via LocationManager, bukan via repository)
 * - Guardian: Subscribe realtime updates ON-DEMAND
 */
class LocationRepository {
    
    private val client = SupabaseClientManager.client
    private var realtimeChannel: RealtimeChannel? = null
    
    /**
     * GET LOCATIONS OF USERS YANG MEMBERI IZIN
     * 
     * Untuk guardian melihat lokasi semua user yang accepted
     * 
     * @return List<LocationData> lokasi semua user
     */
    suspend fun getMonitoredUsersLocations(): Result<List<LocationData>> {
        return try {
            val guardianId = client.auth.currentUserOrNull()?.id
                ?: return Result.failure(Exception("Guardian not logged in"))
            
            // Query lokasi user yang:
            // 1. Guardian sudah accepted
            // 2. Tracking enabled = TRUE
            val locations = client.from("location_current")
                .select {
                    filter {
                        // Filter by tracking enabled
                        eq("tracking_enabled", true)
                    }
                }
                .decodeList<LocationData>()
            
            // Filter lagi di client side (karena RLS sudah handle di server)
            // RLS policy guardian: hanya bisa lihat user yang memberi izin
            
            println("[LocationRepo] ✅ Found ${locations.size} locations")
            Result.success(locations)
            
        } catch (e: Exception) {
            println("[LocationRepo] ❌ Error getting locations: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * GET SINGLE USER LOCATION
     * 
     * @param userId ID user yang akan di-query lokasinya
     */
    suspend fun getUserLocation(userId: String): Result<LocationData?> {
        return try {
            val locations = client.from("location_current")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<LocationData>()
            
            if (locations.isEmpty()) {
                Result.success(null)
            } else {
                Result.success(locations.first())
            }
            
        } catch (e: Exception) {
            println("[LocationRepo] ❌ Error getting user location: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * SUBSCRIBE REALTIME LOCATION UPDATES
     * 
     * ON-DEMAND subscription:
     * - Hanya dipanggil saat guardian buka monitoring screen
     * - Unsubscribe saat guardian tutup screen
     * 
     * @return Flow<LocationData> stream lokasi updates
     */
    suspend fun subscribeLocationUpdates(): Result<Flow<LocationData>> {
        return try {
            val guardianId = client.auth.currentUserOrNull()?.id
                ?: return Result.failure(Exception("Guardian not logged in"))
            
            // Create realtime channel
            val myChannel = client.realtime.channel("location_updates")
            
            // Subscribe to location_current table changes
            val flow = myChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "location_current"
            }
            
            // Subscribe channel
            myChannel.subscribe()
            
            // Save channel reference untuk unsubscribe nanti
            realtimeChannel = myChannel
            
            println("[LocationRepo] ✅ Subscribed to realtime location updates")
            Result.success(flow.map { LocationData("", 0.0, 0.0, 0f, false) })
            
        } catch (e: Exception) {
            println("[LocationRepo] ❌ Error subscribing realtime: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * UNSUBSCRIBE REALTIME UPDATES
     * 
     * WAJIB dipanggil saat guardian tutup monitoring screen
     * untuk menghindari memory leak & battery drain
     */
    suspend fun unsubscribeLocationUpdates() {
        try {
            realtimeChannel?.let { channel ->
                client.realtime.removeChannel(channel)
                realtimeChannel = null
                println("[LocationRepo] ✅ Unsubscribed from realtime updates")
            }
        } catch (e: Exception) {
            println("[LocationRepo] ❌ Error unsubscribing: ${e.message}")
        }
    }
    
    /**
     * UPDATE TRACKING STATUS (Enable/Disable)
     * 
     * @param enabled true untuk enable, false untuk disable
     */
    suspend fun updateTrackingStatus(enabled: Boolean): Result<Unit> {
        return try {
            val userId = client.auth.currentUserOrNull()?.id
                ?: return Result.failure(Exception("User not logged in"))
            
            client.from("location_current")
                .update(mapOf("tracking_enabled" to enabled)) {
                    filter {
                        eq("user_id", userId)
                    }
                }
            
            println("[LocationRepo] ✅ Tracking status updated: $enabled")
            Result.success(Unit)
            
        } catch (e: Exception) {
            println("[LocationRepo] ❌ Error updating tracking status: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * GET USERS LOCATIONS - For Guardian monitoring with realtime updates
     * 
     * @param userIds List of user IDs to monitor
     * @return Flow of UserLocation list with real-time updates
     */
    suspend fun getUsersLocations(userIds: List<String>): kotlinx.coroutines.flow.Flow<List<com.example.aplikasi_deteksi_jalan.tracking.data.models.UserLocation>> {
        return kotlinx.coroutines.flow.flow {
            // Initial query to get current locations
            val initialLocations = mutableListOf<com.example.aplikasi_deteksi_jalan.tracking.data.models.UserLocation>()
            
            println("[LocationRepo] 🔍 Fetching locations for ${userIds.size} users: $userIds")
            
            for (userId in userIds) {
                try {
                    // Query location_current - RLS will filter by tracking_enabled=true and guardian relationship
                    val locationResult = client.from("location_current")
                        .select {
                            filter {
                                eq("user_id", userId)
                            }
                        }
                        .decodeList<LocationData>()
                    
                    println("[LocationRepo] 📍 User $userId: found ${locationResult.size} location entries")
                    
                    if (locationResult.isNotEmpty()) {
                        val locationData = locationResult.first()
                        println("[LocationRepo] 📍 Location data: lat=${locationData.latitude}, lng=${locationData.longitude}, tracking_enabled=${locationData.trackingEnabled}")
                        
                        // Get user email from profiles
                        val profileResult = client.from("profiles")
                            .select {
                                filter {
                                    eq("id", userId)
                                }
                            }
                            .decodeList<com.example.aplikasi_deteksi_jalan.tracking.data.models.Profile>()
                        
                        val userEmail = profileResult.firstOrNull()?.email ?: "Unknown"
                        println("[LocationRepo] 👤 User email: $userEmail")
                        
                        initialLocations.add(
                            com.example.aplikasi_deteksi_jalan.tracking.data.models.UserLocation(
                                userId = locationData.userId,
                                userEmail = userEmail,
                                latitude = locationData.latitude,
                                longitude = locationData.longitude,
                                accuracy = locationData.accuracy,
                                timestamp = locationData.updatedAt
                            )
                        )
                    } else {
                        println("[LocationRepo] ⚠️ User $userId: No location data found (not tracking or RLS blocked)")
                    }
                } catch (e: Exception) {
                    println("[LocationRepo] ❌ Error getting location for user $userId: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            println("[LocationRepo] ✅ Emitting ${initialLocations.size} locations")
            emit(initialLocations)
            
            // Set up realtime subscription for updates
            try {
                val channel = client.realtime.channel("user_locations")
                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "location_current"
                }
                
                channel.subscribe()
                
                changeFlow.collect { action ->
                    // Re-query all locations when any change occurs
                    val updatedLocations = mutableListOf<com.example.aplikasi_deteksi_jalan.tracking.data.models.UserLocation>()
                    
                    for (userId in userIds) {
                        try {
                            val locationResult = client.from("location_current")
                                .select {
                                    filter {
                                        eq("user_id", userId)
                                    }
                                }
                                .decodeList<LocationData>()
                            
                            if (locationResult.isNotEmpty()) {
                                val locationData = locationResult.first()
                                
                                val profileResult = client.from("profiles")
                                    .select {
                                        filter {
                                            eq("id", userId)
                                        }
                                    }
                                    .decodeList<com.example.aplikasi_deteksi_jalan.tracking.data.models.Profile>()
                                
                                val userEmail = profileResult.firstOrNull()?.email ?: "Unknown"
                                
                                updatedLocations.add(
                                    com.example.aplikasi_deteksi_jalan.tracking.data.models.UserLocation(
                                        userId = locationData.userId,
                                        userEmail = userEmail,
                                        latitude = locationData.latitude,
                                        longitude = locationData.longitude,
                                        accuracy = locationData.accuracy,
                                        timestamp = locationData.updatedAt
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            println("[LocationRepo] ❌ Error in realtime update: ${e.message}")
                        }
                    }
                    
                    emit(updatedLocations)
                }
            } catch (e: Exception) {
                println("[LocationRepo] ❌ Error setting up realtime: ${e.message}")
            }
        }
    }
    
    /**
     * GET USERS LOCATIONS ONCE (No Flow, for polling)
     * 
     * Single query untuk polling setiap 5 detik
     * Tidak perlu WebSocket/Realtime
     */
    suspend fun getUsersLocationsOnce(userIds: List<String>): List<com.example.aplikasi_deteksi_jalan.tracking.data.models.UserLocation> {
        return try {
            val locations = mutableListOf<com.example.aplikasi_deteksi_jalan.tracking.data.models.UserLocation>()
            
            for (userId in userIds) {
                try {
                    val locationResult = client.from("location_current")
                        .select {
                            filter {
                                eq("user_id", userId)
                            }
                        }
                        .decodeList<LocationData>()
                    
                    if (locationResult.isNotEmpty()) {
                        val locationData = locationResult.first()
                        
                        val profileResult = client.from("profiles")
                            .select {
                                filter {
                                    eq("id", userId)
                                }
                            }
                            .decodeList<com.example.aplikasi_deteksi_jalan.tracking.data.models.Profile>()
                        
                        val userEmail = profileResult.firstOrNull()?.email ?: "Unknown"
                        
                        locations.add(
                            com.example.aplikasi_deteksi_jalan.tracking.data.models.UserLocation(
                                userId = locationData.userId,
                                userEmail = userEmail,
                                latitude = locationData.latitude,
                                longitude = locationData.longitude,
                                accuracy = locationData.accuracy,
                                timestamp = locationData.updatedAt
                            )
                        )
                    }
                } catch (e: Exception) {
                    println("[LocationRepo] ❌ Error fetching location for user $userId: ${e.message}")
                }
            }
            
            locations
        } catch (e: Exception) {
            println("[LocationRepo] ❌ Error in getUsersLocationsOnce: ${e.message}")
            emptyList()
        }
    }
}
