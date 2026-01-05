package com.example.aplikasi_deteksi_jalan

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aplikasi_deteksi_jalan.tracking.TrackingNavigationScreen
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingLocationManager
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingPreferences
import com.example.aplikasi_deteksi_jalan.tracking.service.LocationForegroundService
import com.example.aplikasi_deteksi_jalan.ui.theme.Aplikasi_deteksi_jalanTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var trackingManager: TrackingLocationManager
    private lateinit var trackingPreferences: TrackingPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize tracking module (non-blocking, tidak ganggu AI)
        initTrackingModule()
        
        setContent {
            Aplikasi_deteksi_jalanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
    
    private fun initTrackingModule() {
        trackingPreferences = TrackingPreferences(this)
        trackingManager = TrackingLocationManager(this)
        
        // Auto-start tracking jika user sudah login dan tracking enabled
        if (trackingPreferences.isLoggedIn() && trackingPreferences.isTrackingEnabled) {
            println("[MainActivity] ✅ Auto-restoring tracking on app launch")
            
            // Start foreground service
            LocationForegroundService.startService(this)
            
            // Start tracking manager
            lifecycleScope.launch {
                trackingManager.startTracking()
                println("[MainActivity] 📍 Tracking restored from preferences")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop tracking saat app ditutup
        if (::trackingManager.isInitialized) {
            trackingManager.stopTracking()
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var showSplash by remember { mutableStateOf(true) }
    
    if (showSplash) {
        SplashScreen(
            onTimeout = { showSplash = false }
        )
    } else {
        NavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") {
                HomeScreen(
                    onNavigateToImageDetection = {
                        navController.navigate("image_detection")
                    },
                    onNavigateToRealtimeDetection = {
                        navController.navigate("realtime_detection")
                    },
                    onNavigateToTracking = {
                        navController.navigate("tracking")
                    }
                )
            }
            
            composable("image_detection") {
                ImageDetectionScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            
            composable("realtime_detection") {
                RealtimeDetectionScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            
            composable("tracking") {
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                 permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    // Permission handling sudah diatur di tracking screens
                }
                
                LaunchedEffect(Unit) {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
                
                TrackingNavigationScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
