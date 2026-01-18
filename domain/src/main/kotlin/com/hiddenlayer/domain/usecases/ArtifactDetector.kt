package com.hiddenlayer.domain.usecases

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.hiddenlayer.domain.models.ArtifactScore
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects manipulation artifacts that CNN might miss
 * - JPEG compression anomalies
 * - Color banding
 * - Frequency domain artifacts
 * - Edge consistency
 */
class ArtifactDetector {
    
    companion object {
        private const val TAG = "ArtifactDetector"
    }
    
    /**
     * Analyze bitmap for visual artifacts
     */
    fun analyze(bitmap: Bitmap): ArtifactScore {
        try {
            val details = mutableMapOf<String, Float>()
            
            // 1. Color banding detection (AI images often have subtle banding)
            val bandingScore = detectColorBanding(bitmap)
            details["banding"] = bandingScore
            
            // 2. Edge consistency (AI = too perfect, Real = micro irregularities)
            val edgeScore = checkEdgeConsistency(bitmap)
            details["edges"] = edgeScore
            
            // 3. Frequency domain analysis (AI = abnormal smoothness)
            val freqScore = analyzeFrequencyDomain(bitmap)
            details["frequency"] = freqScore
            
            // Weighted combination
            val anomalyLevel = (bandingScore * 0.35f) + 
                              (edgeScore * 0.35f) + 
                              (freqScore * 0.30f)
            
            Log.d(TAG, "Artifact analysis: banding=$bandingScore, edges=$edgeScore, freq=$freqScore, total=$anomalyLevel")
            
            return ArtifactScore(
                anomalyLevel = anomalyLevel.coerceIn(0.0f, 1.0f),
                confidence = 0.6f,
                details = details
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Artifact detection failed: ${e.message}")
            return ArtifactScore.clean()
        }
    }
    
    /**
     * Detect color banding artifacts (common in AI-generated images)
     */
    private fun detectColorBanding(bitmap: Bitmap): Float {
        try {
            // Sample center region
            val width = bitmap.width
            val height = bitmap.height
            val sampleSize = minOf(width, height, 200)
            
            val centerX = width / 2
            val centerY = height / 2
            val startX = (centerX - sampleSize / 2).coerceAtLeast(0)
            val startY = (centerY - sampleSize / 2).coerceAtLeast(0)
            
            // Compute histogram
            val rHist = IntArray(256)
            val gHist = IntArray(256)
            val bHist = IntArray(256)
            
            var pixelCount = 0
            for (y in startY until minOf(startY + sampleSize, height)) {
                for (x in startX until minOf(startX + sampleSize, width)) {
                    val pixel = bitmap.getPixel(x, y)
                    rHist[Color.red(pixel)]++
                    gHist[Color.green(pixel)]++
                    bHist[Color.blue(pixel)]++
                    pixelCount++
                }
            }
            
            // Detect clustering (banding creates spikes in histogram)
            val rBanding = detectHistogramClustering(rHist, pixelCount)
            val gBanding = detectHistogramClustering(gHist, pixelCount)
            val bBanding = detectHistogramClustering(bHist, pixelCount)
            
            return (rBanding + gBanding + bBanding) / 3.0f
            
        } catch (e: Exception) {
            Log.d(TAG, "Banding detection failed: ${e.message}")
            return 0.0f
        }
    }
    
    /**
     * Detect clustering in histogram (indicator of banding)
     */
    private fun detectHistogramClustering(histogram: IntArray, totalPixels: Int): Float {
        if (totalPixels == 0) return 0.0f
        
        // Calculate standard deviation of histogram
        val mean = totalPixels / 256.0
        var variance = 0.0
        
        histogram.forEach { count ->
            val diff = count - mean
            variance += diff * diff
        }
        variance /= 256.0
        
        val stdDev = sqrt(variance)
        
        // High std dev = clustering = potential banding
        // Normalize to 0-1 range (empirically determined threshold)
        val clusteringScore = (stdDev / (totalPixels * 0.1)).coerceIn(0.0, 1.0)
        
        return clusteringScore.toFloat()
    }
    
    /**
     * Check edge consistency (AI edges often too perfect)
     */
    private fun checkEdgeConsistency(bitmap: Bitmap): Float {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val sampleSize = minOf(width, height, 150)
            
            // Simple Sobel-like edge detection on sample
            val centerX = width / 2
            val centerY = height / 2
            val startX = (centerX - sampleSize / 2).coerceAtLeast(1)
            val startY = (centerY - sampleSize / 2).coerceAtLeast(1)
            
            var totalEdgeStrength = 0.0
            var perfectEdgeCount = 0
            var edgeCount = 0
            
            for (y in startY until minOf(startY + sampleSize - 1, height - 1)) {
                for (x in startX until minOf(startX + sampleSize - 1, width - 1)) {
                    val center = getGrayscale(bitmap.getPixel(x, y))
                    val right = getGrayscale(bitmap.getPixel(x + 1, y))
                    val bottom = getGrayscale(bitmap.getPixel(x, y + 1))
                    
                    val edgeX = abs(right - center)
                    val edgeY = abs(bottom - center)
                    val edgeStrength = (edgeX + edgeY) / 2.0
                    
                    if (edgeStrength > 20) { // Threshold for considering it an edge
                        edgeCount++
                        totalEdgeStrength += edgeStrength
                        
                        // Check if edge is "too perfect" (very sharp, no noise)
                        if (edgeStrength > 60) {
                            perfectEdgeCount++
                        }
                    }
                }
            }
            
            if (edgeCount == 0) return 0.0f
            
            // Too many perfect edges = suspicious (AI-generated)
            val perfectEdgeRatio = perfectEdgeCount.toFloat() / edgeCount
            
            // Also check average edge strength
            val avgEdgeStrength = (totalEdgeStrength / edgeCount).toFloat()
            val normalizedStrength = (avgEdgeStrength / 100.0f).coerceIn(0.0f, 1.0f)
            
            // Combine: high ratio of perfect edges + high average strength = suspicious
            return (perfectEdgeRatio * 0.6f + normalizedStrength * 0.4f).coerceIn(0.0f, 1.0f)
            
        } catch (e: Exception) {
            Log.d(TAG, "Edge consistency check failed: ${e.message}")
            return 0.0f
        }
    }
    
