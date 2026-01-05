package com.example.aplikasi_deteksi_jalan.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.aplikasi_deteksi_jalan.tracking.data.models.LocationData
import com.example.aplikasi_deteksi_jalan.tracking.data.models.Profile
import com.example.aplikasi_deteksi_jalan.tracking.data.repository.LocationRepository
import com.example.aplikasi_deteksi_jalan.tracking.data.repository.GuardianRepository
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingPreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * GUARDIAN MONITOR SCREEN
 * 
 * Halaman untuk GUARDIAN (Orang Tua/Keluarga) dengan fitur:
 * 1. Lihat lokasi real-time semua user yang memberi izin
 * 2. Subscribe realtime updates (on-demand)
 * 3. Lihat last update time
 * 4. Koordinat lat, lng
 * 
 * REAL-TIME:
 * - Subscribe saat screen aktif
 * - Unsubscribe saat screen ditutup (hemat battery)
 * - Update otomatis via Supabase Realtime
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianMonitorScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit = {},
    onSpeakText: (String) -> Unit = {},
    hideTopBar: Boolean = false // Untuk hide topbar kalau dipanggil dari MainTrackingScreen
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationRepository = remember { LocationRepository() }
    val guardianRepository = remember { GuardianRepository() }
    val trackingPreferences = remember { TrackingPreferences(context) }
    
    // State
    var userLocations by remember { mutableStateOf<List<LocationData>>(emptyList()) }
    var userProfiles by remember { mutableStateOf<Map<String, Profile>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRealtimeActive by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastRefreshTime by remember { mutableStateOf<Long>(0L) }
    
    // Load initial data
    LaunchedEffect(Unit) {
        loadLocations(locationRepository, guardianRepository) { locations, profiles ->
            userLocations = locations
            userProfiles = profiles
            lastRefreshTime = System.currentTimeMillis()
        }
    }
    
    // Subscribe to realtime updates
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val flowResult = locationRepository.subscribeLocationUpdates()
                if (flowResult.isSuccess) {
                    isRealtimeActive = true
                    flowResult.getOrNull()?.collectLatest { newLocation ->
                        // Update specific location
                        userLocations = userLocations.map { loc ->
                            if (loc.userId == newLocation.userId) newLocation else loc
                        }.let { list ->
                            // Add if not exists
                            if (list.none { it.userId == newLocation.userId }) {
                                list + newLocation
                            } else {
                                list
                            }
                        }
                        lastRefreshTime = System.currentTimeMillis()
                        onSpeakText("Lokasi diperbarui")
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Realtime error: ${e.message}"
                isRealtimeActive = false
            }
        }
    }
    
    // Cleanup: unsubscribe on dispose
    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
                locationRepository.unsubscribeLocationUpdates()
            }
        }
    }
    
    Scaffold(
        topBar = {
            if (!hideTopBar) {
                TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Monitor Lokasi",
                            modifier = Modifier.semantics {
                                contentDescription = "Halaman monitor lokasi pengguna yang Anda jaga"
                            }
                        )
                        if (isRealtimeActive) {
                            Text(
                                "● Real-time aktif",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Kembali ke halaman utama"
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    // Refresh button
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                loadLocations(locationRepository, guardianRepository) { locations, profiles ->
                                    userLocations = locations
                                    userProfiles = profiles
                                    lastRefreshTime = System.currentTimeMillis()
                                }
                                isLoading = false
                                onSpeakText("Data disegarkan")
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Segarkan data lokasi"
                        }
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    
                    // Logout button
                    IconButton(
                        onClick = {
                            // Clear preferences
                            trackingPreferences.clear()
                            
                            // Navigate to login
                            onLogout()
                            onSpeakText("Anda telah keluar dari aplikasi")
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Keluar dari akun"
                        }
                    ) {
                        Icon(Icons.Default.ExitToApp, "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // ============================================
            // 1. STATUS CARD
            // ============================================
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Memantau ${userLocations.size} Pengguna",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (lastRefreshTime > 0) {
                                    "Update: ${formatTime(lastRefreshTime)}"
                                } else {
                                    "Memuat data..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            // ============================================
            // 2. INFO CARD
            // ============================================
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Column {
                            Text(
                                text = "Tentang Monitor",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Lokasi diperbarui otomatis real-time\n" +
                                       "• Anda hanya melihat pengguna yang memberi izin\n" +
                                       "• Lokasi adalah posisi terakhir pengguna\n" +
                                       "• Koordinat dapat dibuka di aplikasi peta",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            
            // ============================================
            // 3. USERS LIST HEADER
            // ============================================
            if (userLocations.isNotEmpty()) {
                item {
                    Text(
                        text = "Daftar Pengguna (${userLocations.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics {
                            contentDescription = "Daftar ${userLocations.size} pengguna yang Anda pantau"
                        }
                    )
                }
            }
            
            // ============================================
            // 4. USER LOCATION CARDS
            // ============================================
            if (userLocations.isEmpty() && !isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Tidak ada pengguna untuk dipantau",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Pengguna perlu menambahkan Anda sebagai guardian dan mengaktifkan pelacakan lokasi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                items(userLocations) { location ->
                    UserLocationCard(
                        location = location,
                        userProfile = userProfiles[location.userId],
                        onOpenMap = { lat, lng ->
                            // TODO: Open Google Maps or other map app
                            // val uri = "geo:$lat,$lng?q=$lat,$lng"
                            // context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                            onSpeakText("Membuka peta untuk lokasi $lat, $lng")
                        }
                    )
                }
            }
            
            // ============================================
            // 5. ERROR MESSAGE
            // ============================================
            errorMessage?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Bottom spacing
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        
        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * USER LOCATION CARD
 */
@Composable
private fun UserLocationCard(
    location: LocationData,
    userProfile: Profile?,
    onOpenMap: (Double, Double) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // User info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userProfile?.email ?: "Pengguna",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics {
                            contentDescription = "Pengguna: ${userProfile?.email ?: "memuat"}"
                        }
                    )
                    if (userProfile?.fullName != null) {
                        Text(
                            text = userProfile.fullName!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Status indicator
                if (location.trackingEnabled) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary,
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                            Text(
                                "Aktif",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Divider()
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Location info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Latitude
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Latitude",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "%.6f".format(location.latitude),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics {
                            contentDescription = "Latitude: ${location.latitude}"
                        }
                    )
                }
                
                // Longitude
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Longitude",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "%.6f".format(location.longitude),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics {
                            contentDescription = "Longitude: ${location.longitude}"
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Accuracy & Update time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                location.accuracy?.let { acc ->
                    Text(
                        "Akurasi: ±${acc.toInt()}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                location.updatedAt?.let { time ->
                    Text(
                        formatTimeShort(time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action button
            Button(
                onClick = { onOpenMap(location.latitude, location.longitude) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Buka di peta"
                    }
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buka di Peta")
            }
        }
    }
}

/**
 * HELPER: Load locations and profiles
 */
private suspend fun loadLocations(
    locationRepository: LocationRepository,
    guardianRepository: GuardianRepository,
    onLoaded: (locations: List<LocationData>, profiles: Map<String, Profile>) -> Unit
) {
    val locationsResult = locationRepository.getMonitoredUsersLocations()
    val locations = locationsResult.getOrNull() ?: emptyList()
    
    // Load profiles for each user
    val profiles = mutableMapOf<String, Profile>()
    locations.forEach { location ->
        val profileResult = guardianRepository.getGuardianProfile(location.userId)
        profileResult.getOrNull()?.let { profile ->
            profiles[location.userId] = profile
        }
    }
    
    onLoaded(locations, profiles)
}

/**
 * HELPER: Format timestamp to readable time
 */
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * HELPER: Format ISO timestamp to readable
 */
private fun formatTimeShort(isoTime: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(isoTime.substring(0, 19))
        val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        "N/A"
    }
}
