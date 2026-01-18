package com.hiddenlayer.domain.models

import com.hiddenlayer.data.models.CNNScore

/**
 * Final detection verdict combining all signals
 */
data class FinalVerdict(
    val isFake: Boolean,
    val confidence: Float, // 0.0 to 1.0
    val reason: String,
    val threatLevel: ThreatLevel,
    val signals: DetectionSignals
)

enum class ThreatLevel {
    NONE,      // Authentic content
    LOW,       // Minor concerns
    MEDIUM,    // Likely manipulated
    HIGH,      // Confirmed fake/AI-generated
    CRITICAL   // Severe manipulation
}

data class DetectionSignals(
    val provenance: ProvenanceResult,
    val artifacts: ArtifactScore,
    val cnn: CNNScore
)
