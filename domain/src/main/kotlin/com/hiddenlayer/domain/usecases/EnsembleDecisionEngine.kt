package com.hiddenlayer.domain.usecases

import com.hiddenlayer.core.Constants
import com.hiddenlayer.data.models.LandmarkScore
import com.hiddenlayer.data.models.MotionScore
import com.hiddenlayer.data.models.CNNScore
import com.hiddenlayer.data.models.TemporalScore
import com.hiddenlayer.data.models.SignalQuality
import com.hiddenlayer.domain.models.AnalysisResult

/**
 * SIMPLIFIED Ensemble decision engine.
 * Fixed for production build.
 */
class EnsembleDecisionEngine {
    
    fun makeDecision(
        signalQuality: SignalQuality,
        landmarkScore: LandmarkScore?,
        motionScore: MotionScore?,
        cnnScore: CNNScore?,
        temporalScore: TemporalScore?
    ): AnalysisResult {
        
        // Gate 1: If signal quality too low
        if (signalQuality.overallScore < 0.3f) {
            return AnalysisResult.Inconclusive("Signal quality too low")
        }
        
        // Gate 2: If insufficient data
        if (landmarkScore == null && cnnScore == null) {
            return AnalysisResult.Inconclusive("Insufficient analysis data")
        }
        
        // Calculate simple weighted score
        var totalWeight = 0.0f
        var weightedSum = 0.0f
        
        landmarkScore?.let {
            val weight = 0.25f
            weightedSum += (1.0f - it.value) * weight
            totalWeight += weight
        }
        
        motionScore?.let {
            val weight = 0.20f
            weightedSum += (1.0f - it.value) * weight
            totalWeight += weight
        }
        
        cnnScore?.let {
            val weight = 0.35f
            weightedSum += it.deepfakeConfidence * weight
            totalWeight += weight
        }
        
        temporalScore?.let {
            val weight = 0.20f
            weightedSum += it.consistencyScore * weight
            totalWeight += weight
        }
        
        
        val fusedScore = if (totalWeight > 0) weightedSum / totalWeight else 0.0f
        
        // Map fused score to verdict using Constants
        return when {
            fusedScore < Constants.AUTHENTIC_THRESHOLD -> AnalysisResult.Authentic(
                confidence = (1.0f - fusedScore).coerceIn(0.0f, 0.95f)
            )
            fusedScore >= Constants.CONFIDENCE_MIN_FOR_DEEPFAKE -> AnalysisResult.LikelyDeepfake(
                confidence = fusedScore.coerceIn(0.0f, 0.95f),
                signals = listOf("Deep learning detection")
            )
            else -> AnalysisResult.Suspicious(
                confidence = fusedScore,
                signals = listOf("Anomaly detected")
            )
        }
    }
}
