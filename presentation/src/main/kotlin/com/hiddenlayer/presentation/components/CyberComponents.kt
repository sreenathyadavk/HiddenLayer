package com.hiddenlayer.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.hiddenlayer.presentation.theme.*

/**
 * Soft Minimal Card with rounded corners and subtle shadow
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = PureWhite
        ),
        elevation = CardDefaults.cardElevation(8.dp), // Soft shadow
        shape = RoundedCornerShape(24.dp) // Highly rounded like the reference
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

/**
 * Soft Minimal Button
 * Solid rounded button with clean aesthetic
 */
@Composable
fun CyberButton(
    onClick: () -> Unit,
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    color: Color = SoftBlack,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) color else LightGray,
            contentColor = if (enabled) PureWhite else SoftGray
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Pulsing Scanner Line Animation
 * Use inside a Box over camera preview.
 */
@Composable
fun ScannerAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    
    // Animate vertical position (0f to 1f)
    val position by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "position"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val y = size.height * position
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SoftBlack,
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx()
                )
                // Draw glow
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            SoftBlack.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 10.dp.toPx()
                )
            }
    )
}

/**
 * Rich Animated Cyber Progress Bar
 * Segmented and glowing.
 */
@Composable
fun CyberProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = SoftBlack
) {
    Box(
        modifier = modifier
            .height(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.5f),
                            color,
                            color.copy(alpha = 0.5f)
                        )
                    )
                )
        )
        
        // Glitch/Scanline effect
        val infiniteTransition = rememberInfiniteTransition(label = "progress_glitch")
        val xPosition by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing)
            ),
            label = "glitch_pos"
        )
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
             // Draw scanline
            val x = canvasWidth * xPosition
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(x, 0f),
                end = Offset(x + 20f, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

/**
 * High-Tech Background Pattern
 * Grid lines drawing.
 */
@Composable
fun CyberBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().background(OffWhite)) {
        val gridSize = 40.dp.toPx()
        val gridColor = LightGray.copy(alpha = 0.3f)
        
        // Draw vertical lines
        for (x in 0..size.width.toInt() step gridSize.toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), size.height),
                strokeWidth = 1f
            )
        }
        
        // Draw horizontal lines
        for (y in 0..size.height.toInt() step gridSize.toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y.toFloat()),
                end = Offset(size.width, y.toFloat()),
                strokeWidth = 1f
            )
        }
    }
}

/**
 * Status Badge for Analysis Results
 */
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)) // Increased opacity for richer look
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp) // Richer padding
        )
    }
}
