package com.hiddenlayer.domain.usecases

import android.util.Log
import com.hiddenlayer.data.models.CNNScore
import com.hiddenlayer.domain.models.*

/**
 * Intelligent fusion engine that combines multiple detection signals
 * to make final authenticity determination
 */
class DetectionFusion {
    
    companion object {
        private const val TAG = "DetectionFusion"
    }
    
    /**
     * Combine all detection signals using rule-based logic
     * 
     * Priority order:
     * 1. Provenance (highest confidence)
     * 2. CNN + Artifacts combined
     * 3. Strong CNN alone
     * 4. Default to authentic
     */
    fun combine(
        provenance: ProvenanceResult,
        artifacts: ArtifactScore,
        cnn: CNNScore
    ): FinalVerdict {
        
        Log.i(TAG, """
            
╔════════════════════════════════════════╗
║        DETECTION FUSION ANALYSIS       ║
╠════════════════════════════════════════╣
║ Provenance: ${if (provenance.isAIGenerated) "AI DETECTED" else "Clean"}
║   Method: ${provenance.detectionMethod}
║   Confidence: ${String.format("%.1f%%", provenance.confidence * 100)}
║
║ Artifacts:
║   Anomaly Level: ${String.format("%.1f%%", artifacts.anomalyLevel * 100)}
║   Banding: ${String.format("%.2f", artifacts.details["banding"] ?: 0f)}
║   Edges: ${String.format("%.2f", artifacts.details["edges"] ?: 0f)}
║   Frequency: ${String.format("%.2f", artifacts.details["frequency"] ?: 0f)}
║
║ CNN Model:
║   Fake: ${String.format("%.1f%%", cnn.deepfakeConfidence * 100)}
║   Real: ${String.format("%.1f%%", (1 - cnn.deepfakeConfidence) * 100)}
╚════════════════════════════════════════╝
        """.trimIndent())
        
        val signals = DetectionSignals(provenance, artifacts, cnn)
        
        // RULE 1: PROVENANCE - HIGHEST PRIORITY
        // If we detect AI provenance, it's definitive
        if (provenance.isAIGenerated && provenance.confidence > 0.9f) {
            Log.w(TAG, "🔴 VERDICT: FAKE (Provenance)")
            return FinalVerdict(
                isFake = true,
                confidence = provenance.confidence,
                reason = "AI-Generated Content Detected\n" +
                        "Method: ${provenance.detectionMethod}",
                threatLevel = ThreatLevel.HIGH,
                signals = signals
            )
        }
        
        // RULE 2: HIGH CNN + HIGH ARTIFACTS
        // Both signals agree = high confidence
        if (cnn.deepfakeConfidence > 0.7f && artifacts.anomalyLevel > 0.6f) {
            Log.w(TAG, "🔴 VERDICT: FAKE (CNN + Artifacts)")
            return FinalVerdict(
                isFake = true,
                confidence = 0.85f,
                reason = "Deepfake Detected\n" +
                        "Multiple manipulation indicators found",
                threatLevel = ThreatLevel.HIGH,
                signals = signals
            )
        }
        
        // RULE 3: MEDIUM CNN + STRONG ARTIFACTS
        // Artifacts are more confident
        if (cnn.deepfakeConfidence > 0.5f && artifacts.anomalyLevel > 0.75f) {
            Log.w(TAG, "🟠 VERDICT: LIKELY FAKE (Artifacts)")
            return FinalVerdict(
                isFake = true,
                confidence = 0.75f,
                reason = "Likely Manipulated Content\n" +
                        "Visual artifacts detected",
                threatLevel = ThreatLevel.MEDIUM,
                signals = signals
            )
        }
        
        // RULE 4: STRONG CNN ALONE
        // CNN is very confident, even without other signals
        if (cnn.deepfakeConfidence > 0.8f) {
            Log.w(TAG, "🟠 VERDICT: FAKE (CNN High Confidence)")
            return FinalVerdict(
                isFake = true,
                confidence = 0.8f,
                reason = "Deepfake Detected\n" +
                        "Neural network high confidence",
                threatLevel = ThreatLevel.MEDIUM,
                signals = signals
            )
        }
        
        // RULE 5: WEAK SIGNALS - UNCERTAIN
        // CNN shows some concern but not definitive
        if (cnn.deepfakeConfidence > 0.5f && cnn.deepfakeConfidence < 0.7f) {
            if (artifacts.anomalyLevel > 0.5f) {
                Log.w(TAG, "🟡 VERDICT: UNCERTAIN (Mixed signals)")
                return FinalVerdict(
                    isFake = true,
                    confidence = 0.6f,
                    reason = "Inconclusive - Manual Review Recommended\n" +
                            "Mixed detection signals",
                    threatLevel = ThreatLevel.LOW,
                    signals = signals
                )
            }
        }
        
        // RULE 6: CNN UNCERTAIN BUT ARTIFACTS ELEVATED
        // Handle case where CNN is stuck at ~50% but artifacts show AI patterns
        // This is critical when CNN model isn't working properly
        if (cnn.deepfakeConfidence >= 0.45f && cnn.deepfakeConfidence <= 0.55f) {
            // CNN is completely uncertain, trust artifacts if they're significantly elevated
            // Threshold at 65% to avoid false positives from compression/camera noise
            if (artifacts.anomalyLevel > 0.65f) {
                Log.w(TAG, "🟠 VERDICT: LIKELY FAKE (Artifact-based, CNN uncertain)")
                return FinalVerdict(
                    isFake = true,
                    confidence = 0.65f,
                    reason = "AI-Generated Content Suspected\n" +
                            "Visual artifacts detected (CNN uncertain)",
                    threatLevel = ThreatLevel.MEDIUM,
                    signals = signals
                )
            }
        }
        
        // DEFAULT: AUTHENTIC
        // All signals are low, content appears real
        val realConfidence = maxOf(
            1.0f - cnn.deepfakeConfidence,
            1.0f - artifacts.anomalyLevel,
            0.7f // Minimum confidence when no threats detected
        )
        
        Log.i(TAG, "✅ VERDICT: AUTHENTIC")
        return FinalVerdict(
            isFake = false,
            confidence = realConfidence,
            reason = "Content Appears Authentic\n" +
                    "No AI signals detected",
            threatLevel = ThreatLevel.NONE,
            signals = signals
        )
    }
    
