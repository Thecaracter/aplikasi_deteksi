package com.example.aplikasi_deteksi_jalan.tracking.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.preference.PreferenceManager
import com.example.aplikasi_deteksi_jalan.tracking.data.models.UserLocation
import com.example.aplikasi_deteksi_jalan.tracking.data.repository.LocationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

/**
 * GUARDIAN MAP DETAIL SCREEN
 *
 * Full-screen map view untuk menampilkan lokasi satu user
 * - Scroll map dengan 2 fingers
 * - Pinch to zoom
 * - Marker dengan user email dan akurasi
 * - Real-time polling setiap 5 detik
 * - Back button untuk kembali ke list
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianMapDetailScreen(
    userId: String,
    userName: String = "User Location",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val locationRepository = remember { LocationRepository() }
    
    var userLocation by remember { mutableStateOf<UserLocation?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Initialize OSMDroid
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
    }

    // Fetch location on first load
    LaunchedEffect(userId) {
        coroutineScope.launch {
            try {
                isLoading = true
                val result = locationRepository.getUsersLocationsOnce(listOf(userId))
                if (result.isNotEmpty()) {
                    userLocation = result.first()
                    println("[MapDetailScreen] ✅ Loaded location for $userId: ${result.first().latitude}, ${result.first().longitude}")
                } else {
                    errorMessage = "Lokasi user tidak ditemukan"
                    println("[MapDetailScreen] ❌ No location found for $userId")
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
                println("[MapDetailScreen] ❌ Error loading location: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    // Polling setiap 5 detik untuk update lokasi
    LaunchedEffect(userId) {
        while (true) {
            delay(5000) // 5 seconds
            try {
                val result = locationRepository.getUsersLocationsOnce(listOf(userId))
                if (result.isNotEmpty()) {
                    userLocation = result.first()
                    println("[MapDetailScreen] ⏱️ Polling: Updated location at ${result.first().latitude}, ${result.first().longitude}")
                }
            } catch (e: Exception) {
                println("[MapDetailScreen] ⚠️ Polling error: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (userLocation != null) {
                            Text(
                                "Lat: ${String.format("%.4f", userLocation!!.latitude)}, Lng: ${String.format("%.4f", userLocation!!.longitude)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && userLocation == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "❌ Error",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                userLocation != null -> {
                    // MAP VIEW
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(15.0)

                                val geoPoint = GeoPoint(
                                    userLocation!!.latitude,
                                    userLocation!!.longitude
                                )
                                controller.setCenter(geoPoint)

                                // Add marker for user location
                                val marker = Marker(this).apply {
                                    position = geoPoint
                                    title = userName
                                    snippet = "Akurasi: ${String.format("%.1f", userLocation!!.accuracy)}m\n${if (userLocation!!.timestamp != null) try { val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ", Locale.getDefault()); SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(isoFormat.parse(userLocation!!.timestamp!!)) } catch (e: Exception) { "Unknown" } else "Unknown"}"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                overlays.add(marker)
                            }
                        },
                        update = { mapView ->
                            // Update marker position when location changes
                            if (userLocation != null) {
                                val geoPoint = GeoPoint(
                                    userLocation!!.latitude,
                                    userLocation!!.longitude
                                )

                                // Remove old markers
                                mapView.overlays.removeAll { it is Marker }

                                // Add new marker
                                val marker = Marker(mapView).apply {
                                    position = geoPoint
                                    title = userName
                                    snippet = "Akurasi: ${String.format("%.1f", userLocation!!.accuracy)}m\n${if (userLocation!!.timestamp != null) try { val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ", Locale.getDefault()); SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(isoFormat.parse(userLocation!!.timestamp!!)) } catch (e: Exception) { "Unknown" } else "Unknown"}"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                mapView.overlays.add(marker)

                                // Animate to new location
                                mapView.controller.animateTo(geoPoint)
                                mapView.invalidate()
                            }
                        }
                    )

                    // Info card at bottom
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "📍 Koordinat",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${String.format("%.6f", userLocation!!.latitude)}, ${String.format("%.6f", userLocation!!.longitude)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "Akurasi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${String.format("%.1f", userLocation!!.accuracy)}m",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "🕐 Update Terakhir",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        if (userLocation!!.timestamp != null) {
                                            try {
                                                // Parse ISO 8601 format: "2026-01-03T19:33:49.269973+00:00"
                                                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ", Locale.getDefault())
                                                val date = isoFormat.parse(userLocation!!.timestamp!!)
                                                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
                                            } catch (e: Exception) {
                                                "Unknown"
                                            }
                                        } else {
                                            "Unknown"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Polling every 5s",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        "Tidak ada data lokasi",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
