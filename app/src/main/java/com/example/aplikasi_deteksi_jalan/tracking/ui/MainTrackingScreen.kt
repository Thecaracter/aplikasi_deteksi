package com.example.aplikasi_deteksi_jalan.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingLocationManager
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingPreferences
import com.example.aplikasi_deteksi_jalan.tracking.service.LocationForegroundService

/**
 * MAIN TRACKING SCREEN
 * 
 * Screen utama dengan 2 tabs:
 * 1. My Tracking - User bisa aktifkan tracking untuk diri sendiri
 * 2. Monitor - User bisa monitor lokasi orang lain yang sudah approve
 * 
 * Semua user punya akses ke kedua fitur
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTrackingScreen(
    trackingPreferences: TrackingPreferences,
    trackingLocationManager: TrackingLocationManager,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when (selectedTab) {
                            0 -> "Tracking Saya"
                            else -> "Monitor Lokasi"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    // Logout button
                    IconButton(
                        onClick = {
                            // Stop tracking and service
                            trackingLocationManager.stopTracking()
                            LocationForegroundService.stopService(context)
                            
                            // Clear preferences
                            trackingPreferences.clear()
                            
                            // Navigate to login
                            onLogout()
                        }
                    ) {
                        Icon(Icons.Default.ExitToApp, "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.LocationOn, "My Tracking") },
                    label = { Text("Tracking Saya") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, "Monitor") },
                    label = { Text("Monitor") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> {
                    // My Tracking Tab - sebagai USER
                    UserTrackingScreen(
                        trackingPreferences = trackingPreferences,
                        trackingLocationManager = trackingLocationManager,
                        onNavigateBack = {}, // Already handled by topbar
                        onLogout = {}, // Already handled by topbar
                        hideTopBar = true // Hide internal topbar karena sudah ada di main
                    )
                }
                1 -> {
                    // Monitor Tab - sebagai GUARDIAN
                    GuardianMonitorScreen(
                        onNavigateBack = {}, // Already handled by topbar
                        onLogout = {}, // Already handled by topbar
                        hideTopBar = true // Hide internal topbar
                    )
                }
            }
        }
    }
}
