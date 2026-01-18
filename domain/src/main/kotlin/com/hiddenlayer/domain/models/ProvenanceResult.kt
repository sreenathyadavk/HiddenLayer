package com.hiddenlayer.domain.models

/**
 * Result from provenance detection analysis
 */
data class ProvenanceResult(
    val isAIGenerated: Boolean,
    val confidence: Float, // 0.0 to 1.0
    val detectionMethod: String,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        fun notDetected() = ProvenanceResult(
            isAIGenerated = false,
            confidence = 0.0f,
            detectionMethod = "No AI signatures found",
            metadata = emptyMap()
        )
        
        fun detected(method: String, metadata: Map<String, String> = emptyMap()) = ProvenanceResult(
            isAIGenerated = true,
            confidence = 0.95f,
            detectionMethod = method,
            metadata = metadata
        )
    }
}
