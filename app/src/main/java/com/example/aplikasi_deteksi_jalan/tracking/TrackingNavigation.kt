package com.example.aplikasi_deteksi_jalan.tracking

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aplikasi_deteksi_jalan.tracking.data.repository.AuthRepository
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingLocationManager
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingPreferences
import com.example.aplikasi_deteksi_jalan.tracking.presentation.GuardianMapDetailScreen
import com.example.aplikasi_deteksi_jalan.tracking.ui.GuardianMonitorScreen
import com.example.aplikasi_deteksi_jalan.tracking.ui.UserTrackingScreen
import kotlinx.coroutines.launch

/**
 * TRACKING NAVIGATION
 * 
 * Handle routing berdasarkan role user:
 * - role = "user" → UserTrackingScreen
 * - role = "guardian" → GuardianMonitorScreen
 * 
 * FLOW:
 * 1. Check user logged in?
 * 2. Get user role dari database
 * 3. Navigate ke screen sesuai role
 */

sealed class TrackingRoute(val route: String) {
    object Login : TrackingRoute("login")
    object UserTracking : TrackingRoute("user_tracking")
    object GuardianMonitor : TrackingRoute("guardian_monitor")
    object MapDetail : TrackingRoute("map_detail/{userId}/{userName}") {
        fun createRoute(userId: String, userName: String) = "map_detail/$userId/$userName"
    }
}

@Composable
fun TrackingNavigation(
    trackingPreferences: TrackingPreferences,
    trackingLocationManager: TrackingLocationManager,
    onNavigateBack: () -> Unit,
    onSpeakText: (String) -> Unit = {}
) {
    val navController = rememberNavController()
    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    
    // Determine initial route based on role
    var startDestination by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        scope.launch {
            val isLoggedIn = authRepository.isLoggedIn()
            if (!isLoggedIn) {
                // Not logged in, navigate back or show login
                startDestination = TrackingRoute.Login.route
                return@launch
            }
            
            // Get user profile to determine role
            val profileResult = authRepository.getCurrentProfile()
            if (profileResult.isSuccess) {
                val profile = profileResult.getOrNull()
                val role = profile?.role ?: "user"
                
                // Save to preferences for quick access
                trackingPreferences.userRole = role
                trackingPreferences.userId = profile?.id
                trackingPreferences.userEmail = profile?.email
                
                // Navigate based on role
                startDestination = when (role) {
                    "guardian" -> TrackingRoute.GuardianMonitor.route
                    else -> TrackingRoute.UserTracking.route
                }
            } else {
                startDestination = TrackingRoute.Login.route
            }
        }
    }
    
    // Wait until role is determined
    if (startDestination == null) {
        // Loading screen
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination!!
    ) {
        composable(TrackingRoute.UserTracking.route) {
            UserTrackingScreen(
                trackingPreferences = trackingPreferences,
                trackingLocationManager = trackingLocationManager,
                onNavigateBack = onNavigateBack,
                onSpeakText = onSpeakText
            )
        }
        
        composable(TrackingRoute.GuardianMonitor.route) {
            GuardianMonitorScreen(
                onNavigateBack = onNavigateBack,
                onSpeakText = onSpeakText
            )
        }
        
        composable(
            route = TrackingRoute.MapDetail.route,
            arguments = listOf(
                androidx.navigation.navArgument("userId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("userName") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val userName = backStackEntry.arguments?.getString("userName") ?: "User"
            GuardianMapDetailScreen(
                userId = userId,
                userName = userName,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(TrackingRoute.Login.route) {
            // TODO: Implement login screen or navigate back
            // For now, just navigate back
            LaunchedEffect(Unit) {
                onNavigateBack()
            }
        }
    }
}

/**
 * HELPER: Get screen based on role (tanpa navigation)
 */
@Composable
fun TrackingScreenByRole(
    role: String,
    trackingPreferences: TrackingPreferences,
    trackingLocationManager: TrackingLocationManager,
    onNavigateBack: () -> Unit,
    onSpeakText: (String) -> Unit = {}
) {
    when (role.lowercase()) {
        "guardian" -> {
            GuardianMonitorScreen(
                onNavigateBack = onNavigateBack,
                onSpeakText = onSpeakText
            )
        }
        else -> { // "user" or default
            UserTrackingScreen(
                trackingPreferences = trackingPreferences,
                trackingLocationManager = trackingLocationManager,
                onNavigateBack = onNavigateBack,
                onSpeakText = onSpeakText
            )
        }
    }
}
