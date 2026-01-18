package com.hiddenlayer.data.sources

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.hiddenlayer.data.models.FrameMetadata
import com.hiddenlayer.data.models.SourceType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Screen capture frame source using MediaProjection.
 * 
 * WHY: Captures real device screen content.
 * Essential for "Screen Share" mode analysis.
 */
class ScreenFrameSource(
    private val context: Context
) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    companion object {
        private const val TAG = "ScreenFrameSource"
        private const val VIRTUAL_DISPLAY_NAME = "HiddenLayerScreenCapture"
    }

    /**
     * Start capturing screen frames.
     * 
     * @param resultCode Result code from the MediaProjection permission activity
     * @param data Intent data from the MediaProjection permission activity
     */
    fun captureScreen(resultCode: Int, data: Intent): Flow<Pair<Bitmap, FrameMetadata>> = callbackFlow {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        
        // Use lower resolution for waiting analysis to save battery/perf, 
        // but high enough for deepfake detection features.
        // XceptionNet needs 299x299. 720p is good.
        val width = 720
        val height = (width * (metrics.heightPixels.toFloat() / metrics.widthPixels.toFloat())).toInt()
        val density = metrics.densityDpi

        startBackgroundThread()

        try {
            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)

            // Must register callback BEFORE createVirtualDisplay for Android 14+
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.d(TAG, "MediaProjection stopped")
                    close()
                }
            }, backgroundHandler)

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                backgroundHandler
            )

            var lastFrameTime = System.currentTimeMillis()
            var frames = 0
            var currentFps = 0f

            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        // FPS Calculation
                        val now = System.currentTimeMillis()
                        frames++
                        if (now - lastFrameTime >= 1000) {
                            currentFps = frames.toFloat()
                            frames = 0
                            lastFrameTime = now
                        }

                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        // Create bitmap
                        // This is heavy, but necessary. 
                        // To optimize: Reuse bitmap if possible, but Flow emits new instances.
                        // Ideally we copy into a reused bitmap.
                        
                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        
                        val actualBitmap = if (rowPadding == 0) {
                            bitmap
                        } else {
                            // Trim padding
                            Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        }
                        
                        image.close()
                        
                        // Metadata
                        val metadata = FrameMetadata(
                            timestamp = System.currentTimeMillis(),
                            sourceType = SourceType.SCREEN_SHARE,
                            resolution = Pair(width, height),
                            fps = currentFps, 
                            compressionEstimate = 0f,
                            frameIndex = System.currentTimeMillis()
                        )
                        
                        trySend(Pair(actualBitmap, metadata))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring image", e)
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen capture", e)
            close(e)
        }

        awaitClose {
            stop()
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("ScreenCaptureThread")
            backgroundThread?.start()
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stop() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            
            imageReader?.close()
            imageReader = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            backgroundThread?.quitSafely()
            try {
                backgroundThread?.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screen capture", e)
        }
    }
}
