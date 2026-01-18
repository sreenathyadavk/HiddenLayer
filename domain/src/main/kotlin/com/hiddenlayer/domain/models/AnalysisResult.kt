package com.hiddenlayer.domain.models

/**
  * Final analysis result with uncertainty awareness - SIMPLIFIED FOR BUILD
 */
sealed class AnalysisResult {
    
    data class Authentic(val confidence: Float) : AnalysisResult()
    
    data class Suspicious(
        val confidence: Float,
        val signals: List<String>
    ) : AnalysisResult()
    
    data class Inconclusive(val reason: String) : AnalysisResult()
    
    data class LikelyDeepfake(
        val confidence: Float,
        val signals: List<String>
    ) : AnalysisResult()
    
    fun toDisplayMessage(): String = when (this) {
        is Authentic -> "Content appears authentic"
        is Suspicious -> "Unusual patterns detected (${signals.joinToString(", ")})"
        is Inconclusive -> "Verification inconclusive (model confidence insufficient)"
        is LikelyDeepfake -> "Potential manipulation detected (${signals.joinToString(", ")})"
    }
}

data class DetailedAnalysis(
    val result: AnalysisResult,
    val signalQuality: Float,
    val landmarkConsistency: Float,
    val motionStability: Float,
    val cnnDeepfakeScore: Float,
    val temporalConsistency: Float,
    val processingTimeMs: Long,
    val framesAnalyzed: Int
)
