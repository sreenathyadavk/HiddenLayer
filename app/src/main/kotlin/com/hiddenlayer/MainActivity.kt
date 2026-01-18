package com.hiddenlayer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hiddenlayer.presentation.theme.HiddenLayerTheme
import com.hiddenlayer.presentation.camera.CameraScreen
import com.hiddenlayer.presentation.screenshare.ScreenShareScreen
import com.hiddenlayer.presentation.mediafile.MediaFileScreen
import com.hiddenlayer.presentation.components.GlassCard
import com.hiddenlayer.presentation.components.CyberButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import com.hiddenlayer.presentation.components.CyberBackground

/**
 * Main activity for HiddenLayer - PRODUCTION BUILD
 */
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            HiddenLayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HiddenLayerApp()
                }
            }
        }
    }
}

@Composable
fun HiddenLayerApp() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    var showOnboarding by remember {
        mutableStateOf(!prefs.getBoolean("onboarding_complete", false))
    }
    
    if (showOnboarding) {
        com.hiddenlayer.presentation.onboarding.OnboardingScreen(
            onComplete = {
                prefs.edit().putBoolean("onboarding_complete", true).apply()
                showOnboarding = false
            }
        )
    } else {
        MainAppContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent() {
    var selectedMode by remember { mutableStateOf(AnalysisMode.MEDIA_FILE) }
    
    // Cyberpunk Background Pattern
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // High-Tech Grid Overlay
        CyberBackground()
        
        // Content Area
        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            
            // Main Screen Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedMode) {
                    AnalysisMode.CAMERA -> CameraScreen()
                    AnalysisMode.SCREEN_SHARE -> ScreenShareScreen()
                    AnalysisMode.MEDIA_FILE -> MediaFileScreen()
                }
            }
            
            // Mode Selector (Bottom Navigation style)
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AnalysisMode.values().forEach { mode ->
                        val isSelected = selectedMode == mode
                        val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedMode = mode }
                                .padding(8.dp)
                        ) {
                            Text(
                                text = mode.label.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = color
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Indicator dot
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (isSelected) color else Color.Transparent,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ... remove unused ModeSelector Composable if no longer needed, or keep for reference
// Removing ModeSelector and Placeholders to clean up file

@Composable
fun ScreenSharePlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Screen Share", color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun MediaFilePlaceholder() {
     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Media File", color = MaterialTheme.colorScheme.onSurface)
    }
}

enum class AnalysisMode(val label: String) {
    CAMERA("Camera"),
    SCREEN_SHARE("Screen"),
    MEDIA_FILE("File")
}
