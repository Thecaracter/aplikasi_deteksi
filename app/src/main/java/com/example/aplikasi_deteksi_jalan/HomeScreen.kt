package com.example.aplikasi_deteksi_jalan

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aplikasi_deteksi_jalan.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToImageDetection: () -> Unit,
    onNavigateToRealtimeDetection: () -> Unit,
    onNavigateToTracking: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Road Detection",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MediumBlue,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE3F2FD),
                            Color(0xFFBBDEFB),
                            Color.White
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(24.dp))
                        .animateContentSize(
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = LinearOutSlowInEasing
                            )
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🛣️",
                            fontSize = 64.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Deteksi Jalan Berlubang",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MediumBlue,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Pilih metode deteksi",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Modern Card Button - Upload Image
                ModernFeatureCard(
                    title = "Upload Gambar",
                    description = "Analisis foto dari galeri",
                    emoji = "🖼️",
                    gradient = Brush.horizontalGradient(
                        colors = listOf(MediumBlue, LightBlue)
                    ),
                    onClick = onNavigateToImageDetection
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Modern Card Button - Realtime Detection
                ModernFeatureCard(
                    title = "Deteksi Real-time",
                    description = "Scan langsung dengan kamera",
                    emoji = "📷",
                    gradient = Brush.horizontalGradient(
                        colors = listOf(AccentAmber, AccentOrange)
                    ),
                    onClick = onNavigateToRealtimeDetection
                )
                                Spacer(modifier = Modifier.height(20.dp))
                
                // Modern Card Button - Tracking Lokasi
                ModernFeatureCard(
                    title = "Tracking Lokasi",
                    description = "Pantau lokasi untuk keamanan",
                    emoji = "📍",
                    gradient = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                    ),
                    onClick = onNavigateToTracking
                )
                                Spacer(modifier = Modifier.height(20.dp))
                
                // Modern Card Button - Tracking Lokasi
                ModernFeatureCard(
                    title = "Tracking Lokasi",
                    description = "Pantau lokasi untuk keamanan",
                    emoji = "📍",
                    gradient = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                    ),
                    onClick = onNavigateToTracking
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MediumBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI-Powered Detection",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Menggunakan TensorFlow Lite untuk deteksi akurat",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernFeatureCard(
    title: String,
    description: String,
    emoji: String,
    gradient: Brush,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Text(
                    text = emoji,
                    fontSize = 48.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
