package com.example.aplikasi_deteksi_jalan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingLocationManager
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * CONTOH INTEGRASI TRACKING MODULE DI MAINACTIVITY
 * 
 * PRINSIP:
 * 1. Tracking module TERPISAH dari AI detection
 * 2. Init tracking TIDAK mengganggu init AI
 * 3. Cleanup tracking TIDAK mengganggu cleanup AI
 * 4. Thread tracking TIDAK blok thread AI
 * 
 * COPY CODE INI KE MainActivity Anda yang sudah ada
 */
class MainActivityExample : ComponentActivity() {
    
    // ============================================
    // AI DETECTION MODULE (EXISTING - JANGAN UBAH!)
    // ============================================
    // private lateinit var objectDetectorHelper: ObjectDetectorHelper
    // private lateinit var cameraExecutor: ExecutorService
    // ... existing AI variables ...
    
    // ============================================
    // TRACKING MODULE (BARU - TERPISAH)
    // ============================================
    private lateinit var trackingLocationManager: TrackingLocationManager
    private lateinit var trackingPreferences: TrackingPreferences
    private val trackingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Permission launcher untuk location
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            println("[MainActivity] ✅ Location permission granted")
            // Auto-start tracking jika sebelumnya enabled
            if (trackingPreferences.isTrackingEnabled) {
                trackingLocationManager.startTracking()
            }
        } else {
            println("[MainActivity] ❌ Location permission denied")
            // Disable tracking di preferences
            trackingPreferences.isTrackingEnabled = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ============================================
        // INIT AI MODULE (EXISTING - JANGAN UBAH!)
        // ============================================
        // objectDetectorHelper = ObjectDetectorHelper(this)
        // cameraExecutor = Executors.newSingleThreadExecutor()
        // ... existing AI initialization ...
        
        // ============================================
        // INIT TRACKING MODULE (BARU)
        // ============================================
        initTrackingModule()
        
        // ============================================
        // UI (EXISTING)
        // ============================================
        setContent {
            // Your existing Compose UI
            // Tambahkan button/toggle untuk enable/disable tracking
        }
    }
    
    /**
     * INIT TRACKING MODULE
     * 
     * Dipanggil SETELAH AI module init
     * Berjalan ASYNC (tidak blok AI init)
     */
    private fun initTrackingModule() {
        try {
            // 1. Init managers
            trackingLocationManager = TrackingLocationManager(this)
            trackingPreferences = TrackingPreferences(this)
            
            // 2. Request location permission jika belum ada
            if (!hasLocationPermission()) {
                println("[MainActivity] ⚠️ Location permission not granted, requesting...")
                // Tidak request otomatis, biar user yang klik tombol "Enable Tracking"
            } else {
                // 3. Restore tracking state jika sebelumnya enabled
                if (trackingPreferences.isTrackingEnabled) {
                    println("[MainActivity] ♻️ Restoring tracking state...")
                    trackingLocationManager.startTracking()
                }
            }
            
            println("[MainActivity] ✅ Tracking module initialized")
            
        } catch (e: Exception) {
            println("[MainActivity] ❌ Error initializing tracking module: ${e.message}")
            // Tracking error tidak boleh crash app
            e.printStackTrace()
        }
    }
    
    // ============================================
    // TRACKING CONTROL FUNCTIONS
    // ============================================
    
    /**
     * ENABLE TRACKING
     * 
     * Dipanggil dari UI (button/toggle)
     * 
     * Flow:
     * 1. Check permission → request jika belum ada
     * 2. Start location manager
     * 3. Save state ke preferences
     * 4. Feedback audio (opsional)
     */
    fun enableTracking() {
        trackingScope.launch {
            try {
                // 1. Check permission
                if (!hasLocationPermission()) {
                    println("[MainActivity] Requesting location permission...")
                    requestLocationPermission()
                    return@launch
                }
                
                // 2. Start location manager
                trackingLocationManager.startTracking()
                
                // 3. Save state
                trackingPreferences.isTrackingEnabled = true
                
                // 4. Feedback (opsional - jangan ganggu AI audio)
                speakText("Pelacakan lokasi diaktifkan")
                
                println("[MainActivity] ✅ Tracking enabled")
                
            } catch (e: Exception) {
                println("[MainActivity] ❌ Error enabling tracking: ${e.message}")
            }
        }
    }
    
    /**
     * DISABLE TRACKING
     * 
     * Dipanggil dari UI (button/toggle)
     * 
     * Flow:
     * 1. Stop location manager
     * 2. Disable di database (tracking_enabled = false)
     * 3. Save state ke preferences
     * 4. Feedback audio (opsional)
     */
    fun disableTracking() {
        trackingScope.launch {
            try {
                // 1. Stop location manager
                trackingLocationManager.stopTracking()
                
                // 2. Disable di database
                trackingLocationManager.disableTracking()
                
                // 3. Save state
                trackingPreferences.isTrackingEnabled = false
                
                // 4. Feedback
                speakText("Pelacakan lokasi dinonaktifkan")
                
                println("[MainActivity] ✅ Tracking disabled")
                
            } catch (e: Exception) {
                println("[MainActivity] ❌ Error disabling tracking: ${e.message}")
            }
        }
    }
    
    /**
     * GET TRACKING STATUS
     */
    fun isTrackingEnabled(): Boolean {
        return trackingPreferences.isTrackingEnabled
    }
    
    // ============================================
    // PERMISSION HELPERS
    // ============================================
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    // ============================================
    // AUDIO FEEDBACK (OPSIONAL)
    // ============================================
    
    private fun speakText(text: String) {
        // TODO: Implementasi TTS
        // Pastikan tidak ganggu voice guidance AI
        // Gunakan queue atau delay jika perlu
    }
    
    // ============================================
    // LIFECYCLE CALLBACKS
    // ============================================
    
    override fun onDestroy() {
        super.onDestroy()
        
        // ============================================
        // CLEANUP AI MODULE (EXISTING - JANGAN UBAH!)
        // ============================================
        // cameraExecutor.shutdown()
        // objectDetectorHelper.cleanup()
        // ... existing AI cleanup ...
        
        // ============================================
        // CLEANUP TRACKING MODULE (BARU)
        // ============================================
        try {
            trackingLocationManager.cleanup()
            println("[MainActivity] ✅ Tracking module cleanup completed")
        } catch (e: Exception) {
            println("[MainActivity] ❌ Error cleaning up tracking: ${e.message}")
        }
    }
    
    override fun onPause() {
        super.onPause()
        // AI module onPause (existing)
        
        // Tracking tetap jalan di background (jika enabled)
        // Tidak perlu stop tracking
    }
    
    override fun onResume() {
        super.onResume()
        // AI module onResume (existing)
        
        // Tracking auto-resume (sudah handle di LocationManager)
    }
}

/**
 * ============================================
 * CONTOH INTEGRASI DI COMPOSE UI
 * ============================================
 */

/*
@Composable
fun TrackingToggleButton(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Pelacakan Lokasi",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { 
                contentDescription = "Pelacakan Lokasi untuk keamanan"
            }
        )
        
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics {
                contentDescription = if (isEnabled) {
                    "Pelacakan aktif, tekan untuk matikan"
                } else {
                    "Pelacakan nonaktif, tekan untuk aktifkan"
                }
            }
        )
    }
}

// Gunakan di Screen Anda:
@Composable
fun SettingsScreen(mainActivity: MainActivity) {
    var isTrackingEnabled by remember { 
        mutableStateOf(mainActivity.isTrackingEnabled()) 
    }
    
    Column {
        // ... existing settings ...
        
        TrackingToggleButton(
            isEnabled = isTrackingEnabled,
            onToggle = { enabled ->
                isTrackingEnabled = enabled
                if (enabled) {
                    mainActivity.enableTracking()
                } else {
                    mainActivity.disableTracking()
                }
            }
        )
    }
}
*/
