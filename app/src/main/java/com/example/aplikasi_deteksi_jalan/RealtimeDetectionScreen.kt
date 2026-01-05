package com.example.aplikasi_deteksi_jalan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.aplikasi_deteksi_jalan.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RealtimeDetectionScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )
    
    var detectionResults by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    val objectDetector = remember { ObjectDetectorHelper(context, useOptimizedSize = true) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Throttling untuk realtime detection - balance antara speed dan responsiveness
    var lastInferenceTime by remember { mutableStateOf(0L) }
    val minInferenceInterval = 300L // 300ms = max 3 FPS
    var isProcessing by remember { mutableStateOf(false) }
    
    // FPS tracking - update setiap frame, bukan setiap detection
    var fps by remember { mutableStateOf(0f) }
    var frameCount by remember { mutableStateOf(0) }
    var lastFpsUpdate by remember { mutableStateOf(System.currentTimeMillis()) }
    
    DisposableEffect(Unit) {
        onDispose {
            objectDetector.close()
            cameraExecutor.shutdown()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Deteksi Real-time",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (cameraPermissionState.status.isGranted) {
                CameraPreview(
    onDetectionResults = { results ->
                        // Update detection results - selalu update (termasuk empty untuk clear)
                        detectionResults = results
                        
                        // Update FPS
                        frameCount++
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastFpsUpdate
                        if (timeDiff >= 1000) {
                            fps = (frameCount * 1000f) / timeDiff
                            frameCount = 0
                            lastFpsUpdate = currentTime
                        }
                    },
                    objectDetector = objectDetector,
                    cameraExecutor = cameraExecutor,
                    lastInferenceTime = lastInferenceTime,
                    minInferenceInterval = minInferenceInterval,
                    isProcessing = isProcessing,
                    onProcessingChanged = { isProcessing = it },
                    onInferenceTimeUpdated = { lastInferenceTime = it }
                )
                
                // Overlay untuk menampilkan hasil deteksi
                DetectionOverlay(
                    detectionResults = detectionResults,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Modern FPS Display
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.75f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚡",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = "${"%.1f".format(fps)} FPS",
                            color = AccentAmber,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Modern Panel hasil deteksi
                if (detectionResults.isNotEmpty()) {
                    DetectionResultsPanel(
                        detectionResults = detectionResults,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            } else {
                // Modern permission request screen
                Box(
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
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "📷",
                                    fontSize = 72.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                
                                Text(
                                    text = "Izin Kamera Diperlukan",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = "Aplikasi membutuhkan akses kamera untuk melakukan deteksi lubang jalan secara real-time",
                                    textAlign = TextAlign.Center,
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Button(
                                    onClick = { cameraPermissionState.launchPermissionRequest() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MediumBlue
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        "Berikan Izin Kamera",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
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

@Composable
fun CameraPreview(
    onDetectionResults: (List<DetectionResult>) -> Unit,
    objectDetector: ObjectDetectorHelper,
    cameraExecutor: java.util.concurrent.ExecutorService,
    lastInferenceTime: Long,
    minInferenceInterval: Long,
    isProcessing: Boolean,
    onProcessingChanged: (Boolean) -> Unit,
    onInferenceTimeUpdated: (Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    val previewView = remember { PreviewView(context) }
    
    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(320, 240)) // Lebih kecil untuk speed
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val currentTime = System.currentTimeMillis()
                        
                        // AGRESIF skip frame: hanya proses jika sudah lewat interval DAN tidak sedang processing
                        if (!isProcessing && (currentTime - lastInferenceTime) >= minInferenceInterval) {
                            onProcessingChanged(true)
                            onInferenceTimeUpdated(currentTime)
                            
                            scope.launch {
                                processImageFast(imageProxy, objectDetector, onDetectionResults)
                                onProcessingChanged(false)
                            }
                        } else {
                            // Skip frame ini untuk speed
                            imageProxy.close()
                        }
                    }
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("Camera", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

// Optimasi untuk realtime - SANGAT cepat
private suspend fun processImageFast(
    imageProxy: ImageProxy,
    objectDetector: ObjectDetectorHelper,
    onDetectionResults: (List<DetectionResult>) -> Unit
) {
    withContext(Dispatchers.Default) {
        try {
            val startTime = System.currentTimeMillis()
            
            // Convert imageProxy to bitmap
            val bitmap = imageProxy.toBitmap()
            
            // Rotate jika perlu
            val rotatedBitmap = if (imageProxy.imageInfo.rotationDegrees != 0) {
                rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
            } else {
                bitmap
            }
            
            // Langsung deteksi tanpa resize lagi (ObjectDetector sudah handle resize ke 320)
            val results = objectDetector.detectObjects(rotatedBitmap)
            
            // Hitung jarak untuk setiap deteksi
            val resultsWithDistance = results.map { result ->
                val distance = objectDetector.calculateDistance(result.boundingBox)
                result.copy(distance = distance)
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d("RealtimeDetection", "Processing: ${processingTime}ms, detections: ${resultsWithDistance.size}")
            
            withContext(Dispatchers.Main) {
                onDetectionResults(resultsWithDistance)
            }
            
            // Cleanup
            if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e("Detection", "Error processing image", e)
        } finally {
            imageProxy.close()
        }
    }
}

private suspend fun processImage(
    imageProxy: ImageProxy,
    objectDetector: ObjectDetectorHelper,
    onDetectionResults: (List<DetectionResult>) -> Unit
) {
    withContext(Dispatchers.Default) {
        try {
            val startTime = System.currentTimeMillis()
            
            val bitmap = imageProxy.toBitmap()
            val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat())
            
            // Resize untuk proses lebih cepat (max 640px)
            val maxDimension = 640
            val scaledBitmap = if (rotatedBitmap.width > maxDimension || rotatedBitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / Math.max(rotatedBitmap.width, rotatedBitmap.height)
                val newWidth = (rotatedBitmap.width * scale).toInt()
                val newHeight = (rotatedBitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(rotatedBitmap, newWidth, newHeight, false)
            } else {
                rotatedBitmap
            }
            
            val results = objectDetector.detectObjects(scaledBitmap)
            
            // Hitung jarak untuk setiap deteksi
            val resultsWithDistance = results.map { result ->
                val distance = objectDetector.calculateDistance(result.boundingBox)
                result.copy(distance = distance)
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d("RealtimeDetection", "Processing time: ${processingTime}ms, detections: ${resultsWithDistance.size}")
            
            withContext(Dispatchers.Main) {
                onDetectionResults(resultsWithDistance)
            }
            
            // Cleanup
            if (scaledBitmap != rotatedBitmap) scaledBitmap.recycle()
            if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e("Detection", "Error processing image", e)
        } finally {
            imageProxy.close()
        }
    }
}

private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

@Composable
fun DetectionOverlay(
    detectionResults: List<DetectionResult>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        // Scale coordinates dari imageProxy (480x640) ke canvas size (preview size)
        // ImageProxy: width=480, height=640
        // Canvas: size.width, size.height
        val scaleX = size.width / 480f
        val scaleY = size.height / 640f
        
        detectionResults.forEach { result ->
            val box = result.boundingBox
            val color = Color.Red // Red for pothole detection
            
            // Scale bounding box ke ukuran canvas
            val scaledLeft = box.left * scaleX
            val scaledTop = box.top * scaleY
            val scaledWidth = box.width() * scaleX
            val scaledHeight = box.height() * scaleY
            
            drawRect(
                color = color,
                topLeft = Offset(scaledLeft, scaledTop),
                size = Size(scaledWidth, scaledHeight),
                style = Stroke(width = 10f)
            )
        }
    }
}

@Composable
fun DetectionResultsPanel(
    detectionResults: List<DetectionResult>,
    modifier: Modifier = Modifier
) {
    if (detectionResults.isNotEmpty()) {
        Card(
            modifier = modifier.shadow(12.dp, RoundedCornerShape(20.dp, 20.dp, 0.dp, 0.dp)),
            shape = RoundedCornerShape(20.dp, 20.dp, 0.dp, 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header dengan warning icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Lubang Terdeteksi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${detectionResults.size} objek ditemukan",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Detection items dengan design modern
                detectionResults.take(3).forEachIndexed { index, result ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = DangerRed.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lubang #${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                result.distance?.let { dist ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "📏",
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${String.format("%.1f", dist)}m",
                                            fontSize = 14.sp,
                                            color = MediumBlue,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DangerRed
                            ) {
                                Text(
                                    text = "${(result.confidence * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                
                // Show more indicator
                if (detectionResults.size > 3) {
                    Text(
                        text = "+${detectionResults.size - 3} lainnya",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
