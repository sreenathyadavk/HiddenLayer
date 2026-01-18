package com.hiddenlayer.data.models

/**
 * Source type for frame ingestion.
 * 
 * WHY: Different sources have different quality characteristics.
 * Camera is highest quality, screen share has compression, media files vary.
 */
enum class SourceType {
    CAMERA,        // Live CameraX feed (highest quality)
    SCREEN_SHARE,  // MediaProjection API (compressed, variable FPS)
    MEDIA_FILE     // Video/image file (unknown quality)
}

/**
 * Frame metadata attached to every ingested frame.
 * 
 * WHY: Signal quality scoring requires context beyond pixels.
 * Timestamp enables temporal ordering, metadata drives adaptive logic.
 */
data class FrameMetadata(
    val timestamp: Long,                    // System time in milliseconds
    val sourceType: SourceType,
    val resolution: Pair<Int, Int>,         // (width, height)
    val fps: Float,                         // Effective FPS at capture time
    val compressionEstimate: Float = 1.0f,  // 0-1, where 1 = uncompressed
    val frameIndex: Long = 0L               // Sequential frame number
)

/**
 * Signal quality assessment for each frame.
 * 
 * WHY: Low-quality signals reduce analysis trustworthiness.
 * This score weights all downstream analysis results.
 */
data class SignalQuality(
    val overallScore: Float,            // 0-1 composite quality
    val fpsScore: Float,                // FPS adequacy (0-1)
    val resolutionScore: Float,         // Resolution adequacy (0-1)
    val motionSmoothnessScore: Float,   // Optical flow coherence (0-1)
    val compressionScore: Float,        // Artifact detection (0-1)
    val reason: String = ""             // Human-readable explanation
) {
    companion object {
        fun inadequate(reason: String) = SignalQuality(
            overallScore = 0.0f,
            fpsScore = 0.0f,
            resolutionScore = 0.0f,
            motionSmoothnessScore = 0.0f,
            compressionScore = 0.0f,
            reason = reason
        )
    }
}

/**
 * Individual scoring components from pipeline stages.
 */
data class LandmarkScore(
    val value: Float,                   // 0-1 consistency score
    val eyeBlinkScore: Float,
    val mouthMotionScore: Float,
    val headPoseScore: Float,
    val confidence: Float = 1.0f
)

data class MotionScore(
    val value: Float,                   // 0-1 stability score
    val opticalFlowCoherence: Float,
    val boundaryConsistency: Float,
    val confidence: Float = 1.0f
)

data class CNNScore(
    val deepfakeConfidence: Float,      // 0-1 (higher = more likely deepfake)
    val embedding: FloatArray,          // Feature vector for temporal analysis
    val inferenceTimeMs: Long,
    val uncertainty: Float = 0.0f       // Model uncertainty (0-1)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CNNScore

        if (deepfakeConfidence != other.deepfakeConfidence) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = deepfakeConfidence.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

data class TemporalScore(
    val consistencyScore: Float,        // 0-1 (lower = more consistent/authentic)
    val embeddingVariance: Float,
    val windowSize: Int,
    val irregularityDetected: Boolean = false
)
