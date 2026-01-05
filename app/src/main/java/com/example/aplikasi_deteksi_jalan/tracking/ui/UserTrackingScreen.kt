package com.example.aplikasi_deteksi_jalan.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aplikasi_deteksi_jalan.tracking.data.models.Guardian
import com.example.aplikasi_deteksi_jalan.tracking.data.models.Profile
import com.example.aplikasi_deteksi_jalan.tracking.data.repository.GuardianRepository
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingLocationManager
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingPreferences
import com.example.aplikasi_deteksi_jalan.tracking.service.LocationForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * USER TRACKING SCREEN
 * 
 * Halaman untuk USER (Tunanetra) dengan fitur:
 * 1. Toggle tracking ON/OFF
 * 2. Tambah guardian
 * 3. Lihat & kelola guardian requests
 * 4. Lihat lokasi current
 * 
 * AKSESIBILITAS:
 * - Semua element accessible dengan TalkBack
 * - Voice feedback untuk setiap action
 * - Large touch targets
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserTrackingScreen(
    trackingPreferences: TrackingPreferences,
    trackingLocationManager: TrackingLocationManager,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit = {},
    onSpeakText: (String) -> Unit = {}, // Callback untuk voice feedback
    hideTopBar: Boolean = false // Untuk hide topbar kalau dipanggil dari MainTrackingScreen
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val guardianRepository = remember { GuardianRepository() }
    
    // State
    var isTrackingEnabled by remember { mutableStateOf(trackingPreferences.isTrackingEnabled) }
    var pendingGuardians by remember { mutableStateOf<List<Guardian>>(emptyList()) }
    var acceptedGuardians by remember { mutableStateOf<List<Guardian>>(emptyList()) }
    var showAddGuardianDialog by remember { mutableStateOf(false) }
    var guardianEmail by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // message, isError
    var currentLatitude by remember { mutableStateOf<Double?>(null) }
    var currentLongitude by remember { mutableStateOf<Double?>(null) }
    var currentAccuracy by remember { mutableStateOf<Float?>(null) }
    
    // Load current location on screen load
    LaunchedEffect(Unit) {
        loadGuardians(guardianRepository) { pending, accepted ->
            pendingGuardians = pending
            acceptedGuardians = accepted
        }
        
        // Set location callback for real-time updates
        trackingLocationManager.setOnLocationChangedListener { lat, lng, accuracy ->
            currentLatitude = lat
            currentLongitude = lng
            currentAccuracy = accuracy
            println("[UserTrackingScreen] 📍 Location updated: $lat, $lng, accuracy: $accuracy")
        }
        
        // Try to get last known location
        scope.launch {
            val lastLocation = trackingLocationManager.getLastKnownLocation()
            if (lastLocation != null) {
                currentLatitude = lastLocation.latitude
                currentLongitude = lastLocation.longitude
                currentAccuracy = lastLocation.accuracy
            }
        }
    }
    
    Scaffold(
        topBar = {
            if (!hideTopBar) {
                TopAppBar(
                title = { 
                    Text(
                        "Pelacakan Lokasi Saya",
                        modifier = Modifier.semantics {
                            contentDescription = "Halaman pelacakan lokasi untuk keamanan Anda"
                        }
                    ) 
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
                    // Logout Button
                    IconButton(
                        onClick = {
                            // Stop tracking and service
                            trackingLocationManager.stopTracking()
                            LocationForegroundService.stopService(context)
                            
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            // 1. TRACKING TOGGLE CARD
            // ============================================
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Pelacakan Lokasi",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.semantics {
                                        contentDescription = "Pengaturan pelacakan lokasi"
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isTrackingEnabled) {
                                        "✓ Aktif - Guardian dapat melihat lokasi Anda"
                                    } else {
                                        "✗ Nonaktif - Lokasi tidak dibagikan"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isTrackingEnabled) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.semantics {
                                        contentDescription = if (isTrackingEnabled) {
                                            "Pelacakan aktif, lokasi dibagikan ke guardian"
                                        } else {
                                            "Pelacakan nonaktif, lokasi tidak dibagikan"
                                        }
                                    }
                                )
                            }
                            
                            // Large switch for accessibility
                            Switch(
                                checked = isTrackingEnabled,
                                onCheckedChange = { enabled ->
                                    isTrackingEnabled = enabled
                                    trackingPreferences.isTrackingEnabled = enabled
                                    
                                    if (enabled) {
                                        // Start foreground service untuk GPS tetap jalan
                                        LocationForegroundService.startService(context)
                                        trackingLocationManager.startTracking()
                                        onSpeakText("Pelacakan lokasi diaktifkan")
                                        message = "Pelacakan diaktifkan" to false
                                    } else {
                                        // Stop foreground service
                                        LocationForegroundService.stopService(context)
                                        trackingLocationManager.stopTracking()
                                        scope.launch {
                                            trackingLocationManager.disableTracking()
                                        }
                                        onSpeakText("Pelacakan lokasi dinonaktifkan")
                                        message = "Pelacakan dinonaktifkan" to false
                                    }
                                },
                                modifier = Modifier
                                    .size(60.dp) // Large for easy access
                                    .semantics {
                                        contentDescription = if (isTrackingEnabled) {
                                            "Pelacakan aktif, tekan untuk menonaktifkan"
                                        } else {
                                            "Pelacakan nonaktif, tekan untuk mengaktifkan"
                                        }
                                    }
                            )
                        }
                    }
                }
            }
            
            // ============================================
            // 1.5 GPS COORDINATES DISPLAY
            // ============================================
//            item {
//                Card(
//                    modifier = Modifier.fillMaxWidth(),
//                    colors = CardDefaults.cardColors(
//                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
//                    )
//                ) {
//                    Column(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(16.dp)
//                    ) {
//                        Text(
//                            text = "📍 Koordinat GPS Anda",
//                            style = MaterialTheme.typography.titleSmall,
//                            color = MaterialTheme.colorScheme.onTertiaryContainer,
//                            fontWeight = FontWeight.Bold
//                        )
//                        Spacer(modifier = Modifier.height(12.dp))
//
//                        if (isTrackingEnabled) {
//                            // Show coordinates (always, even if still loading)
//                            Text(
//                                text = "Latitude: ${currentLatitude?.let { String.format("%.8f", it) } ?: "⏳ Loading..."}",
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = MaterialTheme.colorScheme.onTertiaryContainer,
//                                fontWeight = FontWeight.SemiBold
//                            )
//                            Spacer(modifier = Modifier.height(4.dp))
//                            Text(
//                                text = "Longitude: ${currentLongitude?.let { String.format("%.8f", it) } ?: "⏳ Loading..."}",
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = MaterialTheme.colorScheme.onTertiaryContainer,
//                                fontWeight = FontWeight.SemiBold
//                            )
//                            Spacer(modifier = Modifier.height(4.dp))
//                            Text(
//                                text = "Akurasi: ${currentAccuracy?.toInt() ?: 0}m",
//                                style = MaterialTheme.typography.bodySmall,
//                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
//                            )
//                        } else {
//                            Text(
//                                text = "Aktifkan tracking untuk melihat koordinat GPS",
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
//                            )
//                        }
//                    }
//                }
//            }
            
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Tentang Pelacakan",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Lokasi dikirim setiap 100 meter atau 10 menit\n" +
                                       "• Tidak mengganggu deteksi jalan AI\n" +
                                       "• Guardian perlu izin Anda untuk melihat\n" +
                                       "• Hemat baterai dan data internet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.semantics {
                                    contentDescription = "Informasi pelacakan: " +
                                        "Lokasi dikirim minimal setiap 100 meter atau 10 menit. " +
                                        "Tidak mengganggu AI deteksi jalan. " +
                                        "Guardian perlu izin Anda. " +
                                        "Hemat baterai dan data."
                                }
                            )
                        }
                    }
                }
            }
            
            // ============================================
            // 3. GUARDIAN SECTION HEADER
            // ============================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Guardian Saya",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics {
                            contentDescription = "Daftar guardian atau wali yang dapat melihat lokasi Anda"
                        }
                    )
                    
                    IconButton(
                        onClick = { showAddGuardianDialog = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Tambah guardian baru"
                        }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Tambah",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            // ============================================
            // 4. ACCEPTED GUARDIANS LIST
            // ============================================
            if (acceptedGuardians.isNotEmpty()) {
                item {
                    Text(
                        "Guardian Aktif (${acceptedGuardians.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                
                items(acceptedGuardians) { guardian ->
                    GuardianCard(
                        guardian = guardian,
                        status = "accepted",
                        onAccept = {},
                        onReject = {},
                        onRemove = {
                            scope.launch {
                                isLoading = true
                                val result = guardianRepository.removeGuardian(guardian.id ?: "")
                                if (result.isSuccess) {
                                    loadGuardians(guardianRepository) { pending, accepted ->
                                        pendingGuardians = pending
                                        acceptedGuardians = accepted
                                    }
                                    onSpeakText("Guardian dihapus")
                                    message = "Guardian berhasil dihapus" to false
                                } else {
                                    message = "Gagal menghapus guardian" to true
                                }
                                isLoading = false
                            }
                        },
                        guardianRepository = guardianRepository
                    )
                }
            } else {
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
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Belum ada guardian",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Tambahkan orang tua atau keluarga sebagai guardian",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // ============================================
            // 5. PENDING GUARDIANS LIST (Waiting for approval)
            // ============================================
            if (pendingGuardians.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Menunggu Persetujuan (${pendingGuardians.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                items(pendingGuardians) { guardian ->
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Guardian",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Menunggu persetujuan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Text(
                                    "⏳",
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // ============================================
            // MESSAGE DISPLAY
            // ============================================
            message?.let { (msg, isError) ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            )
                            Text(
                                text = msg,
                                color = if (isError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            )
                        }
                    }
                    
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3000)
                        message = null
                    }
                }
            }
            
            // Bottom spacing
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        
        // ============================================
        // ADD GUARDIAN DIALOG
        // ============================================
        if (showAddGuardianDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showAddGuardianDialog = false
                    guardianEmail = ""
                },
                title = { 
                    Text(
                        "Tambah Guardian",
                        modifier = Modifier.semantics {
                            contentDescription = "Dialog untuk menambahkan guardian baru"
                        }
                    ) 
                },
                text = {
                    Column {
                        Text("Minta kode dari guardian (orang tua atau keluarga):")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = guardianEmail,
                            onValueChange = { guardianEmail = it.uppercase() },
                            label = { Text("Kode Guardian") },
                            placeholder = { Text("Contoh: ABC12345") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = "Masukkan kode guardian"
                                }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                val result = guardianRepository.addGuardian(guardianEmail.trim())
                                if (result.isSuccess) {
                                    loadGuardians(guardianRepository) { pending, accepted ->
                                        pendingGuardians = pending
                                        acceptedGuardians = accepted
                                    }
                                    onSpeakText("Guardian berhasil ditambahkan")
                                    message = "Guardian ditambahkan, menunggu persetujuan" to false
                                    guardianEmail = ""
                                    showAddGuardianDialog = false
                                } else {
                                    val errorMsg = result.exceptionOrNull()?.message 
                                        ?: "Gagal menambahkan guardian"
                                    message = errorMsg to true
                                }
                                isLoading = false
                            }
                        },
                        enabled = guardianEmail.isNotBlank() && !isLoading,
                        modifier = Modifier.semantics {
                            contentDescription = "Tombol tambah guardian"
                        }
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Tambah")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showAddGuardianDialog = false
                            guardianEmail = ""
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Tombol batal"
                        }
                    ) {
                        Text("Batal")
                    }
                }
            )
        }
        
        // Loading overlay
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
 * GUARDIAN CARD COMPONENT
 */
