package com.example.aplikasi_deteksi_jalan.tracking.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.example.aplikasi_deteksi_jalan.tracking.data.SupabaseClientManager
import com.example.aplikasi_deteksi_jalan.tracking.data.models.LocationData
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*

/**
 * TRACKING LOCATION MANAGER
 * 
 * CORE PRINCIPLES:
 * 1. TIDAK mengganggu AI detection module
 * 2. Berjalan di background thread (Dispatchers.IO)
 * 3. Update MINIMAL (50-100m atau 10 menit)
 * 4. ASYNC upload ke Supabase (tidak blocking)
 * 5. Hemat baterai (BALANCED_POWER_ACCURACY)
 * 
 * THREAD SAFETY:
 * - LocationCallback di main thread (dari FusedLocationProvider)
 * - Processing & upload di IO thread (coroutines)
 * - Tidak blok AI thread
 */
class TrackingLocationManager(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    // Coroutine scope dengan SupervisorJob (crash tracking tidak crash AI)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Callback untuk UI updates saat location berubah
    private var onLocationChanged: ((latitude: Double, longitude: Double, accuracy: Float) -> Unit)? = null
    
    private var locationCallback: LocationCallback? = null
    private var lastKnownLocation: Location? = null
    private var isTracking = false
    private var periodicUploadJob: Job? = null // Timer untuk upload setiap 10 menit
    private var isDebugMode = false // Track if debug mode is active
    
    // ============================================
    // LOCATION REQUEST CONFIG (MINIMAL & HEMAT)
    // ============================================
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, // HIGH untuk real-time updates seperti debug
        5_000L // Update setiap 5 detik
    ).apply {
        setMinUpdateDistanceMeters(5f) // Update jika bergerak >= 5 meter (very sensitive)
        setGranularity(Granularity.GRANULARITY_FINE) // FINE untuk akurasi
        setWaitForAccurateLocation(false) // Tidak tunggu GPS akurat
        setMaxUpdateDelayMillis(10_000L) // Max 10 detik delay
    }.build()
    
    // DEBUG MODE: More sensitive location updates (5 meter minimum)
    private val debugLocationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, // Need HIGH for real-time debug
        5_000L // Update setiap 5 detik
    ).apply {
        setMinUpdateDistanceMeters(5f) // Update jika bergerak >= 5 meter (very sensitive)
        setGranularity(Granularity.GRANULARITY_FINE) // FINE untuk akurasi tinggi
        setWaitForAccurateLocation(false) // Tidak tunggu GPS akurat
        setMaxUpdateDelayMillis(10_000L) // Max 10 detik delay
    }.build()
    
    /**
     * START TRACKING LOKASI
     * 
     * Flow:
     * 1. Check permission
     * 2. Request location updates dari FusedLocationProvider
     * 3. LocationCallback akan dipanggil saat lokasi berubah
     * 4. Upload ke Supabase ASYNC (tidak blocking)
     */
    fun startTracking() {
        if (!hasLocationPermission()) {
            println("[TrackingLocation] ❌ ERROR: No location permission")
            return
        }
        
        if (isTracking) {
            println("[TrackingLocation] ⚠️ Already tracking")
            return
        }
        
        println("[TrackingLocation] ✅ Starting location tracking...")
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                println("[TrackingLocation] 📍 onLocationResult called with ${result.locations.size} location(s)")
                result.lastLocation?.let { location ->
                    println("[TrackingLocation] 📍 Got location: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}")
                    handleLocationUpdate(location)
                }
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                println("[TrackingLocation] 📡 Location availability: ${availability.isLocationAvailable}")
                if (!availability.isLocationAvailable) {
                    println("[TrackingLocation] ⚠️ Location not available (GPS off?)")
                }
            }
        }
        
        try {
            println("[TrackingLocation] 🔄 Requesting location updates from FusedLocationProvider...")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper() // Callback di main thread
            )
            isTracking = true
            println("[TrackingLocation] ✅ Tracking started successfully - callback registered")
            
            // For emulator testing: try to get last known location immediately
            scope.launch {
                try {
                    val lastLoc = getLastKnownLocation()
                    if (lastLoc != null) {
                        println("[TrackingLocation] 🎯 Got last known location for initial upload: ${lastLoc.latitude}, ${lastLoc.longitude}")
                        handleLocationUpdate(lastLoc)
                    }
                } catch (e: Exception) {
                    println("[TrackingLocation] ℹ️ Could not get last known location: ${e.message}")
                }
            }
            
            // Start periodic upload every 10 minutes
            startPeriodicUpload()
            
        } catch (e: SecurityException) {
            println("[TrackingLocation] ❌ SecurityException: ${e.message}")
        } catch (e: Exception) {
            println("[TrackingLocation] ❌ Error starting tracking: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * STOP TRACKING
     */
    fun stopTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            println("[TrackingLocation] ✅ Stopped tracking")
        }
        locationCallback = null
        isTracking = false
        isDebugMode = false
        
        // Cancel periodic upload
        periodicUploadJob?.cancel()
        periodicUploadJob = null
    }
    
    /**
     * ENABLE DEBUG MODE - Use sensitive location tracking
     * Updates every 5 meters (instead of 100m)
     */
    fun enableDebugMode() {
        if (!hasLocationPermission()) {
            println("[TrackingLocation] ❌ ERROR: No location permission for debug")
            return
        }
        
        if (isDebugMode) {
            println("[TrackingLocation] ⚠️ Debug mode already enabled")
            return
        }
        
        println("[TrackingLocation] 🐛 ENABLING DEBUG MODE - 5m sensitivity")
        isDebugMode = true
        
        // Remove old callback if exists
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        
        // Create new callback for debug mode
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    lastKnownLocation = location
                    // Notify UI of location change
                    onLocationChanged?.invoke(location.latitude, location.longitude, location.accuracy)
                    println("[TrackingLocation] 🐛 DEBUG: Got location: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}")
                    // In debug mode, ALWAYS update (no 100m threshold)
                    scope.launch {
                        uploadLocationToSupabase(location)
                    }
                }
            }
            
            override fun onLocationAvailability(availability: LocationAvailability) {
                println("[TrackingLocation] 📡 Location availability: ${availability.isLocationAvailable}")
            }
        }
        
        try {
            // Request with debug (sensitive) location request
            fusedLocationClient.requestLocationUpdates(
                debugLocationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            println("[TrackingLocation] ✅ Debug mode STARTED - sensitive location tracking active")
        } catch (e: SecurityException) {
            println("[TrackingLocation] ❌ SecurityException in debug: ${e.message}")
        }
    }
    
    /**
     * DISABLE DEBUG MODE
     */
    fun disableDebugMode() {
        println("[TrackingLocation] ⚫ Disabling debug mode")
        isDebugMode = false
        
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        
        // Re-request with normal location request if tracking is still on
        if (isTracking) {
            println("[TrackingLocation] 🔄 Switching back to normal tracking mode")
            startTracking()
        }
    }
    
    /**
     * HANDLE LOCATION UPDATE
     * 
     * DUAL TRIGGER UPLOAD:
     * 1. Real-time location tracking for UI (5m minimum)
     * 2. Upload only if: distance >= 100m OR periodic timer (10 min)
     * 
     * THREAD: Dipanggil di main thread, tapi proses di IO thread
     */
    private fun handleLocationUpdate(location: Location) {
        // Notify UI of location change in real-time (5m sensitivity)
        onLocationChanged?.invoke(location.latitude, location.longitude, location.accuracy)
        
        println("[TrackingLocation] 📍 New location: ${location.latitude}, ${location.longitude}")
        println("[TrackingLocation] 📏 Accuracy: ${location.accuracy}m")
        
        // CHECK DISTANCE: Upload jika bergerak >= 100 meter
        if (lastKnownLocation != null) {
            val distance = lastKnownLocation!!.distanceTo(location)
            if (distance >= 100f) {
                println("[TrackingLocation] 📏 Distance: ${distance}m - UPLOAD TRIGGERED")
                scope.launch {
                    uploadLocationToSupabase(location)
                }
            } else {
                println("[TrackingLocation] ⏭️ Distance: ${distance}m < 100m - Skipped (waiting for timer or 100m)")
            }
        } else {
            println("[TrackingLocation] 🆕 First location received - will upload on 100m or 10min timer")
        }
        
        // Update last known location for next comparison
        lastKnownLocation = location
    }
    
    /**
     * UPLOAD LOCATION KE SUPABASE
     * 
     * ASYNC: Tidak blok thread caller
     * UPSERT: Insert jika belum ada, update jika sudah ada
     * 
     * THREAD: IO thread (dari coroutine)
     */
    private suspend fun uploadLocationToSupabase(location: Location) {
        try {
            // Get current user ID
            val userId = SupabaseClientManager.client.auth.currentUserOrNull()?.id
            if (userId == null) {
                println("[TrackingLocation] ❌ ERROR: User not logged in")
                return
            }
            
            println("[TrackingLocation] 📤 Uploading location for user: $userId")
            
            // Prepare location data
            val locationData = LocationData(
                userId = userId.toString(),
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                trackingEnabled = true
            )
            
            println("[TrackingLocation] 📊 Data to upsert: userId=$userId, lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}, trackingEnabled=true")
            
            // UPSERT to Supabase (conflict on user_id = update)
            SupabaseClientManager.client.from("location_current")
                .upsert(locationData)
            
            println("[TrackingLocation] ✅ Location uploaded successfully")
            
        } catch (e: Exception) {
            println("[TrackingLocation] ❌ ERROR uploading location: ${e.message}")
            println("[TrackingLocation] ❌ Stack trace: ${e.stackTraceToString()}")
            e.printStackTrace()
            // Tidak throw error (tracking failure tidak boleh crash app)
        }
    }
    
    /**
     * GET LAST KNOWN LOCATION
     * 
     * Berguna untuk:
     * - Initial location saat pertama kali enable tracking
     * - Tampilkan lokasi current di UI
     * 
     * THREAD: IO thread (suspend function)
     */
    suspend fun getLastKnownLocation(): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            println("[TrackingLocation] ❌ No permission to get last location")
            return@withContext null
        }
        
        try {
            val location = fusedLocationClient.lastLocation.await()
            if (location != null) {
                lastKnownLocation = location
                println("[TrackingLocation] 📍 Last known location: ${location.latitude}, ${location.longitude}")
            } else {
                println("[TrackingLocation] ⚠️ No last known location available")
            }
            location
        } catch (e: Exception) {
            println("[TrackingLocation] ❌ ERROR getting last location: ${e.message}")
            null
        }
    }
    
    /**
     * DISABLE TRACKING DI DATABASE
     * 
     * Set tracking_enabled = false di database
     * Guardian tidak akan bisa lihat lokasi lagi
     */
    suspend fun disableTracking() {
        try {
            val userId = SupabaseClientManager.client.auth.currentUserOrNull()?.id
            if (userId == null) {
                println("[TrackingLocation] ❌ ERROR: User not logged in")
                return
            }
            
            // Update tracking_enabled = false
            SupabaseClientManager.client.from("location_current")
                .update(
                    mapOf("tracking_enabled" to false)
                ) {
                    filter {
                        eq("user_id", userId)
                    }
                }
            
            println("[TrackingLocation] ✅ Tracking disabled in database")
            
        } catch (e: Exception) {
            println("[TrackingLocation] ❌ ERROR disabling tracking: ${e.message}")
        }
    }
    
    /**
     * PERIODIC UPLOAD - Force upload every 10 minutes
     * Even if user doesn't move 100m
     */
    private fun startPeriodicUpload() {
        periodicUploadJob = scope.launch {
            while (isTracking) {
                delay(1 * 60 * 1000L) // Wait 10 minutes
                
                if (isTracking && lastKnownLocation != null) {
                    println("[TrackingLocation] ⏰ 10-minute periodic upload triggered")
                    uploadLocationToSupabase(lastKnownLocation!!)
                }
            }
        }
    }
    
    /**
     * CHECK IF HAS LOCATION PERMISSION
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * CLEANUP RESOURCES
     * 
     * WAJIB dipanggil di onDestroy() Activity
     */
    fun cleanup() {
        stopTracking()
        scope.cancel() // Cancel semua coroutines
        println("[TrackingLocation] ✅ Cleanup completed")
    }
    
    /**
     * DEBUG: Manually upload test location
     * Used for testing without waiting for 100m distance
     */
    fun uploadTestLocation(latitude: Double = -6.2088, longitude: Double = 106.8456) {
        scope.launch {
            try {
                val userId = SupabaseClientManager.client.auth.currentUserOrNull()?.id
                if (userId == null) {
                    println("[TrackingLocation] ❌ DEBUG: User not logged in")
                    return@launch
                }
                
                val locationData = LocationData(
                    userId = userId.toString(),
                    latitude = latitude,
                    longitude = longitude,
                    accuracy = 20f,
                    trackingEnabled = true
                )
                
                println("[TrackingLocation] 🧪 DEBUG: Uploading test location for user $userId")
                println("[TrackingLocation] 🧪 DEBUG: Coordinates: $latitude, $longitude")
                
                SupabaseClientManager.client.from("location_current")
                    .upsert(locationData)
                
                println("[TrackingLocation] 🧪 DEBUG: Test location uploaded successfully")
                
            } catch (e: Exception) {
                println("[TrackingLocation] 🧪 DEBUG: Error uploading test location: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * GET TRACKING STATUS
     */
    fun isCurrentlyTracking(): Boolean = isTracking
    
    /**
     * SET LOCATION CHANGE CALLBACK
     * Untuk update UI real-time saat lokasi berubah
     */
    fun setOnLocationChangedListener(callback: (latitude: Double, longitude: Double, accuracy: Float) -> Unit) {
        onLocationChanged = callback
    }
}

/**
 * EXTENSION FUNCTION: await() untuk Task
 * 
 * Convert Google Play Services Task ke coroutine suspend function
 */
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return withContext(Dispatchers.IO) {
        // Tunggu sampai task complete
        while (!isComplete) {
            delay(100)
        }
        
        if (isSuccessful) {
            result!!
        } else {
            throw exception ?: Exception("Task failed")
        }
    }
}
