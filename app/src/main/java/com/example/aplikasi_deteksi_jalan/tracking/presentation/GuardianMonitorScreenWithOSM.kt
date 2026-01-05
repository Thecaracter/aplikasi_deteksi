package com.example.aplikasi_deteksi_jalan.tracking.presentation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.aplikasi_deteksi_jalan.tracking.data.models.UserLocation
import com.example.aplikasi_deteksi_jalan.tracking.data.repository.GuardianRepository
import com.example.aplikasi_deteksi_jalan.tracking.data.repository.LocationRepository
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

/**
 * GUARDIAN MONITOR SCREEN WITH OPENSTREETMAP
 * 
 * Guardian bisa:
 * 1. Lihat lokasi semua user yang sudah approve di MAP (OpenStreetMap)
 * 2. Toggle antara Map view dan List view
 * 3. Klik marker untuk detail
 * 4. Real-time location updates via Supabase Realtime
 * 
 * OPENSTREETMAP:
 * - Library: OSMDroid 6.1.18
 * - No API key needed (free & open source)
 * - Tile source: OpenStreetMap
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianMonitorScreenWithOSM(
    onNavigateBack: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToMapDetail: (userId: String, userEmail: String) -> Unit = { _, _ -> },
    hideTopBar: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val trackingPreferences = remember { TrackingPreferences(context) }
    val guardianRepository = remember { GuardianRepository() }
    val locationRepository = remember { LocationRepository() }
    
    var userLocations by remember { mutableStateOf<List<UserLocation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var guardianInvitationCode by remember { mutableStateOf<String?>(null) }
    var pendingRequests by remember { mutableStateOf<List<com.example.aplikasi_deteksi_jalan.tracking.data.models.Guardian>>(emptyList()) }
    var pendingProfiles by remember { mutableStateOf<Map<String, com.example.aplikasi_deteksi_jalan.tracking.data.models.Profile>>(emptyMap()) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Pending, 1 = Aktif
    
    val guardianId = trackingPreferences.userId
    
    // Initialize OSMDroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        
        // Fetch guardian profile with invitation code
        if (guardianId != null) {
            coroutineScope.launch {
                try {
                    val profile = guardianRepository.getProfileById(guardianId)
                    guardianInvitationCode = profile?.invitationCode
                    println("[GuardianOSM] 🔑 Guardian code: ${profile?.invitationCode}")
                } catch (e: Exception) {
                    println("[GuardianOSM] ❌ Error fetching profile: ${e.message}")
                }
            }
        }
    }
    
    // Fetch pending requests
    LaunchedEffect(guardianId) {
        if (guardianId != null) {
            coroutineScope.launch {
                try {
                    val result = guardianRepository.getPendingRequestsForGuardian(guardianId)
                    if (result.isSuccess) {
                        val requests = result.getOrNull() ?: emptyList()
                        pendingRequests = requests
                        println("[GuardianOSM] 📋 Pending requests: ${requests.size}")
                        
                        // Fetch profiles untuk setiap pending request
                        val profiles = mutableMapOf<String, com.example.aplikasi_deteksi_jalan.tracking.data.models.Profile>()
                        requests.forEach { request ->
                            val profile = guardianRepository.getProfileById(request.userId)
                            if (profile != null) {
                                profiles[request.userId] = profile
                            }
                        }
                        pendingProfiles = profiles
                        isLoading = false
                    }
                } catch (e: Exception) {
                    println("[GuardianOSM] ❌ Error fetching pending requests: ${e.message}")
                    isLoading = false
                }
            }
        }
    }
    
    // Reload data when tab changes (to refresh approved users)
    LaunchedEffect(activeTab) {
        if (activeTab == 1 && guardianId != null) {
            // Reload approved users when switching to "Aktif" tab
            coroutineScope.launch {
                try {
                    val approvedUsers = guardianRepository.getApprovedUsers(guardianId)
                    if (approvedUsers.isNotEmpty()) {
                        val userIds = approvedUsers.map { it.userId }
                        locationRepository.getUsersLocations(userIds).collect { locations ->
                            userLocations = locations
                        }
                    }
                } catch (e: Exception) {
                    println("[GuardianOSM] ❌ Error reloading approved users: ${e.message}")
                }
            }
        }
    }
    
    // Fetch approved users
    LaunchedEffect(guardianId) {
        if (guardianId != null) {
            coroutineScope.launch {
                try {
                    val approvedUsers = guardianRepository.getApprovedUsers(guardianId)
                    println("[GuardianOSM] 👥 Approved users: ${approvedUsers.size}")
                    
                    if (approvedUsers.isNotEmpty()) {
                        // Get locations for approved users
                        val userIds = approvedUsers.map { it.userId }
                        locationRepository.getUsersLocations(userIds)
                            .catch { e ->
                                println("[GuardianOSM] ❌ Location flow error: ${e.message}")
                                errorMessage = "Gagal memuat lokasi: ${e.message}"
                            }
                            .collect { locations ->
                                println("[GuardianOSM] 📍 Received ${locations.size} locations")
                                userLocations = locations
                                isLoading = false
                            }
                    } else {
                        // No approved users - normal state, show pending requests
                        isLoading = false
                    }
                } catch (e: Exception) {
                    println("[GuardianOSM] ❌ Error: ${e.message}")
                    errorMessage = e.message
                    isLoading = false
                }
            }
        }
    }
    
    // POLLING LOCATIONS EVERY 5 SECONDS
    // Get latest locations tanpa perlu WebSocket
    LaunchedEffect(guardianId, activeTab) {
        if (guardianId != null && activeTab == 1) { // Only poll on "Aktif" tab
            coroutineScope.launch {
                try {
                    val approvedUsers = guardianRepository.getApprovedUsers(guardianId)
                    val userIds = approvedUsers.map { it.userId }
                    
                    while (true) {
                        if (userIds.isNotEmpty()) {
                            try {
                                // Fetch locations directly (not via Flow)
                                val locations = locationRepository.getUsersLocationsOnce(userIds)
                                if (locations.isNotEmpty()) {
                                    userLocations = locations
                                    println("[GuardianOSM] ⏱️ Polling: Updated ${locations.size} locations")
                                }
                            } catch (e: Exception) {
                                println("[GuardianOSM] ❌ Polling error: ${e.message}")
                            }
                        }
                        delay(5000L) // Poll every 5 seconds
                    }
                } catch (e: Exception) {
                    println("[GuardianOSM] ❌ Polling setup error: ${e.message}")
                }
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
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                "Guardian Dashboard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    },
                    actions = {
                        Button(
                            onClick = onLogout,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Logout", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Guardian Code Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable {
                        guardianInvitationCode?.let { code ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Guardian Code", code)
                            clipboard.setPrimaryClip(clip)
                            println("[GuardianOSM] ✅ Code copied: $code")
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Kode Guardian Anda",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            guardianInvitationCode ?: "Loading...",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // TAB BUTTONS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { activeTab = 0 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        "Tertunda (${pendingRequests.size})",
                        color = if (activeTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Button(
                    onClick = { activeTab = 1 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        "Aktif (${userLocations.size})",
                        color = if (activeTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // CONTENT
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    activeTab == 0 -> {
                        // TAB PENDING REQUESTS
                        if (pendingRequests.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Tidak ada permintaan tertunda", style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(pendingRequests) { request ->
                                    val userProfile = pendingProfiles[request.userId]
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                userProfile?.email ?: "User (${request.userId.take(8)}...)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Meminta Anda sebagai guardian",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            val result = guardianRepository.acceptGuardian(request.id ?: "")
                                                            if (result.isSuccess) {
                                                                val newRequests = guardianRepository.getPendingRequestsForGuardian(guardianId!!)
                                                                if (newRequests.isSuccess) {
                                                                    pendingRequests = newRequests.getOrNull() ?: emptyList()
                                                                }
                                                                val approvedUsers = guardianRepository.getApprovedUsers(guardianId)
                                                                if (approvedUsers.isNotEmpty()) {
                                                                    val userIds = approvedUsers.map { it.userId }
                                                                    locationRepository.getUsersLocations(userIds).collect { locations ->
                                                                        userLocations = locations
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary
                                                    )
                                                ) {
                                                    Text("Terima")
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            guardianRepository.rejectGuardian(request.id ?: "")
                                                            val newRequests = guardianRepository.getPendingRequestsForGuardian(guardianId!!)
                                                            if (newRequests.isSuccess) {
                                                                pendingRequests = newRequests.getOrNull() ?: emptyList()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Tolak")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    activeTab == 1 -> {
                        // TAB AKTIF - LIST USER ONLY
                        if (userLocations.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .padding(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "📍",
                                            fontSize = 64.sp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "Belum Ada User",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Bagikan kode guardian Anda di atas agar user dapat menambahkan Anda sebagai guardian",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(userLocations) { userLocation ->
                                    LocationListItem(
                                        userLocation = userLocation,
                                        onItemClick = { userId, userEmail ->
                                            println("[GuardianOSM] 🗺️ Opening map for user $userId")
                                            onNavigateToMapDetail(userId, userEmail)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