@Composable
private fun GuardianCard(
    guardian: Guardian,
    status: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onRemove: () -> Unit,
    guardianRepository: GuardianRepository
) {
    val scope = rememberCoroutineScope()
    var guardianProfile by remember { mutableStateOf<Profile?>(null) }
    
    LaunchedEffect(guardian.guardianId) {
        scope.launch {
            val result = guardianRepository.getGuardianProfile(guardian.guardianId)
            if (result.isSuccess) {
                guardianProfile = result.getOrNull()
            }
        }
    }
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = guardianProfile?.email ?: "Loading...",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics {
                        contentDescription = "Guardian: ${guardianProfile?.email ?: "memuat"}"
                    }
                )
                if (guardianProfile?.fullName != null) {
                    Text(
                        text = guardianProfile!!.fullName!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = when (status) {
                        "pending" -> "⏳ Menunggu persetujuan"
                        "accepted" -> "✓ Aktif"
                        else -> status
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status) {
                        "accepted" -> MaterialTheme.colorScheme.tertiary
                        "pending" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Status: $status"
                    }
                )
            }
            
            when (status) {
                "pending" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = onAccept,
                            modifier = Modifier.semantics {
                                contentDescription = "Setujui guardian ${guardianProfile?.email ?: ""}"
                            }
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        IconButton(
                            onClick = onReject,
                            modifier = Modifier.semantics {
                                contentDescription = "Tolak guardian ${guardianProfile?.email ?: ""}"
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                "accepted" -> {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.semantics {
                            contentDescription = "Hapus guardian ${guardianProfile?.email ?: ""}"
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * HELPER: Load guardians from repository
 */
private suspend fun loadGuardians(
    repository: GuardianRepository,
    onLoaded: (pending: List<Guardian>, accepted: List<Guardian>) -> Unit
) {
    val pendingResult = repository.getPendingGuardians()
    val acceptedResult = repository.getAcceptedGuardians()
    
    onLoaded(
        pendingResult.getOrNull() ?: emptyList(),
        acceptedResult.getOrNull() ?: emptyList()
    )
}
