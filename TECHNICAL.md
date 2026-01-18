# HiddenLayer Technical Documentation

## Module Architecture

### Core Module
Base utilities and constants shared across all modules.

**Key Files**:
- `Constants.kt` — Pipeline configuration parameters
- `ThermalMonitor.kt` — Device thermal state tracking
- `PrivacyGuard.kt` — Privacy protection utilities

### Data Module
Data models and frame sources.

**Key Files**:
- `FrameModels.kt` — Frame metadata, signal quality, scores
- `CameraFrameSource.kt` — CameraX integration (TODO)
- `ScreenCaptureSource.kt` — MediaProjection API (TODO)
- `MediaFileSource.kt` — Video/image file extraction (TODO)

### Domain Module
Business logic — the 8-stage pipeline implementation.

**Key Files**:
- `AnalysisResult.kt` — Result sealed class hierarchy
- `SignalQualityAnalyzer.kt` — Stage 1: Signal quality scoring
- `BiomechanicalAnalyzer.kt` — Stage 2: Face landmarks + motion (TODO)
- `KeyframeSelector.kt` — Adaptive CNN trigger logic
- `CNNFeatureExtractor.kt` — Stage 3: TFLite inference (TODO)
- `TemporalAnalyzer.kt` — Stage 4: Temporal consistency (TODO)
- `EnsembleDecisionEngine.kt` — Stage 5: Fusion + uncertainty

### Presentation Module
Jetpack Compose UI.

**Key Files**:
- `Theme.kt` — Material 3 theme
- `CameraScreen.kt` — Live camera preview + overlay
- `ConfidenceIndicator.kt` — Animated result visualization

---

## Pipeline Stage Details

### Stage 1: Signal Quality Gating

**Purpose**: Assess input quality to weight downstream analysis.

**Factors**:
1. FPS adequacy (< 12 FPS = unreliable)
2. Resolution (< 480p = degraded landmarks)
3. Motion smoothness (optical flow coherence)
4. Compression (screen share penalty)

**Output**: `SignalQuality` (0-1 score)

**WHY**: Low-quality signals produce unreliable results. This gate prevents false confidence.

---

### Stage 2: Biomechanical Analysis (High-FPS)

**Purpose**: Detect unnatural facial behavior every frame.

**Techniques**:
- MediaPipe Face Mesh (468 landmarks)
- Eye Aspect Ratio (blink pattern detection)
- Mouth Aspect Ratio (speech sync anomalies)
- Head pose smoothness (Kalman filtering)
- Optical flow coherence (boundary checking)

**Output**: `LandmarkScore`, `MotionScore`

**WHY**: Deepfakes often have irregular blinks, mouth jitter, and boundary artifacts.

---

### Stage 3: Adaptive CNN (Keyframes Only)

**Purpose**: Deep learning feature extraction with battery efficiency.

**Model**: EfficientNet-Lite0 (TFLite FP16, ~4.5MB)

**Adaptive Triggers**:
1. Landmark variance > 0.15 (face movement)
2. Motion entropy > 0.2 (scene change)
3. CNN uncertainty increased > 0.1
4. Periodic refresh (every 2 seconds)

**Output**: `CNNScore` (confidence + 1280-dim embedding)

**WHY**: Running CNN every frame = thermal death. Adaptive = 80-90% reduction.

---

### Stage 4: Temporal Behavior Model

**Purpose**: Detect frame-to-frame inconsistencies.

**Approach**:
- Sliding window (10-20 frames)
- Time-normalized aggregation (handles variable FPS)
- 1D temporal convolution over embeddings
- Attention-like weighting by recency

**Output**: `TemporalScore`

**WHY**: Single frames can't reveal temporal artifacts. Deepfakes often have subtle inter-frame glitches.

---

### Stage 5: Adaptive Ensemble Decision

**Purpose**: Fuse all signals with conflict detection.

**Fusion Strategy**:
- Dynamic weights (adapt to signal quality)
- Conflict detection (if CNN ≠ biomechanics → Inconclusive)
- Uncertainty suppression (cap at 95%)
- Multi-signal agreement required for "LikelyDeepfake"

**Result Categories**:
1. **Authentic** — High confidence, low anomaly score
2. **Suspicious** — Medium concern, specific signals flagged
3. **Inconclusive** — Conflicting signals or low quality
4. **LikelyDeepfake** — Multiple signals agree, high confidence

**WHY**: No single detector is perfect. Fusion increases robustness to adversarial attacks.

---

## Performance Optimization

### Thermal Management

