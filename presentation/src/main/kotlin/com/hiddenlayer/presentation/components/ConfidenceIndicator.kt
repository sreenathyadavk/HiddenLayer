package com.hiddenlayer.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hiddenlayer.domain.models.AnalysisResult

/**
 * Animated confidence indicator with color-coded feedback.
 * 
 * WHY: Visual feedback is critical for real-time analysis.
 * Color-coded bar provides instant understanding without panic.
 * 
 * Color mapping:
 * - Green → Authentic
 * - Yellow → Suspicious
 * - Orange → Inconclusive
 * - Red → Likely Deepfake
 */
@Composable
fun ConfidenceIndicator(
    result: AnalysisResult,
    modifier: Modifier = Modifier
) {
    // Determine confidence value
    val confidence = when (result) {
        is AnalysisResult.Authentic -> result.confidence
        is AnalysisResult.Suspicious -> result.confidence
        is AnalysisResult.LikelyDeepfake -> result.confidence
        is AnalysisResult.Inconclusive -> 0.0f
    }
    
    // Smooth animation for confidence changes
    val animatedConfidence by animateFloatAsState(
        targetValue = confidence,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "confidence"
    )
    
    // Determine status color
    val color: Color = when (result) {
        is AnalysisResult.Authentic -> Color(0xFF4CAF50)  // Green
        is AnalysisResult.Suspicious -> Color(0xFFFF9800)  // Orange
        is AnalysisResult.LikelyDeepfake -> Color(0xFFF44336)  // Red
        is AnalysisResult.Inconclusive -> Color(0xFF9E9E9E)  // Gray
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status label
        Text(
            text = when (result) {
                is AnalysisResult.Authentic -> "Authentic"
                is AnalysisResult.Suspicious -> "Suspicious"
                is AnalysisResult.Inconclusive -> "Inconclusive"
                is AnalysisResult.LikelyDeepfake -> "Potential Deepfake"
            },
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
        
        // Animated bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp)
        ) {
            val barWidth = size.width
            val barHeight = size.height
            
            // Background (gray)
            drawRoundRect(
                color = Color(0xFF424242),
                topLeft = Offset.Zero,
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barHeight / 2)
            )
            
            // Foreground (colored progress)
            drawRoundRect(
                color = color,
                topLeft = Offset.Zero,
                size = Size(barWidth * animatedConfidence, barHeight),
                cornerRadius = CornerRadius(barHeight / 2)
            )
        }
        
        // Confidence percentage
        if (result !is AnalysisResult.Inconclusive) {
            Text(
                text = "${(animatedConfidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
