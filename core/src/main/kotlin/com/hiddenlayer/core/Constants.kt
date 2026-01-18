package com.hiddenlayer.core

/**
 * Central configuration for HiddenLayer pipeline.
 * 
 * WHY: Single source of truth for performance tuning.
 * Production-grade apps need easy parameter adjustment without code changes.
 */
object Constants {
    
    // Frame Ingestion
    const val TARGET_FPS_MIN = 15
    const val TARGET_FPS_MAX = 30
    const val TARGET_FPS_OPTIMAL = 24
    
    // Signal Quality Thresholds
    const val MIN_RESOLUTION_WIDTH = 480
    const val MIN_RESOLUTION_HEIGHT = 640
    const val MIN_ACCEPTABLE_FPS = 12.0f
    const val COMPRESSION_QUALITY_THRESHOLD = 0.7f
    
    // CNN Adaptive Triggers
    const val CNN_LANDMARK_VARIANCE_THRESHOLD = 0.15f
    const val CNN_MOTION_ENTROPY_THRESHOLD = 0.2f
    const val CNN_PERIODIC_INTERVAL_MS = 1000L  // Every 1 second max
    const val CNN_UNCERTAINTY_INCREASE_THRESHOLD = 0.1f
    
    // Temporal Window
    const val TEMPORAL_WINDOW_MIN_FRAMES = 10
    const val TEMPORAL_WINDOW_MAX_FRAMES = 20
    const val TEMPORAL_WINDOW_DEFAULT_FRAMES = 15
    
    // Ensemble Decision Thresholds
    const val AUTHENTIC_THRESHOLD = 0.3f
    const val SUSPICIOUS_THRESHOLD_LOW = 0.3f
    const val SUSPICIOUS_THRESHOLD_HIGH = 0.6f
    const val DEEPFAKE_THRESHOLD = 0.6f
    const val CONFIDENCE_MIN_FOR_DEEPFAKE = 0.75f
    const val MAX_CONFIDENCE_CAP = 0.95f  // Never show >95% confidence
    const val CONFLICT_DISAGREEMENT_THRESHOLD = 0.3f
    
    // Thermal Management
    const val THERMAL_NORMAL_MAX_TEMP = 38.0f  // Celsius
    const val THERMAL_MODERATE_MAX_TEMP = 42.0f
    const val THERMAL_HIGH_MAX_TEMP = 45.0f
    const val THERMAL_CHECK_INTERVAL_MS = 5000L
    
    // Performance Limits
    const val MAX_FRAME_BUFFER_SIZE = 100
    const val MAX_INFERENCE_LATENCY_MS = 150L
    const val TARGET_PIPELINE_LATENCY_MS = 100L
    
    // Model Configuration
    const val CNN_INPUT_SIZE = 299  // XceptionNet expects 299x299
    const val CNN_EMBEDDING_DIM = 2  // Xception Output [Real, Fake]
    const val MODEL_FILE_NAME = "deepfake_net.tflite"
    
    // MediaPipe Configuration
    const val MEDIAPIPE_MAX_FACES = 1  // Single face tracking
    const val MEDIAPIPE_MIN_DETECTION_CONFIDENCE = 0.5f
    const val MEDIAPIPE_MIN_TRACKING_CONFIDENCE = 0.5f
    
    // Optical Flow (OpenCV)
    const val OPTICAL_FLOW_PYR_SCALE = 0.5
    const val OPTICAL_FLOW_LEVELS = 3
    const val OPTICAL_FLOW_WINSIZE = 15
    const val OPTICAL_FLOW_ITERATIONS = 3
    const val OPTICAL_FLOW_POLY_N = 5
    const val OPTICAL_FLOW_POLY_SIGMA = 1.2
    
    // Debug Mode
    const val ENABLE_DEBUG_OVERLAY = true
    const val ENABLE_PERFORMANCE_LOGGING = true
    const val LOG_FRAME_METRICS = false  // Too verbose for production
}
