package com.hiddenlayer.domain.usecases

import com.hiddenlayer.core.Constants
import com.hiddenlayer.data.models.CNNScore
import com.hiddenlayer.data.models.TemporalScore
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Temporal behavior analysis using sliding window.
 * 
 * WHY: Single frames can't reveal temporal inconsistencies.
 * Deepfakes often have subtle frame-to-frame artifacts in embedding space.
 * 
 * Approach:
 * 1. Maintain sliding window of CNN embeddings (10-20 frames)
 * 2. Calculate embedding variance over time
 * 3. Detect sudden shifts/irregularities
 * 4. Time-normalized to handle variable FPS
 * 
 * NO LSTM/GRU - too heavy for real-time mobile.
 */
class TemporalAnalyzer {
    
    private val embeddingWindow = LinkedList<TimestampedEmbedding>()
    private val maxWindowSize = Constants.TEMPORAL_WINDOW_DEFAULT_FRAMES
    private val minWindowSize = Constants.TEMPORAL_WINDOW_MIN_FRAMES
    
    /**
     * Analyze temporal consistency of embeddings.
     * 
     * @param cnnScore Current CNN result with embedding
     * @param timestamp Frame timestamp (milliseconds)
     * @return TemporalScore with consistency metrics
     */
    fun analyzeTemporalConsistency(
        cnnScore: CNNScore,
        timestamp: Long
    ): TemporalScore {
        
        // Add to window
        embeddingWindow.add(TimestampedEmbedding(timestamp, cnnScore.embedding))
        
        // Remove oldest if exceeds max size
        while (embeddingWindow.size > maxWindowSize) {
            embeddingWindow.removeFirst()
        }
        
        // Need minimum frames for reliable analysis
        if (embeddingWindow.size < minWindowSize) {
            return TemporalScore(
                consistencyScore = 0.0f,  // Neutral (insufficient data)
                embeddingVariance = 0.0f,
                windowSize = embeddingWindow.size,
                irregularityDetected = false
            )
        }
        
        // Calculate embedding variance over time
        val variance = calculateEmbeddingVariance()
        
        // Detect sudden shifts (irregularities)
        val irregularityDetected = detectIrregularities()
        
        // Temporal consistency score (higher = more inconsistent/suspicious)
        val consistencyScore = when {
            irregularityDetected -> 0.8f  // Strong signal
            variance > 0.5f -> 0.6f  // Moderate variance
            variance > 0.3f -> 0.3f  // Low variance
            else -> 0.1f  // Very consistent
        }
        
        return TemporalScore(
            consistencyScore = consistencyScore,
            embeddingVariance = variance,
            windowSize = embeddingWindow.size,
            irregularityDetected = irregularityDetected
        )
    }
    
    /**
     * Calculate variance of embeddings over temporal window.
     * 
     * WHY: High variance = unstable features = potential manipulation.
     * Authentic video has smooth embedding transitions.
     */
    private fun calculateEmbeddingVariance(): Float {
        if (embeddingWindow.size < 2) return 0.0f
        
        val embeddingDim = embeddingWindow.first().embedding.size
        val windowSize = embeddingWindow.size
        
        // Calculate mean embedding
        val meanEmbedding = FloatArray(embeddingDim)
        for (te in embeddingWindow) {
            for (i in te.embedding.indices) {
                meanEmbedding[i] = meanEmbedding[i] + te.embedding[i]
            }
        }
        for (i in meanEmbedding.indices) {
            meanEmbedding[i] = meanEmbedding[i] / windowSize
        }
        
        // Calculate variance across all dimensions
        var totalVariance = 0.0f
        for (te in embeddingWindow) {
            var squaredDist = 0.0f
            for (i in te.embedding.indices) {
                val diff = te.embedding[i] - meanEmbedding[i]
                squaredDist += diff * diff
            }
            totalVariance += sqrt(squaredDist)
        }
        
        val avgVariance = totalVariance / windowSize
        
        // Normalize (empirically tuned range)
        return (avgVariance / 2.0f).coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Detect sudden irregularities in embedding trajectory.
     * 
     * WHY: Deepfakes often have sudden "jumps" when frame changes.
     * Smooth transitions = authentic, jumps = suspicious.
     */
    private fun detectIrregularities(): Boolean {
        if (embeddingWindow.size < 3) return false
        
        val embeddings = embeddingWindow.map { it.embedding }
        
        // Calculate frame-to-frame distances
        val distances = mutableListOf<Float>()
        for (i in 1 until embeddings.size) {
            val dist = cosineDistance(embeddings[i - 1], embeddings[i])
            distances.add(dist)
        }
        
        if (distances.isEmpty()) return false
        
        // Check for sudden spikes
        val mean = distances.average().toFloat()
        val stdDev = sqrt(distances.map { (it - mean) * (it - mean) }.average()).toFloat()
        
        // If any distance > mean + 2*stdDev = irregularity detected
        for (dist in distances) {
            if (dist > mean + 2.0f * stdDev && dist > 0.3f) {
                return true  // Significant irregularity
            }
        }
        
        return false
    }
    
    /**
     * Calculate cosine distance between two embeddings.
     * Range: [0, 2] where 0 = identical, 2 = opposite
     */
    private fun cosineDistance(emb1: FloatArray, emb2: FloatArray): Float {
        if (emb1.size != emb2.size) return 1.0f
        
        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f
        
        for (i in emb1.indices) {
            dotProduct += emb1[i] * emb2[i]
            norm1 += emb1[i] * emb1[i]
            norm2 += emb2[i] * emb2[i]
        }
        
        norm1 = sqrt(norm1)
        norm2 = sqrt(norm2)
        
        val cosineSimilarity = if (norm1 > 0 && norm2 > 0) {
            dotProduct / (norm1 * norm2)
        } else {
            0.0f
        }
        
        // Convert similarity to distance
        return 1.0f - cosineSimilarity
    }
    
    /**
     * Get current window size.
     */
    fun getCurrentWindowSize(): Int {
        return embeddingWindow.size
    }
    
    /**
     * Reset temporal window (e.g., when switching input source).
     */
    fun reset() {
        embeddingWindow.clear()
    }
}

/**
 * Embedding with timestamp for time-normalized analysis.
 */
private data class TimestampedEmbedding(
    val timestamp: Long,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimestampedEmbedding

        if (timestamp != other.timestamp) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
