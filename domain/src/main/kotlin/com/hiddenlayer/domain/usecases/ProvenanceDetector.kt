package com.hiddenlayer.domain.usecases

import android.graphics.Bitmap
import android.media.ExifInterface
import android.util.Log
import com.hiddenlayer.domain.models.ProvenanceResult
import java.io.File
import java.io.InputStream

/**
 * Detects AI-generated content through provenance analysis
 * - EXIF metadata
 * - C2PA certificates
 * - Generator fingerprints
 */
class ProvenanceDetector {
    
    companion object {
        private const val TAG = "ProvenanceDetector"
        
        // Known AI generator signatures
        private val AI_GENERATORS = setOf(
            "midjourney",
            "dall-e", "dall·e",
            "stable-diffusion", "stable diffusion", "sdxl",
            "leonardo.ai", "leonardo",
            "playground",
            "ideogram",
            "imagen",
            "google ai",
            "openai",
            "anthropic",
            "grok",
            "gemini"
        )
        
        // EXIF tags that indicate AI generation
        private val AI_EXIF_TAGS = listOf(
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            "CreatorTool",
            "Generator",
            "AIGenerated",
            "Model"
        )
    }
    
    /**
     * Analyze image file for AI provenance indicators
     */
    fun analyze(imagePath: String): ProvenanceResult {
        try {
            // 1. EXIF Metadata Check
            val exifResult = checkExifMetadata(imagePath)
            if (exifResult.isAIGenerated) {
                Log.i(TAG, "✅ AI detected via EXIF: ${exifResult.detectionMethod}")
                return exifResult
            }
            
            // 2. C2PA Certificate Check
            val c2paResult = checkC2PASignature(imagePath)
            if (c2paResult.isAIGenerated) {
                Log.i(TAG, "✅ AI detected via C2PA: ${c2paResult.detectionMethod}")
                return c2paResult
            }
            
            // 3. Raw file string scan for generator names
            val fingerprintResult = checkGeneratorFingerprints(imagePath)
            if (fingerprintResult.isAIGenerated) {
                Log.i(TAG, "✅ AI detected via fingerprint: ${fingerprintResult.detectionMethod}")
                return fingerprintResult
            }
            
            Log.d(TAG, "No AI provenance detected")
            return ProvenanceResult.notDetected()
            
        } catch (e: Exception) {
            Log.w(TAG, "Error analyzing provenance: ${e.message}")
            return ProvenanceResult.notDetected()
        }
    }
    
    /**
     * Check EXIF metadata for AI generator signatures
     */
    private fun checkExifMetadata(imagePath: String): ProvenanceResult {
        try {
            val exif = ExifInterface(imagePath)
            val metadata = mutableMapOf<String, String>()
            
            // Check standard EXIF tags
            val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.lowercase()
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.lowercase()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.lowercase()
            
            // Store metadata
            software?.let { metadata["Software"] = it }
            make?.let { metadata["Make"] = it }
            model?.let { metadata["Model"] = it }
            
            // Check for AI generator names
            listOf(software, make, model).forEach { tag ->
                tag?.let {
                    AI_GENERATORS.forEach { generator ->
                        if (it.contains(generator)) {
                            return ProvenanceResult.detected(
                                method = "EXIF Metadata ($generator)",
                                metadata = metadata
                            )
                        }
                    }
                }
            }
            
            // Check for explicit AI generation flag
            if (software?.contains("ai") == true || 
                software?.contains("generated") == true ||
                model?.contains("ai") == true) {
                return ProvenanceResult.detected(
                    method = "EXIF AI Flag",
                    metadata = metadata
                )
            }
            
        } catch (e: Exception) {
            Log.d(TAG, "EXIF check failed: ${e.message}")
        }
        
        return ProvenanceResult.notDetected()
    }
    
    /**
     * Check for C2PA (Content Credentials) signature
     */
    private fun checkC2PASignature(imagePath: String): ProvenanceResult {
        try {
            val file = File(imagePath)
            if (!file.exists()) return ProvenanceResult.notDetected()
            
            // Read first 64KB to check for C2PA markers
            val headerBytes = file.inputStream().use { stream ->
                val buffer = ByteArray(65536)
                val bytesRead = stream.read(buffer)
                buffer.copyOf(bytesRead)
            }
            
            val headerString = String(headerBytes, Charsets.ISO_8859_1).lowercase()
            
            // Check for C2PA indicators
            val c2paMarkers = listOf(
                "c2pa",
                "manifest.json",
                "urn:c2pa",
                "contentcredentials",
                "cai"
            )
            
            c2paMarkers.forEach { marker ->
                if (headerString.contains(marker)) {
                    return ProvenanceResult.detected(
                        method = "C2PA Certificate",
                        metadata = mapOf("Marker" to marker)
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.d(TAG, "C2PA check failed: ${e.message}")
        }
        
        return ProvenanceResult.notDetected()
    }
    
    /**
     * Scan file bytes for known generator fingerprints
     */
    private fun checkGeneratorFingerprints(imagePath: String): ProvenanceResult {
        try {
            val file = File(imagePath)
            if (!file.exists()) return ProvenanceResult.notDetected()
            
            // Read file into string (limit to first 1MB to avoid memory issues)
            val maxBytes = minOf(file.length(), 1024 * 1024).toInt()
            val fileBytes = file.inputStream().use { stream ->
                val buffer = ByteArray(maxBytes)
                stream.read(buffer)
                buffer
            }
            
            val fileString = String(fileBytes, Charsets.ISO_8859_1).lowercase()
            
            // Check for generator names in raw data
            AI_GENERATORS.forEach { generator ->
                if (fileString.contains(generator)) {
                    return ProvenanceResult.detected(
                        method = "Generator Fingerprint ($generator)",
                        metadata = mapOf("Generator" to generator)
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.d(TAG, "Fingerprint check failed: ${e.message}")
        }
        
        return ProvenanceResult.notDetected()
    }
}
