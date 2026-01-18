package com.hiddenlayer.presentation.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiddenlayer.domain.models.AnalysisResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that displays a floating overlay when app is in background.
 * Shows real-time analysis results for screen sharing mode.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null
    private val analysisResult = mutableStateOf<AnalysisResult?>(null)
    
    // Pipeline for screen analysis
    private var pipeline: com.hiddenlayer.domain.pipeline.FramePipeline? = null
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private var isAnalysisRunning = false

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "overlay_service"
        
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        
        // Observable for UI when binding isn't used (simple sharing)
        var currentResult: AnalysisResult? = null
            set(value) {
                field = value
                instance?.updateOverlay(value)
            }
        
        private var instance: OverlayService? = null
        
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun startWithProjection(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Must be 'startForegroundService' for MediaProjection
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Overlay service created")
        
        createNotificationChannel()
        // MUST start foreground immediately with correct type for Android 14
        if (Build.VERSION.SDK_INT >= 34) { // Android 14
             startForeground(
                 NOTIFICATION_ID, 
                 createNotification(), 
                 android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
             )
        } else {
             startForeground(NOTIFICATION_ID, createNotification())
        }
        
        if (canDrawOverlays()) {
            showOverlay()
        } else {
            Log.w(TAG, "Cannot draw overlays, requesting permission")
            // Don't stop self immediately if we are doing analysis but no overlay... 
            // but the point IS the overlay.
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        
        if (resultCode != 0 && resultData != null && !isAnalysisRunning) {
            startAnalysis(resultCode, resultData)
        }
        
        return START_NOT_STICKY
    }
    
    private fun startAnalysis(resultCode: Int, data: Intent) {
        Log.i(TAG, "Starting Screen Analysis Pipeline")
        isAnalysisRunning = true
        
        // Needed for lifecycleOwner which FramePipeline expects
        val lifecycleOwner = object : androidx.lifecycle.LifecycleOwner {
            private val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
            init { registry.currentState = Lifecycle.State.RESUMED }
        }
        
        pipeline = com.hiddenlayer.domain.pipeline.FramePipeline(this, lifecycleOwner)
        
        // Launch collection in service scope
        serviceScope.launch {
            try {
                pipeline?.startScreenPipeline(resultCode, data)
                    ?.collect { detailedAnalysis ->
                         currentResult = detailedAnalysis.result // Update static for UI observers
                         updateOverlay(detailedAnalysis.result)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in analysis loop", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isAnalysisRunning = false
        pipeline?.stop()
        serviceScope.cancel()
        hideOverlay()
        Log.d(TAG, "Overlay service destroyed")
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun showOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 16
                y = 100
            }

            overlayView = createOverlayView()
            windowManager?.addView(overlayView, params)
            
            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    private fun hideOverlay() {
        try {
            overlayView?.let {
                windowManager?.removeView(it)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay", e)
        }
    }

    private fun createOverlayView(): android.view.View {
        return ComposeView(this).apply {
            // 1. Create Lifecycle Owner & Registry (Start in INITIALIZED state)
            val myLifecycleOwner = object : androidx.lifecycle.LifecycleOwner {
                val registry = LifecycleRegistry(this)
                override val lifecycle: Lifecycle get() = registry
            }
            
            // 2. Create SavedStateRegistryOwner
            val mySavedStateRegistryOwner = object : SavedStateRegistryOwner {
                private val controller = SavedStateRegistryController.create(this)
                override val savedStateRegistry = controller.savedStateRegistry
                override val lifecycle = myLifecycleOwner.lifecycle
                
                fun init() {
                    // Restore BEFORE moving lifecycle to CREATED
                    controller.performRestore(null)
                }
            }
            
            // 3. Perform Restore (while Lifecycle is still INITIALIZED)
            mySavedStateRegistryOwner.init()
            
            // 4. Move Lifecycle to RESUMED
            myLifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            myLifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            myLifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            // 5. Set ViewTree Owners
            this.setViewTreeLifecycleOwner(myLifecycleOwner)
            this.setViewTreeSavedStateRegistryOwner(mySavedStateRegistryOwner)

            val viewModelStore = ViewModelStore()
            val viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore
                    get() = viewModelStore
            }
            this.setViewTreeViewModelStoreOwner(viewModelStoreOwner)

            setContent {
                OverlayContent(analysisResult.value)
            }
        }
    }

    private fun updateOverlay(result: AnalysisResult?) {
        analysisResult.value = result
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Analysis Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows analysis results overlay"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Deepfake Analysis Active")
            .setContentText("Analyzing screen content...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .build()
    }
}

@Composable
fun OverlayContent(result: AnalysisResult?) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .background(
                color = Color(0xE0000000), // Semi-transparent black
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "HiddenLayer",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (result != null) {
                Text(
                    text = when (result) {
                        is AnalysisResult.Authentic -> "✓ Authentic"
                        is AnalysisResult.LikelyDeepfake -> "⚠ Likely Fake"
                        is AnalysisResult.Suspicious -> "? Suspicious"
                        is AnalysisResult.Inconclusive -> "- Analyzing..."
                    },
                    color = when (result) {
                        is AnalysisResult.Authentic -> Color(0xFF4CAF50)
                        is AnalysisResult.LikelyDeepfake -> Color(0xFFF44336)
                        is AnalysisResult.Suspicious -> Color(0xFFFF9800)
                        is AnalysisResult.Inconclusive -> Color(0xFF9E9E9E)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                val confidence = when (result) {
                    is AnalysisResult.Authentic -> result.confidence
                    is AnalysisResult.LikelyDeepfake -> result.confidence
                    is AnalysisResult.Suspicious -> result.confidence
                    is AnalysisResult.Inconclusive -> 0f
                }
                
                if (confidence > 0f) {
                    Text(
                        text = "${(confidence * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            } else {
                Text(
                    text = "Initializing...",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
