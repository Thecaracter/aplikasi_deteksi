package com.example.aplikasi_deteksi_jalan.tracking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.aplikasi_deteksi_jalan.tracking.data.repository.GuardianRepository
import com.example.aplikasi_deteksi_jalan.tracking.manager.TrackingPreferences
import kotlinx.coroutines.launch

/**
 * TRACKING SETTINGS SCREEN (CONTOH UI SEDERHANA)
 * 
 * Fitur:
 * 1. Toggle tracking ON/OFF
 * 2. Tambah guardian
 * 3. List guardian aktif
 * 4. Accept/reject guardian requests
 * 
 * AKSESIBILITAS:
 * - Semua element punya contentDescription
 * - Compatible dengan TalkBack
 * - Fokus tidak mengganggu AI audio
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingSettingsScreen(
    trackingPreferences: TrackingPreferences,
    onEnableTracking: () -> Unit,
    onDisableTracking: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val guardianRepository = remember { GuardianRepository() }
    
    var isTrackingEnabled by remember { 
        mutableStateOf(trackingPreferences.isTrackingEnabled) 
    }
    var showAddGuardianDialog by remember { mutableStateOf(false) }
    var guardianEmail by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Pengaturan Pelacakan",
                        modifier = Modifier.semantics {
                            contentDescription = "Halaman pengaturan pelacakan lokasi"
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Kembali ke halaman sebelumnya"
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, "Kembali")
                    }
                }
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
            
            // ============================================
            // 1. TRACKING TOGGLE CARD
            // ============================================
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
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
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics {
                                    contentDescription = "Pelacakan lokasi untuk keamanan"
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isTrackingEnabled) {
                                    "Lokasi Anda dapat dilihat oleh guardian"
                                } else {
                                    "Lokasi Anda tidak dibagikan"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics {
                                    contentDescription = if (isTrackingEnabled) {
                                        "Pelacakan aktif, lokasi dibagikan"
                                    } else {
                                        "Pelacakan nonaktif, lokasi tidak dibagikan"
                                    }
                                }
                            )
                        }
                        
                        Switch(
                            checked = isTrackingEnabled,
                            onCheckedChange = { enabled ->
                                isTrackingEnabled = enabled
                                if (enabled) {
                                    onEnableTracking()
                                    successMessage = "Pelacakan diaktifkan"
                                } else {
                                    onDisableTracking()
                                    successMessage = "Pelacakan dinonaktifkan"
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = if (isTrackingEnabled) {
                                    "Pelacakan aktif, tekan untuk matikan"
                                } else {
                                    "Pelacakan nonaktif, tekan untuk aktifkan"
                                }
                            }
                        )
                    }
                }
            }
            
            // ============================================
            // 2. INFO CARD
            // ============================================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Column {
                        Text(
                            text = "Tentang Pelacakan",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Lokasi hanya dikirim setiap 100 meter\n" +
                                   "• Tidak mengganggu deteksi jalan AI\n" +
                                   "• Guardian hanya bisa lihat jika Anda izinkan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.semantics {
                                contentDescription = "Informasi pelacakan. " +
                                    "Lokasi dikirim setiap 100 meter. " +
                                    "Tidak mengganggu AI. " +
                                    "Guardian perlu izin"
                            }
                        )
                    }
                }
            }
            
            // ============================================
            // 3. GUARDIAN SECTION
            // ============================================
            Text(
                text = "Guardian (Keluarga/Orang Tua)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    contentDescription = "Bagian pengaturan guardian"
                }
            )
            
            Button(
                onClick = { showAddGuardianDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Tambah guardian baru"
                    }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tambah Guardian")
            }
            
            // TODO: List guardians (pending & accepted)
            // Gunakan LazyColumn untuk list
            
            // ============================================
            // 4. MESSAGES (Success/Error)
            // ============================================
            errorMessage?.let { message ->
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
                            text = message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.semantics {
                                contentDescription = "Error: $message"
                            }
                        )
                    }
                }
            }
            
            successMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
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
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.semantics {
                                contentDescription = "Sukses: $message"
                            }
                        )
                    }
                }
            }
        }
        
        // ============================================
        // 5. ADD GUARDIAN DIALOG
        // ============================================
        if (showAddGuardianDialog) {
            AlertDialog(
                onDismissRequest = { showAddGuardianDialog = false },
                title = { 
                    Text(
                        "Tambah Guardian",
                        modifier = Modifier.semantics {
                            contentDescription = "Dialog tambah guardian"
                        }
                    ) 
                },
                text = {
                    Column {
                        Text("Minta kode dari guardian (orang tua atau keluarga):")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = guardianEmail,
                            onValueChange = { guardianEmail = it.uppercase() },
                            label = { Text("Kode Guardian") },
                            placeholder = { Text("Contoh: ABC12345") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = "Input kode guardian"
                                }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val result = guardianRepository.addGuardian(guardianEmail)
                                if (result.isSuccess) {
                                    successMessage = "Guardian berhasil ditambahkan"
                                    guardianEmail = ""
                                    showAddGuardianDialog = false
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message 
                                        ?: "Gagal menambahkan guardian"
                                }
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Tombol tambah guardian"
                        }
                    ) {
                        Text("Tambah")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddGuardianDialog = false },
                        modifier = Modifier.semantics {
                            contentDescription = "Tombol batal"
                        }
                    ) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

/**
 * ============================================
 * GUARDIAN ITEM (untuk LazyColumn)
 * ============================================
 */
@Composable
fun GuardianItem(
    guardianEmail: String,
    status: String, // "pending", "accepted", "rejected"
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
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
                    text = guardianEmail,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics {
                        contentDescription = "Guardian: $guardianEmail"
                    }
                )
                Text(
                    text = when (status) {
                        "pending" -> "Menunggu persetujuan"
                        "accepted" -> "Aktif"
                        "rejected" -> "Ditolak"
                        else -> status
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status) {
                        "accepted" -> MaterialTheme.colorScheme.primary
                        "rejected" -> MaterialTheme.colorScheme.error
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
                                contentDescription = "Terima guardian $guardianEmail"
                            }
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onReject,
                            modifier = Modifier.semantics {
                                contentDescription = "Tolak guardian $guardianEmail"
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
                            contentDescription = "Hapus guardian $guardianEmail"
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
