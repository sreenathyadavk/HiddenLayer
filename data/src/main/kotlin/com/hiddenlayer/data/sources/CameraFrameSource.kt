package com.hiddenlayer.data.sources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.hiddenlayer.data.models.FrameMetadata
import com.hiddenlayer.data.models.SourceType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * CameraX frame source for live camera feed.
 * 
 * WHY: Primary input mode for real-time deepfake detection.
 * CameraX provides modern, lifecycle-aware camera API.
 * 
 * Frame processing:
 * 1. Configure ImageAnalysis use case (YUV_420_888)
 * 2. Convert YUV to RGB Bitmap
 * 3. Attach metadata (timestamp, FPS, resolution)
 * 4. Emit to Flow for pipeline consumption
 */
class CameraFrameSource(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var lastFrameTime = 0L
    private val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var currentFPS = 30.0f
    
    /**
     * Capture frames from camera as Flow.
     * Emits Pair<Bitmap, FrameMetadata> for each frame.
     */
    fun captureFrames(
        surfaceProvider: Preview.SurfaceProvider? = null,
        cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    ): Flow<Pair<Bitmap, FrameMetadata>> = callbackFlow {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Preview use case
            val preview = Preview.Builder()
                .build()
            
            // Attach surface provider if available (this connects to UI)
            if (surfaceProvider != null) {
                preview.setSurfaceProvider(surfaceProvider)
            }
            
            // Image analysis use case (our frame source)
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1280, 720))  // 720p balance
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        analysisExecutor
                    ) { imageProxy ->
                        // Calculate FPS
                        val currentTime = System.currentTimeMillis()
                        if (lastFrameTime > 0) {
                            val deltaTime = (currentTime - lastFrameTime) / 1000.0f
                            currentFPS = if (deltaTime > 0) (1.0f / deltaTime) else 30.0f
                        }
                        lastFrameTime = currentTime
                        
                        // Convert to Bitmap (Heavy operation!)
                        val bitmap = imageProxyToBitmap(imageProxy)
                        
                        // Create metadata
                        val metadata = FrameMetadata(
                            timestamp = currentTime,
                            sourceType = SourceType.CAMERA,
                            resolution = Pair(imageProxy.width, imageProxy.height),
                            fps = currentFPS.coerceIn(1.0f, 60.0f),
                            compressionEstimate = 1.0f,  // Camera = uncompressed
                            frameIndex = currentTime  // Use timestamp as index
                        )
                        
                        // Emit to Flow - recycle if dropped to save memory
                        val result = trySend(Pair(bitmap, metadata))
                        if (result.isFailure) {
                            bitmap.recycle()
                        }
                        
                        imageProxy.close()
                    }
                }
            
            try {
                // Unbind all first to avoid conflicts
                cameraProvider.unbindAll()
                
                // Bind use cases to lifecycle
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                close(e)
            }
        }, ContextCompat.getMainExecutor(context))
        
        awaitClose {
            imageAnalyzer?.clearAnalyzer()
            camera = null
        }
    }

    /**
     * Convert ImageProxy (YUV_420_888) to RGB Bitmap.
     * 
     * WHY: MediaPipe and TFLite need RGB format.
     * YUV conversion is expensive but necessary.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val yBuffer = imageProxy.planes[0].buffer // Y
        val uBuffer = imageProxy.planes[1].buffer // U
        val vBuffer = imageProxy.planes[2].buffer // V
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )
        
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
            100,
            out
        )
        
        val imageBytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        // Rotate if needed (front camera is mirrored)
        return rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
    }
    
    /**
     * Rotate bitmap based on camera orientation.
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }
        
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
    
    /**
     * Stop camera capture and shutdown executor.
     */
    fun stop() {
        imageAnalyzer?.clearAnalyzer()
        camera = null
        // Do not shutdown executor if we plan to reuse this instance, 
        // but FramePipeline creates a new source or reuses it?
        // FramePipeline is created in a Composable with remember, so it is reused.
        // We generally shouldn't shut down the executor here if we want to restart.
        // But if we want to really stop:
        // analysisExecutor.shutdown() 
        // Let's keep it alive for now as the instance handles start/stop/start.
    }
}