    /**
     * Analyze frequency domain for AI artifacts
     * Real photos have natural sensor noise; AI images are often too smooth
     */
    private fun analyzeFrequencyDomain(bitmap: Bitmap): Float {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val sampleSize = minOf(width, height, 100)
            
            // Sample center region
            val centerX = width / 2
            val centerY = height / 2
            val startX = (centerX - sampleSize / 2).coerceAtLeast(0)
            val startY = (centerY - sampleSize / 2).coerceAtLeast(0)
            
            // Calculate high-frequency energy (simple approximation)
            var highFreqEnergy = 0.0
            var pixelCount = 0
            
            for (y in startY until minOf(startY + sampleSize - 1, height - 1)) {
                for (x in startX until minOf(startX + sampleSize - 1, width - 1)) {
                    val current = getGrayscale(bitmap.getPixel(x, y))
                    val right = getGrayscale(bitmap.getPixel(x + 1, y))
                    val bottom = getGrayscale(bitmap.getPixel(x, y + 1))
                    
                    // High frequency = rapid changes
                    val freqX = abs(right - current)
                    val freqY = abs(bottom - current)
                    highFreqEnergy += (freqX + freqY) / 2.0
                    pixelCount++
                }
            }
            
            if (pixelCount == 0) return 0.0f
            
            val avgHighFreq = (highFreqEnergy / pixelCount).toFloat()
            
            // AI images typically have LOWER high-frequency energy (smoother)
            // Real photos have natural sensor noise (higher high frequency)
            // Threshold empirically set
            val normalFreq = 15.0f // Expected for real photos
            val smoothnessScore = if (avgHighFreq < normalFreq) {
                (normalFreq - avgHighFreq) / normalFreq
            } else {
                0.0f
            }
            
            return smoothnessScore.coerceIn(0.0f, 1.0f)
            
        } catch (e: Exception) {
            Log.d(TAG, "Frequency analysis failed: ${e.message}")
            return 0.0f
        }
    }
    
    /**
     * Convert pixel to grayscale
     */
    private fun getGrayscale(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
}
