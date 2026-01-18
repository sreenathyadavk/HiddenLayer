# APK Release Versions

This folder contains versioned APK releases for the HiddenLayer deepfake detection app.

## Version History

### v1.0.0 (2026-01-18)
- Initial release
- Working features: Screen share, Media file analysis
- Camera detection: Under optimization
- Model: 88MB TFLite (deepfake_net.tflite)

## Naming Convention

- Format: `v<major>.<minor>.<patch>.apk`
- Example: `v1.0.0.apk`

## File Size

Each APK is approximately 95-100MB due to the included ML model.

## Installation

```bash
adb install -r apks/v1.0.0.apk
```
