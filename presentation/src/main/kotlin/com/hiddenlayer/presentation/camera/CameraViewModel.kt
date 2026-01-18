package com.hiddenlayer.presentation.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiddenlayer.domain.models.AnalysisResult
import com.hiddenlayer.domain.models.DetailedAnalysis
import com.hiddenlayer.domain.pipeline.FramePipeline
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for camera screen.
 * 
 * WHY: Separates UI from business logic, survives configuration changes.
 * Manages pipeline lifecycle and exposes reactive state to UI.
 */
class CameraViewModel(
    private val pipeline: FramePipeline
) : ViewModel() {
    
    private val _analysisState = MutableStateFlow<DetailedAnalysis?>(null)
    val analysisState: StateFlow<DetailedAnalysis?> = _analysisState.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    /**
     * Start camera analysis pipeline.
     */
    fun startAnalysis(
        surfaceProvider: Preview.SurfaceProvider? = null,
        cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    ) {
        if (_isProcessing.value) return
        
        _isProcessing.value = true
        
        viewModelScope.launch {
            pipeline.startPipeline(surfaceProvider, cameraSelector)
                .catch { e ->
                    // Error handling
                    _analysisState.value = DetailedAnalysis(
                        result = AnalysisResult.Inconclusive("Pipeline error: ${e.message}"),
                        signalQuality = 0.0f,
                        landmarkConsistency = 0.0f,
                        motionStability = 0.0f,
                        cnnDeepfakeScore = 0.0f,
                        temporalConsistency = 0.0f,
                        processingTimeMs = 0L,
                        framesAnalyzed = 0
                    )
                    _isProcessing.value = false
                }
                .collect { analysis ->
                    _analysisState.value = analysis
                }
        }
    }
    
    /**
     * Stop analysis pipeline.
     */
    fun stopAnalysis() {
        pipeline.stop()
        _isProcessing.value = false
    }
    
    override fun onCleared() {
        super.onCleared()
        stopAnalysis()
    }
}
