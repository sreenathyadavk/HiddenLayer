#!/bin/bash
# Quick fix and build script

echo "🔧 Fixing Gradle issue..."

# Clean previous build
echo "1. Cleaning previous build..."
./gradlew clean --no-daemon

# Build APK
echo "2. Building release APK..."
./gradlew assembleRelease --no-daemon --warning-mode=none

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ BUILD SUCCESSFUL!"
    echo ""
    echo "📦 APK Location:"
    ls -lh app/build/outputs/apk/release/app-release.apk
    echo ""
    echo "📱 To install:"
    echo "   Option 1: Transfer APK to phone and install manually"
    echo "   Option 2: adb install app/build/outputs/apk/release/app-release.apk"
    echo ""
else
    echo ""
    echo "❌ BUILD FAILED"
    echo "Opening Android Studio is recommended for this project."
    echo ""
fi