    /**
     * Get human-readable explanation of verdict
     */
    fun explainVerdict(verdict: FinalVerdict): String {
        val sb = StringBuilder()
        
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine("      DETECTION REPORT")
        sb.appendLine("═══════════════════════════════════")
        sb.appendLine()
        
        // Verdict
        val status = if (verdict.isFake) "❌ DETECTED" else "✅ AUTHENTIC"
        sb.appendLine("STATUS: $status")
        sb.appendLine("CONFIDENCE: ${String.format("%.0f%%", verdict.confidence * 100)}")
        sb.appendLine("THREAT LEVEL: ${verdict.threatLevel}")
        sb.appendLine()
        
        // Reason
        sb.appendLine("ANALYSIS:")
        sb.appendLine(verdict.reason)
        sb.appendLine()
        
        // Signal breakdown
        sb.appendLine("DETECTION SIGNALS:")
        sb.appendLine("├─ Provenance: ${if (verdict.signals.provenance.isAIGenerated) "⚠️ AI Detected" else "✓ Clean"}")
        sb.appendLine("├─ Artifacts: ${String.format("%.0f%%", verdict.signals.artifacts.anomalyLevel * 100)} anomaly")
        sb.appendLine("└─ CNN Model: ${String.format("%.0f%%", verdict.signals.cnn.deepfakeConfidence * 100)} fake probability")
        sb.appendLine()
        
        sb.appendLine("═══════════════════════════════════")
        
        return sb.toString()
    }
}