**Strategy**:
- Monitor thermal headroom (API 30+)
- Adaptive CNN frequency:
  - Normal: 1.0x (full speed)
  - Moderate: 0.5x (half speed)
  - High: 0.0x (skip CNN)
  - Critical: Pause pipeline

**WHY**: Real-time AI generates heat. Graceful degradation prevents throttling/crashes.

---

### Backpressure Handling

**Strategy**:
- Unlimited ingestion buffer (no frame drops at source)
- Consume at sustainable rate
- Drop oldest frames if buffer grows
- Flow-based reactive streams

**WHY**: Frame drops at camera = lost data. Better to drop downstream after metadata extraction.

---

### Memory Management

**Strategy**:
- Immediate bitmap recycling (`PrivacyGuard.recycleBitmap()`)
- No frame persistence to disk
- Bounded temporal window
- Explicit GC hints after heavy processing

**WHY**: Android has limited heap. Memory leaks = OOM crashes.

---

## Testing Guidelines

### Unit Tests

Test pure logic, no Android dependencies:
```kotlin
@Test
fun signalQualityAnalyzer_lowFPS_penalizesScore() {
    val analyzer = SignalQualityAnalyzer()
    val metadata = FrameMetadata(
        timestamp = 0L,
        sourceType = SourceType.CAMERA,
        resolution = Pair(1920, 1080),
        fps = 10.0f  // Below minimum
    )
    val quality = analyzer.calculateQuality(metadata)
    
    assertTrue(quality.fpsScore < 0.5f)
    assertTrue(quality.overallScore < 0.6f)
}
```

### Integration Tests

Test Android components:
```kotlin
@Test
fun cameraFrameSource_emitsFrames() = runTest {
    val source = CameraFrameSource(context)
    val frames = source.captureFrames().take(10).toList()
    
    assertEquals(10, frames.size)
    assertTrue(frames.all { it.metadata.sourceType == SourceType.CAMERA })
}
```

### Manual Testing

**Checklist**:
- [ ] Camera preview launches without crash
- [ ] Face tracking locks onto user face
- [ ] Confidence indicator animates smoothly
- [ ] Results update every 1-2 seconds
- [ ] No thermal throttling after 5 minutes
- [ ] Memory usage < 200 MB sustained

---

## API Reference

### AnalysisResult

Sealed class with four variants:

```kotlin
sealed class AnalysisResult {
    data class Authentic(val confidence: Float)
    data class Suspicious(val reason: String, val confidence: Float, val signals: List<String>)
    data class Inconclusive(val reason: String)
    data class LikelyDeepfake(val signals: List<String>, val confidence: Float, ...)
    
    fun toDisplayMessage(): String
    fun getConfidence(): Float
}
```

### SignalQualityAnalyzer

```kotlin
class SignalQualityAnalyzer {
    fun calculateQuality(
        metadata: FrameMetadata,
        motionSmoothness: Float? = null
    ): SignalQuality
    
    fun updateFPSHistory(fps: Float)
    fun getAverageFPS(): Float
}
```

### EnsembleDecisionEngine

```kotlin
class EnsembleDecisionEngine {
    fun makeDecision(
        signalQuality: SignalQuality,
        landmarkScore: LandmarkScore?,
        motionScore: MotionScore?,
        cnnScore: CNNScore?,
        temporalScore: TemporalScore?
    ): AnalysisResult
}
```

---

## Troubleshooting

### "App crashes on launch"
- Check camera permissions granted
- Verify TFLite model in `app/src/main/assets/`
- Check Logcat for initialization errors

### "Low FPS / laggy"
- Device thermal state (check `ThermalMonitor` logs)
- Background apps consuming RAM
- NNAPI/GPU delegate not available

### "Always shows Inconclusive"
- Signal quality too low (check overlay message)
- CNN not running (thermal skip?)
- Conflicting signals (expected behavior)

### "High battery drain"
- CNN running too frequently (check keyframe selector)
- Thermal throttling not activating
- Background processing not paused

---

## Future Enhancements

1. **Advanced Temporal Model**
   - Transformer-based attention
   - Multi-scale temporal analysis
   - Adversarial training data

2. **Cross-Platform**
   - iOS variant (CoreML + Metal)
   - Desktop SDK (TensorRT)

3. **Model Updates**
   - OTA model updates
   - A/B testing framework
   - Federated learning

4. **User Features**
   - Recording mode with timestamp
   - Export analysis report
   - Custom sensitivity settings

---

This is production-grade documentation. Update as implementation progresses.
