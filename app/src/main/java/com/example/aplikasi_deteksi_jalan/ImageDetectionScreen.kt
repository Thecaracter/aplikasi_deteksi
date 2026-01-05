package com.example.aplikasi_deteksi_jalan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aplikasi_deteksi_jalan.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDetectionScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectionResults by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    
    // Untuk image upload, bisa gunakan full quality
    val objectDetector = remember { ObjectDetectorHelper(context, useOptimizedSize = false) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        uri?.let {
            scope.launch {
                isProcessing = true
                withContext(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val loadedBitmap = BitmapFactory.decodeStream(inputStream)
                        
                        // Optimasi: Resize gambar jika terlalu besar untuk proses lebih cepat
                        val maxDimension = 1280
                        val scaledBitmap = if (loadedBitmap.width > maxDimension || loadedBitmap.height > maxDimension) {
                            val scale = maxDimension.toFloat() / Math.max(loadedBitmap.width, loadedBitmap.height)
                            val newWidth = (loadedBitmap.width * scale).toInt()
                            val newHeight = (loadedBitmap.height * scale).toInt()
                            Bitmap.createScaledBitmap(loadedBitmap, newWidth, newHeight, true)
                        } else {
                            loadedBitmap
                        }
                        
                        bitmap = scaledBitmap
                        
                        // Deteksi objek dengan debug logs enabled
                        val results = objectDetector.detectObjects(scaledBitmap, enableDebugLogs = true)
                        
                        if (results.isNotEmpty()) {
                            // Gambar bounding box langsung ke bitmap SEBELUM set detectionResults
                            val mutableBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, true)
                            val canvas = Canvas(mutableBitmap)
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.RED
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 12f // Lebih tebal
                                isAntiAlias = true
                            }
                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 50f
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            val bgPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.RED
                                style = android.graphics.Paint.Style.FILL
                            }
                            
                            val resultsWithDistance = mutableListOf<DetectionResult>()
                            
                            results.forEach { result ->
                                val box = result.boundingBox
                                
                                // Hitung jarak
                                val distance = objectDetector.calculateDistance(box)
                                resultsWithDistance.add(result.copy(distance = distance))
                                
                                // Draw red bounding box
                                canvas.drawRect(box.left, box.top, box.right, box.bottom, paint)
                                
                                // Draw label background
                                val label = "${(result.confidence * 100).toInt()}%"
                                val textBounds = android.graphics.Rect()
                                textPaint.getTextBounds(label, 0, label.length, textBounds)
                                
                                val bgTop = if (box.top > 60f) box.top - 60f else box.bottom + 5f
                                canvas.drawRect(
                                    box.left,
                                    bgTop,
                                    box.left + textBounds.width() + 30f,
                                    bgTop + 55f,
                                    bgPaint
                                )
                                
                                // Draw label text
                                canvas.drawText(
                                    label,
                                    box.left + 15f,
                                    bgTop + 40f,
                                    textPaint
                                )
                            }
                            
                            // Set bitmap dengan drawing
                            bitmap = mutableBitmap
                            detectionResults = resultsWithDistance
                        } else {
                            bitmap = scaledBitmap
                            detectionResults = emptyList()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                isProcessing = false
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            objectDetector.close()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Analisis Gambar",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MediumBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE3F2FD),
                            Color.White
                        )
                    )
                )
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upload Button with modern design
            Card(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(MediumBlue, LightBlue)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🖼️",
                            fontSize = 56.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Pilih Gambar dari Galeri",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap untuk memilih",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            if (isProcessing) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MediumBlue,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Menganalisis gambar...",
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            "Mohon tunggu sebentar",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            bitmap?.let { bmp ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Detected Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Hasil deteksi dengan design modern
                if (detectionResults.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Lubang Terdeteksi: ${detectionResults.size}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            detectionResults.forEachIndexed { index, result ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = DangerRed.copy(alpha = 0.08f)
                                    )
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
                                                text = "Lubang #${index + 1}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = TextPrimary
                                            )
                                            result.distance?.let { dist ->
                                                Text(
                                                    text = "📏 ${String.format("%.2f", dist)} meter",
                                                    fontSize = 14.sp,
                                                    color = MediumBlue,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = DangerRed
                                        ) {
                                            Text(
                                                text = "${(result.confidence * 100).toInt()}%",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!isProcessing && bitmap != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = SuccessGreen.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✓",
                                fontSize = 32.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = "Jalan Aman",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = SuccessGreen
                                )
                                Text(
                                    text = "Tidak ada lubang terdeteksi",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
