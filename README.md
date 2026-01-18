# HiddenLayer - AI-Powered Deepfake Detection

<p align="center">
  <img src="https://img.shields.io/badge/Android-34-green?logo=android" alt="Android"/>
  <img src="https://img.shields.io/badge/Kotlin-1.9-purple?logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/TensorFlow%20Lite-2.14-orange?logo=tensorflow" alt="TFLite"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-1.5-blue?logo=jetpackcompose" alt="Compose"/>
  <img src="https://img.shields.io/badge/Status-Active-success" alt="Status"/>
</p>

Real-time deepfake detection Android application using advanced multi-modal fusion techniques for identifying AI-generated or manipulated media.

## ✨ Key Features

- 🎥 **Real-time Camera Analysis** - Live deepfake detection through device camera
- 📺 **Screen Share Detection** - Analyze screen content in real-time  
- 📁 **Media File Scanner** - Batch analysis of local images and videos
- 🧠 **Multi-Modal Fusion** - Combines CNN inference, artifact detection, and provenance analysis
- ⚡ **Optimized Performance** - TensorFlow Lite with XNNPACK CPU acceleration
- 🔒 **Privacy-First Design** - 100% on-device processing, zero cloud uploads

## 🏗️ Architecture

```
┌──────────────────────────────────────────────┐
│          Presentation Layer                  │
│        Jetpack Compose + Material3           │
├──────────────────────────────────────────────┤
│           Domain Layer (Business Logic)      │
│  ┌─────────────┐  ┌──────────────────────┐  │
│  │ CNN Model   │  │ Artifact Detector    │  │
│  │ (TFLite)    │  │ (Signal Processing)  │  │
│  └─────────────┘  └──────────────────────┘  │
│  ┌──────────────────────────────────────┐   │
│  │   Detection Fusion Engine            │   │
│  │   (Multi-modal Decision Making)      │   │
│  └──────────────────────────────────────┘   │
├──────────────────────────────────────────────┤
│            Data Layer (Sources)              │
│    CameraX  •  MediaProjection  •  Storage   │
└──────────────────────────────────────────────┘
```

## 🎯 Detection Methods

### 1. **CNN Deep Learning**
- Custom trained model (88MB)
- Input: 299x299 RGB images
- Binary classification: Real vs Fake

### 2. **Artifact Analysis**
- Banding detection (compression artifacts)
- Edge inconsistency analysis
- Frequency domain analysis

### 3. **Provenance Checking**
- EXIF metadata inspection
- AI signature detection
- Creation tool identification

### 4. **Fusion Engine**
- Combines all signals with weighted confidence
- Adaptive thresholding
- Temporal consistency validation

## 🚀 Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose + Material3 |
| **ML Inference** | TensorFlow Lite 2.14 |
| **Camera** | CameraX 1.3 |
| **Architecture** | Clean Architecture (MVVM) |
| **Async** | Kotlin Coroutines + Flow |
| **DI** | Manual (lightweight) |

## 📦 Model Details

- **File:** `app/src/main/assets/deepfake_net.tflite`
- **Size:** 88MB
- **Architecture:** Custom CNN with 105 operations
- **Delegation:** XNNPACK (CPU optimized)
- **Input Shape:** `[1, 299, 299, 3]`
- **Output Shape:** `[1, 2]` (fake_probability, real_probability)

## 🔧 Building from Source

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17+
- Android SDK 34
- Gradle 8.2+

### Build Steps

```bash
# Clone repository
git clone git@github.com:sreenathyadavk/HiddenLayer.git
cd HiddenLayer

# Build debug APK
./gradlew assembleDebug

# Output location
# app/build/outputs/apk/debug/app-debug.apk

# Install via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## 📱 System Requirements

- **OS:** Android 8.0 (API 26) or higher
- **RAM:** 4GB+ recommended (2GB minimum)
- **Storage:** 150MB for app + models
- **Permissions:** 
  - Camera (for live analysis)
  - Storage (for media file scanning)

## 🎮 Usage

### Live Camera Detection
1. Open app → Select "Camera" mode
2. Grant camera permissions
3. Point camera at subject
4. Real-time confidence score displayed

### Media File Analysis
1. Select "Media File" mode
2. Choose image/video from gallery
3. View detailed analysis results

### Screen Share Detection
1. Select "Screen Share" mode
2. Grant screen recording permission
3. Share any app screen for analysis

## 📊 Current Status

| Feature | Status |
|---------|--------|
| Screen Share Detection | ✅ Operational |
| Media File Analysis | ✅ Operational |
| Live Camera Detection | 🚧 Under Optimization |
| Batch Processing | 📝 Planned |

## 📈 Performance

- **Frame Processing:** ~150-200ms per frame
- **Throughput:** 5-7 FPS (real-time camera)|
- **Memory Usage:** ~256MB peak
- **Battery Impact:** Moderate (camera + ML inference)

## 🔐 Privacy & Security

- ✅ All processing happens on-device
- ✅ No internet connection required
- ✅ No data uploaded to servers
- ✅ No user tracking or analytics
- ✅ Full source code transparency

## 🗂️ Project Structure

```
HiddenLayer/
├── app/                    # Android application module
├── core/                   # Shared utilities and constants
├── data/                   # Data layer (frame sources)
├── domain/                 # Business logic
│   ├── models/            # Data models
│   ├── pipeline/          # Processing pipeline
│   └── usecases/          # Detection algorithms
├── presentation/           # UI layer (Compose)
└── apks/                   # Versioned APK releases
```

## 📦 APK Releases

Versioned APKs are available in the `/apks` folder:

- `apks/v1.0.0.apk` - Initial release
- `apks/v1.1.0.apk` - Camera optimization
- Check folder for latest versions

## 🤝 Contributing

This is an academic/research project. Contributions, issues, and feature requests are welcome!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add AmazingFeature'`)
4. Push to branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

```
MIT License

Copyright (c) 2026 Sreenath Yadav K

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED.
```

## 👨‍💻 Author

**Sreenath Yadav K**  
GitHub: [@sreenathyadavk](https://github.com/sreenathyadavk)

## 📚 Research & References

This project implements concepts from:
- DeepFake detection research papers
- CNN-based image forensics
- Multi-modal fusion techniques
- Artifact analysis in compressed media

## ⚠️ Disclaimer

This is an academic/research project for educational purposes. For production use in critical applications, additional validation, testing, and certifications are recommended.

---

<p align="center">Made with ❤️ for a safer digital world</p>
