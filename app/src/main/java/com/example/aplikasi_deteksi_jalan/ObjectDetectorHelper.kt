package com.example.aplikasi_deteksi_jalan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

data class DetectionResult(
    val boundingBox: RectF,
    val label: String,
    val confidence: Float,
    val distance: Float? = null
)

/**
 * YOLO v11 TFLite Object Detector
 * Format output: (1, 5, 8400) -> perlu transpose jadi (8400, 5)
 * Format deteksi: [cx, cy, w, h, confidence]
 */
class ObjectDetectorHelper(
    private val context: Context,
    private val modelName: String = "best_int8.tflite", // INT8 untuk kecepatan realtime
    private val threshold: Float = 0.00002f, // Threshold sangat rendah karena INT8 model output kecil (0.00001-0.0001)
    private val maxResults: Int = 5, // Limit hasil deteksi
    private val useOptimizedSize: Boolean = true // Gunakan size kecil untuk realtime
) {
    private var interpreter: Interpreter? = null
    private val labels = listOf("jalan_berlubang")
    
    // Konfigurasi model YOLO v11 - akan diset dari model shape
    private var inputSize = 640
    private val numDetections = 8400
    private val numChannels = 5
    
    init {
        setupInterpreter()
    }
    
    private fun setupInterpreter() {
        try {
            val model = FileUtil.loadMappedFile(context, modelName)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            }
            interpreter = Interpreter(model, options)
            
            // Ambil input size dari model (HARUS pakai size ini, tidak bisa di-override)
            val inputTensor = interpreter?.getInputTensor(0)
            val inputShape = inputTensor?.shape()
            if (inputShape != null && inputShape.size >= 4) {
                // Shape format: [batch, height, width, channels] -> [1, 640, 640, 3]
                inputSize = inputShape[1]
            }
            
            val outputTensor = interpreter?.getOutputTensor(0)
            println("[ObjectDetector] ========== MODEL INFO ==========")
            println("[ObjectDetector] Model: $modelName (INT8 Quantized)")
            println("[ObjectDetector] Input shape: ${inputShape?.contentToString()}")
            println("[ObjectDetector] Output shape: ${outputTensor?.shape()?.contentToString()}")
            println("[ObjectDetector] Input size: ${inputSize}x${inputSize} (FIXED by model)")
            println("[ObjectDetector] Threshold: $threshold (${(threshold * 100).toInt()}% confidence)")
            println("[ObjectDetector] ================================")
        } catch (e: Exception) {
            println("[ObjectDetector] ERROR: Failed to load model '$modelName'")
            e.printStackTrace()
        }
    }
    
    fun detectObjects(bitmap: Bitmap, enableDebugLogs: Boolean = false): List<DetectionResult> {
        if (interpreter == null) {
            println("[ObjectDetector] ERROR: Interpreter is null!")
            return emptyList()
        }
        
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        
        if (enableDebugLogs) {
            println("[ObjectDetector] ===== DETECTION START =====")
            println("[ObjectDetector] Input: ${originalWidth}x${originalHeight}, Model: $modelName, Threshold: $threshold")
        }
        
        // 1. Preprocess: Resize dan normalize ke [0,1]
        val inputBuffer = preprocessImage(bitmap)
        
        // 2. Prepare output buffer
        val outputBuffer = ByteBuffer.allocateDirect(1 * numChannels * numDetections * 4)
            .order(ByteOrder.nativeOrder())
        
        // 3. Run inference
        val startTime = System.currentTimeMillis()
        interpreter?.run(inputBuffer, outputBuffer)
        val inferenceTime = System.currentTimeMillis() - startTime
        
        if (enableDebugLogs) {
            println("[ObjectDetector] Inference time: ${inferenceTime}ms")
        }
        
        // 4. Parse output
        outputBuffer.rewind()
        val output = FloatArray(numChannels * numDetections)
        outputBuffer.asFloatBuffer().get(output)
        
        // 5. Transpose dari (5, 8400) ke (8400, 5) dan filter deteksi
        val results = mutableListOf<DetectionResult>()
        var detectedAboveThreshold = 0
        
        for (i in 0 until numDetections) {
            // Ambil data per deteksi: cx, cy, w, h, conf
            val cx = output[0 * numDetections + i]
            val cy = output[1 * numDetections + i]
            val w = output[2 * numDetections + i]
            val h = output[3 * numDetections + i]
            val confidence = output[4 * numDetections + i]
            
            // Filter berdasarkan confidence threshold
            if (confidence < threshold) continue
            
            detectedAboveThreshold++
            
            // cx, cy, w, h sudah dalam bentuk normalized (0-1)
            // Konversi langsung ke koordinat gambar asli
            val x1 = ((cx - w / 2f) * originalWidth).coerceIn(0f, originalWidth.toFloat())
            val y1 = ((cy - h / 2f) * originalHeight).coerceIn(0f, originalHeight.toFloat())
            val x2 = ((cx + w / 2f) * originalWidth).coerceIn(0f, originalWidth.toFloat())
            val y2 = ((cy + h / 2f) * originalHeight).coerceIn(0f, originalHeight.toFloat())
            
            val boundingBox = RectF(x1, y1, x2, y2)
            
            // Filter: buang deteksi yang terlalu kecil (<20px) atau terlalu besar (>80% gambar)
            val boxWidth = x2 - x1
            val boxHeight = y2 - y1
            val boxArea = boxWidth * boxHeight
            val imageArea = originalWidth * originalHeight
            val areaRatio = boxArea / imageArea
            
            if (boxWidth < 20 || boxHeight < 20 || areaRatio > 0.8f) {
                continue // Skip deteksi yang tidak masuk akal
            }
            
            results.add(DetectionResult(boundingBox, labels[0], confidence))
        }
        
        // 6. Apply Non-Maximum Suppression (NMS) untuk remove overlapping boxes
        val nmsResults = applyNMS(results, iouThreshold = 0.3f) // Lebih aggressive untuk hapus duplikat
        
        if (enableDebugLogs) {
            println("[ObjectDetector] Raw detections above threshold ($threshold): $detectedAboveThreshold")
            println("[ObjectDetector] After NMS: ${nmsResults.size} detections")
        }
        
        // 7. Limit jumlah hasil - filter sudah dilakukan di atas, langsung ambil top results
        return nmsResults
            .sortedByDescending { it.confidence }
            .take(maxResults)
    }
    
    /**
     * Preprocess image: resize dan normalize ke [0,1]
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, false)
        
        val inputBuffer = ByteBuffer.allocateDirect(4 * 1 * inputSize * inputSize * 3)
            .order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)
        
        val floatBuffer = inputBuffer.asFloatBuffer()
        
        // Normalize to [0, 1]
        for (pixel in intValues) {
            floatBuffer.put(((pixel shr 16) and 0xFF) / 255.0f) // R
            floatBuffer.put(((pixel shr 8) and 0xFF) / 255.0f)  // G
            floatBuffer.put((pixel and 0xFF) / 255.0f)          // B
        }
        
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }
        
        return inputBuffer
    }
    
    /**
     * Non-Maximum Suppression untuk remove overlapping boxes
     */
    private fun applyNMS(
        detections: List<DetectionResult>,
        iouThreshold: Float = 0.45f
    ): List<DetectionResult> {
        if (detections.isEmpty()) return emptyList()
        
        // Sort by confidence descending
        val sortedDetections = detections.sortedByDescending { it.confidence }
        val selectedDetections = mutableListOf<DetectionResult>()
        val suppressed = BooleanArray(sortedDetections.size)
        
        for (i in sortedDetections.indices) {
            if (suppressed[i]) continue
            
            selectedDetections.add(sortedDetections[i])
            
            // Suppress boxes with high IoU
            for (j in i + 1 until sortedDetections.size) {
                if (suppressed[j]) continue
                
                val iou = calculateIoU(
                    sortedDetections[i].boundingBox,
                    sortedDetections[j].boundingBox
                )
                
                if (iou > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        
        return selectedDetections
    }
    
    /**
     * Calculate Intersection over Union (IoU) between two bounding boxes
     */
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = kotlin.math.min(box1.right, box2.right)
        val intersectionBottom = kotlin.math.min(box1.bottom, box2.bottom)
        
        val intersectionArea = max(0f, intersectionRight - intersectionLeft) *
                              max(0f, intersectionBottom - intersectionTop)
        
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        
        val unionArea = box1Area + box2Area - intersectionArea
        
        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }
    
    /**
     * Menghitung estimasi jarak berdasarkan ukuran bounding box
     * Formula: distance = (knownRealHeight * focalLength) / imageHeight
     * 
     * Asumsi:
     * - Lubang jalan rata-rata 30-50cm diameter
     * - Kamera smartphone typical focal length ~26mm (35mm equivalent)
     * - Untuk gambar yang sudah di-resize, perlu adjust focal length
     */
    fun calculateDistance(boundingBox: RectF, knownRealHeight: Float = 0.5f): Float {
        // Gunakan tinggi atau lebar box, mana yang lebih besar (untuk handle berbagai orientasi lubang)
        val boxHeight = boundingBox.height()
        val boxWidth = boundingBox.width()
        val boxSize = max(boxHeight, boxWidth)
        
        println("[Distance] Box size: ${boxSize}px (H: ${boxHeight}px, W: ${boxWidth}px)")
        
        if (boxSize < 5f) {
            println("[Distance] Box too small, returning 0")
            return 0f
        }
        
        // Focal length disesuaikan dengan ukuran gambar
        // Formula empiris: distance ≈ (realSize * calibrationFactor) / pixelSize
        val calibrationFactor = 100f // Adjust berdasarkan testing
        
        val distance = (knownRealHeight * calibrationFactor) / boxSize
        
        println("[Distance] Calculated: ${distance}m (raw), clamped: ${distance.coerceIn(0.5f, 50f)}m")
        
        // Clamp jarak ke range realistis (0.5m - 50m)
        return distance.coerceIn(0.5f, 50f)
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
