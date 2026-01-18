package com.hiddenlayer.domain.models

/**
 * Result from artifact detection analysis
 */
data class ArtifactScore(
    val anomalyLevel: Float, // 0.0 to 1.0
    val confidence: Float,
    val details: Map<String, Float> = emptyMap()
) {
    companion object {
        fun clean() = ArtifactScore(
            anomalyLevel = 0.0f,
            confidence = 0.6f,
            details = emptyMap()
        )
    }
}
