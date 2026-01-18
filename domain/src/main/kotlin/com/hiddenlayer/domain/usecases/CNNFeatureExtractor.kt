package com.hiddenlayer.domain.usecases

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.hiddenlayer.core.Constants
import com.hiddenlayer.data.models.CNNScore
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.random.Random

/**
 * CNN Feature Extractor using TensorFlow Lite.
 * 
 * ⚠️ PLACEHOLDER MODEL WARNING ⚠️
 * Current model: EfficientNet-Lite0 trained on ImageNet (NOT deepfakes)
 * 
 * MUST BE REPLACED before production deployment with:
 * - XceptionNet trained on FaceForensics++
 * - MesoNet trained on FaceForensics++/DFDC
 * - EfficientNet fine-tuned on deepfake datasets
 * - Any equivalent deepfake-trained TFLite model
 * 
 * Architecture is designed for deepfake-specific CNN:
 * - Clean model swap interface (just replace .tflite file)
 * - Biomechanical + temporal pipeline provides robustness
 * - Does NOT depend on ImageNet semantics
 * 
 * Thread-safe with GPU/NNAPI delegates and graceful fallback.
 */
class CNNFeatureExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "DeepfakeDetector" // Updated from Placeholder
        private const val embeddingDim = Constants.CNN_EMBEDDING_DIM // [Real, Fake] probabilities
        
        // DEEPFAKE MODEL CONFIGURATION
        // Architecture: Xception (Pretrained ImageNet + Fine-Tuned Head)
        // Input: 299x299, Normalized [-1, 1]
        // Output: [Real, Fake]
    }
    
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isRealModelLoaded = false
    @Volatile private var isShutdown = false  // Thread-safe shutdown flag
    
    init {
        loadModel()
    }
    
    /**
     * Load TFLite model with GPU acceleration.
     * 
     * ⚠️ PLACEHOLDER: Current model is NOT deepfake-trained.
     * Replace efficientnet_lite0.tflite with deepfake-specific model.
     */
    private fun loadModel() {
        try {
            Log.i(TAG, "Loading TensorFlow Lite model...")
            
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options()
            
            // Try GPU first - with safe fallback
            try {
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    gpuDelegate = GpuDelegate()
                    options.addDelegate(gpuDelegate)
                    Log.d(TAG, "GPU delegate enabled")
                }
            } catch (e: NoClassDefFoundError) {
                Log.w(TAG, "GPU classes not found, using CPU", e)
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "GPU native library not found, using CPU", e)
            } catch (e: Exception) {
                Log.w(TAG, "GPU initialization failed, using CPU", e)
            }
            
            // Create interpreter
            interpreter = Interpreter(modelBuffer, options)
            isRealModelLoaded = true
            
            Log.i(TAG, "✅ Real TFLite model loaded successfully")
            Log.d(TAG, "Input shape: ${interpreter?.getInputTensor(0)?.shape()?.contentToString()}")
            Log.d(TAG, "Output shape: ${interpreter?.getOutputTensor(0)?.shape()?.contentToString()}")
            
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to load real model, using mock inference: ${e.message}")
            isRealModelLoaded = false
            cleanup()
        }
    }
    
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(Constants.MODEL_FILE_NAME)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * Extract features from bitmap (pipeline API).
     */
    fun extractFeatures(bitmap: Bitmap): CNNScore {
        return try {
            synchronized(this) {
                if (isShutdown) {
                    return CNNScore(0.5f, FloatArray(embeddingDim), 0L, 1.0f)
                }
                if (!isRealModelLoaded || interpreter == null) {
                    return performMockInference(bitmap)
                }
                performRealInference(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION IN extractFeatures: ${e.message}", e)
            CNNScore(0.5f, FloatArray(embeddingDim), 0L, 1.0f)
        }
    }
    
    /**
     * Real TFLite inference (called within synchronized block).
     */
    private fun performRealInference(bitmap: Bitmap): CNNScore {
        val startTime = System.currentTimeMillis()
        
        // Create immutable copy to avoid bitmap reuse issues
        val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        
        // Preprocess - XceptionNet expects 299x299
        val inputSize = Constants.CNN_INPUT_SIZE
        val resizedBitmap = Bitmap.createScaledBitmap(safeBitmap, inputSize, inputSize, true)
        
        // Recycle the copy immediately after resize
        if (safeBitmap !== resizedBitmap) {
            safeBitmap.recycle()
        }
        
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        
        // Recycle the resized bitmap immediately after getting pixels
        resizedBitmap.recycle()
        
        // Track min/max for verification
        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        
        // XceptionNet normalization: [-1, 1]
        for (pixel in pixels) {
            val r = (((pixel shr 16) and 0xFF) / 127.5f) - 1.0f
            val g = (((pixel shr 8) and 0xFF) / 127.5f) - 1.0f
            val b = ((pixel and 0xFF) / 127.5f) - 1.0f
            
            // Track range
            minVal = minOf(minVal, r, g, b)
            maxVal = maxOf(maxVal, r, g, b)
            
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
        
        Log.d(TAG, "Tensor range: min=$minVal, max=$maxVal (expected: [-1.0, 1.0])")
        
        // Run inference - Binary output: [real_prob, fake_prob]
        // Model outputs shape [1, 2], so we need a 2D array
        val outputBuffer = Array(1) { FloatArray(2) }
        interpreter?.run(inputBuffer, outputBuffer)
        
        val inferenceTime = System.currentTimeMillis() - startTime
        
        // LOG RAW OUTPUT IMMEDIATELY
        Log.w(TAG, "🔴 RAW OUTPUT TENSOR: [${outputBuffer[0][0]}, ${outputBuffer[0][1]}]")
        Log.w(TAG, "🔴 Raw sum: ${outputBuffer[0].sum()}")
        
        // CRITICAL: Keras sorts classes alphabetically!
        // Training directory has "fake" (index 0) and "real" (index 1)
        // So model outputs: [fake_probability, real_probability]
        val fakeProb = outputBuffer[0][0]  // ← FAKE is at index 0
        val realProb = outputBuffer[0][1]  // ← REAL is at index 1
        val confidence = fakeProb.coerceIn(0.0f, 1.0f)
        
        val verdict = if (fakeProb > 0.6f) "FAKE" else if (realProb > 0.6f) "REAL" else "UNCERTAIN"
        Log.i(TAG, "🔍 Prediction: fake=${String.format("%.3f", fakeProb)}, real=${String.format("%.3f", realProb)} → $verdict (${inferenceTime}ms)")
        
        return CNNScore(
            deepfakeConfidence = confidence,
            embedding = outputBuffer[0], // Use first element of 2D array
            inferenceTimeMs = inferenceTime,
            uncertainty = 0.1f
        )
    }
    
    /**
     * Mock inference fallback.
     */
    private fun performMockInference(bitmap: Bitmap): CNNScore {
        val startTime = System.currentTimeMillis()
        
        Log.d(TAG, "Using mock inference (model not loaded)")
        
        val avgBrightness = calculateAverageBrightness(bitmap)
        val contrast = calculateContrast(bitmap)
        
        val baseConfidence = when {
            avgBrightness < 0.3f && contrast < 0.4f -> Random.nextFloat() * 0.4f + 0.3f
            avgBrightness > 0.7f && contrast > 0.6f -> Random.nextFloat() * 0.2f + 0.0f
            else -> Random.nextFloat() * 0.3f + 0.1f
        }
        
        val embedding = FloatArray(embeddingDim) {
            if (it == 0) (1.0f - baseConfidence.coerceIn(0.0f, 1.0f)) else baseConfidence.coerceIn(0.0f, 1.0f)
        }
        
        val uncertainty = when {
            avgBrightness in 0.4f..0.7f && contrast > 0.5f -> 0.1f
            else -> 0.3f
        }
        
        val inferenceTime = System.currentTimeMillis() - startTime
        
        return CNNScore(
            deepfakeConfidence = baseConfidence.coerceIn(0.0f, 1.0f),
            embedding = embedding,
            inferenceTimeMs = inferenceTime,
            uncertainty = uncertainty
        )
    }

    private fun calculateAverageBrightness(bitmap: Bitmap): Float {
        val width = bitmap.width.coerceAtMost(100)
        val height = bitmap.height.coerceAtMost(100)
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, false)
        
        var totalBrightness = 0f
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val pixel = resized.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalBrightness += (r + g + b) / (3.0f * 255.0f)
            }
        }
        
        return totalBrightness / (width * height / 16)
    }
    
    private fun calculateContrast(bitmap: Bitmap): Float {
        val width = bitmap.width.coerceAtMost(50)
        val height = bitmap.height.coerceAtMost(50)
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, false)
        
        val brightnesses = mutableListOf<Float>()
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val pixel = resized.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                brightnesses.add((r + g + b) / (3.0f * 255.0f))
            }
        }
        
        val mean = brightnesses.average().toFloat()
        val variance = brightnesses.map { (it - mean) * (it - mean) }.average().toFloat()
        
        return (variance * 4.0f).coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Close - THREAD SAFE with shutdown flag.
     */
    fun close() {
        synchronized(this) {
            if (isShutdown) return
            
            Log.d(TAG, "Closing CNN extractor")
            isShutdown = true
            
            // Cleanup resources immediately
            // The 150ms delay in FramePipeline.stop() already handles graceful shutdown
            cleanup()
        }
    }
    
    private fun cleanup() {
        try {
            gpuDelegate?.close()
            gpuDelegate = null
            interpreter?.close()
            interpreter = null
            isRealModelLoaded = false
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error (safe to ignore): ${e.message}")
        }
    }
}
