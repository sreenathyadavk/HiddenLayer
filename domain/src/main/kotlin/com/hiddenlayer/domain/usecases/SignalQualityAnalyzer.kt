package com.hiddenlayer.domain.usecases

import com.hiddenlayer.core.Constants
import com.hiddenlayer.data.models.FrameMetadata
import com.hiddenlayer.data.models.SignalQuality
import com.hiddenlayer.data.models.SourceType
import kotlin.math.min

/**
 * Analyzes frame signal quality to weight downstream analysis.
 * 
 * WHY: Low-quality inputs produce unreliable results.
 * This gate prevents garbage-in-garbage-out by quantifying input quality.
 * 
 * Signal quality factors:
 * 1. FPS adequacy - Too low = temporal analysis unreliable
 * 2. Resolution - Too small = CNN features degraded
 * 3. Motion smoothness - Jittery = optical flow fails
 * 4. Compression - Heavy = artifacts mask deepfake signals
 * 
 * The overall score weights ALL downstream analysis results.
 */
class SignalQualityAnalyzer {
    
    private var previousFrameTime: Long = 0L
    private val fpsHistory = mutableListOf<Float>()
    private val maxFpsHistorySize = 10
    
    /**
     * Calculate comprehensive signal quality score.
     * 
     * @param metadata Frame metadata
     * @param motionSmoothness Optional optical flow coherence (0-1)
     * @return SignalQuality assessment
     */
    fun calculateQuality(
        metadata: FrameMetadata,
        motionSmoothness: Float? = null
    ): SignalQuality {
        
        // 1. FPS Score: Penalize below minimum threshold
        val fpsScore = calculateFPSScore(metadata.fps)
        
        // 2. Resolution Score: Penalize below minimum for face analysis
        val resolutionScore = calculateResolutionScore(metadata.resolution)
        
        // 3. Motion Smoothness: If available from optical flow
        val motionScore = motionSmoothness ?: 1.0f
        
        // 4. Compression Score: Reduce trust for compressed sources
        val compressionScore = when (metadata.sourceType) {
            SourceType.CAMERA -> 1.0f  // Uncompressed
            SourceType.SCREEN_SHARE -> 0.7f  // Moderate compression
            SourceType.MEDIA_FILE -> metadata.compressionEstimate
        }
        
        // Weighted composite score
        // WHY these weights: FPS and resolution are critical for face analysis,
        // compression matters more than motion smoothness for deepfake detection
        val overallScore = (
            fpsScore * 0.3f +
            resolutionScore * 0.3f +
            motionScore * 0.2f +
            compressionScore * 0.2f
        )
        
        // Generate human-readable reason if quality is poor
        val reason = when {
            overallScore < 0.4f -> buildQualityIssuesReason(
                fpsScore, resolutionScore, motionScore, compressionScore
            )
            else -> ""
        }
        
        return SignalQuality(
            overallScore = overallScore,
            fpsScore = fpsScore,
            resolutionScore = resolutionScore,
            motionSmoothnessScore = motionScore,
            compressionScore = compressionScore,
            reason = reason
        )
    }
    
    /**
     * FPS adequacy scoring.
     * Full score at TARGET_FPS_OPTIMAL, linear penalty below MIN_ACCEPTABLE_FPS.
     */
    private fun calculateFPSScore(fps: Float): Float {
        return when {
            fps >= Constants.TARGET_FPS_OPTIMAL -> 1.0f
            fps >= Constants.MIN_ACCEPTABLE_FPS -> {
                (fps - Constants.MIN_ACCEPTABLE_FPS) / 
                (Constants.TARGET_FPS_OPTIMAL - Constants.MIN_ACCEPTABLE_FPS)
            }
            else -> 0.0f  // Too low for reliable analysis
        }
    }
    
    /**
     * Resolution adequacy scoring.
     * Full score above minimum, linear penalty below.
     */
    private fun calculateResolutionScore(resolution: Pair<Int, Int>): Float {
        val (width, height) = resolution
        val minDim = min(width, height)
        
        return when {
            minDim >= Constants.MIN_RESOLUTION_WIDTH -> 1.0f
            minDim >= 320 -> {
                (minDim - 320f) / (Constants.MIN_RESOLUTION_WIDTH - 320f)
            }
            else -> 0.0f  // Too small for face landmarks
        }
    }
    
    /**
     * Build human-readable explanation of quality issues.
     */
    private fun buildQualityIssuesReason(
        fpsScore: Float,
        resolutionScore: Float,
        motionScore: Float,
        compressionScore: Float
    ): String {
        val issues = mutableListOf<String>()
        
        if (fpsScore < 0.5f) issues.add("Low frame rate")
        if (resolutionScore < 0.5f) issues.add("Low resolution")
        if (motionScore < 0.5f) issues.add("Unstable motion")
        if (compressionScore < 0.5f) issues.add("High compression")
        
        return if (issues.isEmpty()) {
            "Marginal signal quality"
        } else {
            issues.joinToString(", ")
        }
    }
    
    /**
     * Track FPS over time for smoothing.
     * Helps detect FPS drops that impact temporal analysis.
     */
    fun updateFPSHistory(fps: Float) {
        fpsHistory.add(fps)
        if (fpsHistory.size > maxFpsHistorySize) {
            fpsHistory.removeAt(0)
        }
    }
    
    /**
     * Get average FPS over recent history.
     */
    fun getAverageFPS(): Float {
        return if (fpsHistory.isNotEmpty()) {
            fpsHistory.average().toFloat()
        } else {
            Constants.TARGET_FPS_OPTIMAL.toFloat()
        }
    }
}
