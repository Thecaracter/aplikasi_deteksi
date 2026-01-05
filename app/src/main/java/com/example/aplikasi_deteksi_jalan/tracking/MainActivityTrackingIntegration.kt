package com.example.aplikasi_deteksi_jalan.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.aplikasi_deteksi_jalan.tracking.data.repository.AuthRepository
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingLocationManager
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * CONTOH INTEGRASI LENGKAP
 * 
 * MainActivity dengan routing berdasarkan role:
 * - role = "user" → UserTrackingScreen
 * - role = "guardian" → GuardianMonitorScreen
 * 
 * COPY kode ini ke MainActivity.kt Anda yang sudah ada
 * (yang sudah punya AI detection)
 * 
 * INTEGRASI:
 * 1. Copy variable declarations tracking module
 * 2. Copy initTrackingModule() function
 * 3. Copy openTrackingScreen() function
 * 4. Tambahkan button di UI untuk buka tracking screen
 * 5. Cleanup di onDestroy()
 */

class MainActivityTrackingIntegration : ComponentActivity() {
    
    // ============================================
    // AI DETECTION MODULE (EXISTING - JANGAN UBAH!)
    // ============================================
    // ... existing AI variables ...
    
    // ============================================
    // TRACKING MODULE (BARU - TERPISAH)
    // ============================================
    private lateinit var trackingLocationManager: TrackingLocationManager
    private lateinit var trackingPreferences: TrackingPreferences
    private lateinit var authRepository: AuthRepository
    private val trackingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false ||
                     permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (granted) {
            if (trackingPreferences.isTrackingEnabled) {
                trackingLocationManager.startTracking()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ============================================
        // INIT AI MODULE (EXISTING - JANGAN UBAH!)
        // ============================================
        // ... existing AI initialization ...
        
        // ============================================
        // INIT TRACKING MODULE
        // ============================================
        initTrackingModule()
        
        // ============================================
        // UI
        // ============================================
        setContent {
            MaterialTheme {
                TrackingIntegrationScreen(
                    context = this,
                    trackingPreferences = trackingPreferences,
                    trackingLocationManager = trackingLocationManager,
                    authRepository = authRepository,
                    onRequestPermission = {
                        requestLocationPermissionIfNeeded()
                    }
                )
            }
        }
    }
    
    /**
     * INIT TRACKING MODULE
     */
    private fun initTrackingModule() {
        try {
            trackingLocationManager = TrackingLocationManager(this)
            trackingPreferences = TrackingPreferences(this)
            authRepository = AuthRepository()
            
            // Restore tracking state
            if (trackingPreferences.isTrackingEnabled) {
                if (hasLocationPermission()) {
                    trackingLocationManager.startTracking()
                }
            }
            
            println("[MainActivity] ✅ Tracking module initialized")
        } catch (e: Exception) {
            println("[MainActivity] ❌ Error initializing tracking: ${e.message}")
        }
    }
    
    /**
     * CHECK & REQUEST LOCATION PERMISSION
     */
    private fun requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    
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
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup AI (existing)
        // ... existing cleanup ...
        
        // Cleanup tracking
        try {
            trackingLocationManager.cleanup()
        } catch (e: Exception) {
            println("[MainActivity] Error cleaning up tracking: ${e.message}")
        }
    }
}

/**
 * TRACKING INTEGRATION SCREEN (DEMO)
 * 
 * Screen untuk demo integrasi
 * Di aplikasi Anda, ganti dengan flow yang sesuai
 */
@Composable
fun TrackingIntegrationScreen(
    context: Context,
    trackingPreferences: TrackingPreferences,
    trackingLocationManager: TrackingLocationManager,
    authRepository: AuthRepository,
    onRequestPermission: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showTrackingScreen by remember { mutableStateOf(false) }
    var userRole by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Load user role
    LaunchedEffect(Unit) {
        scope.launch {
            if (authRepository.isLoggedIn()) {
                val profileResult = authRepository.getCurrentProfile()
                if (profileResult.isSuccess) {
                    val profile = profileResult.getOrNull()
                    userRole = profile?.role
                    trackingPreferences.userRole = profile?.role
                }
            }
        }
    }
    
    if (showTrackingScreen && userRole != null) {
        // Show tracking screen based on role
        TrackingScreenByRole(
            role = userRole!!,
            trackingPreferences = trackingPreferences,
            trackingLocationManager = trackingLocationManager,
            onNavigateBack = { showTrackingScreen = false },
            onSpeakText = { /* TODO: TTS */ }
        )
    } else {
        // Main screen with button to open tracking
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                androidx.compose.material3.TopAppBar(
                    title = { Text("Aplikasi Deteksi Jalan") }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                
                // Info card
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Demo Integrasi Tracking",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Role Anda: ${userRole ?: "Belum login"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // Button to open tracking screen
                Button(
                    onClick = {
                        if (userRole == null) {
                            // User belum login
                            // TODO: Show login screen
                        } else {
                            // Request permission dulu
                            onRequestPermission()
                            showTrackingScreen = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (userRole == "guardian") {
                            "Buka Monitor Lokasi"
                        } else {
                            "Buka Pengaturan Pelacakan"
                        }
                    )
                }
                
                // Info
                Text(
                    "Tekan tombol di atas untuk membuka halaman tracking sesuai role Anda",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
