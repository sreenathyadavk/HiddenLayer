package com.hiddenlayer

import android.app.Application
import android.util.Log
import com.hiddenlayer.core.utils.PrivacyGuard

/**
 * Application class for app-wide initialization.
 * 
 * WHY: Sets up global config, logs privacy audit on startup.
 * Helps debugging device capabilities.
 */
class HiddenLayerApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        Log.i("HiddenLayer", "HiddenLayer starting...")
        Log.d("HiddenLayer", "Privacy audit: ${PrivacyGuard.toString()}")
        
        // Log device capabilities for debugging
        logDeviceCapabilities()
    }
    
    private fun logDeviceCapabilities() {
        Log.d("HiddenLayer", "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        Log.d("HiddenLayer", "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        Log.d("HiddenLayer", "RAM: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB")
        
        // Check hardware acceleration
        val hasGPU = packageManager.hasSystemFeature("android.hardware.opengles.aep")
        Log.d("HiddenLayer", "GPU acceleration: $hasGPU")
    }
}
